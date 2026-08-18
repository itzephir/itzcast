package dev.itzcast.platform

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories

class BundledExtensionInstaller(
    private val root: Path,
    private val openArchive: () -> InputStream? = {
        BundledExtensionInstaller::class.java.getResourceAsStream("/itzcast/bundled-extensions.zip")
    },
) {
    fun install(): Result<Unit> = runCatching {
        val extensionsRoot = root.resolve("extensions").createDirectories().toAbsolutePath().normalize()
        val archive = checkNotNull(openArchive()) { "Bundled extension archive is missing" }

        archive.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = extensionsRoot.resolve(entry.name).normalize()
                    require(target.startsWith(extensionsRoot)) { "Unsafe bundled extension path: ${entry.name}" }
                    if (entry.isDirectory) {
                        target.createDirectories()
                    } else {
                        target.parent?.createDirectories()
                        val temporary = target.resolveSibling("${target.fileName}.tmp")
                        Files.copy(zip, temporary, StandardCopyOption.REPLACE_EXISTING)
                        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                    zip.closeEntry()
                }
            }
        }
    }
}
