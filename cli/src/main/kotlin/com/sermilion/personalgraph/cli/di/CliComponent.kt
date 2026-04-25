package com.sermilion.personalgraph.cli.di

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.common.di.CoreComponent
import com.sermilion.personalgraph.data.di.DataComponent
import com.sermilion.personalgraph.domain.repository.ConsolidationService
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalService
import com.sermilion.personalgraph.domain.scaffold.VaultScaffolder
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import java.nio.file.Path

@AppScope
@Component
abstract class CliComponent(@get:Provides val vaultRoot: Path) :
  CoreComponent,
  DataComponent {
  abstract val vaultScaffolder: VaultScaffolder
  abstract val consolidationService: ConsolidationService
  abstract val sessionStartRetrievalService: SessionStartRetrievalService
}
