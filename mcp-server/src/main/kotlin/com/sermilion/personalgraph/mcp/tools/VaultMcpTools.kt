package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.domain.capture.VaultCaptureService
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.layout.VaultPolicy
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.repository.VaultRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import me.tatarka.inject.annotations.Inject
import java.nio.file.Files
import java.nio.file.Path

@AppScope
@Inject
class VaultMcpTools(
  private val repository: VaultRepository,
  private val pathResolver: VaultPathResolver,
  private val vaultRoot: Path,
  private val captureService: VaultCaptureService,
) {

  suspend fun writeState(args: JsonObject): JsonObject = when (val parsed = parseWriteStateArgs(args)) {
    is Parsed.Failure -> parsed.json
    is Parsed.Success -> captureService.writeStateObservation(parsed.value).toJson()
  }

  suspend fun writeEpisode(args: JsonObject): JsonObject = when (val parsed = parseWriteEpisodeArgs(args)) {
    is Parsed.Failure -> parsed.json
    is Parsed.Success -> captureService.writeEpisode(parsed.value).toJson()
  }

  suspend fun writeToStaging(args: JsonObject): JsonObject = when (val parsed = parseWriteToStagingArgs(args)) {
    is Parsed.Failure -> parsed.json
    is Parsed.Success -> captureService.writeToStaging(parsed.value).toJson()
  }

  suspend fun flagSensitive(args: JsonObject): JsonObject = when (val parsed = parseFlagSensitiveArgs(args)) {
    is Parsed.Failure -> parsed.json
    is Parsed.Success -> captureService.flagSensitive(parsed.value).toJson()
  }

  suspend fun listPendingSensitive(args: JsonObject = JsonObject(emptyMap())): JsonObject {
    val includeExcerpts = args.booleanOrNull(ToolSchemas.KEY_INCLUDE_EXCERPTS) == true
    if (includeExcerpts && !consentMarkerPresent()) {
      return statusJson(
        ToolSchemas.STATUS_PERMISSION_DENIED,
        mapOf(ToolSchemas.KEY_REASON to CONSENT_MARKER_REQUIRED_REASON),
      )
    }
    val nodes = repository.listNodesInBranch(VaultLayout.BRANCH_STAGING_SENSITIVE)
    val items = buildJsonArray {
      for (node in nodes) {
        add(
          buildJsonObject {
            put(ToolSchemas.KEY_ID, JsonPrimitive(node.id.value))
            if (includeExcerpts) {
              put(ToolSchemas.KEY_EXCERPT, JsonPrimitive(excerpt(node.body)))
            }
          },
        )
      }
    }
    return buildJsonObject {
      put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_OK))
      put(ToolSchemas.KEY_NODES, items)
    }
  }

  suspend fun readNode(args: JsonObject): JsonObject = when (val gate = readNodeGate(args)) {
    is ReadNodeGate.Reject -> gate.json
    is ReadNodeGate.Allow -> readGatedNode(gate)
  }

  suspend fun listBranch(args: JsonObject): JsonObject {
    val branch = args.stringOrNull(ToolSchemas.KEY_BRANCH)
      ?: return invalidInputJson(ToolSchemas.KEY_BRANCH, REASON_MISSING)
    val gate = listBranchGate(branch)
    if (gate != null) return gate
    val nodes = repository.listNodesInBranch(branch)
    val items = buildJsonArray { for (node in nodes) add(nodeJson(node)) }
    return buildJsonObject {
      put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_OK))
      put(ToolSchemas.KEY_NODES, items)
    }
  }

  private suspend fun readGatedNode(gate: ReadNodeGate.Allow): JsonObject {
    val node = repository.findNode(gate.nodeId)
      ?: return statusJson(ToolSchemas.STATUS_NOT_FOUND, mapOf(ToolSchemas.KEY_PATH to gate.rawId))
    return buildJsonObject {
      put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_OK))
      put(ToolSchemas.KEY_NODE, nodeJson(node))
    }
  }

  private fun readNodeGate(args: JsonObject): ReadNodeGate {
    val rawId = args.stringOrNull(ToolSchemas.KEY_ID)
      ?: return ReadNodeGate.Reject(invalidInputJson(ToolSchemas.KEY_ID, REASON_MISSING))
    val precheck = readNodePrecheck(rawId)
    return precheck ?: resolveReadNodeGate(rawId)
  }

  private fun readNodePrecheck(rawId: String): ReadNodeGate? = when {
    VaultPolicy.isReadBlocked(rawId) -> ReadNodeGate.Reject(permissionDeniedReadBlocked(rawId))
    parseNodeId(rawId) == null -> ReadNodeGate.Reject(invalidInputJson(ToolSchemas.KEY_ID, REASON_INVALID))
    else -> null
  }

  private fun resolveReadNodeGate(rawId: String): ReadNodeGate {
    val nodeId = parseNodeId(rawId)
      ?: return ReadNodeGate.Reject(invalidInputJson(ToolSchemas.KEY_ID, REASON_INVALID))
    val candidate = pathResolver.resolve(vaultRoot, nodeId)
    return if (!pathResolver.assertWithinVault(vaultRoot, candidate)) {
      ReadNodeGate.Reject(permissionDeniedOutside(rawId))
    } else {
      ReadNodeGate.Allow(rawId, nodeId)
    }
  }

  private fun listBranchGate(branch: String): JsonObject? {
    if (VaultPolicy.isReadBlocked(branch)) {
      return statusJson(
        ToolSchemas.STATUS_PERMISSION_DENIED,
        mapOf(
          ToolSchemas.KEY_BRANCH to branch,
          ToolSchemas.KEY_REASON to PERMISSION_DENIED_PEOPLE,
        ),
      )
    }
    val candidate = vaultRoot.resolve(branch)
    if (!pathResolver.assertWithinVault(vaultRoot, candidate)) {
      return statusJson(
        ToolSchemas.STATUS_PERMISSION_DENIED,
        mapOf(
          ToolSchemas.KEY_BRANCH to branch,
          ToolSchemas.KEY_REASON to PERMISSION_DENIED_OUTSIDE,
        ),
      )
    }
    return null
  }

  private fun consentMarkerPresent(): Boolean {
    val marker = vaultRoot.resolve(VaultLayout.BRANCH_STAGING_SENSITIVE).resolve(CONSENT_MARKER_FILENAME)
    return Files.exists(marker)
  }

  private sealed interface ReadNodeGate {
    data class Allow(val rawId: String, val nodeId: NodeId) : ReadNodeGate
    data class Reject(val json: JsonObject) : ReadNodeGate
  }

  companion object {
    private const val CONSENT_MARKER_FILENAME: String = ".consent"
    private const val CONSENT_MARKER_REQUIRED_REASON: String =
      "include_excerpts requires staging/sensitive/.consent marker file to exist"
  }
}
