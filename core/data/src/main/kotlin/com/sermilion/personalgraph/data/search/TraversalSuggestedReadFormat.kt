package com.sermilion.personalgraph.data.search

internal fun String.suggestedReadReasonValue(): String = when (lowercase()) {
  "maxnodes" -> "pruned by max_nodes"
  "budgettokens" -> "pruned by budget_tokens"
  else -> lowercase()
}

internal fun Int.priorityValue(): String = when {
  this >= HIGH_PRIORITY_SCORE -> "high"
  this >= MEDIUM_PRIORITY_SCORE -> "medium"
  else -> "low"
}

private const val HIGH_PRIORITY_SCORE: Int = 80
private const val MEDIUM_PRIORITY_SCORE: Int = 40
