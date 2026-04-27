package com.sermilion.personalgraph.data.mapper

import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

class VaultNodeMappersTest :
  FunSpec({

    test("StateNode created/updated convert through UTC timezone") {
      val instantLateUtc = Instant.parse("2026-04-24T23:30:00Z")
      val source = VaultNodeFixtures.stateNode(id = "state/preferences/sample").copy(
        createdAt = instantLateUtc,
        updatedAt = instantLateUtc,
      )

      val frontmatter = VaultNodeMappers.toStateFrontmatter(source)

      frontmatter.created shouldBe LocalDate(2026, 4, 24)
      frontmatter.updated shouldBe LocalDate(2026, 4, 24)
    }

    test("PatternNode created/lastObserved convert through UTC timezone") {
      val source = VaultNodeFixtures.patternNode().copy(
        createdAt = Instant.parse("2026-04-24T22:00:00Z"),
        lastObserved = Instant.parse("2026-04-23T22:00:00Z"),
      )

      val frontmatter = VaultNodeMappers.toPatternFrontmatter(source)

      frontmatter.created shouldBe LocalDate(2026, 4, 24)
      frontmatter.lastObserved shouldBe LocalDate(2026, 4, 23)
    }

    test("SubjectNode created/updated convert through UTC timezone") {
      val source = VaultNodeFixtures.subjectNode().copy(
        createdAt = Instant.parse("2026-04-24T22:00:00Z"),
        updatedAt = Instant.parse("2026-04-25T01:00:00Z"),
      )

      val frontmatter = VaultNodeMappers.toSubjectFrontmatter(source)

      frontmatter.created shouldBe LocalDate(2026, 4, 24)
      frontmatter.updated shouldBe LocalDate(2026, 4, 25)
    }
  })
