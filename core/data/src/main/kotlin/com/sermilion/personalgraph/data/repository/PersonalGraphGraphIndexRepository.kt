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
import com.sermilion.personalgraph.domain.repository.GraphIndexBranchQuery
import com.sermilion.personalgraph.domain.repository.GraphIndexInvalidator
import com.sermilion.personalgraph.domain.repository.GraphIndexRepository
import com.sermilion.personalgraph.domain.tokens.TokenEstimator
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import me.tatarka.inject.annotations.Inject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.PriorityQueue
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
  ): List<GraphIndexEntry> = listEntriesInBranch(branchPath, GraphIndexBranchQuery(limit = MAX_INDEX_RESULTS))

  override suspend fun listEntriesInBranch(
    branchPath: String,
    query: GraphIndexBranchQuery,
  ): List<GraphIndexEntry> = withContext(dispatcherProvider.io) {
    val normalized = branchPath.trim('/')
    if (query.limit <= 0) return@withContext emptyList()
    val branchDir = resolveBranchDirOrNull(normalized) ?: return@withContext emptyList()
    refreshBranchIfChanged(normalized, branchDir)
    try {
      walkBranch(normalized, branchDir, query).sortedBy { it.id.value }
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

  private suspend fun walkBranch(
    normalized: String,
    branchDir: Path,
    query: GraphIndexBranchQuery,
  ): List<GraphIndexEntry> {
    val candidateFiles = if (query.preferredRelativePrefixes.isEmpty()) {
      walkBranchFilesUntilLimit(vaultRoot, pathResolver, normalized, branchDir, query.limit, MAX_INDEX_DEPTH)
    } else {
      topBranchFilesByPathPriority(
        vaultRoot = vaultRoot,
        pathResolver = pathResolver,
        normalized = normalized,
        branchDir = branchDir,
        query = query,
        maxDepth = MAX_INDEX_DEPTH,
        maxIndexResults = MAX_INDEX_RESULTS,
      )
    }
    val results = mutableListOf<GraphIndexEntry>()
    for (file in candidateFiles) {
      currentCoroutineContext().ensureActive()
      if (results.size >= query.limit) break
      buildOrCacheFromFile(file)
        ?.takeIf { it.branch == normalized || it.branch.startsWith("$normalized/") }
        ?.let(results::add)
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
    val currentMtime = readMtimeOrNull(branchDir, logger) ?: return
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
    val nodeId = eligibleNodeIdUnder(vaultRoot, pathResolver, file, "")
    return nodeId?.takeIf { it.value.isNotBlank() }
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
    val snippet = firstMeaningfulPreviewLine(previewBody).take(MAX_SNIPPET_LENGTH)
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
    private const val MAX_INDEX_DEPTH: Int = 8
    private const val MAX_INDEX_RESULTS: Int = 1000
    private const val MAX_INDEX_FILE_SIZE_BYTES: Long = 1L * 1024L * 1024L
    private const val BODY_PREVIEW_WORD_LIMIT: Int = 64
    private const val MAX_SNIPPET_LENGTH: Int = 200
  }
}

private suspend fun walkBranchFilesUntilLimit(
  vaultRoot: Path,
  pathResolver: VaultPathResolver,
  normalized: String,
  branchDir: Path,
  limit: Int,
  maxDepth: Int,
): List<Path> {
  val results = mutableListOf<Path>()
  Files.walk(branchDir, maxDepth).use { stream ->
    val iter = stream.iterator()
    while (iter.hasNext() && results.size < limit) {
      currentCoroutineContext().ensureActive()
      val file = iter.next()
      if (isEligibleMarkdownFileUnder(vaultRoot, pathResolver, file, normalized)) results.add(file)
    }
  }
  return results
}

private suspend fun topBranchFilesByPathPriority(
  vaultRoot: Path,
  pathResolver: VaultPathResolver,
  normalized: String,
  branchDir: Path,
  query: GraphIndexBranchQuery,
  maxDepth: Int,
  maxIndexResults: Int,
): List<Path> {
  val candidateLimit = (query.limit * PATH_CANDIDATE_MULTIPLIER)
    .coerceAtLeast(query.limit)
    .coerceAtMost(maxIndexResults)
  val selected = PriorityQueue<RankedFile>(WORST_RANKED_FILE_FIRST)
  Files.walk(branchDir, maxDepth).use { stream ->
    val iter = stream.iterator()
    while (iter.hasNext()) {
      currentCoroutineContext().ensureActive()
      val file = iter.next()
      val id = eligibleNodeIdUnder(vaultRoot, pathResolver, file, normalized) ?: continue
      val candidate = RankedFile(
        file = file,
        id = id.value,
        priority = id.relativePriority(normalized, query.preferredRelativePrefixes),
      )
      if (selected.size < candidateLimit) {
        selected.add(candidate)
      } else if (candidate.isBetterThan(selected.peek())) {
        selected.poll()
        selected.add(candidate)
      }
    }
  }
  return selected.sortedWith(BEST_RANKED_FILE_FIRST).map { it.file }
}

private fun isEligibleMarkdownFileUnder(
  vaultRoot: Path,
  pathResolver: VaultPathResolver,
  file: Path,
  normalized: String,
): Boolean = eligibleNodeIdUnder(vaultRoot, pathResolver, file, normalized) != null

private fun eligibleNodeIdUnder(
  vaultRoot: Path,
  pathResolver: VaultPathResolver,
  file: Path,
  normalized: String,
): NodeId? {
  val isMarkdownRegularFile = !Files.isSymbolicLink(file) &&
    Files.isRegularFile(file) &&
    file.fileName?.toString().orEmpty().endsWith(MARKDOWN_SUFFIX)
  if (!isMarkdownRegularFile) return null
  return pathResolver.relativize(vaultRoot, file)?.takeIf { id ->
    (normalized.isBlank() || id.value == normalized || id.value.startsWith("$normalized/")) &&
      !VaultPolicy.isIndexExcluded(id.value) &&
      !VaultPolicy.isReadBlocked(id.value) &&
      VaultPolicy.isReadAllowed(id.value)
  }
}

private fun NodeId.relativePriority(
  normalized: String,
  preferredRelativePrefixes: List<String>,
): Int {
  val relative = value.removePrefix(normalized).trimStart('/')
  val preferredIndex = preferredRelativePrefixes.indexOfFirst { prefix ->
    relative == prefix || relative.startsWith("$prefix/")
  }
  return if (preferredIndex >= 0) preferredIndex else preferredRelativePrefixes.size
}

private data class RankedFile(
  val file: Path,
  val id: String,
  val priority: Int,
) {
  fun isBetterThan(other: RankedFile): Boolean = hasBetterPriorityThan(other) || hasSamePriorityAndLowerIdThan(other)

  private fun hasBetterPriorityThan(other: RankedFile): Boolean = priority < other.priority

  private fun hasSamePriorityAndLowerIdThan(other: RankedFile): Boolean = priority == other.priority && id < other.id
}

private const val PATH_CANDIDATE_MULTIPLIER: Int = 4
private const val MARKDOWN_SUFFIX: String = ".md"

private val BEST_RANKED_FILE_FIRST: Comparator<RankedFile> =
  compareBy<RankedFile> { it.priority }.thenBy { it.id }

private val WORST_RANKED_FILE_FIRST: Comparator<RankedFile> =
  compareByDescending<RankedFile> { it.priority }.thenByDescending { it.id }

private fun readMtimeOrNull(target: Path, logger: KLogger): Long? = try {
  Files.getLastModifiedTime(target).toMillis()
} catch (e: IOException) {
  logger.debug(e) { "mtime read failed for target=$target" }
  null
} catch (e: SecurityException) {
  logger.debug(e) { "mtime read denied for target=$target" }
  null
}

private fun firstMeaningfulPreviewLine(body: String): String = body.lineSequence()
  .map { it.trim() }
  .filter { it.isNotBlank() }
  .firstOrNull { !it.startsWith("#") }
  .orEmpty()
