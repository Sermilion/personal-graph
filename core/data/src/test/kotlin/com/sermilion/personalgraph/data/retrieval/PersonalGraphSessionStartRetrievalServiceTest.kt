package com.sermilion.personalgraph.data.retrieval

import com.sermilion.personalgraph.data.codec.MarkdownFrontmatterCodec
import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.data.repository.PersonalGraphVaultRepository
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import com.sermilion.personalgraph.domain.retrieval.RetrievalDomain
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalMode
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalRequest
import com.sermilion.personalgraph.testing.TestDispatcherProvider
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

class PersonalGraphSessionStartRetrievalServiceTest :
  FunSpec({

    fun newService(): TestContext {
      val tempDir = Files.createTempDirectory("session-start-")
      val resolver = VaultPathResolver()
      val dispatcherProvider = TestDispatcherProvider()
      val repo = PersonalGraphVaultRepository(
        vaultRoot = tempDir,
        dispatcherProvider = dispatcherProvider,
        codec = MarkdownFrontmatterCodec(),
        pathResolver = resolver,
      )
      Files.writeString(tempDir.resolve(VaultLayout.BRAIAN_FILENAME), "# Braian\nRoot context.\n")
      val service = PersonalGraphSessionStartRetrievalService(
        vaultRoot = tempDir,
        repository = repo,
        pathResolver = resolver,
        dispatcherProvider = dispatcherProvider,
      )
      return TestContext(service, repo, tempDir)
    }

    test("loads Braian first then maps classified work subtree and linked patterns") {
      val (service, repo) = newService()
      val workNode = VaultNodeFixtures.episodeNode().copy(
        id = NodeId("domains/work/capmo/events/review"),
        links = listOf(NodeId("patterns/review-shape")),
        body = "Review context with [[patterns/review-shape]].\n",
      )
      val pattern = VaultNodeFixtures.patternNode(
        id = "patterns/review-shape",
        body = "Pattern context.\n",
      )
      repo.writeNode(workNode) shouldBe WriteOutcome.Applied
      repo.writeNode(pattern) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("Capmo work please"))

      report.rootDocument?.loadOrder shouldBe 1
      report.rootDocument?.body shouldContain "Root context"
      report.classification.domain shouldBe RetrievalDomain.WorkCapmo
      report.classification.matchedTerms shouldContainExactly listOf("capmo")
      report.loadedBranches.map { it.branch } shouldContainExactly listOf(
        VaultLayout.BRANCH_STATE_PREFERENCES,
        VaultLayout.BRANCH_STATE_ROLES,
        "domains/work/capmo",
      )
      report.loadedNodes.shouldBeEmpty()
      report.loadedFullBodyContext.map { it.id } shouldContain "Braian.md"
      report.loadedFullBodyContext.map { it.id } shouldNotContain "domains/work/capmo/events/review"
      report.compactMapEntries.map { it.id } shouldContain "domains/work/capmo"
      report.compactMapEntries.map { it.id } shouldContain "domains/work/capmo/events/review"
      report.compactMapEntries.map { it.id } shouldContain "patterns/review-shape"
      report.compactMapEntries.first { it.id == "patterns/review-shape" }.reason shouldContain "pattern hub"
      report.suggestedReads.map { it.id } shouldContain "patterns/review-shape"
      report.auditEntries shouldBe report.audit
      report.audit.map { it.action } shouldContain "loaded_pattern"
      report.audit.map { it.action } shouldContain "skipped_full_branch"
    }

    test("explicit full-loading includes non-root loaded node bodies") {
      val (service, repo) = newService()
      repo.writeNode(
        VaultNodeFixtures.episodeNode().copy(
          id = NodeId("domains/work/capmo/events/review"),
          body = "Full review body.\n",
        ),
      ) shouldBe WriteOutcome.Applied

      val report = service.retrieve(
        SessionStartRetrievalRequest(
          firstSubstantiveMessage = "Capmo review",
          retrievalMode = SessionStartRetrievalMode.FullLoading,
        ),
      )

      report.loadedFullBodyContext.map { it.id } shouldContainExactly listOf(
        "Braian.md",
        "domains/work/capmo/events/review",
      )
      report.loadedFullBodyContext.first { it.id == "domains/work/capmo/events/review" }.body shouldBe
        "Full review body.\n"
    }

    test("map-first loaded full-body context keeps root only while loaded nodes remain mapped") {
      val (service, repo) = newService()
      repo.writeNode(
        VaultNodeFixtures.episodeNode().copy(id = NodeId("domains/work/capmo/events/review")),
      ) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("Capmo review"))

      report.loadedNodes.shouldBeEmpty()
      report.compactMapEntries.map { it.id } shouldContain "domains/work/capmo/events/review"
      report.loadedFullBodyContext.map { it.id } shouldContainExactly listOf("Braian.md")
    }

    test("general classification loads durable state branches and skips emotional states by default") {
      val (service, repo) = newService()
      repo.writeNode(VaultNodeFixtures.stateNode(id = "state/preferences/status-updates")) shouldBe WriteOutcome.Applied
      repo.writeNode(VaultNodeFixtures.emotionalStateNode()) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("What should we talk about next?"))

      report.classification.domain shouldBe RetrievalDomain.General
      report.loadedBranches.map { it.branch } shouldContainExactly listOf(
        VaultLayout.BRANCH_STATE_PREFERENCES,
        VaultLayout.BRANCH_STATE_ROLES,
        VaultLayout.BRANCH_STATE_KNOWLEDGE,
      )
      report.compactMapEntries.map { it.id } shouldContain "state/preferences/status-updates"
      report.compactMapEntries.map { it.id }.contains("emotional-states/2026-04-24-debug-frustration") shouldBe false
      report.skippedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_EMOTIONAL_STATES
    }

    test("emotional context explicitly includes emotional-state branch") {
      val (service, repo) = newService()
      repo.writeNode(VaultNodeFixtures.emotionalStateNode()) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("I am feeling frustrated and want reflection."))

      report.classification.emotionalContextRequested shouldBe true
      report.loadedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_EMOTIONAL_STATES
      report.compactMapEntries.map { it.id } shouldContain "emotional-states/2026-04-24-debug-frustration"
    }

    test("retrieval skips people and staging sensitive and does not follow symlinked pattern files") {
      val (service, repo, root) = newService()
      val workNode = VaultNodeFixtures.episodeNode().copy(
        id = NodeId("domains/work/capmo/events/symlink-pattern"),
        links = listOf(NodeId("patterns/secret")),
      )
      repo.writeNode(workNode) shouldBe WriteOutcome.Applied
      repo.writeNode(VaultNodeFixtures.stateNode(id = "staging/sensitive/private")) shouldBe WriteOutcome.Applied
      val outsideTarget = Files.createTempDirectory("session-start-leak-").resolve("secret.md")
      writeRaw(outsideTarget, VaultNodeFixtures.PATTERN_NODE_MARKDOWN)
      Files.createDirectories(root.resolve(VaultLayout.BRANCH_PATTERNS))
      Files.createSymbolicLink(
        root.resolve("patterns/secret.md"),
        outsideTarget,
      )

      val report = service.retrieve(SessionStartRetrievalRequest("Capmo please"))

      report.skippedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_PEOPLE
      report.skippedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_STAGING
      report.loadedNodes.map { it.id }.contains("staging/sensitive/private") shouldBe false
      report.loadedNodes.map { it.id }.contains("patterns/secret") shouldBe false
      report.compactMapEntries.map { it.id }.contains("staging/sensitive/private") shouldBe false
      report.audit.any { it.action == "skipped_pattern" && it.subject == "patterns/secret" } shouldBe true
    }

    test("classifier picks the domain with the highest match count") {
      val (service, _) = newService()

      val report = service.retrieve(
        SessionStartRetrievalRequest("song guitar drums and a tiny bit of work"),
      )

      report.classification.domain shouldBe RetrievalDomain.CreativeMusic
      report.classification.matchedTerms shouldContainExactlyInAnyOrder listOf("song", "guitar", "drums")
    }

    test("generic work terms do not beat explicit personal or creative terms") {
      val (service, _) = newService()

      val workVsPersonal = service.retrieve(SessionStartRetrievalRequest("work with family"))
      workVsPersonal.classification.domain shouldBe RetrievalDomain.Personal

      val workVsCreative = service.retrieve(SessionStartRetrievalRequest("work with song"))
      workVsCreative.classification.domain shouldBe RetrievalDomain.CreativeMusic

      val personalVsCreative = service.retrieve(SessionStartRetrievalRequest("family time with song"))
      personalVsCreative.classification.domain shouldBe RetrievalDomain.Personal
    }

    test("explicit product terms beat generic work language") {
      val (service, _) = newService()

      val report = service.retrieve(SessionStartRetrievalRequest("work on Readian"))

      report.classification.domain shouldBe RetrievalDomain.WorkReadian
      report.classification.matchedTerms shouldContainExactly listOf("readian")
    }

    test("hyphen does not act as a word boundary in compound terms like work-from-home") {
      val (service, _) = newService()

      val report = service.retrieve(
        SessionStartRetrievalRequest("work-from-home setup"),
      )

      report.classification.matchedTerms.shouldNotContain("work")
      report.classification.domain shouldBe RetrievalDomain.General
    }

    test("compound word personal-graph does not match the pruned personal term and stays General") {
      val (service, _) = newService()

      val report = service.retrieve(
        SessionStartRetrievalRequest("Tell me about personal-graph internals"),
      )

      report.classification.domain shouldBe RetrievalDomain.General
      report.classification.matchedTerms.shouldNotContain("personal")
    }

    test("pruned generic terms no longer trigger their domain") {
      val (service, _) = newService()
      val prunedSentences = listOf(
        "review the pr and the code today",
        "this is a project review",
        "let's have a meeting",
        "personal note about home",
      )

      for (message in prunedSentences) {
        val report = service.retrieve(SessionStartRetrievalRequest(message))
        report.classification.domain shouldBe RetrievalDomain.General
        report.classification.matchedTerms.shouldBeEmpty()
      }
    }

    test("expanded creative vocabulary routes to creative branch") {
      val (service, _) = newService()
      val creativeMessages = listOf(
        "let me write a song today",
        "audio recording session in the studio",
        "starting a guitar mixdown",
        "want to paint and sketch",
        "joining a band as instrumentalist",
      )

      for (message in creativeMessages) {
        val report = service.retrieve(SessionStartRetrievalRequest(message))
        report.classification.domain shouldBe RetrievalDomain.CreativeMusic
      }
    }

    test("classifier and branch planner cover all active vault domains") {
      val (service, _) = newService()
      val cases = listOf(
        Triple("Capmo work", RetrievalDomain.WorkCapmo, "domains/work/capmo"),
        Triple("Skill-bill runtime workflow", RetrievalDomain.WorkSkillBill, "domains/work/skill-bill"),
        Triple("Readian editorial article", RetrievalDomain.WorkReadian, "domains/work/readian"),
        Triple("Context app macOS shelf", RetrievalDomain.WorkContextApp, "domains/work/context-app"),
        Triple("song guitar studio", RetrievalDomain.CreativeMusic, "domains/creative/music"),
        Triple("family health habit", RetrievalDomain.Personal, "domains/personal"),
        Triple("What should we talk about next?", RetrievalDomain.General, VaultLayout.BRANCH_STATE_KNOWLEDGE),
      )

      for ((message, expectedDomain, expectedBranch) in cases) {
        val report = service.retrieve(SessionStartRetrievalRequest(message))
        report.classification.domain shouldBe expectedDomain
        report.loadedBranches.map { it.branch } shouldContain expectedBranch
      }
    }

    test("session_start always loads preferences and roles regardless of classification") {
      val (service, _) = newService()
      val classifications = listOf(
        "Capmo work" to RetrievalDomain.WorkCapmo,
        "song guitar studio" to RetrievalDomain.CreativeMusic,
        "family health habit" to RetrievalDomain.Personal,
        "What should we talk about next?" to RetrievalDomain.General,
      )

      for ((message, expected) in classifications) {
        val report = service.retrieve(SessionStartRetrievalRequest(message))
        report.classification.domain shouldBe expected
        report.loadedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_STATE_PREFERENCES
        report.loadedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_STATE_ROLES
      }
    }

    test("state knowledge branch is loaded only on General classification") {
      val (service, _) = newService()

      val workReport = service.retrieve(SessionStartRetrievalRequest("Capmo work"))
      workReport.loadedBranches.map { it.branch }.shouldNotContain(VaultLayout.BRANCH_STATE_KNOWLEDGE)

      val generalReport = service.retrieve(SessionStartRetrievalRequest("What should we talk about next?"))
      generalReport.loadedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_STATE_KNOWLEDGE
    }

    test("state branch retrieval keeps global state and filters scoped state by classified domain") {
      val (service, repo) = newService()
      repo.writeNode(VaultNodeFixtures.stateNode(id = "state/preferences/global")) shouldBe WriteOutcome.Applied
      repo.writeNode(
        VaultNodeFixtures.stateNode(
          id = "state/preferences/capmo-scope",
          scope = "work/capmo",
        ),
      ) shouldBe WriteOutcome.Applied
      repo.writeNode(
        VaultNodeFixtures.stateNode(
          id = "state/preferences/readian-scope",
          scopes = listOf("work/readian"),
        ),
      ) shouldBe WriteOutcome.Applied

      val capmoReport = service.retrieve(SessionStartRetrievalRequest("Capmo review"))
      capmoReport.compactMapEntries.map { it.id } shouldContain "state/preferences/global"
      capmoReport.compactMapEntries.map { it.id } shouldContain "state/preferences/capmo-scope"
      capmoReport.compactMapEntries.map { it.id } shouldNotContain "state/preferences/readian-scope"

      val readianReport = service.retrieve(SessionStartRetrievalRequest("Readian article"))
      readianReport.compactMapEntries.map { it.id } shouldContain "state/preferences/global"
      readianReport.compactMapEntries.map { it.id } shouldContain "state/preferences/readian-scope"
      readianReport.compactMapEntries.map { it.id } shouldNotContain "state/preferences/capmo-scope"
    }

    test("general retrieval excludes scoped state from broad state branches") {
      val (service, repo) = newService()
      repo.writeNode(VaultNodeFixtures.stateNode(id = "state/preferences/global")) shouldBe WriteOutcome.Applied
      repo.writeNode(
        VaultNodeFixtures.stateNode(
          id = "state/preferences/capmo-scope",
          scope = "work/capmo",
        ),
      ) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("What should we talk about next?"))

      report.classification.domain shouldBe RetrievalDomain.General
      report.compactMapEntries.map { it.id } shouldContain "state/preferences/global"
      report.compactMapEntries.map { it.id } shouldNotContain "state/preferences/capmo-scope"
    }

    test("default map entries carry required map-first fields") {
      val (service, repo) = newService()
      repo.writeNode(VaultNodeFixtures.subjectNode()) shouldBe WriteOutcome.Applied
      repo.writeNode(
        VaultNodeFixtures.episodeNode().copy(
          id = NodeId("domains/work/capmo/events/review"),
          date = VaultNodeFixtures.episodeInstant,
          topic = "review topic",
          body = "Evidence body line.\n",
        ),
      ) shouldBe WriteOutcome.Applied
      repo.writeNode(
        VaultNodeFixtures.stateNode(
          id = "state/preferences/capmo-scope",
          body = "Prefer explicit architecture.\n",
          scope = "work/capmo",
        ),
      ) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("Capmo architecture"))

      val subject = report.compactMapEntries.first { it.id == "domains/work/capmo/subjects/build-pipeline" }
      subject.kind shouldBe com.sermilion.personalgraph.domain.retrieval.CompactMapEntryKind.Node
      subject.domain shouldBe "work/capmo"
      subject.category shouldBe "subject"
      subject.updatedAt.orEmpty() shouldContain "2026-04-24"
      subject.summary shouldBe "Build pipeline ownership is shared."
      subject.excerpt shouldBe "Build pipeline ownership is shared."
      subject.aliases shouldContainExactly listOf("deploy-pipeline")
      subject.terms shouldContain "build-pipeline"
      subject.links shouldContain "domains/work/capmo/events/sample-episode"
      subject.backlinkCount shouldBe 0

      val event = report.compactMapEntries.first { it.id == "domains/work/capmo/events/review" }
      event.domain shouldBe "work/capmo"
      event.category shouldBe "decision"
      event.date shouldBe VaultNodeFixtures.episodeInstant.toString()
      event.summary shouldContain "review topic"

      val scopedState = report.compactMapEntries.first { it.id == "state/preferences/capmo-scope" }
      scopedState.category shouldBe "preference"
      scopedState.scope shouldBe "work/capmo"
      scopedState.summary shouldBe "Prefer explicit architecture."
    }

    test("default full-body loading is bounded to root orientation budget") {
      val (service, repo, root) = newService()
      val longRoot = (1..1_700).joinToString(" ") { "root$it" }
      Files.writeString(root.resolve(VaultLayout.BRAIAN_FILENAME), longRoot)
      repo.writeNode(
        VaultNodeFixtures.episodeNode().copy(
          id = NodeId("domains/work/capmo/events/long"),
          body = (1..1_000).joinToString(" ") { "event$it" },
        ),
      ) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("Capmo"))

      report.loadedNodes.shouldBeEmpty()
      report.loadedFullBodyContext.map { it.id } shouldContainExactly listOf("Braian.md")
      report.loadedFullBodyContext.single().body.split(Regex("\\s+")).size shouldBe 1_500
      report.audit.any {
        it.action == "full_body_budget" &&
          it.reason.contains("default full-body budget=1500 words")
      } shouldBe true
    }

    test("default map entries use bounded node previews instead of full bodies") {
      val (service, repo) = newService()
      repo.writeNode(
        VaultNodeFixtures.episodeNode().copy(
          id = NodeId("domains/work/capmo/events/long-preview"),
          body = (1..500).joinToString(" ") { "event$it" },
        ),
      ) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("Capmo"))

      report.loadedNodes.shouldBeEmpty()
      val entry = report.compactMapEntries.first { it.id == "domains/work/capmo/events/long-preview" }
      entry.summary.orEmpty() shouldContain "event1"
      entry.summary.orEmpty().contains("event500") shouldBe false
      entry.excerpt.orEmpty().contains("event500") shouldBe false
    }

    test("suggested reads prefer subject hubs over raw events unless recent evidence is requested") {
      val (service, repo) = newService()
      repo.writeNode(VaultNodeFixtures.subjectNode()) shouldBe WriteOutcome.Applied
      repo.writeNode(
        VaultNodeFixtures.episodeNode().copy(
          id = NodeId("domains/work/capmo/events/recent-review"),
          topic = "recent review",
          body = "Recent event body.\n",
        ),
      ) shouldBe WriteOutcome.Applied

      val defaultReport = service.retrieve(SessionStartRetrievalRequest("Capmo review"))
      defaultReport.suggestedReads.first().id shouldBe "domains/work/capmo/subjects/build-pipeline"

      val recentReport = service.retrieve(SessionStartRetrievalRequest("latest Capmo evidence timeline"))
      recentReport.suggestedReads.first().id shouldBe "domains/work/capmo/events/recent-review"
      recentReport.audit.any {
        it.action == "suggested_read" &&
          it.subject == "domains/work/capmo/events/recent-review" &&
          it.reason.contains("recent event evidence")
      } shouldBe true
    }

    test("suggested scoped state is global or classified-domain relevant only") {
      val (service, repo) = newService()
      repo.writeNode(VaultNodeFixtures.stateNode(id = "state/preferences/global")) shouldBe WriteOutcome.Applied
      repo.writeNode(
        VaultNodeFixtures.stateNode(id = "state/preferences/capmo-scope", scope = "work/capmo"),
      ) shouldBe WriteOutcome.Applied
      repo.writeNode(
        VaultNodeFixtures.stateNode(id = "state/preferences/readian-scope", scope = "work/readian"),
      ) shouldBe WriteOutcome.Applied

      val capmoReport = service.retrieve(SessionStartRetrievalRequest("Capmo"))
      capmoReport.suggestedReads.map { it.id } shouldContain "state/preferences/global"
      capmoReport.suggestedReads.map { it.id } shouldContain "state/preferences/capmo-scope"
      capmoReport.suggestedReads.map { it.id } shouldNotContain "state/preferences/readian-scope"

      val generalReport = service.retrieve(SessionStartRetrievalRequest("What should we talk about?"))
      generalReport.suggestedReads.map { it.id } shouldContain "state/preferences/global"
      generalReport.suggestedReads.map { it.id } shouldNotContain "state/preferences/capmo-scope"
      generalReport.suggestedReads.map { it.id } shouldNotContain "state/preferences/readian-scope"
    }
  })

private data class TestContext(
  val service: PersonalGraphSessionStartRetrievalService,
  val repository: PersonalGraphVaultRepository,
  val root: Path,
)

private fun writeRaw(path: Path, body: String) {
  Files.createDirectories(path.parent)
  Files.writeString(path, body)
}
