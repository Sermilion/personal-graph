package com.sermilion.personalgraph.data.search

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
import io.mockk.mockk
import kotlinx.datetime.Instant

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
      outcome.edges.map { it.label }.shouldContainExactlyInAnyOrder(
        "subject_evidence",
        "timeline",
        "pattern",
        "background",
        "link",
        "contradiction",
        "state",
      )
      outcome.estimatedTokens shouldBeGreaterThan 0
    }

    test("backlink edge type is collected from warmed graph links") {
      val start = traversalEntry(TraversalEntrySpec(id = "state/preferences/start"))
      val backlink = traversalEntry(
        TraversalEntrySpec(
          id = "domains/work/project/generic-note",
          type = "note",
          links = listOf(start.id),
        ),
      )
      val index = testIndex(mapOf("state" to listOf(start), "domains" to listOf(backlink)))
      val service = newService(index)

      val outcome = service.traverse(
        TraverseGraphQuery(
          startIds = listOf(start.id),
          branches = listOf("state", "domains"),
          edgeTypes = setOf(TraversalEdgeType.Backlink),
          maxDepth = 1,
        ),
      )

      outcome.edges.map { it.label } shouldBe listOf("backlink")
      outcome.nodes.map { it.id.value } shouldContain backlink.id.value
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

    test("ranking boosts exact direct evidence and recency while penalizing broad unrelated hubs") {
      val exact = traversalEntry(TraversalEntrySpec(id = "state/preferences/api-latency"))
      val start = traversalEntry(
        TraversalEntrySpec(
          id = "state/preferences/start",
          subject = "api",
          links = listOf(NodeId("domains/work/project/subjects/api")),
        ),
      )
      val subject = traversalEntry(
        TraversalEntrySpec(
          id = "domains/work/project/subjects/api",
          type = "subject",
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
      val broad = traversalEntry(
        TraversalEntrySpec(
          id = "domains/work/project/subjects/everything",
          type = "subject",
          subject = "api-latency",
          linkCount = 60,
        ),
      )
      val index = testIndex(
        mapOf(
          "state" to listOf(exact, start),
          "domains" to listOf(subject, broad),
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
          maxNodes = 10,
        ),
      )
      val baseline = service.traverse(
        TraverseGraphQuery(
          query = "api-latency",
          startIds = listOf(start.id),
          branches = listOf("state", "domains", "timeline"),
          maxDepth = 1,
          maxNodes = 10,
        ),
      )

      val scores = outcome.nodes.associate { it.id.value to it.score }
      val baselineScores = baseline.nodes.associate { it.id.value to it.score }
      scores.getValue(exact.id.value) shouldBeGreaterThan scores.getValue(start.id.value)
      scores.getValue(subject.id.value) shouldBeGreaterThan scores.getValue(start.id.value)
      scores.getValue(event.id.value) shouldBeGreaterThan baselineScores.getValue(event.id.value)
      scores.getValue(broad.id.value) shouldBeGreaterThan 0
      scores.getValue(start.id.value) shouldBeGreaterThan scores.getValue(broad.id.value)
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
        TraverseGraphQuery(query = "budget", branches = listOf("state"), maxNodes = 10, budgetTokens = 1),
      )

      maxNodesOutcome.nodes.size shouldBe 1
      maxNodesOutcome.pruned.map { it.reason }.distinct() shouldBe listOf(TraversalPrunedReason.MaxNodes)
      budgetOutcome.nodes shouldBe emptyList()
      budgetOutcome.pruned.map { it.reason }.distinct() shouldBe listOf(TraversalPrunedReason.BudgetTokens)
      maxNodesOutcome.suggestedReads.map { it.id.value } shouldContain entries[1].id.value
    }

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

      budgetOutcome.estimatedTokens shouldBe metadataOnly.nodes.first().let {
        TokenEstimator.estimateString(it.id.value) +
          TokenEstimator.estimateString(it.snippet) +
          TokenEstimator.estimateString(it.subject.orEmpty())
      }
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

    test("data search component provides traverse graph service binding") {
      val service = newService(testIndex(emptyMap()))
      val component = object : DataSearchComponent {}

      val provided: TraverseGraphService = component.provideTraverseGraphService(service)

      provided shouldNotBe null
    }
  })
