package com.photoguide.app.analysis

import kotlin.math.abs

/**
 * Turns a [FrameSignals] snapshot into prioritized, human-readable coaching
 * tips. This is the "brain" of the app and is intentionally pure Kotlin so it
 * can be unit-tested without an emulator.
 *
 * The heuristics encode a handful of well-known photography rules:
 *  - Keep the horizon level.
 *  - Place the subject's face near the upper third (good headroom).
 *  - Keep the subject off the extreme edges and fully in frame.
 *  - Get the subject distance right (not tiny, not cramped).
 *  - Expose well and avoid backlighting the subject.
 */
class GuidanceEngine(private val cfg: Config = Config()) {

    data class Config(
        // ---- Level / tilt ----
        val rollLevelTolerance: Float = 3f,    // deg considered "level"
        val rollStrongTilt: Float = 8f,        // deg considered a strong tilt
        val pitchExtreme: Float = 35f,         // deg of forward/back tilt to warn

        // ---- Composition (face center target) ----
        val targetFaceY: Float = 0.40f,        // ideal vertical position of face
        val faceYTolerance: Float = 0.10f,
        val edgeMarginX: Float = 0.18f,        // how close to L/R edge is "too close"

        // ---- Distance (face box width as fraction of frame) ----
        val faceTooFar: Float = 0.10f,
        val faceTooClose: Float = 0.55f,

        // ---- Lighting (0..255 luma) ----
        val tooDark: Float = 55f,
        val tooBright: Float = 205f,
        val backlitDelta: Float = 45f,         // surround brighter than center by this
    )

    fun analyze(s: FrameSignals): Guidance {
        val tips = ArrayList<Tip>()

        addLevelTips(s, tips)
        addExposureTips(s, tips)

        if (s.hasSubject) {
            val box = s.subject!!
            addFramingTips(box, tips)
            addBacklightTip(s, box, tips)
            addHeadroomTips(box, tips)
            addHorizontalTips(box, tips)
            addDistanceTips(box, tips)
        } else {
            tips += Tip(
                TipCategory.SUBJECT,
                "Point the camera at your subject",
                ArrowDirection.NONE,
                severity = 20,
            )
        }

        // Most urgent first.
        tips.sortByDescending { it.severity }
        return Guidance(tips)
    }

    private fun addLevelTips(s: FrameSignals, out: MutableList<Tip>) {
        if (!s.hasTilt) return
        val roll = s.rollDegrees
        if (abs(roll) > cfg.rollLevelTolerance) {
            // Positive roll => phone tilted so the right side is down; rotate
            // counter-clockwise to level it (and vice-versa).
            val cw = roll > 0
            val sev = if (abs(roll) > cfg.rollStrongTilt) 70 else 55
            out += Tip(
                TipCategory.LEVEL,
                "Straighten up — keep the horizon level",
                if (cw) ArrowDirection.ROTATE_CCW else ArrowDirection.ROTATE_CW,
                sev,
            )
        }
        if (abs(s.pitchDegrees) > cfg.pitchExtreme) {
            out += Tip(
                TipCategory.LEVEL,
                "Hold the phone more vertical to avoid distortion",
                ArrowDirection.NONE,
                severity = 30,
            )
        }
    }

    private fun addExposureTips(s: FrameSignals, out: MutableList<Tip>) {
        when {
            s.meanLuma < cfg.tooDark ->
                out += Tip(
                    TipCategory.LIGHTING,
                    "Too dark — find more light or turn on the flash",
                    ArrowDirection.NONE,
                    severity = 80,
                )
            s.meanLuma > cfg.tooBright ->
                out += Tip(
                    TipCategory.LIGHTING,
                    "Too bright — tap to lower exposure or step into shade",
                    ArrowDirection.NONE,
                    severity = 75,
                )
        }
    }

    private fun addBacklightTip(s: FrameSignals, box: SubjectBox, out: MutableList<Tip>) {
        // Subject is roughly central and noticeably darker than the bright
        // background behind them — classic backlighting.
        val subjectCentral = abs(box.centerX - 0.5f) < 0.30f
        if (subjectCentral && s.surroundLuma - s.centerLuma > cfg.backlitDelta) {
            out += Tip(
                TipCategory.LIGHTING,
                "Subject is backlit — turn so the light hits their face, or tap to focus on them",
                ArrowDirection.NONE,
                severity = 78,
            )
        }
    }

    private fun addFramingTips(box: SubjectBox, out: MutableList<Tip>) {
        if (box.isCutOff()) {
            out += Tip(
                TipCategory.FRAMING,
                "Subject is cut off — step back or reframe",
                ArrowDirection.NONE,
                severity = 90,
            )
        }
    }

    private fun addHeadroomTips(box: SubjectBox, out: MutableList<Tip>) {
        val dy = box.centerY - cfg.targetFaceY
        if (abs(dy) <= cfg.faceYTolerance) return
        if (dy > 0) {
            // Face sits too low => too much empty headroom above; tilt down.
            out += Tip(
                TipCategory.COMPOSITION,
                "Too much headroom — tilt down a little",
                ArrowDirection.DOWN,
                severity = 50,
            )
        } else {
            out += Tip(
                TipCategory.COMPOSITION,
                "Leave a bit of headroom — tilt up a little",
                ArrowDirection.UP,
                severity = 50,
            )
        }
    }

    private fun addHorizontalTips(box: SubjectBox, out: MutableList<Tip>) {
        val cx = box.centerX
        if (cx < cfg.edgeMarginX) {
            out += Tip(
                TipCategory.COMPOSITION,
                "Subject hugging the left edge — pan left to recenter",
                ArrowDirection.LEFT,
                severity = 45,
            )
        } else if (cx > 1f - cfg.edgeMarginX) {
            out += Tip(
                TipCategory.COMPOSITION,
                "Subject hugging the right edge — pan right to recenter",
                ArrowDirection.RIGHT,
                severity = 45,
            )
        }
    }

    private fun addDistanceTips(box: SubjectBox, out: MutableList<Tip>) {
        when {
            box.width < cfg.faceTooFar ->
                out += Tip(
                    TipCategory.FRAMING,
                    "Move closer for a stronger portrait",
                    ArrowDirection.NONE,
                    severity = 40,
                )
            box.width > cfg.faceTooClose ->
                out += Tip(
                    TipCategory.FRAMING,
                    "A touch too close — step back",
                    ArrowDirection.NONE,
                    severity = 40,
                )
        }
    }
}
