package com.kim.austopo.download

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

/**
 * Single serialisation point for all app-managed disk writes:
 *   - PinnedTileStore (offline_tiles_pinned/)
 *   - TransientTileStore (offline_tiles_transient/)
 *   - OfflineRegionStore (offline_regions.json)
 *   - BookmarkStore (bookmarks.json, later)
 *
 * Writes go through `withLock` so concurrent coroutines can't interleave
 * file writes, directory walks, or read-modify-write JSON updates. Reads
 * are unlocked: once a file exists on disk it's immutable (we rewrite via
 * atomic rename), so readers can't observe a torn write.
 */
class StorageManager(context: Context) {

    private val mutex = Mutex()
    val filesDir: File = context.filesDir

    val pinnedDir: File get() = File(filesDir, PINNED_DIR)
    val transientDir: File get() = File(filesDir, TRANSIENT_DIR)

    init {
        pinnedDir.mkdirs()
        transientDir.mkdirs()
    }

    /**
     * Run [block] under the storage mutex. Use for any read-modify-write
     * on files owned by the app (tile writes, JSON metadata updates, eviction).
     */
    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    /**
     * Atomically write [bytes] to [dest]: write to `<dest>.tmp` then rename.
     * Must be called inside [withLock].
     */
    fun writeAtomic(dest: File, bytes: ByteArray) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        FileOutputStream(tmp).use { it.write(bytes) }
        if (!tmp.renameTo(dest)) {
            // renameTo can fail on some filesystems if dest exists; fall back to delete+rename.
            dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                throw java.io.IOException("failed to atomically write $dest")
            }
        }
    }

    /** Atomically write [text] to [dest] as UTF-8. Must be called inside [withLock]. */
    fun writeAtomic(dest: File, text: String) = writeAtomic(dest, text.toByteArray(Charsets.UTF_8))

    /** Total bytes under [dir], recursively. Cheap-ish; used for usage readouts. */
    fun totalSize(dir: File): Long {
        if (!dir.exists()) return 0
        var size = 0L
        dir.walkTopDown().filter { it.isFile }.forEach { size += it.length() }
        return size
    }

    companion object {
        const val PINNED_DIR = "offline_tiles_pinned"
        const val TRANSIENT_DIR = "offline_tiles_transient"
        const val LEGACY_OFFLINE_DIR = "offline_tiles"
    }
}
