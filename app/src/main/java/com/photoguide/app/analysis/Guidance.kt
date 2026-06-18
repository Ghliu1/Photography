package com.photoguide.app.analysis

/** Which way the user should physically move/rotate the phone. */
enum class ArrowDirection { UP, DOWN, LEFT, RIGHT, ROTATE_CW, ROTATE_CCW, NONE }

/** Broad bucket a tip belongs to, used for the on-screen status chips. */
enum class TipCategory { FRAMING, COMPOSITION, LIGHTING, LEVEL, SUBJECT }

/**
 * A single, actionable coaching hint.
 *
 * @param severity higher = more urgent; the engine surfaces the most
 *        severe tip as the primary instruction.
 */
data class Tip(
    val category: TipCategory,
    val message: String,
    val arrow: ArrowDirection = ArrowDirection.NONE,
    val severity: Int = 0,
)

/**
 * The full result of analysing a frame: an ordered list of tips (most
 * urgent first) plus convenience accessors for the UI layer.
 */
data class Guidance(
    val tips: List<Tip>,
) {
    /** The single most important thing to fix right now, if anything. */
    val primary: Tip? get() = tips.firstOrNull()

    /** True when the shot looks good and there is nothing pressing to fix. */
    val isGood: Boolean get() = tips.isEmpty()

    companion object {
        val GOOD = Guidance(emptyList())
    }
}
