package com.sermilion.personalgraph.data.capture

import com.sermilion.personalgraph.data.codec.MarkdownFrontmatterCodec
import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.data.repository.PersonalGraphVaultRepository
import com.sermilion.personalgraph.domain.capture.CaptureResult
import com.sermilion.personalgraph.domain.capture.FlagSensitiveArgs
import com.sermilion.personalgraph.domain.capture.PayloadKind
import com.sermilion.personalgraph.domain.capture.WriteStateArgs
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.Confidence
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.StateCategory
import com.sermilion.personalgraph.domain.model.StateNode
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

class PersonalGraphVaultCaptureServiceTest :
  FunSpec({

    fun newService(): Pair<PersonalGraphVaultCaptureService, VaultRepository> {
      val repo = mockk<VaultRepository>()
      val clock = fixedClock()
      coEvery { repo.findNode(any()) } returns null
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
        graphIndexInvalidator = NoOpGraphIndexInvalidator,
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

    test("writeStateObservation slugifies bare caller ids without word bounding") {
      val (service, repo) = newService()
      val captured = slot<StateNode>()
      coEvery { repo.writeNode(capture(captured)) } returns WriteOutcome.Applied

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = "this is a very long caller provided identifier that should not be bounded",
          category = StateCategory.Knowledge,
          confidence = Confidence.Medium,
          body = "knowledge body",
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.Created>()
      captured.captured.id.value shouldBe
        "state/knowledge/this-is-a-very-long-caller-provided-identifier-that-should-not-be-bounded"
    }

    test("writeStateObservation preserves explicit canonical state paths") {
      val (service, repo) = newService()
      val captured = slot<StateNode>()
      coEvery { repo.writeNode(capture(captured)) } returns WriteOutcome.Applied
      val explicitId = "state/knowledge/this-long-canonical-id-is-caller-owned-and-preserved"

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = explicitId,
          category = StateCategory.Knowledge,
          confidence = Confidence.Medium,
          body = "knowledge body",
          links = emptyList(),
          sensitive = false,
        ),
      )

      result.shouldBeInstanceOf<CaptureResult.Created>()
      captured.captured.id.value shouldBe explicitId
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

    test("writeStateObservation archives previous node version before replacing the same graph path") {
      val (service, repo) = newRepositoryService()

      service.writeStateObservation(
        WriteStateArgs(
          id = "memory-policy",
          category = StateCategory.Preference,
          confidence = Confidence.High,
          body = "Old policy body.",
          links = emptyList(),
          sensitive = false,
        ),
      ).shouldBeInstanceOf<CaptureResult.Created>()

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = "memory-policy",
          category = StateCategory.Preference,
          confidence = Confidence.High,
          body = "New policy body.",
          links = emptyList(),
          sensitive = false,
        ),
      ).shouldBeInstanceOf<CaptureResult.Created>()

      result.id.value shouldBe "state/preferences/memory-policy"
      result.archivedIds.single().value.startsWith(
        "${VaultLayout.BRANCH_OUTDATED_RESOLVED}/state/preferences/memory-policy/",
      ) shouldBe true
      repo.findNode(NodeId("state/preferences/memory-policy"))
        .shouldBeInstanceOf<StateNode>()
        .body shouldBe "New policy body."
      val archived = repo.listNodesInBranch("${VaultLayout.BRANCH_OUTDATED_RESOLVED}/state/preferences/memory-policy")
        .single()
        .shouldBeInstanceOf<StateNode>()
      archived.body shouldContain "Archived from `state/preferences/memory-policy`"
      archived.body shouldContain "Superseded by `state/preferences/memory-policy`"
      archived.body shouldContain "Old policy body."
    }

    test("writeStateObservation does not copy sensitive staging replacements into readable archive") {
      val (service, repo) = newRepositoryService()

      service.writeStateObservation(
        WriteStateArgs(
          id = "private-note",
          category = StateCategory.Knowledge,
          confidence = Confidence.Low,
          body = "Old sensitive body.",
          links = emptyList(),
          sensitive = true,
        ),
      ).shouldBeInstanceOf<CaptureResult.Created>()

      val result = service.writeStateObservation(
        WriteStateArgs(
          id = "private-note",
          category = StateCategory.Knowledge,
          confidence = Confidence.Low,
          body = "New sensitive body.",
          links = emptyList(),
          sensitive = true,
        ),
      ).shouldBeInstanceOf<CaptureResult.Created>()

      result.archivedIds shouldBe emptyList()
      repo.listNodesInBranch(VaultLayout.BRANCH_OUTDATED_RESOLVED) shouldBe emptyList()
      repo.findNode(NodeId("staging/sensitive/private-note"))
        .shouldBeInstanceOf<StateNode>()
        .body shouldBe "New sensitive body."
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
  })

private fun fixedClock(): Clock = object : Clock {
  override fun now(): Instant = Instant.parse("2026-04-25T10:00:00Z")
}
