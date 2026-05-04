package com.sermilion.personalgraph.domain.search

interface TraverseGraphService {
  suspend fun traverse(query: TraverseGraphQuery): TraverseGraphOutcome
}
