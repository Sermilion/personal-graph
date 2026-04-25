package com.sermilion.personalgraph.domain.repository

import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.VaultNode
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
  /**
   * Hot stream: emits the current node state immediately, then re-emits on every
   * subsequent vault change affecting this id. Completes only on cancellation.
   * Never throws — emits `null` when the node does not exist.
   */
  fun observeNode(id: NodeId): Flow<VaultNode?>

  /**
   * Hot stream: emits the current contents of the branch immediately, then
   * re-emits on every subsequent vault change within the branch. Completes only
   * on cancellation. Never throws — emits an empty list on failure.
   */
  fun observeNodesInBranch(branchPath: String): Flow<List<VaultNode>>

  suspend fun findNode(id: NodeId): VaultNode?

  suspend fun listNodesInBranch(branchPath: String): List<VaultNode>

  suspend fun listStagedObservations(): List<StateNode>

  suspend fun writeNode(node: VaultNode): WriteOutcome

  suspend fun moveNode(id: NodeId, newBranchPath: String): WriteOutcome

  suspend fun deleteNode(id: NodeId): WriteOutcome

  suspend fun listBacklinks(id: NodeId): List<VaultNode>
}
