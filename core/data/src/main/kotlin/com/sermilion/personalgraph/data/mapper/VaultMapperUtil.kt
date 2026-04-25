package com.sermilion.personalgraph.data.mapper

import com.sermilion.personalgraph.data.mapper.VaultMapperConstants.FRONTMATTER_DATE_TIMEZONE
import com.sermilion.personalgraph.domain.model.NodeId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal fun Instant.toFrontmatterLocalDate(): LocalDate = this.toLocalDateTime(FRONTMATTER_DATE_TIMEZONE).date

internal fun LocalDate.atStartOfDayUtc(): Instant {
  val dt = LocalDateTime(this, LocalTime(0, 0))
  return dt.toInstant(FRONTMATTER_DATE_TIMEZONE)
}

internal fun wrapWikilink(value: String): String = "[[$value]]"

internal fun unwrapWikilink(raw: String): NodeId? {
  val trimmed = raw.trim()
  val inner = if (trimmed.startsWith("[[") && trimmed.endsWith("]]")) {
    trimmed.substring(2, trimmed.length - 2)
  } else {
    trimmed
  }
  val target = inner.substringBefore('|').trim()
  return runCatching { NodeId(target) }.getOrNull()
}

internal fun mergeLinks(frontmatterLinks: List<NodeId>, bodyLinks: List<NodeId>): List<NodeId> {
  val seen = mutableSetOf<String>()
  val result = mutableListOf<NodeId>()
  for (link in frontmatterLinks + bodyLinks) {
    if (seen.add(link.value)) {
      result.add(link)
    }
  }
  return result
}
