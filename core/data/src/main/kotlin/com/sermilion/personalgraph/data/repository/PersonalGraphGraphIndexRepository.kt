package com.sermilion.personalgraph.data.repository

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.common.dispatcher.DispatcherProvider
import com.sermilion.personalgraph.data.codec.MarkdownFrontmatterCodec
import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.domain.graph.GraphIndexEntry
import com.sermilion.personalgraph.domain.layout.VaultPolicy
import com.sermilion.personalgraph.domain.model.EmotionalStateNode
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.repository.GraphIndexInvalidator
import com.sermilion.personalgraph.domain.repository.GraphIndexRepository
import com.sermilion.personalgraph.domain.tokens.TokenEstimator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import me.tatarka.inject.annotations.Inject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@AppScope
@Inject
class PersonalGraphGraphIndexRepository(
  private val vaultRoot: Path,
  private val dispatcherProvider: DispatcherProvider,
  private val codec: MarkdownFrontmatterCodec,
  private val pathResolver: VaultPathResolver,
  private val tokenEstimator: TokenEstimator,
) : GraphIndexRepository,
  GraphIndexInvalidator {

  private val logger = KotlinLogging.logger {}

  private val cache: ConcurrentHashMap<NodeId, CachedEntry> = ConcurrentHashMap()
  private val branchRootMtime: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
  private val aliasIndex: ConcurrentHashMap<String, NodeId> = ConcurrentHashMap()
  private val titleIndex: ConcurrentHashMap<String, NodeId> = ConcurrentHashMap()
  private val pathIndex: ConcurrentHashMap<String, NodeId> = ConcurrentHashMap()
  private val sideMapLock: Any = Any()

  override suspend fun listEntriesInBranch(
    branchPath: String,
  ): List<GraphIndexEntry> = withContext(dispatcherProvider.io) {
    val normalized = branchPath.trim('/')
    val branchDir = resolveBranchDirOrNull(normalized) ?: return@withContext emptyList()
    refreshBranchIfChanged(normalized, branchDir)
    try {
      walkBranch(normalized, branchDir).sortedBy { it.id.value }
    } catch (e: IOException) {
      logger.warn(e) { "listEntriesInBranch failed for branchPath=$normalized" }
      emptyList()
    } catch (e: SecurityException) {
      logger.warn(e) { "listEntriesInBranch denied for branchPath=$normalized" }
      emptyList()
    }
  }

  override suspend fun findEntry(id: NodeId): GraphIndexEntry? = withContext(dispatcherProvider.io) {
    if (VaultPolicy.isIndexExcluded(id.value) || VaultPolicy.isReadBlocked(id.value)) {
      return@withContext null
    }
    val target = pathResolver.resolve(vaultRoot, id)
    val readable = pathResolver.assertWithinVault(vaultRoot, target) &&
      Files.exists(target) &&
      Files.isRegularFile(target)
    if (!readable) null else buildOrCacheFromFile(target)
  }

  override suspend fun findEntryByAlias(alias: String): GraphIndexEntry? = withContext(dispatcherProvider.io) {
    val key = alias.trim().lowercase()
    val id = if (key.isEmpty()) null else aliasIndex[key]
    if (id == null) null else findEntry(id)
  }

  override suspend fun findEntryByTitle(title: String): GraphIndexEntry? = withContext(dispatcherProvider.io) {
    val key = title.trim().lowercase()
    val id = if (key.isEmpty()) null else titleIndex[key]
    if (id == null) null else findEntry(id)
  }

  override suspend fun findEntryByPath(path: String): GraphIndexEntry? = withContext(dispatcherProvider.io) {
    val normalized = path.trim()
    val candidates = if (normalized.isEmpty()) {
      emptyList()
    } else {
      listOf(
        normalized,
        normalized.removeSuffix(MARKDOWN_SUFFIX),
        normalized.trim('/'),
        normalized.trim('/').removeSuffix(MARKDOWN_SUFFIX),
      )
    }
    val id = candidates.firstNotNullOfOrNull { pathIndex[it] }
    if (id == null) null else findEntry(id)
  }

  override suspend fun invalidate(id: NodeId) {
    val cached = cache.remove(id)
    if (cached != null) pruneSideIndexes(id)
  }

  override suspend fun invalidateAll() {
    synchronized(sideMapLock) {
      cache.clear()
      branchRootMtime.clear()
      aliasIndex.clear()
      titleIndex.clear()
      pathIndex.clear()
    }
  }

  private fun resolveBranchDirOrNull(normalized: String): Path? {
    val openForReading = normalized.isNotBlank() &&
      !VaultPolicy.isIndexExcluded(normalized) &&
      VaultPolicy.isReadAllowed(normalized)
    if (!openForReading) return null
    val branchDir = vaultRoot.resolve(normalized)
    val resolvable = Files.exists(branchDir) &&
      Files.isDirectory(branchDir) &&
      pathResolver.assertWithinVault(vaultRoot, branchDir)
    return if (resolvable) branchDir else null
  }

  private suspend fun walkBranch(normalized: String, branchDir: Path): List<GraphIndexEntry> {
    val results = mutableListOf<GraphIndexEntry>()
    Files.walk(branchDir, MAX_INDEX_DEPTH).use { stream ->
      val iter = stream.iterator()
      while (iter.hasNext() && results.size < MAX_INDEX_RESULTS) {
        currentCoroutineContext().ensureActive()
        buildOrCacheFromFile(iter.next())
          ?.takeIf { it.branch == normalized || it.branch.startsWith("$normalized/") }
          ?.let(results::add)
      }
    }
    return results
  }

  private fun pruneSideIndexes(id: NodeId) {
    synchronized(sideMapLock) {
      aliasIndex.entries.removeAll { it.value == id }
      titleIndex.entries.removeAll { it.value == id }
      pathIndex.entries.removeAll { it.value == id }
    }
  }

  private fun refreshBranchIfChanged(normalizedBranch: String, branchDir: Path) {
    val currentMtime = readMtimeOrNull(branchDir) ?: return
    val previous = branchRootMtime[normalizedBranch]
    if (previous == null || previous != currentMtime) {
      val toDrop = cache.entries.filter {
        val b = it.value.entry.branch
        b == normalizedBranch || b.startsWith("$normalizedBranch/")
      }
      for (entry in toDrop) {
        cache.remove(entry.key)
        pruneSideIndexes(entry.key)
      }
      branchRootMtime[normalizedBranch] = currentMtime
    }
  }

  private fun readMtimeOrNull(target: Path): Long? = try {
    Files.getLastModifiedTime(target).toMillis()
  } catch (e: IOException) {
    logger.debug(e) { "mtime read failed for target=$target" }
    null
  } catch (e: SecurityException) {
    logger.debug(e) { "mtime read denied for target=$target" }
    null
  }

  private fun buildOrCacheFromFile(file: Path): GraphIndexEntry? {
    val nodeId = eligibleNodeIdOrNull(file) ?: return null
    val stat = statOrNull(file) ?: return null
    val cached = cache[nodeId]
    val hit = cached?.takeIf { it.fileSize == stat.size && it.fileModifiedAtMillis == stat.mtime }
    return hit?.entry ?: decodeAndCache(nodeId, file, stat)
  }

  private fun decodeAndCache(nodeId: NodeId, file: Path, stat: FileStat): GraphIndexEntry? {
    val raw = readRawOrNull(file) ?: return null
    val node = codec.decodePreview(nodeId, raw, BODY_PREVIEW_WORD_LIMIT) ?: return null
    val entry = buildEntry(node, stat.size, stat.mtime)
    cache[nodeId] = CachedEntry(entry, stat.size, stat.mtime)
    refreshSideIndexes(nodeId, entry, file)
    return entry
  }

  private fun eligibleNodeIdOrNull(file: Path): NodeId? {
    val isMarkdownRegularFile = !Files.isSymbolicLink(file) &&
      Files.isRegularFile(file) &&
      file.fileName?.toString().orEmpty().endsWith(MARKDOWN_SUFFIX)
    if (!isMarkdownRegularFile) return null
    return pathResolver.relativize(vaultRoot, file)?.takeIf {
      !VaultPolicy.isIndexExcluded(it.value) &&
        !VaultPolicy.isReadBlocked(it.value) &&
        VaultPolicy.isReadAllowed(it.value)
    }
  }

  private fun statOrNull(file: Path): FileStat? {
    val stat: FileStat? = try {
      FileStat(Files.size(file), Files.getLastModifiedTime(file).toMillis())
    } catch (e: IOException) {
      logger.debug(e) { "stat failed for file=$file" }
      null
    } catch (e: SecurityException) {
      logger.debug(e) { "stat denied for file=$file" }
      null
    }
    return when {
      stat == null -> null
      stat.size > MAX_INDEX_FILE_SIZE_BYTES -> {
        logger.debug { "skipping oversized file=$file size=${stat.size} limit=$MAX_INDEX_FILE_SIZE_BYTES" }
        null
      }
      else -> stat
    }
  }

  private fun readRawOrNull(file: Path): String? = try {
    Files.readString(file)
  } catch (e: IOException) {
    logger.debug(e) { "read failed for file=$file" }
    null
  } catch (e: SecurityException) {
    logger.debug(e) { "read denied for file=$file" }
    null
  }

  private fun buildEntry(node: VaultNode, fileSize: Long, mtime: Long): GraphIndexEntry {
    val filteredLinks = node.links.filterNot { link ->
      VaultPolicy.isIndexExcluded(link.value) || VaultPolicy.isReadBlocked(link.value)
    }
    val previewBody = node.body
    val snippet = previewBody.lineSequence()
      .firstOrNull { it.isNotBlank() }
      .orEmpty()
      .trim()
      .take(MAX_SNIPPET_LENGTH)
    val typeName = when (node) {
      is StateNode -> "state"
      is EpisodeNode -> "episode"
      is PatternNode -> "pattern"
      is SubjectNode -> "subject"
      is EmotionalStateNode -> "emotional-state"
    }
    val branch = node.id.value.split('/').dropLast(1).joinToString("/")
    val category = (node as? StateNode)?.category?.name?.lowercase()
    val domain = when (node) {
      is EpisodeNode -> node.domain
      is SubjectNode -> node.domain
      else -> null
    }
    val scope = (node as? StateNode)?.scope
    val scopes = (node as? StateNode)?.scopes.orEmpty()
    val subject = (node as? SubjectNode)?.subject
    val topic = (node as? EpisodeNode)?.topic
    val aliases = (node as? SubjectNode)?.aliases.orEmpty()
    val hypothesis = (node as? PatternNode)?.hypothesis
    val date = when (node) {
      is EpisodeNode -> node.date
      is EmotionalStateNode -> node.date
      else -> null
    }
    return GraphIndexEntry(
      id = node.id,
      branch = branch,
      type = typeName,
      category = category,
      domain = domain,
      scope = scope,
      scopes = scopes,
      subject = subject,
      topic = topic,
      aliases = aliases,
      hypothesis = hypothesis,
      date = date,
      updated = node.updatedAt,
      created = node.createdAt,
      links = filteredLinks,
      linkCount = filteredLinks.size,
      snippet = snippet,
      bodyTokenEstimate = tokenEstimator.estimateBody(previewBody),
      fileSize = fileSize,
      fileModifiedAt = Instant.fromEpochMilliseconds(mtime),
    )
  }

  private fun refreshSideIndexes(id: NodeId, entry: GraphIndexEntry, file: Path) {
    synchronized(sideMapLock) {
      aliasIndex.entries.removeAll { it.value == id }
      titleIndex.entries.removeAll { it.value == id }
      pathIndex.entries.removeAll { it.value == id }
      for (alias in entry.aliases) {
        val key = alias.trim().lowercase()
        if (key.isNotEmpty()) aliasIndex[key] = id
      }
      val title = entry.subject ?: entry.topic
      if (title != null) {
        val titleKey = title.trim().lowercase()
        if (titleKey.isNotEmpty()) titleIndex[titleKey] = id
      }
      val absolute = file.toAbsolutePath().normalize().toString()
      pathIndex[absolute] = id
      pathIndex[id.value] = id
      pathIndex[id.value + MARKDOWN_SUFFIX] = id
    }
  }

  private data class CachedEntry(
    val entry: GraphIndexEntry,
    val fileSize: Long,
    val fileModifiedAtMillis: Long,
  )

  private data class FileStat(val size: Long, val mtime: Long)

  companion object {
    private const val MARKDOWN_SUFFIX: String = ".md"
    private const val MAX_INDEX_DEPTH: Int = 8
    private const val MAX_INDEX_RESULTS: Int = 1000
    private const val MAX_INDEX_FILE_SIZE_BYTES: Long = 1L * 1024L * 1024L
    private const val BODY_PREVIEW_WORD_LIMIT: Int = 64
    private const val MAX_SNIPPET_LENGTH: Int = 200
  }
}
