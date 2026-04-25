package com.sermilion.personalgraph.data.codec

import com.sermilion.personalgraph.domain.model.EmotionalStateNode
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class MarkdownFrontmatterCodecRoundTripTest :
  FunSpec({

    val codec = MarkdownFrontmatterCodec()

    test("state node round-trips through encode + decode") {
      val source = VaultNodeFixtures.stateNode(
        id = "state/preferences/editor-indent",
        body = "Wikilink to [[state/roles/current-role]] in body.\n",
      )

      val encoded = codec.encode(source)
      val decoded = codec.decode(source.id, encoded) as StateNode

      decoded.id shouldBe source.id
      decoded.category shouldBe source.category
      decoded.confidence shouldBe source.confidence
      decoded.body shouldBe source.body
      decoded.links.map { it.value } shouldBe listOf("state/roles/current-role")
    }

    test("episode node round-trips through encode + decode and preserves linked array") {
      val source = VaultNodeFixtures.episodeNode()

      val encoded = codec.encode(source)
      val decoded = codec.decode(source.id, encoded) as EpisodeNode

      decoded.id shouldBe source.id
      decoded.episodeType shouldBe source.episodeType
      decoded.domain shouldBe source.domain
      decoded.topic shouldBe source.topic
      decoded.intensity shouldBe source.intensity
      decoded.date shouldBe source.date
      decoded.body shouldBe source.body
      decoded.links.map { it.value } shouldContainAll source.links.map { it.value }
    }

    test("pattern node round-trips through encode + decode") {
      val source = VaultNodeFixtures.patternNode()

      val encoded = codec.encode(source)
      val decoded = codec.decode(source.id, encoded) as PatternNode

      decoded.id shouldBe source.id
      decoded.hypothesis shouldBe source.hypothesis
      decoded.evidenceCount shouldBe source.evidenceCount
      decoded.domainsSeenIn shouldBe source.domainsSeenIn
      decoded.body shouldBe source.body
      decoded.links.map { it.value } shouldContainAll source.links.map { it.value }
    }

    test("emotional-state node round-trips through encode + decode") {
      val source = VaultNodeFixtures.emotionalStateNode()

      val encoded = codec.encode(source)
      val decoded = codec.decode(source.id, encoded) as EmotionalStateNode

      decoded.id shouldBe source.id
      decoded.marker shouldBe source.marker
      decoded.intensity shouldBe source.intensity
      decoded.context shouldBe source.context
      decoded.triggerHypothesis shouldBe source.triggerHypothesis
      decoded.date shouldBe source.date
      decoded.body shouldBe source.body
      decoded.links.map { it.value } shouldContainAll source.links.map { it.value }
    }

    test("decode returns null for malformed markdown without throwing") {
      val malformed = "no frontmatter at all"
      codec.decode(NodeId("state/preferences/anything"), malformed).shouldBeNull()

      val unclosedFence = "---\ntype: state\ncategory: preference\nconfidence: high\n"
      codec.decode(NodeId("state/preferences/anything"), unclosedFence).shouldBeNull()

      val unknownType = "---\ntype: nonsense\nfoo: bar\n---\nbody\n"
      codec.decode(NodeId("state/preferences/anything"), unknownType).shouldBeNull()
    }

    test("decode returns null for oversized input without parsing") {
      val oversized = buildString {
        append("---\n")
        append("type: state\n")
        append("category: preference\n")
        append("confidence: high\n")
        append("---\n")
        append("a".repeat(2 * 1024 * 1024))
      }
      codec.decode(NodeId("state/preferences/oversized"), oversized).shouldBeNull()
    }
  })
