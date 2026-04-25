package com.sermilion.personalgraph.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import com.sermilion.personalgraph.cli.di.CliComponent
import com.sermilion.personalgraph.cli.di.create
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking

class InitCommand : CliktCommand(name = COMMAND_NAME) {

  private val vaultRoot by option("--vault").path(canBeFile = false).required()

  private val logger = KotlinLogging.logger {}

  override fun run() {
    val outcome = runBlocking {
      val component = CliComponent::class.create(vaultRoot)
      component.vaultScaffolder.scaffold()
    }
    when (outcome) {
      is WriteOutcome.Applied -> logger.info { "$LOG_TAG_APPLIED $vaultRoot" }
      is WriteOutcome.Failed -> {
        logger.error { "$LOG_TAG_FAILED $vaultRoot: ${outcome.reason}" }
        throw PrintMessage(outcome.reason, statusCode = 1, printError = true)
      }
      is WriteOutcome.NotFound,
      is WriteOutcome.Conflict,
      -> {
        logger.error { "$LOG_TAG_FAILED $vaultRoot: $UNEXPECTED_OUTCOME_REASON" }
        throw PrintMessage(UNEXPECTED_OUTCOME_REASON, statusCode = 1, printError = true)
      }
    }
  }

  companion object {
    const val COMMAND_NAME: String = "init"
    private const val LOG_TAG_APPLIED: String = "init: scaffolded vault at"
    private const val LOG_TAG_FAILED: String = "init: scaffold failed for"
    private const val UNEXPECTED_OUTCOME_REASON: String = "Unexpected scaffold outcome"
  }
}
