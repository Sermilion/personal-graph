package com.sermilion.personalgraph.data.retrieval

import com.sermilion.personalgraph.domain.retrieval.CompactMapEntry

private const val MIN_QUERY_TERM_LENGTH: Int = 4
private const val SIMPLE_RELEVANCE_BOOST: Int = 10
private const val COMPOUND_RELEVANCE_BOOST: Int = 20
private const val STEM_ING_LENGTH: Int = 3
private const val STEM_ED_LENGTH: Int = 2
private const val STEM_PLURAL_LENGTH: Int = 1
private val QUERY_TOKEN_REGEX = Regex("[a-z0-9]+")

internal fun sessionStartRelevanceTerms(message: String): Set<String> = QUERY_TOKEN_REGEX
  .findAll(message.lowercase())
  .map { it.value }
  .toList()
  .let { tokens ->
    tokens.flatMap { it.relevanceVariants() } + tokens.adjacentCompounds()
  }
  .filter { it.length >= MIN_QUERY_TERM_LENGTH }
  .toSet()

internal fun CompactMapEntry.relevanceBoost(
  relevanceTerms: Set<String>,
  maxBoost: Int,
): Int {
  if (relevanceTerms.isEmpty()) return 0
  val text = searchableText()
  val boost = relevanceTerms.sumOf { term ->
    when {
      !text.contains(term) -> 0
      term.contains("-") -> COMPOUND_RELEVANCE_BOOST
      else -> SIMPLE_RELEVANCE_BOOST
    }
  }
  return boost.coerceAtMost(maxBoost)
}

private fun CompactMapEntry.searchableText(): String = buildString {
  append(id.substringAfterLast('/').lowercase())
  append(' ')
  summary?.let { append(it.lowercase()) }
  append(' ')
  aliases.joinTo(this, separator = " ") { it.lowercase() }
}

private fun String.relevanceVariants(): List<String> = buildList {
  add(this@relevanceVariants)
  when {
    startsWith("implement") -> add("implement")
    startsWith("feature") -> add("feature")
    endsWith("ing") -> add(this@relevanceVariants.dropLast(STEM_ING_LENGTH))
    endsWith("ed") -> add(this@relevanceVariants.dropLast(STEM_ED_LENGTH))
    endsWith("s") -> add(this@relevanceVariants.dropLast(STEM_PLURAL_LENGTH))
  }
}

private fun List<String>.adjacentCompounds(): List<String> = zipWithNext()
  .flatMap { (left, right) ->
    left.relevanceVariants().flatMap { leftVariant ->
      right.relevanceVariants().map { rightVariant -> "$leftVariant-$rightVariant" }
    }
  }
