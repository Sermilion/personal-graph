package com.sermilion.personalgraph.data.scaffold

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.common.dispatcher.DispatcherProvider
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import com.sermilion.personalgraph.domain.scaffold.VaultScaffolder
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@AppScope
@Inject
class PersonalGraphVaultScaffolder(
  private val vaultRoot: Path,
  private val dispatcherProvider: DispatcherProvider,
) : VaultScaffolder {

  private val logger = KotlinLogging.logger {}

  override suspend fun scaffold(): WriteOutcome = withContext(dispatcherProvider.io) {
    try {
      Files.createDirectories(vaultRoot)
      for (relative in VaultLayout.SCAFFOLD_DIRECTORIES) {
        Files.createDirectories(vaultRoot.resolve(relative))
      }
      val orientationFile = vaultRoot.resolve(VaultLayout.BRAIAN_FILENAME)
      if (Files.notExists(orientationFile)) {
        Files.writeString(orientationFile, DEFAULT_BRAIAN_ORIENTATION)
      }
      WriteOutcome.Applied
    } catch (e: IOException) {
      logger.error(e) { "scaffold failed for vaultRoot=$vaultRoot" }
      WriteOutcome.Failed(e.reasonString())
    } catch (e: SecurityException) {
      logger.error(e) { "scaffold denied for vaultRoot=$vaultRoot" }
      WriteOutcome.Failed(e.reasonString())
    }
  }

  private fun Throwable.reasonString(): String = "${this::class.simpleName}: ${this.message.orEmpty()}"

  companion object {

    private val DEFAULT_BRAIAN_ORIENTATION: String = buildString {
      appendLine("# Braian")
      appendLine()
      appendLine("Root orientation note. Loaded first by every agent session.")
      appendLine()
      appendLine("This vault stores durable observations as a normalized markdown graph.")
      appendLine("Folder layout:")
      appendLine()
      appendLine("- `state/` — durable facts (preferences, roles, knowledge)")
      appendLine("- `domains/<domain>/events/` — dated work or life records kept in their canonical domain/topic")
      appendLine("- `domains/<domain>/subjects/` — reusable subject hubs that accumulate dated evidence and links")
      appendLine("- `patterns/` — extracted cross-cutting pattern hubs")
      appendLine("- `emotional-states/` — dated emotional incidents (evidence-only)")
      appendLine("- `timeline/YYYY-MM/` — chronological index/backlink stubs, not duplicated content")
      appendLine("- `staging/observations/` — low-confidence captures awaiting promotion")
      appendLine("- `staging/sensitive/` — flagged content awaiting batch disposition")
      appendLine("- `people/` — anonymized people index (read-blocked from MCP by default)")
      appendLine()
      appendLine("# TODO")
      appendLine()
      appendLine("Replace this section with a short orientation about yourself: name, current role,")
      appendLine("preferred domains, working style. Agents read this first to ground every session.")
    }
  }
}
