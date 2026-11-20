package com.pharrlabs.voice.asr

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class ModelManagerTest {
    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private val goodBytes = ByteArray(50_000) { (it % 251).toByte() }
    private val wrongBytes = ByteArray(50_000) { ((it + 1) % 251).toByte() }

    private fun sha256(b: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Before fun setUp() {
        dir = Files.createTempDirectory("models").toFile()
        server = MockWebServer().also { it.start() }
    }

    @After fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    private fun serve(bytes: ByteArray) =
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(bytes)))

    private fun manager() = ModelManager(
        dir,
        mapOf("test" to ModelSpec(server.url("/model.bin").toString(), sha256(goodBytes), goodBytes.size.toLong()))
    )

    @Test fun goodChecksumKeepsFile() = runBlocking<Unit> {
        serve(goodBytes)
        val m = manager()
        val progress = mutableListOf<Float>()
        val result = m.download("test") { progress += it }
        assertTrue("download should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue(m.isInstalled("test"))
        assertEquals(sha256(goodBytes), sha256(result.getOrThrow().readBytes()))
        assertEquals(1f, progress.last(), 0.0001f)
        assertEquals("no partial files left", 0, dir.listFiles()!!.count { it.name.endsWith(".part") })
    }

    @Test fun badChecksumDeletesFile() = runBlocking<Unit> {
        serve(wrongBytes)
        val m = manager()
        val result = m.download("test") { }
        assertTrue(result.isFailure)
        assertFalse(m.isInstalled("test"))
        assertFalse(m.modelFile("test").exists())
        assertEquals("nothing left in the model dir", 0, dir.listFiles()!!.size)
    }

    @Test fun httpErrorFailsCleanly() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(404))
        val m = manager()
        assertTrue(m.download("test") { }.isFailure)
        assertFalse(m.isInstalled("test"))
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun unknownModelFails() = runBlocking<Unit> {
        assertTrue(manager().download("nope") { }.isFailure)
    }

    @Test fun defaultSpecsPinBothModels() {
        val specs = ModelManager.DEFAULT_SPECS
        assertEquals(190_098_681L, specs.getValue("small.en-q5_1").sizeBytes)
        assertEquals(59_721_011L, specs.getValue("base.en-q5_1").sizeBytes)
        assertEquals(64, specs.getValue("small.en-q5_1").sha256.length)
    }

    @Test fun removeDeletesAnInstalledModelAndReportsTheBytesFreed() {
        val m = manager()
        m.modelFile("test").writeBytes(goodBytes)
        assertTrue(m.isInstalled("test"))
        assertEquals(goodBytes.size.toLong(), m.remove("test"))
        assertFalse(m.modelFile("test").exists())
        assertFalse(m.isInstalled("test"))
    }

    @Test fun removeAlsoClearsALeftoverPartialDownload() {
        val m = manager()
        File(dir, "ggml-test.bin.part").writeBytes(ByteArray(1234))
        assertEquals(1234L, m.remove("test"))
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun removeOfSomethingNotDownloadedFreesNothing() {
        assertEquals(0L, manager().remove("test"))
    }

    @Test fun removeLeavesOtherModelsAlone() {
        val m = manager()
        m.modelFile("test").writeBytes(goodBytes)
        File(dir, "ggml-other.bin").writeBytes(ByteArray(10))
        m.remove("test")
        assertTrue(File(dir, "ggml-other.bin").exists())
    }

    @Test fun removeRejectsNamesThatCouldEscapeTheModelFolder() {
        assertEquals(0L, manager().remove("../evil"))
    }

    @Test fun fiveModelsAreOffered() {
        assertEquals(
            listOf("base.en-q5_1", "small.en-q5_1", ModelManager.PARAKEET_V2, "medium.en-q5_0", "large-v3-turbo-q5_0"),
            com.pharrlabs.voice.settings.Prefs.MODELS.map { it.first },
        )
    }

    @Test fun everyOfferedModelHasAPinnedSpec() {
        for ((name, label) in com.pharrlabs.voice.settings.Prefs.MODELS) {
            val spec = ModelManager.DEFAULT_SPECS[name]
            assertNotNull("no spec for $name", spec)
            val files = if (spec!!.isMultiFile) spec.parts else listOf(ModelFile("ggml-$name.bin", spec.url, spec.sha256, spec.sizeBytes))
            for (f in files) {
                assertEquals("sha for ${f.name}", 64, f.sha256.length)
                assertTrue("hex sha for ${f.name}", f.sha256.all { it in '0'..'9' || it in 'a'..'f' })
                assertTrue("url for ${f.name}", f.url.endsWith(if (spec.isMultiFile) f.name else "ggml-$name.bin"))
                assertTrue("size for ${f.name}", f.sizeBytes > 1_000)
            }
            assertTrue("label for $name", label.isNotBlank())
        }
    }

    @Test fun retiredModelFilesAreDeleted() {
        val old = File(dir, "ggml-tiny.en-q5_1.bin").apply { writeBytes(ByteArray(5000)) }
        val part = File(dir, "ggml-tiny.en-q5_1.bin.part").apply { writeBytes(ByteArray(300)) }
        val tiny = File(dir, "ggml-medium.en-q5_0.bin").apply { writeBytes(ByteArray(700)) }
        val keep = File(dir, "ggml-small.en-q5_1.bin").apply { writeBytes(ByteArray(10)) }
        assertEquals(5300L, ModelManager(dir).removeRetired())
        assertFalse(old.exists()); assertFalse(part.exists()); assertTrue("current models are never removed", tiny.exists() && keep.exists())
    }

    @Test fun multiFileModelDownloadsEveryFileIntoItsFolder() = runBlocking<Unit> {
        val a = ByteArray(30_000) { (it % 7).toByte() }
        val b = ByteArray(20_000) { (it % 11).toByte() }
        serve(a); serve(b)
        val spec = ModelSpec("", "", 0, parts = listOf(
            ModelFile("a.onnx", server.url("/a").toString(), sha256(a), a.size.toLong()),
            ModelFile("b.txt", server.url("/b").toString(), sha256(b), b.size.toLong()),
        ))
        val m = ModelManager(dir, mapOf("multi" to spec))
        assertFalse(m.isInstalled("multi"))
        val progress = mutableListOf<Float>()
        val result = m.download("multi") { progress += it }
        assertTrue("download should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue(m.isInstalled("multi"))
        assertEquals(1f, progress.last(), 0.0001f)
        assertTrue("progress never goes backwards", progress.zipWithNext().all { (x, y) -> y >= x })
        assertEquals("no partial files left", 0, File(dir, "multi").listFiles()!!.count { it.name.endsWith(".part") })
        assertEquals(a.size + b.size.toLong(), m.remove("multi"))
        assertFalse(m.isInstalled("multi"))
        assertFalse(File(dir, "multi").exists())
    }

    @Test fun multiFileModelWithOneBadFileIsNotInstalled() = runBlocking<Unit> {
        val a = ByteArray(10_000) { 1 }
        serve(a); serve(ByteArray(10_000) { 2 })
        val spec = ModelSpec("", "", 0, parts = listOf(
            ModelFile("a.bin", server.url("/a").toString(), sha256(a), a.size.toLong()),
            ModelFile("b.bin", server.url("/b").toString(), sha256(ByteArray(10_000) { 3 }), 10_000L),
        ))
        val m = ModelManager(dir, mapOf("multi" to spec))
        assertTrue(m.download("multi") { }.isFailure)
        assertFalse("a half-downloaded multi-file model must not count as installed", m.isInstalled("multi"))
    }

    @Test fun pinnedSizesMatchTheHuggingFaceFiles() {
        val s = ModelManager.DEFAULT_SPECS
        assertEquals(59_721_011L, s.getValue("base.en-q5_1").sizeBytes)
        assertEquals(539_225_533L, s.getValue("medium.en-q5_0").sizeBytes)
        assertEquals(574_041_195L, s.getValue("large-v3-turbo-q5_0").sizeBytes)
    }

    @Test fun theDefaultModelIsTheRecommendedOne() {
        assertEquals(com.pharrlabs.voice.settings.Prefs.RECOMMENDED_MODEL, com.pharrlabs.voice.settings.Prefs.DEFAULT_MODEL)
    }

    @Test fun theDefaultModelIsStillOffered() {
        assertTrue(com.pharrlabs.voice.settings.Prefs.MODELS.any { it.first == com.pharrlabs.voice.settings.Prefs.DEFAULT_MODEL })
    }
}
