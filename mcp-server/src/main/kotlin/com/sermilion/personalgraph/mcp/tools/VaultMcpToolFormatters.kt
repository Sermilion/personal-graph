package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.domain.capture.BacklinkStatus
import com.sermilion.personalgraph.domain.capture.CaptureObservationDecision
import com.sermilion.personalgraph.domain.capture.CaptureObservationResult
import com.sermilion.personalgraph.domain.capture.CaptureResult
import com.sermilion.personalgraph.domain.capture.SubjectHubStatus
import com.sermilion.personalgraph.domain.model.EmotionalStateNode
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalReport
import com.sermilion.personalgraph.domain.search.SearchHit
import com.sermilion.personalgraph.domain.search.SearchOutcome
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal const val EXCERPT_LIMIT: Int = 140
internal const val BACKLINK_FAILED_REASON: String = "primary written; timeline backlink failed"

internal fun statusJson(status: String, extras: Map<String, String>): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_STATUS, JsonPrimitive(status))
  for ((k, v) in extras) put(k, JsonPrimitive(v))
}

internal fun invalidInputJson(field: String, reason: String): JsonObject = statusJson(
  ToolSchemas.STATUS_INVALID_INPUT,
  mapOf(
    ToolSchemas.KEY_FIELD to field,
    ToolSchemas.KEY_REASON to reason,
  ),
)

internal fun permissionDeniedBranch(branch: String, reason: String): JsonObject = statusJson(
  ToolSchemas.STATUS_PERMISSION_DENIED,
  mapOf(
    ToolSchemas.KEY_BRANCH to branch,
    ToolSchemas.KEY_REASON to reason,
  ),
)

internal fun nodeJson(node: VaultNode): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_ID, JsonPrimitive(node.id.value))
  put(ToolSchemas.KEY_BODY, JsonPrimitive(node.body))
  val linksKey = when (node) {
    is EpisodeNode, is EmotionalStateNode -> ToolSchemas.KEY_LINKED
    is StateNode, is PatternNode, is SubjectNode -> ToolSchemas.KEY_LINKS
  }
  put(
    linksKey,
    buildJsonArray { for (link in node.links) add(JsonPrimitive(link.value)) },
  )
  if (node is SubjectNode) {
    put(ToolSchemas.KEY_DOMAIN, JsonPrimitive(node.domain))
    put(ToolSchemas.KEY_SUBJECT, JsonPrimitive(node.subject))
  }
  if (node is StateNode) {
    node.scope?.let { put(ToolSchemas.KEY_SCOPE, JsonPrimitive(it)) }
    if (node.scopes.isNotEmpty()) {
      put(ToolSchemas.KEY_SCOPES, stringArrayJsonOf(node.scopes))
    }
  }
}

internal fun SessionStartRetrievalReport.toJson(): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_OK))
  rootDocument?.let { root ->
    put(
      ToolSchemas.KEY_ROOT,
      buildJsonObject {
        put(ToolSchemas.KEY_PATH, JsonPrimitive(root.path))
        put(ToolSchemas.KEY_LOAD_ORDER, JsonPrimitive(root.loadOrder))
        put(ToolSchemas.KEY_REASON, JsonPrimitive(root.reason))
      },
    )
  }
  put(ToolSchemas.KEY_CLASSIFICATION, classificationJsonOf(classification))
  put(ToolSchemas.KEY_LOADED_CONTEXT, loadedFullBodyContextJsonOf(loadedContext))
  put(ToolSchemas.KEY_AVAILABLE_MAP, compactMapEntriesJsonOf(availableMap))
  put(
    ToolSchemas.KEY_SUGGESTED_READS,
    buildJsonArray {
      for (read in suggestedReads) {
        add(
          buildJsonObject {
            put(ToolSchemas.KEY_ID, JsonPrimitive(read.id))
            put(ToolSchemas.KEY_REASON, JsonPrimitive(read.reason))
            put(ToolSchemas.KEY_PRIORITY, JsonPrimitive(read.priority.value))
          },
        )
      }
    },
  )
  put(
    ToolSchemas.KEY_SUGGESTED_ACTIONS,
    buildJsonArray { for (action in suggestedActions) add(suggestedActionJson(action)) },
  )
  put(ToolSchemas.KEY_ESTIMATED_TOKENS, tokenAccountingJson(estimatedTokens))
  put(ToolSchemas.KEY_SKIPPED_BRANCHES, skippedBranchesJsonOf(skippedBranches))
  put(ToolSchemas.KEY_AUDIT, auditJsonOf(audit))
  put(ToolSchemas.KEY_AUDIT_ENTRIES, auditJsonOf(auditEntries))
}

internal fun searchNodesResultJson(outcome: SearchOutcome): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_OK))
  put(
    ToolSchemas.KEY_NODES,
    buildJsonArray { for (hit in outcome.hits) add(searchHitJson(hit)) },
  )
  put(ToolSchemas.KEY_SEARCH_PLAN, searchPlanJson(outcome))
  put(ToolSchemas.KEY_ESTIMATED_TOKENS, JsonPrimitive(outcome.estimatedTokens))
}

private fun searchHitJson(hit: SearchHit): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_ID, JsonPrimitive(hit.id.value))
  put(ToolSchemas.KEY_TYPE, JsonPrimitive(hit.type))
  hit.domain?.let { put(ToolSchemas.KEY_DOMAIN, JsonPrimitive(it)) }
  hit.subject?.let { put(ToolSchemas.KEY_SUBJECT, JsonPrimitive(it)) }
  put(ToolSchemas.KEY_MATCH_FIELDS, stringArrayJsonOf(hit.matchFields))
  put(ToolSchemas.KEY_SNIPPET, JsonPrimitive(hit.snippet))
  put(ToolSchemas.KEY_SCORE, JsonPrimitive(hit.score))
  if (hit.links.isNotEmpty()) {
    put(ToolSchemas.KEY_LINKS, stringArrayJsonOf(hit.links.map { it.value }))
  }
  hit.body?.let { put(ToolSchemas.KEY_BODY, JsonPrimitive(it)) }
}

private fun searchPlanJson(outcome: SearchOutcome): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_METADATA_INDEX_USED, JsonPrimitive(outcome.plan.metadataIndexUsed))
  put(ToolSchemas.KEY_BODY_FALLBACK_USED, JsonPrimitive(outcome.plan.bodyFallbackUsed))
  put(ToolSchemas.KEY_BRANCHES_SEARCHED, stringArrayJsonOf(outcome.plan.branchesSearched))
}

internal data class CompactListEntry(
  val id: String,
  val type: String,
  val domain: String?,
  val subject: String?,
  val snippet: String,
  val matchFields: List<String>,
  val score: Int,
  val links: List<String>,
  val includeLinks: Boolean,
)

internal fun listBranchCompactEntryJson(entry: CompactListEntry): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_ID, JsonPrimitive(entry.id))
  put(ToolSchemas.KEY_TYPE, JsonPrimitive(entry.type))
  entry.domain?.let { put(ToolSchemas.KEY_DOMAIN, JsonPrimitive(it)) }
  entry.subject?.let { put(ToolSchemas.KEY_SUBJECT, JsonPrimitive(it)) }
  put(ToolSchemas.KEY_SNIPPET, JsonPrimitive(entry.snippet))
  put(ToolSchemas.KEY_MATCH_FIELDS, stringArrayJsonOf(entry.matchFields))
  put(ToolSchemas.KEY_SCORE, JsonPrimitive(entry.score))
  if (entry.includeLinks) {
    put(ToolSchemas.KEY_LINKS, stringArrayJsonOf(entry.links))
  }
}

internal data class ListBranchTokenAccounting(
  val metadataTokens: Int,
  val bodyTokens: Int,
  val prunedBodyTokens: Int,
)

internal fun compactListResultJson(
  entries: List<CompactListEntry>,
  accounting: ListBranchTokenAccounting,
): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_OK))
  put(ToolSchemas.KEY_MODE, JsonPrimitive(ToolSchemas.LIST_MODE_INDEX))
  put(
    ToolSchemas.KEY_ENTRIES,
    buildJsonArray { for (entry in entries) add(listBranchCompactEntryJson(entry)) },
  )
  put(ToolSchemas.KEY_ESTIMATED_TOKENS, tokenAccountingJson(accounting))
}

internal fun fullListResultJson(
  nodesArray: JsonArray,
  accounting: ListBranchTokenAccounting,
): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_OK))
  put(ToolSchemas.KEY_NODES, nodesArray)
  put(ToolSchemas.KEY_MODE, JsonPrimitive(ToolSchemas.LIST_MODE_FULL))
  put(ToolSchemas.KEY_ESTIMATED_TOKENS, tokenAccountingJson(accounting))
}

private fun tokenAccountingJson(accounting: ListBranchTokenAccounting): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_METADATA_TOKENS, JsonPrimitive(accounting.metadataTokens))
  put(ToolSchemas.KEY_BODY_TOKENS, JsonPrimitive(accounting.bodyTokens))
  put(ToolSchemas.KEY_PRUNED_BODY_TOKENS, JsonPrimitive(accounting.prunedBodyTokens))
}

internal fun excerpt(body: String): String {
  val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
  return if (firstLine.length <= EXCERPT_LIMIT) firstLine else firstLine.take(EXCERPT_LIMIT) + "..."
}

internal fun CaptureResult.toJson(): JsonObject = when (this) {
  is CaptureResult.Created -> createdToJson(this)
  is CaptureResult.PermissionDenied -> statusJson(
    ToolSchemas.STATUS_PERMISSION_DENIED,
    mapOf(ToolSchemas.KEY_REASON to reason),
  )
  is CaptureResult.InvalidInput -> {
    val extras = mutableMapOf(
      ToolSchemas.KEY_FIELD to field,
      ToolSchemas.KEY_REASON to reason,
    )
    expected?.let { extras[ToolSchemas.KEY_EXPECTED] = it }
    statusJson(ToolSchemas.STATUS_INVALID_INPUT, extras)
  }
  is CaptureResult.NotFound -> statusJson(
    ToolSchemas.STATUS_NOT_FOUND,
    mapOf(ToolSchemas.KEY_PATH to targetPath),
  )
  is CaptureResult.Failed -> statusJson(
    ToolSchemas.STATUS_FAILED,
    mapOf(ToolSchemas.KEY_REASON to reason),
  )
}

internal fun CaptureObservationResult.toJson(): JsonObject = when (this) {
  is CaptureObservationResult.Decided -> captureObservationDecisionToJson(this)
  is CaptureObservationResult.InvalidInput -> invalidInputJson(field, reason)
}

private fun captureObservationDecisionToJson(result: CaptureObservationResult.Decided): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_OK))
  put(ToolSchemas.KEY_DECISION, JsonPrimitive(result.decision.toWireValue()))
  put(ToolSchemas.KEY_REASON, JsonPrimitive(result.reason))
  val captureJson = result.captureResult?.toJson()
  if (captureJson != null) {
    copyIfPresent(captureJson, ToolSchemas.KEY_PATH)
    copyIfPresent(captureJson, ToolSchemas.KEY_BACKLINK_PATH)
    copyIfPresent(captureJson, ToolSchemas.KEY_BACKLINK_STATUS)
    copyIfPresent(captureJson, ToolSchemas.KEY_SUBJECT_HUB_PATH)
    copyIfPresent(captureJson, ToolSchemas.KEY_SUBJECT_HUB_STATUS)
    copyIfPresent(captureJson, ToolSchemas.KEY_ARCHIVED_PATHS)
    if ((captureJson[ToolSchemas.KEY_STATUS] as? JsonPrimitive)?.content != ToolSchemas.STATUS_OK) {
      put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_FAILED))
      copyIfPresent(captureJson, ToolSchemas.KEY_FIELD)
      copyIfPresent(captureJson, ToolSchemas.KEY_EXPECTED)
      copyIfPresent(captureJson, ToolSchemas.KEY_REASON)
    }
  }
}

private fun JsonObjectBuilder.copyIfPresent(source: JsonObject, key: String) {
  source[key]?.let { put(key, it) }
}

private fun CaptureObservationDecision.toWireValue(): String = when (this) {
  CaptureObservationDecision.Rejected -> ToolSchemas.DECISION_REJECTED
  CaptureObservationDecision.StagedObservation -> ToolSchemas.DECISION_STAGED_OBSERVATION
  CaptureObservationDecision.StagedSensitive -> ToolSchemas.DECISION_STAGED_SENSITIVE
  CaptureObservationDecision.StateWritten -> ToolSchemas.DECISION_STATE_WRITTEN
  CaptureObservationDecision.StateUpdated -> ToolSchemas.DECISION_STATE_UPDATED
  CaptureObservationDecision.EpisodeWritten -> ToolSchemas.DECISION_EPISODE_WRITTEN
  CaptureObservationDecision.EpisodeUpdated -> ToolSchemas.DECISION_EPISODE_UPDATED
}

private fun createdToJson(result: CaptureResult.Created): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_OK))
  put(ToolSchemas.KEY_PATH, JsonPrimitive(result.id.value))
  result.backlinkId?.let { put(ToolSchemas.KEY_BACKLINK_PATH, JsonPrimitive(it.value)) }
  val backlinkStatusValue = when (result.backlinkStatus) {
    BacklinkStatus.Ok -> ToolSchemas.BACKLINK_STATUS_OK
    BacklinkStatus.Failed -> ToolSchemas.BACKLINK_STATUS_FAILED
    BacklinkStatus.Skipped -> ToolSchemas.BACKLINK_STATUS_SKIPPED
  }
  put(ToolSchemas.KEY_BACKLINK_STATUS, JsonPrimitive(backlinkStatusValue))
  result.subjectHubId?.let { put(ToolSchemas.KEY_SUBJECT_HUB_PATH, JsonPrimitive(it.value)) }
  if (result.archivedIds.isNotEmpty()) {
    put(ToolSchemas.KEY_ARCHIVED_PATHS, stringArrayJsonOf(result.archivedIds.map { it.value }))
  }
  val subjectHubStatusValue = when (result.subjectHubStatus) {
    SubjectHubStatus.Created -> ToolSchemas.SUBJECT_HUB_STATUS_CREATED
    SubjectHubStatus.Updated -> ToolSchemas.SUBJECT_HUB_STATUS_UPDATED
    SubjectHubStatus.Failed -> ToolSchemas.SUBJECT_HUB_STATUS_FAILED
    SubjectHubStatus.Skipped -> ToolSchemas.SUBJECT_HUB_STATUS_SKIPPED
  }
  put(ToolSchemas.KEY_SUBJECT_HUB_STATUS, JsonPrimitive(subjectHubStatusValue))
  if (result.backlinkStatus == BacklinkStatus.Failed) {
    put(ToolSchemas.KEY_REASON, JsonPrimitive(BACKLINK_FAILED_REASON))
  }
}
