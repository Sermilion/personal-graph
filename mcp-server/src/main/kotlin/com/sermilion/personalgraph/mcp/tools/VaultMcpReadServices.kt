package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalService
import com.sermilion.personalgraph.domain.search.BranchListingService
import com.sermilion.personalgraph.domain.search.NodeSearchService
import com.sermilion.personalgraph.domain.search.TraverseGraphService
import me.tatarka.inject.annotations.Inject

@AppScope
@Inject
class VaultMcpReadServices(
  val sessionStartRetrievalService: SessionStartRetrievalService,
  val nodeSearchService: NodeSearchService,
  val branchListingService: BranchListingService,
  val traverseGraphService: TraverseGraphService,
)
