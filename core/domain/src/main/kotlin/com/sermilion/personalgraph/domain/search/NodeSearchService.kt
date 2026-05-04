package com.sermilion.personalgraph.domain.search

interface NodeSearchService {
  suspend fun search(query: SearchQuery): SearchOutcome
}
