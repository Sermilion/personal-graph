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
      invocation.output shouldContain "Loaded context (1)"
      invocation.output shouldContain "context=Braian.md; source=root"
      invocation.output shouldContain "context_body_begin=Braian.md"
      invocation.output shouldContain "Root note."
      invocation.output shouldContain "context_body_end=Braian.md"
      invocation.output shouldContain "Available map"
      invocation.output shouldContain "map=domains/work/capmo;"
      invocation.output shouldContain "map=state/preferences;"
      invocation.output shouldContain "map=domains/work/capmo/events/review"
      invocation.output shouldContain "Suggested reads"
      invocation.output shouldContain "read=domains/work/capmo/events/review; priority=medium"
      invocation.output shouldContain "event evidence may be useful after map review"
      invocation.output shouldContain "Skipped branches"
      invocation.output shouldContain "skipped=people"
      invocation.output shouldContain "Audit reasons"
      invocation.output shouldContain "audit=classified"
    }
  })

private fun writeNode(root: Path, node: com.sermilion.personalgraph.domain.model.VaultNode) {
  val target = root.resolve("${node.id.value}.md")
  Files.createDirectories(target.parent)
  Files.writeString(target, MarkdownFrontmatterCodec().encode(node))
}
