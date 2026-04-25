package com.sermilion.personalgraph.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import com.sermilion.personalgraph.cli.di.CliComponent
import com.sermilion.personalgraph.cli.di.create
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.repository.ConsolidationReport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking

class ConsolidateCommand : CliktCommand(name = COMMAND_NAME) {
  private val vaultRoot by option("--vault").path(mustExist = true, canBeFile = false).required()

  private val logger = KotlinLogging.logger {}

  override fun run() {
    val report = runBlocking {
      val component = CliComponent::class.create(vaultRoot)
      component.consolidationService.consolidate()
    }
    logger.info { "consolidation completed for vault=$vaultRoot" }
    echo(formatReport(report))
  }

  private fun formatReport(report: ConsolidationReport): String = buildString {
    appendLine("Consolidation report")
    appendLine("graduated=${report.graduated.size}")
    appendLine("merged=${report.mergedDuplicates.size}")
    appendLine("promoted_patterns=${report.promotedPatterns.size}")
    appendLine("annotated_contradictions=${report.annotatedContradictions.size}")
    appendIds("graduated_ids", report.graduated.map { it.nodeId })
    appendIds("merged_into_ids", report.mergedDuplicates.map { it.mergedInto })
    appendIds("pattern_ids", report.promotedPatterns.map { it.nodeId })
    appendIds("contradiction_ids", report.annotatedContradictions.map { it.nodeId })
    appendIds("contradiction_source_ids", report.annotatedContradictions.flatMap { it.sourceIds })
  }.trimEnd()

  private fun StringBuilder.appendIds(label: String, ids: List<NodeId>) {
    if (ids.isEmpty()) return
    appendLine("$label=${ids.joinToString(",") { it.value }}")
  }

  companion object {
    const val COMMAND_NAME: String = "consolidate"
  }
}
