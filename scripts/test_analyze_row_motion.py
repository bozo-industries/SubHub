import unittest
from analyze_row_motion import parse

class RowTraceTest(unittest.TestCase):
    def test_valid_rejected_and_source_scale(self):
        line='ROW_MOTION accepted=true previousMs=100 currentMs=400 dyMilliPx=-10000 bands=3 preparedHeight=320 sourceHeight=3200 costMs=2'
        report=parse('prefix: '+line+'\n'+line.replace('accepted=true','accepted=false').replace('previousMs=100','previousMs=-1'))
        self.assertTrue(report['complete']); self.assertEqual(2,report['parsedRecords'])
        self.assertEqual(1,report['acceptedRecords']); self.assertEqual(-100,report['records'][0]['sourceDy'])
    def test_unknown_and_invalid_are_visible(self):
        base='ROW_MOTION accepted=true previousMs=100 currentMs=400 dyMilliPx=0 bands=3 preparedHeight=320 sourceHeight=3200 costMs=2'
        for line in [base+' extra=1',base.replace('bands=3','bands=2'),base.replace('currentMs=400','currentMs=99')]:
            report=parse(line); self.assertFalse(report['complete']); self.assertEqual(1,report['rawRecords'])
            self.assertEqual(0,report['parsedRecords'])

if __name__=='__main__': unittest.main()
