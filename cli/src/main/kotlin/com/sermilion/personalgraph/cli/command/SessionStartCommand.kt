package com.sermilion.personalgraph.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import com.sermilion.personalgraph.cli.di.CliComponent
import com.sermilion.personalgraph.cli.di.create
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalReport
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking

class SessionStartCommand : CliktCommand(name = COMMAND_NAME) {
  private val vaultRoot by option("--vault").path(mustExist = true, canBeFile = false).required()
  private val messageParts by argument(name = "message").multiple(required = true)

  private val logger = KotlinLogging.logger {}

  override fun run() {
    val message = messageParts.joinToString(" ")
    val report = runBlocking {
      val component = CliComponent::class.create(vaultRoot)
      component.sessionStartRetrievalService.retrieve(
        SessionStartRetrievalRequest(firstSubstantiveMessage = message),
      )
    }
    logger.info { "session-start retrieval completed for vault=$vaultRoot" }
    echo(formatReport(report))
  }

  private fun formatReport(report: SessionStartRetrievalReport): String = buildString {
    appendLine("Session-start retrieval report")
    appendLine("classification=${report.classification.domain.value}")
    appendLine("matched_terms=${report.classification.matchedTerms.joinToString(",")}")
    appendLine("emotional_context=${report.classification.emotionalContextRequested}")
    appendLine("emotional_terms=${report.classification.emotionalMatchedTerms.joinToString(",")}")
    report.rootDocument?.let { root ->
      appendLine("root=${root.path}")
      appendLine("root_load_order=${root.loadOrder}")
    }
    appendLine("loaded_branches=${report.loadedBranches.size}")
    for (branch in report.loadedBranches) {
      appendLine("branch=${branch.branch}; nodes=${branch.nodeCount}; reason=${branch.reason}")
    }
    appendLine("loaded_nodes=${report.loadedNodes.size}")
    for (node in report.loadedNodes) {
      appendLine("node=${node.id}; order=${node.loadOrder}; reason=${node.reason}")
    }
    appendLine("skipped_branches=${report.skippedBranches.size}")
    for (skip in report.skippedBranches) {
      appendLine("skipped=${skip.branch}; reason=${skip.reason}")
    }
    appendLine("audit_entries=${report.audit.size}")
    for (entry in report.audit) {
      appendLine("audit=${entry.action}; subject=${entry.subject}; reason=${entry.reason}")
    }
  }.trimEnd()

  companion object {
    const val COMMAND_NAME: String = "session-start"
  }
}
