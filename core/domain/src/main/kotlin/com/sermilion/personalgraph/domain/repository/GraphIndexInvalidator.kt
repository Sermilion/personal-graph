package com.sermilion.personalgraph.domain.repository

import com.sermilion.personalgraph.domain.model.NodeId

interface GraphIndexInvalidator {
  suspend fun invalidate(id: NodeId)

  suspend fun invalidateAll()
}
