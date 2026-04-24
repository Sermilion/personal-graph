package com.sermilion.personalgraph.domain.repository

sealed interface WriteOutcome {
  data object Applied : WriteOutcome
  data object NotFound : WriteOutcome
  data object Conflict : WriteOutcome
  data class Failed(val reason: String) : WriteOutcome
}
