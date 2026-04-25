package com.sermilion.personalgraph.data.repository

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.common.dispatcher.DispatcherProvider
import com.sermilion.personalgraph.data.codec.MarkdownFrontmatterCodec
import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.layout.VaultPolicy
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.ConcurrentHashMap

@AppScope
@Inject
class PersonalGraphVaultRepository(
  private val vaultRoot: Path,
  private val dispatcherProvider: DispatcherProvider,
  private val codec: MarkdownFrontmatterCodec,
  private val pathResolver: VaultPathResolver,
) : VaultRepository {

  private val logger = KotlinLogging.logger {}

  private val posixSupported: Boolean =
    FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

  private val decodeContext: VaultDecodeContext = VaultDecodeContext(
    vaultRoot = vaultRoot,
    pathResolver = pathResolver,
    codec = codec,
    logger = logger,
  )

  // Per-id serialization for concurrent vault writes; entries are intentionally never removed
  // (low-cardinality, short-lived process).
  private val nodeMutexes: ConcurrentHashMap<NodeId, Mutex> = ConcurrentHashMap()

  override fun observeNode(id: NodeId): Flow<VaultNode?> {
    logger.debug { "observeNode placeholder for id=$id" }
    return flowOf(null)
  }

  override fun observeNodesInBranch(branchPath: String): Flow<List<VaultNode>> {
    logger.debug { "observeNodesInBranch placeholder for branchPath=$branchPath" }
    return flowOf(emptyList())
  }

  override suspend fun findNode(id: NodeId): VaultNode? = withContext(dispatcherProvider.io) {
    if (VaultPolicy.isReadBlocked(id.value)) {
      logger.debug { "findNode blocked: id=$id is in a read-blocked branch" }
      return@withContext null
    }
    findNodeUnsafeOrLogged(id)
  }

  override suspend fun listNodesInBranch(branchPath: String): List<VaultNode> = withContext(dispatcherProvider.io) {
    if (VaultPolicy.isReadBlocked(branchPath)) {
      logger.debug { "listNodesInBranch blocked: branchPath=$branchPath is read-blocked" }
      return@withContext emptyList()
    }
    if (!VaultPolicy.isReadAllowed(branchPath)) {
      logger.debug { "listNodesInBranch blocked: branchPath=$branchPath is not read-allowed" }
      return@withContext emptyList()
    }
    listBranchUnsafeOrLogged(branchPath)
  }

  override suspend fun listStagedObservations(): List<StateNode> = listNodesInBranch(
    VaultLayout.BRANCH_STAGING_OBSERVATIONS,
  ).filterIsInstance<StateNode>()

  override suspend fun writeNode(node: VaultNode): WriteOutcome = withContext(dispatcherProvider.io) {
    nodeMutexes.computeIfAbsent(node.id) { Mutex() }.withLock { writeNodeLocked(node) }
  }

  override suspend fun moveNode(id: NodeId, newBranchPath: String): WriteOutcome = withContext(dispatcherProvider.io) {
    nodeMutexes.computeIfAbsent(id) { Mutex() }.withLock { moveNodeLockedOrLogged(id, newBranchPath) }
  }

  override suspend fun deleteNode(id: NodeId): WriteOutcome = withContext(dispatcherProvider.io) {
    nodeMutexes.computeIfAbsent(id) { Mutex() }.withLock { deleteNodeLockedOrLogged(id) }
  }

  override suspend fun listBacklinks(id: NodeId): List<VaultNode> = withContext(dispatcherProvider.io) {
    try {
      collectBacklinks(id.value)
    } catch (e: IOException) {
      logger.warn(e) { "listBacklinks failed for id=$id" }
      emptyList()
    } catch (e: SecurityException) {
      logger.warn(e) { "listBacklinks denied for id=$id" }
      emptyList()
    }
  }

  private fun findNodeUnsafeOrLogged(id: NodeId): VaultNode? = try {
    val target = pathResolver.resolve(vaultRoot, id)
    when {
      !pathResolver.assertWithinVault(vaultRoot, target) -> {
        logger.warn { "findNode rejected: outside vault id=$id" }
        null
      }
      !Files.exists(target) -> null
      Files.size(target) > MAX_VAULT_FILE_SIZE_BYTES -> {
        logger.warn { "findNode skipped: file=$target exceeds max size of $MAX_VAULT_FILE_SIZE_BYTES bytes" }
        null
      }
      else -> codec.decode(id, Files.readString(target))
    }
  } catch (e: IOException) {
    logger.warn(e) { "findNode failed for id=$id" }
    null
  } catch (e: SecurityException) {
    logger.warn(e) { "findNode denied for id=$id" }
    null
  }

  private suspend fun listBranchUnsafeOrLogged(branchPath: String): List<VaultNode> = try {
    val branchDir = vaultRoot.resolve(branchPath)
    when {
      !Files.exists(branchDir) || !Files.isDirectory(branchDir) -> emptyList()
      !pathResolver.assertWithinVault(vaultRoot, branchDir) -> emptyList()
      else -> {
        val results = mutableListOf<VaultNode>()
        walkAndAccumulate(decodeContext, branchDir, MAX_LIST_DEPTH, MAX_LIST_RESULTS, results) { true }
        results
      }
    }
  } catch (e: IOException) {
    logger.warn(e) { "listNodesInBranch failed for branchPath=$branchPath" }
    emptyList()
  } catch (e: SecurityException) {
    logger.warn(e) { "listNodesInBranch denied for branchPath=$branchPath" }
    emptyList()
  }

  private fun writeNodeLocked(node: VaultNode): WriteOutcome {
    val target = pathResolver.resolve(vaultRoot, node.id)
    val parent = target.parent
    val rejection = when {
      !VaultPolicy.isWriteAllowed(node.id.value) -> WriteOutcome.Failed(REASON_WRITE_BLOCKED)
      !pathResolver.assertWithinVault(vaultRoot, target) -> WriteOutcome.Failed(REASON_OUTSIDE_VAULT)
      parent == null -> WriteOutcome.Failed(REASON_NO_PARENT)
      else -> null
    }
    if (rejection != null) return rejection
    val realParent = requireNotNull(parent)
    val createdAncestors = createDirectoriesTracking(realParent)
    if (!pathResolver.assertWithinVault(vaultRoot, realParent.toRealPathOrSelf())) {
      return WriteOutcome.Failed(REASON_OUTSIDE_VAULT)
    }
    applyOwnerOnlyToDirs(createdAncestors)
    return performAtomicWrite(node, realParent, target)
  }

  private fun applyOwnerOnlyToDirs(directories: List<Path>) {
    if (!posixSupported || directories.isEmpty()) return
    for (dir in directories) {
      runCatching { Files.setPosixFilePermissions(dir, ownerOnlyDirPermissions()) }
        .onFailure { logger.warn(it) { "Failed to set 0700 permissions on $dir" } }
    }
  }

  private fun performAtomicWrite(node: VaultNode, parent: Path, target: Path): WriteOutcome {
    val encoded = codec.encode(node)
    var moved = false
    var tempFile: Path? = null
    return try {
      tempFile = if (posixSupported) {
        val attr = PosixFilePermissions.asFileAttribute(ownerOnlyFilePermissions())
        Files.createTempFile(parent, ".pg-", ".tmp", attr)
      } else {
        Files.createTempFile(parent, ".pg-", ".tmp")
      }
      Files.writeString(tempFile, encoded)
      try {
        Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      } catch (e: AtomicMoveNotSupportedException) {
        logger.warn(e) { "Atomic move not supported for target=$target; retrying with REPLACE_EXISTING only" }
        Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING)
      }
      moved = true
      if (posixSupported) {
        runCatching { Files.setPosixFilePermissions(target, ownerOnlyFilePermissions()) }
          .onFailure { logger.warn(it) { "Failed to set 0600 permissions on $target" } }
      }
      WriteOutcome.Applied
    } catch (e: IOException) {
      logger.warn(e) { "writeNode failed for id=${node.id}" }
      WriteOutcome.Failed(e.reasonString())
    } catch (e: SecurityException) {
      logger.warn(e) { "writeNode denied for id=${node.id}" }
      WriteOutcome.Failed(e.reasonString())
    } finally {
      if (!moved && tempFile != null) {
        runCatching { Files.deleteIfExists(tempFile) }
      }
    }
  }

  private fun moveNodeLockedOrLogged(id: NodeId, newBranchPath: String): WriteOutcome = try {
    moveNodeUnsafe(id, newBranchPath)
  } catch (e: IOException) {
    logger.warn(e) { "moveNode failed for id=$id newBranchPath=$newBranchPath" }
    WriteOutcome.Failed(e.reasonString())
  } catch (e: SecurityException) {
    logger.warn(e) { "moveNode denied for id=$id newBranchPath=$newBranchPath" }
    WriteOutcome.Failed(e.reasonString())
  }

  private fun moveNodeUnsafe(id: NodeId, newBranchPath: String): WriteOutcome {
    val source = pathResolver.resolve(vaultRoot, id)
    val targetDir = vaultRoot.resolve(newBranchPath)
    val precheck = when {
      !VaultPolicy.isWriteAllowed(newBranchPath) -> WriteOutcome.Failed(REASON_WRITE_BLOCKED)
      !pathResolver.assertWithinVault(vaultRoot, source) -> WriteOutcome.Failed(REASON_OUTSIDE_VAULT)
      !Files.exists(source) -> WriteOutcome.NotFound
      !pathResolver.assertWithinVault(vaultRoot, targetDir) -> WriteOutcome.Failed(REASON_OUTSIDE_VAULT)
      else -> null
    }
    if (precheck != null) return precheck
    val createdAncestors = createDirectoriesTracking(targetDir)
    applyOwnerOnlyToDirs(createdAncestors)
    val target = targetDir.resolve(source.fileName.toString())
    if (Files.exists(target)) return WriteOutcome.Conflict
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: AtomicMoveNotSupportedException) {
      logger.warn(e) { "Atomic move not supported for target=$target; retrying without ATOMIC_MOVE" }
      Files.move(source, target)
    }
    return WriteOutcome.Applied
  }

  private fun deleteNodeLockedOrLogged(id: NodeId): WriteOutcome = try {
    val target = pathResolver.resolve(vaultRoot, id)
    when {
      !VaultPolicy.isWriteAllowed(id.value) -> WriteOutcome.Failed(REASON_WRITE_BLOCKED)
      !pathResolver.assertWithinVault(vaultRoot, target) -> WriteOutcome.Failed(REASON_OUTSIDE_VAULT)
      Files.deleteIfExists(target) -> WriteOutcome.Applied
      else -> WriteOutcome.NotFound
    }
  } catch (e: IOException) {
    logger.warn(e) { "deleteNode failed for id=$id" }
    WriteOutcome.Failed(e.reasonString())
  } catch (e: SecurityException) {
    logger.warn(e) { "deleteNode denied for id=$id" }
    WriteOutcome.Failed(e.reasonString())
  }

  private suspend fun collectBacklinks(targetValue: String): List<VaultNode> {
    val results = mutableListOf<VaultNode>()
    val readableBranchDirs = VaultPolicy.WHITELISTED_READ_BRANCH_PREFIXES
      .filterNot { VaultPolicy.isReadBlocked(it) }
      .map { vaultRoot.resolve(it) }
      .filter { Files.exists(it) && Files.isDirectory(it) }
    for (branchDir in readableBranchDirs) {
      if (results.size >= MAX_BACKLINK_RESULTS) break
      walkAndAccumulate(decodeContext, branchDir, MAX_BACKLINK_DEPTH, MAX_BACKLINK_RESULTS, results) { node ->
        node.links.any { it.value == targetValue }
      }
    }
    return results
  }

  companion object {
    private const val REASON_OUTSIDE_VAULT: String = "Path is outside the vault root"
    private const val REASON_NO_PARENT: String = "Target has no parent directory"
    private const val REASON_WRITE_BLOCKED: String = "Path is outside write-allowed vault branches"
    private const val MAX_LIST_DEPTH: Int = 8
    private const val MAX_LIST_RESULTS: Int = 1000
    private const val MAX_BACKLINK_DEPTH: Int = 8
    private const val MAX_BACKLINK_RESULTS: Int = 1000
  }
}
