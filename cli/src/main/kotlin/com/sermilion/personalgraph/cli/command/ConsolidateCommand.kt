package com.sermilion.personalgraph.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import io.github.oshai.kotlinlogging.KotlinLogging

class ConsolidateCommand : CliktCommand(name = "consolidate") {
  private val vaultRoot by option("--vault").path(mustExist = true, canBeFile = false).required()

  private val logger = KotlinLogging.logger {}

  override fun run() {
    logger.info { "consolidate stub for vault=$vaultRoot" }
  }
}
