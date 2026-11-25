package dev.nexterm.session

/** How many one-second buckets the traffic chart keeps. */
const val HISTORY_SECONDS = 60

/** A frozen view of [SessionStats], safe to read from the UI. History arrays run oldest to newest. */
class StatsSnapshot(
    /** Time since the session started (up to the moment it ended, for an ended session). */
    val durationMs: Long,
    val rx: Long,
    val tx: Long,
    val errors: Int,
    /** Bytes received during the last full second. */
    val rxSpeed: Int,
    /** Time since the last byte moved either way; null when nothing has moved yet. */
    val sinceActivityMs: Long?,
    val rxHistory: IntArray,
    val txHistory: IntArray,
    /** Wall-clock time the session ended, null while it is running. */
    val endedAt: Long?,
)

/**
 * Counters of one session: bytes received and sent, errors, the moment of the last activity and the
 * bytes per second of the last [HISTORY_SECONDS] seconds. Written from the I/O threads, read by the UI.
 */
class SessionStats(private val clock: () -> Long = System::currentTimeMillis) {
    private val startedAt = clock()
    private var endedAt: Long? = null
    private var rx = 0L
    private var tx = 0L
    private var errors = 0
    private var lastActivity = 0L
    private val rxRing = IntArray(HISTORY_SECONDS)
    private val txRing = IntArray(HISTORY_SECONDS)
    /** Index (seconds since the start) of the newest bucket in the rings. */
    private var ringSec = 0

    private fun secondOf(time: Long) = ((time - startedAt) / 1000).toInt().coerceAtLeast(0)

    /** Moves the rings forward to second [sec], emptying the buckets that come into use. */
    private fun advance(sec: Int) {
        if (sec <= ringSec) return
        val steps = minOf(sec - ringSec, HISTORY_SECONDS)
        for (i in 1..steps) {
            val slot = (ringSec + i) % HISTORY_SECONDS
            rxRing[slot] = 0
            txRing[slot] = 0
        }
        ringSec = sec
    }

    @Synchronized
    fun addRx(bytes: Int) {
        if (endedAt != null || bytes <= 0) return
        val now = clock()
        advance(secondOf(now))
        rx += bytes
        rxRing[ringSec % HISTORY_SECONDS] += bytes
        lastActivity = now
    }

    @Synchronized
    fun addTx(bytes: Int) {
        if (endedAt != null || bytes <= 0) return
        val now = clock()
        advance(secondOf(now))
        tx += bytes
        txRing[ringSec % HISTORY_SECONDS] += bytes
        lastActivity = now
    }

    @Synchronized
    fun addError() {
        if (endedAt == null) errors++
    }

    /** "Reset counters": zero the byte and error counts. The chart and the duration keep going. */
    @Synchronized
    fun reset() {
        rx = 0; tx = 0; errors = 0
    }

    /** Freeze everything: the session is over. */
    @Synchronized
    fun end() {
        if (endedAt == null) {
            advance(secondOf(clock()))
            endedAt = clock()
        }
    }

    @Synchronized
    fun snapshot(): StatsSnapshot {
        val now = endedAt ?: clock()
        advance(secondOf(now))
        val rxHist = IntArray(HISTORY_SECONDS)
        val txHist = IntArray(HISTORY_SECONDS)
        for (k in 0 until HISTORY_SECONDS) {
            val sec = ringSec - (HISTORY_SECONDS - 1) + k
            if (sec >= 0) {
                rxHist[k] = rxRing[sec % HISTORY_SECONDS]
                txHist[k] = txRing[sec % HISTORY_SECONDS]
            }
        }
        val speedSec = if (ringSec >= 1) ringSec - 1 else ringSec
        return StatsSnapshot(
            durationMs = now - startedAt,
            rx = rx, tx = tx, errors = errors,
            rxSpeed = rxRing[speedSec % HISTORY_SECONDS],
            sinceActivityMs = if (lastActivity == 0L) null else now - lastActivity,
            rxHistory = rxHist, txHistory = txHist,
            endedAt = endedAt,
        )
    }
}
