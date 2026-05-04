package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.search.TraversalEdge
import com.sermilion.personalgraph.domain.search.TraversalEdgeType
import com.sermilion.personalgraph.domain.search.TraversalEntrypoint
import com.sermilion.personalgraph.domain.search.TraversalNode
import com.sermilion.personalgraph.domain.search.TraversalPrunedCandidate
import com.sermilion.personalgraph.domain.search.TraversalPrunedReason
import com.sermilion.personalgraph.domain.search.TraversalRankBy
import com.sermilion.personalgraph.domain.search.TraversalSuggestedRead
import com.sermilion.personalgraph.domain.search.TraverseGraphOutcome
import com.sermilion.personalgraph.domain.search.TraverseGraphQuery
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int

class VaultMcpToolsTraverseGraphTest :
  FunSpec({

    test("traverse_graph forwards parsed args and formats entrypoints nodes edges pruned reads and tokens") {
      val ctx = newVaultMcpToolsTestContext()
      val captured = slot<TraverseGraphQuery>()
      coEvery { ctx.traverse.traverse(capture(captured)) } returns TraverseGraphOutcome(
        entrypoints = listOf(
          TraversalEntrypoint(
            id = NodeId("domains/work/skill-bill/subjects/skill-bill-skill-33-pr-91-and-pr"),
            reason = "query",
            score = 100,
          ),
        ),
        nodes = listOf(
          TraversalNode(
            id = NodeId("domains/work/skill-bill/events/skill-33-pr91-pr92-merged-2026-05-02"),
            type = "episode",
            domain = "work/skill-bill",
            subject = "skill-33 merge",
            snippet = "Merged the two SKILL-33 pull requests in skill-bill...",
            score = 91,
            depth = 1,
            matchFields = listOf("subject_evidence"),
            body = "Merged the two SKILL-33 pull requests in skill-bill with index-first follow-ups.",
          ),
        ),
        edges = listOf(
          TraversalEdge(
            from = NodeId("domains/work/skill-bill/subjects/skill-bill-skill-33-pr-91-and-pr"),
            to = NodeId("domains/work/skill-bill/events/skill-33-pr91-pr92-merged-2026-05-02"),
            type = TraversalEdgeType.SubjectEvidence,
            label = "subject_evidence",
            weight = 90,
          ),
        ),
        pruned = listOf(
          TraversalPrunedCandidate(
            id = NodeId("domains/work/skill-bill/subjects/skill-32-technical-stabilization-plan"),
            reason = TraversalPrunedReason.BudgetTokens,
            score = 12,
            estimatedTokens = 16000,
          ),
        ),
        suggestedReads = listOf(
          TraversalSuggestedRead(
            id = NodeId("domains/work/skill-bill/subjects/skill-bill-skill-33-pr-91-and-pr"),
            reason = "BudgetTokens",
            priority = 100,
          ),
        ),
        estimatedTokens = 2600,
      )

      val args = buildJsonObject {
        put(ToolSchemas.KEY_QUERY, JsonPrimitive("SKILL-33"))
        put(
          ToolSchemas.KEY_START_IDS,
          JsonArray(listOf(JsonPrimitive("domains/work/skill-bill/subjects/skill-bill-skill-33-pr-91-and-pr"))),
        )
        put(
          ToolSchemas.KEY_BRANCHES,
          JsonArray(listOf(JsonPrimitive("domains/work/skill-bill"), JsonPrimitive("state/knowledge"))),
        )
        put(
          ToolSchemas.KEY_EDGE_TYPES,
          JsonArray(
            listOf(
              JsonPrimitive(ToolSchemas.TRAVERSAL_EDGE_TYPE_LINK),
              JsonPrimitive(ToolSchemas.TRAVERSAL_EDGE_TYPE_BACKLINK),
              JsonPrimitive(ToolSchemas.TRAVERSAL_EDGE_TYPE_SUBJECT_EVIDENCE),
              JsonPrimitive(ToolSchemas.TRAVERSAL_EDGE_TYPE_STATE),
              JsonPrimitive(ToolSchemas.TRAVERSAL_EDGE_TYPE_TIMELINE),
            ),
          ),
        )
        put(ToolSchemas.KEY_MAX_DEPTH, JsonPrimitive(2))
        put(ToolSchemas.KEY_MAX_NODES, JsonPrimitive(30))
        put(ToolSchemas.KEY_BUDGET_TOKENS, JsonPrimitive(4000))
        put(ToolSchemas.KEY_INCLUDE_BODIES, JsonPrimitive(true))
        put(
          ToolSchemas.KEY_RANK_BY,
          JsonArray(
            listOf(
              JsonPrimitive(ToolSchemas.TRAVERSAL_RANK_BY_EXACT_ID_MATCH),
              JsonPrimitive(ToolSchemas.TRAVERSAL_RANK_BY_EDGE_WEIGHT),
              JsonPrimitive(ToolSchemas.TRAVERSAL_RANK_BY_RECENCY),
              JsonPrimitive(ToolSchemas.TRAVERSAL_RANK_BY_BRANCH_RELEVANCE),
            ),
          ),
        )
      }

      val result = ctx.tools.traverseGraph(args)

      captured.captured.query shouldBe "SKILL-33"
      captured.captured.startIds shouldContainExactly listOf(
        NodeId("domains/work/skill-bill/subjects/skill-bill-skill-33-pr-91-and-pr"),
      )
      captured.captured.branches shouldContainExactly listOf("domains/work/skill-bill", "state/knowledge")
      captured.captured.edgeTypes.shouldContainExactlyInAnyOrder(
        TraversalEdgeType.Link,
        TraversalEdgeType.Backlink,
        TraversalEdgeType.SubjectEvidence,
        TraversalEdgeType.State,
        TraversalEdgeType.Timeline,
      )
      captured.captured.maxDepth shouldBe 2
      captured.captured.maxNodes shouldBe 30
      captured.captured.budgetTokens shouldBe 4000
      captured.captured.includeBodies shouldBe true
      captured.captured.rankBy shouldBe TraversalRankBy.Recency

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_OK
      val entrypoints = result[ToolSchemas.KEY_ENTRYPOINTS] as JsonArray
      entrypoints.size shouldBe 1
      val entrypoint = entrypoints[0] as JsonObject
      (entrypoint[ToolSchemas.KEY_ID] as JsonPrimitive).content shouldBe
        "domains/work/skill-bill/subjects/skill-bill-skill-33-pr-91-and-pr"
      (entrypoint[ToolSchemas.KEY_SCORE] as JsonPrimitive).int shouldBe 100

      val nodes = result[ToolSchemas.KEY_NODES] as JsonArray
      nodes.size shouldBe 1
      val node = nodes[0] as JsonObject
      (node[ToolSchemas.KEY_DISTANCE] as JsonPrimitive).int shouldBe 1
      (node[ToolSchemas.KEY_REASON] as JsonPrimitive).content shouldBe "subject_evidence"
      ((node[ToolSchemas.KEY_BODY] as JsonPrimitive).content.contains("SKILL-33 pull requests")) shouldBe true

      val edges = result[ToolSchemas.KEY_EDGES] as JsonArray
      edges.size shouldBe 1
      val edge = edges[0] as JsonObject
      (edge[ToolSchemas.KEY_LABEL] as JsonPrimitive).content shouldBe "subject_evidence"
      ((edge[ToolSchemas.KEY_REASON] as JsonPrimitive).content.contains("subject evidence")) shouldBe true

      val pruned = result[ToolSchemas.KEY_PRUNED] as JsonArray
      val prunedFirst = pruned[0] as JsonObject
      (prunedFirst[ToolSchemas.KEY_REASON] as JsonPrimitive).content shouldBe "budget_tokens"
      (prunedFirst[ToolSchemas.KEY_ESTIMATED_TOKENS] as JsonPrimitive).int shouldBe 16000

      val reads = result[ToolSchemas.KEY_SUGGESTED_READS] as JsonArray
      val suggested = reads[0] as JsonObject
      (suggested[ToolSchemas.KEY_PRIORITY] as JsonPrimitive).content shouldBe "high"
      (suggested[ToolSchemas.KEY_REASON] as JsonPrimitive).content shouldBe "pruned by budget_tokens"

      val tokens = result[ToolSchemas.KEY_ESTIMATED_TOKENS] as JsonObject
      (tokens[ToolSchemas.KEY_RESPONSE_TOTAL] as JsonPrimitive).int shouldBeGreaterThan 0
      (tokens[ToolSchemas.KEY_METADATA_TOKENS] as JsonPrimitive).int shouldBeGreaterThan 0
      (tokens[ToolSchemas.KEY_BODY_TOKENS] as JsonPrimitive).int shouldBeGreaterThan 0
      (tokens[ToolSchemas.KEY_PRUNED_BODY_TOKENS] as JsonPrimitive).int shouldBeGreaterThan 0
    }

    test("traverse_graph rejects negative max_depth before calling the service") {
      val ctx = newVaultMcpToolsTestContext()
      val args = buildJsonObject {
        put(ToolSchemas.KEY_QUERY, JsonPrimitive("SKILL-33"))
        put(ToolSchemas.KEY_MAX_DEPTH, JsonPrimitive(-1))
      }

      val result = ctx.tools.traverseGraph(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_INVALID_INPUT
      (result[ToolSchemas.KEY_FIELD] as JsonPrimitive).content shouldBe ToolSchemas.KEY_MAX_DEPTH
      coVerify(exactly = 0) { ctx.traverse.traverse(any()) }
    }

    test("traverse_graph schema exposes edge labels and pruning semantics") {
      val schema = traverseGraphSchema()

      val edgeTypesField = schema.properties!![ToolSchemas.KEY_EDGE_TYPES] as JsonObject
      val edgeTypesItems = edgeTypesField["items"] as JsonObject
      val edgeTypesEnum = edgeTypesItems["enum"] as JsonArray
      edgeTypesEnum.map { (it as JsonPrimitive).content } shouldContainExactly listOf(
        ToolSchemas.TRAVERSAL_EDGE_TYPE_LINK,
        ToolSchemas.TRAVERSAL_EDGE_TYPE_BACKLINK,
        ToolSchemas.TRAVERSAL_EDGE_TYPE_SUBJECT_EVIDENCE,
        ToolSchemas.TRAVERSAL_EDGE_TYPE_TIMELINE,
        ToolSchemas.TRAVERSAL_EDGE_TYPE_STATE,
        ToolSchemas.TRAVERSAL_EDGE_TYPE_PATTERN,
        ToolSchemas.TRAVERSAL_EDGE_TYPE_CONTRADICTION,
        ToolSchemas.TRAVERSAL_EDGE_TYPE_BACKGROUND,
      )

      val rankByField = schema.properties!![ToolSchemas.KEY_RANK_BY] as JsonObject
      val rankByDescription = (rankByField["description"] as JsonPrimitive).content
      rankByDescription.contains("exact_id_match") shouldBe true
      rankByDescription.contains("recency") shouldBe true
      rankByDescription.contains("branch_relevance") shouldBe true
    }
  })
