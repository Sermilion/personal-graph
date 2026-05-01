package com.sermilion.personalgraph.data.capture

internal object GeneratedSlugPolicy {

  private const val MAX_SLUG_WORDS: Int = 8
  private const val SLUG_FALLBACK: String = "untitled"

  private val normalizeRegex: Regex = Regex("[^a-z0-9]+")

  fun generatedObservationId(value: String): String = boundedSlug(
    value = value.lineSequence().firstOrNull().orEmpty(),
    dropStopWords = true,
  )

  fun generatedLeaf(value: String): String = boundedSlug(
    value = value,
    dropStopWords = false,
  )

  fun callerLeaf(value: String): String = slug(value.substringAfterLast('/'))

  private fun boundedSlug(value: String, dropStopWords: Boolean): String {
    val words = slug(value)
      .split('-')
      .filter(String::isNotBlank)
      .filterNot { dropStopWords && it in generatedIdStopWords }
      .take(MAX_SLUG_WORDS)
    return words.joinToString("-").ifEmpty { SLUG_FALLBACK }
  }

  private fun slug(value: String): String = value.lowercase()
    .replace(normalizeRegex, "-")
    .trim('-')
    .ifEmpty { SLUG_FALLBACK }

  private val generatedIdStopWords: Set<String> = setOf(
    "a",
    "an",
    "and",
    "as",
    "for",
    "in",
    "of",
    "on",
    "or",
    "the",
    "to",
  )
}
