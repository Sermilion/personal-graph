package com.sermilion.personalgraph.data.retrieval

import com.sermilion.personalgraph.data.codec.MarkdownFrontmatterCodec
import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.data.repository.PersonalGraphVaultRepository
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import com.sermilion.personalgraph.domain.retrieval.RetrievalDomain
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalRequest
import com.sermilion.personalgraph.testing.TestDispatcherProvider
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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

    test("loads Braian first then classified work subtree and linked patterns") {
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

      val report = service.retrieve(SessionStartRetrievalRequest("Can you review this Capmo PR?"))

      report.rootDocument?.loadOrder shouldBe 1
      report.rootDocument?.body shouldContain "Root context"
      report.classification.domain shouldBe RetrievalDomain.WorkCapmo
      report.classification.matchedTerms shouldContainExactlyInAnyOrder listOf("capmo", "pr", "review")
      report.loadedBranches.map { it.branch } shouldContainExactly listOf("domains/work/capmo")
      report.loadedNodes.map { it.id } shouldContain "domains/work/capmo/events/review"
      report.loadedNodes.map { it.id } shouldContain "patterns/review-shape"
      report.loadedNodes.first { it.id == "domains/work/capmo/events/review" }.loadOrder shouldBe 2
      report.loadedNodes.first { it.id == "patterns/review-shape" }.reason shouldContain "wikilinked pattern"
      report.audit.map { it.action } shouldContain "loaded_pattern"
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
      report.loadedNodes.map { it.id } shouldContain "state/preferences/status-updates"
      report.loadedNodes.map { it.id }.contains("emotional-states/2026-04-24-debug-frustration") shouldBe false
      report.skippedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_EMOTIONAL_STATES
    }

    test("emotional context explicitly includes emotional-state branch") {
      val (service, repo) = newService()
      repo.writeNode(VaultNodeFixtures.emotionalStateNode()) shouldBe WriteOutcome.Applied

      val report = service.retrieve(SessionStartRetrievalRequest("I am feeling frustrated and want reflection."))

      report.classification.emotionalContextRequested shouldBe true
      report.loadedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_EMOTIONAL_STATES
      report.loadedNodes.map { it.id } shouldContain "emotional-states/2026-04-24-debug-frustration"
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

      val report = service.retrieve(SessionStartRetrievalRequest("Work review please"))

      report.skippedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_PEOPLE
      report.skippedBranches.map { it.branch } shouldContain VaultLayout.BRANCH_STAGING
      report.loadedNodes.map { it.id }.contains("staging/sensitive/private") shouldBe false
      report.loadedNodes.map { it.id }.contains("patterns/secret") shouldBe false
      report.audit.any { it.action == "skipped_pattern" && it.subject == "patterns/secret" } shouldBe true
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
