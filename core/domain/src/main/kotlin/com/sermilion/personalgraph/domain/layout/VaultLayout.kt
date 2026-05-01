package com.sermilion.personalgraph.domain.layout

object VaultLayout {

  const val BRANCH_STATE: String = "state"
  const val BRANCH_DOMAINS: String = "domains"
  const val BRANCH_PATTERNS: String = "patterns"
  const val BRANCH_EMOTIONAL_STATES: String = "emotional-states"
  const val BRANCH_TIMELINE: String = "timeline"
  const val BRANCH_STAGING: String = "staging"
  const val BRANCH_OUTDATED: String = "outdated"
  const val BRANCH_PEOPLE: String = "people"

  const val SUB_STATE_PREFERENCES: String = "preferences"
  const val SUB_STATE_ROLES: String = "roles"
  const val SUB_STATE_KNOWLEDGE: String = "knowledge"
  const val SUB_DOMAIN_EVENTS: String = "events"
  const val SUB_DOMAIN_SUBJECTS: String = "subjects"

  const val SUB_STAGING_OBSERVATIONS: String = "observations"
  const val SUB_STAGING_SENSITIVE: String = "sensitive"
  const val SUB_OUTDATED_RESOLVED: String = "resolved"

  const val BRANCH_STATE_PREFERENCES: String = "$BRANCH_STATE/$SUB_STATE_PREFERENCES"
  const val BRANCH_STATE_ROLES: String = "$BRANCH_STATE/$SUB_STATE_ROLES"
  const val BRANCH_STATE_KNOWLEDGE: String = "$BRANCH_STATE/$SUB_STATE_KNOWLEDGE"

  const val BRANCH_STAGING_OBSERVATIONS: String = "$BRANCH_STAGING/$SUB_STAGING_OBSERVATIONS"
  const val BRANCH_STAGING_SENSITIVE: String = "$BRANCH_STAGING/$SUB_STAGING_SENSITIVE"
  const val BRANCH_OUTDATED_RESOLVED: String = "$BRANCH_OUTDATED/$SUB_OUTDATED_RESOLVED"

  const val BRAIAN_FILENAME: String = "Braian.md"

  private val SCAFFOLD_DOMAIN_KEYS: List<String> = listOf(
    "work/capmo",
    "work/reddit",
    "work/skill-bill",
    "work/readian",
    "work/context-app",
    "work/personal-graph",
    "personal",
    "personal-graph",
    "creative",
  )

  val SCAFFOLD_DIRECTORIES: List<String> = listOf(
    BRANCH_STATE_PREFERENCES,
    BRANCH_STATE_ROLES,
    BRANCH_STATE_KNOWLEDGE,
  ) + SCAFFOLD_DOMAIN_KEYS.flatMap { domainKey ->
    listOf(
      domainEvents(domainKey),
      domainSubjects(domainKey),
    )
  } + listOf(
    BRANCH_PATTERNS,
    BRANCH_EMOTIONAL_STATES,
    BRANCH_TIMELINE,
    BRANCH_STAGING_OBSERVATIONS,
    BRANCH_STAGING_SENSITIVE,
    BRANCH_OUTDATED_RESOLVED,
    BRANCH_PEOPLE,
  )

  val SCAFFOLD_DOMAIN_INDEXES: List<String> = listOf(
    "$BRANCH_DOMAINS/index",
    domainIndex("work"),
  ) + SCAFFOLD_DOMAIN_KEYS.map(::domainIndex)

  fun stateBranch(category: StateCategoryDirectory): String = when (category) {
    StateCategoryDirectory.Preferences -> BRANCH_STATE_PREFERENCES
    StateCategoryDirectory.Roles -> BRANCH_STATE_ROLES
    StateCategoryDirectory.Knowledge -> BRANCH_STATE_KNOWLEDGE
  }

  fun staging(kind: StagingKind): String = when (kind) {
    StagingKind.Observations -> BRANCH_STAGING_OBSERVATIONS
    StagingKind.Sensitive -> BRANCH_STAGING_SENSITIVE
  }

  fun domain(domainKey: String): String = "$BRANCH_DOMAINS/$domainKey"

  fun domainIndex(domainKey: String): String = "${domain(domainKey)}/index"

  fun domainEvents(domainKey: String): String = "${domain(domainKey)}/$SUB_DOMAIN_EVENTS"

  fun domainSubjects(domainKey: String): String = "${domain(domainKey)}/$SUB_DOMAIN_SUBJECTS"

  fun subjectHub(domainKey: String, subjectKey: String): String = "${domainSubjects(domainKey)}/$subjectKey"

  fun timeline(yearMonth: String): String = "$BRANCH_TIMELINE/$yearMonth"

  enum class StateCategoryDirectory {
    Preferences,
    Roles,
    Knowledge,
  }

  enum class StagingKind {
    Observations,
    Sensitive,
  }
}
