package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.search.TraversalEdgeType
import com.sermilion.personalgraph.domain.search.TraversalRankBy
import com.sermilion.personalgraph.domain.search.TraverseGraphQuery
import kotlinx.serialization.json.JsonObject

internal data class TraverseGraphArgs(
  val query: String,
  val startIds: List<NodeId>,
  val branches: List<String>,
  val edgeTypes: Set<TraversalEdgeType>,
  val maxDepth: Int,
  val maxNodes: Int,
  val budgetTokens: Int,
  val includeBodies: Boolean,
  val rankBy: TraversalRankBy,
)

internal fun parseTraverseGraphArgs(args: JsonObject): Parsed<TraverseGraphArgs> {
  val parts = collectTraverseGraphParts(args)
  val firstError = parts.firstError
  if (firstError != null) return Parsed.Failure(firstError)
  return Parsed.Success(
    TraverseGraphArgs(
      query = parts.query.orEmpty(),
      startIds = parts.startIds,
      branches = parts.branches,
      edgeTypes = parts.edgeTypes,
      maxDepth = parts.maxDepth ?: DEFAULT_TRAVERSE_MAX_DEPTH,
      maxNodes = parts.maxNodes ?: TraverseGraphQuery.DEFAULT_MAX_NODES,
      budgetTokens = parts.budgetTokens ?: TraverseGraphQuery.DEFAULT_BUDGET_TOKENS,
      includeBodies = parts.includeBodies,
      rankBy = parts.rankBy,
    ),
  )
}

internal fun TraverseGraphArgs.toTraverseGraphQuery(): TraverseGraphQuery = TraverseGraphQuery(
  query = query,
  startIds = startIds,
  branches = branches,
  edgeTypes = edgeTypes,
  maxDepth = maxDepth,
  maxNodes = maxNodes,
  budgetTokens = budgetTokens,
  includeBodies = includeBodies,
  rankBy = rankBy,
)

private data class TraverseGraphParts(
  val query: String?,
  val startIds: List<NodeId>,
  val branches: List<String>,
  val edgeTypes: Set<TraversalEdgeType>,
  val maxDepth: Int?,
  val maxNodes: Int?,
  val budgetTokens: Int?,
  val includeBodies: Boolean,
  val rankBy: TraversalRankBy,
  val firstError: JsonObject?,
)

private fun collectTraverseGraphParts(args: JsonObject): TraverseGraphParts {
  val queryParsed = args.optionalStringArgument(ToolSchemas.KEY_QUERY)
  val startIdsParsed = parseTraverseNodeIds(args)
  val branchesParsed = args.stringArrayArgument(ToolSchemas.KEY_BRANCHES)
  val edgeTypesParsed = parseTraverseEdgeTypes(args)
  val maxDepthParsed = args.boundedNonNegativeIntArgument(
    ToolSchemas.KEY_MAX_DEPTH,
    MAX_TRAVERSE_MAX_DEPTH,
  )
  val maxNodesParsed = args.boundedNonNegativeIntArgument(
    ToolSchemas.KEY_MAX_NODES,
    MAX_TRAVERSE_MAX_NODES,
  )
  val budgetTokensParsed = args.boundedNonNegativeIntArgument(
    ToolSchemas.KEY_BUDGET_TOKENS,
    MAX_TRAVERSE_BUDGET_TOKENS,
  )
  val includeBodiesParsed = args.optionalBooleanArgument(ToolSchemas.KEY_INCLUDE_BODIES)
  val rankByParsed = parseTraverseRankBy(args)
  val error = firstFailureJson(
    queryParsed,
    startIdsParsed,
    branchesParsed,
    edgeTypesParsed,
    maxDepthParsed,
    maxNodesParsed,
    budgetTokensParsed,
    includeBodiesParsed,
    rankByParsed,
  )
  return TraverseGraphParts(
    query = if (queryParsed is Parsed.Success) queryParsed.value else null,
    startIds = if (startIdsParsed is Parsed.Success) startIdsParsed.value else emptyList(),
    branches = if (branchesParsed is Parsed.Success) branchesParsed.value else emptyList(),
    edgeTypes = if (edgeTypesParsed is Parsed.Success) edgeTypesParsed.value else TraversalEdgeType.DEFAULTS,
    maxDepth = if (maxDepthParsed is Parsed.Success) maxDepthParsed.value else null,
    maxNodes = if (maxNodesParsed is Parsed.Success) maxNodesParsed.value else null,
    budgetTokens = if (budgetTokensParsed is Parsed.Success) budgetTokensParsed.value else null,
    includeBodies = if (includeBodiesParsed is Parsed.Success) includeBodiesParsed.value == true else false,
    rankBy = if (rankByParsed is Parsed.Success) rankByParsed.value else TraversalRankBy.Relevance,
    firstError = error,
  )
}

private fun parseTraverseNodeIds(args: JsonObject): Parsed<List<NodeId>> {
  val raw = args.stringArrayArgument(ToolSchemas.KEY_START_IDS)
  if (raw is Parsed.Failure) return Parsed.Failure(raw.json)
  return collectTraverseNodeIds((raw as Parsed.Success).value)
}

private fun collectTraverseNodeIds(values: List<String>): Parsed<List<NodeId>> {
  val parsed = mutableListOf<NodeId>()
  for (value in values) {
    val nodeId = parseNodeId(value)
      ?: return Parsed.Failure(invalidInputJson(ToolSchemas.KEY_START_IDS, REASON_INVALID))
    parsed += nodeId
  }
  return Parsed.Success(parsed)
}

private fun parseTraverseEdgeTypes(args: JsonObject): Parsed<Set<TraversalEdgeType>> {
  val raw = args.stringArrayArgument(ToolSchemas.KEY_EDGE_TYPES)
  if (raw is Parsed.Failure) return Parsed.Failure(raw.json)
  val values = (raw as Parsed.Success).value
  if (values.isEmpty()) return Parsed.Success(TraversalEdgeType.DEFAULTS)
  return collectTraverseEdgeTypes(values)
}

private fun collectTraverseEdgeTypes(values: List<String>): Parsed<Set<TraversalEdgeType>> {
  val parsed = mutableSetOf<TraversalEdgeType>()
  for (value in values) {
    val edgeType = parseTraverseEdgeType(value)
      ?: return Parsed.Failure(invalidInputJson(ToolSchemas.KEY_EDGE_TYPES, REASON_INVALID))
    parsed += edgeType
  }
  return Parsed.Success(parsed)
}

private fun parseTraverseEdgeType(raw: String): TraversalEdgeType? = when (raw.lowercase()) {
  ToolSchemas.TRAVERSAL_EDGE_TYPE_LINK -> TraversalEdgeType.Link
  ToolSchemas.TRAVERSAL_EDGE_TYPE_BACKLINK -> TraversalEdgeType.Backlink
  ToolSchemas.TRAVERSAL_EDGE_TYPE_SUBJECT_EVIDENCE -> TraversalEdgeType.SubjectEvidence
  ToolSchemas.TRAVERSAL_EDGE_TYPE_TIMELINE -> TraversalEdgeType.Timeline
  ToolSchemas.TRAVERSAL_EDGE_TYPE_STATE -> TraversalEdgeType.State
  ToolSchemas.TRAVERSAL_EDGE_TYPE_PATTERN -> TraversalEdgeType.Pattern
  ToolSchemas.TRAVERSAL_EDGE_TYPE_CONTRADICTION -> TraversalEdgeType.Contradiction
  ToolSchemas.TRAVERSAL_EDGE_TYPE_BACKGROUND -> TraversalEdgeType.Background
  else -> null
}

private fun parseTraverseRankBy(args: JsonObject): Parsed<TraversalRankBy> {
  val raw = args.stringArrayArgument(ToolSchemas.KEY_RANK_BY)
  if (raw is Parsed.Failure) return Parsed.Failure(raw.json)
  val values = (raw as Parsed.Success).value
  if (values.isEmpty()) return Parsed.Success(TraversalRankBy.Relevance)
  return collectTraverseRankBy(values)
}

private fun collectTraverseRankBy(values: List<String>): Parsed<TraversalRankBy> {
  var recencyRequested = false
  for (value in values) {
    when (parseTraverseRankByHint(value)) {
      null -> return Parsed.Failure(invalidInputJson(ToolSchemas.KEY_RANK_BY, REASON_INVALID))
      TraversalRankBy.Relevance -> Unit
      TraversalRankBy.Recency -> recencyRequested = true
    }
  }
  return Parsed.Success(if (recencyRequested) TraversalRankBy.Recency else TraversalRankBy.Relevance)
}

private fun parseTraverseRankByHint(raw: String): TraversalRankBy? = when (raw.lowercase()) {
  ToolSchemas.TRAVERSAL_RANK_BY_EXACT_ID_MATCH -> TraversalRankBy.Relevance
  ToolSchemas.TRAVERSAL_RANK_BY_EDGE_WEIGHT -> TraversalRankBy.Relevance
  ToolSchemas.TRAVERSAL_RANK_BY_RECENCY -> TraversalRankBy.Recency
  ToolSchemas.TRAVERSAL_RANK_BY_BRANCH_RELEVANCE -> TraversalRankBy.Relevance
  else -> null
}

private const val DEFAULT_TRAVERSE_MAX_DEPTH: Int = 1
private const val MAX_TRAVERSE_MAX_DEPTH: Int = 4
private const val MAX_TRAVERSE_MAX_NODES: Int = 100
private const val MAX_TRAVERSE_BUDGET_TOKENS: Int = 20_000
