package com.sermilion.personalgraph.data.search

import com.sermilion.personalgraph.domain.graph.GraphIndexEntry
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.repository.GraphIndexRepository
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.search.BranchListMode
import com.sermilion.personalgraph.domain.search.BranchListOutcome
import com.sermilion.personalgraph.domain.search.BranchListQuery
import com.sermilion.personalgraph.domain.tokens.TokenEstimator
import com.sermilion.personalgraph.testing.TestDispatcherProvider
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.Instant

private fun indexEntry(
  id: String,
  links: List<NodeId> = emptyList(),
  type: String = "state",
): GraphIndexEntry = GraphIndexEntry(
  id = NodeId(id),
  branch = id.substringBeforeLast('/'),
  type = type,
  category = null,
  domain = null,
  scope = null,
  scopes = emptyList(),
  subject = null,
  topic = null,
  aliases = emptyList(),
  hypothesis = null,
  date = null,
  updated = Instant.parse("2026-05-01T00:00:00Z"),
  created = Instant.parse("2026-05-01T00:00:00Z"),
  links = links,
  linkCount = links.size,
  snippet = "snippet for $id",
  bodyTokenEstimate = 4,
  fileSize = 128,
  fileModifiedAt = Instant.parse("2026-05-01T00:00:00Z"),
)

class PersonalGraphBranchListingServiceTest :
  FunSpec({

    fun newService(
      vault: VaultRepository = mockk(),
      index: GraphIndexRepository = mockk(),
    ): PersonalGraphBranchListingService = PersonalGraphBranchListingService(
      vaultRepository = vault,
      graphIndexRepository = index,
      tokenEstimator = TokenEstimator,
      dispatcherProvider = TestDispatcherProvider(),
    )

    test("index mode filters out blocked links from each entry") {
      val branch = "state/preferences"
      val allowed = indexEntry(
        id = "$branch/keep",
        links = listOf(NodeId("people/alice"), NodeId("$branch/sibling"), NodeId("staging/sensitive/leak")),
      )
      val index = mockk<GraphIndexRepository>()
      coEvery { index.listEntriesInBranch(branch) } returns listOf(allowed)
      val service = newService(index = index)

      val outcome = service.list(
        BranchListQuery(
          branch = branch,
          mode = BranchListMode.Index,
          filter = null,
          limit = null,
          includeLinks = true,
          includeBody = false,
        ),
      )

      outcome.shouldBeInstanceOfBranchListIndex()
      val entry = (outcome as BranchListOutcome.Index).entries.first()
      val linkValues = entry.links.map { it.value }
      linkValues shouldContain "$branch/sibling"
      linkValues shouldNotContain "people/alice"
      linkValues shouldNotContain "staging/sensitive/leak"
    }

    test("index mode filters out index-excluded entry ids") {
      val branch = "state/preferences"
      val allowed = indexEntry(id = "$branch/keep")
      val sensitive = indexEntry(id = "staging/sensitive/leaked")
      val index = mockk<GraphIndexRepository>()
      coEvery { index.listEntriesInBranch(branch) } returns listOf(allowed, sensitive)
      val service = newService(index = index)

      val outcome = service.list(
        BranchListQuery(
          branch = branch,
          mode = BranchListMode.Index,
          filter = null,
          limit = null,
          includeLinks = false,
          includeBody = false,
        ),
      )

      val entries = (outcome as BranchListOutcome.Index).entries
      entries.map { it.id.value } shouldBe listOf("$branch/keep")
    }

    test("full mode filters out read-blocked nodes") {
      val branch = "state/preferences"
      val allowed = VaultNodeFixtures.stateNode(id = "$branch/keep", body = "stay")
      val blocked = VaultNodeFixtures.stateNode(id = "people/alice", body = "secret")
      val vault = mockk<VaultRepository>()
      coEvery { vault.listNodesInBranch(branch) } returns listOf(allowed, blocked)
      val service = newService(vault = vault)

      val outcome = service.list(
        BranchListQuery(
          branch = branch,
          mode = BranchListMode.Full,
          filter = null,
          limit = null,
          includeLinks = false,
          includeBody = true,
        ),
      )

      val nodeIds = (outcome as BranchListOutcome.Full).nodes.map { it.id.value }
      nodeIds shouldBe listOf("$branch/keep")
    }

    test("limit caps the number of returned entries") {
      val branch = "state/preferences"
      val entries = (1..5).map { indexEntry(id = "$branch/entry-$it") }
      val index = mockk<GraphIndexRepository>()
      coEvery { index.listEntriesInBranch(branch) } returns entries
      val service = newService(index = index)

      val outcome = service.list(
        BranchListQuery(
          branch = branch,
          mode = BranchListMode.Index,
          filter = null,
          limit = 2,
          includeLinks = false,
          includeBody = false,
        ),
      )

      (outcome as BranchListOutcome.Index).entries.size shouldBe 2
    }

    test("filter applies substring match on entry ids") {
      val branch = "domains/work/skill-bill/events"
      val target = indexEntry(id = "$branch/SKILL-33-fix")
      val unrelated = indexEntry(id = "$branch/PG-9-other")
      val index = mockk<GraphIndexRepository>()
      coEvery { index.listEntriesInBranch(branch) } returns listOf(target, unrelated)
      val service = newService(index = index)

      val outcome = service.list(
        BranchListQuery(
          branch = branch,
          mode = BranchListMode.Index,
          filter = "SKILL-33",
          limit = null,
          includeLinks = false,
          includeBody = false,
        ),
      )

      (outcome as BranchListOutcome.Index).entries.map { it.id.value } shouldBe listOf(target.id.value)
    }
  })

private fun BranchListOutcome.shouldBeInstanceOfBranchListIndex() {
  check(this is BranchListOutcome.Index) { "expected Index outcome but was $this" }
}
