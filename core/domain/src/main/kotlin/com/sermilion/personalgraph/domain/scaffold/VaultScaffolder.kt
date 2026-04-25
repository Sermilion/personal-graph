package com.sermilion.personalgraph.domain.scaffold

import com.sermilion.personalgraph.domain.repository.WriteOutcome

interface VaultScaffolder {
  suspend fun scaffold(): WriteOutcome
}
