package com.sermilion.personalgraph.data.capture

import com.sermilion.personalgraph.data.codec.MarkdownFrontmatterCodec
import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.data.repository.PersonalGraphVaultRepository
import com.sermilion.personalgraph.domain.capture.CaptureObservationArgs
import com.sermilion.personalgraph.domain.capture.CaptureObservationDecision
import com.sermilion.personalgraph.domain.capture.CaptureObservationKind
import com.sermilion.personalgraph.domain.capture.CaptureObservationResult
import com.sermilion.personalgraph.domain.capture.CaptureResult
import com.sermilion.personalgraph.domain.capture.FlagSensitiveArgs
import com.sermilion.personalgraph.domain.capture.PayloadKind
import com.sermilion.personalgraph.domain.capture.WriteEpisodeArgs
import com.sermilion.personalgraph.domain.capture.WriteStateArgs
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.Confidence
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.EpisodeType
import com.sermilion.personalgraph.domain.model.Intensity
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.StateCategory
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import com.sermilion.personalgraph.testing.TestDispatcherProvider
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.nio.file.Files

class PersonalGraphVaultCaptureServiceTest :
  FunSpec({

    fun newService(): Pair<PersonalGraphVaultCaptureService, VaultRepository> {
      val repo = mockk<VaultRepository>()
      val clock = fixedClock()
      val service = PersonalGraphVaultCaptureService(repo, clock)
      return service to repo
    }

    fun newRepositoryService(): Pair<PersonalGraphVaultCaptureService, PersonalGraphVaultRepository> {
      val tempDir = Files.createTempDirectory("capture-scoped-")
      val resolver = VaultPathResolver()
      val repository = PersonalGraphVaultRepository(
        vaultRoot = tempDir,
        dispatcherProvider = TestDispatcherProvider(),
        codec = MarkdownFrontmatterCodec(),
        pathResolver = resolver,
      )
      return PersonalGraphVaultCaptureService(repository, fixedClock()) to repository
    }

    test("flagSensitive on existing state node calls moveNode atomically") {
      val (service, repo) = newService()
      val source = VaultNodeFixtures.stateNode(id = "state/preferences/something", body = "private")
      val sourceId = NodeId(source.id.value)
      coEvery { repo.findNode(sourceId) } returns source
      coEvery { repo.moveNode(sourceId, VaultLayout.BRANCH_STAGING_SENSITIVE) } returns WriteOutcome.Applied

      val result = service.flagSensitive(
        FlagSensitiveArgs(targetPath = source.id.value, payloadKind = PayloadKind.State),
      )

      result.shouldBeInstanceOf<CaptureResult.Created>()
      result.id.value.startsWith("${VaultLayout.BRANCH_STAGING_SENSITIVE}/") shouldBe true
      coVerify(exactly = 1) { repo.moveNode(sourceId, VaultLayout.BRANCH_STAGING_SENSITIVE) }
      coVerify(exactly = 0) { repo.deleteNode(any()) }
      coVerify(exactly = 0) { repo.writeNode(any()) }
    }

    test("flagSensitive rejects when payload_kind does not match node type") {
      val (service, repo) = newService()
      val source = VaultNodeFixtures.stateNode(id = "state/preferences/something")
      coEvery { repo.findNode(NodeId(source.id.value)) } returns source

      val result = service.flagSensitive(
        FlagSensitiveArgs(targetPath = source.id.value, payloadKind = PayloadKind.Episode),
      )

      result.shouldBeInstanceOf<CaptureResult.InvalidInput>()
      result.field shouldBe "payload_kind"
      result.expected shouldBe PayloadKind.State.name
      coVerify(exactly = 0) { repo.moveNode(any(), any()) }
    }

    test("flagSensitive returns PermissionDenied when target path is read-blocked") {
      val (service, repo) = newService()

      val result = service.flagSensitive(
        FlagSensitiveArgs(targetPath = "people/alice", payloadKind = PayloadKind.State),
      )

      result.shouldBeInstanceOf<CaptureResult.PermissionDenied>()
      coVerify(exactly = 0) { repo.findNode(any()) }
    }

    test("writeStateObservation accepts canonical state/roles/<leaf> as-is") {
      val (service, repo) = newService()
      val captured = slot<StateNode>()
      coEvery { repo.writeNode(capture(captured)) } returns WriteOutcome.Applied

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = "state/roles/sermilion-music",
          category = StateCategory.Role,
          confidence = Confidence.High,
          body = "role body",
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.Created>()
      result.id.value shouldBe "state/roles/sermilion-music"
      captured.captured.id.value shouldBe "state/roles/sermilion-music"
    }

    test("writeStateObservation accepts bare leaf and routes via category to plural prefix") {
      val (service, repo) = newService()
      val captured = slot<StateNode>()
      coEvery { repo.writeNode(capture(captured)) } returns WriteOutcome.Applied

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = "sermilion-music",
          category = StateCategory.Role,
          confidence = Confidence.High,
          body = "role body",
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.Created>()
      result.id.value shouldBe "state/roles/sermilion-music"
      captured.captured.id.value shouldBe "state/roles/sermilion-music"
    }

    test("writeStateObservation persists scoped state metadata through repository encoding") {
      val (service, repo) = newRepositoryService()

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = "scoped-preference",
          category = StateCategory.Preference,
          confidence = Confidence.High,
          body = "Scoped preference.",
          links = emptyList(),
          sensitive = false,
          scope = "work/capmo",
          scopes = listOf("work/skill-bill", "creative/music"),
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.Created>()
      val decoded = repo.findNode(NodeId("state/preferences/scoped-preference"))
        .shouldBeInstanceOf<StateNode>()
      decoded.scope shouldBe "work/capmo"
      decoded.scopes shouldBe listOf("work/skill-bill", "creative/music")
    }

    test("writeStateObservation rejects state/role/<leaf> singular form before parsing") {
      val (service, repo) = newService()

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = "state/role/sermilion-music",
          category = StateCategory.Role,
          confidence = Confidence.High,
          body = "role body",
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.InvalidInput>()
      result.field shouldBe "id"
      result.expected shouldBe "state/roles/sermilion-music"
      coVerify(exactly = 0) { repo.writeNode(any()) }
    }

    test("writeStateObservation rejects state/preference/<leaf> singular form before parsing") {
      val (service, repo) = newService()

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = "state/preference/editor-indent",
          category = StateCategory.Preference,
          confidence = Confidence.Medium,
          body = "pref body",
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.InvalidInput>()
      result.field shouldBe "id"
      result.expected shouldBe "state/preferences/editor-indent"
      coVerify(exactly = 0) { repo.writeNode(any()) }
    }

    test("writeStateObservation rejects state/fact/<leaf> singular form and routes to knowledge") {
      val (service, repo) = newService()

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = "state/fact/k8s",
          category = StateCategory.Fact,
          confidence = Confidence.Medium,
          body = "fact body",
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.InvalidInput>()
      result.field shouldBe "id"
      result.expected shouldBe "state/knowledge/k8s"
      coVerify(exactly = 0) { repo.writeNode(any()) }
    }

    test("writeStateObservation rejects singular role even when sensitive is true") {
      val (service, repo) = newService()

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = "state/role/x",
          category = StateCategory.Role,
          confidence = Confidence.High,
          body = "role body",
          links = emptyList(),
          sensitive = true,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.InvalidInput>()
      result.field shouldBe "id"
      result.expected shouldBe "state/roles/x"
      coVerify(exactly = 0) { repo.writeNode(any()) }
    }

    test("writeStateObservation rejects singular preference when sensitive is true") {
      val (service, repo) = newService()

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = "state/preference/x",
          category = StateCategory.Preference,
          confidence = Confidence.Medium,
          body = "pref body",
          links = emptyList(),
          sensitive = true,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.InvalidInput>()
      result.field shouldBe "id"
      result.expected shouldBe "state/preferences/x"
      coVerify(exactly = 0) { repo.writeNode(any()) }
    }

    test("writeStateObservation rejects singular fact when sensitive is true") {
      val (service, repo) = newService()

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = "state/fact/x",
          category = StateCategory.Fact,
          confidence = Confidence.Low,
          body = "fact body",
          links = emptyList(),
          sensitive = true,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.InvalidInput>()
      result.field shouldBe "id"
      result.expected shouldBe "state/knowledge/x"
      coVerify(exactly = 0) { repo.writeNode(any()) }
    }

    test("writeStateObservation rejects singular prefix with empty leaf and reports placeholder expected") {
      val (service, repo) = newService()

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = "state/role/",
          category = StateCategory.Role,
          confidence = Confidence.High,
          body = "role body",
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.InvalidInput>()
      result.field shouldBe "id"
      result.expected shouldBe "state/roles/<leaf>"
      coVerify(exactly = 0) { repo.writeNode(any()) }
    }

    test("writeStateObservation normalizes mixed-case singular prefix and rejects with canonical expected") {
      val (service, repo) = newService()

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = "State/Role/foo",
          category = StateCategory.Role,
          confidence = Confidence.High,
          body = "role body",
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.InvalidInput>()
      result.field shouldBe "id"
      result.expected shouldBe "state/roles/foo"
      coVerify(exactly = 0) { repo.writeNode(any()) }
    }

    test("writeStateObservation trims surrounding whitespace before checking singular prefix") {
      val (service, repo) = newService()

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = " state/role/foo",
          category = StateCategory.Role,
          confidence = Confidence.High,
          body = "role body",
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.InvalidInput>()
      result.field shouldBe "id"
      result.expected shouldBe "state/roles/foo"
      coVerify(exactly = 0) { repo.writeNode(any()) }
    }

    test("captureObservation rejects routine transient noise without writing") {
      val (service, repo) = newService()

      val result = service.captureObservation(
        CaptureObservationArgs(
          observation = "ran tests",
          sourceContext = "local check",
          suggestedKind = null,
          id = null,
          category = null,
          confidence = null,
          date = null,
          episodeType = null,
          domain = null,
          topic = null,
          intensity = null,
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureObservationResult.Decided>()
      result.decision shouldBe CaptureObservationDecision.Rejected
      coVerify(exactly = 0) { repo.writeNode(any()) }
    }

    test("captureObservation writes reusable preference candidates as state") {
      val (service, repo) = newService()
      val captured = slot<StateNode>()
      coEvery { repo.findNode(any()) } returns null
      coEvery { repo.writeNode(capture(captured)) } returns WriteOutcome.Applied

      val result = service.captureObservation(
        CaptureObservationArgs(
          observation = "Braian prefers personal-graph as the source of truth for memory filtering.",
          sourceContext = "design discussion",
          suggestedKind = null,
          id = null,
          category = null,
          confidence = null,
          date = null,
          episodeType = null,
          domain = null,
          topic = null,
          intensity = null,
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureObservationResult.Decided>()
      result.decision shouldBe CaptureObservationDecision.StateWritten
      captured.captured.id.value shouldBe
        "state/preferences/braian-prefers-personal-graph-source-truth-memory-filtering"
      captured.captured.confidence shouldBe Confidence.High
      captured.captured.body shouldContain "Source context: design discussion"
    }

    test("captureObservation persists scoped state metadata through repository encoding") {
      val (service, repo) = newRepositoryService()

      val result = service.captureObservation(
        CaptureObservationArgs(
          observation = "Braian prefers scoped memory for Capmo-specific implementation rules.",
          sourceContext = "design discussion",
          suggestedKind = null,
          id = "capmo-scoped-memory",
          category = StateCategory.Preference,
          confidence = Confidence.High,
          date = null,
          episodeType = null,
          domain = null,
          topic = null,
          intensity = null,
          links = emptyList(),
          sensitive = false,
          scope = "work/capmo",
          scopes = listOf("work/capmo", "work/context-app"),
        ),
      )

      result.shouldBeInstanceOf<CaptureObservationResult.Decided>()
      result.decision shouldBe CaptureObservationDecision.StateWritten
      val decoded = repo.findNode(NodeId("state/preferences/capmo-scoped-memory"))
        .shouldBeInstanceOf<StateNode>()
      decoded.scope shouldBe "work/capmo"
      decoded.scopes shouldBe listOf("work/capmo", "work/context-app")
    }

    test("captureObservation stages low-confidence candidates instead of saving as durable state") {
      val (service, repo) = newService()
      val captured = slot<StateNode>()
      coEvery { repo.writeNode(capture(captured)) } returns WriteOutcome.Applied

      val result = service.captureObservation(
        CaptureObservationArgs(
          observation = "Maybe this might matter later.",
          sourceContext = "uncertain session note",
          suggestedKind = null,
          id = "maybe-later",
          category = StateCategory.Knowledge,
          confidence = Confidence.Low,
          date = null,
          episodeType = null,
          domain = null,
          topic = null,
          intensity = null,
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureObservationResult.Decided>()
      result.decision shouldBe CaptureObservationDecision.StagedObservation
      captured.captured.id.value shouldBe "staging/observations/maybe-later"
    }

    test("captureObservation routes sensitive candidates to sensitive staging") {
      val (service, repo) = newService()
      val captured = slot<StateNode>()
      coEvery { repo.writeNode(capture(captured)) } returns WriteOutcome.Applied

      val result = service.captureObservation(
        CaptureObservationArgs(
          observation = "API key: abcdefghijklmnop should not be stored as durable knowledge.",
          sourceContext = "secret-bearing candidate",
          suggestedKind = null,
          id = "api-key",
          category = null,
          confidence = null,
          date = null,
          episodeType = null,
          domain = null,
          topic = null,
          intensity = null,
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureObservationResult.Decided>()
      result.decision shouldBe CaptureObservationDecision.StagedSensitive
      captured.captured.id.value shouldBe "staging/sensitive/api-key"
      captured.captured.confidence shouldBe Confidence.Low
    }

    test("writeEpisode creates a canonical subject hub and timeline stub") {
      val (service, repo) = newService()
      val captured = mutableListOf<com.sermilion.personalgraph.domain.model.VaultNode>()
      coEvery { repo.writeNode(capture(captured)) } returns WriteOutcome.Applied
      coEvery { repo.findSubjectHub(any(), any(), any()) } returns null

      val result = service.writeEpisode(
        WriteEpisodeArgs(
          id = "design-review",
          date = VaultNodeFixtures.episodeInstant,
          episodeType = EpisodeType.Decision,
          domain = "work/capmo",
          topic = "Build Pipeline",
          intensity = Intensity.Medium,
          body = "Settled on one deployment workflow.\n",
          linked = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.Created>()
      result.id.value shouldBe "domains/work/capmo/events/design-review"
      result.subjectHubId?.value shouldBe "domains/work/capmo/subjects/build-pipeline"
      result.backlinkId?.value shouldBe "timeline/2026-04/2026-04-24-build-pipeline"
      result.subjectHubStatus.name shouldBe "Created"
      captured.filterIsInstance<SubjectNode>().single().body shouldBe
        (
          "## Summary\nCanonical subject hub for Build Pipeline.\n\n## Evidence\n" +
            "- 2026-04-24: [[domains/work/capmo/events/design-review]]" +
            " — Settled on one deployment workflow.\n"
          )
      captured.filterIsInstance<EpisodeNode>().last().links.map { it.value } shouldBe listOf(
        "domains/work/capmo/events/design-review",
        "domains/work/capmo/subjects/build-pipeline",
      )
    }

    test("writeEpisode appends evidence to an existing subject hub before writing timeline stub") {
      val (service, repo) = newService()
      val existing = VaultNodeFixtures.subjectNode().copy(
        id = NodeId("domains/work/capmo/subjects/build-pipeline"),
        subject = "build-pipeline",
        body =
        "## Summary\nExisting hub.\n\n## Evidence\n" +
          "- 2026-04-23: [[domains/work/capmo/events/older]] — Older evidence.\n",
        evidenceCount = 1,
        sourceIds = listOf(NodeId("domains/work/capmo/events/older")),
      )
      val captured = mutableListOf<com.sermilion.personalgraph.domain.model.VaultNode>()
      coEvery { repo.findSubjectHub("work/capmo", "Build Pipeline", any()) } returns existing
      coEvery { repo.writeNode(capture(captured)) } returns WriteOutcome.Applied

      val result = service.writeEpisode(
        WriteEpisodeArgs(
          id = "design-review",
          date = VaultNodeFixtures.episodeInstant,
          episodeType = EpisodeType.Decision,
          domain = "work/capmo",
          topic = "Build Pipeline",
          intensity = Intensity.Medium,
          body = "Settled on one deployment workflow.\n",
          linked = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.Created>()
      result.subjectHubStatus.name shouldBe "Updated"
      val writtenSubject = captured.filterIsInstance<SubjectNode>().single()
      writtenSubject.evidenceCount shouldBe 2
      writtenSubject.body shouldContain "[[domains/work/capmo/events/design-review]]"
    }

    test("writeEpisode keeps timeline stub ids keyed by topic slug for compatibility") {
      val (service, repo) = newService()
      coEvery { repo.writeNode(any()) } returns WriteOutcome.Applied
      coEvery { repo.findSubjectHub(any(), any(), any()) } returns null

      val result = service.writeEpisode(
        WriteEpisodeArgs(
          id = "internal-ticket-1234",
          date = VaultNodeFixtures.episodeInstant,
          episodeType = EpisodeType.Decision,
          domain = "work/capmo",
          topic = "Build Pipeline",
          intensity = Intensity.Medium,
          body = "Settled on one deployment workflow.\n",
          linked = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.Created>()
      result.backlinkId?.value shouldBe "timeline/2026-04/2026-04-24-build-pipeline"
    }

    test("captureObservation writes complete episode candidates as episodes") {
      val (service, repo) = newService()
      coEvery { repo.findNode(NodeId("domains/work/personal-graph/events/candidate-ingest-boundary")) } returns null
      coEvery { repo.writeNode(any()) } returns WriteOutcome.Applied
      coEvery { repo.findSubjectHub(any(), any(), any()) } returns null

      val result = service.captureObservation(
        CaptureObservationArgs(
          observation = "Decided personal-graph owns capture filtering instead of every caller.",
          sourceContext = "personal-graph design session",
          suggestedKind = CaptureObservationKind.Episode,
          id = "candidate-ingest-boundary",
          category = null,
          confidence = null,
          date = VaultNodeFixtures.episodeInstant,
          episodeType = EpisodeType.Decision,
          domain = "work/personal-graph",
          topic = "Candidate ingest boundary",
          intensity = Intensity.Medium,
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureObservationResult.Decided>()
      result.decision shouldBe CaptureObservationDecision.EpisodeWritten
      val capture = result.captureResult.shouldBeInstanceOf<CaptureResult.Created>()
      capture.id.value shouldBe "domains/work/personal-graph/events/candidate-ingest-boundary"
    }
  })

private fun fixedClock(): Clock = object : Clock {
  override fun now(): Instant = Instant.parse("2026-04-25T10:00:00Z")
}
