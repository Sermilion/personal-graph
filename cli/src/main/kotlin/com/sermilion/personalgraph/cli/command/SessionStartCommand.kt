package com.sermilion.personalgraph.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import com.sermilion.personalgraph.cli.di.CliComponent
import com.sermilion.personalgraph.cli.di.create
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalMode
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalReport
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking

class SessionStartCommand : CliktCommand(name = COMMAND_NAME) {
  private val vaultRoot by option("--vault").path(mustExist = true, canBeFile = false).required()
  private val retrievalModeRaw by option("--retrieval-mode")
  private val messageParts by argument(name = "message").multiple(required = true)

  private val logger = KotlinLogging.logger {}

  override fun run() {
    val message = messageParts.joinToString(" ")
    val retrievalMode = retrievalModeRaw?.let(::parseRetrievalMode) ?: SessionStartRetrievalMode.MapFirst
    val report = runBlocking {
      val component = CliComponent::class.create(vaultRoot)
      component.sessionStartRetrievalService.retrieve(
        SessionStartRetrievalRequest(
          firstSubstantiveMessage = message,
          retrievalMode = retrievalMode,
        ),
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
    appendLine("loaded_full_body_context=${report.loadedFullBodyContext.size}")
    appendLine("compact_map_entries=${report.compactMapEntries.size}")
    for (entry in report.compactMapEntries) {
      appendLine(
        "map=${entry.id}; kind=${entry.kind.value}; domain=${entry.domain.orEmpty()}; " +
          "category=${entry.category.orEmpty()}; scope=${entry.scope.orEmpty()}; " +
          "scopes=${entry.scopes.joinToString(",")}; date=${entry.date.orEmpty()}; " +
          "updated=${entry.updatedAt.orEmpty()}; summary=${entry.summary.orEmpty()}; " +
          "excerpt=${entry.excerpt.orEmpty()}; aliases=${entry.aliases.joinToString(",")}; " +
          "terms=${entry.terms.joinToString(",")}; links=${entry.links.joinToString(",")}; " +
          "pattern_links=${entry.patternLinks.joinToString(",")}; " +
          "backlinks=${entry.backlinkCount?.toString().orEmpty()}; reason=${entry.reason}",
      )
    }
    appendLine("suggested_reads=${report.suggestedReads.size}")
    for (read in report.suggestedReads) {
      appendLine(
        "suggested=${read.id}; kind=${read.kind.value}; domain=${read.domain.orEmpty()}; " +
          "category=${read.category.orEmpty()}; scope=${read.scope.orEmpty()}; " +
          "scopes=${read.scopes.joinToString(",")}; date=${read.date.orEmpty()}; " +
          "updated=${read.updatedAt.orEmpty()}; summary=${read.summary.orEmpty()}; " +
          "excerpt=${read.excerpt.orEmpty()}; aliases=${read.aliases.joinToString(",")}; " +
          "terms=${read.terms.joinToString(",")}; links=${read.links.joinToString(",")}; " +
          "pattern_links=${read.patternLinks.joinToString(",")}; " +
          "backlinks=${read.backlinkCount?.toString().orEmpty()}; " +
          "reason=${read.reason}",
      )
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

  private fun parseRetrievalMode(raw: String): SessionStartRetrievalMode = when (raw) {
    SessionStartRetrievalMode.MapFirst.value -> SessionStartRetrievalMode.MapFirst
    SessionStartRetrievalMode.FullLoading.value -> SessionStartRetrievalMode.FullLoading
    else -> throw UsageError("invalid --retrieval-mode: $raw")
  }

  companion object {
    const val COMMAND_NAME: String = "session-start"
  }
}
