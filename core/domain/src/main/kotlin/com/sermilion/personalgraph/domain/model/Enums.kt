package com.sermilion.personalgraph.domain.model

enum class StateCategory {
  Preference,
  Role,
  Knowledge,
  Fact,
}

enum class Confidence {
  High,
  Medium,
  Low,
}

enum class EpisodeType {
  Purchase,
  AdviceSeeking,
  Research,
  DesignDoc,
  Question,
  PersonalStory,
  WorkInteraction,
  Decision,
}

enum class Intensity {
  Low,
  Medium,
  High,
}

enum class EmotionMarker {
  Frustration,
  Excitement,
  Anxiety,
  Curiosity,
  Disengagement,
  Satisfaction,
  Confusion,
}
