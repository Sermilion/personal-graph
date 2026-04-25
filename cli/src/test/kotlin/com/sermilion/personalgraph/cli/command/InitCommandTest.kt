package com.sermilion.personalgraph.cli.command

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import com.sermilion.personalgraph.cli.PersonalGraphCli
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class InitCommandTest :
  FunSpec({

    test("init creates the vault layout and seeds Braian.md") {
      val tempDir = Files.createTempDirectory("cli-init-")
      val cli = PersonalGraphCli().subcommands(InitCommand())

      val invocation = cli.test(arrayOf("init", "--vault", tempDir.toString()))
      invocation.statusCode shouldBe 0
      Files.exists(tempDir.resolve("Braian.md")) shouldBe true
      Files.exists(tempDir.resolve("state/preferences")) shouldBe true
      Files.exists(tempDir.resolve("domains/work/capmo/events")) shouldBe true
      Files.exists(tempDir.resolve("staging/sensitive")) shouldBe true
      Files.exists(tempDir.resolve("people")) shouldBe true
    }

    test("init is idempotent and preserves an existing Braian.md") {
      val tempDir = Files.createTempDirectory("cli-init-idempotent-")
      val cli = PersonalGraphCli().subcommands(InitCommand())
      val custom = "# my orientation\n\nDo not overwrite.\n"
      Files.writeString(tempDir.resolve("Braian.md"), custom)

      cli.test(arrayOf("init", "--vault", tempDir.toString())).statusCode shouldBe 0
      cli.test(arrayOf("init", "--vault", tempDir.toString())).statusCode shouldBe 0

      Files.readString(tempDir.resolve("Braian.md")) shouldBe custom
      val expectedDirs = listOf(
        "state/preferences",
        "domains/work/capmo/events",
        "staging/sensitive",
        "patterns",
        "emotional-states",
        "timeline",
        "people",
      )
      for (relative in expectedDirs) {
        Files.exists(tempDir.resolve(relative)) shouldBe true
      }
    }
  })
