package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.domain.capture.BacklinkStatus
import com.sermilion.personalgraph.domain.capture.CaptureObservationArgs
import com.sermilion.personalgraph.domain.capture.CaptureObservationDecision
import com.sermilion.personalgraph.domain.capture.CaptureObservationKind
import com.sermilion.personalgraph.domain.capture.CaptureObservationResult
import com.sermilion.personalgraph.domain.capture.CaptureResult
import com.sermilion.personalgraph.domain.capture.FlagSensitiveArgs
import com.sermilion.personalgraph.domain.capture.PayloadKind
import com.sermilion.personalgraph.domain.capture.SubjectHubStatus
import com.sermilion.personalgraph.domain.capture.VaultCaptureService
import com.sermilion.personalgraph.domain.capture.WriteEpisodeArgs
import com.sermilion.personalgraph.domain.capture.WriteStateArgs
import com.sermilion.personalgraph.domain.capture.WriteToStagingArgs
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.Confidence
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.StateCategory
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.retrieval.CompactMapEntry
import com.sermilion.personalgraph.domain.retrieval.CompactMapEntryKind
import com.sermilion.personalgraph.domain.retrieval.FullBodyContextSource
import com.sermilion.personalgraph.domain.retrieval.LoadedFullBodyContext
import com.sermilion.personalgraph.domain.retrieval.RetrievalAuditEntry
import com.sermilion.personalgraph.domain.retrieval.RetrievalClassification
import com.sermilion.personalgraph.domain.retrieval.RetrievalDomain
import com.sermilion.personalgraph.domain.retrieval.RetrievedBranch
import com.sermilion.personalgraph.domain.retrieval.RetrievedRootDocument
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalMode
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalReport
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalRequest
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalService
import com.sermilion.personalgraph.domain.retrieval.SessionStartTokenAccounting
import com.sermilion.personalgraph.domain.retrieval.SkippedBranch
import com.sermilion.personalgraph.domain.retrieval.SuggestedAction
import com.sermilion.personalgraph.domain.retrieval.SuggestedActionArg
import com.sermilion.personalgraph.domain.retrieval.SuggestedActionPriority
import com.sermilion.personalgraph.domain.retrieval.SuggestedActionValue
import com.sermilion.personalgraph.domain.retrieval.SuggestedRead
import com.sermilion.personalgraph.domain.retrieval.SuggestedReadPriority
import com.sermilion.personalgraph.domain.search.BranchListingService
import com.sermilion.personalgraph.domain.search.NodeSearchService
import com.sermilion.personalgraph.domain.search.TraverseGraphService
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import java.nio.file.Files
import java.nio.file.Path

class VaultMcpToolsTest :
  FunSpec({

    fun newTools(): VaultMcpToolsTestContext {
      val tempDir = Files.createTempDirectory("mcp-tools-")
      val repo = mockk<VaultRepository>()
      val capture = mockk<VaultCaptureService>()
      val retrieval = mockk<SessionStartRetrievalService>()
      val search = mockk<NodeSearchService>()
      val branchListing = mockk<BranchListingService>()
      val traverse = mockk<TraverseGraphService>()
      val readServices = VaultMcpReadServices(
        retrieval,
        search,
        branchListing,
        traverse,
      )
      val tools = VaultMcpTools(
        repo,
        VaultPathResolver(),
        tempDir,
        capture,
        readServices,
      )
      return VaultMcpToolsTestContext(tools, repo, capture, retrieval, search, branchListing, traverse, tempDir)
    }

    test("write_state happy-path forwards typed args to capture service") {
      val ctx = newTools()
      val captured = slot<WriteStateArgs>()
      coEvery { ctx.capture.writeStateObservation(capture(captured)) } returns
        CaptureResult.Created(NodeId("state/preferences/editor-indent"))

      val args = buildJsonObject {
        put(ToolSchemas.KEY_ID, JsonPrimitive("editor-indent"))
        put(ToolSchemas.KEY_CATEGORY, JsonPrimitive("preference"))
        put(ToolSchemas.KEY_CONFIDENCE, JsonPrimitive("medium"))
        put(ToolSchemas.KEY_BODY, JsonPrimitive("Use 2 spaces."))
        put(ToolSchemas.KEY_SCOPE, JsonPrimitive("work/capmo"))
        put(
          ToolSchemas.KEY_SCOPES,
          JsonArray(listOf(JsonPrimitive("work/capmo"), JsonPrimitive("work/skill-bill"))),
        )
      }

      val result = ctx.tools.writeState(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_OK
      (result[ToolSchemas.KEY_PATH] as JsonPrimitive).content shouldBe "state/preferences/editor-indent"
      captured.captured.id shouldBe "editor-indent"
      captured.captured.body shouldBe "Use 2 spaces."
      captured.captured.category shouldBe StateCategory.Preference
      captured.captured.confidence shouldBe Confidence.Medium
      captured.captured.scope shouldBe "work/capmo"
      captured.captured.scopes shouldBe listOf("work/capmo", "work/skill-bill")
      coVerify(exactly = 1) { ctx.capture.writeStateObservation(any()) }
    }

    test("write_state reports archived paths when a previous memory version was resolved") {
      val ctx = newTools()
      coEvery { ctx.capture.writeStateObservation(any()) } returns CaptureResult.Created(
        id = NodeId("state/preferences/editor-indent"),
        archivedIds = listOf(NodeId("outdated/resolved/state/preferences/editor-indent/2026-04-25t10-00-00z")),
      )

      val args = buildJsonObject {
        put(ToolSchemas.KEY_ID, JsonPrimitive("editor-indent"))
        put(ToolSchemas.KEY_CATEGORY, JsonPrimitive("preference"))
        put(ToolSchemas.KEY_CONFIDENCE, JsonPrimitive("medium"))
        put(ToolSchemas.KEY_BODY, JsonPrimitive("Use 2 spaces."))
      }

      val result = ctx.tools.writeState(args)

      val archivedPaths = result[ToolSchemas.KEY_ARCHIVED_PATHS].shouldBeInstanceOf<JsonArray>()
      archivedPaths.map { (it as JsonPrimitive).content } shouldBe listOf(
        "outdated/resolved/state/preferences/editor-indent/2026-04-25t10-00-00z",
      )
    }

    test("write_state with non-string id is rejected with invalid_input") {
      val ctx = newTools()
      val args = buildJsonObject {
        put(ToolSchemas.KEY_ID, JsonPrimitive(123))
        put(ToolSchemas.KEY_CATEGORY, JsonPrimitive("preference"))
        put(ToolSchemas.KEY_CONFIDENCE, JsonPrimitive("medium"))
      }

      val result = ctx.tools.writeState(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_INVALID_INPUT
      (result[ToolSchemas.KEY_FIELD] as JsonPrimitive).content shouldBe ToolSchemas.KEY_ID
      coVerify(exactly = 0) { ctx.capture.writeStateObservation(any()) }
    }

    test("write_state rejects non-string scope") {
      val ctx = newTools()
      val args = buildJsonObject {
        put(ToolSchemas.KEY_ID, JsonPrimitive("editor-indent"))
        put(ToolSchemas.KEY_CATEGORY, JsonPrimitive("preference"))
        put(ToolSchemas.KEY_CONFIDENCE, JsonPrimitive("medium"))
        put(ToolSchemas.KEY_SCOPE, JsonPrimitive(123))
      }

      val result = ctx.tools.writeState(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_INVALID_INPUT
      (result[ToolSchemas.KEY_FIELD] as JsonPrimitive).content shouldBe ToolSchemas.KEY_SCOPE
      coVerify(exactly = 0) { ctx.capture.writeStateObservation(any()) }
    }

    test("write_state rejects scopes arrays with non-string entries") {
      val ctx = newTools()
      val args = buildJsonObject {
        put(ToolSchemas.KEY_ID, JsonPrimitive("editor-indent"))
        put(ToolSchemas.KEY_CATEGORY, JsonPrimitive("preference"))
        put(ToolSchemas.KEY_CONFIDENCE, JsonPrimitive("medium"))
        put(ToolSchemas.KEY_SCOPES, JsonArray(listOf(JsonPrimitive("work/capmo"), JsonPrimitive(123))))
      }

      val result = ctx.tools.writeState(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_INVALID_INPUT
      (result[ToolSchemas.KEY_FIELD] as JsonPrimitive).content shouldBe ToolSchemas.KEY_SCOPES
      coVerify(exactly = 0) { ctx.capture.writeStateObservation(any()) }
    }

    test("capture_observation forwards candidate observations to capture service") {
      val ctx = newTools()
      val captured = slot<CaptureObservationArgs>()
      coEvery { ctx.capture.captureObservation(capture(captured)) } returns CaptureObservationResult.Decided(
        decision = CaptureObservationDecision.StateWritten,
        reason = "candidate_accepted_as_state",
        captureResult = CaptureResult.Created(NodeId("state/preferences/personal-graph-source-of-truth")),
      )

      val args = buildJsonObject {
        put(
          ToolSchemas.KEY_OBSERVATION,
          JsonPrimitive("Braian prefers personal-graph to own capture filtering."),
        )
        put(ToolSchemas.KEY_SOURCE_CONTEXT, JsonPrimitive("design discussion"))
        put(ToolSchemas.KEY_SUGGESTED_KIND, JsonPrimitive(ToolSchemas.PAYLOAD_KIND_STATE))
        put(ToolSchemas.KEY_LINKS, JsonArray(listOf(JsonPrimitive("state/preferences/example"))))
        put(ToolSchemas.KEY_SCOPE, JsonPrimitive("work/context-app"))
        put(ToolSchemas.KEY_SCOPES, JsonArray(listOf(JsonPrimitive("work/context-app"))))
      }

      val result = ctx.tools.captureObservation(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_OK
      (result[ToolSchemas.KEY_DECISION] as JsonPrimitive).content shouldBe ToolSchemas.DECISION_STATE_WRITTEN
      (result[ToolSchemas.KEY_PATH] as JsonPrimitive).content shouldBe
        "state/preferences/personal-graph-source-of-truth"
      captured.captured.suggestedKind shouldBe CaptureObservationKind.State
      captured.captured.sourceContext shouldBe "design discussion"
      captured.captured.links shouldBe listOf(NodeId("state/preferences/example"))
      captured.captured.scope shouldBe "work/context-app"
      captured.captured.scopes shouldBe listOf("work/context-app")
      coVerify(exactly = 1) { ctx.capture.captureObservation(any()) }
    }

    test("write_episode reports backlink_status when capture service returns Failed backlink") {
      val ctx = newTools()
      coEvery { ctx.capture.writeEpisode(any<WriteEpisodeArgs>()) } returns CaptureResult.Created(
        id = NodeId("domains/work/capmo/events/sample"),
        backlinkId = NodeId("timeline/2026-04/2026-04-24-sample"),
        backlinkStatus = BacklinkStatus.Failed,
        subjectHubId = NodeId("domains/work/capmo/subjects/sample"),
        subjectHubStatus = SubjectHubStatus.Created,
      )

      val args = buildJsonObject {
        put(ToolSchemas.KEY_ID, JsonPrimitive("sample"))
        put(ToolSchemas.KEY_DATE, JsonPrimitive("2026-04-24T15:02:00Z"))
        put(ToolSchemas.KEY_EPISODE_TYPE, JsonPrimitive("decision"))
        put(ToolSchemas.KEY_DOMAIN, JsonPrimitive("work/capmo"))
        put(ToolSchemas.KEY_TOPIC, JsonPrimitive("sample"))
        put(ToolSchemas.KEY_INTENSITY, JsonPrimitive("medium"))
      }
      val result = ctx.tools.writeEpisode(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_OK
      (result[ToolSchemas.KEY_BACKLINK_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.BACKLINK_STATUS_FAILED
      (result[ToolSchemas.KEY_BACKLINK_PATH] as JsonPrimitive).content shouldBe "timeline/2026-04/2026-04-24-sample"
      (result[ToolSchemas.KEY_SUBJECT_HUB_PATH] as JsonPrimitive).content shouldBe "domains/work/capmo/subjects/sample"
      (result[ToolSchemas.KEY_SUBJECT_HUB_STATUS] as JsonPrimitive).content shouldBe
        ToolSchemas.SUBJECT_HUB_STATUS_CREATED
    }

    test("flag_sensitive on people/ path is rejected at the adapter without calling capture") {
      val ctx = newTools()
      val args = buildJsonObject {
        put(ToolSchemas.KEY_TARGET_PATH, JsonPrimitive("people/alice"))
        put(ToolSchemas.KEY_PAYLOAD_KIND, JsonPrimitive(ToolSchemas.PAYLOAD_KIND_STATE))
      }

      val result = ctx.tools.flagSensitive(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_PERMISSION_DENIED
      coVerify(exactly = 0) { ctx.capture.flagSensitive(any()) }
    }

    test("flag_sensitive happy-path forwards typed args to capture service") {
      val ctx = newTools()
      val captured = slot<FlagSensitiveArgs>()
      coEvery { ctx.capture.flagSensitive(capture(captured)) } returns CaptureResult.Created(
        NodeId("staging/sensitive/something"),
      )

      val args = buildJsonObject {
        put(ToolSchemas.KEY_TARGET_PATH, JsonPrimitive("state/preferences/something"))
        put(ToolSchemas.KEY_PAYLOAD_KIND, JsonPrimitive(ToolSchemas.PAYLOAD_KIND_STATE))
      }

      val result = ctx.tools.flagSensitive(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_OK
      val newPath = (result[ToolSchemas.KEY_PATH] as JsonPrimitive).content
      newPath.startsWith("${ToolSchemas.BRANCH_STAGING_SENSITIVE}/") shouldBe true
      captured.captured.targetPath shouldBe "state/preferences/something"
      captured.captured.payloadKind shouldBe PayloadKind.State
      coVerify(exactly = 1) { ctx.capture.flagSensitive(any()) }
    }

    test("list_pending_sensitive returns ids only by default and excludes excerpts") {
      val ctx = newTools()
      val staged = listOf(
        VaultNodeFixtures.stateNode(
          id = "staging/sensitive/note-a",
          body = "first sensitive note.\nsecond line.",
        ),
        VaultNodeFixtures.stateNode(id = "staging/sensitive/note-b", body = "second item"),
      )
      coEvery { ctx.repo.listNodesInBranch(VaultLayout.BRANCH_STAGING_SENSITIVE) } returns staged

      val result = ctx.tools.listPendingSensitive()

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_OK
      val nodes = result[ToolSchemas.KEY_NODES] as JsonArray
      nodes.size shouldBe 2
      val ids = nodes.map { ((it as JsonObject)[ToolSchemas.KEY_ID] as JsonPrimitive).content }
      ids shouldBe listOf("staging/sensitive/note-a", "staging/sensitive/note-b")
      nodes.forEach { (it as JsonObject)[ToolSchemas.KEY_EXCERPT] shouldBe null }
    }

    test("list_pending_sensitive with include_excerpts requires consent marker") {
      val ctx = newTools()
      val args = buildJsonObject {
        put(ToolSchemas.KEY_INCLUDE_EXCERPTS, JsonPrimitive(true))
      }

      val result = ctx.tools.listPendingSensitive(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_PERMISSION_DENIED
    }

    test("list_pending_sensitive with include_excerpts truncates and uses first non-blank line") {
      val ctx = newTools()
      val sensitiveDir = ctx.vaultRoot.resolve(VaultLayout.BRANCH_STAGING_SENSITIVE)
      Files.createDirectories(sensitiveDir)
      Files.writeString(sensitiveDir.resolve(".consent"), "")
      val shortBody = "\n\nfirst real line.\nsecond line.\n"
      val longLine = "x".repeat(160)
      val truncatedBody = "\n$longLine\nsecond line\n"
      coEvery { ctx.repo.listNodesInBranch(VaultLayout.BRANCH_STAGING_SENSITIVE) } returns listOf(
        VaultNodeFixtures.stateNode(id = "staging/sensitive/note-a", body = shortBody),
        VaultNodeFixtures.stateNode(id = "staging/sensitive/note-b", body = truncatedBody),
      )
      val args = buildJsonObject {
        put(ToolSchemas.KEY_INCLUDE_EXCERPTS, JsonPrimitive(true))
      }

      val result = ctx.tools.listPendingSensitive(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_OK
      val nodes = result[ToolSchemas.KEY_NODES] as JsonArray
      val first = nodes[0] as JsonObject
      val second = nodes[1] as JsonObject
      (first[ToolSchemas.KEY_EXCERPT] as JsonPrimitive).content shouldBe "first real line."
      val secondExcerpt = (second[ToolSchemas.KEY_EXCERPT] as JsonPrimitive).content
      secondExcerpt shouldBe "x".repeat(EXCERPT_LIMIT) + "..."
    }

    test("read_node on people/foo returns permission_denied without calling repository") {
      val ctx = newTools()
      val args = buildJsonObject { put(ToolSchemas.KEY_ID, JsonPrimitive("people/alice")) }

      val result = ctx.tools.readNode(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_PERMISSION_DENIED
      coVerify(exactly = 0) { ctx.repo.findNode(any()) }
    }

    test("read_node rejects ids that resolve outside the vault root") {
      val ctx = newTools()
      val outside = Files.createTempDirectory("vault-outside-")
      val symlink = ctx.vaultRoot.resolve("escape")
      Files.createSymbolicLink(symlink, outside)
      val args = buildJsonObject { put(ToolSchemas.KEY_ID, JsonPrimitive("escape/secret")) }

      val result = ctx.tools.readNode(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_PERMISSION_DENIED
      coVerify(exactly = 0) { ctx.repo.findNode(any()) }
    }

    test("write_to_staging forwards typed args to capture service") {
      val ctx = newTools()
      val captured = slot<WriteToStagingArgs>()
      coEvery { ctx.capture.writeToStaging(capture(captured)) } returns CaptureResult.Created(
        NodeId("staging/observations/maybe"),
      )

      val args = buildJsonObject {
        put(ToolSchemas.KEY_ID, JsonPrimitive("maybe"))
        put(ToolSchemas.KEY_CATEGORY, JsonPrimitive("preference"))
        put(ToolSchemas.KEY_CONFIDENCE, JsonPrimitive("low"))
        put(ToolSchemas.KEY_BODY, JsonPrimitive("staged body"))
      }

      val result = ctx.tools.writeToStaging(args)

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_OK
      captured.captured.id shouldBe "maybe"
      coVerify(exactly = 1) { ctx.capture.writeToStaging(any()) }
    }

    test("session_start returns retrieval report json") {
      val ctx = newTools()
      coEvery {
        ctx.retrieval.retrieve(SessionStartRetrievalRequest("review a Capmo PR"))
      } returns SessionStartRetrievalReport(
        rootDocument = RetrievedRootDocument(
          path = "Braian.md",
          body = "# Braian\n",
          loadOrder = 1,
          reason = "root orienting note is always loaded first",
        ),
        classification = RetrievalClassification(
          domain = RetrievalDomain.WorkCapmo,
          matchedTerms = listOf("capmo", "pr"),
          emotionalContextRequested = false,
          emotionalMatchedTerms = emptyList(),
        ),
        loadedBranches = listOf(
          RetrievedBranch("domains/work/capmo", "classified work/capmo from first substantive message", 1),
        ),
        skippedBranches = listOf(SkippedBranch("people", "people/ is never loaded by session-start retrieval")),
        audit = listOf(
          RetrievalAuditEntry("classified", "work/capmo", "matched terms: capmo,pr"),
        ),
        loadedContext = listOf(
          LoadedFullBodyContext(
            id = "Braian.md",
            body = "# Braian\n",
            source = FullBodyContextSource.Root,
            loadOrder = 1,
            reason = "root orienting note is always loaded first",
          ),
        ),
        availableMap = listOf(
          CompactMapEntry(
            id = "domains/work/capmo",
            kind = CompactMapEntryKind.Branch,
            reason = "classified work/capmo from first substantive message",
            nodeCount = 1,
            type = "branch",
          ),
          CompactMapEntry(
            id = "domains/work/capmo/events/review",
            kind = CompactMapEntryKind.Node,
            reason = "classified work/capmo from first substantive message",
            type = "episode",
            domain = "work/capmo",
            summary = "Review context.",
          ),
        ),
        suggestedReads = listOf(
          SuggestedRead(
            id = "domains/work/capmo/events/review",
            reason = "classified work/capmo; event evidence may be useful after map review",
            priority = SuggestedReadPriority.Medium,
          ),
        ),
        suggestedActions = listOf(
          SuggestedAction(
            tool = "search_nodes",
            args = listOf(
              SuggestedActionArg("query", SuggestedActionValue.StringValue("review a Capmo PR")),
              SuggestedActionArg(
                "branches",
                SuggestedActionValue.StringListValue(listOf("state/preferences", "state/roles")),
              ),
              SuggestedActionArg("limit", SuggestedActionValue.IntValue(20)),
              SuggestedActionArg(
                "search_fields",
                SuggestedActionValue.StringListValue(listOf("id", "metadata", "body")),
              ),
              SuggestedActionArg("body_fallback", SuggestedActionValue.BooleanValue(true)),
              SuggestedActionArg("include_body", SuggestedActionValue.BooleanValue(false)),
            ),
            reason = "search the loaded branches before reading full bodies",
            priority = SuggestedActionPriority.High,
          ),
        ),
        estimatedTokens = SessionStartTokenAccounting(
          responseTotal = 142,
          metadataTokens = 100,
          bodyTokens = 23,
          prunedBodyTokens = 19,
        ),
      )

      val result = ctx.tools.sessionStart(
        buildJsonObject { put(ToolSchemas.KEY_MESSAGE, JsonPrimitive("review a Capmo PR")) },
      )

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_OK
      val classification = result[ToolSchemas.KEY_CLASSIFICATION] as JsonObject
      (classification[ToolSchemas.KEY_DOMAIN] as JsonPrimitive).content shouldBe "work/capmo"
      result[ToolSchemas.KEY_NODES] shouldBe null
      result[ToolSchemas.KEY_LOADED_BRANCHES] shouldBe null
      result[ToolSchemas.KEY_LOADED_FULL_BODY_CONTEXT] shouldBe null
      result[ToolSchemas.KEY_COMPACT_MAP_ENTRIES] shouldBe null
      val loadedContext = result[ToolSchemas.KEY_LOADED_CONTEXT] as JsonArray
      ((loadedContext[0] as JsonObject)[ToolSchemas.KEY_SOURCE] as JsonPrimitive).content shouldBe "root"
      val compactMap = result[ToolSchemas.KEY_AVAILABLE_MAP] as JsonArray
      ((compactMap[0] as JsonObject)[ToolSchemas.KEY_KIND] as JsonPrimitive).content shouldBe "branch"
      ((compactMap[1] as JsonObject)[ToolSchemas.KEY_TYPE] as JsonPrimitive).content shouldBe "episode"
      ((compactMap[1] as JsonObject)[ToolSchemas.KEY_SUMMARY] as JsonPrimitive).content shouldBe "Review context."
      val suggestedReads = result[ToolSchemas.KEY_SUGGESTED_READS].shouldBeInstanceOf<JsonArray>()
      suggestedReads.size shouldBe 1
      val suggestedRead = suggestedReads[0] as JsonObject
      (suggestedRead[ToolSchemas.KEY_ID] as JsonPrimitive).content shouldBe "domains/work/capmo/events/review"
      (suggestedRead[ToolSchemas.KEY_REASON] as JsonPrimitive).content shouldBe
        "classified work/capmo; event evidence may be useful after map review"
      (suggestedRead[ToolSchemas.KEY_PRIORITY] as JsonPrimitive).content shouldBe "medium"
      val suggestedActions = result[ToolSchemas.KEY_SUGGESTED_ACTIONS].shouldBeInstanceOf<JsonArray>()
      suggestedActions.size shouldBe 1
      val suggestedAction = suggestedActions[0] as JsonObject
      (suggestedAction[ToolSchemas.KEY_TOOL] as JsonPrimitive).content shouldBe "search_nodes"
      (suggestedAction[ToolSchemas.KEY_REASON] as JsonPrimitive).content shouldBe
        "search the loaded branches before reading full bodies"
      val actionArgs = suggestedAction[ToolSchemas.KEY_ARGS].shouldBeInstanceOf<JsonObject>()
      (actionArgs[ToolSchemas.KEY_QUERY] as JsonPrimitive).content shouldBe "review a Capmo PR"
      val actionBranches = actionArgs[ToolSchemas.KEY_BRANCHES].shouldBeInstanceOf<JsonArray>()
      actionBranches.map { (it as JsonPrimitive).content } shouldBe listOf("state/preferences", "state/roles")
      val tokens = result[ToolSchemas.KEY_ESTIMATED_TOKENS].shouldBeInstanceOf<JsonObject>()
      (tokens[ToolSchemas.KEY_RESPONSE_TOTAL] as JsonPrimitive).int shouldBe 142
      (tokens[ToolSchemas.KEY_METADATA_TOKENS] as JsonPrimitive).int shouldBe 100
      (tokens[ToolSchemas.KEY_BODY_TOKENS] as JsonPrimitive).int shouldBe 23
      (tokens[ToolSchemas.KEY_PRUNED_BODY_TOKENS] as JsonPrimitive).int shouldBe 19
      coVerify(exactly = 1) { ctx.retrieval.retrieve(SessionStartRetrievalRequest("review a Capmo PR")) }
    }

    test("session_start forwards explicit full-loading retrieval mode") {
      val ctx = newTools()
      val captured = slot<SessionStartRetrievalRequest>()
      coEvery { ctx.retrieval.retrieve(capture(captured)) } returns SessionStartRetrievalReport(
        rootDocument = null,
        classification = RetrievalClassification(
          domain = RetrievalDomain.General,
          matchedTerms = emptyList(),
          emotionalContextRequested = false,
          emotionalMatchedTerms = emptyList(),
        ),
        loadedBranches = emptyList(),
        loadedNodes = emptyList(),
        skippedBranches = emptyList(),
        audit = emptyList(),
      )

      val result = ctx.tools.sessionStart(
        buildJsonObject {
          put(ToolSchemas.KEY_MESSAGE, JsonPrimitive("load everything"))
          put(ToolSchemas.KEY_RETRIEVAL_MODE, JsonPrimitive("full-loading"))
        },
      )

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_OK
      captured.captured.retrievalMode shouldBe SessionStartRetrievalMode.FullLoading
    }

    test("session_start rejects non-string retrieval mode") {
      val ctx = newTools()

      val result = ctx.tools.sessionStart(
        buildJsonObject {
          put(ToolSchemas.KEY_MESSAGE, JsonPrimitive("load everything"))
          put(ToolSchemas.KEY_RETRIEVAL_MODE, JsonPrimitive(123))
        },
      )

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_INVALID_INPUT
      (result[ToolSchemas.KEY_FIELD] as JsonPrimitive).content shouldBe ToolSchemas.KEY_RETRIEVAL_MODE
      coVerify(exactly = 0) { ctx.retrieval.retrieve(any()) }
    }

    test("session_start requires a message") {
      val ctx = newTools()

      val result = ctx.tools.sessionStart(JsonObject(emptyMap()))

      (result[ToolSchemas.KEY_STATUS] as JsonPrimitive).content shouldBe ToolSchemas.STATUS_INVALID_INPUT
      (result[ToolSchemas.KEY_FIELD] as JsonPrimitive).content shouldBe ToolSchemas.KEY_MESSAGE
      coVerify(exactly = 0) { ctx.retrieval.retrieve(any()) }
    }

    test("write_episode schema describes ISO-8601 instant rule on date field") {
      val schema = ToolSchemaBuilder.writeEpisodeSchema()
      val dateField = schema.properties!![ToolSchemas.KEY_DATE] as JsonObject
      val description = (dateField["description"] as JsonPrimitive).content
      description shouldContain "ISO-8601"
      description shouldContain "2026-04-25T00:00:00Z"
    }

    test("write_state schema describes id rejection of singular state prefixes") {
      val schema = ToolSchemaBuilder.writeStateSchema()
      val idField = schema.properties!![ToolSchemas.KEY_ID] as JsonObject
      val description = (idField["description"] as JsonPrimitive).content
      description shouldContain "state/roles"
      description.lowercase() shouldContain "singular"
      description shouldContain "slugified without word bounding"
    }

    test("write_state schema describes links as silently dropping invalid entries") {
      val schema = ToolSchemaBuilder.writeStateSchema()
      val linksField = schema.properties!![ToolSchemas.KEY_LINKS] as JsonObject
      val description = (linksField["description"] as JsonPrimitive).content
      description shouldContain "silently dropped"
    }

    test("write_state schema exposes scope and scopes fields") {
      val schema = ToolSchemaBuilder.writeStateSchema()

      schema.properties!![ToolSchemas.KEY_SCOPE].shouldBeInstanceOf<JsonObject>()
      schema.properties!![ToolSchemas.KEY_SCOPES].shouldBeInstanceOf<JsonObject>()
    }

    test("capture_observation schema describes personal-graph as the decision owner") {
      val schema = ToolSchemaBuilder.captureObservationSchema()
      val observationField = schema.properties!![ToolSchemas.KEY_OBSERVATION] as JsonObject
      val description = (observationField["description"] as JsonPrimitive).content
      description shouldContain "Personal-graph owns"
      description shouldContain "reject"
    }

    test("session_start schema exposes explicit retrieval mode") {
      val schema = ToolSchemaBuilder.sessionStartSchema()
      val retrievalModeField = schema.properties!![ToolSchemas.KEY_RETRIEVAL_MODE] as JsonObject
      val description = (retrievalModeField["description"] as JsonPrimitive).content

      description shouldContain "Defaults to map-first"
      description shouldContain "available_map"
      description shouldContain "suggested_reads"
      description shouldContain "suggested_actions"
      description shouldContain "estimated_tokens"
      description shouldContain "full-loading"
    }

    test("tool descriptions document map-first and full-body follow-up path") {
      ToolSchemas.DESC_SESSION_START shouldContain "Map-first"
      ToolSchemas.DESC_SESSION_START shouldContain "available_map"
      ToolSchemas.DESC_SESSION_START shouldContain "suggested_actions"
      ToolSchemas.DESC_SESSION_START shouldContain "estimated_tokens"
      ToolSchemas.DESC_TRAVERSE_GRAPH shouldContain "entrypoints"
      ToolSchemas.DESC_TRAVERSE_GRAPH shouldContain "pruned candidates"
      ToolSchemas.DESC_TRAVERSE_GRAPH shouldContain "budget_tokens"
      ToolSchemas.DESC_READ_NODE shouldContain "full node body"
      ToolSchemas.DESC_LIST_BRANCH shouldContain "explicit follow-up"
    }

    test("flag_sensitive schema describes payload_kind rejection on type mismatch") {
      val schema = ToolSchemaBuilder.flagSensitiveSchema()
      val payloadKindField = schema.properties!![ToolSchemas.KEY_PAYLOAD_KIND] as JsonObject
      val description = (payloadKindField["description"] as JsonPrimitive).content
      description shouldContain "payload kind"
      description shouldContain "rejected"
    }
  })

internal data class VaultMcpToolsTestContext(
  val tools: VaultMcpTools,
  val repo: VaultRepository,
  val capture: VaultCaptureService,
  val retrieval: SessionStartRetrievalService,
  val search: NodeSearchService,
  val branchListing: BranchListingService,
  val traverse: TraverseGraphService,
  val vaultRoot: Path,
)

internal fun newVaultMcpToolsTestContext(): VaultMcpToolsTestContext {
  val tempDir = Files.createTempDirectory("mcp-tools-")
  val repo = mockk<VaultRepository>()
  val capture = mockk<VaultCaptureService>()
  val retrieval = mockk<SessionStartRetrievalService>()
  val search = mockk<NodeSearchService>()
  val branchListing = mockk<BranchListingService>()
  val traverse = mockk<TraverseGraphService>()
  val readServices = VaultMcpReadServices(
    retrieval,
    search,
    branchListing,
    traverse,
  )
  val tools = VaultMcpTools(
    repo,
    VaultPathResolver(),
    tempDir,
    capture,
    readServices,
  )
  return VaultMcpToolsTestContext(tools, repo, capture, retrieval, search, branchListing, traverse, tempDir)
}
