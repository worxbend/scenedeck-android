import runpy
import unittest
from pathlib import Path

metadata = runpy.run_path(str(Path(__file__).with_name('release-metadata.py')))['metadata']


class ReleaseMetadataTest(unittest.TestCase):
    def check(self, version_properties='', **overrides):
        return metadata({'GITHUB_RUN_NUMBER': '42', **overrides}, version_properties)

    def test_tag_uses_matching_static_version_code(self):
        props = 'versionName=0.1.4\nversionCode=1010\n'
        result = self.check(props, GITHUB_REF_TYPE='tag', GITHUB_REF_NAME='v0.1.4')
        self.assertEqual(result['version_code'], '1010')

    def test_tag_falls_back_to_run_number_when_static_version_differs(self):
        props = 'versionName=0.1.4\nversionCode=1010\n'
        result = self.check(props, GITHUB_REF_TYPE='tag', GITHUB_REF_NAME='v0.2.0')
        self.assertEqual(result['version_code'], '1042')

    def test_manual_runs_ignore_static_version_code(self):
        props = 'versionName=0.1.0\nversionCode=1010\n'
        self.assertEqual(self.check(props)['version_code'], '1042')

    def test_static_version_code_is_validated(self):
        with self.assertRaises(ValueError):
            self.check('versionName=0.1.4\nversionCode=abc\n', GITHUB_REF_TYPE='tag', GITHUB_REF_NAME='v0.1.4')

    def test_repository_version_file_is_read_by_default(self):
        props = Path(__file__).resolve().parent.parent.joinpath('fdroid', 'version.properties').read_text()
        name = next(line.split('=', 1)[1].strip() for line in props.splitlines() if line.startswith('versionName='))
        result = metadata({'GITHUB_RUN_NUMBER': '42', 'GITHUB_REF_TYPE': 'tag', 'GITHUB_REF_NAME': f'v{name}'})
        self.assertNotEqual(result['version_code'], '1042')

    def test_debug_defaults(self):
        self.assertEqual(self.check(), dict(variant='debug', version_name='0.1.0', version_code='1042', publish_play='false', tagged='false', play_track='internal', play_release_status='draft', upload_play_metadata='false'))

    def test_tag_destination(self):
        result = self.check(GITHUB_REF_TYPE='tag', GITHUB_REF_NAME='v1.0.0',
                            PLAY_TAG_TRACK='production', PLAY_TAG_RELEASE_STATUS='completed',
                            PLAY_TAG_UPLOAD_METADATA='true', INPUT_PLAY_TRACK='internal')
        self.assertEqual(result['play_track'], 'production')
        self.assertEqual(result['play_release_status'], 'completed')
        self.assertEqual(result['upload_play_metadata'], 'true')

    def test_manual_destination(self):
        result = self.check(INPUT_PLAY_TRACK='beta', INPUT_PLAY_RELEASE_STATUS='completed',
                            INPUT_UPLOAD_PLAY_METADATA='true', PLAY_TAG_TRACK='production')
        self.assertEqual(result['play_track'], 'beta')
        self.assertEqual(result['play_release_status'], 'completed')
        self.assertEqual(result['upload_play_metadata'], 'true')

    def test_rejects_invalid_play_destination(self):
        for key, value in [('INPUT_PLAY_TRACK', 'unknown'), ('INPUT_PLAY_RELEASE_STATUS', 'unknown'),
                           ('INPUT_UPLOAD_PLAY_METADATA', 'yes')]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.check(**{key: value})

    def test_tag_release(self):
        result = self.check(GITHUB_REF_TYPE='tag', GITHUB_REF_NAME='v1.2.3-rc.1', PLAY_PUBLISH_ENABLED='true')
        self.assertEqual(result['variant'], 'release')
        self.assertEqual(result['version_name'], '1.2.3-rc.1')
        self.assertEqual(result['publish_play'], 'true')

    def test_manual_release(self):
        result = self.check(INPUT_VARIANT='release', INPUT_VERSION_CODE='00123', INPUT_PUBLISH_PLAY='true')
        self.assertEqual(result['version_code'], '123')
        self.assertEqual(result['publish_play'], 'true')

    def test_rejects_invalid_versions(self):
        for version in ['1.2', '1.2.3\noutput=injected', '$(id)', '1.2.3/evil']:
            with self.subTest(version=version), self.assertRaises(ValueError):
                self.check(INPUT_VERSION_NAME=version)

    def test_rejects_invalid_codes(self):
        for code in ['0', '-1', '2100000001', '１２', '1\n', 'abc']:
            with self.subTest(code=code), self.assertRaises(ValueError):
                self.check(INPUT_VERSION_CODE=code)

    def test_rejects_debug_publication(self):
        with self.assertRaises(ValueError):
            self.check(INPUT_PUBLISH_PLAY='true')

    def test_rejects_invalid_variant_and_tag(self):
        with self.assertRaises(ValueError):
            self.check(INPUT_VARIANT='unknown')
        with self.assertRaises(ValueError):
            self.check(GITHUB_REF_TYPE='tag', GITHUB_REF_NAME='1.2.3')


if __name__ == '__main__':
    unittest.main()
