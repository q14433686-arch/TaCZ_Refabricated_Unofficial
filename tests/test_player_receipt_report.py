"""Checks UI visibility is not a replacement for the native assertion/marker gates."""
import contextlib
import importlib.util
import io
from pathlib import Path
import sys
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / 'scripts'
sys.path.insert(0, str(SCRIPTS))
SPEC = importlib.util.spec_from_file_location('player_receipts', SCRIPTS / 'test_12111_player.py')
PLAYER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PLAYER)


class ReceiptReportTest(unittest.TestCase):
    def report(self, receipts):
        with contextlib.redirect_stdout(io.StringIO()) as captured:
            PLAYER.report_receipts(receipts)
        return captured.getvalue()

    def test_late_contracts_fit_in_checks_ui(self):
        receipts = [f'PORT_PLATFORM_{index}: ' + 'native evidence ' * 18 for index in range(27)]
        output = self.report(receipts)
        notices = [line.removeprefix('::notice::') for line in output.splitlines() if line.startswith('::notice::')]
        self.assertLess(len(notices), 10)
        self.assertTrue(all(len(part) <= 3500 for part in notices))
        for receipt in receipts:
            self.assertEqual(sum(receipt in part for part in notices), 1)
            self.assertIn(receipt + '\n', output)

    def test_workflow_special_characters_are_escaped_and_long_rows_not_truncated(self):
        receipt = 'PORT_PIXEL: ' + '%\r\n' * 1800 + 'last-corner'
        output = self.report([receipt])
        notices = [line.removeprefix('::notice::') for line in output.splitlines() if line.startswith('::notice::')]
        joined = ''.join(notices).replace(' | ', '')
        self.assertEqual(receipt.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'), joined)
        self.assertTrue(all(len(part) <= 3500 for part in notices))


if __name__ == '__main__':
    unittest.main()
