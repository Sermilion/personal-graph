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
      "domains/work/skill-bill/events",
      "domains/work/skill-bill/subjects",
      "domains/work/readian/events",
      "domains/work/readian/subjects",
      "domains/work/context-app/events",
      "domains/work/context-app/subjects",
      "domains/work/personal-graph/events",
      "domains/work/personal-graph/subjects",
      "domains/personal/events",
      "domains/personal/subjects",
      "domains/personal-graph/events",
      "domains/personal-graph/subjects",
      "domains/creative/events",
      "domains/creative/subjects",
      "patterns",
      "emotional-states",
      "timeline",
      "staging/observations",
      "staging/sensitive",
      "outdated/resolved",
      "people",
    )

    val expectedIndexes = listOf(
      "domains/index.md",
      "domains/work/index.md",
      "domains/work/capmo/index.md",
      "domains/work/reddit/index.md",
      "domains/work/skill-bill/index.md",
      "domains/work/readian/index.md",
      "domains/work/context-app/index.md",
      "domains/work/personal-graph/index.md",
      "domains/personal/index.md",
      "domains/personal-graph/index.md",
      "domains/creative/index.md",
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
      for (relative in expectedIndexes) {
        val index = tempDir.resolve(relative)
        Files.exists(index) shouldBe true
        Files.readString(index).contains("Use this index for orientation") shouldBe true
      }
    }

    test("scaffold is idempotent and preserves existing user-authored notes") {
      val tempDir = Files.createTempDirectory("vault-idempotent-")
      val scaffolder = PersonalGraphVaultScaffolder(tempDir, TestDispatcherProvider())
      val customContent = "# my own orientation\n\nDo not overwrite me.\n"
      val customIndex = "# Capmo\n\nCustom domain orientation.\n"
      Files.createDirectories(tempDir.resolve("domains/work/capmo"))
      Files.writeString(tempDir.resolve("Braian.md"), customContent)
      Files.writeString(tempDir.resolve("domains/work/capmo/index.md"), customIndex)

      val first = scaffolder.scaffold()
      val second = scaffolder.scaffold()

      first shouldBe WriteOutcome.Applied
      second shouldBe WriteOutcome.Applied
      Files.readString(tempDir.resolve("Braian.md")) shouldBe customContent
      Files.readString(tempDir.resolve("domains/work/capmo/index.md")) shouldBe customIndex
      for (relative in expectedDirectories) {
        Files.exists(tempDir.resolve(relative)) shouldBe true
      }
    }
  })
