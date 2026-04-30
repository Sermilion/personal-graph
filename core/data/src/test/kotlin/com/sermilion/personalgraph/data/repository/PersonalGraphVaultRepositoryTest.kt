package com.sermilion.personalgraph.data.repository

import com.sermilion.personalgraph.data.codec.MarkdownFrontmatterCodec
import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import com.sermilion.personalgraph.testing.TestDispatcherProvider
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files

class PersonalGraphVaultRepositoryTest :
  FunSpec({

    fun newRepository(): Pair<PersonalGraphVaultRepository, java.nio.file.Path> {
      val tempDir = Files.createTempDirectory("vault-repo-")
      val codec = MarkdownFrontmatterCodec()
      val resolver = VaultPathResolver()
      val repo = PersonalGraphVaultRepository(
        vaultRoot = tempDir,
        dispatcherProvider = TestDispatcherProvider(),
        codec = codec,
        pathResolver = resolver,
      )
      return repo to tempDir
    }

    test("writeNode happy-path writes file with frontmatter") {
      val (repo, root) = newRepository()
      val node = VaultNodeFixtures.stateNode(
        id = "state/preferences/editor-indent",
        body = "Body text.\n",
      )

      val outcome = repo.writeNode(node)

      outcome shouldBe WriteOutcome.Applied
      val target = root.resolve("state/preferences/editor-indent.md")
      Files.exists(target) shouldBe true
      val written = Files.readString(target)
      written shouldContain "type: \"state\""
      written shouldContain "Body text."
    }

    test("findNode returns null for missing files") {
      val (repo, _) = newRepository()
      repo.findNode(NodeId("state/preferences/missing")) shouldBe null
    }

    test("writeNode then findNode returns the same state node") {
      val (repo, _) = newRepository()
      val source = VaultNodeFixtures.stateNode(
        id = "state/preferences/round-trip",
        body = "Round trip body with [[state/roles/current-role]] reference.\n",
      )

      repo.writeNode(source) shouldBe WriteOutcome.Applied
      val read = repo.findNode(source.id)

      read.shouldNotBeNull()
      read.shouldBeInstanceOf<StateNode>()
      read.id shouldBe source.id
      read.body shouldBe source.body
      read.category shouldBe source.category
      read.confidence shouldBe source.confidence
      read.links.map { it.value } shouldContain "state/roles/current-role"
    }

    test("findNode returns null when id is in a read-blocked branch") {
      val (repo, _) = newRepository()
      repo.findNode(NodeId("people/alice")) shouldBe null
    }

    test("listNodesInBranch returns empty list when branch is read-blocked") {
      val (repo, _) = newRepository()
      repo.listNodesInBranch("people") shouldBe emptyList()
    }

    test("writeNode rejects paths outside write-allowed branches") {
      val (repo, _) = newRepository()

      repo.writeNode(VaultNodeFixtures.stateNode(id = "people/private-note"))
        .shouldBeInstanceOf<WriteOutcome.Failed>()
      repo.writeNode(VaultNodeFixtures.stateNode(id = "archive/private-note"))
        .shouldBeInstanceOf<WriteOutcome.Failed>()
    }

    test("vault-escape via symlink to a sibling temp dir is rejected") {
      val (repo, root) = newRepository()
      val outsideRoot = Files.createTempDirectory("vault-escape-")
      val symlinkDir = root.resolve("state/preferences")
      Files.createDirectories(symlinkDir)
      val symlink = symlinkDir.resolve("escape")
      Files.createSymbolicLink(symlink, outsideRoot)

      val node = VaultNodeFixtures.stateNode(id = "state/preferences/escape/leak")

      val outcome = repo.writeNode(node)
      outcome.shouldBeInstanceOf<WriteOutcome.Failed>()
    }

    test("moveNode happy-path moves file") {
      val (repo, root) = newRepository()
      val originalId = NodeId("staging/observations/maybe")
      val node = VaultNodeFixtures.stateNode(id = originalId.value)
      repo.writeNode(node) shouldBe WriteOutcome.Applied

      val outcome = repo.moveNode(originalId, "state/preferences")

      outcome shouldBe WriteOutcome.Applied
      Files.exists(root.resolve("state/preferences/maybe.md")) shouldBe true
      Files.exists(root.resolve("staging/observations/maybe.md")) shouldBe false
    }

    test("moveNode rejects target branches outside write policy") {
      val (repo, root) = newRepository()
      val originalId = NodeId("staging/observations/private")
      repo.writeNode(VaultNodeFixtures.stateNode(id = originalId.value)) shouldBe WriteOutcome.Applied

      repo.moveNode(originalId, "people").shouldBeInstanceOf<WriteOutcome.Failed>()
      repo.moveNode(originalId, "archive").shouldBeInstanceOf<WriteOutcome.Failed>()

      Files.exists(root.resolve("staging/observations/private.md")) shouldBe true
      Files.exists(root.resolve("people/private.md")) shouldBe false
      Files.exists(root.resolve("archive/private.md")) shouldBe false
    }

    test("deleteNode happy-path removes file") {
      val (repo, root) = newRepository()
      val id = NodeId("state/preferences/temp")
      repo.writeNode(VaultNodeFixtures.stateNode(id = id.value)) shouldBe WriteOutcome.Applied
      Files.exists(root.resolve("state/preferences/temp.md")) shouldBe true

      val outcome = repo.deleteNode(id)

      outcome shouldBe WriteOutcome.Applied
      Files.exists(root.resolve("state/preferences/temp.md")) shouldBe false
    }

    test("deleteNode rejects paths outside write policy") {
      val (repo, root) = newRepository()
      val target = root.resolve("people/private.md")
      Files.createDirectories(target.parent)
      Files.writeString(target, "private")

      repo.deleteNode(NodeId("people/private")).shouldBeInstanceOf<WriteOutcome.Failed>()

      Files.exists(target) shouldBe true
    }

    test("listBacklinks returns nodes that link to the target id") {
      val (repo, _) = newRepository()
      val target = NodeId("patterns/applies-normalization-thinking")
      val episode = VaultNodeFixtures.episodeNode()
      repo.writeNode(VaultNodeFixtures.stateNode(id = "state/preferences/editor-indent")) shouldBe
        WriteOutcome.Applied
      repo.writeNode(episode) shouldBe WriteOutcome.Applied

      val backlinks = repo.listBacklinks(target)

      backlinks.map { it.id.value } shouldContain episode.id.value
    }

    test("listNodesInBranch returns nodes in the given branch") {
      val (repo, _) = newRepository()
      val first = VaultNodeFixtures.stateNode(id = "state/preferences/a")
      val second = VaultNodeFixtures.stateNode(id = "state/preferences/b")
      repo.writeNode(first) shouldBe WriteOutcome.Applied
      repo.writeNode(second) shouldBe WriteOutcome.Applied

      val nodes = repo.listNodesInBranch("state/preferences")

      nodes.map { it.id.value } shouldContainExactlyInAnyOrder listOf(first.id.value, second.id.value)
    }

    test("listMapNodesInBranch returns bounded node previews") {
      val (repo, _) = newRepository()
      val body = (1..100).joinToString(" ") { "word$it" }
      val node = VaultNodeFixtures.stateNode(id = "state/preferences/preview", body = body)
      repo.writeNode(node) shouldBe WriteOutcome.Applied

      val previews = repo.listMapNodesInBranch("state/preferences", bodyWordLimit = 10)

      previews.map { it.id.value } shouldBe listOf(node.id.value)
      previews.single().body shouldBe (1..10).joinToString(" ") { "word$it" } + "..."
    }

    test("listStagedObservations returns observation staging only") {
      val (repo, _) = newRepository()
      val observation = VaultNodeFixtures.stateNode(id = "${VaultLayout.BRANCH_STAGING_OBSERVATIONS}/candidate")
      val sensitive = VaultNodeFixtures.stateNode(id = "${VaultLayout.BRANCH_STAGING_SENSITIVE}/candidate")
      repo.writeNode(observation) shouldBe WriteOutcome.Applied
      repo.writeNode(sensitive) shouldBe WriteOutcome.Applied

      val staged = repo.listStagedObservations()

      staged.map { it.id.value } shouldBe listOf(observation.id.value)
    }

    test("listStagedObservations skips symlinked files") {
      val (repo, root) = newRepository()
      val sensitive = VaultNodeFixtures.stateNode(id = "${VaultLayout.BRANCH_STAGING_SENSITIVE}/candidate")
      repo.writeNode(sensitive) shouldBe WriteOutcome.Applied
      val observationDir = root.resolve(VaultLayout.BRANCH_STAGING_OBSERVATIONS)
      Files.createDirectories(observationDir)
      Files.createSymbolicLink(
        observationDir.resolve("candidate.md"),
        root.resolve("${VaultLayout.BRANCH_STAGING_SENSITIVE}/candidate.md"),
      )

      repo.listStagedObservations() shouldBe emptyList()
    }

    test("findSubjectHub reuses canonical subject note by alias") {
      val (repo, _) = newRepository()
      val subject = VaultNodeFixtures.subjectNode()
      repo.writeNode(subject) shouldBe WriteOutcome.Applied

      val found = repo.findSubjectHub("work/capmo", "deploy-pipeline")

      found.shouldNotBeNull()
      found.shouldBeInstanceOf<SubjectNode>()
      found.id shouldBe subject.id
    }

    test("listSubjectHubs returns only subject notes in the domain branch") {
      val (repo, _) = newRepository()
      val first = VaultNodeFixtures.subjectNode()
      val second = VaultNodeFixtures.subjectNode(
        id = "domains/work/capmo/subjects/release-process",
        subject = "release-process",
      )
      repo.writeNode(first) shouldBe WriteOutcome.Applied
      repo.writeNode(second) shouldBe WriteOutcome.Applied
      repo.writeNode(VaultNodeFixtures.episodeNode()) shouldBe WriteOutcome.Applied

      val subjects = repo.listSubjectHubs("work/capmo")

      subjects.map { it.id.value } shouldContainExactlyInAnyOrder listOf(first.id.value, second.id.value)
    }

    test("moveNode returns Conflict when target already exists") {
      val (repo, root) = newRepository()
      val sourceId = NodeId("staging/observations/conflict")
      val targetId = NodeId("state/preferences/conflict")
      repo.writeNode(VaultNodeFixtures.stateNode(id = sourceId.value)) shouldBe WriteOutcome.Applied
      repo.writeNode(VaultNodeFixtures.stateNode(id = targetId.value)) shouldBe WriteOutcome.Applied

      val outcome = repo.moveNode(sourceId, "state/preferences")

      outcome shouldBe WriteOutcome.Conflict
      Files.exists(root.resolve("staging/observations/conflict.md")) shouldBe true
      Files.exists(root.resolve("state/preferences/conflict.md")) shouldBe true
    }

    test("moveNode returns NotFound when source is absent") {
      val (repo, _) = newRepository()
      val outcome = repo.moveNode(NodeId("state/preferences/missing"), "state/roles")
      outcome shouldBe WriteOutcome.NotFound
    }

    test("deleteNode returns NotFound when target is absent") {
      val (repo, _) = newRepository()
      val outcome = repo.deleteNode(NodeId("state/preferences/missing"))
      outcome shouldBe WriteOutcome.NotFound
    }
  })
