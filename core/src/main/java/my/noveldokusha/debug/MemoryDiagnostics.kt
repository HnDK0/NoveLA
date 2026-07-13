package my.noveldokusha.debug

import android.os.Debug
import timber.log.Timber

object MemoryDiagnostics {

    fun logMemoryStats(tag: String = "MemoryDiagnostics") {
        val nativeHeapSize = Debug.getNativeHeapSize()
        val nativeHeapAllocated = Debug.getNativeHeapAllocatedSize()
        val nativeHeapFree = Debug.getNativeHeapFreeSize()

        val rt = Runtime.getRuntime()
        val jvmMax = rt.maxMemory()
        val jvmTotal = rt.totalMemory()
        val jvmFree = rt.freeMemory()
        val jvmUsed = jvmTotal - jvmFree

        Timber.tag(tag).d(
            "Native: alloc=%.1fMB / free=%.1fMB / reserved=%.1fMB | " +
            "JVM: used=%.1fMB / total=%.1fMB / max=%.1fMB",
            nativeHeapAllocated / 1_000_000.0,
            nativeHeapFree / 1_000_000.0,
            nativeHeapSize / 1_000_000.0,
            jvmUsed / 1_000_000.0,
            jvmTotal / 1_000_000.0,
            jvmMax / 1_000_000.0
        )
    }
}
