package com.sermilion.personalgraph.domain.model

import kotlin.jvm.JvmInline

@JvmInline
value class NodeId(val value: String) {
  init {
    require(value.isNotBlank()) { "NodeId cannot be blank" }
    require(!value.startsWith("/") && !value.startsWith("\\")) {
      "NodeId cannot be absolute: $value"
    }
    require(!value.contains(':')) { "NodeId cannot contain drive or scheme separators: $value" }
    require(!value.contains('\u0000')) { "NodeId cannot contain NUL bytes" }
    require(value.split('/', '\\').none { it == ".." || it == "." }) {
      "NodeId cannot contain '.' or '..' path segments: $value"
    }
    require(value == value.trim()) { "NodeId cannot have leading or trailing whitespace: $value" }
    require(!value.contains("//") && !value.contains("\\\\")) {
      "NodeId cannot contain empty path segments: $value"
    }
  }
}
