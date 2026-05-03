package com.sermilion.personalgraph.testing

import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.repository.GraphIndexInvalidator

object NoOpGraphIndexInvalidator : GraphIndexInvalidator {
  override suspend fun invalidate(id: NodeId) = Unit

  override suspend fun invalidateAll() = Unit
}
