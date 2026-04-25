package com.sermilion.personalgraph.data.repository

import com.sermilion.personalgraph.data.codec.MarkdownFrontmatterCodec
import com.sermilion.personalgraph.data.path.VaultPathResolver
import com.sermilion.personalgraph.domain.model.VaultNode
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet

internal const val MARKDOWN_EXTENSION_VALUE: String = ".md"
internal const val MAX_VAULT_FILE_SIZE_BYTES: Long = 1L * 1024L * 1024L

internal fun createDirectoriesTracking(target: Path): List<Path> {
  val created = mutableListOf<Path>()
  val parts = mutableListOf<Path>()
  var probe: Path? = target.toAbsolutePath().normalize()
  while (probe != null && !Files.exists(probe)) {
    parts.add(0, probe)
    probe = probe.parent
  }
  Files.createDirectories(target)
  for (dir in parts) {
    if (Files.exists(dir)) created.add(dir)
  }
  return created
}

internal fun Path.toRealPathOrSelf(): Path = try {
  this.toRealPath()
} catch (_: IOException) {
  this.toAbsolutePath().normalize()
}

internal fun Throwable.reasonString(): String = "${this::class.simpleName}: ${this.message.orEmpty()}"

internal fun ownerOnlyFilePermissions(): EnumSet<PosixFilePermission> = EnumSet.of(
  PosixFilePermission.OWNER_READ,
  PosixFilePermission.OWNER_WRITE,
)

internal fun ownerOnlyDirPermissions(): EnumSet<PosixFilePermission> = EnumSet.of(
  PosixFilePermission.OWNER_READ,
  PosixFilePermission.OWNER_WRITE,
  PosixFilePermission.OWNER_EXECUTE,
)

internal class VaultDecodeContext(
  val vaultRoot: Path,
  val pathResolver: VaultPathResolver,
  val codec: MarkdownFrontmatterCodec,
  val logger: KLogger,
)

internal suspend fun walkAndAccumulate(
  ctx: VaultDecodeContext,
  branchDir: Path,
  maxDepth: Int,
  maxResults: Int,
  results: MutableList<VaultNode>,
  accept: (VaultNode) -> Boolean,
) {
  Files.walk(branchDir, maxDepth).use { stream ->
    for (file in stream) {
      currentCoroutineContext().ensureActive()
      if (results.size >= maxResults) return@use
      val node = decodeMarkdownIfEligible(ctx, file) ?: continue
      if (accept(node)) results.add(node)
    }
  }
}

internal fun decodeMarkdownIfEligible(ctx: VaultDecodeContext, file: Path): VaultNode? = try {
  when {
    !Files.isRegularFile(file) -> null
    !file.fileName.toString().endsWith(MARKDOWN_EXTENSION_VALUE) -> null
    else -> decodeMarkdownFromFile(ctx, file)
  }
} catch (e: IOException) {
  ctx.logger.debug(e) { "Skipping unreadable file=$file" }
  null
} catch (e: SecurityException) {
  ctx.logger.debug(e) { "Skipping access-denied file=$file" }
  null
} catch (e: SerializationException) {
  ctx.logger.debug(e) { "Skipping undecodable file=$file" }
  null
}

private fun decodeMarkdownFromFile(ctx: VaultDecodeContext, file: Path): VaultNode? {
  val nodeId = ctx.pathResolver.relativize(ctx.vaultRoot, file) ?: return null
  if (Files.size(file) > MAX_VAULT_FILE_SIZE_BYTES) {
    ctx.logger.warn { "Skipping oversized file=$file size=${Files.size(file)} bytes" }
    return null
  }
  return ctx.codec.decode(nodeId, Files.readString(file))
}
