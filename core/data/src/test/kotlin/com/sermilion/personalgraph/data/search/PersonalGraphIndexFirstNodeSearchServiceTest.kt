package com.sermilion.personalgraph.data.search

import com.sermilion.personalgraph.domain.graph.GraphIndexEntry
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.repository.GraphIndexRepository
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.search.SearchField
import com.sermilion.personalgraph.domain.search.SearchQuery
import com.sermilion.personalgraph.domain.search.SearchRankingTier
import com.sermilion.personalgraph.domain.tokens.TokenEstimator
import com.sermilion.personalgraph.testing.TestDispatcherProvider
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.Instant

private data class StateEntrySpec(
  val id: String,
  val branch: String? = null,
  val category: String? = "preference",
  val domain: String? = null,
  val scope: String? = null,
  val subject: String? = null,
  val topic: String? = null,
  val aliases: List<String> = emptyList(),
  val hypothesis: String? = null,
  val links: List<NodeId> = emptyList(),
  val snippet: String? = null,
)

private fun stateEntry(spec: StateEntrySpec): GraphIndexEntry = GraphIndexEntry(
  id = NodeId(spec.id),
  branch = spec.branch ?: spec.id.substringBeforeLast('/'),
  type = "state",
  category = spec.category,
  domain = spec.domain,
  scope = spec.scope,
  scopes = emptyList(),
  subject = spec.subject,
  topic = spec.topic,
  aliases = spec.aliases,
  hypothesis = spec.hypothesis,
  date = null,
  updated = Instant.parse("2026-05-01T00:00:00Z"),
  created = Instant.parse("2026-04-24T00:00:00Z"),
  links = spec.links,
  linkCount = spec.links.size,
  snippet = spec.snippet ?: "snippet for ${spec.id}",
  bodyTokenEstimate = 4,
  fileSize = 256,
  fileModifiedAt = Instant.parse("2026-05-01T00:00:00Z"),
)

private fun stateEntry(
  id: String,
  branch: String? = null,
  subject: String? = null,
  domain: String? = null,
  links: List<NodeId> = emptyList(),
  snippet: String? = null,
): GraphIndexEntry = stateEntry(
  StateEntrySpec(
    id = id,
    branch = branch,
    subject = subject,
    domain = domain,
    links = links,
    snippet = snippet,
  ),
)

class PersonalGraphIndexFirstNodeSearchServiceTest :
  FunSpec({

    fun newService(
      index: GraphIndexRepository = mockk(),
      vault: VaultRepository = mockk(),
    ): PersonalGraphIndexFirstNodeSearchService = PersonalGraphIndexFirstNodeSearchService(
      graphIndexRepository = index,
      vaultRepository = vault,
      tokenEstimator = TokenEstimator,
      dispatcherProvider = TestDispatcherProvider(),
    )

    test("id-match search does not decode unrelated bodies via VaultRepository") {
      val target = stateEntry(id = "state/preferences/editor-indent")
      val other = stateEntry(id = "state/preferences/keyboard-layout")
      val index = mockk<GraphIndexRepository>()
      val vault = mockk<VaultRepository>()
      coEvery { index.findEntryByPath(any()) } returns null
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      coEvery { index.listEntriesInBranch(any()) } returns emptyList()
      coEvery { index.listEntriesInBranch("state") } returns listOf(target, other)
      val service = newService(index, vault)

      val outcome = service.search(SearchQuery(query = "editor-indent", branches = listOf("state")))

      outcome.hits.map { it.id.value } shouldContain "state/preferences/editor-indent"
      outcome.plan.metadataIndexUsed shouldBe true
      outcome.plan.bodyFallbackUsed shouldBe false
      coVerify(exactly = 0) { vault.findNode(any()) }
    }

    test("body fallback engages only when metadata insufficient") {
      val entry = stateEntry(id = "state/preferences/note", snippet = "irrelevant snippet")
      val node = VaultNodeFixtures.stateNode(
        id = entry.id.value,
        body = "deep inside the body lives the keyword frobnicate",
      )
      val index = mockk<GraphIndexRepository>()
      val vault = mockk<VaultRepository>()
      coEvery { index.findEntryByPath(any()) } returns null
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      coEvery { index.listEntriesInBranch(any()) } returns emptyList()
      coEvery { index.listEntriesInBranch("state") } returns listOf(entry)
      coEvery { vault.findNode(entry.id) } returns node
      val service = newService(index, vault)

      val outcome = service.search(
        SearchQuery(
          query = "frobnicate",
          branches = listOf("state"),
          bodyFallback = true,
        ),
      )

      outcome.hits.map { it.id.value } shouldContain entry.id.value
      outcome.plan.bodyFallbackUsed shouldBe true
    }

    test("body_fallback=false suppresses body scan even when metadata empty") {
      val entry = stateEntry(id = "state/preferences/quiet")
      val index = mockk<GraphIndexRepository>()
      val vault = mockk<VaultRepository>()
      coEvery { index.findEntryByPath(any()) } returns null
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      coEvery { index.listEntriesInBranch(any()) } returns emptyList()
      coEvery { index.listEntriesInBranch("state") } returns listOf(entry)
      val service = newService(index, vault)

      val outcome = service.search(
        SearchQuery(
          query = "totally-absent-token",
          branches = listOf("state"),
          bodyFallback = false,
        ),
      )

      outcome.hits shouldBe emptyList()
      outcome.plan.bodyFallbackUsed shouldBe false
      coVerify(exactly = 0) { vault.findNode(any()) }
    }

    test("recency keywords boost score") {
      val entry = stateEntry(id = "state/preferences/editor-indent")
      val index = mockk<GraphIndexRepository>()
      val vault = mockk<VaultRepository>()
      coEvery { index.findEntryByPath(any()) } returns null
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      coEvery { index.listEntriesInBranch(any()) } returns emptyList()
      coEvery { index.listEntriesInBranch("state") } returns listOf(entry)
      val service = newService(index, vault)

      val baseline = service.search(SearchQuery(query = "editor-indent", branches = listOf("state")))
      val boosted = service.search(SearchQuery(query = "editor-indent latest", branches = listOf("state")))

      baseline.hits.first().score shouldNotBe boosted.hits.first().score
      (boosted.hits.first().score > baseline.hits.first().score) shouldBe true
    }

    test("ranking tier order respected: exact id outranks subject match") {
      val exactMatch = stateEntry(id = "state/preferences/build-pipeline", subject = "unrelated")
      val subjectMatch = stateEntry(
        id = "state/preferences/something-else",
        subject = "build-pipeline",
      )
      val index = mockk<GraphIndexRepository>()
      val vault = mockk<VaultRepository>()
      coEvery { index.findEntryByPath(any()) } returns null
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      coEvery { index.listEntriesInBranch(any()) } returns emptyList()
      coEvery { index.listEntriesInBranch("state") } returns listOf(subjectMatch, exactMatch)
      val service = newService(index, vault)

      val outcome = service.search(SearchQuery(query = "build-pipeline", branches = listOf("state")))

      outcome.hits.first().id.value shouldBe exactMatch.id.value
    }

    test("VaultPolicy-blocked ids and links never appear in results") {
      val allowed = stateEntry(
        id = "state/preferences/editor-indent",
        links = listOf(NodeId("people/alice"), NodeId("state/preferences/sibling")),
      )
      val index = mockk<GraphIndexRepository>()
      val vault = mockk<VaultRepository>()
      coEvery { index.findEntryByPath(any()) } returns null
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      coEvery { index.listEntriesInBranch(any()) } returns emptyList()
      coEvery { index.listEntriesInBranch("state") } returns listOf(allowed)
      val service = newService(index, vault)

      val outcome = service.search(SearchQuery(query = "editor-indent", branches = listOf("state")))

      val hit = outcome.hits.first()
      val linkValues = hit.links.map { it.value }
      linkValues shouldNotContain "people/alice"
      linkValues shouldContain "state/preferences/sibling"
    }

    test("blocked branches passed in branches argument are ignored") {
      val index = mockk<GraphIndexRepository>()
      val vault = mockk<VaultRepository>()
      coEvery { index.findEntryByPath(any()) } returns null
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      coEvery { index.listEntriesInBranch(any()) } returns emptyList()
      val service = newService(index, vault)

      val outcome = service.search(SearchQuery(query = "alice", branches = listOf("people")))

      outcome.plan.branchesSearched shouldBe emptyList()
      outcome.hits shouldBe emptyList()
    }

    test("ranking tier order: leaf id outranks subject/topic/alias/hypothesis match") {
      val leafMatch = stateEntry(
        id = "state/preferences/build-pipeline",
        subject = "unrelated subject",
      )
      val subjectMatch = stateEntry(
        id = "state/preferences/something-else",
        subject = "build-pipeline",
      )
      val index = mockk<GraphIndexRepository>()
      val vault = mockk<VaultRepository>()
      coEvery { index.findEntryByPath(any()) } returns null
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      coEvery { index.listEntriesInBranch(any()) } returns emptyList()
      coEvery { index.listEntriesInBranch("state") } returns listOf(subjectMatch, leafMatch)
      val service = newService(index, vault)

      val outcome = service.search(SearchQuery(query = "build-pipeline", branches = listOf("state")))

      val leafScore = outcome.hits.first { it.id.value == leafMatch.id.value }.score
      val subjectScore = outcome.hits.first { it.id.value == subjectMatch.id.value }.score
      (leafScore > subjectScore) shouldBe true
    }

    test("ranking tier order: subject/topic/alias/hypothesis outranks domain/branch relevance") {
      val subjectMatch = stateEntry(
        id = "state/preferences/alpha",
        subject = "alpha-subject",
        domain = "unrelated",
      )
      val domainMatch = stateEntry(
        id = "state/preferences/beta",
        subject = "unrelated",
        domain = "alpha-subject",
      )
      val index = mockk<GraphIndexRepository>()
      val vault = mockk<VaultRepository>()
      coEvery { index.findEntryByPath(any()) } returns null
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      coEvery { index.listEntriesInBranch(any()) } returns emptyList()
      coEvery { index.listEntriesInBranch("state") } returns listOf(domainMatch, subjectMatch)
      val service = newService(index, vault)

      val outcome = service.search(SearchQuery(query = "alpha-subject", branches = listOf("state")))

      val subjectScore = outcome.hits.first { it.id.value == subjectMatch.id.value }.score
      val domainScore = outcome.hits.first { it.id.value == domainMatch.id.value }.score
      (subjectScore > domainScore) shouldBe true
    }

    test("ranking tier order: domain/branch relevance outranks body mention") {
      SearchRankingTier.DomainOrBranchRelevance.score shouldBeGreaterThan SearchRankingTier.BodyMention.score
    }

    test("staging/sensitive ids are filtered from candidates even when leaked into a branch listing") {
      val sensitive = stateEntry(id = "staging/sensitive/leaked-secret", branch = "state")
      val index = mockk<GraphIndexRepository>()
      val vault = mockk<VaultRepository>()
      coEvery { index.findEntryByPath(any()) } returns null
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      coEvery { index.listEntriesInBranch(any()) } returns emptyList()
      coEvery { index.listEntriesInBranch("state") } returns listOf(sensitive)
      val service = newService(index, vault)

      val outcome = service.search(SearchQuery(query = "leaked-secret", branches = listOf("state")))

      outcome.hits.map { it.id.value } shouldNotContain sensitive.id.value
    }

    test("staging/sensitive links on a hit are filtered out") {
      val allowed = stateEntry(
        id = "state/preferences/links-to-sensitive",
        links = listOf(
          NodeId("staging/sensitive/leak"),
          NodeId("state/preferences/sibling"),
        ),
      )
      val index = mockk<GraphIndexRepository>()
      val vault = mockk<VaultRepository>()
      coEvery { index.findEntryByPath(any()) } returns null
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      coEvery { index.listEntriesInBranch(any()) } returns emptyList()
      coEvery { index.listEntriesInBranch("state") } returns listOf(allowed)
      val service = newService(index, vault)

      val outcome = service.search(SearchQuery(query = "links-to-sensitive", branches = listOf("state")))

      val hit = outcome.hits.first()
      val linkValues = hit.links.map { it.value }
      linkValues shouldNotContain "staging/sensitive/leak"
      linkValues shouldContain "state/preferences/sibling"
    }

    test("branches argument containing staging/sensitive is silently dropped") {
      val index = mockk<GraphIndexRepository>()
      val vault = mockk<VaultRepository>()
      coEvery { index.findEntryByPath(any()) } returns null
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      coEvery { index.listEntriesInBranch(any()) } returns emptyList()
      val service = newService(index, vault)

      val outcome = service.search(SearchQuery(query = "anything", branches = listOf("staging/sensitive")))

      outcome.plan.branchesSearched shouldBe emptyList()
      outcome.hits shouldBe emptyList()
    }

    test("estimatedTokens is positive for non-empty hits and zero for empty hits") {
      val entry = stateEntry(id = "state/preferences/editor-indent")
      val index = mockk<GraphIndexRepository>()
      val vault = mockk<VaultRepository>()
      coEvery { index.findEntryByPath(any()) } returns null
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      coEvery { index.listEntriesInBranch(any()) } returns emptyList()
      coEvery { index.listEntriesInBranch("state") } returns listOf(entry)
      val service = newService(index, vault)

      val nonEmpty = service.search(SearchQuery(query = "editor-indent", branches = listOf("state")))
      val empty = service.search(SearchQuery(query = "no-such-token", branches = listOf("state"), bodyFallback = false))

      nonEmpty.estimatedTokens shouldBeGreaterThan 0
      empty.estimatedTokens shouldBe 0
    }

    test("search_fields without body suppresses body scan") {
      val entry = stateEntry(id = "state/preferences/note")
      val index = mockk<GraphIndexRepository>()
      val vault = mockk<VaultRepository>()
      coEvery { index.findEntryByPath(any()) } returns null
      coEvery { index.findEntryByTitle(any()) } returns null
      coEvery { index.findEntryByAlias(any()) } returns null
      coEvery { index.listEntriesInBranch(any()) } returns emptyList()
      coEvery { index.listEntriesInBranch("state") } returns listOf(entry)
      val service = newService(index, vault)

      val outcome = service.search(
        SearchQuery(
          query = "anything",
          branches = listOf("state"),
          searchFields = setOf(SearchField.Id, SearchField.Metadata),
        ),
      )

      outcome.plan.bodyFallbackUsed shouldBe false
      coVerify(exactly = 0) { vault.findNode(any()) }
    }
  })
