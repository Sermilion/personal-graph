package com.sermilion.personalgraph.data.capture

import com.sermilion.personalgraph.data.codec.MarkdownFrontmatterCodec
import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.data.repository.PersonalGraphVaultRepository
import com.sermilion.personalgraph.domain.capture.CaptureObservationArgs
import com.sermilion.personalgraph.domain.capture.CaptureObservationDecision
import com.sermilion.personalgraph.domain.capture.CaptureObservationKind
import com.sermilion.personalgraph.domain.capture.CaptureObservationResult
import com.sermilion.personalgraph.domain.capture.CaptureResult
import com.sermilion.personalgraph.domain.capture.SubjectHubStatus
import com.sermilion.personalgraph.domain.capture.WriteEpisodeArgs
import com.sermilion.personalgraph.domain.model.Confidence
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.EpisodeType
import com.sermilion.personalgraph.domain.model.Intensity
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.StateCategory
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import com.sermilion.personalgraph.testing.NoOpGraphIndexInvalidator
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

class PersonalGraphVaultCaptureEpisodeServiceTest :
  FunSpec({

    fun newService(): Pair<PersonalGraphVaultCaptureService, VaultRepository> {
      val repo = mockk<VaultRepository>()
      coEvery { repo.findNode(any()) } returns null
      return PersonalGraphVaultCaptureService(repo, fixedClock()) to repo
    }

    fun newRepositoryService(): Pair<PersonalGraphVaultCaptureService, PersonalGraphVaultRepository> {
      val tempDir = Files.createTempDirectory("capture-episode-")
      val repository = PersonalGraphVaultRepository(
        vaultRoot = tempDir,
        dispatcherProvider = TestDispatcherProvider(),
        codec = MarkdownFrontmatterCodec(),
        pathResolver = VaultPathResolver(),
        graphIndexInvalidator = NoOpGraphIndexInvalidator,
      )
      return PersonalGraphVaultCaptureService(repository, fixedClock()) to repository
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
          category = null,
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
      val captured = mutableListOf<VaultNode>()
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
      captured.filterIsInstance<EpisodeNode>().last().body shouldBe
        "[[domains/work/capmo/events/design-review]]\n[[domains/work/capmo/subjects/build-pipeline]]\n"
    }

    test("writeEpisode filters subject hub self-links from new subject hubs") {
      val (service, repo) = newService()
      val captured = mutableListOf<VaultNode>()
      coEvery { repo.writeNode(capture(captured)) } returns WriteOutcome.Applied
      coEvery { repo.findNode(NodeId("domains/work/capmo/subjects/build-pipeline")) } returns null
      coEvery { repo.findSubjectHub(any(), any(), any()) } returns null

      service.writeEpisode(
        WriteEpisodeArgs(
          id = "design-review",
          date = VaultNodeFixtures.episodeInstant,
          episodeType = EpisodeType.Decision,
          domain = "work/capmo",
          topic = "Build Pipeline",
          intensity = Intensity.Medium,
          body = "Settled on one deployment workflow.\n",
          linked = listOf(NodeId("domains/work/capmo/subjects/build-pipeline")),
          sensitive = false,
        ),
      ).shouldBeInstanceOf<CaptureResult.Created>()

      captured.filterIsInstance<SubjectNode>().single().links.map { it.value } shouldBe listOf(
        "domains/work/capmo/events/design-review",
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
        links = listOf(NodeId("domains/work/capmo/subjects/build-pipeline")),
        evidenceCount = 1,
        sourceIds = listOf(NodeId("domains/work/capmo/events/older")),
      )
      val captured = mutableListOf<VaultNode>()
      coEvery { repo.findSubjectHub("work/capmo", "build-pipeline", any()) } returns existing
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
      writtenSubject.links.map { it.value } shouldBe listOf("domains/work/capmo/events/design-review")
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

    test("writeEpisode preserves explicit canonical episode paths") {
      val (service, repo) = newService()
      val captured = mutableListOf<VaultNode>()
      coEvery { repo.writeNode(capture(captured)) } returns WriteOutcome.Applied
      coEvery { repo.findSubjectHub(any(), any(), any()) } returns null
      val explicitId = "domains/work/capmo/events/this-long-canonical-episode-id-is-caller-owned-and-preserved"

      val result = service.writeEpisode(
        WriteEpisodeArgs(
          id = explicitId,
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
      result.id.value shouldBe explicitId
      captured.filterIsInstance<EpisodeNode>().first().id.value shouldBe explicitId
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

    test("captureObservation generates bounded episode, subject, and timeline ids from long topics") {
      val (service, repo) = newService()
      val id = "domains/work/personal-graph/events/personal-graph-candidate-observation-capture-routing-topic-generated"
      coEvery { repo.findNode(NodeId(id)) } returns null
      coEvery { repo.writeNode(any()) } returns WriteOutcome.Applied
      coEvery { repo.findSubjectHub(any(), any(), any()) } returns null

      val result = service.captureObservation(
        CaptureObservationArgs(
          observation = "Implemented complete candidate routing as an episode-shaped observation.",
          sourceContext = "personal-graph design session",
          suggestedKind = CaptureObservationKind.Episode,
          id = null,
          category = null,
          confidence = null,
          date = VaultNodeFixtures.episodeInstant,
          episodeType = EpisodeType.Decision,
          domain = "work/personal-graph",
          topic = "Personal graph candidate observation capture routing and topic generated filenames stay short",
          intensity = Intensity.Medium,
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureObservationResult.Decided>()
      val capture = result.captureResult.shouldBeInstanceOf<CaptureResult.Created>()
      capture.id.value shouldBe
        "domains/work/personal-graph/events/personal-graph-candidate-observation-capture-routing-topic-generated"
      capture.subjectHubId?.value shouldBe
        "domains/work/personal-graph/subjects/personal-graph-candidate-observation-capture-routing-and-topic"
      capture.backlinkId?.value shouldBe
        "timeline/2026-04/2026-04-24-personal-graph-candidate-observation-capture-routing-and-topic"
    }

    test("writeEpisode reuses bounded subject hubs for repeated long topics") {
      val (service, repo) = newRepositoryService()
      val topic = "Personal graph candidate observation capture routing and topic generated filenames stay short"

      service.writeEpisode(
        WriteEpisodeArgs(
          id = "first-long-topic-episode",
          date = VaultNodeFixtures.episodeInstant,
          episodeType = EpisodeType.Decision,
          domain = "work/personal-graph",
          topic = topic,
          intensity = Intensity.Medium,
          body = "First decision.\n",
          linked = emptyList(),
          sensitive = false,
        ),
      ).shouldBeInstanceOf<CaptureResult.Created>()

      val result = service.writeEpisode(
        WriteEpisodeArgs(
          id = "second-long-topic-episode",
          date = Instant.parse("2026-04-25T12:00:00Z"),
          episodeType = EpisodeType.Decision,
          domain = "work/personal-graph",
          topic = topic,
          intensity = Intensity.Medium,
          body = "Second decision.\n",
          linked = emptyList(),
          sensitive = false,
        ),
      ).shouldBeInstanceOf<CaptureResult.Created>()

      result.subjectHubStatus shouldBe SubjectHubStatus.Updated
      val hub = repo.findNode(
        NodeId("domains/work/personal-graph/subjects/personal-graph-candidate-observation-capture-routing-and-topic"),
      ).shouldBeInstanceOf<SubjectNode>()
      hub.evidenceCount shouldBe 2
      hub.sourceIds.map { it.value } shouldBe listOf(
        "domains/work/personal-graph/events/first-long-topic-episode",
        "domains/work/personal-graph/events/second-long-topic-episode",
      )
      hub.body shouldContain "[[domains/work/personal-graph/events/first-long-topic-episode]]"
      hub.body shouldContain "[[domains/work/personal-graph/events/second-long-topic-episode]]"
    }

    test("captureObservation stages low-confidence complete episode candidates") {
      val (service, repo) = newService()
      val captured = slot<StateNode>()
      coEvery { repo.writeNode(capture(captured)) } returns WriteOutcome.Applied

      val result = service.captureObservation(
        CaptureObservationArgs(
          observation = "Decided tentative candidate routing during a low-confidence session.",
          sourceContext = "uncertain personal-graph design session",
          suggestedKind = CaptureObservationKind.Episode,
          id = "tentative-candidate-routing",
          category = null,
          confidence = Confidence.Low,
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
      result.decision shouldBe CaptureObservationDecision.StagedObservation
      result.reason shouldBe "low_confidence_candidate"
      captured.captured.id.value shouldBe "staging/observations/tentative-candidate-routing"
      coVerify(exactly = 0) { repo.findSubjectHub(any(), any(), any()) }
    }

    test("captureObservation stages event-like observations without durable state structure") {
      val (service, repo) = newService()
      val captured = slot<StateNode>()
      coEvery { repo.writeNode(capture(captured)) } returns WriteOutcome.Applied

      val result = service.captureObservation(
        CaptureObservationArgs(
          observation = "Decided GP-6 vault cleanup during today's session.",
          sourceContext = "implementation session",
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
      result.decision shouldBe CaptureObservationDecision.StagedObservation
      captured.captured.id.value shouldBe "staging/observations/decided-gp-6-vault-cleanup-during-today-s"
    }

    test("event-like detection uses token matching") {
      looksEventLike("Fixed a regression during capture routing.") shouldBe true
      looksEventLike("Updated fixtures during capture routing.") shouldBe false
    }
  })

private fun fixedClock(): Clock = object : Clock {
  override fun now(): Instant = Instant.parse("2026-04-25T10:00:00Z")
}
