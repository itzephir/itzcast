package dev.itzcast.platform

import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledExtensionInstallerTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun installsAndUpdatesOfficialExtensions() {
        val first = archive("itzcast.youtube/manifest.toml" to "version = \"0.0.1\"")
        BundledExtensionInstaller(root) { ByteArrayInputStream(first) }.install().getOrThrow()
        val manifest = root.resolve("extensions/itzcast.youtube/manifest.toml")
        assertEquals("version = \"0.0.1\"", manifest.readText())

        val update = archive("itzcast.youtube/manifest.toml" to "version = \"0.0.2\"")
        BundledExtensionInstaller(root) { ByteArrayInputStream(update) }.install().getOrThrow()
        assertEquals("version = \"0.0.2\"", manifest.readText())
    }

    @Test
    fun rejectsPathsOutsideExtensionDirectory() {
        val result = BundledExtensionInstaller(root) {
            ByteArrayInputStream(archive("../settings.toml" to "unsafe = true"))
        }.install()

        assertTrue(result.isFailure)
    }

    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
