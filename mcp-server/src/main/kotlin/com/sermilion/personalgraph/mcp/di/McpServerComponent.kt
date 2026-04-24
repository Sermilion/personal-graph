package com.sermilion.personalgraph.mcp.di

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.common.di.CoreComponent
import com.sermilion.personalgraph.common.dispatcher.DispatcherProvider
import com.sermilion.personalgraph.data.di.DataComponent
import com.sermilion.personalgraph.domain.repository.VaultRepository
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import java.nio.file.Path

@AppScope
@Component
abstract class McpServerComponent(
  @get:Provides val vaultRoot: Path,
) : CoreComponent, DataComponent {
  abstract val dispatcherProvider: DispatcherProvider
  abstract val vaultRepository: VaultRepository
}
