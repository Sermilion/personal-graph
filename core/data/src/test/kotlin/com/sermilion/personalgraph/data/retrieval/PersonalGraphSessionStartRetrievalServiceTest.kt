package com.sermilion.personalgraph.data.retrieval

import com.sermilion.personalgraph.data.codec.MarkdownFrontmatterCodec
import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.data.repository.PersonalGraphGraphIndexRepository
import com.sermilion.personalgraph.data.repository.PersonalGraphVaultRepository
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.StateCategory
import com.sermilion.personalgraph.domain.repository.GraphIndexRepository
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import com.sermilion.personalgraph.domain.retrieval.RetrievalDomain
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalMode
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalRequest
import com.sermilion.personalgraph.domain.retrieval.SuggestedActionValue
import com.sermilion.personalgraph.domain.tokens.TokenEstimator
import com.sermilion.personalgraph.testing.NoOpGraphIndexInvalidator
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
import io.mockk.coVerify
import io.mockk.spyk
import java.nio.file.Files
import java.nio.file.Path

class PersonalGraphSessionStartRetrievalServiceTest :
  FunSpec({

    fun newService(
      spyVaultRepository: Boolean = false,
      spyGraphIndexRepository: Boolean = false,
    ): TestContext {
      val tempDir = Files.createTempDirectory("session-start-")
      val resolver = VaultPathResolver()
      val dispatcherProvider = TestDispatcherProvider()
      val repo = PersonalGraphVaultRepository(
        vaultRoot = tempDir,
        dispatcherProvider = dispatcherProvider,
        codec = MarkdownFrontmatterCodec(),
        pathResolver = resolver,
        graphIndexInvalidator = NoOpGraphIndexInvalidator,
      )
      val serviceRepository = if (spyVaultRepository) spyk(repo) else repo
      Files.writeString(tempDir.resolve(VaultLayout.BRAIAN_FILENAME), "# Braian\nRoot context.\n")
      val graphIndexRepository = PersonalGraphGraphIndexRepository(
        vaultRoot = tempDir,
        dispatcherProvider = dispatcherProvider,
        codec = MarkdownFrontmatterCodec(),
        pathResolver = resolver,
        tokenEstimator = TokenEstimator,
      )
      val serviceGraphIndexRepository = if (spyGraphIndexRepository) {
        spyk(graphIndexRepository)
      } else {
        graphIndexRepository
      }
      val service = PersonalGraphSessionStartRetrievalService(
        vaultRoot = tempDir,
        repository = serviceRepository,
        graphIndexRepository = serviceGraphIndexRepository,
        pathResolver = resolver,
        dispatcherProvider = dispatcherProvider,
      )
      return TestContext(service, repo, tempDir, serviceRepository, serviceGraphIndexRepository)
    }

    test("map-first loads Braian first then classified work index without linked pattern expansion") {
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
        "domains/work/capmo",
        VaultLayout.BRANCH_STATE_PREFERENCES,
      )
      report.loadedNodes.shouldBeEmpty()
      report.loadedContext.map { it.id } shouldContain "Braian.md"
      report.loadedContext.map { it.id } shouldNotContain "domains/work/capmo/events/review"
      report.availableMap.map { it.id } shouldContain "domains/work/capmo"
      report.availableMap.map { it.id } shouldContain "domains/work/capmo/events/review"
      report.availableMap.map { it.id } shouldNotContain "patterns/review-shape"
      report.suggestedReads.map { it.id } shouldContain "domains/work/capmo/events/review"
      report.suggestedActions.map { it.tool } shouldContainExactly listOf("search_nodes", "list_branch")
      val queryArg = report.suggestedActions.first()
        .args.first { it.key == "query" }
        .value
      (queryArg as SuggestedActionValue.StringValue).value shouldContain "Capmo work please"
      report.estimatedTokens.responseTotal shouldBe
        report.estimatedTokens.metadataTokens + report.estimatedTokens.bodyTokens
      (report.estimatedTokens.responseTotal > 0) shouldBe true
      report.auditEntries shouldBe report.audit
      report.audit.map { it.action } shouldContain "loaded_branch_index"
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

      report.loadedContext.map { it.id } shouldContainExactly listOf(
        "Braian.md",
        "domains/work/capmo/events/review",
      )
      report.loadedContext.first { it.id == "domains/work/capmo/events/review" }.body shouldBe
        "Full review body.\n"
    }

    test("map-first loaded full-body context keeps root only while loaded nodes remain mapped") {
      val (service, repo) = newService()
      repo.writeNode(
        VaultNodeFixtures.episodeNode().copy(id = NodeId("domains/work/capmo/events/review")),
      ) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("Capmo review"))

      report.loadedNodes.shouldBeEmpty()
      report.availableMap.map { it.id } shouldContain "domains/work/capmo/events/review"
      report.loadedContext.map { it.id } shouldContainExactly listOf("Braian.md")
    }

    test("available map exposes compact node metadata without default node bodies") {
      val (service, repo) = newService()
      repo.writeNode(
        VaultNodeFixtures.subjectNode(
          id = "domains/work/capmo/subjects/attendance",
          body = "## Summary\nAttendance variants prefer canonical storage.\n\n## Evidence\n- 2026-04-30: design.",
          aliases = listOf("company-attendance"),
        ),
      ) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("Capmo attendance"))

      val entry = report.availableMap.first { it.id == "domains/work/capmo/subjects/attendance" }
      entry.type shouldBe "subject"
      entry.domain shouldBe "work/capmo"
      entry.summary shouldBe "Attendance variants prefer canonical storage."
      entry.aliases shouldContainExactly listOf("company-attendance")
      report.loadedContext.map { it.id } shouldContainExactly listOf("Braian.md")
      report.loadedNodes.shouldBeEmpty()
    }

    test("available map is bounded by per-branch top-k before the global cap") {
      val (service, repo) = newService()
      repeat(120) { index ->
        repo.writeNode(
          VaultNodeFixtures.episodeNode().copy(
            id = NodeId("domains/work/capmo/events/item-$index"),
            topic = "item-$index",
          ),
        ) shouldBe WriteOutcome.Applied
      }

      val report = service.retrieve(SessionStartRetrievalRequest("Capmo work"))

      report.availableMap.size shouldBe 22
      report.suggestedReads.size shouldBe 8
      report.audit.any { it.action == "map_first_default" } shouldBe true
    }

    test("map-first keeps subject hubs when large event folders exceed the old branch cap") {
      val (service, repo) = newService()
      repeat(1005) { index ->
        repo.writeNode(
          VaultNodeFixtures.episodeNode().copy(
            id = NodeId("domains/work/capmo/events/aaa-item-$index"),
            topic = "aaa-item-$index",
          ),
        ) shouldBe WriteOutcome.Applied
      }
      repo.writeNode(
        VaultNodeFixtures.subjectNode(
          id = "domains/work/capmo/subjects/zz-attendance",
          subject = "attendance",
        ),
      ) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("Capmo attendance"))

      report.availableMap.map { it.id } shouldContain "domains/work/capmo/subjects/zz-attendance"
      report.suggestedReads.first().id shouldBe "domains/work/capmo/subjects/zz-attendance"
    }

    test("available map budget preserves classified domain subject over broad global state") {
      val (service, repo) = newService()
      repeat(100) { index ->
        repo.writeNode(
          VaultNodeFixtures.stateNode(id = "state/preferences/global-$index"),
        ) shouldBe WriteOutcome.Applied
      }
      repo.writeNode(
        VaultNodeFixtures.subjectNode(id = "domains/work/capmo/subjects/attendance"),
      ) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("Capmo attendance"))

      report.availableMap.size shouldBe 23
      report.availableMap.map { it.id } shouldContain "domains/work/capmo/subjects/attendance"
      report.suggestedReads.first().id shouldBe "domains/work/capmo/subjects/attendance"
    }

    test("suggested reads prefer subject hubs over event evidence for classified domain") {
      val (service, repo) = newService()
      repo.writeNode(
        VaultNodeFixtures.subjectNode(id = "domains/work/capmo/subjects/attendance"),
      ) shouldBe WriteOutcome.Applied
      repo.writeNode(
        VaultNodeFixtures.episodeNode().copy(id = NodeId("domains/work/capmo/events/attendance-evidence")),
      ) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("Capmo attendance"))

      report.suggestedReads.first().id shouldBe "domains/work/capmo/subjects/attendance"
      report.suggestedReads.first().priority.value shouldBe "high"
      report.audit.any {
        it.action == "suggested_read" && it.subject == "domains/work/capmo/subjects/attendance"
      } shouldBe true
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
      report.availableMap.map { it.id } shouldContain "state/preferences/status-updates"
      report.availableMap.map { it.id }.contains("emotional-states/2026-04-24-debug-frustration") shouldBe false
      report.loadedContext.map { it.id } shouldContainExactly listOf("Braian.md")
      report.loadedNodes.shouldBeEmpty()
      report.skippedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_EMOTIONAL_STATES
    }

    test("available map omits blocked people and staging link targets") {
      val (service, repo) = newService()
      repo.writeNode(
        VaultNodeFixtures.episodeNode().copy(
          id = NodeId("domains/work/capmo/events/linked-private"),
          body = "Private link test.\n",
          links = listOf(
            NodeId("people/private-person"),
            NodeId("staging/sensitive/private"),
            NodeId("domains/work/capmo/subjects/public"),
          ),
        ),
      ) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("Capmo work"))

      val entry = report.availableMap.first { it.id == "domains/work/capmo/events/linked-private" }
      entry.links shouldContainExactly listOf("domains/work/capmo/subjects/public")
      entry.linkCount shouldBe 1
    }

    test("emotional context explicitly includes emotional-state branch") {
      val (service, repo) = newService()
      repo.writeNode(VaultNodeFixtures.emotionalStateNode()) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("I am feeling frustrated and want reflection."))

      report.classification.emotionalContextRequested shouldBe true
      report.loadedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_EMOTIONAL_STATES
      report.availableMap.map { it.id } shouldContain "emotional-states/2026-04-24-debug-frustration"
    }

    test("retrieval skips people and staging sensitive and does not follow linked pattern files in map-first") {
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
      val outsideBranchTarget = Files.createTempDirectory("session-start-branch-leak-").resolve("branch-secret.md")
      writeRaw(outsideBranchTarget, VaultNodeFixtures.EPISODE_NODE_MARKDOWN)
      Files.createSymbolicLink(
        root.resolve("domains/work/capmo/events/branch-secret.md"),
        outsideBranchTarget,
      )

      val report = service.retrieve(SessionStartRetrievalRequest("Capmo please"))

      report.skippedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_PEOPLE
      report.skippedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_STAGING
      report.availableMap.map { it.id }.contains("staging/sensitive/private") shouldBe false
      report.availableMap.map { it.id }.contains("patterns/secret") shouldBe false
      report.availableMap.map { it.id }.contains("domains/work/capmo/events/branch-secret") shouldBe false
      report.suggestedReads.map { it.id }.contains("domains/work/capmo/events/branch-secret") shouldBe false
      report.audit.any { it.action == "loaded_branch_index" && it.subject == "domains/work/capmo" } shouldBe true
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

    test("map-first narrows classified state to preferences while general keeps durable state branches") {
      val (service, _) = newService()
      val classifications = listOf(
        "Capmo work" to RetrievalDomain.WorkCapmo,
        "song guitar studio" to RetrievalDomain.CreativeMusic,
        "family health habit" to RetrievalDomain.Personal,
      )

      for ((message, expected) in classifications) {
        val report = service.retrieve(SessionStartRetrievalRequest(message))
        report.classification.domain shouldBe expected
        report.loadedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_STATE_PREFERENCES
        report.loadedBranches.map { it.branch }.shouldNotContain(VaultLayout.BRANCH_STATE_ROLES)
      }

      val generalReport = service.retrieve(SessionStartRetrievalRequest("What should we talk about next?"))
      generalReport.classification.domain shouldBe RetrievalDomain.General
      generalReport.loadedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_STATE_PREFERENCES
      generalReport.loadedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_STATE_ROLES
      generalReport.loadedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_STATE_KNOWLEDGE
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
      capmoReport.availableMap.map { it.id } shouldContain "state/preferences/global"
      capmoReport.availableMap.map { it.id } shouldContain "state/preferences/capmo-scope"
      capmoReport.availableMap.map { it.id } shouldNotContain "state/preferences/readian-scope"
      capmoReport.suggestedReads.map { it.id } shouldContain "state/preferences/global"
      capmoReport.suggestedReads.map { it.id } shouldContain "state/preferences/capmo-scope"

      val readianReport = service.retrieve(SessionStartRetrievalRequest("Readian article"))
      readianReport.availableMap.map { it.id } shouldContain "state/preferences/global"
      readianReport.availableMap.map { it.id } shouldContain "state/preferences/readian-scope"
      readianReport.availableMap.map { it.id } shouldNotContain "state/preferences/capmo-scope"
      readianReport.suggestedReads.map { it.id } shouldContain "state/preferences/readian-scope"
    }

    test("classified state map reserves global preferences when scoped preferences exceed branch quota") {
      val (service, repo) = newService()
      repeat(25) { index ->
        repo.writeNode(
          VaultNodeFixtures.stateNode(
            id = "state/preferences/skill-bill-scope-$index",
            scope = "work/skill-bill",
          ),
        ) shouldBe WriteOutcome.Applied
      }
      repo.writeNode(VaultNodeFixtures.stateNode(id = "state/preferences/global")) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("Skill-bill workflow"))

      report.availableMap.map { it.id } shouldContain "state/preferences/global"
      report.availableMap.map { it.id } shouldContain "state/preferences/skill-bill-scope-0"
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
      report.availableMap.map { it.id } shouldContain "state/preferences/global"
      report.availableMap.map { it.id } shouldNotContain "state/preferences/capmo-scope"
    }

    test("classified skill-bill map-first uses scoped top-k index map with exact follow-up paths and lower tokens") {
      val context = newService(spyVaultRepository = true, spyGraphIndexRepository = true)
      val service = context.service
      val repo = context.repository
      val serviceRepository = context.serviceRepository
      val graphIndexRepository = context.graphIndexRepository
      val longBody = (1..400).joinToString(" ") { "body-token-$it" }
      repo.writeNode(
        VaultNodeFixtures.subjectNode(
          id = "domains/work/skill-bill/subjects/session-start",
          domain = "work/skill-bill",
          subject = "session-start",
          body = "## Summary\nSession start should inspect compact maps first.\n\n## Evidence\n$longBody",
        ),
      ) shouldBe WriteOutcome.Applied
      repeat(35) { index ->
        repo.writeNode(
          VaultNodeFixtures.episodeNode().copy(
            id = NodeId("domains/work/skill-bill/events/item-$index"),
            domain = "work/skill-bill",
            topic = "item-$index",
            body = longBody,
          ),
        ) shouldBe WriteOutcome.Applied
      }
      repo.writeNode(VaultNodeFixtures.stateNode(id = "state/preferences/global")) shouldBe WriteOutcome.Applied
      repo.writeNode(
        VaultNodeFixtures.stateNode(
          id = "state/preferences/skill-bill-scope",
          scope = "work/skill-bill",
        ),
      ) shouldBe WriteOutcome.Applied
      repo.writeNode(
        VaultNodeFixtures.stateNode(
          id = "state/preferences/capmo-scope",
          scope = "work/capmo",
        ),
      ) shouldBe WriteOutcome.Applied
      repo.writeNode(
        VaultNodeFixtures.stateNode(
          id = "state/roles/current-role",
          category = StateCategory.Role,
        ),
      ) shouldBe WriteOutcome.Applied

      val mapReport = service.retrieve(SessionStartRetrievalRequest("Skill-bill session start map"))

      coVerify(exactly = 0) { serviceRepository.listNodesInBranch(any()) }
      coVerify(exactly = 0) { serviceRepository.findNode(any()) }
      coVerify(exactly = 0) { graphIndexRepository.listEntriesInBranch(any<String>()) }
      coVerify {
        graphIndexRepository.listEntriesInBranch(
          "domains/work/skill-bill",
          match {
            it.limit == 40 &&
              it.preferredRelativePrefixes.firstOrNull() == VaultLayout.SUB_DOMAIN_SUBJECTS
          },
        )
      }
      coVerify {
        graphIndexRepository.listEntriesInBranch(
          VaultLayout.BRANCH_STATE_PREFERENCES,
          match { it.limit == 80 },
        )
      }
      mapReport.loadedNodes.shouldBeEmpty()
      mapReport.loadedContext.map { it.id } shouldContainExactly listOf("Braian.md")
      mapReport.loadedBranches.map { it.branch } shouldContainExactly listOf(
        "domains/work/skill-bill",
        VaultLayout.BRANCH_STATE_PREFERENCES,
      )
      mapReport.availableMap.size shouldBe 24
      mapReport.availableMap.map { it.id } shouldContain "domains/work/skill-bill/subjects/session-start"
      mapReport.availableMap.map { it.id } shouldContain "state/preferences/global"
      mapReport.availableMap.map { it.id } shouldContain "state/preferences/skill-bill-scope"
      mapReport.availableMap.map { it.id } shouldNotContain "state/preferences/capmo-scope"
      mapReport.availableMap.map { it.id } shouldNotContain "state/roles/current-role"
      mapReport.suggestedReads.map { it.id } shouldContain "domains/work/skill-bill/subjects/session-start"
      mapReport.suggestedReads.map { it.id } shouldContain "state/preferences/skill-bill-scope"

      val actions = mapReport.suggestedActions
      actions.map { it.tool } shouldContainExactly listOf("search_nodes", "list_branch")
      val searchBranches = actions.first { it.tool == "search_nodes" }
        .args.first { it.key == "branches" }
        .value as SuggestedActionValue.StringListValue
      searchBranches.value shouldContainExactly listOf(
        "domains/work/skill-bill",
        VaultLayout.BRANCH_STATE_PREFERENCES,
      )
      val listBranchArgs = actions.first { it.tool == "list_branch" }.args.associateBy { it.key }
      (listBranchArgs.getValue("branch").value as SuggestedActionValue.StringValue).value shouldBe
        "domains/work/skill-bill"
      (listBranchArgs.getValue("mode").value as SuggestedActionValue.StringValue).value shouldBe "index"
      (listBranchArgs.getValue("include_links").value as SuggestedActionValue.BooleanValue).value shouldBe true
      (listBranchArgs.getValue("include_body").value as SuggestedActionValue.BooleanValue).value shouldBe false
      (listBranchArgs.getValue("limit").value as SuggestedActionValue.IntValue).value shouldBe 20

      val fullReport = service.retrieve(
        SessionStartRetrievalRequest(
          firstSubstantiveMessage = "Skill-bill session start map",
          retrievalMode = SessionStartRetrievalMode.FullLoading,
        ),
      )
      (mapReport.estimatedTokens.responseTotal < fullReport.estimatedTokens.responseTotal) shouldBe true
      mapReport.estimatedTokens.bodyTokens shouldBe fullReport.rootDocument
        ?.body
        ?.let { TokenEstimator.estimateBody(it) }
    }
  })

private data class TestContext(
  val service: PersonalGraphSessionStartRetrievalService,
  val repository: PersonalGraphVaultRepository,
  val root: Path,
  val serviceRepository: VaultRepository,
  val graphIndexRepository: GraphIndexRepository,
)

private fun writeRaw(path: Path, body: String) {
  Files.createDirectories(path.parent)
  Files.writeString(path, body)
}
