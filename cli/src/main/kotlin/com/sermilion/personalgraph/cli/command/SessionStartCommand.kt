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
import com.sermilion.personalgraph.domain.retrieval.SuggestedActionValue
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
    appendLine()
    appendLoadedContext(report)
    appendLine()
    appendAvailableMap(report)
    appendLine()
    appendSuggestedReads(report)
    appendLine()
    appendSuggestedActions(report)
    appendLine()
    appendEstimatedTokens(report)
    appendLine()
    appendSkippedBranches(report)
    appendLine()
    appendAudit(report)
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

private fun StringBuilder.appendLoadedContext(report: SessionStartRetrievalReport) {
  appendLine("Loaded context (${report.loadedContext.size})")
  for (entry in report.loadedContext) {
    appendLine(
      "context=${entry.id}; source=${entry.source.value}; words=${entry.body.wordCount()}; reason=${entry.reason}",
    )
    appendLine("context_body_begin=${entry.id}")
    appendLine(entry.body.trimEnd())
    appendLine("context_body_end=${entry.id}")
  }
}

private fun StringBuilder.appendAvailableMap(report: SessionStartRetrievalReport) {
  appendLine("Available map (${report.availableMap.size})")
  for (entry in report.availableMap) {
    val details = buildList {
      add("kind=${entry.kind.value}")
      entry.type?.let { add("type=$it") }
      entry.category?.let { add("category=$it") }
      entry.domain?.let { add("domain=$it") }
      entry.scope?.let { add("scope=$it") }
      if (entry.scopes.isNotEmpty()) add("scopes=${entry.scopes.joinToString(",")}")
      entry.nodeCount?.let { add("nodes=$it") }
      entry.summary?.let { add("summary=$it") }
    }.joinToString("; ")
    appendLine("map=${entry.id}; $details; reason=${entry.reason}")
  }
}

private fun StringBuilder.appendSuggestedReads(report: SessionStartRetrievalReport) {
  appendLine("Suggested reads (${report.suggestedReads.size})")
  for (read in report.suggestedReads) {
    appendLine("read=${read.id}; priority=${read.priority.value}; reason=${read.reason}")
  }
}

private fun StringBuilder.appendSuggestedActions(report: SessionStartRetrievalReport) {
  appendLine("Suggested actions (${report.suggestedActions.size})")
  for (action in report.suggestedActions) {
    appendLine("action=${action.tool}; priority=${action.priority.value}; reason=${action.reason}")
    for (arg in action.args) {
      appendLine("action_arg=${action.tool}.${arg.key}=${renderActionArg(arg.value)}")
    }
  }
}

private fun StringBuilder.appendEstimatedTokens(report: SessionStartRetrievalReport) {
  appendLine(
    "Estimated tokens total=${report.estimatedTokens.responseTotal}; " +
      "metadata=${report.estimatedTokens.metadataTokens}; " +
      "body=${report.estimatedTokens.bodyTokens}; " +
      "pruned_body=${report.estimatedTokens.prunedBodyTokens}",
  )
}

private fun StringBuilder.appendSkippedBranches(report: SessionStartRetrievalReport) {
  appendLine("Skipped branches (${report.skippedBranches.size})")
  for (skip in report.skippedBranches) {
    appendLine("skipped=${skip.branch}; reason=${skip.reason}")
  }
}

private fun StringBuilder.appendAudit(report: SessionStartRetrievalReport) {
  appendLine("Audit reasons (${report.audit.size})")
  for (entry in report.audit) {
    appendLine("audit=${entry.action}; subject=${entry.subject}; reason=${entry.reason}")
  }
}

private fun renderActionArg(value: SuggestedActionValue): String = when (value) {
  is SuggestedActionValue.StringValue -> value.value
  is SuggestedActionValue.BooleanValue -> value.value.toString()
  is SuggestedActionValue.IntValue -> value.value.toString()
  is SuggestedActionValue.StringListValue -> value.value.joinToString(",")
}

private fun String.wordCount(): Int = trim()
  .split(Regex("\\s+"))
  .count { it.isNotBlank() }
