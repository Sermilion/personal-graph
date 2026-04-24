package com.sermilion.personalgraph.data.di

import com.sermilion.personalgraph.data.repository.PersonalGraphVaultRepository
import com.sermilion.personalgraph.domain.repository.VaultRepository
import me.tatarka.inject.annotations.Provides

interface DataComponent {
  @Provides
  fun provideVaultRepository(impl: PersonalGraphVaultRepository): VaultRepository = impl
}
