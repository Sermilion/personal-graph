package com.sermilion.personalgraph.domain.tokens

import com.sermilion.personalgraph.domain.graph.GraphIndexEntry

/**
 * Approximate, deterministic token estimator.
 *
 * The estimator is intentionally not a tokenizer: it produces a stable upper-bound
 * approximation of how many tokens a given text would consume by dividing the
 * character count by [CHARS_PER_TOKEN]. The result is deterministic for a given
 * input — the same input always produces the same value — but it is only an
 * approximation: callers should not treat the value as exact.
 *
 * Used to budget MCP responses without paying the cost of a real tokenizer.
 */
object TokenEstimator {

  /**
   * Average characters per token used for the approximation. Four is a widely
   * documented heuristic for English text and matches the order of magnitude of
   * common BPE tokenizers; absolute precision is not required since the value
   * is a budget hint, not a contract.
   */
  const val CHARS_PER_TOKEN: Int = 4

  /**
   * Estimate the number of tokens consumed by [text]. Returns 0 for empty input.
   * Approximate and deterministic.
   */
  fun estimateString(text: String): Int {
    if (text.isEmpty()) return 0
    return (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN
  }

  /**
   * Typed wrapper around [estimateString] for assembled metadata blocks (such as
   * a serialized YAML frontmatter snippet). Kept distinct from [estimateString]
   * so future call sites can specialize metadata estimation without churn.
   * Approximate and deterministic.
   */
  fun estimateMetadata(metadataBlock: String): Int = estimateString(metadataBlock)

  /**
   * Typed wrapper around [estimateString] for node bodies. Kept distinct from
   * [estimateString] so future call sites can specialize body estimation
   * without churn. Approximate and deterministic.
   */
  fun estimateBody(body: String): Int = estimateString(body)

  /**
   * Estimate the total tokens consumed by a list of [GraphIndexEntry] values.
   *
   * For each entry, the cost is the sum of:
   *  - the entry's pre-computed [GraphIndexEntry.bodyTokenEstimate],
   *  - the metadata block assembled deterministically from typed fields, and
   *  - the entry's snippet.
   *
   * Approximate and deterministic: identical input lists always yield the same Int.
   */
  fun estimateEntries(entries: List<GraphIndexEntry>): Int {
    var total = 0
    for (entry in entries) {
      total += entry.bodyTokenEstimate
      total += estimateMetadata(buildMetadataBlock(entry))
      total += estimateString(entry.snippet)
    }
    return total
  }

  private fun buildMetadataBlock(entry: GraphIndexEntry): String {
    val builder = StringBuilder()
    builder.append("id:").append(entry.id.value).append('\n')
    builder.append("branch:").append(entry.branch).append('\n')
    builder.append("type:").append(entry.type).append('\n')
    entry.category?.let { builder.append("category:").append(it).append('\n') }
    entry.domain?.let { builder.append("domain:").append(it).append('\n') }
    entry.scope?.let { builder.append("scope:").append(it).append('\n') }
    if (entry.scopes.isNotEmpty()) {
      builder.append("scopes:").append(entry.scopes.joinToString(",")).append('\n')
    }
    entry.subject?.let { builder.append("subject:").append(it).append('\n') }
    entry.topic?.let { builder.append("topic:").append(it).append('\n') }
    if (entry.aliases.isNotEmpty()) {
      builder.append("aliases:").append(entry.aliases.joinToString(",")).append('\n')
    }
    entry.hypothesis?.let { builder.append("hypothesis:").append(it).append('\n') }
    entry.date?.let { builder.append("date:").append(it).append('\n') }
    builder.append("updated:").append(entry.updated).append('\n')
    builder.append("created:").append(entry.created).append('\n')
    if (entry.links.isNotEmpty()) {
      builder.append("links:").append(entry.links.joinToString(",") { it.value }).append('\n')
    }
    builder.append("link_count:").append(entry.linkCount).append('\n')
    builder.append("body_token_estimate:").append(entry.bodyTokenEstimate).append('\n')
    builder.append("file_size:").append(entry.fileSize).append('\n')
    builder.append("file_modified_at:").append(entry.fileModifiedAt).append('\n')
    return builder.toString()
  }
}
