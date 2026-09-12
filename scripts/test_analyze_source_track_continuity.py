import unittest
from analyze_source_track_continuity import analyze


class SourceTrackTest(unittest.TestCase):
    def test_counts_updates_not_visible_frames(self):
        result = analyze("SOURCE_TRACK v=1 id=1 enabled=true corrected=2 extraDy=-170")
        self.assertTrue(result["complete"])
        self.assertEqual(2, result["correctedTracks"])
        self.assertEqual(1, result["correctedUpdates"])
        self.assertEqual(170, result["maxAbsExtraDy"])
        self.assertFalse(result["visualAcceptance"])

    def test_disabled_control_has_no_correction(self):
        self.assertTrue(analyze("SOURCE_TRACK v=1 id=1 enabled=false corrected=0 extraDy=0")["complete"])
        self.assertFalse(analyze("SOURCE_TRACK v=1 id=1 enabled=false corrected=1 extraDy=1")["complete"])

    def test_malformed_and_inconsistent_records_are_incomplete(self):
        for row in ("SOURCE_TRACK v=1 id=1 enabled=true corrected=1 extraDy=0",
                    "SOURCE_TRACK v=1 id=1 enabled=true corrected=0 extraDy=1",
                    "SOURCE_TRACK v=2 id=1 enabled=true corrected=1 extraDy=1",
                    "SOURCE_TRACK v=1 id=1 enabled=true corrected=1 extraDy=1 extra=1"):
            self.assertFalse(analyze(row)["complete"])


if __name__ == "__main__":
    unittest.main()
