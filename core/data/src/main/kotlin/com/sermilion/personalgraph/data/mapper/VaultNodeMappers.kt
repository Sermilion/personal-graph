package com.sermilion.personalgraph.data.mapper

import com.sermilion.personalgraph.data.model.EmotionalStateNodeFrontmatterDataModel
import com.sermilion.personalgraph.data.model.EpisodeNodeFrontmatterDataModel
import com.sermilion.personalgraph.data.model.PatternNodeFrontmatterDataModel
import com.sermilion.personalgraph.data.model.StateNodeFrontmatterDataModel
import com.sermilion.personalgraph.domain.model.Confidence
import com.sermilion.personalgraph.domain.model.EmotionMarker
import com.sermilion.personalgraph.domain.model.EmotionalStateNode
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.EpisodeType
import com.sermilion.personalgraph.domain.model.Intensity
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateCategory
import com.sermilion.personalgraph.domain.model.StateNode

object VaultNodeMappers {

  private val stateCategoryToString: Map<StateCategory, String> = mapOf(
    StateCategory.Preference to "preference",
    StateCategory.Role to "role",
    StateCategory.Knowledge to "knowledge",
    StateCategory.Fact to "fact",
  )
  private val stringToStateCategory: Map<String, StateCategory> =
    stateCategoryToString.entries.associate { (k, v) -> v to k }

  private val confidenceToString: Map<Confidence, String> = mapOf(
    Confidence.High to "high",
    Confidence.Medium to "medium",
    Confidence.Low to "low",
  )
  private val stringToConfidence: Map<String, Confidence> =
    confidenceToString.entries.associate { (k, v) -> v to k }

  private val episodeTypeToString: Map<EpisodeType, String> = mapOf(
    EpisodeType.Purchase to "purchase",
    EpisodeType.AdviceSeeking to "advice-seeking",
    EpisodeType.Research to "research",
    EpisodeType.DesignDoc to "design-doc",
    EpisodeType.Question to "question",
    EpisodeType.PersonalStory to "personal-story",
    EpisodeType.WorkInteraction to "work-interaction",
    EpisodeType.Decision to "decision",
  )
  private val stringToEpisodeType: Map<String, EpisodeType> =
    episodeTypeToString.entries.associate { (k, v) -> v to k }

  private val intensityToString: Map<Intensity, String> = mapOf(
    Intensity.Low to "low",
    Intensity.Medium to "medium",
    Intensity.High to "high",
  )
  private val stringToIntensity: Map<String, Intensity> =
    intensityToString.entries.associate { (k, v) -> v to k }

  private val emotionMarkerToString: Map<EmotionMarker, String> = mapOf(
    EmotionMarker.Frustration to "frustration",
    EmotionMarker.Excitement to "excitement",
    EmotionMarker.Anxiety to "anxiety",
    EmotionMarker.Curiosity to "curiosity",
    EmotionMarker.Disengagement to "disengagement",
    EmotionMarker.Satisfaction to "satisfaction",
    EmotionMarker.Confusion to "confusion",
  )
  private val stringToEmotionMarker: Map<String, EmotionMarker> =
    emotionMarkerToString.entries.associate { (k, v) -> v to k }

  fun toStateFrontmatter(node: StateNode): StateNodeFrontmatterDataModel = StateNodeFrontmatterDataModel(
    category = stateCategoryToString.getValue(node.category),
    confidence = confidenceToString.getValue(node.confidence),
    created = node.createdAt.toFrontmatterLocalDate(),
    updated = node.updatedAt.toFrontmatterLocalDate(),
  )

  fun fromStateFrontmatter(
    id: NodeId,
    frontmatter: StateNodeFrontmatterDataModel,
    body: String,
    bodyLinks: List<NodeId>,
  ): StateNode? {
    val category = stringToStateCategory[frontmatter.category] ?: return null
    val confidence = stringToConfidence[frontmatter.confidence] ?: return null
    return StateNode(
      id = id,
      createdAt = frontmatter.created.atStartOfDayUtc(),
      updatedAt = frontmatter.updated.atStartOfDayUtc(),
      body = body,
      links = bodyLinks,
      category = category,
      confidence = confidence,
    )
  }

  fun toEpisodeFrontmatter(node: EpisodeNode): EpisodeNodeFrontmatterDataModel = EpisodeNodeFrontmatterDataModel(
    date = node.date,
    episodeType = episodeTypeToString.getValue(node.episodeType),
    domain = node.domain,
    topic = node.topic,
    linked = node.links.map { wrapWikilink(it.value) },
    intensity = intensityToString.getValue(node.intensity),
  )

  fun fromEpisodeFrontmatter(
    id: NodeId,
    frontmatter: EpisodeNodeFrontmatterDataModel,
    body: String,
    bodyLinks: List<NodeId>,
  ): EpisodeNode? {
    val episodeType = stringToEpisodeType[frontmatter.episodeType] ?: return null
    val intensity = stringToIntensity[frontmatter.intensity] ?: return null
    val frontmatterLinks = frontmatter.linked.mapNotNull { unwrapWikilink(it) }
    val mergedLinks = mergeLinks(frontmatterLinks, bodyLinks)
    return EpisodeNode(
      id = id,
      createdAt = frontmatter.date,
      updatedAt = frontmatter.date,
      body = body,
      links = mergedLinks,
      date = frontmatter.date,
      episodeType = episodeType,
      domain = frontmatter.domain,
      topic = frontmatter.topic,
      intensity = intensity,
    )
  }

  fun toPatternFrontmatter(node: PatternNode): PatternNodeFrontmatterDataModel = PatternNodeFrontmatterDataModel(
    created = node.createdAt.toFrontmatterLocalDate(),
    hypothesis = node.hypothesis,
    evidenceCount = node.evidenceCount,
    lastObserved = node.lastObserved.toFrontmatterLocalDate(),
    domainsSeenIn = node.domainsSeenIn,
    contradictedBy = node.contradictedBy.map { it.value },
  )

  fun fromPatternFrontmatter(
    id: NodeId,
    frontmatter: PatternNodeFrontmatterDataModel,
    body: String,
    bodyLinks: List<NodeId>,
  ): PatternNode = PatternNode(
    id = id,
    createdAt = frontmatter.created.atStartOfDayUtc(),
    updatedAt = frontmatter.lastObserved.atStartOfDayUtc(),
    body = body,
    links = bodyLinks,
    hypothesis = frontmatter.hypothesis,
    evidenceCount = frontmatter.evidenceCount,
    lastObserved = frontmatter.lastObserved.atStartOfDayUtc(),
    domainsSeenIn = frontmatter.domainsSeenIn,
    contradictedBy = frontmatter.contradictedBy.mapNotNull { runCatching { NodeId(it) }.getOrNull() },
  )

  fun toEmotionalStateFrontmatter(
    node: EmotionalStateNode,
  ): EmotionalStateNodeFrontmatterDataModel = EmotionalStateNodeFrontmatterDataModel(
    date = node.date,
    marker = emotionMarkerToString.getValue(node.marker),
    intensity = intensityToString.getValue(node.intensity),
    context = node.context,
    triggerHypothesis = node.triggerHypothesis,
    linked = node.links.map { wrapWikilink(it.value) },
    contradictedBy = node.contradictedBy.map { it.value },
  )

  fun fromEmotionalStateFrontmatter(
    id: NodeId,
    frontmatter: EmotionalStateNodeFrontmatterDataModel,
    body: String,
    bodyLinks: List<NodeId>,
  ): EmotionalStateNode? {
    val marker = stringToEmotionMarker[frontmatter.marker] ?: return null
    val intensity = stringToIntensity[frontmatter.intensity] ?: return null
    val frontmatterLinks = frontmatter.linked.mapNotNull { unwrapWikilink(it) }
    val mergedLinks = mergeLinks(frontmatterLinks, bodyLinks)
    return EmotionalStateNode(
      id = id,
      createdAt = frontmatter.date,
      updatedAt = frontmatter.date,
      body = body,
      links = mergedLinks,
      date = frontmatter.date,
      marker = marker,
      intensity = intensity,
      context = frontmatter.context,
      triggerHypothesis = frontmatter.triggerHypothesis,
      contradictedBy = frontmatter.contradictedBy.mapNotNull { runCatching { NodeId(it) }.getOrNull() },
    )
  }
}
