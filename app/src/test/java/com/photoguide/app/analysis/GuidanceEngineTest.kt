package com.photoguide.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure-Kotlin coaching heuristics. */
class GuidanceEngineTest {

    private val engine = GuidanceEngine()

    /** A well-composed, well-lit, level portrait should produce no tips. */
    private fun goodPortrait() = FrameSignals(
        subject = SubjectBox(left = 0.35f, top = 0.25f, right = 0.65f, bottom = 0.55f),
        meanLuma = 130f, centerLuma = 135f, surroundLuma = 128f,
        rollDegrees = 1f, pitchDegrees = 2f, hasTilt = true,
    )

    @Test
    fun goodFrame_hasNoTips() {
        val g = engine.analyze(goodPortrait())
        assertTrue("expected no tips but got ${g.tips.map { it.message }}", g.isGood)
        assertNull(g.primary)
    }

    @Test
    fun noSubject_suggestsPointingAtSubject() {
        val g = engine.analyze(FrameSignals(subject = null, meanLuma = 130f))
        assertEquals(TipCategory.SUBJECT, g.primary?.category)
    }

    @Test
    fun darkScene_isHighestPriority() {
        val g = engine.analyze(goodPortrait().copy(meanLuma = 30f))
        assertEquals(TipCategory.LIGHTING, g.primary?.category)
    }

    @Test
    fun tiltedPhone_asksToStraighten_withRotationArrow() {
        val g = engine.analyze(goodPortrait().copy(rollDegrees = 12f))
        val level = g.tips.first { it.category == TipCategory.LEVEL }
        assertEquals(ArrowDirection.ROTATE_CCW, level.arrow)
    }

    @Test
    fun cutOffSubject_outranksComposition() {
        // Subject runs off the top edge.
        val s = goodPortrait().copy(
            subject = SubjectBox(left = 0.35f, top = 0.0f, right = 0.65f, bottom = 0.30f)
        )
        val g = engine.analyze(s)
        assertEquals(TipCategory.FRAMING, g.primary?.category)
        assertTrue(g.primary!!.message.contains("cut off"))
    }

    @Test
    fun subjectTooLow_suggestsTiltDown() {
        val s = goodPortrait().copy(
            subject = SubjectBox(left = 0.35f, top = 0.60f, right = 0.65f, bottom = 0.85f)
        )
        val tip = engine.analyze(s).tips.first { it.category == TipCategory.COMPOSITION }
        assertEquals(ArrowDirection.DOWN, tip.arrow)
    }

    @Test
    fun subjectOnLeftEdge_suggestsPanLeft() {
        val s = goodPortrait().copy(
            subject = SubjectBox(left = 0.02f, top = 0.25f, right = 0.25f, bottom = 0.55f)
        )
        val tip = engine.analyze(s).tips.first {
            it.message.contains("left edge")
        }
        assertEquals(ArrowDirection.LEFT, tip.arrow)
    }

    @Test
    fun backlitSubject_isFlagged() {
        val s = goodPortrait().copy(centerLuma = 70f, surroundLuma = 200f)
        val g = engine.analyze(s)
        assertNotNull(g.tips.firstOrNull { it.message.contains("backlit") })
    }

    @Test
    fun tinyDistantFace_suggestsMovingCloser() {
        val s = goodPortrait().copy(
            subject = SubjectBox(left = 0.47f, top = 0.38f, right = 0.53f, bottom = 0.44f)
        )
        val g = engine.analyze(s)
        assertNotNull(g.tips.firstOrNull { it.message.contains("closer") })
    }
}
