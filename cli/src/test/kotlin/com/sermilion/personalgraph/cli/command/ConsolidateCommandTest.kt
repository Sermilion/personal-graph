package com.sermilion.personalgraph.cli.command

import com.github.ajalt.clikt.testing.test
import com.sermilion.personalgraph.cli.personalGraphCli
import com.sermilion.personalgraph.data.codec.MarkdownFrontmatterCodec
import com.sermilion.personalgraph.domain.layout.VaultLayout.BRANCH_STAGING_OBSERVATIONS
import com.sermilion.personalgraph.domain.layout.VaultLayout.BRANCH_STATE_PREFERENCES
import com.sermilion.personalgraph.domain.model.Confidence
import com.sermilion.personalgraph.domain.model.EpisodeType
import com.sermilion.personalgraph.domain.model.Intensity
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.StateCategory
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

class ConsolidateCommandTest :
  FunSpec({

    test("consolidate command runs real consolidation and prints a report") {
      val tempDir = Files.createTempDirectory("cli-consolidate-")
      writeStaged(tempDir, "one", "User prefers concise status updates.\n")
      writeStaged(tempDir, "two", "User prefers concise status updates.\n")

      val invocation = personalGraphCli().test(arrayOf("consolidate", "--vault", tempDir.toString()))

      invocation.statusCode shouldBe 0
      invocation.output shouldContain "Consolidation report"
      invocation.output shouldContain "graduated=1"
      invocation.output shouldContain "merged=1"
      invocation.output shouldContain "promoted_patterns=0"
      invocation.output shouldContain "annotated_contradictions=0"
      invocation.output shouldContain "graduated_ids=$BRANCH_STATE_PREFERENCES/user-prefers-concise-status-updates-"
      invocation.output shouldContain "merged_into_ids=$BRANCH_STATE_PREFERENCES/user-prefers-concise-status-updates-"
      Files.list(tempDir.resolve(BRANCH_STATE_PREFERENCES)).use { stream ->
        stream.count() shouldBe 1
      }
    }

    test("consolidate command prints pattern and contradiction ids") {
      val tempDir = Files.createTempDirectory("cli-consolidate-report-")
      writeStaged(tempDir, "one", "User tends to normalize data before comparing it.\n")
      writeStaged(tempDir, "two", "User tends to normalize data before comparing it.\n")
      writeStaged(tempDir, "three", "User tends to normalize data before comparing it.\n")
      writeState(tempDir, "state/preferences/editor-tabs", "User prefers tabs for indentation.\n")
      writeStaged(tempDir, "tabs-contradiction", "User does not prefer tabs for indentation.\n")

      val invocation = personalGraphCli().test(arrayOf("consolidate", "--vault", tempDir.toString()))

      invocation.statusCode shouldBe 0
      invocation.output shouldContain "promoted_patterns=1"
      invocation.output shouldContain "annotated_contradictions=1"
      invocation.output shouldContain "pattern_ids=patterns/user-tends-to-normalize-data-before-comparing-it-"
      invocation.output shouldContain "contradiction_source_ids=$BRANCH_STAGING_OBSERVATIONS/tabs-contradiction"
    }

    test("consolidate command reports migrated legacy notes") {
      val tempDir = Files.createTempDirectory("cli-consolidate-migration-")
      writeLegacyEpisode(tempDir)

      val invocation = personalGraphCli().test(arrayOf("consolidate", "--vault", tempDir.toString()))

      invocation.statusCode shouldBe 0
      invocation.output shouldContain "migrated_legacy_notes=1"
      invocation.output shouldContain "migrated_subject_hub_ids=domains/work/capmo/subjects/build-pipeline"
      invocation.output shouldContain "migrated_source_ids=domains/work/capmo/notes/build-pipeline"
    }
  })

private fun writeStaged(root: Path, id: String, body: String) {
  writeState(root, "$BRANCH_STAGING_OBSERVATIONS/$id", body)
}

private fun writeState(root: Path, id: String, body: String) {
  val codec = MarkdownFrontmatterCodec()
  val node = VaultNodeFixtures.stateNode(
    id = id,
    body = body,
    category = StateCategory.Preference,
    confidence = Confidence.Medium,
    links = listOf(NodeId("domains/work/capmo/events/source-${id.substringAfterLast('/')}")),
  )
  val target = root.resolve("${node.id.value}.md")
  Files.createDirectories(target.parent)
  Files.writeString(target, codec.encode(node))
}

private fun writeLegacyEpisode(root: Path) {
  val codec = MarkdownFrontmatterCodec()
  val node = VaultNodeFixtures.episodeNode().copy(
    id = NodeId("domains/work/capmo/notes/build-pipeline"),
    topic = "Build Pipeline",
    episodeType = EpisodeType.Decision,
    intensity = Intensity.Medium,
    body = "Legacy note details.\n",
  )
  val target = root.resolve("${node.id.value}.md")
  Files.createDirectories(target.parent)
  Files.writeString(target, codec.encode(node))
}
