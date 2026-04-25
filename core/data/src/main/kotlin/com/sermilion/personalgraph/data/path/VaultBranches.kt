package com.sermilion.personalgraph.data.path

import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.layout.VaultPolicy

object VaultBranches {

  const val STATE: String = VaultLayout.BRANCH_STATE
  const val DOMAINS: String = VaultLayout.BRANCH_DOMAINS
  const val PATTERNS: String = VaultLayout.BRANCH_PATTERNS
  const val EMOTIONAL_STATES: String = VaultLayout.BRANCH_EMOTIONAL_STATES
  const val TIMELINE: String = VaultLayout.BRANCH_TIMELINE
  const val STAGING: String = VaultLayout.BRANCH_STAGING
  const val STAGING_SENSITIVE: String = VaultLayout.BRANCH_STAGING_SENSITIVE
  const val STAGING_OBSERVATIONS: String = VaultLayout.BRANCH_STAGING_OBSERVATIONS
  const val PEOPLE: String = VaultLayout.BRANCH_PEOPLE

  val WHITELISTED_WRITE_BRANCH_PREFIXES: Set<String> = VaultPolicy.WHITELISTED_WRITE_BRANCH_PREFIXES
  val READ_BLOCKED_BRANCH_PREFIXES: Set<String> = VaultPolicy.READ_BLOCKED_BRANCH_PREFIXES
  val WHITELISTED_READ_BRANCH_PREFIXES: Set<String> = VaultPolicy.WHITELISTED_READ_BRANCH_PREFIXES

  fun isWriteAllowed(branchOrPath: String): Boolean = VaultPolicy.isWriteAllowed(branchOrPath)

  fun isReadAllowed(branchOrPath: String): Boolean = VaultPolicy.isReadAllowed(branchOrPath)

  fun isReadBlocked(branchOrPath: String): Boolean = VaultPolicy.isReadBlocked(branchOrPath)
}
