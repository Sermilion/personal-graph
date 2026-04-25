package com.sermilion.personalgraph.mcp

import com.github.ajalt.clikt.core.main
import com.sermilion.personalgraph.mcp.cli.McpServerCli

fun main(args: Array<String>) {
  disableKotlinLoggingStartupBanner()
  McpServerCli().main(args)
}

private fun disableKotlinLoggingStartupBanner() {
  runCatching {
    val configuration = Class.forName(KOTLIN_LOGGING_CONFIGURATION_CLASS)
    val instance = configuration.getField("INSTANCE").get(null)
    configuration.getMethod("setLogStartupMessage", java.lang.Boolean.TYPE).invoke(instance, false)
  }
}

private const val KOTLIN_LOGGING_CONFIGURATION_CLASS = "io.github.oshai.kotlinlogging.KotlinLoggingConfiguration"
