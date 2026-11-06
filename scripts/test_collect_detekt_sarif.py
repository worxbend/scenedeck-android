import json
import runpy
import tempfile
import unittest
from pathlib import Path

collect_reports = runpy.run_path(str(Path(__file__).with_name('collect-detekt-sarif.py')))['collect_reports']


class DetektSarifTest(unittest.TestCase):
    def write_report(self, root, module, runs=1):
        report = root / module / 'build/reports/detekt/detekt.sarif'
        report.parent.mkdir(parents=True)
        report.write_text(json.dumps({'version': '2.1.0', 'runs': [
            {'tool': {'driver': {'name': 'detekt'}}, 'results': [{'ruleId': module}]}
            for _ in range(runs)
        ]}))

    def test_all_23_module_reports_keep_results_and_stable_categories(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            modules = ['app'] + [f'core/module{i}' for i in range(8)] + [f'feature/module{i}' for i in range(14)]
            for module in modules:
                self.write_report(root, module)
            self.assertEqual(23, collect_reports(root))
            ids, results = [], []
            for group in ('app', 'core', 'feature'):
                runs = [run for file in (root / 'build/sarif/detekt' / group).glob('*.sarif')
                        for run in json.loads(file.read_text())['runs']]
                self.assertLessEqual(len(runs), 20)
                ids.extend(run['automationDetails']['id'] for run in runs)
                results.extend(run['results'][0]['ruleId'] for run in runs)
            self.assertCountEqual([f'detekt/{module}/0/' for module in modules], ids)
            self.assertCountEqual(modules, results)
            # A second collection must remove stale reports from the prior upload.
            (root / 'app/build/reports/detekt/detekt.sarif').unlink()
            self.assertEqual(22, collect_reports(root))
            self.assertFalse((root / 'build/sarif/detekt/app').exists())

    def test_group_limit_counts_runs_and_fails_instead_of_dropping_reports(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_report(root, 'feature/multiple', runs=21)
            with self.assertRaisesRegex(ValueError, 'at most 20 runs'):
                collect_reports(root)

    def test_unknown_group_requires_explicit_upload(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_report(root, 'new/module')
            with self.assertRaisesRegex(ValueError, 'Configure a SARIF upload'):
                collect_reports(root)


if __name__ == '__main__':
    unittest.main()
