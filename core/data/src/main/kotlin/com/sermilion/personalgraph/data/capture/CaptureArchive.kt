package com.sermilion.personalgraph.data.capture

import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.EmotionalStateNode
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import kotlinx.datetime.Instant

internal object CaptureArchive {
  sealed interface Result {
    data class Ok(val archivedIds: List<NodeId>) : Result
    data class Failed(val reason: String) : Result
  }

  suspend fun archiveExistingBeforeReplace(
    repository: VaultRepository,
    replacement: VaultNode,
    archivedAt: Instant,
  ): Result {
    val existing = if (shouldArchive(replacement.id)) repository.findNode(replacement.id) else null
    return if (existing == null) {
      Result.Ok(emptyList())
    } else {
      archiveExisting(repository, existing, replacement.id, archivedAt)
    }
  }

  private suspend fun archiveExisting(
    repository: VaultRepository,
    existing: VaultNode,
    replacementId: NodeId,
    archivedAt: Instant,
  ): Result {
    val archiveId = archiveIdFor(existing, archivedAt)
    val archiveNode = existing.toArchiveNode(archiveId, replacementId, archivedAt)
    return when (val outcome = repository.writeNode(archiveNode)) {
      WriteOutcome.Applied -> Result.Ok(listOf(archiveId))
      WriteOutcome.NotFound -> Result.Failed("archive_not_found")
      WriteOutcome.Conflict -> Result.Failed("archive_conflict")
      is WriteOutcome.Failed -> Result.Failed("archive_failed: ${outcome.reason}")
    }
  }
}

private fun shouldArchive(id: NodeId): Boolean {
  val value = id.value
  return !value.startsWith("${VaultLayout.BRANCH_OUTDATED_RESOLVED}/") &&
    !value.startsWith("${VaultLayout.BRANCH_STAGING_SENSITIVE}/")
}

private fun archiveIdFor(node: VaultNode, archivedAt: Instant): NodeId {
  val timestamp = archivedAt.toString().slugSegment()
  val bodyHash = Integer.toHexString(node.body.hashCode())
  val archivePath = "${VaultLayout.BRANCH_OUTDATED_RESOLVED}/${node.id.value}/$timestamp-$bodyHash"
  return NodeId(archivePath)
}

private fun VaultNode.toArchiveNode(
  archiveId: NodeId,
  replacementId: NodeId,
  archivedAt: Instant,
): VaultNode = when (this) {
  is StateNode -> copy(
    id = archiveId,
    updatedAt = archivedAt,
    body = archiveBody(originalId = id, replacementId = replacementId, archivedAt = archivedAt, body = body),
    links = mergeLinks(links, replacementId),
  )
  is EpisodeNode -> copy(
    id = archiveId,
    updatedAt = archivedAt,
    body = archiveBody(originalId = id, replacementId = replacementId, archivedAt = archivedAt, body = body),
    links = mergeLinks(links, replacementId),
  )
  is PatternNode -> copy(
    id = archiveId,
    updatedAt = archivedAt,
    body = archiveBody(originalId = id, replacementId = replacementId, archivedAt = archivedAt, body = body),
    links = mergeLinks(links, replacementId),
  )
  is SubjectNode -> copy(
    id = archiveId,
    updatedAt = archivedAt,
    body = archiveBody(originalId = id, replacementId = replacementId, archivedAt = archivedAt, body = body),
    links = mergeLinks(links, replacementId),
  )
  is EmotionalStateNode -> copy(
    id = archiveId,
    updatedAt = archivedAt,
    body = archiveBody(originalId = id, replacementId = replacementId, archivedAt = archivedAt, body = body),
    links = mergeLinks(links, replacementId),
  )
}

private fun archiveBody(
  originalId: NodeId,
  replacementId: NodeId,
  archivedAt: Instant,
  body: String,
): String = buildString {
  append("Archived from `${originalId.value}` on $archivedAt ")
  appendLine("because a newer write replaced that graph path.")
  appendLine("Superseded by `${replacementId.value}`.")
  appendLine()
  appendLine("---")
  appendLine()
  append(body)
}

private fun mergeLinks(existing: List<NodeId>, replacementId: NodeId): List<NodeId> {
  val alreadyLinked = existing.any { it.value == replacementId.value }
  return if (alreadyLinked) existing else existing + replacementId
}

private fun String.slugSegment(): String = lowercase()
  .replace(Regex("[^a-z0-9]+"), "-")
  .trim('-')
  .ifEmpty { "unknown-time" }
