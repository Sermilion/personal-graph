package com.sermilion.personalgraph.mcp.runtime

import com.sermilion.personalgraph.mcp.di.McpServerComponent
import com.sermilion.personalgraph.mcp.di.create
import com.sermilion.personalgraph.mcp.tools.ToolSchemaBuilder
import com.sermilion.personalgraph.mcp.tools.ToolSchemas
import com.sermilion.personalgraph.mcp.tools.VaultMcpTools
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.error
import io.modelcontextprotocol.kotlin.sdk.types.success
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path

object McpServerRuntime {

  private val logger = KotlinLogging.logger {}

  private val json: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
  }

  fun run(vaultRoot: Path) {
    val component = McpServerComponent::class.create(vaultRoot)
    val tools = component.vaultMcpTools
    runBlocking {
      val server = Server(
        serverInfo = Implementation(name = SERVER_NAME, version = SERVER_VERSION),
        options = ServerOptions(
          capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
        ),
      )
      registerTools(server, tools)
      val source: Source = System.`in`.asSource().buffered()
      val sink: Sink = System.out.asSink().buffered()
      val transport = StdioServerTransport(inputStream = source, outputStream = sink)
      val shutdownSignal = CompletableDeferred<Unit>()
      val shutdownHook = Thread({ shutdownSignal.complete(Unit) }, "mcp-shutdown")
      Runtime.getRuntime().addShutdownHook(shutdownHook)
      try {
        server.createSession(transport)
        logger.info { "personal-graph MCP server connected (vault=$vaultRoot)" }
        shutdownSignal.await()
      } finally {
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
        runCatching { sink.close() }
        runCatching { source.close() }
      }
    }
  }

  private fun registerTools(server: Server, tools: VaultMcpTools) {
    server.addTool(
      name = ToolSchemas.TOOL_WRITE_STATE,
      description = ToolSchemas.DESC_WRITE_STATE,
      inputSchema = ToolSchemaBuilder.writeStateSchema(),
    ) { request -> tools.writeState(request.arguments ?: emptyJsonObject).asResult() }
    server.addTool(
      name = ToolSchemas.TOOL_WRITE_EPISODE,
      description = ToolSchemas.DESC_WRITE_EPISODE,
      inputSchema = ToolSchemaBuilder.writeEpisodeSchema(),
    ) { request -> tools.writeEpisode(request.arguments ?: emptyJsonObject).asResult() }
    server.addTool(
      name = ToolSchemas.TOOL_WRITE_TO_STAGING,
      description = ToolSchemas.DESC_WRITE_TO_STAGING,
      inputSchema = ToolSchemaBuilder.writeToStagingSchema(),
    ) { request -> tools.writeToStaging(request.arguments ?: emptyJsonObject).asResult() }
    server.addTool(
      name = ToolSchemas.TOOL_FLAG_SENSITIVE,
      description = ToolSchemas.DESC_FLAG_SENSITIVE,
      inputSchema = ToolSchemaBuilder.flagSensitiveSchema(),
    ) { request -> tools.flagSensitive(request.arguments ?: emptyJsonObject).asResult() }
    server.addTool(
      name = ToolSchemas.TOOL_LIST_PENDING_SENSITIVE,
      description = ToolSchemas.DESC_LIST_PENDING_SENSITIVE,
      inputSchema = ToolSchemaBuilder.listPendingSensitiveSchema(),
    ) { request -> tools.listPendingSensitive(request.arguments ?: emptyJsonObject).asResult() }
    server.addTool(
      name = ToolSchemas.TOOL_READ_NODE,
      description = ToolSchemas.DESC_READ_NODE,
      inputSchema = ToolSchemaBuilder.readNodeSchema(),
    ) { request -> tools.readNode(request.arguments ?: emptyJsonObject).asResult() }
    server.addTool(
      name = ToolSchemas.TOOL_LIST_BRANCH,
      description = ToolSchemas.DESC_LIST_BRANCH,
      inputSchema = ToolSchemaBuilder.listBranchSchema(),
    ) { request -> tools.listBranch(request.arguments ?: emptyJsonObject).asResult() }
  }

  private fun JsonObject.asResult(): CallToolResult {
    val statusValue = (this[ToolSchemas.KEY_STATUS] as? JsonPrimitive)?.content
    val isSuccess = statusValue == ToolSchemas.STATUS_OK
    val text = json.encodeToString(JsonObject.serializer(), this)
    return if (isSuccess) {
      CallToolResult.success(text, meta = this)
    } else {
      CallToolResult.error(text, meta = this)
    }
  }

  private const val SERVER_NAME: String = "personal-graph"
  private const val SERVER_VERSION: String = "0.1.0"

  private val emptyJsonObject: JsonObject = JsonObject(emptyMap())
}
