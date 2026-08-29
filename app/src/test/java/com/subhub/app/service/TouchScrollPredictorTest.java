package com.subhub.app.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TouchScrollPredictorTest {
    @Test public void touchMotionOnlyPredictsAfterARealScrollEvent() {
        TouchScrollPredictor predictor = new TouchScrollPredictor();
        assertFalse(predictor.onAction(0, 500f, 1200f).active);
        assertFalse(predictor.onAction(2, 500f, 1120f).active);
        predictor.onAuthoritativeScroll();
        TouchScrollPredictor.Prediction prediction =
                predictor.onAction(2, 500f, 1060f);
        assertTrue(prediction.active);
        assertEquals(0f, prediction.dx, 0.001f);
        assertEquals(-60f, prediction.dy, 0.001f);
    }

    @Test public void everyAuthoritativeDeltaReanchorsWithoutDoubleApplyingMotion() {
        TouchScrollPredictor predictor = new TouchScrollPredictor();
        predictor.onAction(0, 400f, 1000f);
        predictor.onAction(2, 400f, 900f);
        predictor.onAuthoritativeScroll();
        assertEquals(-50f, predictor.onAction(2, 400f, 850f).dy, 0.001f);
        assertFalse(predictor.onAuthoritativeScroll().active);
        assertEquals(0f, predictor.currentPrediction().dy, 0.001f);
        assertFalse(predictor.onAction(1, 400f, 850f).active);
    }
}
