package com.sermilion.personalgraph.domain.tokens

import com.sermilion.personalgraph.domain.graph.GraphIndexEntry
import com.sermilion.personalgraph.domain.model.NodeId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class TokenEstimatorTest :
  FunSpec({

    val sampleInstant = Instant.parse("2026-04-24T12:00:00Z")

    fun entry(
      id: String = "state/preferences/sample",
      body: String = "body content here",
      snippet: String = "snippet content here",
      bodyTokenEstimate: Int = TokenEstimator.estimateBody(body),
    ): GraphIndexEntry = GraphIndexEntry(
      id = NodeId(id),
      branch = "state/preferences",
      type = "state",
      category = "preference",
      domain = null,
      scope = null,
      scopes = emptyList(),
      subject = null,
      topic = null,
      aliases = emptyList(),
      hypothesis = null,
      date = null,
      updated = sampleInstant,
      created = sampleInstant,
      links = emptyList(),
      linkCount = 0,
      snippet = snippet,
      bodyTokenEstimate = bodyTokenEstimate,
      fileSize = 100L,
      fileModifiedAt = sampleInstant,
    )

    test("estimateString is deterministic for repeated calls") {
      val text = "the quick brown fox jumps over the lazy dog"
      val first = TokenEstimator.estimateString(text)
      repeat(10) {
        TokenEstimator.estimateString(text) shouldBe first
      }
    }

    test("estimateString returns 0 for empty input") {
      TokenEstimator.estimateString("") shouldBe 0
    }

    test("estimateString rounds up by character count and CHARS_PER_TOKEN") {
      TokenEstimator.estimateString("abcd") shouldBe 1
      TokenEstimator.estimateString("abcde") shouldBe 2
      TokenEstimator.estimateString("a") shouldBe 1
    }

    test("estimateString is additive within rounding tolerance") {
      val a = "lorem ipsum dolor sit amet"
      val b = "consectetur adipiscing elit"
      val combined = TokenEstimator.estimateString(a + b)
      val parts = TokenEstimator.estimateString(a) + TokenEstimator.estimateString(b)
      val delta = parts - combined
      (delta in 0..1) shouldBe true
    }

    test("estimateMetadata behaves identically to estimateString") {
      val block = "type: state\ncategory: preference\n"
      TokenEstimator.estimateMetadata(block) shouldBe TokenEstimator.estimateString(block)
    }

    test("estimateBody behaves identically to estimateString") {
      val body = "Some body content with multiple sentences. Continues here."
      TokenEstimator.estimateBody(body) shouldBe TokenEstimator.estimateString(body)
    }

    test("estimateEntries is deterministic across repeated calls") {
      val list = listOf(
        entry(id = "state/preferences/a", body = "alpha body", snippet = "alpha snippet"),
        entry(id = "state/preferences/b", body = "beta body content", snippet = "beta snippet"),
      )
      val first = TokenEstimator.estimateEntries(list)
      repeat(5) {
        TokenEstimator.estimateEntries(list) shouldBe first
      }
    }

    test("estimateEntries sums per-entry body and snippet contributions plus a non-zero metadata cost") {
      val list = listOf(
        entry(id = "state/preferences/a", body = "alpha body", snippet = "alpha snippet"),
        entry(id = "state/preferences/b", body = "beta body content", snippet = "beta snippet"),
      )
      val total = TokenEstimator.estimateEntries(list)
      val bodyAndSnippet = list.sumOf { e ->
        e.bodyTokenEstimate + TokenEstimator.estimateString(e.snippet)
      }
      total shouldBeGreaterThan bodyAndSnippet
    }

    test("estimateEntries returns 0 for empty list") {
      TokenEstimator.estimateEntries(emptyList()) shouldBe 0
    }
  })
