package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalMode
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalRequest
import com.sermilion.personalgraph.domain.search.SearchField
import com.sermilion.personalgraph.domain.search.SearchQuery
import kotlinx.serialization.json.JsonObject

internal fun parseSearchNodesArgs(args: JsonObject): Parsed<SearchQuery> {
  val query = args.stringOrNull(ToolSchemas.KEY_QUERY)
    ?: return Parsed.Failure(invalidInputJson(ToolSchemas.KEY_QUERY, REASON_MISSING))
  return resolveSearchNodesArgs(args, query)
}

private fun resolveSearchNodesArgs(args: JsonObject, query: String): Parsed<SearchQuery> {
  val parts = collectSearchNodesParts(args)
  val firstError = parts.firstError
  if (firstError != null) return Parsed.Failure(firstError)
  return Parsed.Success(
    SearchQuery(
      query = query,
      branches = parts.branches,
      limit = parts.limit ?: SearchQuery.DEFAULT_LIMIT,
      searchFields = parts.fields,
      bodyFallback = args.booleanOrNull(ToolSchemas.KEY_BODY_FALLBACK) ?: true,
      includeBody = args.booleanOrNull(ToolSchemas.KEY_INCLUDE_BODY) ?: false,
    ),
  )
}

private data class SearchNodesParts(
  val branches: List<String>,
  val limit: Int?,
  val fields: Set<SearchField>,
  val firstError: JsonObject?,
)

private fun collectSearchNodesParts(args: JsonObject): SearchNodesParts {
  val branchesParsed = args.stringArrayArgument(ToolSchemas.KEY_BRANCHES)
  val limitParsed = args.nonNegativeIntArgument(ToolSchemas.KEY_LIMIT)
  val fieldsParsed = parseSearchFields(args)
  val error = firstFailureJson(branchesParsed, limitParsed, fieldsParsed)
  return SearchNodesParts(
    branches = if (branchesParsed is Parsed.Success) branchesParsed.value else emptyList(),
    limit = if (limitParsed is Parsed.Success) limitParsed.value else null,
    fields = if (fieldsParsed is Parsed.Success) fieldsParsed.value else SearchQuery.DEFAULT_FIELDS,
    firstError = error,
  )
}

private fun parseSearchFields(args: JsonObject): Parsed<Set<SearchField>> {
  val raw = args.stringArrayArgument(ToolSchemas.KEY_SEARCH_FIELDS)
  if (raw is Parsed.Failure) return Parsed.Failure(raw.json)
  val list = (raw as Parsed.Success).value
  if (list.isEmpty()) return Parsed.Success(SearchQuery.DEFAULT_FIELDS)
  return collectSearchFields(list)
}

private fun collectSearchFields(list: List<String>): Parsed<Set<SearchField>> {
  val parsed = mutableSetOf<SearchField>()
  for (entry in list) {
    val field = parseSearchField(entry)
      ?: return Parsed.Failure(invalidInputJson(ToolSchemas.KEY_SEARCH_FIELDS, REASON_INVALID))
    parsed += field
  }
  return Parsed.Success(parsed)
}

private fun parseSearchField(raw: String): SearchField? = when (raw.lowercase()) {
  ToolSchemas.SEARCH_FIELD_ID -> SearchField.Id
  ToolSchemas.SEARCH_FIELD_METADATA -> SearchField.Metadata
  ToolSchemas.SEARCH_FIELD_BODY -> SearchField.Body
  else -> null
}

internal fun parseSessionStartRetrievalRequest(args: JsonObject): Parsed<SessionStartRetrievalRequest> {
  val message = args.stringOrNull(ToolSchemas.KEY_MESSAGE)
    ?: return Parsed.Failure(invalidInputJson(ToolSchemas.KEY_MESSAGE, REASON_MISSING))
  return when (val mode = parseSessionStartRetrievalModeArgument(args)) {
    is Parsed.Failure -> Parsed.Failure(mode.json)
    is Parsed.Success -> Parsed.Success(
      SessionStartRetrievalRequest(
        firstSubstantiveMessage = message,
        retrievalMode = mode.value,
      ),
    )
  }
}

private fun parseSessionStartRetrievalModeArgument(
  args: JsonObject,
): Parsed<SessionStartRetrievalMode> {
  val modeRaw = args.optionalStringArgument(ToolSchemas.KEY_RETRIEVAL_MODE)
  return when (modeRaw) {
    is Parsed.Failure -> Parsed.Failure(modeRaw.json)
    is Parsed.Success -> resolveSessionStartRetrievalMode(modeRaw.value)
  }
}

private fun resolveSessionStartRetrievalMode(raw: String?): Parsed<SessionStartRetrievalMode> {
  if (raw == null) return Parsed.Success(SessionStartRetrievalMode.MapFirst)
  val mode = parseSessionStartRetrievalMode(raw)
  return if (mode != null) {
    Parsed.Success(mode)
  } else {
    Parsed.Failure(invalidInputJson(ToolSchemas.KEY_RETRIEVAL_MODE, REASON_INVALID))
  }
}
