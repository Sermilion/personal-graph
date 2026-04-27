package com.sermilion.personalgraph.data.scaffold

import com.sermilion.personalgraph.domain.repository.WriteOutcome
import com.sermilion.personalgraph.testing.TestDispatcherProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class PersonalGraphVaultScaffolderTest :
  FunSpec({

    val expectedDirectories = listOf(
      "state/preferences",
      "state/roles",
      "state/knowledge",
      "domains/work/capmo/events",
      "domains/work/capmo/subjects",
      "domains/work/reddit/events",
      "domains/work/reddit/subjects",
      "domains/personal/events",
      "domains/personal/subjects",
      "domains/creative/events",
      "domains/creative/subjects",
      "patterns",
      "emotional-states",
      "timeline",
      "staging/observations",
      "staging/sensitive",
      "people",
    )

    test("scaffold creates every expected branch directory and seeds Braian.md") {
      val tempDir = Files.createTempDirectory("vault-scaffold-")
      val scaffolder = PersonalGraphVaultScaffolder(tempDir, TestDispatcherProvider())

      val outcome = scaffolder.scaffold()

      outcome shouldBe WriteOutcome.Applied
      for (relative in expectedDirectories) {
        val path = tempDir.resolve(relative)
        Files.exists(path) shouldBe true
        Files.isDirectory(path) shouldBe true
      }
      val braian = tempDir.resolve("Braian.md")
      Files.exists(braian) shouldBe true
      Files.readString(braian).contains("# Braian") shouldBe true
    }

    test("scaffold is idempotent and preserves an existing Braian.md") {
      val tempDir = Files.createTempDirectory("vault-idempotent-")
      val scaffolder = PersonalGraphVaultScaffolder(tempDir, TestDispatcherProvider())
      val customContent = "# my own orientation\n\nDo not overwrite me.\n"
      Files.writeString(tempDir.resolve("Braian.md"), customContent)

      val first = scaffolder.scaffold()
      val second = scaffolder.scaffold()

      first shouldBe WriteOutcome.Applied
      second shouldBe WriteOutcome.Applied
      Files.readString(tempDir.resolve("Braian.md")) shouldBe customContent
      for (relative in expectedDirectories) {
        Files.exists(tempDir.resolve(relative)) shouldBe true
      }
    }
  })
