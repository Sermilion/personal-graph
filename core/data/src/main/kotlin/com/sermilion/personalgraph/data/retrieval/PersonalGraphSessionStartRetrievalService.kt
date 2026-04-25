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
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.retrieval.RetrievalAuditEntry
import com.sermilion.personalgraph.domain.retrieval.RetrievalClassification
import com.sermilion.personalgraph.domain.retrieval.RetrievalDomain
import com.sermilion.personalgraph.domain.retrieval.RetrievedBranch
import com.sermilion.personalgraph.domain.retrieval.RetrievedNode
import com.sermilion.personalgraph.domain.retrieval.RetrievedRootDocument
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalReport
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalRequest
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalService
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
    val loadedNodes = mutableListOf<RetrievedNode>()
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
    val branchPlan = branchPlanFor(classification)
    addDefaultSkips(classification, skippedBranches, audit)

    val seedNodes = mutableListOf<VaultNode>()
    for ((branch, reason) in branchPlan) {
      val nodes = loadBranch(branch, reason, loadedBranches, skippedBranches, audit)
      seedNodes.addAll(nodes)
      for (node in nodes.sortedBy { it.id.value }) {
        loadedNodes.add(node.toRetrievedNode(++loadOrder, reason))
      }
    }

    val patternNodes = loadLinkedPatterns(seedNodes, audit)
    for (node in patternNodes.sortedBy { it.id.value }) {
      loadedNodes.add(
        node.toRetrievedNode(
          loadOrder = ++loadOrder,
          reason = "wikilinked pattern hub from loaded retrieval context",
        ),
      )
    }

    SessionStartRetrievalReport(
      rootDocument = rootDocument,
      classification = classification,
      loadedBranches = loadedBranches,
      loadedNodes = loadedNodes,
      skippedBranches = skippedBranches.distinctBy { it.branch },
      audit = audit,
    )
  }

  private fun classify(message: String): RetrievalClassification {
    val workMatches = matchedTerms(message, WORK_TERMS)
    val personalMatches = matchedTerms(message, PERSONAL_TERMS)
    val creativeMatches = matchedTerms(message, CREATIVE_TERMS)
    val emotionalMatches = matchedTerms(message, EMOTIONAL_TERMS)
    val domain = when {
      workMatches.isNotEmpty() -> RetrievalDomain.WorkCapmo
      personalMatches.isNotEmpty() -> RetrievalDomain.Personal
      creativeMatches.isNotEmpty() -> RetrievalDomain.Creative
      else -> RetrievalDomain.General
    }
    return RetrievalClassification(
      domain = domain,
      matchedTerms = when (domain) {
        RetrievalDomain.WorkCapmo -> workMatches
        RetrievalDomain.Personal -> personalMatches
        RetrievalDomain.Creative -> creativeMatches
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
    return Regex("""(?i)(?<![a-z0-9])$escaped(?![a-z0-9])""").containsMatchIn(message)
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
    val branches = when (classification.domain) {
      RetrievalDomain.WorkCapmo -> listOf(
        "${VaultLayout.BRANCH_DOMAINS}/work/capmo" to
          "classified work/capmo from first substantive message",
      )
      RetrievalDomain.Personal -> listOf(
        "${VaultLayout.BRANCH_DOMAINS}/personal" to
          "classified personal from first substantive message",
      )
      RetrievalDomain.Creative -> listOf(
        "${VaultLayout.BRANCH_DOMAINS}/creative" to
          "classified creative from first substantive message",
      )
      RetrievalDomain.General -> DURABLE_STATE_BRANCHES.map { branch ->
        branch to "general classification loads durable state branch"
      }
    }
    return if (classification.emotionalContextRequested) {
      branches + (VaultLayout.BRANCH_EMOTIONAL_STATES to "explicit emotional/self-reflection context")
    } else {
      branches
    }
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
          audit.add(
            RetrievalAuditEntry(
              action = "loaded",
              subject = VaultLayout.BRAIAN_FILENAME,
              reason = "root orienting note is always loaded first",
            ),
          )
          RetrievedRootDocument(
            path = VaultLayout.BRAIAN_FILENAME,
            body = Files.readString(target),
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
    loadedBranches: MutableList<RetrievedBranch>,
    skippedBranches: MutableList<SkippedBranch>,
    audit: MutableList<RetrievalAuditEntry>,
  ): List<VaultNode> {
    val candidate = vaultRoot.resolve(branch)
    if (!pathResolver.assertWithinVault(vaultRoot, candidate)) {
      skip(branch, "branch is outside the vault root or crosses a symlink", skippedBranches, audit)
      return emptyList()
    }
    val nodes = repository.listNodesInBranch(branch).sortedBy { it.id.value }
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

  private fun VaultNode.directPatternLinks(): List<NodeId> = when (this) {
    is StateNode -> patternLinks
    is EpisodeNode -> patternLinks
    is PatternNode -> patternLinks
    is EmotionalStateNode -> patternLinks
  }.distinctBy { it.value }

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

    private val DURABLE_STATE_BRANCHES: List<String> = listOf(
      VaultLayout.BRANCH_STATE_PREFERENCES,
      VaultLayout.BRANCH_STATE_ROLES,
      VaultLayout.BRANCH_STATE_KNOWLEDGE,
    )

    private val WORK_TERMS: List<String> = listOf(
      "capmo",
      "work",
      "job",
      "company",
      "project",
      "code",
      "pr",
      "review",
      "meeting",
      "manager",
    )

    private val PERSONAL_TERMS: List<String> = listOf(
      "personal",
      "home",
      "family",
      "health",
      "habit",
      "finances",
      "purchase",
    )

    private val CREATIVE_TERMS: List<String> = listOf(
      "creative",
      "writing",
      "story",
      "music",
      "art",
      "design",
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
