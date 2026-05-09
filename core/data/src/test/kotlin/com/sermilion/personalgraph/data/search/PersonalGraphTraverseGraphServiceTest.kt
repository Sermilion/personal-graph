package com.sermilion.personalgraph.data.search

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.common.dispatcher.DispatcherProvider
import com.sermilion.personalgraph.data.di.DataSearchComponent
import com.sermilion.personalgraph.domain.graph.GraphIndexEntry
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.repository.GraphIndexRepository
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.search.TraversalEdgeType
import com.sermilion.personalgraph.domain.search.TraversalPrunedReason
import com.sermilion.personalgraph.domain.search.TraversalRankBy
import com.sermilion.personalgraph.domain.search.TraverseGraphQuery
import com.sermilion.personalgraph.domain.search.TraverseGraphService
import com.sermilion.personalgraph.domain.tokens.TokenEstimator
import com.sermilion.personalgraph.testing.TestDispatcherProvider
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.Instant
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

private data class TraversalEntrySpec(
  val id: String,
  val branch: String? = null,
  val type: String = "state",
  val category: String? = "preference",
  val domain: String? = null,
  val subject: String? = null,
  val topic: String? = null,
  val aliases: List<String> = emptyList(),
  val hypothesis: String? = null,
  val date: Instant? = null,
  val links: List<NodeId> = emptyList(),
  val linkCount: Int = links.size,
  val snippet: String? = null,
  val bodyTokenEstimate: Int = 4,
)

private fun traversalEntry(spec: TraversalEntrySpec): GraphIndexEntry = GraphIndexEntry(
  id = NodeId(spec.id),
  branch = spec.branch ?: spec.id.substringBeforeLast('/'),
  type = spec.type,
  category = spec.category,
  domain = spec.domain,
  scope = null,
  scopes = emptyList(),
  subject = spec.subject,
  topic = spec.topic,
  aliases = spec.aliases,
  hypothesis = spec.hypothesis,
  date = spec.date,
  updated = Instant.parse("2026-05-01T00:00:00Z"),
  created = Instant.parse("2026-04-24T00:00:00Z"),
  links = spec.links,
  linkCount = spec.linkCount,
  snippet = spec.snippet ?: "snippet for ${spec.id}",
  bodyTokenEstimate = spec.bodyTokenEstimate,
  fileSize = 256,
  fileModifiedAt = Instant.parse("2026-05-01T00:00:00Z"),
)

private fun testIndex(
  entriesByBranch: Map<String, List<GraphIndexEntry>>,
  pathLookups: Map<String, GraphIndexEntry> = emptyMap(),
): GraphIndexRepository {
  val entries = entriesByBranch.values.flatten()
  val entriesById = entries.associateBy { it.id }
  val index = mockk<GraphIndexRepository>()
  coEvery { index.listEntriesInBranch(any()) } answers { entriesByBranch[invocation.args[0] as String].orEmpty() }
  coEvery { index.findEntry(any()) } answers { entriesById[nodeIdArg(invocation.args[0])] }
  coEvery { index.findEntryByPath(any()) } answers {
    val query = invocation.args[0] as String
    pathLookups[query] ?: runCatching { NodeId(query) }.getOrNull()?.let(entriesById::get)
  }
  coEvery { index.findEntryByTitle(any()) } answers {
    val query = invocation.args[0] as String
    entries.firstOrNull { it.subject.equals(query, ignoreCase = true) || it.topic.equals(query, ignoreCase = true) }
  }
  coEvery { index.findEntryByAlias(any()) } answers {
    val query = invocation.args[0] as String
    entries.firstOrNull { entry -> entry.aliases.any { it.equals(query, ignoreCase = true) } }
  }
  return index
}

private fun testVault(
  backlinks: Map<NodeId, List<VaultNode>> = emptyMap(),
  nodes: Map<NodeId, VaultNode> = emptyMap(),
): VaultRepository {
  val vault = mockk<VaultRepository>()
  coEvery { vault.listBacklinks(any()) } answers { backlinks[nodeIdArg(invocation.args[0])].orEmpty() }
  coEvery { vault.findNode(any()) } answers { nodes[nodeIdArg(invocation.args[0])] }
  return vault
}

private fun nodeIdArg(value: Any?): NodeId = when (value) {
  is NodeId -> value
  is String -> NodeId(value)
  else -> error("Expected NodeId argument but was $value")
}

private fun newService(
  index: GraphIndexRepository,
  vault: VaultRepository = testVault(),
): PersonalGraphTraverseGraphService = PersonalGraphTraverseGraphService(
  graphIndexRepository = index,
  vaultRepository = vault,
  tokenEstimator = TokenEstimator,
  dispatcherProvider = TestDispatcherProvider(),
)

@AppScope
@Component
internal abstract class TestDataSearchComponent(
  @get:Provides val graphIndexRepository: GraphIndexRepository,
  @get:Provides val vaultRepository: VaultRepository,
  @get:Provides val dispatcherProvider: DispatcherProvider,
) : DataSearchComponent {
  @Provides
  fun provideTokenEstimator(): TokenEstimator = TokenEstimator

  abstract val traverseGraphService: TraverseGraphService
}

class PersonalGraphTraverseGraphServiceTest :
  FunSpec({

    test("traversal returns entrypoints scored nodes labeled edges and token estimates") {
      val start = traversalEntry(
        TraversalEntrySpec(
          id = "state/preferences/start",
          links = listOf(
            NodeId("domains/work/project/subjects/api"),
            NodeId("timeline/2026-05/event"),
            NodeId("patterns/retry-loop"),
            NodeId("outdated/resolved/old-note"),
            NodeId("domains/work/project/generic-note"),
            NodeId("state/preferences/contradicting-note"),
            NodeId("state/preferences/related-state"),
          ),
        ),
      )
      val subject = traversalEntry(
        TraversalEntrySpec(id = "domains/work/project/subjects/api", type = "subject", subject = "API"),
      )
      val event = traversalEntry(
        TraversalEntrySpec(
          id = "timeline/2026-05/event",
          type = "episode",
          topic = "API launch",
          date = Instant.parse("2026-05-01T00:00:00Z"),
        ),
      )
      val pattern = traversalEntry(TraversalEntrySpec(id = "patterns/retry-loop", type = "pattern"))
      val background = traversalEntry(TraversalEntrySpec(id = "outdated/resolved/old-note", type = "note"))
      val link = traversalEntry(TraversalEntrySpec(id = "domains/work/project/generic-note", type = "note"))
      val contradiction = traversalEntry(TraversalEntrySpec(id = "state/preferences/contradicting-note"))
      val state = traversalEntry(TraversalEntrySpec(id = "state/preferences/related-state"))
      val index = testIndex(
        mapOf(
          "state" to listOf(start, contradiction, state),
          "domains" to listOf(subject, link),
          "timeline" to listOf(event),
          "patterns" to listOf(pattern),
          "outdated" to listOf(background),
        ),
      )
      val service = newService(index)

      val outcome = service.traverse(
        TraverseGraphQuery(
          startIds = listOf(start.id),
          branches = listOf("state", "domains", "timeline", "patterns", "outdated"),
          maxDepth = 1,
          maxNodes = 10,
        ),
      )

      outcome.entrypoints.map { it.id.value } shouldBe listOf(start.id.value)
      outcome.nodes.map { it.id.value } shouldContain start.id.value
      outcome.edges.map { Triple(it.type, it.label, it.weight) }.shouldContainExactlyInAnyOrder(
        Triple(TraversalEdgeType.SubjectEvidence, "subject_evidence", 14),
        Triple(TraversalEdgeType.Timeline, "timeline", 12),
        Triple(TraversalEdgeType.Pattern, "pattern", 12),
        Triple(TraversalEdgeType.Background, "background", 4),
        Triple(TraversalEdgeType.Link, "link", 8),
        Triple(TraversalEdgeType.Contradiction, "contradiction", 15),
        Triple(TraversalEdgeType.State, "state", 10),
      )
      outcome.estimatedTokens shouldBeGreaterThan 0
    }

    test("backlink edge type is collected from warmed graph links") {
      val start = traversalEntry(
        TraversalEntrySpec(id = "state/preferences/start", subject = "backlink target"),
      )
      val backlink = traversalEntry(
        TraversalEntrySpec(
          id = "domains/work/project/generic-note",
          type = "note",
          links = listOf(start.id),
        ),
      )
      val index = testIndex(mapOf("state" to listOf(start), "domains" to listOf(backlink)))
      val vault = testVault()
      val service = newService(index, vault)

      val outcome = service.traverse(
        TraverseGraphQuery(
          query = "backlink target",
          startIds = listOf(start.id),
          branches = listOf("state", "domains"),
          edgeTypes = setOf(TraversalEdgeType.Backlink),
          maxDepth = 1,
        ),
      )

      outcome.edges.map { Triple(it.type, it.label, it.weight) } shouldBe listOf(
        Triple(TraversalEdgeType.Backlink, "backlink", 7),
      )
      outcome.nodes.map { it.id.value } shouldContain backlink.id.value
      coVerify(exactly = 0) { vault.listBacklinks(any()) }
    }

    test("traversal with default branches returns an allowed default branch entry") {
      val entry = traversalEntry(
        TraversalEntrySpec(
          id = "state/preferences/default-branch-entry",
          subject = "default branch entry",
        ),
      )
      val index = testIndex(mapOf("state" to listOf(entry)))
      val service = newService(index)

      val outcome = service.traverse(TraverseGraphQuery(query = "default branch entry"))

      outcome.nodes.map { it.id.value } shouldContain entry.id.value
    }

    test("custom branch scope excludes matching entries from unrequested branches") {
      val domainEntry = traversalEntry(
        TraversalEntrySpec(id = "domains/work/project/subjects/scoped", type = "subject", subject = "scope match"),
      )
      val stateEntry = traversalEntry(
        TraversalEntrySpec(
          id = "state/preferences/scoped",
          subject = "scope match",
          links = listOf(domainEntry.id),
        ),
      )
      val index = testIndex(mapOf("state" to listOf(stateEntry), "domains" to listOf(domainEntry)))
      val service = newService(index)

      val outcome = service.traverse(
        TraverseGraphQuery(
          query = "scope match",
          startIds = listOf(stateEntry.id),
          branches = listOf("state"),
          maxDepth = 1,
        ),
      )

      val visibleIds = outcome.nodes.map { it.id.value } +
        outcome.pruned.map { it.id.value } +
        outcome.suggestedReads.map { it.id.value } +
        outcome.edges.flatMap { listOf(it.from.value, it.to.value) }
      visibleIds shouldContain stateEntry.id.value
      visibleIds shouldNotContain domainEntry.id.value
    }

    test("invalid raw query syntax does not construct a NodeId") {
      val entry = traversalEntry(TraversalEntrySpec(id = "state/preferences/open", subject = "status open"))
      val index = testIndex(mapOf("state" to listOf(entry)))
      val service = newService(index)

      val outcome = service.traverse(TraverseGraphQuery(query = "status: open", branches = listOf("state")))

      outcome.nodes shouldBe emptyList()
    }

    test("forward edgeTypes filtering excludes edges and expansion nodes") {
      val pattern = traversalEntry(TraversalEntrySpec(id = "patterns/retry-loop", type = "pattern"))
      val generic = traversalEntry(TraversalEntrySpec(id = "domains/work/project/generic-note", type = "note"))
      val start = traversalEntry(
        TraversalEntrySpec(
          id = "state/preferences/start",
          links = listOf(pattern.id, generic.id),
        ),
      )
      val index = testIndex(
        mapOf(
          "state" to listOf(start),
          "patterns" to listOf(pattern),
          "domains" to listOf(generic),
        ),
      )
      val service = newService(index)

      val outcome = service.traverse(
        TraverseGraphQuery(
          startIds = listOf(start.id),
          branches = listOf("state", "patterns", "domains"),
          edgeTypes = setOf(TraversalEdgeType.Pattern),
          maxDepth = 1,
          maxNodes = 10,
        ),
      )

      outcome.edges.map { it.label } shouldBe listOf("pattern")
      outcome.nodes.map { it.id.value } shouldContain pattern.id.value
      outcome.nodes.map { it.id.value } shouldNotContain generic.id.value
    }

    test("forward expansion skips new candidate cap misses and keeps scanning existing targets") {
      val existing = traversalEntry(TraversalEntrySpec(id = "state/preferences/ab-existing", subject = "scan"))
      val newTarget = traversalEntry(TraversalEntrySpec(id = "state/preferences/zz-new-target"))
      val start = traversalEntry(
        TraversalEntrySpec(
          id = "state/preferences/aa-start",
          subject = "scan",
          links = listOf(newTarget.id, existing.id),
        ),
      )
      val fillers = (1..6).map {
        traversalEntry(TraversalEntrySpec(id = "state/preferences/filler-$it", subject = "scan"))
      }
      val index = testIndex(mapOf("state" to listOf(start, existing) + fillers + newTarget))
      val service = newService(index)

      val outcome = service.traverse(
        TraverseGraphQuery(
          query = "scan",
          branches = listOf("state"),
          maxDepth = 1,
          maxNodes = 2,
        ),
      )

      outcome.nodes.map { it.id.value } shouldBe listOf(start.id.value, existing.id.value)
      outcome.edges.map { it.to.value } shouldContain existing.id.value
      outcome.edges.map { it.to.value } shouldNotContain newTarget.id.value
    }

    test("max_depth controls multi-hop traversal depth") {
      val target = traversalEntry(TraversalEntrySpec(id = "state/preferences/target"))
      val intermediate = traversalEntry(
        TraversalEntrySpec(
          id = "state/preferences/intermediate",
          links = listOf(target.id),
        ),
      )
      val start = traversalEntry(
        TraversalEntrySpec(
          id = "state/preferences/start",
          links = listOf(intermediate.id),
        ),
      )
      val index = testIndex(mapOf("state" to listOf(start, intermediate, target)))
      val service = newService(index)

      val oneHop = service.traverse(
        TraverseGraphQuery(
          startIds = listOf(start.id),
          branches = listOf("state"),
          maxDepth = 1,
          maxNodes = 10,
        ),
      )
      val twoHops = service.traverse(
        TraverseGraphQuery(
          startIds = listOf(start.id),
          branches = listOf("state"),
          maxDepth = 2,
          maxNodes = 10,
        ),
      )

      oneHop.nodes.associate { it.id.value to it.depth } shouldBe mapOf(
        start.id.value to 0,
        intermediate.id.value to 1,
      )
      twoHops.nodes.associate { it.id.value to it.depth } shouldBe mapOf(
        start.id.value to 0,
        intermediate.id.value to 1,
        target.id.value to 2,
      )
    }

    test("ranking isolates exact direct evidence recency and hub penalties") {
      val exact = traversalEntry(TraversalEntrySpec(id = "domains/work/project/api-latency", type = "note"))
      val leafPeer = traversalEntry(
        TraversalEntrySpec(id = "domains/work/project/api-latency-peer", type = "note"),
      )
      val start = traversalEntry(
        TraversalEntrySpec(
          id = "state/preferences/start",
          subject = "api",
          links = listOf(
            exact.id,
            leafPeer.id,
            NodeId("domains/work/project/subjects/direct-evidence"),
            NodeId("domains/work/project/subjects/api-latency-everything"),
            NodeId("domains/work/project/subjects/everything-else"),
          ),
        ),
      )
      val subject = traversalEntry(
        TraversalEntrySpec(
          id = "domains/work/project/subjects/topic-hub",
          type = "subject",
          subject = "api-latency",
        ),
      )
      val directSubject = traversalEntry(
        TraversalEntrySpec(
          id = "domains/work/project/subjects/direct-evidence",
          type = "subject",
          subject = "api-latency direct evidence",
        ),
      )
      val notePeer = traversalEntry(
        TraversalEntrySpec(
          id = "domains/work/project/api-note-peer",
          type = "note",
          subject = "api-latency",
        ),
      )
      val event = traversalEntry(
        TraversalEntrySpec(
          id = "timeline/2026-05/api-event",
          type = "episode",
          topic = "api-latency",
          date = Instant.parse("2026-05-03T00:00:00Z"),
        ),
      )
      val eventPeer = traversalEntry(
        TraversalEntrySpec(
          id = "state/preferences/api-event-peer",
          topic = "api-latency",
        ),
      )
      val relatedHub = traversalEntry(
        TraversalEntrySpec(
          id = "domains/work/project/subjects/api-latency-everything",
          type = "subject",
          subject = "planning",
          linkCount = 60,
        ),
      )
      val unrelatedHub = traversalEntry(
        TraversalEntrySpec(
          id = "domains/work/project/subjects/everything-else",
          type = "subject",
          subject = "planning",
          linkCount = 60,
        ),
      )
      val index = testIndex(
        mapOf(
          "state" to listOf(start, eventPeer),
          "domains" to listOf(exact, leafPeer, subject, directSubject, notePeer, relatedHub, unrelatedHub),
          "timeline" to listOf(event),
        ),
      )
      val service = newService(index)

      val outcome = service.traverse(
        TraverseGraphQuery(
          query = "api-latency latest",
          startIds = listOf(start.id),
          branches = listOf("state", "domains", "timeline"),
          maxDepth = 1,
          maxNodes = 11,
        ),
      )

      val scores = outcome.nodes.associate { it.id.value to it.score }
      scores.getValue(exact.id.value) shouldBeGreaterThan scores.getValue(leafPeer.id.value)
      scores.getValue(subject.id.value) shouldBeGreaterThan scores.getValue(notePeer.id.value)
      scores.getValue(directSubject.id.value) shouldBe 123
      scores.getValue(event.id.value) shouldBeGreaterThan scores.getValue(eventPeer.id.value)
      scores.getValue(relatedHub.id.value) shouldBeGreaterThan scores.getValue(unrelatedHub.id.value)
    }

    test("rank_by recency boosts event-like nodes without textual recency trigger") {
      val event = traversalEntry(
        TraversalEntrySpec(
          id = "timeline/2026-05/api-event",
          type = "episode",
          topic = "api-latency",
          date = Instant.parse("2026-05-03T00:00:00Z"),
        ),
      )
      val state = traversalEntry(TraversalEntrySpec(id = "state/preferences/api-latency", subject = "api-latency"))
      val index = testIndex(mapOf("state" to listOf(state), "timeline" to listOf(event)))
      val service = newService(index)

      val relevance = service.traverse(
        TraverseGraphQuery(query = "api-latency", branches = listOf("state", "timeline")),
      )
      val recency = service.traverse(
        TraverseGraphQuery(
          query = "api-latency",
          branches = listOf("state", "timeline"),
          rankBy = TraversalRankBy.Recency,
        ),
      )

      val relevanceScores = relevance.nodes.associate { it.id.value to it.score }
      val recencyScores = recency.nodes.associate { it.id.value to it.score }
      recencyScores.getValue(event.id.value) shouldBeGreaterThan relevanceScores.getValue(event.id.value)
      recencyScores.getValue(state.id.value) shouldBe relevanceScores.getValue(state.id.value)
    }

    test("max_nodes and budget_tokens pruning use stable reasons and suggested reads") {
      val entries = (1..3).map {
        traversalEntry(TraversalEntrySpec(id = "state/preferences/item-$it", subject = "budget"))
      }
      val index = testIndex(mapOf("state" to entries))
      val service = newService(index)

      val maxNodesOutcome = service.traverse(
        TraverseGraphQuery(query = "budget", branches = listOf("state"), maxNodes = 1),
      )
      val budgetOutcome = service.traverse(
        TraverseGraphQuery(query = "budget", branches = listOf("state"), maxNodes = 10, budgetTokens = 35),
      )

      maxNodesOutcome.nodes.size shouldBe 1
      maxNodesOutcome.pruned.map { it.reason }.distinct() shouldBe listOf(TraversalPrunedReason.MaxNodes)
      budgetOutcome.nodes shouldBe emptyList()
      budgetOutcome.pruned.map { it.reason }.distinct() shouldBe listOf(TraversalPrunedReason.BudgetTokens)
      maxNodesOutcome.suggestedReads.map { it.id.value } shouldContain entries[1].id.value
    }

    test("token accounting budgets returned diagnostics") {
      val entries = (1..3).map {
        traversalEntry(TraversalEntrySpec(id = "state/preferences/accounted-$it", subject = "accounted"))
      }
      val index = testIndex(mapOf("state" to entries))
      val service = newService(index)

      val withDiagnostics = service.traverse(
        TraverseGraphQuery(query = "accounted", branches = listOf("state"), maxNodes = 1),
      )
      val diagnosticFreeBudget = estimateTokens(
        tokenEstimator = TokenEstimator,
        entrypoints = withDiagnostics.entrypoints,
        nodes = withDiagnostics.nodes,
        edges = withDiagnostics.edges,
        pruned = emptyList(),
        suggestedReads = emptyList(),
      )
      val budgeted = service.traverse(
        TraverseGraphQuery(
          query = "accounted",
          branches = listOf("state"),
          maxNodes = 1,
          budgetTokens = diagnosticFreeBudget,
        ),
      )

      withDiagnostics.pruned.size shouldBeGreaterThan budgeted.pruned.size
      withDiagnostics.suggestedReads.size shouldBeGreaterThan budgeted.suggestedReads.size
      budgeted.nodes.size shouldBe 1
      budgeted.pruned shouldBe emptyList()
      budgeted.suggestedReads shouldBe emptyList()
      (budgeted.estimatedTokens <= diagnosticFreeBudget) shouldBe true
    }
  })

class PersonalGraphTraverseGraphServiceBudgetAndLookupTest :
  FunSpec({
    test("entrypoints are capped to traversal overfetch budget") {
      val entries = (1..10).map {
        traversalEntry(TraversalEntrySpec(id = "state/preferences/item-$it", subject = "budget"))
      }
      val index = testIndex(mapOf("state" to entries))
      val service = newService(index)

      val outcome = service.traverse(
        TraverseGraphQuery(query = "budget", branches = listOf("state"), maxNodes = 1),
      )

      outcome.entrypoints.size shouldBe 4
    }

    test("start-id traversal resolves without warming scoped branches") {
      val entry = traversalEntry(TraversalEntrySpec(id = "state/preferences/start-only"))
      val index = testIndex(mapOf("state" to listOf(entry)))
      val service = newService(index)

      val outcome = service.traverse(
        TraverseGraphQuery(
          startIds = listOf(entry.id),
          branches = listOf("state"),
          maxDepth = 0,
        ),
      )

      outcome.nodes.map { it.id.value } shouldBe listOf(entry.id.value)
      coVerify(exactly = 0) { index.listEntriesInBranch(any()) }
    }

    test("start-id backlink expansion warms scoped graph entries") {
      val start = traversalEntry(TraversalEntrySpec(id = "state/preferences/start"))
      val backlink = traversalEntry(
        TraversalEntrySpec(
          id = "domains/work/project/backlink-source",
          type = "note",
          links = listOf(start.id),
        ),
      )
      val index = testIndex(mapOf("state" to listOf(start), "domains" to listOf(backlink)))
      val vault = testVault()
      val service = newService(index, vault)

      val outcome = service.traverse(
        TraverseGraphQuery(
          startIds = listOf(start.id),
          branches = listOf("state", "domains"),
          edgeTypes = setOf(TraversalEdgeType.Backlink),
          maxDepth = 1,
        ),
      )

      outcome.edges.map { Triple(it.type, it.label, it.weight) } shouldBe listOf(
        Triple(TraversalEdgeType.Backlink, "backlink", 7),
      )
      outcome.nodes.map { it.id.value } shouldContain backlink.id.value
      coVerify(exactly = 1) { index.listEntriesInBranch("state") }
      coVerify(exactly = 1) { index.listEntriesInBranch("domains") }
      coVerify(exactly = 0) { vault.listBacklinks(any()) }
    }

    test("budget_tokens pruning includes returned edge token cost") {
      val target = traversalEntry(TraversalEntrySpec(id = "state/preferences/target", subject = "edge-budget"))
      val start = traversalEntry(
        TraversalEntrySpec(
          id = "state/preferences/start",
          subject = "edge-budget",
          links = listOf(target.id),
        ),
      )
      val index = testIndex(mapOf("state" to listOf(start, target)))
      val service = newService(index)

      val metadataOnly = service.traverse(
        TraverseGraphQuery(
          startIds = listOf(start.id),
          branches = listOf("state"),
          maxDepth = 1,
          maxNodes = 2,
          budgetTokens = 10_000,
        ),
      )
      val budgetOutcome = service.traverse(
        TraverseGraphQuery(
          startIds = listOf(start.id),
          branches = listOf("state"),
          maxDepth = 1,
          maxNodes = 2,
          budgetTokens = metadataOnly.estimatedTokens - 1,
        ),
      )

      (budgetOutcome.estimatedTokens <= metadataOnly.estimatedTokens - 1) shouldBe true
      budgetOutcome.edges shouldBe emptyList()
      budgetOutcome.pruned.map { it.reason } shouldContain TraversalPrunedReason.BudgetTokens
    }

    test("policy-blocked branches ids links and pruned output remain invisible") {
      val allowed = traversalEntry(
        TraversalEntrySpec(
          id = "state/preferences/allowed",
          links = listOf(NodeId("people/alice"), NodeId("staging/sensitive/secret")),
          subject = "allowed",
        ),
      )
      val leakedPeople = traversalEntry(TraversalEntrySpec(id = "people/alice", branch = "state", subject = "allowed"))
      val leakedSensitive = traversalEntry(
        TraversalEntrySpec(id = "staging/sensitive/secret", branch = "state", subject = "allowed"),
      )
      val index = testIndex(mapOf("state" to listOf(allowed, leakedPeople, leakedSensitive)))
      val vault = testVault(
        backlinks = mapOf(
          allowed.id to listOf(VaultNodeFixtures.stateNode(id = "people/bob", body = "blocked")),
        ),
      )
      val service = newService(index, vault)

      val outcome = service.traverse(
        TraverseGraphQuery(
          query = "allowed",
          startIds = listOf(NodeId("people/alice"), allowed.id),
          branches = listOf("people", "staging/sensitive", "state"),
          maxDepth = 1,
          maxNodes = 1,
        ),
      )

      val visibleIds = outcome.nodes.map { it.id.value } +
        outcome.pruned.map { it.id.value } +
        outcome.suggestedReads.map { it.id.value } +
        outcome.edges.flatMap { listOf(it.from.value, it.to.value) }
      visibleIds shouldNotContain "people/alice"
      visibleIds shouldNotContain "people/bob"
      visibleIds shouldNotContain "staging/sensitive/secret"
    }

    test("include_bodies hydrates returned nodes and affects token estimates") {
      val entry = traversalEntry(TraversalEntrySpec(id = "state/preferences/body-note", subject = "body"))
      val node = VaultNodeFixtures.stateNode(
        id = entry.id.value,
        body = "body text with enough content to increase the deterministic token estimate",
      )
      val index = testIndex(mapOf("state" to listOf(entry)))
      val service = newService(index, testVault(nodes = mapOf(entry.id to node)))

      val metadataOnly = service.traverse(TraverseGraphQuery(query = "body-note", branches = listOf("state")))
      val withBody = service.traverse(
        TraverseGraphQuery(query = "body-note", branches = listOf("state"), includeBodies = true),
      )

      withBody.nodes.first().body shouldBe node.body
      withBody.estimatedTokens shouldBeGreaterThan metadataOnly.estimatedTokens
    }

    test("include_bodies prunes hydrated body that exceeds tight budget") {
      val entry = traversalEntry(
        TraversalEntrySpec(
          id = "state/preferences/large-body-note",
          subject = "large body",
          bodyTokenEstimate = 1,
        ),
      )
      val node = VaultNodeFixtures.stateNode(
        id = entry.id.value,
        body = "large hydrated body ".repeat(80),
      )
      val index = testIndex(mapOf("state" to listOf(entry)))
      val vault = testVault(nodes = mapOf(entry.id to node))
      val service = newService(index, vault)
      val metadataOnly = service.traverse(
        TraverseGraphQuery(query = "large-body-note", branches = listOf("state")),
      )

      val withBody = service.traverse(
        TraverseGraphQuery(
          query = "large-body-note",
          branches = listOf("state"),
          includeBodies = true,
          budgetTokens = metadataOnly.estimatedTokens + entry.bodyTokenEstimate,
        ),
      )

      withBody.nodes shouldBe emptyList()
      withBody.pruned.map { it.reason } shouldContain TraversalPrunedReason.BudgetTokens
      (withBody.estimatedTokens <= metadataOnly.estimatedTokens + entry.bodyTokenEstimate) shouldBe true
      coVerify(exactly = 1) { vault.findNode(entry.id) }
    }

    test("include_bodies skips hydration when indexed body estimate cannot fit") {
      val entry = traversalEntry(
        TraversalEntrySpec(
          id = "state/preferences/indexed-large-body-note",
          subject = "indexed large body",
          bodyTokenEstimate = 1_000,
        ),
      )
      val node = VaultNodeFixtures.stateNode(id = entry.id.value, body = "small body")
      val index = testIndex(mapOf("state" to listOf(entry)))
      val vault = testVault(nodes = mapOf(entry.id to node))
      val service = newService(index, vault)
      val metadataOnly = service.traverse(
        TraverseGraphQuery(query = "indexed-large-body-note", branches = listOf("state")),
      )

      val withBody = service.traverse(
        TraverseGraphQuery(
          query = "indexed-large-body-note",
          branches = listOf("state"),
          includeBodies = true,
          budgetTokens = metadataOnly.estimatedTokens,
        ),
      )

      withBody.nodes shouldBe emptyList()
      withBody.pruned.map { it.reason } shouldContain TraversalPrunedReason.BudgetTokens
      coVerify(exactly = 0) { vault.findNode(entry.id) }
    }

    test("absolute path query can resolve through repository path lookup") {
      val entry = traversalEntry(TraversalEntrySpec(id = "state/preferences/path-note", subject = "path note"))
      val absolutePath = "/Users/sermilion/vault/state/preferences/path-note.md"
      val index = testIndex(
        entriesByBranch = mapOf("state" to listOf(entry)),
        pathLookups = mapOf(absolutePath to entry),
      )
      val service = newService(index)

      val outcome = service.traverse(TraverseGraphQuery(query = absolutePath, branches = listOf("state")))

      outcome.entrypoints.map { it.id.value } shouldBe listOf(entry.id.value)
      outcome.nodes.map { it.id.value } shouldContain entry.id.value
    }

    test("absolute path query retries path lookup after branch warming") {
      val entry = traversalEntry(
        TraversalEntrySpec(id = "state/preferences/cold-path-note", subject = "cold path note"),
      )
      val absolutePath = "/Users/sermilion/vault/state/preferences/cold-path-note.md"
      val warmedBranches = mutableSetOf<String>()
      val index = mockk<GraphIndexRepository>()
      coEvery { index.listEntriesInBranch(any()) } answers {
        val branch = invocation.args[0] as String
        warmedBranches += branch
        if (branch == "state") listOf(entry) else emptyList()
      }
      coEvery { index.findEntry(any()) } returns entry
      coEvery { index.findEntryByPath(absolutePath) } answers {
        if ("state" in warmedBranches) entry else null
      }
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      val service = newService(index)

      val outcome = service.traverse(TraverseGraphQuery(query = absolutePath, branches = listOf("state")))

      outcome.entrypoints.map { it.id.value } shouldBe listOf(entry.id.value)
      outcome.nodes.map { it.id.value } shouldContain entry.id.value
      coVerify(exactly = 2) { index.findEntryByPath(absolutePath) }
    }

    test("cold absolute path query can replace full start id entrypoint set") {
      val starters = (1..4).map {
        traversalEntry(TraversalEntrySpec(id = "state/preferences/start-$it", subject = "starter $it"))
      }
      val exact = traversalEntry(TraversalEntrySpec(id = "state/preferences/exact", subject = "exact path"))
      val absolutePath = "/Users/sermilion/vault/state/preferences/exact.md"
      val entriesById = (starters + exact).associateBy { it.id }
      val warmedBranches = mutableSetOf<String>()
      val index = mockk<GraphIndexRepository>()
      coEvery { index.findEntry(any()) } answers { entriesById[nodeIdArg(invocation.args[0])] }
      coEvery { index.listEntriesInBranch(any()) } answers {
        val branch = invocation.args[0] as String
        warmedBranches += branch
        if (branch == "state") starters + exact else emptyList()
      }
      coEvery { index.findEntryByPath(absolutePath) } answers {
        if ("state" in warmedBranches) exact else null
      }
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      val service = newService(index)

      val outcome = service.traverse(
        TraverseGraphQuery(
          query = absolutePath,
          startIds = starters.map { it.id },
          branches = listOf("state"),
          maxNodes = 1,
        ),
      )

      outcome.entrypoints.map { it.id.value } shouldContain exact.id.value
      outcome.nodes.map { it.id.value } shouldBe listOf(exact.id.value)
      coVerify(exactly = 2) { index.findEntryByPath(absolutePath) }
    }

    test("data search component provides traverse graph service binding") {
      val component = TestDataSearchComponent::class.create(
        graphIndexRepository = testIndex(emptyMap()),
        vaultRepository = testVault(),
        dispatcherProvider = TestDispatcherProvider(),
      )

      val provided: TraverseGraphService = component.traverseGraphService

      provided shouldNotBe null
    }
  })
