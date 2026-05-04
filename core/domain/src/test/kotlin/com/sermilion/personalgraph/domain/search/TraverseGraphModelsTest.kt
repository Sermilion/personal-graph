package com.sermilion.personalgraph.domain.search

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class TraverseGraphModelsTest :
  FunSpec({

    test("traverse graph query has conservative parser-ready defaults") {
      val query = TraverseGraphQuery()

      query.query shouldBe ""
      query.startIds shouldBe emptyList()
      query.branches shouldBe emptyList()
      query.edgeTypes shouldBe TraversalEdgeType.DEFAULTS
      query.maxDepth shouldBe TraverseGraphQuery.DEFAULT_MAX_DEPTH
      query.maxNodes shouldBe TraverseGraphQuery.DEFAULT_MAX_NODES
      query.budgetTokens shouldBe TraverseGraphQuery.DEFAULT_BUDGET_TOKENS
      query.includeBodies shouldBe false
      query.rankBy shouldBe TraversalRankBy.Relevance
    }

    test("traversal edge classification includes the graph relationship vocabulary") {
      TraversalEdgeType.entries.shouldContainExactlyInAnyOrder(
        TraversalEdgeType.Link,
        TraversalEdgeType.Backlink,
        TraversalEdgeType.SubjectEvidence,
        TraversalEdgeType.Timeline,
        TraversalEdgeType.State,
        TraversalEdgeType.Pattern,
        TraversalEdgeType.Contradiction,
        TraversalEdgeType.Background,
      )
    }
  })
