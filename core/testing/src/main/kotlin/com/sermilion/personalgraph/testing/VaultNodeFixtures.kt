package com.sermilion.personalgraph.testing

import com.sermilion.personalgraph.domain.model.Confidence
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.StateCategory
import com.sermilion.personalgraph.domain.model.StateNode
import kotlinx.datetime.Instant

object VaultNodeFixtures {
  val sampleInstant: Instant = Instant.parse("2026-04-24T12:00:00Z")

  fun stateNode(
    id: String = "state/preferences/sample",
    body: String = "",
    category: StateCategory = StateCategory.Preference,
    confidence: Confidence = Confidence.High,
  ): StateNode = StateNode(
    id = NodeId(id),
    createdAt = sampleInstant,
    updatedAt = sampleInstant,
    body = body,
    links = emptyList(),
    category = category,
    confidence = confidence,
  )
}
