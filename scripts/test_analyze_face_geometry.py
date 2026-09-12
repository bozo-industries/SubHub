import unittest
from analyze_face_geometry import analyze

ROW = ("FACE_GEOMETRY v=1 id=1 source=100x200 viewport=100x200 cameras=0,0,0,0,0,0 "
       "obsTotal=1 obsEncoded=1 observations=1,0,20,100,40,40 "
       "tracksTotal=1 tracksEncoded=1 tracks=1,1,0,0,20,100,40,40,20,70,40,40")


class GeometryTest(unittest.TestCase):
    def test_raw_filtered_difference_is_not_absolute_alignment(self):
        result = analyze(ROW)
        self.assertTrue(result["complete"])
        self.assertEqual(30, result["freshRawToFilteredYMaxPx"])
        self.assertFalse(result["absoluteAlignmentMeasured"])

    def test_schema_and_truncated_data_are_explicitly_incomplete(self):
        self.assertFalse(analyze(ROW + " extra=1")["complete"])
        self.assertFalse(analyze(ROW.replace("obsTotal=1", "obsTotal=9"))["complete"])
        self.assertFalse(analyze(ROW.replace("tracks=1,1,0", "tracks=1,3,0"))["complete"])

    def test_missing_track_is_not_counted_as_a_fresh_measurement(self):
        result = analyze(ROW.replace("tracks=1,1,0", "tracks=1,1,2"))
        self.assertTrue(result["complete"])
        self.assertEqual(0, result["freshTracks"])
        self.assertIsNone(result["freshRawToFilteredYMaxPx"])


if __name__ == "__main__":
    unittest.main()
