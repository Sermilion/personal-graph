package com.sermilion.personalgraph.testing

import com.sermilion.personalgraph.common.dispatcher.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider(
  private val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : DispatcherProvider {
  override val io: CoroutineDispatcher = dispatcher
  override val default: CoroutineDispatcher = dispatcher
  override val unconfined: CoroutineDispatcher = dispatcher
}
