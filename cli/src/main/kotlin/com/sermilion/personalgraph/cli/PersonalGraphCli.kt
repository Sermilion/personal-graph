package com.sermilion.personalgraph.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.sermilion.personalgraph.cli.command.ConsolidateCommand
import com.sermilion.personalgraph.cli.command.InitCommand
import com.sermilion.personalgraph.cli.command.SessionStartCommand

class PersonalGraphCli : CliktCommand(name = "personal-graph") {
  override fun run() = Unit
}

fun personalGraphCli(): CliktCommand = PersonalGraphCli()
  .subcommands(ConsolidateCommand(), InitCommand(), SessionStartCommand())
