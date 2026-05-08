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
import com.sermilion.personalgraph.domain.repository.GraphIndexBranchQuery
import com.sermilion.personalgraph.domain.repository.GraphIndexRepository
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.retrieval.CompactMapEntry
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
import com.sermilion.personalgraph.domain.retrieval.SessionStartTokenAccounting
import com.sermilion.personalgraph.domain.retrieval.SkippedBranch
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
  private val graphIndexRepository: GraphIndexRepository,
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
    var loadOrder = 0

    val classification = classify(request.firstSubstantiveMessage)
    audit.add(
      RetrievalAuditEntry(
        action = "classified",
        subject = classification.domain.value,
        reason = classificationReason(classification),
      ),
    )

    val rootDocument = loadBraian(++loadOrder, audit)
    val branchPlan = branchPlanFor(classification, request.retrievalMode)
    addDefaultSkips(classification, skippedBranches, audit)

    val branchMap = loadBranchMap(
      branchPlan = branchPlan,
      classification = classification,
      retrievalMode = request.retrievalMode,
      initialLoadOrder = loadOrder,
      loadedBranches = loadedBranches,
      skippedBranches = skippedBranches,
      audit = audit,
    )
    val loadedNodes = branchMap.loadedNodes
    val availableMap = branchMap.availableMap
    val loadedContext = loadedContext(rootDocument, loadedNodes, request.retrievalMode, audit)
    val suggestedReads = suggestedReads(availableMap, classification, audit)
    val suggestedActions = suggestedActions(request.firstSubstantiveMessage, classification, loadedBranches, audit)
    audit.add(retrievalModeAudit(request.retrievalMode))

    val report = SessionStartRetrievalReport(
      rootDocument = rootDocument,
      classification = classification,
      loadedContext = loadedContext,
      availableMap = availableMap,
      suggestedReads = suggestedReads,
      suggestedActions = suggestedActions,
      estimatedTokens = SessionStartTokenAccounting(),
      skippedBranches = skippedBranches.distinctBy { it.branch },
      audit = audit,
      loadedBranches = loadedBranches,
      loadedNodes = if (request.retrievalMode == SessionStartRetrievalMode.FullLoading) loadedNodes else emptyList(),
      loadedFullBodyContext = loadedContext,
      compactMapEntries = availableMap,
      auditEntries = audit,
    )
    report.copy(estimatedTokens = estimatedTokens(report))
  }

  private suspend fun loadBranchMap(
    branchPlan: List<Pair<String, String>>,
    classification: RetrievalClassification,
    retrievalMode: SessionStartRetrievalMode,
    initialLoadOrder: Int,
    loadedBranches: MutableList<RetrievedBranch>,
    skippedBranches: MutableList<SkippedBranch>,
    audit: MutableList<RetrievalAuditEntry>,
  ): BranchMapResult = if (retrievalMode == SessionStartRetrievalMode.MapFirst) {
    loadMapFirstBranchMap(branchPlan, classification, loadedBranches, skippedBranches, audit)
  } else {
    loadFullBranchMap(branchPlan, classification, initialLoadOrder, loadedBranches, skippedBranches, audit)
  }

  private suspend fun loadMapFirstBranchMap(
    branchPlan: List<Pair<String, String>>,
    classification: RetrievalClassification,
    loadedBranches: MutableList<RetrievedBranch>,
    skippedBranches: MutableList<SkippedBranch>,
    audit: MutableList<RetrievalAuditEntry>,
  ): BranchMapResult {
    val indexEntries = mutableListOf<SessionStartIndexMapEntry>()
    for ((branch, reason) in branchPlan) {
      indexEntries.addAll(loadIndexBranch(branch, reason, classification, loadedBranches, skippedBranches, audit))
    }
    return BranchMapResult(
      availableMap = availableMapFromIndex(loadedBranches, indexEntries, classification),
      loadedNodes = emptyList(),
    )
  }

  private suspend fun loadFullBranchMap(
    branchPlan: List<Pair<String, String>>,
    classification: RetrievalClassification,
    initialLoadOrder: Int,
    loadedBranches: MutableList<RetrievedBranch>,
    skippedBranches: MutableList<SkippedBranch>,
    audit: MutableList<RetrievalAuditEntry>,
  ): BranchMapResult {
    var loadOrder = initialLoadOrder
    val seedNodes = mutableListOf<VaultNode>()
    val loadedNodes = mutableListOf<RetrievedNode>()
    for ((branch, reason) in branchPlan) {
      val nodes = loadBranch(branch, reason, classification, loadedBranches, skippedBranches, audit)
      seedNodes.addAll(nodes)
      nodes.sortedBy { it.id.value }.mapTo(loadedNodes) { node ->
        node.toRetrievedNode(++loadOrder, reason)
      }
    }
    loadLinkedPatterns(seedNodes, audit).sortedBy { it.id.value }.mapTo(loadedNodes) { node ->
      node.toRetrievedNode(++loadOrder, "wikilinked pattern hub from loaded retrieval context")
    }
    return BranchMapResult(
      availableMap = availableMap(loadedBranches, loadedNodes, classification),
      loadedNodes = loadedNodes,
    )
  }

  private fun branchPlanFor(
    classification: RetrievalClassification,
    retrievalMode: SessionStartRetrievalMode,
  ): List<Pair<String, String>> {
    val durableStateBranches = durableStateBranchesFor(classification, retrievalMode)
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
    return if (retrievalMode == SessionStartRetrievalMode.FullLoading) {
      durableStateBranches + domainBranches + emotionalBranch
    } else {
      domainBranches + durableStateBranches + emotionalBranch
    }
  }

  private fun durableStateBranchesFor(
    classification: RetrievalClassification,
    retrievalMode: SessionStartRetrievalMode,
  ): List<Pair<String, String>> = when {
    retrievalMode == SessionStartRetrievalMode.FullLoading && classification.domain != RetrievalDomain.General ->
      DURABLE_STATE_BRANCHES_FULL_LOADING
    classification.domain == RetrievalDomain.General -> DURABLE_STATE_BRANCHES_GENERAL
    else -> SCOPED_STATE_BRANCHES_FOR_CLASSIFIED
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
          val rawBody = Files.readString(target)
          val boundedBody = rawBody.limitWords(MAX_LOADED_CONTEXT_WORDS)
          audit.add(
            RetrievalAuditEntry(
              action = "loaded",
              subject = VaultLayout.BRAIAN_FILENAME,
              reason = "root orienting note is always loaded first",
            ),
          )
          if (boundedBody != rawBody) {
            audit.add(
              RetrievalAuditEntry(
                action = "bounded_loaded_context",
                subject = VaultLayout.BRAIAN_FILENAME,
                reason = "root orientation exceeded $MAX_LOADED_CONTEXT_WORDS words and was truncated",
              ),
            )
          }
          RetrievedRootDocument(
            path = VaultLayout.BRAIAN_FILENAME,
            body = boundedBody,
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
    loadedBranches: MutableList<RetrievedBranch>,
    skippedBranches: MutableList<SkippedBranch>,
    audit: MutableList<RetrievalAuditEntry>,
  ): List<VaultNode> {
    val candidate = vaultRoot.resolve(branch)
    if (!pathResolver.assertWithinVault(vaultRoot, candidate)) {
      skip(branch, "branch is outside the vault root or crosses a symlink", skippedBranches, audit)
      return emptyList()
    }
    val nodes = repository.listNodesInBranch(branch)
      .filter { it.isVisibleInStateBranch(branch, classification.domain) }
      .sortedBy { it.id.value }
    loadedBranches.add(RetrievedBranch(branch = branch, reason = reason, nodeCount = nodes.size))
    audit.add(
      RetrievalAuditEntry(
        action = "loaded_branch",
        subject = branch,
        reason = "$reason; nodes=${nodes.size}",
      ),
    )
    return nodes
  }

  private suspend fun loadIndexBranch(
    branch: String,
    reason: String,
    classification: RetrievalClassification,
    loadedBranches: MutableList<RetrievedBranch>,
    skippedBranches: MutableList<SkippedBranch>,
    audit: MutableList<RetrievalAuditEntry>,
  ): List<SessionStartIndexMapEntry> {
    val candidate = vaultRoot.resolve(branch)
    if (!pathResolver.assertWithinVault(vaultRoot, candidate)) {
      skip(branch, "branch is outside the vault root or crosses a symlink", skippedBranches, audit)
      return emptyList()
    }
    val entries = graphIndexRepository.listEntriesInBranch(branch, mapFirstIndexQuery(branch))
      .filter { it.isVisibleInStateBranch(branch, classification.domain) }
      .sortedBy { it.id.value }
    loadedBranches.add(RetrievedBranch(branch = branch, reason = reason, nodeCount = entries.size))
    audit.add(
      RetrievalAuditEntry(
        action = "loaded_branch_index",
        subject = branch,
        reason = "$reason; index_entries=${entries.size}; include_body=false",
      ),
    )
    return entries.map { entry -> SessionStartIndexMapEntry(entry = entry, reason = reason, plannedBranch = branch) }
  }

  private fun mapFirstIndexQuery(branch: String): GraphIndexBranchQuery = GraphIndexBranchQuery(
    limit = if (branch.startsWith("${VaultLayout.BRANCH_STATE}/")) {
      MAP_FIRST_STATE_INDEX_CANDIDATES_PER_BRANCH
    } else {
      MAP_FIRST_INDEX_CANDIDATES_PER_BRANCH
    },
    preferredRelativePrefixes = if (branch.startsWith("${VaultLayout.BRANCH_DOMAINS}/")) {
      listOf(
        VaultLayout.SUB_DOMAIN_SUBJECTS,
        "index",
        VaultLayout.SUB_DOMAIN_EVENTS,
      )
    } else {
      emptyList()
    },
  )

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
    type = mapType(),
    category = mapCategory(),
    domain = mapDomain(),
    scope = mapScope(),
    scopes = mapScopes(),
    updated = updatedAt.toDateString(),
    date = mapDate(),
    summary = mapSummary(),
    aliases = mapAliases(),
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
    private const val MAP_FIRST_INDEX_CANDIDATES_PER_BRANCH: Int = 40
    private const val MAP_FIRST_STATE_INDEX_CANDIDATES_PER_BRANCH: Int = 80

    private const val REASON_CLASSIFIED_PREFERENCES_SCOPED: String =
      "classified sessions load scoped preferences plus essential global preferences"
    private const val REASON_PREFERENCES_GENERAL: String =
      "general classification loads durable global preferences"
    private const val REASON_ROLES_ALWAYS: String =
      "general classification loads durable role context"
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

    private val SCOPED_STATE_BRANCHES_FOR_CLASSIFIED: List<Pair<String, String>> = listOf(
      VaultLayout.BRANCH_STATE_PREFERENCES to REASON_CLASSIFIED_PREFERENCES_SCOPED,
    )

    private val DURABLE_STATE_BRANCHES_FULL_LOADING: List<Pair<String, String>> = listOf(
      VaultLayout.BRANCH_STATE_PREFERENCES to REASON_PREFERENCES_GENERAL,
      VaultLayout.BRANCH_STATE_ROLES to REASON_ROLES_ALWAYS,
    )

    private val DURABLE_STATE_BRANCHES_GENERAL: List<Pair<String, String>> = listOf(
      VaultLayout.BRANCH_STATE_PREFERENCES to REASON_PREFERENCES_GENERAL,
      VaultLayout.BRANCH_STATE_ROLES to REASON_ROLES_ALWAYS,
      VaultLayout.BRANCH_STATE_KNOWLEDGE to REASON_KNOWLEDGE_GENERAL,
    )
  }
}

private data class BranchMapResult(
  val availableMap: List<CompactMapEntry>,
  val loadedNodes: List<RetrievedNode>,
)
