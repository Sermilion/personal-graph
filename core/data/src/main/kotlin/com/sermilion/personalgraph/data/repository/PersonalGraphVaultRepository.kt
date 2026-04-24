package com.sermilion.personalgraph.data.repository

import com.sermilion.personalgraph.common.dispatcher.DispatcherProvider
import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import me.tatarka.inject.annotations.Inject
import java.nio.file.Path

@AppScope
@Inject
class PersonalGraphVaultRepository(
  private val vaultRoot: Path,
  private val dispatcherProvider: DispatcherProvider,
) : VaultRepository {

  private val logger = KotlinLogging.logger {}

  override fun observeNode(id: NodeId): Flow<VaultNode?> {
    logger.debug { "observeNode stub for id=$id" }
    return flowOf(null)
  }

  override fun observeNodesInBranch(branchPath: String): Flow<List<VaultNode>> {
    logger.debug { "observeNodesInBranch stub for branchPath=$branchPath" }
    return flowOf(emptyList())
  }

  override suspend fun findNode(id: NodeId): VaultNode? {
    logger.debug { "findNode stub for id=$id" }
    return null
  }

  override suspend fun listNodesInBranch(branchPath: String): List<VaultNode> {
    logger.debug { "listNodesInBranch stub for branchPath=$branchPath" }
    return emptyList()
  }

  override suspend fun writeNode(node: VaultNode): WriteOutcome {
    logger.debug { "writeNode stub for id=${node.id}" }
    return WriteOutcome.Failed("not implemented")
  }

  override suspend fun moveNode(id: NodeId, newBranchPath: String): WriteOutcome {
    logger.debug { "moveNode stub for id=$id, newBranchPath=$newBranchPath" }
    return WriteOutcome.Failed("not implemented")
  }

  override suspend fun deleteNode(id: NodeId): WriteOutcome {
    logger.debug { "deleteNode stub for id=$id" }
    return WriteOutcome.Failed("not implemented")
  }

  override suspend fun listBacklinks(id: NodeId): List<VaultNode> {
    logger.debug { "listBacklinks stub for id=$id" }
    return emptyList()
  }
}
