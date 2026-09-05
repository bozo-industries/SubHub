import unittest
from pathlib import Path
from unittest.mock import patch

import cv2
import numpy as np

from analyze_scroll_alignment import analyze, local_background_motion, match_box_pairs


class AnalyzeScrollAlignmentTest(unittest.TestCase):
    def test_constant_spatial_offset_is_invisible_to_motion_residual(self):
        # Targets move 160 -> 120, while both censor positions are incorrectly 100px below.
        previous = [(100.0, 260.0, 42.0, 48.0)]
        current = [(100.0, 220.0, 42.0, 48.0)]
        pairs = match_box_pairs(previous, current, -40.0)
        self.assertEqual(pairs, [(0, 0)])
        residual = current[0][1] - previous[0][1] - (-40.0)
        self.assertEqual(residual, 0.0)
        self.assertEqual(current[0][1] - 120.0, 100.0)

    def test_bounded_diagnostic_is_labeled_and_not_a_release_gate(self):
        with patch("analyze_scroll_alignment.cv2.VideoCapture") as factory:
            capture = factory.return_value
            capture.isOpened.return_value = True
            capture.get.side_effect = lambda prop: 60 if prop == cv2.CAP_PROP_FPS else 64
            capture.read.return_value = (True, np.zeros((64, 64, 3), dtype=np.uint8))
            result = analyze(Path("diagnostic.mp4"), 1, .16, 3)
            self.assertEqual(capture.read.call_count, 3)
            capture.release.assert_called_once()
        self.assertTrue(result["sampleLimitReached"])
        self.assertEqual(result["sampledFrames"], 3)
        self.assertFalse(result["measurementContract"]["absoluteTargetAlignmentMeasured"])
        self.assertFalse(result["measurementContract"]["releaseGateEligible"])

    def test_invalid_sample_budget_rejected_before_opening_capture(self):
        with self.assertRaises(ValueError):
            analyze(Path("missing.mp4"), 1, .16, 0)

    def test_match_box_pairs_keeps_one_to_one_identity(self):
        previous = [(100.0, 160.0, 42.0, 48.0), (260.0, 220.0, 50.0, 52.0)]
        current = [(101.0, 120.0, 43.0, 48.0), (259.0, 180.0, 50.0, 52.0)]

        self.assertEqual(sorted(match_box_pairs(previous, current, -40.0)), [(0, 0), (1, 1)])

    def test_local_background_follows_large_translation(self):
        rng = np.random.default_rng(25)
        previous = rng.integers(0, 256, size=(420, 420), dtype=np.uint8)
        previous = cv2.GaussianBlur(previous, (5, 5), 0)
        current = cv2.warpAffine(
            previous,
            np.float32([[1.0, 0.0, 0.0], [0.0, 1.0, -86.0]]),
            (420, 420),
            borderMode=cv2.BORDER_REPLICATE,
        )
        previous_mask = np.zeros_like(previous, dtype=np.uint8)
        current_mask = np.zeros_like(previous, dtype=np.uint8)
        previous_mask[130:190, 170:230] = 255
        current_mask[44:104, 170:230] = 255

        evidence = local_background_motion(
            previous,
            current,
            previous_mask,
            current_mask,
            (200.0, 160.0, 60.0, 60.0),
            (0.0, -86.0),
            0,
            1.0,
        )

        self.assertIsNotNone(evidence)
        self.assertTrue(evidence["accepted"])
        self.assertAlmostEqual(evidence["dy"], -86.0, delta=4.0)


if __name__ == "__main__":
    unittest.main()
