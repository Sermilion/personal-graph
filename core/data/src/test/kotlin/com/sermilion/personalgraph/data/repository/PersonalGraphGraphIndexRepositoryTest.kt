package com.sermilion.personalgraph.data.repository

import com.sermilion.personalgraph.data.codec.MarkdownFrontmatterCodec
import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.tokens.TokenEstimator
import com.sermilion.personalgraph.testing.TestDispatcherProvider
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.spyk
import io.mockk.verify
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

private const val BLOCKED_BRANCH_NODE_MARKDOWN = """---
type: "state"
category: "preference"
confidence: "high"
created: "2026-04-24"
updated: "2026-04-24"
---
Private body content.
"""

private const val LINK_TO_BLOCKED_STATE_MARKDOWN = """---
type: "state"
category: "preference"
confidence: "high"
created: "2026-04-24"
updated: "2026-04-24"
---
Body referencing [[people/alice]] and [[staging/sensitive/secret]] and [[state/preferences/legit]].
"""

private data class TestRepo(
  val repo: PersonalGraphGraphIndexRepository,
  val root: Path,
  val codec: MarkdownFrontmatterCodec,
)

class PersonalGraphGraphIndexRepositoryTest :
  FunSpec({

    fun newRepo(): TestRepo {
      val tempDir = Files.createTempDirectory("graph-index-")
      val codec = spyk(MarkdownFrontmatterCodec())
      val resolver = VaultPathResolver()
      val repo = PersonalGraphGraphIndexRepository(
        vaultRoot = tempDir,
        dispatcherProvider = TestDispatcherProvider(),
        codec = codec,
        pathResolver = resolver,
        tokenEstimator = TokenEstimator,
      )
      return TestRepo(repo, tempDir, codec)
    }

    fun writeFixture(root: Path, relative: String, contents: String): Path {
      val target = root.resolve(relative)
      Files.createDirectories(target.parent)
      Files.writeString(target, contents)
      return target
    }

    test("listEntriesInBranch returns state entry with expected fields") {
      val (repo, root, _) = newRepo()
      writeFixture(root, "state/preferences/sample.md", VaultNodeFixtures.STATE_NODE_MARKDOWN)

      val entries = repo.listEntriesInBranch("state/preferences")

      entries.size shouldBe 1
      val entry = entries.single()
      entry.id.value shouldBe "state/preferences/sample"
      entry.type shouldBe "state"
      entry.category shouldBe "preference"
      entry.branch shouldBe "state/preferences"
      entry.fileSize shouldBe Files.size(root.resolve("state/preferences/sample.md"))
    }

    test("subject hub aliases, title, and path lookups resolve") {
      val (repo, root, _) = newRepo()
      writeFixture(
        root,
        "domains/work/capmo/subjects/build-pipeline.md",
        VaultNodeFixtures.SUBJECT_NODE_MARKDOWN,
      )
      repo.listEntriesInBranch("domains/work/capmo/subjects")

      val byAlias = repo.findEntryByAlias("deploy-pipeline")
      byAlias.shouldNotBeNull()
      byAlias.aliases shouldContain "deploy-pipeline"

      val byTitle = repo.findEntryByTitle("build-pipeline")
      byTitle.shouldNotBeNull()
      byTitle.subject shouldBe "build-pipeline"

      val byPath = repo.findEntryByPath("domains/work/capmo/subjects/build-pipeline")
      byPath.shouldNotBeNull()
      byPath.id.value shouldBe "domains/work/capmo/subjects/build-pipeline"

      val byAbsolutePath = repo.findEntryByPath(
        root.resolve("domains/work/capmo/subjects/build-pipeline.md").toAbsolutePath().normalize().toString(),
      )
      byAbsolutePath.shouldNotBeNull()
    }

    test("cache hit avoids re-decoding files across two listings") {
      val (repo, root, codec) = newRepo()
      writeFixture(root, "state/preferences/sample.md", VaultNodeFixtures.STATE_NODE_MARKDOWN)

      repo.listEntriesInBranch("state/preferences")
      repo.listEntriesInBranch("state/preferences")

      verify(exactly = 1) { codec.decodePreview(NodeId("state/preferences/sample"), any(), any()) }
    }

    test("cache invalidation by mtime change re-decodes") {
      val (repo, root, codec) = newRepo()
      val target = writeFixture(root, "state/preferences/sample.md", VaultNodeFixtures.STATE_NODE_MARKDOWN)
      repo.listEntriesInBranch("state/preferences")
      val newMtime = Files.getLastModifiedTime(target).toMillis() + 5_000L
      Files.setLastModifiedTime(target, FileTime.fromMillis(newMtime))
      Files.setLastModifiedTime(target.parent, FileTime.fromMillis(newMtime + 1_000L))

      repo.listEntriesInBranch("state/preferences")

      verify(exactly = 2) { codec.decodePreview(NodeId("state/preferences/sample"), any(), any()) }
    }

    test("cache invalidation by size change re-decodes") {
      val (repo, root, codec) = newRepo()
      val target = writeFixture(root, "state/preferences/sample.md", VaultNodeFixtures.STATE_NODE_MARKDOWN)
      repo.listEntriesInBranch("state/preferences")
      Files.writeString(target, VaultNodeFixtures.STATE_NODE_MARKDOWN + "\nadded body bytes here\n")
      Files.setLastModifiedTime(
        target.parent,
        FileTime.fromMillis(Files.getLastModifiedTime(target.parent).toMillis() + 1_000L),
      )

      repo.listEntriesInBranch("state/preferences")

      verify(exactly = 2) { codec.decodePreview(NodeId("state/preferences/sample"), any(), any()) }
    }

    test("invalidate(id) drops cache entry and forces re-decode") {
      val (repo, root, codec) = newRepo()
      writeFixture(root, "state/preferences/sample.md", VaultNodeFixtures.STATE_NODE_MARKDOWN)
      repo.listEntriesInBranch("state/preferences")
      verify(exactly = 1) { codec.decodePreview(NodeId("state/preferences/sample"), any(), any()) }

      repo.invalidate(NodeId("state/preferences/sample"))
      repo.findEntry(NodeId("state/preferences/sample")).shouldNotBeNull()

      verify(exactly = 2) { codec.decodePreview(NodeId("state/preferences/sample"), any(), any()) }
    }

    test("people/ branch never appears under any query path") {
      val (repo, root, _) = newRepo()
      writeFixture(root, "${VaultLayout.BRANCH_PEOPLE}/alice.md", BLOCKED_BRANCH_NODE_MARKDOWN)
      writeFixture(root, "state/preferences/sample.md", VaultNodeFixtures.STATE_NODE_MARKDOWN)

      repo.listEntriesInBranch(VaultLayout.BRANCH_PEOPLE) shouldBe emptyList()
      repo.findEntry(NodeId("people/alice")) shouldBe null
      repo.findEntryByPath("people/alice") shouldBe null
    }

    test("staging/sensitive never appears under any query path") {
      val (repo, root, _) = newRepo()
      writeFixture(root, "${VaultLayout.BRANCH_STAGING_SENSITIVE}/secret.md", BLOCKED_BRANCH_NODE_MARKDOWN)

      repo.listEntriesInBranch(VaultLayout.BRANCH_STAGING_SENSITIVE) shouldBe emptyList()
      repo.findEntry(NodeId("staging/sensitive/secret")) shouldBe null
      repo.findEntryByPath("staging/sensitive/secret") shouldBe null
    }

    test("links to blocked branches are filtered out of returned entries") {
      val (repo, root, _) = newRepo()
      writeFixture(root, "${VaultLayout.BRANCH_PEOPLE}/alice.md", BLOCKED_BRANCH_NODE_MARKDOWN)
      writeFixture(root, "${VaultLayout.BRANCH_STAGING_SENSITIVE}/secret.md", BLOCKED_BRANCH_NODE_MARKDOWN)
      writeFixture(root, "state/preferences/legit.md", VaultNodeFixtures.STATE_NODE_MARKDOWN)
      writeFixture(root, "state/preferences/links-blocked.md", LINK_TO_BLOCKED_STATE_MARKDOWN)

      val entry = repo.findEntry(NodeId("state/preferences/links-blocked"))
      entry.shouldNotBeNull()
      val linkValues = entry.links.map { it.value }
      linkValues shouldNotContain "people/alice"
      linkValues shouldNotContain "staging/sensitive/secret"
      linkValues shouldContain "state/preferences/legit"
      entry.linkCount shouldBe entry.links.size
    }

    test("indexes a fixture vault with episodes, patterns, and subjects") {
      val (repo, root, _) = newRepo()
      writeFixture(root, "state/preferences/sample.md", VaultNodeFixtures.STATE_NODE_MARKDOWN)
      writeFixture(
        root,
        "domains/work/capmo/events/sample-episode.md",
        VaultNodeFixtures.EPISODE_NODE_MARKDOWN,
      )
      writeFixture(
        root,
        "patterns/applies-normalization-thinking.md",
        VaultNodeFixtures.PATTERN_NODE_MARKDOWN,
      )
      writeFixture(
        root,
        "domains/work/capmo/subjects/build-pipeline.md",
        VaultNodeFixtures.SUBJECT_NODE_MARKDOWN,
      )

      val state = repo.findEntry(NodeId("state/preferences/sample"))
      val episode = repo.findEntry(NodeId("domains/work/capmo/events/sample-episode"))
      val pattern = repo.findEntry(NodeId("patterns/applies-normalization-thinking"))
      val subject = repo.findEntry(NodeId("domains/work/capmo/subjects/build-pipeline"))

      state.shouldNotBeNull()
      episode.shouldNotBeNull()
      pattern.shouldNotBeNull()
      subject.shouldNotBeNull()

      state.type shouldBe "state"
      episode.type shouldBe "episode"
      episode.domain shouldBe "work/capmo"
      episode.topic shouldBe "sample-topic"
      pattern.type shouldBe "pattern"
      pattern.hypothesis shouldBe "Short description of the pattern"
      subject.type shouldBe "subject"
      subject.subject shouldBe "build-pipeline"
      subject.aliases shouldContain "deploy-pipeline"
    }
  })
