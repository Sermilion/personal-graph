package com.sermilion.personalgraph.cli.command

import com.github.ajalt.clikt.testing.test
import com.sermilion.personalgraph.cli.personalGraphCli
import com.sermilion.personalgraph.data.codec.MarkdownFrontmatterCodec
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

class SessionStartCommandTest :
  FunSpec({

    test("session-start command prints classification and audit report") {
      val tempDir = Files.createTempDirectory("cli-session-start-")
      Files.writeString(tempDir.resolve("Braian.md"), "# Braian\nRoot note.\n")
      writeNode(
        tempDir,
        VaultNodeFixtures.episodeNode().copy(
          id = NodeId("domains/work/capmo/events/review"),
          links = listOf(NodeId("patterns/review-shape")),
          body = "Review body with [[patterns/review-shape]].\n",
        ),
      )
      writeNode(tempDir, VaultNodeFixtures.patternNode(id = "patterns/review-shape", body = "Pattern body.\n"))

      val invocation = personalGraphCli().test(
        arrayOf("session-start", "--vault", tempDir.toString(), "Can", "you", "review", "this", "Capmo", "PR?"),
      )

      invocation.statusCode shouldBe 0
      invocation.output shouldContain "Session-start retrieval report"
      invocation.output shouldContain "classification=work/capmo"
      invocation.output shouldContain "root=Braian.md"
      invocation.output shouldContain "branch=domains/work/capmo"
      invocation.output shouldContain "loaded_full_body_context="
      invocation.output shouldContain "compact_map_entries="
      invocation.output shouldContain "map=domains/work/capmo/events/review"
      invocation.output shouldContain "summary=sample-topic - Review body with [[patterns/review-shape]]."
      invocation.output shouldContain "terms=work/capmo,decision"
      invocation.output shouldContain "links=patterns/review-shape"
      invocation.output shouldContain "map=patterns/review-shape"
      invocation.output shouldContain "suggested_reads="
      invocation.output shouldContain "suggested=domains/work/capmo/events/review"
      val suggestedLine = invocation.output.lineSequence()
        .first { it.startsWith("suggested=domains/work/capmo/events/review") }
      suggestedLine shouldContain "domain=work/capmo"
      suggestedLine shouldContain "category=decision"
      suggestedLine shouldContain "scope="
      suggestedLine shouldContain "date=2026-04-24T15:02:00Z"
      suggestedLine shouldContain "summary=sample-topic - Review body with [[patterns/review-shape]]."
      suggestedLine shouldContain "excerpt=Review body with [[patterns/review-shape]]."
      suggestedLine shouldContain "aliases="
      suggestedLine shouldContain "terms=work/capmo,decision"
      suggestedLine shouldContain "links=patterns/review-shape"
      suggestedLine shouldContain "pattern_links="
      suggestedLine shouldContain "backlinks=0"
      invocation.output shouldContain "skipped=people"
      invocation.output shouldContain "audit=classified"
    }
  })

private fun writeNode(root: Path, node: com.sermilion.personalgraph.domain.model.VaultNode) {
  val target = root.resolve("${node.id.value}.md")
  Files.createDirectories(target.parent)
  Files.writeString(target, MarkdownFrontmatterCodec().encode(node))
}
