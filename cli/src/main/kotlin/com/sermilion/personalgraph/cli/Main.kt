package com.sermilion.personalgraph.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.sermilion.personalgraph.cli.command.ConsolidateCommand

class PersonalGraphCli : CliktCommand(name = "personal-graph") {
  override fun run() = Unit
}

fun main(args: Array<String>) {
  PersonalGraphCli()
    .subcommands(ConsolidateCommand())
    .main(args)
}
