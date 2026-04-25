package com.sermilion.personalgraph.cli

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.sermilion.personalgraph.cli.command.ConsolidateCommand
import com.sermilion.personalgraph.cli.command.InitCommand

fun main(args: Array<String>) {
  PersonalGraphCli()
    .subcommands(ConsolidateCommand(), InitCommand())
    .main(args)
}
