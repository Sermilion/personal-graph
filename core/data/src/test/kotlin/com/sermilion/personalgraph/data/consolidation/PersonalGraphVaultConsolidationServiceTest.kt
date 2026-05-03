package com.sermilion.personalgraph.data.consolidation

import com.sermilion.personalgraph.data.codec.MarkdownFrontmatterCodec
import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.data.repository.PersonalGraphVaultRepository
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.Confidence
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateCategory
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import com.sermilion.personalgraph.testing.NoOpGraphIndexInvalidator
import com.sermilion.personalgraph.testing.TestDispatcherProvider
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.nio.file.Files
import java.nio.file.Path

class PersonalGraphVaultConsolidationServiceTest :
  FunSpec({

    fun newHarness(): ConsolidationHarness {
      val root = Files.createTempDirectory("vault-consolidation-")
      val repository = PersonalGraphVaultRepository(
        vaultRoot = root,
        dispatcherProvider = TestDispatcherProvider(),
        codec = MarkdownFrontmatterCodec(),
        pathResolver = VaultPathResolver(),
        graphIndexInvalidator = NoOpGraphIndexInvalidator,
      )
      val service = PersonalGraphVaultConsolidationService(
        repository = repository,
        dispatcherProvider = TestDispatcherProvider(),
        clock = FixedClock(VaultNodeFixtures.sampleInstant),
      )
      return ConsolidationHarness(root, repository, service)
    }

    test("promotes repeated staged observations in a matching context") {
      val harness = newHarness()
      harness.writeStaged("first", "User prefers 2-space indentation.\n")
      harness.writeStaged("second", "User prefers 2-space indentation.\n")

      val report = harness.service.consolidate()

      report.graduated.shouldHaveSize(1)
      report.graduated.first().occurrenceCount shouldBe 2
      report.mergedDuplicates.shouldHaveSize(1)
      harness.repository.listNodesInBranch(VaultLayout.BRANCH_STAGING_OBSERVATIONS) shouldBe emptyList()
      val durable = harness.repository.listNodesInBranch(VaultLayout.BRANCH_STATE_PREFERENCES)
      durable.shouldHaveSize(1)
      durable.first().shouldBeInstanceOf<StateNode>()
      val durableState = durable.first() as StateNode
      durableState.sourceIds.map { it.value } shouldContain
        "staging/observations/first"
      durableState.patternLinks shouldBe emptyList()
      durableState.body.contains("[[patterns/") shouldBe false
      harness.repository.listNodesInBranch(VaultLayout.BRANCH_PATTERNS) shouldBe emptyList()
    }

    test("merges equivalent staged duplicates into one durable node") {
      val harness = newHarness()
      harness.writeStaged("first", "User prefers quiet CLI output.\n")
      harness.writeStaged("second", "User prefers quiet CLI output.\n")
      harness.writeStaged("third", "User prefers quiet CLI output.\n")

      val report = harness.service.consolidate()

      report.graduated.shouldHaveSize(1)
      report.mergedDuplicates.shouldHaveSize(1)
      report.mergedDuplicates.first().sourceIds.shouldHaveSize(3)
      harness.repository.listNodesInBranch(VaultLayout.BRANCH_STATE_PREFERENCES).shouldHaveSize(1)
    }

    test("increments occurrence count when staged observation matches an existing durable node") {
      val harness = newHarness()
      val existing = VaultNodeFixtures.stateNode(
        id = "state/preferences/compact-status",
        body = "User prefers compact status updates.\n",
        links = listOf(NodeId("domains/work/capmo/events/source-first")),
      )
      harness.repository.writeNode(existing) shouldBe WriteOutcome.Applied
      harness.writeStaged("second", "User prefers compact status updates.\n")

      val report = harness.service.consolidate()

      report.graduated.shouldHaveSize(1)
      report.graduated.first().occurrenceCount shouldBe 2
      val durable = harness.repository.findNode(existing.id)
      durable.shouldBeInstanceOf<StateNode>()
      durable.occurrenceCount shouldBe 2
      durable.sourceIds.map { it.value } shouldBe listOf("staging/observations/second")
    }

    test("promotes pattern hub when observation reaches occurrence threshold") {
      val harness = newHarness()
      harness.writeStaged("one", "User tends to normalize messy inputs before comparing them.\n")
      harness.writeStaged("two", "User tends to normalize messy inputs before comparing them.\n")
      harness.writeStaged("three", "User tends to normalize messy inputs before comparing them.\n")

      val report = harness.service.consolidate()

      report.promotedPatterns.shouldHaveSize(1)
      report.promotedPatterns.first().evidenceCount shouldBe 3
      val patterns = harness.repository.listNodesInBranch(VaultLayout.BRANCH_PATTERNS)
      patterns.shouldHaveSize(1)
      patterns.first().shouldBeInstanceOf<PatternNode>()
      val evidence = harness.repository.listNodesInBranch(VaultLayout.BRANCH_STATE_PREFERENCES).first() as StateNode
      evidence.patternLinks.map { it.value } shouldContain report.promotedPatterns.first().nodeId.value
      evidence.body shouldContain "[[${report.promotedPatterns.first().nodeId.value}]]"
    }

    test("promotes pattern hub when existing durable occurrence count reaches threshold with staged match") {
      val harness = newHarness()
      val existing = VaultNodeFixtures.stateNode(
        id = "state/preferences/normalizes-inputs",
        body = "User tends to normalize inputs before comparing them.\n",
      ).copy(occurrenceCount = 2)
      harness.repository.writeNode(existing) shouldBe WriteOutcome.Applied
      harness.writeStaged("third", "User tends to normalize inputs before comparing them.\n")

      val report = harness.service.consolidate()

      report.promotedPatterns.shouldHaveSize(1)
      report.promotedPatterns.first().evidenceCount shouldBe 3
      val durable = harness.repository.findNode(existing.id)
      durable.shouldBeInstanceOf<StateNode>()
      durable.occurrenceCount shouldBe 3
      durable.patternLinks.map { it.value } shouldContain report.promotedPatterns.first().nodeId.value
    }

    test("promotes pattern hub when observation appears in two domains") {
      val harness = newHarness()
      harness.writeStaged(
        id = "work",
        body = "User prefers written decisions before implementation.\n",
        links = listOf(NodeId("domains/work/capmo/events/design-review")),
      )
      harness.writeStaged(
        id = "personal",
        body = "User prefers written decisions before implementation.\n",
        links = listOf(NodeId("domains/personal/events/project-note")),
      )

      val report = harness.service.consolidate()

      report.promotedPatterns.shouldHaveSize(1)
      report.promotedPatterns.first().domainsSeenIn shouldBe listOf("personal", "work/capmo")
    }

    test("promotes existing durable match when staged sighting adds a second domain") {
      val harness = newHarness()
      val existing = VaultNodeFixtures.stateNode(
        id = "state/preferences/written-decisions",
        body = "User prefers written decisions before implementation.\n",
        links = listOf(NodeId("domains/work/capmo/events/design-review")),
      )
      harness.repository.writeNode(existing) shouldBe WriteOutcome.Applied
      harness.writeStaged(
        id = "personal",
        body = "User prefers written decisions before implementation.\n",
        links = listOf(NodeId("domains/personal/events/project-note")),
      )

      val report = harness.service.consolidate()

      report.graduated.shouldHaveSize(1)
      report.promotedPatterns.shouldHaveSize(1)
      report.promotedPatterns.first().domainsSeenIn shouldBe listOf("personal", "work/capmo")
      val durable = harness.repository.findNode(existing.id)
      durable.shouldBeInstanceOf<StateNode>()
      durable.occurrenceCount shouldBe 2
      durable.patternLinks.map { it.value } shouldContain report.promotedPatterns.first().nodeId.value
    }

    test("preserves existing pattern metadata when candidate scan did not include patterns") {
      val durable = VaultNodeFixtures.stateNode(
        id = "state/preferences/normalizes-data",
        body = "User tends to normalize data before comparing it.\n",
        links = listOf(NodeId("domains/work/capmo/events/source-one")),
      ).copy(occurrenceCount = 2)
      val staged = VaultNodeFixtures.stateNode(
        id = "staging/observations/third",
        body = "User tends to normalize data before comparing it.\n",
        links = listOf(NodeId("domains/work/capmo/events/source-third")),
      )
      val patternId = targetPatternId(fingerprint(durable))
      val existingPattern = VaultNodeFixtures.patternNode(
        id = patternId.value,
        body = "Custom pattern note that must survive.\n",
        hypothesis = "Existing hypothesis",
      )
      val repository = PatternFallbackRepository(
        durableNodes = listOf(durable),
        stagedNodes = listOf(staged),
        existingPattern = existingPattern,
      )
      val service = PersonalGraphVaultConsolidationService(
        repository = repository,
        dispatcherProvider = TestDispatcherProvider(),
        clock = FixedClock(VaultNodeFixtures.sampleInstant),
      )

      val report = service.consolidate()

      report.promotedPatterns.shouldHaveSize(1)
      val writtenPattern = repository.writtenNodes
        .filterIsInstance<PatternNode>()
        .first { it.id == existingPattern.id }
      writtenPattern.body shouldBe existingPattern.body
      writtenPattern.hypothesis shouldBe existingPattern.hypothesis
    }

    test("annotates contradictions against existing durable state and reports them") {
      val harness = newHarness()
      val existing = VaultNodeFixtures.stateNode(
        id = "state/preferences/editor-tabs",
        body = "User prefers tabs for indentation.\n",
      )
      harness.repository.writeNode(existing) shouldBe WriteOutcome.Applied
      harness.writeStaged("tabs-contradiction", "User does not prefer tabs for indentation.\n")

      val report = harness.service.consolidate()

      report.annotatedContradictions.shouldHaveSize(1)
      report.annotatedContradictions.first().nodeId shouldBe existing.id
      report.annotatedContradictions.first().contradictedNodeId shouldBe existing.id
      val annotated = harness.repository.findNode(existing.id)
      annotated.shouldNotBeNull()
      annotated.shouldBeInstanceOf<StateNode>()
      annotated.contradictedBy.map { it.value } shouldContain "staging/observations/tabs-contradiction"
      annotated.body shouldContain "Contradiction noted"
      report.graduated shouldBe emptyList()
    }

    test("does not re-report already annotated contradictions or promote their staged source") {
      val harness = newHarness()
      val existing = VaultNodeFixtures.stateNode(
        id = "state/preferences/editor-tabs",
        body = "User prefers tabs for indentation.\n",
      )
      harness.repository.writeNode(existing) shouldBe WriteOutcome.Applied
      harness.writeStaged("tabs-contradiction", "User does not prefer tabs for indentation.\n")

      harness.service.consolidate().annotatedContradictions.shouldHaveSize(1)
      val secondReport = harness.service.consolidate()

      secondReport.annotatedContradictions shouldBe emptyList()
      secondReport.graduated shouldBe emptyList()
      val staged = harness.repository.listNodesInBranch(VaultLayout.BRANCH_STAGING_OBSERVATIONS)
      staged.map { it.id.value } shouldBe listOf("staging/observations/tabs-contradiction")
    }

    test("does not read sensitive staging or people branches during consolidation") {
      val harness = newHarness()
      harness.writeStaged("only-observation", "User prefers compact summaries.\n")
      harness.repository.writeNode(
        VaultNodeFixtures.stateNode(
          id = "${VaultLayout.BRANCH_STAGING_SENSITIVE}/compact-summary",
          body = "User prefers compact summaries.\n",
        ),
      ) shouldBe WriteOutcome.Applied
      writeRawStateNode(
        harness.root.resolve("${VaultLayout.BRANCH_PEOPLE}/compact-summary.md"),
        "User prefers compact summaries.\n",
      )

      val report = harness.service.consolidate()

      report.graduated shouldBe emptyList()
      harness.repository.listNodesInBranch(VaultLayout.BRANCH_STAGING_OBSERVATIONS)
        .map { it.id.value } shouldBe listOf("staging/observations/only-observation")
      harness.repository.listNodesInBranch(VaultLayout.BRANCH_STATE_PREFERENCES) shouldBe emptyList()
      Files.exists(harness.root.resolve("${VaultLayout.BRANCH_STAGING_SENSITIVE}/compact-summary.md")) shouldBe true
      Files.exists(harness.root.resolve("${VaultLayout.BRANCH_PEOPLE}/compact-summary.md")) shouldBe true
    }

    test("migrates legacy domain notes into canonical subject hubs") {
      val harness = newHarness()
      val legacy = VaultNodeFixtures.episodeNode().copy(
        id = NodeId("domains/work/capmo/notes/build-pipeline"),
        topic = "Build Pipeline",
        body = "Legacy note details.\nSecond line.\n",
      )
      harness.repository.writeNode(legacy) shouldBe WriteOutcome.Applied

      val report = harness.service.consolidate()

      report.migratedLegacyNotes.shouldHaveSize(1)
      report.migratedLegacyNotes.first().migratedFrom shouldBe legacy.id
      harness.repository.findNode(NodeId("domains/work/capmo/subjects/build-pipeline"))
        .shouldBeInstanceOf<SubjectNode>()
      harness.repository.findNode(legacy.id) shouldBe null
    }
  })

private data class ConsolidationHarness(
  val root: Path,
  val repository: PersonalGraphVaultRepository,
  val service: PersonalGraphVaultConsolidationService,
) {
  suspend fun writeStaged(
    id: String,
    body: String,
    links: List<NodeId> = listOf(NodeId("domains/work/capmo/events/source-$id")),
  ) {
    val node = VaultNodeFixtures.stateNode(
      id = "${VaultLayout.BRANCH_STAGING_OBSERVATIONS}/$id",
      body = body,
      category = StateCategory.Preference,
      confidence = Confidence.Medium,
      links = links,
    )
    repository.writeNode(node) shouldBe WriteOutcome.Applied
  }
}

private class FixedClock(private val instant: Instant) : Clock {
  override fun now(): Instant = instant
}

private class PatternFallbackRepository(
  private val durableNodes: List<StateNode>,
  private val stagedNodes: List<StateNode>,
  private val existingPattern: PatternNode,
) : VaultRepository {
  val writtenNodes: MutableList<VaultNode> = mutableListOf()

  override fun observeNode(id: NodeId): Flow<VaultNode?> = flowOf(null)

  override fun observeNodesInBranch(branchPath: String): Flow<List<VaultNode>> = flowOf(emptyList())

  override suspend fun findNode(id: NodeId): VaultNode? = if (id == existingPattern.id) existingPattern else null

  override suspend fun listNodesInBranch(branchPath: String): List<VaultNode> = when (branchPath) {
    VaultLayout.BRANCH_STATE -> durableNodes
    VaultLayout.BRANCH_PATTERNS -> emptyList()
    else -> emptyList()
  }

  override suspend fun listMapNodesInBranch(
    branchPath: String,
    bodyWordLimit: Int,
  ): List<VaultNode> = listNodesInBranch(branchPath)

  override suspend fun listStagedObservations(): List<StateNode> = stagedNodes

  override suspend fun listSubjectHubs(domain: String): List<SubjectNode> = emptyList()

  override suspend fun findSubjectHub(
    domain: String,
    subjectKey: String,
    aliases: List<String>,
  ): SubjectNode? = null

  override suspend fun writeNode(node: VaultNode): WriteOutcome {
    writtenNodes.add(node)
    return WriteOutcome.Applied
  }

  override suspend fun moveNode(id: NodeId, newBranchPath: String): WriteOutcome = WriteOutcome.NotFound

  override suspend fun deleteNode(id: NodeId): WriteOutcome = WriteOutcome.Applied

  override suspend fun listBacklinks(id: NodeId): List<VaultNode> = emptyList()
}

private fun writeRawStateNode(target: Path, body: String) {
  Files.createDirectories(target.parent)
  Files.writeString(
    target,
    """
    ---
    type: "state"
    category: "preference"
    confidence: "medium"
    created: "2026-04-24"
    updated: "2026-04-24"
    ---
    $body
    """.trimIndent(),
  )
}
