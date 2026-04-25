package com.sermilion.personalgraph.mcp.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import com.sermilion.personalgraph.mcp.runtime.McpServerRuntime

class McpServerCli : CliktCommand(name = COMMAND_NAME) {

  internal val vaultRoot by option("--vault")
    .path(mustExist = true, canBeFile = false)
    .required()

  override fun run() {
    McpServerRuntime.run(vaultRoot)
  }

  companion object {
    const val COMMAND_NAME: String = "personal-graph-mcp-server"
  }
}
