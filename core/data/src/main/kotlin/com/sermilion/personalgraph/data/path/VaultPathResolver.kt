package com.sermilion.personalgraph.data.path

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.domain.model.NodeId
import io.github.oshai.kotlinlogging.KotlinLogging
import me.tatarka.inject.annotations.Inject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

@AppScope
@Inject
class VaultPathResolver {

  private val logger = KotlinLogging.logger {}

  fun resolve(vaultRoot: Path, id: NodeId): Path {
    val relative = withMarkdownExtension(id.value)
    return vaultRoot.resolve(relative)
  }

  fun relativize(vaultRoot: Path, file: Path): NodeId? = try {
    val normalizedRoot = vaultRoot.toAbsolutePath().normalize()
    val normalizedFile = file.toAbsolutePath().normalize()
    if (!normalizedFile.startsWith(normalizedRoot)) {
      null
    } else {
      val relative = normalizedRoot.relativize(normalizedFile).toString().replace('\\', '/')
      val withoutExt = relative.removeSuffix(MARKDOWN_EXTENSION)
      runCatching { NodeId(withoutExt) }.getOrNull()
    }
  } catch (e: InvalidPathException) {
    logger.warn(e) { "Failed to relativize file=$file against vaultRoot=$vaultRoot" }
    null
  } catch (e: IOException) {
    logger.warn(e) { "Failed to relativize file=$file against vaultRoot=$vaultRoot" }
    null
  } catch (e: SecurityException) {
    logger.warn(e) { "Permission denied while relativizing file=$file against vaultRoot=$vaultRoot" }
    null
  }

  fun assertWithinVault(vaultRoot: Path, candidate: Path): Boolean = try {
    val resolvedRoot = vaultRoot.toRealPath()
    val normalizedRoot = vaultRoot.toAbsolutePath().normalize()
    val absoluteCandidate = candidate.toAbsolutePath().normalize()
    val realPrefix = deepestExistingRealPath(absoluteCandidate) ?: return false
    if (!realPrefix.startsWith(resolvedRoot)) return false
    val stopAt = if (absoluteCandidate.startsWith(normalizedRoot)) normalizedRoot else resolvedRoot
    if (existingPrefixContainsSymlink(absoluteCandidate, stopAt)) return false
    true
  } catch (e: IOException) {
    logger.warn(e) { "Failed to assert vault containment for candidate=$candidate" }
    false
  } catch (e: SecurityException) {
    logger.warn(e) { "Permission denied asserting vault containment for candidate=$candidate" }
    false
  } catch (e: InvalidPathException) {
    logger.warn(e) { "Invalid path asserting vault containment for candidate=$candidate" }
    false
  }

  private fun deepestExistingRealPath(absoluteCandidate: Path): Path? {
    var probe: Path? = absoluteCandidate
    while (probe != null && !Files.exists(probe)) {
      val parent = probe.parent ?: return null
      if (parent == probe) return null
      probe = parent
    }
    return runCatching { probe?.toRealPath() }.getOrNull()
  }

  private fun existingPrefixContainsSymlink(absoluteCandidate: Path, normalizedVaultRoot: Path): Boolean {
    val ascended = ascendToFirstExisting(absoluteCandidate, normalizedVaultRoot) ?: return true
    if (ascended == normalizedVaultRoot) return false
    return chainContainsSymlink(ascended, normalizedVaultRoot)
  }

  private fun ascendToFirstExisting(start: Path, stopAt: Path): Path? {
    var current: Path = start
    while (current != stopAt && !Files.exists(current)) {
      val parent = current.parent ?: return null
      if (parent == current) return null
      current = parent
    }
    return current
  }

  private fun chainContainsSymlink(start: Path, stopAt: Path): Boolean {
    var current: Path = start
    var found = false
    while (current != stopAt && !found) {
      if (Files.isSymbolicLink(current)) {
        found = true
      } else {
        val parent = current.parent
        if (parent == null || parent == current) {
          found = true
        } else {
          current = parent
        }
      }
    }
    return found
  }

  private fun withMarkdownExtension(value: String): String = if (value.endsWith(MARKDOWN_EXTENSION)) {
    value
  } else {
    value +
      MARKDOWN_EXTENSION
  }

  companion object {
    const val MARKDOWN_EXTENSION: String = ".md"
  }
}
