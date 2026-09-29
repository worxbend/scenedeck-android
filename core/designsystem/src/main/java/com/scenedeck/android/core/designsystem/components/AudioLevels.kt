package com.scenedeck.android.core.designsystem.components

import kotlin.math.log10
import kotlin.math.pow

/** dB floor of the OBS meter scale. Multipliers below this read as −inf. */
const val METER_DB_FLOOR = -60f

/** Linear multiplier → dB (OBS convention: 20·log10). Below floor → [METER_DB_FLOOR]. */
fun mulToDb(mul: Float): Float = when {
    mul <= 0f -> METER_DB_FLOOR
    else -> (20f * log10(mul)).coerceAtLeast(METER_DB_FLOOR)
}

fun mulToDb(mul: Double): Float = mulToDb(mul.toFloat())

/** dB → linear multiplier. At/below the floor returns 0. */
fun dbToMul(db: Float): Float = when {
    db <= METER_DB_FLOOR -> 0f
    else -> 10f.pow(db / 20f)
}

/** dB → 0..1 fraction of the meter scale. */
fun dbToFraction(db: Float): Float = ((db - METER_DB_FLOOR) / -METER_DB_FLOOR).coerceIn(0f, 1f)

/** Convenience formatter for strip readouts: "-6.0 dB", "-inf dB". */
fun formatDb(db: Float): String = when {
    db <= METER_DB_FLOOR -> "-inf dB"
    else -> "%.1f dB".format(db)
}
