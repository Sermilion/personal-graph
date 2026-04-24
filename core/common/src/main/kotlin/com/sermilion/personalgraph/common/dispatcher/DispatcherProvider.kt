package com.sermilion.personalgraph.common.dispatcher

import kotlinx.coroutines.CoroutineDispatcher

interface DispatcherProvider {
  val io: CoroutineDispatcher
  val default: CoroutineDispatcher
  val unconfined: CoroutineDispatcher
}
