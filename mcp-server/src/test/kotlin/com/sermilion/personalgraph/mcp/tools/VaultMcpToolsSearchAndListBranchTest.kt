package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.search.BranchListEntry
import com.sermilion.personalgraph.domain.search.BranchListMode
import com.sermilion.personalgraph.domain.search.BranchListOutcome
import com.sermilion.personalgraph.domain.search.BranchListQuery
import com.sermilion.personalgraph.domain.search.BranchListTokenAccounting
import com.sermilion.personalgraph.domain.search.SearchHit
import com.sermilion.personalgraph.domain.search.SearchOutcome
import com.sermilion.personalgraph.domain.search.SearchPlan
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int

class VaultMcpToolsSearchAndListBranchTest :
  FunSpec({

    test("default list_branch shape is unchanged byte-for-byte") {
      val ctx = newVaultMcpToolsTestContext()
      val node = VaultNodeFixtures.stateNode(id = "state/preferences/editor-indent", body = "Use 2 spaces.")
      coEvery { ctx.branchListing.list(any()) } returns BranchListOutcome.Full(
        mode = BranchListMode.Full,
        nodes = listOf(node),
        estimatedTokens = BranchListTokenAccounting(metadataTokens = 1, bodyTokens = 1, prunedBodyTokens = 0),
      )

      val result = ctx.tools.listBranch(
        buildJsonObject { put(ToolSchemas.KEY_BRANCH, JsonPrimitive("state/preferences")) },
      )

      result.keys shouldBe setOf(ToolSchemas.KEY_STATUS, ToolSchemas.KEY_NODES)
      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_OK
      val nodes = result[ToolSchemas.KEY_NODES] as JsonArray
      nodes.size shouldBe 1
      val first = nodes[0] as JsonObject
      (first[ToolSchemas.KEY_ID] as JsonPrimitive).content shouldBe "state/preferences/editor-indent"
      (first[ToolSchemas.KEY_BODY] as JsonPrimitive).content shouldBe "Use 2 spaces."
      val links = first[ToolSchemas.KEY_LINKS] as JsonArray
      links.size shouldBe 0
      result[ToolSchemas.KEY_MODE] shouldBe null
      result[ToolSchemas.KEY_ESTIMATED_TOKENS] shouldBe null
    }

    test("list_branch with explicit mode=full and include_body=true returns extended schema with token accounting") {
      val ctx = newVaultMcpToolsTestContext()
      val node = VaultNodeFixtures.stateNode(id = "state/preferences/editor-indent", body = "Use 2 spaces.")
      coEvery { ctx.branchListing.list(any()) } returns BranchListOutcome.Full(
        mode = BranchListMode.Full,
        nodes = listOf(node),
        estimatedTokens = BranchListTokenAccounting(metadataTokens = 8, bodyTokens = 4, prunedBodyTokens = 0),
      )

      val args = buildJsonObject {
        put(ToolSchemas.KEY_BRANCH, JsonPrimitive("state/preferences"))
        put(ToolSchemas.KEY_MODE, JsonPrimitive(ToolSchemas.LIST_MODE_FULL))
        put(ToolSchemas.KEY_INCLUDE_BODY, JsonPrimitive(true))
      }

      val result = ctx.tools.listBranch(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_OK
      (result[ToolSchemas.KEY_MODE] as JsonPrimitive).content shouldBe ToolSchemas.LIST_MODE_FULL
      val tokens = result[ToolSchemas.KEY_ESTIMATED_TOKENS] as JsonObject
      (tokens[ToolSchemas.KEY_METADATA_TOKENS] as JsonPrimitive).int shouldBe 8
      (tokens[ToolSchemas.KEY_BODY_TOKENS] as JsonPrimitive).int shouldBe 4
      (tokens[ToolSchemas.KEY_PRUNED_BODY_TOKENS] as JsonPrimitive).int shouldBe 0
    }

    test("list_branch with mode=index returns compact entries with no body field and token accounting") {
      val ctx = newVaultMcpToolsTestContext()
      val branch = "domains/work/skill-bill/events"
      val entry = BranchListEntry(
        id = NodeId("$branch/SKILL-33-fix"),
        type = "episode",
        domain = "work/skill-bill",
        subject = "skill-33-fix",
        snippet = "snippet for SKILL-33-fix",
        matchFields = emptyList(),
        score = 0,
        links = emptyList(),
      )
      coEvery { ctx.branchListing.list(any()) } returns BranchListOutcome.Index(
        mode = BranchListMode.Index,
        entries = listOf(entry),
        estimatedTokens = BranchListTokenAccounting(metadataTokens = 12, bodyTokens = 0, prunedBodyTokens = 7),
      )

      val args = buildJsonObject {
        put(ToolSchemas.KEY_BRANCH, JsonPrimitive(branch))
        put(ToolSchemas.KEY_MODE, JsonPrimitive(ToolSchemas.LIST_MODE_INDEX))
        put(ToolSchemas.KEY_FILTER, JsonPrimitive("SKILL-33"))
      }

      val result = ctx.tools.listBranch(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_OK
      (result[ToolSchemas.KEY_MODE] as JsonPrimitive).content shouldBe ToolSchemas.LIST_MODE_INDEX
      val entries = result[ToolSchemas.KEY_ENTRIES] as JsonArray
      entries.size shouldBe 1
      val first = entries[0] as JsonObject
      (first[ToolSchemas.KEY_ID] as JsonPrimitive).content shouldBe entry.id.value
      first[ToolSchemas.KEY_BODY] shouldBe null
      val tokens = result[ToolSchemas.KEY_ESTIMATED_TOKENS] as JsonObject
      (tokens[ToolSchemas.KEY_PRUNED_BODY_TOKENS] as JsonPrimitive).int shouldBe 7
      (tokens[ToolSchemas.KEY_BODY_TOKENS] as JsonPrimitive).int shouldBe 0
    }

    test("list_branch with mode=index renders only allowed links when include_links=true") {
      val ctx = newVaultMcpToolsTestContext()
      val branch = "state/preferences"
      val entry = BranchListEntry(
        id = NodeId("$branch/keep"),
        type = "state",
        domain = null,
        subject = null,
        snippet = "snippet",
        matchFields = emptyList(),
        score = 0,
        links = listOf(NodeId("$branch/sibling")),
      )
      val capturedQuery = slot<BranchListQuery>()
      coEvery { ctx.branchListing.list(capture(capturedQuery)) } returns BranchListOutcome.Index(
        mode = BranchListMode.Index,
        entries = listOf(entry),
        estimatedTokens = BranchListTokenAccounting(metadataTokens = 5, bodyTokens = 0, prunedBodyTokens = 1),
      )

      val args = buildJsonObject {
        put(ToolSchemas.KEY_BRANCH, JsonPrimitive(branch))
        put(ToolSchemas.KEY_MODE, JsonPrimitive(ToolSchemas.LIST_MODE_INDEX))
        put(ToolSchemas.KEY_INCLUDE_LINKS, JsonPrimitive(true))
      }

      val result = ctx.tools.listBranch(args)
      capturedQuery.captured.includeLinks shouldBe true
      val entries = result[ToolSchemas.KEY_ENTRIES] as JsonArray
      val first = entries[0] as JsonObject
      val links = first[ToolSchemas.KEY_LINKS] as JsonArray
      links.map { (it as JsonPrimitive).content } shouldBe listOf("$branch/sibling")
    }

    test("list_branch rejects negative limit at the parser") {
      val ctx = newVaultMcpToolsTestContext()
      val args = buildJsonObject {
        put(ToolSchemas.KEY_BRANCH, JsonPrimitive("state/preferences"))
        put(ToolSchemas.KEY_LIMIT, JsonPrimitive(-1))
      }

      val result = ctx.tools.listBranch(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_INVALID_INPUT
      (result[ToolSchemas.KEY_FIELD] as JsonPrimitive).content shouldBe ToolSchemas.KEY_LIMIT
      coVerify(exactly = 0) { ctx.branchListing.list(any()) }
    }

    test("search_nodes output forwards estimated_tokens passthrough") {
      val ctx = newVaultMcpToolsTestContext()
      val outcome = SearchOutcome(
        hits = listOf(
          SearchHit(
            id = NodeId("state/preferences/editor-indent"),
            type = "state",
            domain = null,
            subject = null,
            matchFields = listOf("id"),
            snippet = "Use 2 spaces.",
            links = emptyList(),
            score = 100,
          ),
        ),
        plan = SearchPlan(
          metadataIndexUsed = true,
          bodyFallbackUsed = false,
          branchesSearched = listOf("state"),
        ),
        estimatedTokens = 42,
      )
      coEvery { ctx.search.search(any()) } returns outcome

      val result = ctx.tools.searchNodes(
        buildJsonObject { put(ToolSchemas.KEY_QUERY, JsonPrimitive("editor-indent")) },
      )

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_OK
      val plan = result[ToolSchemas.KEY_SEARCH_PLAN] as JsonObject
      (plan[ToolSchemas.KEY_METADATA_INDEX_USED] as JsonPrimitive).boolean shouldBe true
      (plan[ToolSchemas.KEY_BODY_FALLBACK_USED] as JsonPrimitive).boolean shouldBe false
      (result[ToolSchemas.KEY_ESTIMATED_TOKENS] as JsonPrimitive).int shouldBe 42
      val nodes = result[ToolSchemas.KEY_NODES] as JsonArray
      nodes.size shouldBe 1
    }

    test("search_nodes round-trips a non-zero estimated_tokens from the search service") {
      val ctx = newVaultMcpToolsTestContext()
      val outcome = SearchOutcome(
        hits = listOf(
          SearchHit(
            id = NodeId("state/preferences/role-policy"),
            type = "state",
            domain = "work/capmo",
            subject = "role-policy",
            matchFields = listOf("subject"),
            snippet = "Role policy",
            links = emptyList(),
            score = 60,
          ),
        ),
        plan = SearchPlan(
          metadataIndexUsed = true,
          bodyFallbackUsed = false,
          branchesSearched = listOf("state"),
        ),
        estimatedTokens = 137,
      )
      coEvery { ctx.search.search(any()) } returns outcome

      val result = ctx.tools.searchNodes(
        buildJsonObject { put(ToolSchemas.KEY_QUERY, JsonPrimitive("role-policy")) },
      )

      (result[ToolSchemas.KEY_ESTIMATED_TOKENS] as JsonPrimitive).int shouldBe 137
    }

    test("search_nodes rejects requested branch under people/ with permission_denied") {
      val ctx = newVaultMcpToolsTestContext()

      val result = ctx.tools.searchNodes(
        buildJsonObject {
          put(ToolSchemas.KEY_QUERY, JsonPrimitive("alice"))
          put(ToolSchemas.KEY_BRANCHES, JsonArray(listOf(JsonPrimitive("people"))))
        },
      )

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_PERMISSION_DENIED
      (result[ToolSchemas.KEY_BRANCH] as JsonPrimitive).content shouldBe "people"
      coVerify(exactly = 0) { ctx.search.search(any()) }
    }

    test("search_nodes rejects requested staging/sensitive branch with permission_denied") {
      val ctx = newVaultMcpToolsTestContext()

      val result = ctx.tools.searchNodes(
        buildJsonObject {
          put(ToolSchemas.KEY_QUERY, JsonPrimitive("anything"))
          put(ToolSchemas.KEY_BRANCHES, JsonArray(listOf(JsonPrimitive("staging/sensitive"))))
        },
      )

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_PERMISSION_DENIED
      (result[ToolSchemas.KEY_BRANCH] as JsonPrimitive).content shouldBe "staging/sensitive"
      coVerify(exactly = 0) { ctx.search.search(any()) }
    }

    test("search_nodes rejects negative limit at the parser") {
      val ctx = newVaultMcpToolsTestContext()
      val args = buildJsonObject {
        put(ToolSchemas.KEY_QUERY, JsonPrimitive("anything"))
        put(ToolSchemas.KEY_LIMIT, JsonPrimitive(-3))
      }

      val result = ctx.tools.searchNodes(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_INVALID_INPUT
      (result[ToolSchemas.KEY_FIELD] as JsonPrimitive).content shouldBe ToolSchemas.KEY_LIMIT
      coVerify(exactly = 0) { ctx.search.search(any()) }
    }

    test("search_nodes requires query") {
      val ctx = newVaultMcpToolsTestContext()
      val result = ctx.tools.searchNodes(JsonObject(emptyMap()))
      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_INVALID_INPUT
      (result[ToolSchemas.KEY_FIELD] as JsonPrimitive).content shouldBe ToolSchemas.KEY_QUERY
    }
  })
