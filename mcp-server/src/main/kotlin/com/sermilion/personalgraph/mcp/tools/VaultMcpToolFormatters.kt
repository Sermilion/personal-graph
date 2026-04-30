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
import com.sermilion.personalgraph.domain.retrieval.CompactMapEntry
import com.sermilion.personalgraph.domain.retrieval.LoadedFullBodyContext
import com.sermilion.personalgraph.domain.retrieval.RetrievalAuditEntry
import com.sermilion.personalgraph.domain.retrieval.RetrievalClassification
import com.sermilion.personalgraph.domain.retrieval.RetrievedBranch
import com.sermilion.personalgraph.domain.retrieval.RetrievedNode
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalReport
import com.sermilion.personalgraph.domain.retrieval.SkippedBranch
import com.sermilion.personalgraph.domain.retrieval.SuggestedRead
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
      put(ToolSchemas.KEY_SCOPES, stringArrayJson(node.scopes))
    }
  }
}

internal fun SessionStartRetrievalReport.toJson(): JsonObject = SessionStartReportJsonFormatter.toJson(this)

private object SessionStartReportJsonFormatter {

  fun toJson(report: SessionStartRetrievalReport): JsonObject = with(report) {
    buildJsonObject {
      put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_OK))
      rootDocument?.let { root ->
        put(
          ToolSchemas.KEY_ROOT,
          buildJsonObject {
            put(ToolSchemas.KEY_PATH, JsonPrimitive(root.path))
            put(ToolSchemas.KEY_BODY, JsonPrimitive(root.body))
            put(ToolSchemas.KEY_LOAD_ORDER, JsonPrimitive(root.loadOrder))
            put(ToolSchemas.KEY_REASON, JsonPrimitive(root.reason))
          },
        )
      }
      put(ToolSchemas.KEY_CLASSIFICATION, classificationJson(classification))
      put(ToolSchemas.KEY_LOADED_BRANCHES, loadedBranchesJson(loadedBranches))
      put(ToolSchemas.KEY_NODES, retrievedNodesJson(loadedNodes))
      put(ToolSchemas.KEY_LOADED_FULL_BODY_CONTEXT, loadedFullBodyContextJson(loadedFullBodyContext))
      put(ToolSchemas.KEY_COMPACT_MAP_ENTRIES, compactMapEntriesJson(compactMapEntries))
      put(ToolSchemas.KEY_SUGGESTED_READS, suggestedReadsJson(suggestedReads))
      put(ToolSchemas.KEY_SKIPPED_BRANCHES, skippedBranchesJson(skippedBranches))
      put(ToolSchemas.KEY_AUDIT, auditJson(audit))
      put(ToolSchemas.KEY_AUDIT_ENTRIES, auditJson(auditEntries))
    }
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

  private fun loadedFullBodyContextJson(context: List<LoadedFullBodyContext>) = buildJsonArray {
    for (entry in context) {
      add(
        buildJsonObject {
          put(ToolSchemas.KEY_ID, JsonPrimitive(entry.id))
          put(ToolSchemas.KEY_BODY, JsonPrimitive(entry.body))
          put(ToolSchemas.KEY_SOURCE, JsonPrimitive(entry.source.value))
          put(ToolSchemas.KEY_LOAD_ORDER, JsonPrimitive(entry.loadOrder))
          put(ToolSchemas.KEY_REASON, JsonPrimitive(entry.reason))
        },
      )
    }
  }

  private fun compactMapEntriesJson(entries: List<CompactMapEntry>) = buildJsonArray {
    for (entry in entries) {
      add(
        buildJsonObject {
          put(ToolSchemas.KEY_ID, JsonPrimitive(entry.id))
          put(ToolSchemas.KEY_KIND, JsonPrimitive(entry.kind.value))
          put(ToolSchemas.KEY_REASON, JsonPrimitive(entry.reason))
          putOptional(ToolSchemas.KEY_NODE_COUNT, entry.nodeCount)
          putOptional(ToolSchemas.KEY_DOMAIN, entry.domain)
          putOptional(ToolSchemas.KEY_CATEGORY, entry.category)
          putOptional(ToolSchemas.KEY_SCOPE, entry.scope)
          putIfNotEmpty(ToolSchemas.KEY_SCOPES, entry.scopes)
          putOptional(ToolSchemas.KEY_CREATED_AT, entry.createdAt)
          putOptional(ToolSchemas.KEY_UPDATED_AT, entry.updatedAt)
          putOptional(ToolSchemas.KEY_DATE, entry.date)
          putOptional(ToolSchemas.KEY_SUMMARY, entry.summary)
          putOptional(ToolSchemas.KEY_EXCERPT, entry.excerpt)
          putIfNotEmpty(ToolSchemas.KEY_ALIASES, entry.aliases)
          putIfNotEmpty(ToolSchemas.KEY_TERMS, entry.terms)
          putIfNotEmpty(ToolSchemas.KEY_LINKS, entry.links)
          putIfNotEmpty(ToolSchemas.KEY_PATTERN_LINKS, entry.patternLinks)
          putOptional(ToolSchemas.KEY_BACKLINK_COUNT, entry.backlinkCount)
        },
      )
    }
  }

  private fun suggestedReadsJson(suggestedReads: List<SuggestedRead>) = buildJsonArray {
    for (read in suggestedReads) {
      add(
        buildJsonObject {
          put(ToolSchemas.KEY_ID, JsonPrimitive(read.id))
          put(ToolSchemas.KEY_REASON, JsonPrimitive(read.reason))
          put(ToolSchemas.KEY_KIND, JsonPrimitive(read.kind.value))
          putOptional(ToolSchemas.KEY_DOMAIN, read.domain)
          putOptional(ToolSchemas.KEY_CATEGORY, read.category)
          putOptional(ToolSchemas.KEY_SCOPE, read.scope)
          putIfNotEmpty(ToolSchemas.KEY_SCOPES, read.scopes)
          putOptional(ToolSchemas.KEY_CREATED_AT, read.createdAt)
          putOptional(ToolSchemas.KEY_UPDATED_AT, read.updatedAt)
          putOptional(ToolSchemas.KEY_DATE, read.date)
          putOptional(ToolSchemas.KEY_SUMMARY, read.summary)
          putOptional(ToolSchemas.KEY_EXCERPT, read.excerpt)
          putIfNotEmpty(ToolSchemas.KEY_ALIASES, read.aliases)
          putIfNotEmpty(ToolSchemas.KEY_TERMS, read.terms)
          putIfNotEmpty(ToolSchemas.KEY_LINKS, read.links)
          putIfNotEmpty(ToolSchemas.KEY_PATTERN_LINKS, read.patternLinks)
          putOptional(ToolSchemas.KEY_BACKLINK_COUNT, read.backlinkCount)
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
}

private fun stringArrayJson(values: List<String>) = buildJsonArray {
  for (value in values) add(JsonPrimitive(value))
}

private fun JsonObjectBuilder.putOptional(key: String, value: String?) {
  if (!value.isNullOrBlank()) put(key, JsonPrimitive(value))
}

private fun JsonObjectBuilder.putOptional(key: String, value: Int?) {
  if (value != null) put(key, JsonPrimitive(value))
}

private fun JsonObjectBuilder.putIfNotEmpty(key: String, values: List<String>) {
  if (values.isNotEmpty()) put(key, stringArrayJson(values))
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
