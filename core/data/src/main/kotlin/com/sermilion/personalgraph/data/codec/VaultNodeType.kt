package com.sermilion.personalgraph.data.codec

import com.sermilion.personalgraph.data.model.EmotionalStateNodeFrontmatterDataModel
import com.sermilion.personalgraph.data.model.EpisodeNodeFrontmatterDataModel
import com.sermilion.personalgraph.data.model.PatternNodeFrontmatterDataModel
import com.sermilion.personalgraph.data.model.StateNodeFrontmatterDataModel
import com.sermilion.personalgraph.data.model.SubjectNodeFrontmatterDataModel

enum class VaultNodeType(val discriminator: String) {
  State(StateNodeFrontmatterDataModel.NODE_TYPE),
  Episode(EpisodeNodeFrontmatterDataModel.NODE_TYPE),
  Pattern(PatternNodeFrontmatterDataModel.NODE_TYPE),
  Subject(SubjectNodeFrontmatterDataModel.NODE_TYPE),
  EmotionalState(EmotionalStateNodeFrontmatterDataModel.NODE_TYPE),
  ;

  companion object {
    fun fromDiscriminator(value: String): VaultNodeType? = entries.firstOrNull { it.discriminator == value }
  }
}
