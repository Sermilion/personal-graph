package com.sermilion.personalgraph.common.di

import com.sermilion.personalgraph.common.dispatcher.DispatcherProvider
import com.sermilion.personalgraph.common.dispatcher.PersonalGraphDispatcherProvider
import me.tatarka.inject.annotations.Provides

interface CoreComponent {
  @Provides
  fun provideDispatcherProvider(impl: PersonalGraphDispatcherProvider): DispatcherProvider = impl
}
