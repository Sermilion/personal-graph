package com.sermilion.personalgraph.domain.layout

object VaultPolicy {

  val WHITELISTED_WRITE_BRANCH_PREFIXES: Set<String> = setOf(
    VaultLayout.BRANCH_STATE,
    VaultLayout.BRANCH_DOMAINS,
    VaultLayout.BRANCH_PATTERNS,
    VaultLayout.BRANCH_EMOTIONAL_STATES,
    VaultLayout.BRANCH_TIMELINE,
    VaultLayout.BRANCH_STAGING,
    VaultLayout.BRANCH_OUTDATED,
  )

  val READ_BLOCKED_BRANCH_PREFIXES: Set<String> = setOf(
    VaultLayout.BRANCH_PEOPLE,
  )

  val WHITELISTED_READ_BRANCH_PREFIXES: Set<String> = setOf(
    VaultLayout.BRANCH_STATE,
    VaultLayout.BRANCH_DOMAINS,
    VaultLayout.BRANCH_PATTERNS,
    VaultLayout.BRANCH_EMOTIONAL_STATES,
    VaultLayout.BRANCH_TIMELINE,
    VaultLayout.BRANCH_STAGING,
    VaultLayout.BRANCH_OUTDATED,
  )

  fun isWriteAllowed(branchOrPath: String): Boolean = matchesPrefix(branchOrPath, WHITELISTED_WRITE_BRANCH_PREFIXES)

  fun isReadAllowed(branchOrPath: String): Boolean {
    if (matchesPrefix(branchOrPath, READ_BLOCKED_BRANCH_PREFIXES)) return false
    return matchesPrefix(branchOrPath, WHITELISTED_READ_BRANCH_PREFIXES)
  }

  fun isReadBlocked(branchOrPath: String): Boolean = matchesPrefix(branchOrPath, READ_BLOCKED_BRANCH_PREFIXES)

  private fun matchesPrefix(value: String, prefixes: Set<String>): Boolean {
    val normalized = value.trim('/')
    return prefixes.any { prefix ->
      normalized == prefix || normalized.startsWith("$prefix/")
    }
  }
}
