package com.sermilion.personalgraph.data.codec

import com.sermilion.personalgraph.domain.model.EmotionalStateNode
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

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

    test("state node without scope round-trips without emitting scope frontmatter") {
      val source = VaultNodeFixtures.stateNode(id = "state/preferences/global", body = "Global body.\n")

      val encoded = codec.encode(source)
      val decoded = codec.decode(source.id, encoded) as StateNode

      encoded shouldNotContain "\nscope:"
      encoded shouldNotContain "\nscopes:"
      decoded.scope shouldBe null
      decoded.scopes shouldBe emptyList()
    }

    test("state node with singular scope round-trips scope frontmatter") {
      val source = VaultNodeFixtures.stateNode(
        id = "state/preferences/capmo-only",
        body = "Scoped body.\n",
        scope = "work/capmo",
      )

      val encoded = codec.encode(source)
      val decoded = codec.decode(source.id, encoded) as StateNode

      encoded shouldContain "scope: \"work/capmo\""
      encoded shouldNotContain "\nscopes:"
      decoded.scope shouldBe "work/capmo"
      decoded.scopes shouldBe emptyList()
    }

    test("state node with plural scopes round-trips scopes frontmatter") {
      val source = VaultNodeFixtures.stateNode(
        id = "state/preferences/multi-scope",
        body = "Scoped body.\n",
        scopes = listOf("work/capmo", "work/skill-bill"),
      )

      val encoded = codec.encode(source)
      val decoded = codec.decode(source.id, encoded) as StateNode

      encoded shouldNotContain "\nscope:"
      encoded shouldContain "scopes:"
      decoded.scope shouldBe null
      decoded.scopes shouldBe listOf("work/capmo", "work/skill-bill")
    }

    test("state node with singular and plural scopes round-trips both frontmatter fields") {
      val source = VaultNodeFixtures.stateNode(
        id = "state/preferences/both-scope-shapes",
        body = "Scoped body.\n",
        scope = "general",
        scopes = listOf("work/readian", "creative/music"),
      )

      val encoded = codec.encode(source)
      val decoded = codec.decode(source.id, encoded) as StateNode

      encoded shouldContain "scope: \"general\""
      encoded shouldContain "scopes:"
      decoded.scope shouldBe "general"
      decoded.scopes shouldBe listOf("work/readian", "creative/music")
    }

    test("state consolidation metadata round-trips through encode + decode") {
      val source = VaultNodeFixtures.stateNode(
        id = "state/preferences/consolidated",
        body = "Body linking [[patterns/repeated-choice]].\n",
      ).copy(
        occurrenceCount = 3,
        sourceIds = listOf(NodeId("staging/observations/a"), NodeId("staging/observations/b")),
        patternLinks = listOf(NodeId("patterns/repeated-choice")),
        contradictedBy = listOf(NodeId("staging/observations/not-a")),
      )

      val encoded = codec.encode(source)
      val decoded = codec.decode(source.id, encoded) as StateNode

      decoded.occurrenceCount shouldBe source.occurrenceCount
      decoded.sourceIds shouldBe source.sourceIds
      decoded.patternLinks shouldBe source.patternLinks
      decoded.contradictedBy shouldBe source.contradictedBy
      decoded.body shouldBe source.body
    }

    test("episode node round-trips through encode + decode and preserves linked array") {
      val source = VaultNodeFixtures.episodeNode().copy(
        occurrenceCount = 2,
        sourceIds = listOf(NodeId("staging/observations/episode-a")),
        patternLinks = listOf(NodeId("patterns/repeated-choice")),
        contradictedBy = listOf(NodeId("staging/observations/not-episode-a")),
      )

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
      decoded.occurrenceCount shouldBe source.occurrenceCount
      decoded.sourceIds shouldBe source.sourceIds
      decoded.patternLinks shouldBe source.patternLinks
      decoded.contradictedBy shouldBe source.contradictedBy
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

    test("subject node round-trips through encode + decode") {
      val source = VaultNodeFixtures.subjectNode()

      val encoded = codec.encode(source)
      val decoded = codec.decode(source.id, encoded) as SubjectNode

      decoded.id shouldBe source.id
      decoded.domain shouldBe source.domain
      decoded.subject shouldBe source.subject
      decoded.aliases shouldBe source.aliases
      decoded.evidenceCount shouldBe source.evidenceCount
      decoded.sourceIds shouldBe source.sourceIds
      decoded.links.map { it.value } shouldContainAll source.links.map { it.value }
    }

    test("pattern consolidation metadata round-trips through encode + decode") {
      val source = VaultNodeFixtures.patternNode().copy(
        sourceIds = listOf(NodeId("state/preferences/source-a")),
        patternLinks = listOf(NodeId("patterns/meta-pattern")),
      )

      val encoded = codec.encode(source)
      val decoded = codec.decode(source.id, encoded) as PatternNode

      decoded.sourceIds shouldBe source.sourceIds
      decoded.patternLinks shouldBe source.patternLinks
    }

    test("emotional-state node round-trips through encode + decode") {
      val source = VaultNodeFixtures.emotionalStateNode().copy(
        occurrenceCount = 2,
        sourceIds = listOf(NodeId("staging/observations/emotion-a")),
        patternLinks = listOf(NodeId("patterns/repeated-trigger")),
        contradictedBy = listOf(NodeId("staging/observations/not-emotion-a")),
      )

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
      decoded.occurrenceCount shouldBe source.occurrenceCount
      decoded.sourceIds shouldBe source.sourceIds
      decoded.patternLinks shouldBe source.patternLinks
      decoded.contradictedBy shouldBe source.contradictedBy
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
