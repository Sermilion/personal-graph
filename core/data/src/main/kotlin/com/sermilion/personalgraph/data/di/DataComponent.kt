package com.sermilion.personalgraph.data.di

import com.sermilion.personalgraph.data.capture.PersonalGraphVaultCaptureService
import com.sermilion.personalgraph.data.repository.PersonalGraphVaultRepository
import com.sermilion.personalgraph.data.scaffold.PersonalGraphVaultScaffolder
import com.sermilion.personalgraph.domain.capture.VaultCaptureService
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.scaffold.VaultScaffolder
import kotlinx.datetime.Clock
import me.tatarka.inject.annotations.Provides

interface DataComponent {
  @Provides
  fun provideVaultRepository(impl: PersonalGraphVaultRepository): VaultRepository = impl

  @Provides
  fun provideVaultScaffolder(impl: PersonalGraphVaultScaffolder): VaultScaffolder = impl

  @Provides
  fun provideVaultCaptureService(impl: PersonalGraphVaultCaptureService): VaultCaptureService = impl

  @Provides
  fun provideClock(): Clock = Clock.System
}
