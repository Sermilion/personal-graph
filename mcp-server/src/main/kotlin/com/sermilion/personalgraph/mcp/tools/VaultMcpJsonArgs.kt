package com.sermilion.personalgraph.mcp.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal fun JsonObject.optionalStringArgument(key: String): Parsed<String?> = when (val element = this[key]) {
  null -> Parsed.Success(null)
  is JsonPrimitive -> {
    val content = element.contentOrNull
    if (element.isString && content != null) {
      Parsed.Success(content)
    } else {
      Parsed.Failure(invalidInputJson(key, REASON_INVALID))
    }
  }
  else -> Parsed.Failure(invalidInputJson(key, REASON_INVALID))
}

internal fun JsonObject.optionalIntArgument(key: String): Parsed<Int?> = when (val element = this[key]) {
  null -> Parsed.Success(null)
  is JsonPrimitive -> {
    val intValue = element.intOrNull
    if (!element.isString && intValue != null) {
      Parsed.Success(intValue)
    } else {
      Parsed.Failure(invalidInputJson(key, REASON_INVALID))
    }
  }
  else -> Parsed.Failure(invalidInputJson(key, REASON_INVALID))
}

internal fun JsonObject.nonNegativeIntArgument(key: String): Parsed<Int?> {
  val parsed = optionalIntArgument(key)
  return when (parsed) {
    is Parsed.Failure -> parsed
    is Parsed.Success -> {
      val value = parsed.value
      if (value != null && value < 0) {
        Parsed.Failure(invalidInputJson(key, REASON_INVALID))
      } else {
        parsed
      }
    }
  }
}

internal fun JsonObject.stringArrayArgument(key: String): Parsed<List<String>> {
  var error: JsonObject? = null
  val values = when (val element = this[key]) {
    null -> emptyList()
    is JsonArray -> buildList {
      for (entry in element) {
        val primitive = entry as? JsonPrimitive
        val content = primitive?.contentOrNull
        if (primitive?.isString == true && content != null) {
          add(content)
        } else {
          error = invalidInputJson(key, REASON_INVALID)
        }
      }
    }
    else -> {
      error = invalidInputJson(key, REASON_INVALID)
      emptyList()
    }
  }
  return error?.let { Parsed.Failure(it) } ?: Parsed.Success(values)
}

internal fun firstFailureJson(vararg parsed: Parsed<*>): JsonObject? {
  for (p in parsed) {
    if (p is Parsed.Failure) return p.json
  }
  return null
}

internal fun permissionDeniedReadBlocked(rawId: String): JsonObject = statusJson(
  ToolSchemas.STATUS_PERMISSION_DENIED,
  mapOf(
    ToolSchemas.KEY_PATH to rawId,
    ToolSchemas.KEY_REASON to PERMISSION_DENIED_PEOPLE,
  ),
)

internal fun permissionDeniedOutside(rawId: String): JsonObject = statusJson(
  ToolSchemas.STATUS_PERMISSION_DENIED,
  mapOf(
    ToolSchemas.KEY_PATH to rawId,
    ToolSchemas.KEY_REASON to PERMISSION_DENIED_OUTSIDE,
  ),
)
