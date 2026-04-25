package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.domain.capture.BacklinkStatus
import com.sermilion.personalgraph.domain.capture.CaptureResult
import com.sermilion.personalgraph.domain.model.EmotionalStateNode
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.retrieval.RetrievalAuditEntry
import com.sermilion.personalgraph.domain.retrieval.RetrievalClassification
import com.sermilion.personalgraph.domain.retrieval.RetrievedBranch
import com.sermilion.personalgraph.domain.retrieval.RetrievedNode
import com.sermilion.personalgraph.domain.retrieval.RetrievedRootDocument
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalReport
import com.sermilion.personalgraph.domain.retrieval.SkippedBranch
import kotlinx.serialization.json.JsonObject
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

internal fun nodeJson(node: VaultNode): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_ID, JsonPrimitive(node.id.value))
  put(ToolSchemas.KEY_BODY, JsonPrimitive(node.body))
  val linksKey = when (node) {
    is EpisodeNode, is EmotionalStateNode -> ToolSchemas.KEY_LINKED
    is StateNode, is PatternNode -> ToolSchemas.KEY_LINKS
  }
  put(
    linksKey,
    buildJsonArray { for (link in node.links) add(JsonPrimitive(link.value)) },
  )
}

internal fun SessionStartRetrievalReport.toJson(): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_OK))
  rootDocument?.let { put(ToolSchemas.KEY_ROOT, rootDocumentJson(it)) }
  put(ToolSchemas.KEY_CLASSIFICATION, classificationJson(classification))
  put(ToolSchemas.KEY_LOADED_BRANCHES, loadedBranchesJson(loadedBranches))
  put(ToolSchemas.KEY_NODES, retrievedNodesJson(loadedNodes))
  put(ToolSchemas.KEY_SKIPPED_BRANCHES, skippedBranchesJson(skippedBranches))
  put(ToolSchemas.KEY_AUDIT, auditJson(audit))
}

private fun rootDocumentJson(root: RetrievedRootDocument): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_PATH, JsonPrimitive(root.path))
  put(ToolSchemas.KEY_BODY, JsonPrimitive(root.body))
  put(ToolSchemas.KEY_LOAD_ORDER, JsonPrimitive(root.loadOrder))
  put(ToolSchemas.KEY_REASON, JsonPrimitive(root.reason))
}

private fun classificationJson(classification: RetrievalClassification): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_DOMAIN, JsonPrimitive(classification.domain.value))
  put(ToolSchemas.KEY_MATCHED_TERMS, stringArrayJson(classification.matchedTerms))
  put(ToolSchemas.KEY_EMOTIONAL_CONTEXT, JsonPrimitive(classification.emotionalContextRequested))
  put(ToolSchemas.KEY_EMOTIONAL_TERMS, stringArrayJson(classification.emotionalMatchedTerms))
}

private fun loadedBranchesJson(branches: List<RetrievedBranch>) = buildJsonArray {
  for (branch in branches) {
    add(
      buildJsonObject {
        put(ToolSchemas.KEY_BRANCH, JsonPrimitive(branch.branch))
        put(ToolSchemas.KEY_REASON, JsonPrimitive(branch.reason))
        put(ToolSchemas.KEY_NODE_COUNT, JsonPrimitive(branch.nodeCount))
      },
    )
  }
}

private fun retrievedNodesJson(nodes: List<RetrievedNode>) = buildJsonArray {
  for (node in nodes) {
    add(
      buildJsonObject {
        put(ToolSchemas.KEY_ID, JsonPrimitive(node.id))
        put(ToolSchemas.KEY_BODY, JsonPrimitive(node.body))
        put(ToolSchemas.KEY_LINKS, stringArrayJson(node.links))
        put(ToolSchemas.KEY_PATTERN_LINKS, stringArrayJson(node.patternLinks))
        put(ToolSchemas.KEY_LOAD_ORDER, JsonPrimitive(node.loadOrder))
        put(ToolSchemas.KEY_REASON, JsonPrimitive(node.reason))
      },
    )
  }
}

private fun skippedBranchesJson(branches: List<SkippedBranch>) = buildJsonArray {
  for (branch in branches) {
    add(
      buildJsonObject {
        put(ToolSchemas.KEY_BRANCH, JsonPrimitive(branch.branch))
        put(ToolSchemas.KEY_REASON, JsonPrimitive(branch.reason))
      },
    )
  }
}

private fun auditJson(audit: List<RetrievalAuditEntry>) = buildJsonArray {
  for (entry in audit) {
    add(
      buildJsonObject {
        put(ToolSchemas.KEY_ACTION, JsonPrimitive(entry.action))
        put(ToolSchemas.KEY_SUBJECT, JsonPrimitive(entry.subject))
        put(ToolSchemas.KEY_REASON, JsonPrimitive(entry.reason))
      },
    )
  }
}

private fun stringArrayJson(values: List<String>) = buildJsonArray {
  for (value in values) add(JsonPrimitive(value))
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
  if (result.backlinkStatus == BacklinkStatus.Failed) {
    put(ToolSchemas.KEY_REASON, JsonPrimitive(BACKLINK_FAILED_REASON))
  }
}
