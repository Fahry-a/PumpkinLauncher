package com.blizzardfire.pumpkinlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ServerManagerFileOpsTest {
    private fun tempDir(): File = Files.createTempDirectory("pumpkin-fs").toFile()

    @Test
    fun copyRecursivelyCopiesNestedTreeWithoutTouchingSource() {
        val root = tempDir()
        try {
            val source = File(root, "server").apply { mkdirs() }
            File(source, "world").apply { mkdirs() }
            File(source, "world/level.dat").writeText("data")
            File(source, "server.properties").writeText("motd=hi")

            val dest = File(root, "copy")
            ServerManagerActivity.copyRecursively(source, dest)

            assertEquals("data", File(dest, "world/level.dat").readText())
            assertEquals("motd=hi", File(dest, "server.properties").readText())
            assertTrue(File(source, "world/level.dat").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deleteRecursivelyRemovesNestedFilesAndReportsFailureOnMissing() {
        val root = tempDir()
        try {
            val target = File(root, "gone").apply { mkdirs() }
            File(target, "a/b.txt").apply { parentFile.mkdirs(); writeText("x") }

            ServerManagerActivity.deleteRecursively(target)
            assertFalse(target.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun renameToWithFallbackMovesFile() {
        val root = tempDir()
        try {
            val source = File(root, "old.txt").apply { writeText("hello") }
            val dest = File(root, "new.txt")

            assertTrue(ServerManagerActivity.renameToWithFallback(source, dest))
            assertEquals("hello", dest.readText())
            assertFalse(source.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
