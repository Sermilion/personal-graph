package com.sermilion.personalgraph.data.codec

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlMap
import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.data.mapper.VaultNodeMappers
import com.sermilion.personalgraph.data.model.EmotionalStateNodeFrontmatterDataModel
import com.sermilion.personalgraph.data.model.EpisodeNodeFrontmatterDataModel
import com.sermilion.personalgraph.data.model.PatternNodeFrontmatterDataModel
import com.sermilion.personalgraph.data.model.StateNodeFrontmatterDataModel
import com.sermilion.personalgraph.data.model.SubjectNodeFrontmatterDataModel
import com.sermilion.personalgraph.domain.model.EmotionalStateNode
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import com.sermilion.personalgraph.domain.model.VaultNode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.tatarka.inject.annotations.Inject

@AppScope
@Inject
class MarkdownFrontmatterCodec {

  private val logger = KotlinLogging.logger {}

  private val yaml: Yaml = Yaml(
    configuration = YamlConfiguration(
      encodeDefaults = true,
      strictMode = false,
      breakScalarsAt = Int.MAX_VALUE,
    ),
  )

  private val stateYaml: Yaml = Yaml(
    configuration = YamlConfiguration(
      encodeDefaults = false,
      strictMode = false,
      breakScalarsAt = Int.MAX_VALUE,
    ),
  )

  fun encode(node: VaultNode): String = when (node) {
    is StateNode -> renderEncoded(stateYaml.encodeToString(VaultNodeMappers.toStateFrontmatter(node)), node.body)
    is EpisodeNode -> renderEncoded(yaml.encodeToString(VaultNodeMappers.toEpisodeFrontmatter(node)), node.body)
    is PatternNode -> renderEncoded(yaml.encodeToString(VaultNodeMappers.toPatternFrontmatter(node)), node.body)
    is SubjectNode -> renderEncoded(yaml.encodeToString(VaultNodeMappers.toSubjectFrontmatter(node)), node.body)
    is EmotionalStateNode -> renderEncoded(
      yaml.encodeToString(VaultNodeMappers.toEmotionalStateFrontmatter(node)),
      node.body,
    )
  }

  fun decode(id: NodeId, raw: String): VaultNode? {
    val sizeBytes = raw.toByteArray(Charsets.UTF_8).size
    if (sizeBytes > MAX_FILE_SIZE_BYTES) {
      logger.warn { "decode skipped: id=$id size=$sizeBytes exceeds max $MAX_FILE_SIZE_BYTES bytes" }
      return null
    }
    return decodeOrLogged(id, raw)
  }

  private fun decodeOrLogged(id: NodeId, raw: String): VaultNode? = try {
    decodeUnsafe(id, raw)
  } catch (e: SerializationException) {
    logger.warn(e) { "Failed to decode markdown for id=$id reason=${e.reasonString()}" }
    null
  } catch (e: IllegalArgumentException) {
    logger.warn(e) { "Invalid markdown frontmatter for id=$id reason=${e.reasonString()}" }
    null
  } catch (e: StackOverflowError) {
    logger.warn(e) { "Pathological YAML caused stack overflow for id=$id reason=${e.reasonString()}" }
    null
  } catch (e: OutOfMemoryError) {
    logger.warn(e) { "Pathological YAML caused OOM for id=$id reason=${e.reasonString()}" }
    null
  }

  private fun decodeUnsafe(id: NodeId, raw: String): VaultNode? {
    val (frontmatterText, body) = splitFrontmatter(raw) ?: return null
    val type = peekType(frontmatterText) ?: return null
    val bodyLinks = scanWikilinks(body)
    return when (type) {
      VaultNodeType.State -> {
        val fm = yaml.decodeFromString<StateNodeFrontmatterDataModel>(frontmatterText)
        VaultNodeMappers.fromStateFrontmatter(id, fm, body, bodyLinks)
      }
      VaultNodeType.Episode -> {
        val fm = yaml.decodeFromString<EpisodeNodeFrontmatterDataModel>(frontmatterText)
        VaultNodeMappers.fromEpisodeFrontmatter(id, fm, body, bodyLinks)
      }
      VaultNodeType.Pattern -> {
        val fm = yaml.decodeFromString<PatternNodeFrontmatterDataModel>(frontmatterText)
        VaultNodeMappers.fromPatternFrontmatter(id, fm, body, bodyLinks)
      }
      VaultNodeType.Subject -> {
        val fm = yaml.decodeFromString<SubjectNodeFrontmatterDataModel>(frontmatterText)
        VaultNodeMappers.fromSubjectFrontmatter(id, fm, body, bodyLinks)
      }
      VaultNodeType.EmotionalState -> {
        val fm = yaml.decodeFromString<EmotionalStateNodeFrontmatterDataModel>(frontmatterText)
        VaultNodeMappers.fromEmotionalStateFrontmatter(id, fm, body, bodyLinks)
      }
    }
  }

  fun scanWikilinks(body: String): List<NodeId> = WIKILINK_REGEX.findAll(body)
    .map { it.groupValues[1].trim() }
    .filter { it.isNotEmpty() }
    .distinct()
    .mapNotNull { runCatching { NodeId(it) }.getOrNull() }
    .toList()

  private fun renderEncoded(yamlText: String, body: String): String {
    val trimmedYaml = yamlText.trimEnd('\n')
    val builder = StringBuilder()
    builder.append(FENCE).append('\n')
    builder.append(trimmedYaml).append('\n')
    builder.append(FENCE).append('\n')
    builder.append(body)
    return builder.toString()
  }

  private fun splitFrontmatter(raw: String): Pair<String, String>? {
    val normalized = raw.replace("\r\n", "\n")
    if (!normalized.startsWith(FENCE)) return null
    val rest = normalized.removePrefix(FENCE).removePrefix("\n")
    val closingIndex = findClosingFence(rest)
    if (closingIndex < 0) return null
    val frontmatter = rest.substring(0, closingIndex)
    var bodyStart = closingIndex + FENCE.length
    if (bodyStart < rest.length && rest[bodyStart] == '\n') bodyStart += 1
    val body = if (bodyStart >= rest.length) "" else rest.substring(bodyStart)
    return frontmatter to body
  }

  private fun findClosingFence(rest: String): Int {
    var searchFrom = 0
    while (searchFrom <= rest.length) {
      val idx = rest.indexOf(FENCE, searchFrom)
      if (idx < 0) return -1
      val precededByNewline = idx == 0 || rest[idx - 1] == '\n'
      val followedByNewlineOrEnd = idx + FENCE.length == rest.length || rest[idx + FENCE.length] == '\n'
      if (precededByNewline && followedByNewlineOrEnd) {
        return idx
      }
      searchFrom = idx + FENCE.length
    }
    return -1
  }

  private fun peekType(frontmatterText: String): VaultNodeType? = try {
    val rootNode = yaml.parseToYamlNode(frontmatterText)
    val map = rootNode as? YamlMap ?: return null
    val typeValue = map.getScalar(TYPE_KEY)?.content
    typeValue?.let { VaultNodeType.fromDiscriminator(it) }
  } catch (e: SerializationException) {
    logger.warn(e) { "Failed to peek frontmatter type reason=${e.reasonString()}" }
    null
  } catch (e: IllegalArgumentException) {
    logger.warn(e) { "Invalid frontmatter type reason=${e.reasonString()}" }
    null
  }

  private fun Throwable.reasonString(): String = "${this::class.simpleName}: ${this.message.orEmpty()}"

  companion object {
    private const val FENCE: String = "---"
    private const val TYPE_KEY: String = "type"
    private const val MAX_FILE_SIZE_BYTES: Long = 1L * 1024L * 1024L
    private val WIKILINK_REGEX: Regex = Regex("""\[\[([^\]|]+)(?:\|[^\]]+)?]]""")
  }
}
