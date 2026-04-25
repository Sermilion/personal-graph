package com.sermilion.personalgraph.data.capture

import com.sermilion.personalgraph.domain.capture.CaptureResult
import com.sermilion.personalgraph.domain.capture.FlagSensitiveArgs
import com.sermilion.personalgraph.domain.capture.PayloadKind
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import com.sermilion.personalgraph.testing.VaultNodeFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
  })
