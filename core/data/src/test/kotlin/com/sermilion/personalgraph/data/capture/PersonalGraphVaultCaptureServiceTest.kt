package com.sermilion.personalgraph.data.capture

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
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class PersonalGraphVaultCaptureServiceTest :
  FunSpec({

    fun newService(): Pair<PersonalGraphVaultCaptureService, VaultRepository> {
      val repo = mockk<VaultRepository>()
      val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-04-25T10:00:00Z")
      }
      val service = PersonalGraphVaultCaptureService(repo, clock)
      return service to repo
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
