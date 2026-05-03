package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.search.BranchListMode
import com.sermilion.personalgraph.domain.search.BranchListOutcome
import com.sermilion.personalgraph.domain.search.BranchListQuery
import com.sermilion.personalgraph.domain.search.BranchListTokenAccounting
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal fun ListBranchArgs.toBranchListQuery(): BranchListQuery = BranchListQuery(
  branch = branch,
  mode = mode.toDomainMode(),
  filter = filter,
  limit = limit,
  includeLinks = includeLinks,
  includeBody = includeBody,
)

internal fun ListBranchMode.toDomainMode(): BranchListMode = when (this) {
  ListBranchMode.Full -> BranchListMode.Full
  ListBranchMode.Index -> BranchListMode.Index
}

internal fun formatBranchListOutcome(request: ListBranchArgs, outcome: BranchListOutcome): JsonObject = when (outcome) {
  is BranchListOutcome.Full -> formatBranchListFull(request, outcome)
  is BranchListOutcome.Index -> formatBranchListIndex(request, outcome)
}

private fun formatBranchListFull(request: ListBranchArgs, outcome: BranchListOutcome.Full): JsonObject {
  val nodesJson = buildJsonArray { for (node in outcome.nodes) add(nodeJson(node)) }
  if (request.legacyShape) {
    return buildJsonObject {
      put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_OK))
      put(ToolSchemas.KEY_NODES, nodesJson)
    }
  }
  return fullListResultJson(nodesJson, outcome.estimatedTokens.toAccounting())
}

private fun formatBranchListIndex(request: ListBranchArgs, outcome: BranchListOutcome.Index): JsonObject {
  val entries = outcome.entries.map {
    CompactListEntry(
      id = it.id.value,
      type = it.type,
      domain = it.domain,
      subject = it.subject,
      snippet = it.snippet,
      matchFields = it.matchFields,
      score = it.score,
      links = it.links.map(NodeId::value),
      includeLinks = request.includeLinks,
    )
  }
  return compactListResultJson(entries, outcome.estimatedTokens.toAccounting())
}

private fun BranchListTokenAccounting.toAccounting(): ListBranchTokenAccounting = ListBranchTokenAccounting(
  metadataTokens = metadataTokens,
  bodyTokens = bodyTokens,
  prunedBodyTokens = prunedBodyTokens,
)
