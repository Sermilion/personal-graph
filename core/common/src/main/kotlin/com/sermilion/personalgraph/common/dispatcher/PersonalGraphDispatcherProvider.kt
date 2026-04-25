package com.sermilion.personalgraph.common.dispatcher

import com.sermilion.personalgraph.common.di.AppScope
import kotlinx.coroutines.Dispatchers
import me.tatarka.inject.annotations.Inject

@AppScope
@Inject
class PersonalGraphDispatcherProvider : DispatcherProvider {
  override val io = Dispatchers.IO
  override val default = Dispatchers.Default
  override val unconfined = Dispatchers.Unconfined
}
