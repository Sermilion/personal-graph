package com.sermilion.personalgraph.data.retrieval

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.common.dispatcher.DispatcherProvider
import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.EmotionalStateNode
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.retrieval.CompactMapEntry
import com.sermilion.personalgraph.domain.retrieval.CompactMapEntryKind
import com.sermilion.personalgraph.domain.retrieval.FullBodyContextSource
import com.sermilion.personalgraph.domain.retrieval.LoadedFullBodyContext
import com.sermilion.personalgraph.domain.retrieval.RetrievalAuditEntry
import com.sermilion.personalgraph.domain.retrieval.RetrievalClassification
import com.sermilion.personalgraph.domain.retrieval.RetrievalDomain
import com.sermilion.personalgraph.domain.retrieval.RetrievedBranch
import com.sermilion.personalgraph.domain.retrieval.RetrievedNode
import com.sermilion.personalgraph.domain.retrieval.RetrievedRootDocument
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalMode
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalReport
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalRequest
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalService
import com.sermilion.personalgraph.domain.retrieval.SkippedBranch
import com.sermilion.personalgraph.domain.retrieval.SuggestedRead
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@AppScope
@Inject
class PersonalGraphSessionStartRetrievalService(
  private val vaultRoot: Path,
  private val repository: VaultRepository,
  private val pathResolver: VaultPathResolver,
  private val dispatcherProvider: DispatcherProvider,
) : SessionStartRetrievalService {

  private val logger = KotlinLogging.logger {}

  override suspend fun retrieve(
    request: SessionStartRetrievalRequest,
  ): SessionStartRetrievalReport = withContext(dispatcherProvider.io) {
    val audit = mutableListOf<RetrievalAuditEntry>()
    val loadedBranches = mutableListOf<RetrievedBranch>()
    val skippedBranches = mutableListOf<SkippedBranch>()

    val classification = classify(request.firstSubstantiveMessage)
    audit.add(
      RetrievalAuditEntry(
        action = "classified",
        subject = classification.domain.value,
        reason = classificationReason(classification),
      ),
    )

    val rootDocument = loadBraian(ROOT_LOAD_ORDER, request.retrievalMode, audit)
    val branchPlan = branchPlanFor(classification)
    addDefaultSkips(classification, skippedBranches, audit)

    val branchContext = loadBranchesAndPatterns(
      branchPlan = branchPlan,
      classification = classification,
      retrievalMode = request.retrievalMode,
      initialLoadOrder = ROOT_LOAD_ORDER,
      loadedBranches = loadedBranches,
      skippedBranches = skippedBranches,
      audit = audit,
    )
    val reportBuilder = MapFirstReportBuilder()
    val nodeMapEntries = reportBuilder.nodeMapEntries(branchContext.mapNodes)
    val compactMapEntries = reportBuilder.compactMapEntries(
      loadedBranches = loadedBranches,
      nodeEntries = nodeMapEntries,
    )
    val suggestedReads = reportBuilder.suggestedReads(
      nodes = branchContext.mapNodes,
      nodeEntries = nodeMapEntries.associateBy { it.id },
      classification = classification,
      message = request.firstSubstantiveMessage,
      audit = audit,
    )
    audit.add(fullBodyBudgetAudit(rootDocument, request.retrievalMode))

    SessionStartRetrievalReport(
      rootDocument = rootDocument,
      classification = classification,
      loadedBranches = loadedBranches,
      loadedNodes = branchContext.loadedNodes,
      skippedBranches = skippedBranches.distinctBy { it.branch },
      audit = audit,
      loadedFullBodyContext = loadedFullBodyContext(
        rootDocument = rootDocument,
        loadedNodes = branchContext.loadedNodes,
        retrievalMode = request.retrievalMode,
      ),
      compactMapEntries = compactMapEntries,
      suggestedReads = suggestedReads,
      auditEntries = audit,
    )
  }

  private suspend fun loadBranchesAndPatterns(
    branchPlan: List<Pair<String, String>>,
    classification: RetrievalClassification,
    retrievalMode: SessionStartRetrievalMode,
    initialLoadOrder: Int,
    loadedBranches: MutableList<RetrievedBranch>,
    skippedBranches: MutableList<SkippedBranch>,
    audit: MutableList<RetrievalAuditEntry>,
  ): BranchRetrievalContext {
    val loadedNodes = mutableListOf<RetrievedNode>()
    val mapNodes = mutableListOf<VaultNode>()
    val seedNodes = mutableListOf<VaultNode>()
    var loadOrder = initialLoadOrder

    for ((branch, reason) in branchPlan) {
      val nodes = loadBranch(
        branch = branch,
        reason = reason,
        classification = classification,
        retrievalMode = retrievalMode,
        loadedBranches = loadedBranches,
        skippedBranches = skippedBranches,
        audit = audit,
      )
      seedNodes.addAll(nodes)
      mapNodes.addAll(nodes)
      if (retrievalMode == SessionStartRetrievalMode.FullLoading) {
        for (node in nodes.sortedBy { it.id.value }) {
          loadedNodes.add(node.toRetrievedNode(++loadOrder, reason))
        }
      }
    }

    val patternNodes = loadLinkedPatterns(seedNodes, audit)
    mapNodes.addAll(patternNodes)
    if (retrievalMode == SessionStartRetrievalMode.FullLoading) {
      for (node in patternNodes.sortedBy { it.id.value }) {
        loadedNodes.add(
          node.toRetrievedNode(
            loadOrder = ++loadOrder,
            reason = "wikilinked pattern hub from loaded retrieval context",
          ),
        )
      }
    }
    return BranchRetrievalContext(
      loadedNodes = loadedNodes,
      mapNodes = mapNodes.distinctBy { it.id.value },
    )
  }

  private fun classify(message: String): RetrievalClassification {
    val capmoMatches = matchedTerms(message, WORK_CAPMO_TERMS)
    val skillBillMatches = matchedTerms(message, WORK_SKILL_BILL_TERMS)
    val readianMatches = matchedTerms(message, WORK_READIAN_TERMS)
    val contextAppMatches = matchedTerms(message, WORK_CONTEXT_APP_TERMS)
    val personalMatches = matchedTerms(message, PERSONAL_TERMS)
    val creativeMusicMatches = matchedTerms(message, CREATIVE_MUSIC_TERMS)
    val emotionalMatches = matchedTerms(message, EMOTIONAL_TERMS)
    val candidates = listOf(
      RetrievalDomain.WorkCapmo to capmoMatches,
      RetrievalDomain.WorkSkillBill to skillBillMatches,
      RetrievalDomain.WorkReadian to readianMatches,
      RetrievalDomain.WorkContextApp to contextAppMatches,
      RetrievalDomain.Personal to personalMatches,
      RetrievalDomain.CreativeMusic to creativeMusicMatches,
    )
    val bestDomain = candidates
      .filter { it.second.isNotEmpty() }
      .maxByOrNull { it.second.size }
      ?.first
    val domain = bestDomain ?: RetrievalDomain.General
    return RetrievalClassification(
      domain = domain,
      matchedTerms = when (domain) {
        RetrievalDomain.WorkCapmo -> capmoMatches
        RetrievalDomain.WorkSkillBill -> skillBillMatches
        RetrievalDomain.WorkReadian -> readianMatches
        RetrievalDomain.WorkContextApp -> contextAppMatches
        RetrievalDomain.Personal -> personalMatches
        RetrievalDomain.CreativeMusic -> creativeMusicMatches
        RetrievalDomain.General -> emptyList()
      },
      emotionalContextRequested = emotionalMatches.isNotEmpty(),
      emotionalMatchedTerms = emotionalMatches,
    )
  }

  private fun matchedTerms(message: String, terms: List<String>): List<String> = terms
    .filter { term -> containsTerm(message, term) }

  private fun containsTerm(message: String, term: String): Boolean {
    val escaped = Regex.escape(term)
    return Regex("""(?i)(?<![a-z0-9_-])$escaped(?![a-z0-9_-])""").containsMatchIn(message)
  }

  private fun classificationReason(classification: RetrievalClassification): String {
    val domainReason = if (classification.matchedTerms.isEmpty()) {
      "no domain-specific terms matched; using durable general context"
    } else {
      "matched terms: ${classification.matchedTerms.joinToString(",")}"
    }
    val emotionalReason = if (classification.emotionalContextRequested) {
      "; emotional context requested by: ${classification.emotionalMatchedTerms.joinToString(",")}"
    } else {
      "; emotional-states skipped because no emotional/self-reflection term matched"
    }
    return domainReason + emotionalReason
  }

  private fun branchPlanFor(classification: RetrievalClassification): List<Pair<String, String>> {
    val knowledgeBranch = if (classification.domain == RetrievalDomain.General) {
      listOf(VaultLayout.BRANCH_STATE_KNOWLEDGE to REASON_KNOWLEDGE_GENERAL)
    } else {
      emptyList()
    }
    val domainBranches = when (classification.domain) {
      RetrievalDomain.WorkCapmo -> listOf(
        "${VaultLayout.BRANCH_DOMAINS}/work/capmo" to REASON_DOMAIN_WORK_CAPMO,
      )
      RetrievalDomain.WorkSkillBill -> listOf(
        "${VaultLayout.BRANCH_DOMAINS}/work/skill-bill" to REASON_DOMAIN_WORK_SKILL_BILL,
      )
      RetrievalDomain.WorkReadian -> listOf(
        "${VaultLayout.BRANCH_DOMAINS}/work/readian" to REASON_DOMAIN_WORK_READIAN,
      )
      RetrievalDomain.WorkContextApp -> listOf(
        "${VaultLayout.BRANCH_DOMAINS}/work/context-app" to REASON_DOMAIN_WORK_CONTEXT_APP,
      )
      RetrievalDomain.Personal -> listOf(
        "${VaultLayout.BRANCH_DOMAINS}/personal" to REASON_DOMAIN_PERSONAL,
      )
      RetrievalDomain.CreativeMusic -> listOf(
        "${VaultLayout.BRANCH_DOMAINS}/creative/music" to REASON_DOMAIN_CREATIVE_MUSIC,
      )
      RetrievalDomain.General -> emptyList()
    }
    val emotionalBranch = if (classification.emotionalContextRequested) {
      listOf(VaultLayout.BRANCH_EMOTIONAL_STATES to REASON_EMOTIONAL_REQUESTED)
    } else {
      emptyList()
    }
    return DURABLE_STATE_BRANCHES_ALWAYS + knowledgeBranch + domainBranches + emotionalBranch
  }

  private fun addDefaultSkips(
    classification: RetrievalClassification,
    skippedBranches: MutableList<SkippedBranch>,
    audit: MutableList<RetrievalAuditEntry>,
  ) {
    listOf(
      VaultLayout.BRANCH_STAGING to "retrieval skips staging by default, including staging/sensitive/",
      VaultLayout.BRANCH_PEOPLE to "people/ is never loaded by session-start retrieval",
    ).forEach { (branch, reason) -> skip(branch, reason, skippedBranches, audit) }
    if (!classification.emotionalContextRequested) {
      skip(
        VaultLayout.BRANCH_EMOTIONAL_STATES,
        "no explicit emotional or self-reflection context in first substantive message",
        skippedBranches,
        audit,
      )
    }
  }

  private fun loadBraian(
    loadOrder: Int,
    retrievalMode: SessionStartRetrievalMode,
    audit: MutableList<RetrievalAuditEntry>,
  ): RetrievedRootDocument? {
    val target = vaultRoot.resolve(VaultLayout.BRAIAN_FILENAME)
    return try {
      when {
        !pathResolver.assertWithinVault(vaultRoot, target) -> {
          audit.add(rejectedAudit(VaultLayout.BRAIAN_FILENAME, "path is outside the vault root or crosses a symlink"))
          null
        }
        Files.isSymbolicLink(target) -> {
          audit.add(rejectedAudit(VaultLayout.BRAIAN_FILENAME, "root note is a symlink"))
          null
        }
        !Files.isRegularFile(target) -> {
          audit.add(
            RetrievalAuditEntry(
              action = "missing",
              subject = VaultLayout.BRAIAN_FILENAME,
              reason = "root orienting note does not exist",
            ),
          )
          null
        }
        else -> {
          audit.add(
            RetrievalAuditEntry(
              action = "loaded",
              subject = VaultLayout.BRAIAN_FILENAME,
              reason = "root orienting note is always loaded first",
            ),
          )
          RetrievedRootDocument(
            path = VaultLayout.BRAIAN_FILENAME,
            body = Files.readString(target).let { body ->
              if (retrievalMode == SessionStartRetrievalMode.FullLoading) {
                body
              } else {
                body.limitWords(DEFAULT_FULL_BODY_WORD_BUDGET_WORDS)
              }
            },
            loadOrder = loadOrder,
            reason = "root orienting note is always loaded first",
          )
        }
      }
    } catch (e: IOException) {
      logger.warn(e) { "Failed to load ${VaultLayout.BRAIAN_FILENAME}" }
      audit.add(rejectedAudit(VaultLayout.BRAIAN_FILENAME, e.reasonString()))
      null
    } catch (e: SecurityException) {
      logger.warn(e) { "Permission denied loading ${VaultLayout.BRAIAN_FILENAME}" }
      audit.add(rejectedAudit(VaultLayout.BRAIAN_FILENAME, e.reasonString()))
      null
    }
  }

  private suspend fun loadBranch(
    branch: String,
    reason: String,
    classification: RetrievalClassification,
    retrievalMode: SessionStartRetrievalMode,
    loadedBranches: MutableList<RetrievedBranch>,
    skippedBranches: MutableList<SkippedBranch>,
    audit: MutableList<RetrievalAuditEntry>,
  ): List<VaultNode> {
    val candidate = vaultRoot.resolve(branch)
    if (!pathResolver.assertWithinVault(vaultRoot, candidate)) {
      skip(branch, "branch is outside the vault root or crosses a symlink", skippedBranches, audit)
      return emptyList()
    }
    val nodes = if (retrievalMode == SessionStartRetrievalMode.FullLoading) {
      repository.listNodesInBranch(branch)
    } else {
      repository.listMapNodesInBranch(branch, MAP_NODE_BODY_WORD_BUDGET_WORDS)
    }
      .filter { it.isVisibleInStateBranch(branch, classification.domain) }
      .sortedBy { it.id.value }
    loadedBranches.add(RetrievedBranch(branch = branch, reason = reason, nodeCount = nodes.size))
    if (retrievalMode == SessionStartRetrievalMode.FullLoading) {
      audit.add(
        RetrievalAuditEntry(
          action = "loaded_branch",
          subject = branch,
          reason = "$reason; nodes=${nodes.size}",
        ),
      )
    } else {
      audit.add(
        RetrievalAuditEntry(
          action = "skipped_full_branch",
          subject = branch,
          reason = "map-first default exposes compact map entries instead of loading full branch bodies; " +
            "$reason; nodes=${nodes.size}",
        ),
      )
    }
    return nodes
  }

  private suspend fun loadLinkedPatterns(
    seedNodes: List<VaultNode>,
    audit: MutableList<RetrievalAuditEntry>,
  ): List<PatternNode> {
    val loaded = linkedPatternSeeds(seedNodes).toMutableList()
    val loadedIds = mutableSetOf<String>()
    val results = mutableListOf<PatternNode>()
    while (loaded.isNotEmpty() && results.size < MAX_PATTERN_RESULTS) {
      val link = loaded.removeAt(0)
      val alreadySeen = !loadedIds.add(link.value)
      if (!alreadySeen) {
        val node = repository.findNode(link)
        if (node is PatternNode) {
          results.add(node)
          audit.add(
            RetrievalAuditEntry(
              action = "loaded_pattern",
              subject = node.id.value,
              reason = "wikilinked from loaded retrieval context",
            ),
          )
          enqueueLinkedPatterns(node, loadedIds, loaded)
        } else {
          audit.add(
            RetrievalAuditEntry(
              action = "skipped_pattern",
              subject = link.value,
              reason = "linked pattern hub was not found or was not a pattern node",
            ),
          )
        }
      }
    }
    return results
  }

  private fun enqueueLinkedPatterns(
    node: PatternNode,
    loadedIds: Set<String>,
    queue: MutableList<NodeId>,
  ) {
    for (next in node.patternLinks()) {
      if (!loadedIds.contains(next.value) && queue.none { it.value == next.value }) {
        queue.add(next)
      }
    }
  }

  private fun linkedPatternSeeds(nodes: List<VaultNode>): List<NodeId> = nodes
    .flatMap { it.patternLinks() }
    .distinctBy { it.value }
    .sortedBy { it.value }

  private fun VaultNode.patternLinks(): List<NodeId> = when (this) {
    is StateNode -> links + patternLinks
    is EpisodeNode -> links + patternLinks
    is PatternNode -> links + patternLinks
    is SubjectNode -> links + patternLinks
    is EmotionalStateNode -> links + patternLinks
  }
    .filter { it.value.startsWith("${VaultLayout.BRANCH_PATTERNS}/") }
    .distinctBy { it.value }

  private fun VaultNode.toRetrievedNode(loadOrder: Int, reason: String): RetrievedNode = RetrievedNode(
    id = id.value,
    body = body,
    links = links.map { it.value },
    patternLinks = directPatternLinks().map { it.value },
    loadOrder = loadOrder,
    reason = reason,
  )

  private fun skip(
    branch: String,
    reason: String,
    skippedBranches: MutableList<SkippedBranch>,
    audit: MutableList<RetrievalAuditEntry>,
  ) {
    skippedBranches.add(SkippedBranch(branch = branch, reason = reason))
    audit.add(RetrievalAuditEntry(action = "skipped_branch", subject = branch, reason = reason))
  }

  private fun rejectedAudit(subject: String, reason: String): RetrievalAuditEntry = RetrievalAuditEntry(
    action = "rejected",
    subject = subject,
    reason = reason,
  )

  private fun Throwable.reasonString(): String = "${this::class.simpleName}: ${this.message.orEmpty()}"

  companion object {
    private const val MAX_PATTERN_RESULTS: Int = 64
    private const val ROOT_LOAD_ORDER: Int = 1
    private const val MAP_NODE_BODY_WORD_BUDGET_WORDS: Int = 80

    private const val REASON_PREFERENCES_ALWAYS: String =
      "preferences are always loaded regardless of classification"
    private const val REASON_ROLES_ALWAYS: String =
      "roles are always loaded regardless of classification"
    private const val REASON_KNOWLEDGE_GENERAL: String =
      "general classification loads durable knowledge branch"
    private const val REASON_DOMAIN_WORK_CAPMO: String =
      "classified work/capmo from first substantive message"
    private const val REASON_DOMAIN_WORK_SKILL_BILL: String =
      "classified work/skill-bill from first substantive message"
    private const val REASON_DOMAIN_WORK_READIAN: String =
      "classified work/readian from first substantive message"
    private const val REASON_DOMAIN_WORK_CONTEXT_APP: String =
      "classified work/context-app from first substantive message"
    private const val REASON_DOMAIN_PERSONAL: String =
      "classified personal from first substantive message"
    private const val REASON_DOMAIN_CREATIVE_MUSIC: String =
      "classified creative/music from first substantive message"
    private const val REASON_EMOTIONAL_REQUESTED: String =
      "explicit emotional/self-reflection context"

    private val DURABLE_STATE_BRANCHES_ALWAYS: List<Pair<String, String>> = listOf(
      VaultLayout.BRANCH_STATE_PREFERENCES to REASON_PREFERENCES_ALWAYS,
      VaultLayout.BRANCH_STATE_ROLES to REASON_ROLES_ALWAYS,
    )

    private val WORK_CAPMO_TERMS: List<String> = listOf(
      "capmo",
    )

    private val WORK_SKILL_BILL_TERMS: List<String> = listOf(
      "skill-bill",
      "skill bill",
      "skillbill",
      "skill",
      "skills",
      "agent workflow",
    )

    private val WORK_READIAN_TERMS: List<String> = listOf(
      "readian",
      "editorial",
      "assignment desk",
      "article",
      "articles",
      "news",
    )

    private val WORK_CONTEXT_APP_TERMS: List<String> = listOf(
      "context-app",
      "context app",
      "context",
      "shelf",
      "desktop app",
      "macos app",
    )

    private val PERSONAL_TERMS: List<String> = listOf(
      "family",
      "health",
      "habit",
      "finances",
      "purchase",
    )

    private val CREATIVE_MUSIC_TERMS: List<String> = listOf(
      "creative",
      "writing",
      "story",
      "music",
      "art",
      "design",
      "song",
      "audio",
      "recording",
      "mixdown",
      "bass",
      "drums",
      "guitar",
      "track",
      "arrangement",
      "mp3",
      "studio",
      "compose",
      "paint",
      "draw",
      "sketch",
      "band",
      "instrument",
    )

    private val EMOTIONAL_TERMS: List<String> = listOf(
      "emotion",
      "anxious",
      "anxiety",
      "frustrated",
      "frustration",
      "excited",
      "curiosity",
      "self-reflection",
      "reflection",
      "mood",
      "feeling",
      "feelings",
    )
  }
}

private data class BranchRetrievalContext(
  val loadedNodes: List<RetrievedNode>,
  val mapNodes: List<VaultNode>,
)

private class MapFirstReportBuilder {

  fun nodeMapEntries(nodes: List<VaultNode>): List<CompactMapEntry> {
    val backlinkCounts = nodes.backlinkCounts()
    return nodes
      .sortedWith(mapNodeOrdering())
      .map { node -> node.toCompactMapEntry(backlinkCounts) }
  }

  fun compactMapEntries(
    loadedBranches: List<RetrievedBranch>,
    nodeEntries: List<CompactMapEntry>,
  ): List<CompactMapEntry> {
    val entries = mutableListOf<CompactMapEntry>()
    for (branch in loadedBranches) {
      entries.add(branch.toCompactMapEntry())
    }
    entries.addAll(nodeEntries)
    return entries
  }

  fun suggestedReads(
    nodes: List<VaultNode>,
    nodeEntries: Map<String, CompactMapEntry>,
    classification: RetrievalClassification,
    message: String,
    audit: MutableList<RetrievalAuditEntry>,
  ): List<SuggestedRead> {
    val recentEvidenceUseful = message.requestsRecentEvidence()
    val suggestions = nodes
      .filter { it.isSuggestionEligible(classification.domain) }
      .map { node ->
        RankedSuggestion(
          node = node,
          score = node.suggestionScore(recentEvidenceUseful, classification),
          reason = node.suggestionReason(recentEvidenceUseful, classification),
        )
      }
      .sortedWith(
        compareByDescending<RankedSuggestion> { it.score }
          .thenByDescending { it.node.updatedAt }
          .thenBy { it.node.id.value },
      )
      .take(MAX_SUGGESTED_READS)

    for (suggestion in suggestions) {
      audit.add(
        RetrievalAuditEntry(
          action = "suggested_read",
          subject = suggestion.node.id.value,
          reason = suggestion.reason,
        ),
      )
    }
    val result = mutableListOf<SuggestedRead>()
    for (suggestion in suggestions) {
      val entry = nodeEntries.getValue(suggestion.node.id.value)
      result.add(suggestion.toSuggestedRead(entry))
    }
    return result
  }

  private fun List<VaultNode>.backlinkCounts(): Map<String, Int> = buildMap {
    for (node in this@backlinkCounts) {
      for (link in node.links + node.directPatternLinks()) {
        put(link.value, getOrDefault(link.value, 0) + 1)
      }
    }
  }

  private fun VaultNode.toCompactMapEntry(backlinkCounts: Map<String, Int>): CompactMapEntry {
    val directPatternLinks = directPatternLinks().map { it.value }
    return CompactMapEntry(
      id = id.value,
      kind = CompactMapEntryKind.Node,
      reason = mapEntryReason(),
      domain = mapDomain(),
      category = mapCategory(),
      scope = (this as? StateNode)?.scope,
      scopes = (this as? StateNode)?.scopes.orEmpty(),
      createdAt = createdAt.toString(),
      updatedAt = updatedAt.toString(),
      date = mapDate(),
      summary = summaryText(),
      excerpt = body.firstMeaningfulLine().limitWords(MAX_EXCERPT_WORDS),
      aliases = (this as? SubjectNode)?.aliases.orEmpty(),
      terms = mapTerms(),
      links = links.map { it.value },
      patternLinks = directPatternLinks,
      backlinkCount = backlinkCounts[id.value] ?: 0,
    )
  }

  private fun RetrievedBranch.toCompactMapEntry(): CompactMapEntry = CompactMapEntry(
    id = branch,
    kind = CompactMapEntryKind.Branch,
    reason = reason,
    nodeCount = nodeCount,
    domain = branchDomain(),
    category = branchCategory(),
    summary = reason,
    excerpt = reason,
  )

  private fun RetrievedBranch.branchDomain(): String? = branch
    .removePrefix("${VaultLayout.BRANCH_DOMAINS}/")
    .takeIf { branch.startsWith("${VaultLayout.BRANCH_DOMAINS}/") }

  private fun RetrievedBranch.branchCategory(): String? = when (branch) {
    VaultLayout.BRANCH_STATE_PREFERENCES -> "preference"
    VaultLayout.BRANCH_STATE_ROLES -> "role"
    VaultLayout.BRANCH_STATE_KNOWLEDGE -> "knowledge"
    VaultLayout.BRANCH_EMOTIONAL_STATES -> "emotional-state"
    else -> null
  }

  private fun mapNodeOrdering(): Comparator<VaultNode> = compareByDescending<VaultNode> {
    when (it) {
      is SubjectNode -> SUBJECT_MAP_ORDER
      is StateNode -> STATE_MAP_ORDER
      is PatternNode -> PATTERN_MAP_ORDER
      is EpisodeNode -> EPISODE_MAP_ORDER
      is EmotionalStateNode -> EMOTIONAL_MAP_ORDER
    }
  }.thenBy { it.id.value }

  private fun VaultNode.mapEntryReason(): String = when (this) {
    is PatternNode -> "available pattern hub linked from map-first retrieval context"
    else -> "available as compact map entry from ${id.value.branchPath()} without loading full body"
  }

  private fun VaultNode.mapDomain(): String? = when (this) {
    is EpisodeNode -> domain
    is PatternNode -> domainsSeenIn.firstOrNull()
    is SubjectNode -> domain
    is StateNode,
    is EmotionalStateNode,
    -> null
  }

  private fun VaultNode.mapCategory(): String? = when (this) {
    is StateNode -> category.wireValue()
    is EpisodeNode -> episodeType.wireValue()
    is PatternNode -> "pattern"
    is SubjectNode -> "subject"
    is EmotionalStateNode -> "emotional-state:${marker.name.lowercase()}"
  }

  private fun VaultNode.mapDate(): String? = when (this) {
    is EpisodeNode -> date.toString()
    is EmotionalStateNode -> date.toString()
    is PatternNode -> lastObserved.toString()
    is StateNode,
    is SubjectNode,
    -> null
  }

  private fun VaultNode.summaryText(): String = when (this) {
    is StateNode -> body.firstMeaningfulLine()
    is EpisodeNode -> listOf(topic, body.firstMeaningfulLine())
      .filter { it.isNotBlank() }
      .joinToString(" - ")
    is PatternNode -> hypothesis.ifBlank { body.firstMeaningfulLine() }
    is SubjectNode -> body.summarySection().ifBlank { body.firstMeaningfulLine() }
    is EmotionalStateNode -> context.ifBlank { body.firstMeaningfulLine() }
  }.limitWords(MAX_SUMMARY_WORDS)

  private fun VaultNode.mapTerms(): List<String> = when (this) {
    is StateNode -> listOfNotNull(scope) + scopes
    is EpisodeNode -> listOf(domain, episodeType.wireValue(), topic)
    is PatternNode -> domainsSeenIn
    is SubjectNode -> listOf(domain, subject)
    is EmotionalStateNode -> listOf(marker.name.lowercase(), intensity.name.lowercase())
  }.filter { it.isNotBlank() }.distinct()

  private fun VaultNode.isSuggestionEligible(domain: RetrievalDomain): Boolean = when (this) {
    is StateNode -> isVisibleForRetrievalDomain(domain)
    is EmotionalStateNode -> false
    else -> true
  }

  private fun VaultNode.suggestionScore(
    recentEvidenceUseful: Boolean,
    classification: RetrievalClassification,
  ): Int {
    val domainBonus = if (mapDomain() == classification.domain.value) DOMAIN_SUGGESTION_BONUS else 0
    return when (this) {
      is SubjectNode -> SUBJECT_SUGGESTION_SCORE + evidenceCount + domainBonus
      is EpisodeNode -> if (recentEvidenceUseful) {
        RECENT_EVENT_SUGGESTION_SCORE + domainBonus
      } else {
        EVENT_SUGGESTION_SCORE + domainBonus
      }
      is StateNode -> STATE_SUGGESTION_SCORE + domainBonus
      is PatternNode -> PATTERN_SUGGESTION_SCORE + evidenceCount + domainBonus
      is EmotionalStateNode -> EMOTIONAL_SUGGESTION_SCORE
    }
  }

  private fun VaultNode.suggestionReason(
    recentEvidenceUseful: Boolean,
    classification: RetrievalClassification,
  ): String = when (this) {
    is SubjectNode -> "subject hub ranks ahead of raw event nodes for ${classification.domain.value} orientation"
    is EpisodeNode -> if (recentEvidenceUseful) {
      "recent event evidence requested by message; event can be read on demand"
    } else {
      "event evidence is available on demand after reusable hubs and state"
    }
    is StateNode -> if (scope == null && scopes.isEmpty()) {
      "global scoped state is relevant across retrieval domains"
    } else {
      "scoped state matches classified domain ${classification.domain.value}"
    }
    is PatternNode -> "linked pattern hub is available as reusable follow-up context"
    is EmotionalStateNode -> "emotional-state nodes are not suggested by default"
  }

  private fun RankedSuggestion.toSuggestedRead(
    entry: CompactMapEntry,
  ): SuggestedRead = SuggestedRead(
    id = entry.id,
    reason = reason,
    kind = entry.kind,
    domain = entry.domain,
    category = entry.category,
    scope = entry.scope,
    scopes = entry.scopes,
    createdAt = entry.createdAt,
    updatedAt = entry.updatedAt,
    date = entry.date,
    summary = entry.summary,
    excerpt = entry.excerpt,
    aliases = entry.aliases,
    terms = entry.terms,
    links = entry.links,
    patternLinks = entry.patternLinks,
    backlinkCount = entry.backlinkCount,
  )

  private companion object {
    const val MAX_SUGGESTED_READS: Int = 8
    const val MAX_SUMMARY_WORDS: Int = 32
    const val MAX_EXCERPT_WORDS: Int = 24
    const val SUBJECT_MAP_ORDER: Int = 4
    const val STATE_MAP_ORDER: Int = 3
    const val PATTERN_MAP_ORDER: Int = 2
    const val EPISODE_MAP_ORDER: Int = 1
    const val EMOTIONAL_MAP_ORDER: Int = 0
    const val DOMAIN_SUGGESTION_BONUS: Int = 50
    const val SUBJECT_SUGGESTION_SCORE: Int = 1_000
    const val RECENT_EVENT_SUGGESTION_SCORE: Int = 1_100
    const val STATE_SUGGESTION_SCORE: Int = 800
    const val PATTERN_SUGGESTION_SCORE: Int = 700
    const val EVENT_SUGGESTION_SCORE: Int = 400
    const val EMOTIONAL_SUGGESTION_SCORE: Int = 0
  }
}

private fun VaultNode.isVisibleInStateBranch(
  branch: String,
  domain: RetrievalDomain,
): Boolean = !branch.startsWith("${VaultLayout.BRANCH_STATE}/") ||
  (this as? StateNode)?.isVisibleForRetrievalDomain(domain) != false

private fun StateNode.isVisibleForRetrievalDomain(domain: RetrievalDomain): Boolean {
  val hasNoScope = scope == null && scopes.isEmpty()
  val matchesDomain = domain != RetrievalDomain.General &&
    (scope == domain.value || domain.value in scopes)
  return hasNoScope || matchesDomain
}

private fun VaultNode.directPatternLinks(): List<NodeId> = when (this) {
  is StateNode -> patternLinks
  is EpisodeNode -> patternLinks
  is PatternNode -> patternLinks
  is SubjectNode -> patternLinks
  is EmotionalStateNode -> patternLinks
}.distinctBy { it.value }

private fun fullBodyBudgetAudit(
  rootDocument: RetrievedRootDocument?,
  retrievalMode: SessionStartRetrievalMode,
): RetrievalAuditEntry {
  val rootWords = rootDocument?.body?.wordCount() ?: 0
  val reason = if (retrievalMode == SessionStartRetrievalMode.FullLoading) {
    "explicit full-loading mode bypasses default map-first body budget"
  } else {
    "default full-body budget=$DEFAULT_FULL_BODY_WORD_BUDGET_WORDS words; " +
      "root_words=$rootWords; branch bodies deferred to available_map"
  }
  return RetrievalAuditEntry(
    action = "full_body_budget",
    subject = retrievalMode.value,
    reason = reason,
  )
}

private fun loadedFullBodyContext(
  rootDocument: RetrievedRootDocument?,
  loadedNodes: List<RetrievedNode>,
  retrievalMode: SessionStartRetrievalMode,
): List<LoadedFullBodyContext> = buildList {
  if (rootDocument != null) {
    val body = if (retrievalMode == SessionStartRetrievalMode.FullLoading) {
      rootDocument.body
    } else {
      rootDocument.body.limitWords(DEFAULT_FULL_BODY_WORD_BUDGET_WORDS)
    }
    add(
      LoadedFullBodyContext(
        id = rootDocument.path,
        body = body,
        source = FullBodyContextSource.Root,
        loadOrder = rootDocument.loadOrder,
        reason = rootDocument.reason,
      ),
    )
  }
  if (retrievalMode != SessionStartRetrievalMode.FullLoading) return@buildList
  for (node in loadedNodes) {
    add(
      LoadedFullBodyContext(
        id = node.id,
        body = node.body,
        source = FullBodyContextSource.Node,
        loadOrder = node.loadOrder,
        reason = node.reason,
      ),
    )
  }
}

private data class RankedSuggestion(
  val node: VaultNode,
  val score: Int,
  val reason: String,
)

private const val DEFAULT_FULL_BODY_WORD_BUDGET_WORDS: Int = 1_500

private fun String.branchPath(): String = substringBeforeLast("/", missingDelimiterValue = this)

private fun String.firstMeaningfulLine(): String = lineSequence()
  .map { it.trim() }
  .firstOrNull { line -> line.isNotBlank() && !line.startsWith("#") && line != "---" }
  .orEmpty()

private fun String.summarySection(): String {
  val lines = lines()
  val start = lines.indexOfFirst { it.trim().equals("## Summary", ignoreCase = true) }
  if (start == -1) return ""
  return lines
    .drop(start + 1)
    .takeWhile { !it.trimStart().startsWith("## ") }
    .map { it.trim() }
    .firstOrNull { it.isNotBlank() }
    .orEmpty()
}

private fun String.limitWords(maxWords: Int): String {
  val words = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
  if (words.size <= maxWords) return trim()
  return words.take(maxWords).joinToString(" ") + "..."
}

private fun String.wordCount(): Int = trim()
  .split(Regex("\\s+"))
  .count { it.isNotBlank() }

private fun String.requestsRecentEvidence(): Boolean {
  val terms = listOf(
    "recent",
    "latest",
    "today",
    "yesterday",
    "this week",
    "evidence",
    "incident",
    "debug",
    "what happened",
    "timeline",
  )
  return terms.any { contains(it, ignoreCase = true) }
}

private fun com.sermilion.personalgraph.domain.model.StateCategory.wireValue(): String = when (this) {
  com.sermilion.personalgraph.domain.model.StateCategory.Preference -> "preference"
  com.sermilion.personalgraph.domain.model.StateCategory.Role -> "role"
  com.sermilion.personalgraph.domain.model.StateCategory.Knowledge -> "knowledge"
  com.sermilion.personalgraph.domain.model.StateCategory.Fact -> "fact"
}

private fun com.sermilion.personalgraph.domain.model.EpisodeType.wireValue(): String = when (this) {
  com.sermilion.personalgraph.domain.model.EpisodeType.Purchase -> "purchase"
  com.sermilion.personalgraph.domain.model.EpisodeType.AdviceSeeking -> "advice-seeking"
  com.sermilion.personalgraph.domain.model.EpisodeType.Research -> "research"
  com.sermilion.personalgraph.domain.model.EpisodeType.DesignDoc -> "design-doc"
  com.sermilion.personalgraph.domain.model.EpisodeType.Question -> "question"
  com.sermilion.personalgraph.domain.model.EpisodeType.PersonalStory -> "personal-story"
  com.sermilion.personalgraph.domain.model.EpisodeType.WorkInteraction -> "work-interaction"
  com.sermilion.personalgraph.domain.model.EpisodeType.Decision -> "decision"
}
