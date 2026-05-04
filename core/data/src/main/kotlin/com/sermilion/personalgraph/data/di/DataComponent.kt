package com.sermilion.personalgraph.data.di

import com.sermilion.personalgraph.data.capture.PersonalGraphVaultCaptureService
import com.sermilion.personalgraph.data.consolidation.PersonalGraphVaultConsolidationService
import com.sermilion.personalgraph.data.repository.PersonalGraphGraphIndexRepository
import com.sermilion.personalgraph.data.repository.PersonalGraphVaultRepository
import com.sermilion.personalgraph.data.retrieval.PersonalGraphSessionStartRetrievalService
import com.sermilion.personalgraph.data.scaffold.PersonalGraphVaultScaffolder
import com.sermilion.personalgraph.data.search.PersonalGraphBranchListingService
import com.sermilion.personalgraph.data.search.PersonalGraphIndexFirstNodeSearchService
import com.sermilion.personalgraph.domain.capture.VaultCaptureService
import com.sermilion.personalgraph.domain.repository.ConsolidationService
import com.sermilion.personalgraph.domain.repository.GraphIndexInvalidator
import com.sermilion.personalgraph.domain.repository.GraphIndexRepository
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalService
import com.sermilion.personalgraph.domain.scaffold.VaultScaffolder
import com.sermilion.personalgraph.domain.search.BranchListingService
import com.sermilion.personalgraph.domain.search.NodeSearchService
import com.sermilion.personalgraph.domain.tokens.TokenEstimator
import kotlinx.datetime.Clock
import me.tatarka.inject.annotations.Provides

interface DataComponent : DataSearchComponent {
  @Provides
  fun provideVaultRepository(impl: PersonalGraphVaultRepository): VaultRepository = impl

  @Provides
  fun provideVaultScaffolder(impl: PersonalGraphVaultScaffolder): VaultScaffolder = impl

  @Provides
  fun provideVaultCaptureService(impl: PersonalGraphVaultCaptureService): VaultCaptureService = impl

  @Provides
  fun provideConsolidationService(impl: PersonalGraphVaultConsolidationService): ConsolidationService = impl

  @Provides
  fun provideSessionStartRetrievalService(
    impl: PersonalGraphSessionStartRetrievalService,
  ): SessionStartRetrievalService = impl

  @Provides
  fun provideGraphIndexRepository(impl: PersonalGraphGraphIndexRepository): GraphIndexRepository = impl

  @Provides
  fun provideGraphIndexInvalidator(impl: PersonalGraphGraphIndexRepository): GraphIndexInvalidator = impl

  @Provides
  fun provideTokenEstimator(): TokenEstimator = TokenEstimator

  @Provides
  fun provideClock(): Clock = Clock.System
}

interface DataSearchComponent {
  @Provides
  fun provideNodeSearchService(impl: PersonalGraphIndexFirstNodeSearchService): NodeSearchService = impl

  @Provides
  fun provideBranchListingService(impl: PersonalGraphBranchListingService): BranchListingService = impl
}
