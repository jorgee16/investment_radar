import unittest
from unittest.mock import patch
from tempfile import TemporaryDirectory
from pathlib import Path
from check_android_update import apk_identity, validate_update


class UpdateIdentityTest(unittest.TestCase):
    def setUp(self):
        self.previous = dict(package="com.investmentradar.app", versionCode=9, certificates=["same-certificate"])
        self.candidate = dict(self.previous, versionCode=10)

    def test_same_identity_and_newer_version_can_retain_data(self):
        self.assertTrue(validate_update(self.previous, self.candidate)["compatible"])

    def test_changing_signer_is_rejected_instead_of_requiring_uninstall(self):
        self.candidate["certificates"] = ["fresh-ci-certificate"]
        with self.assertRaisesRegex(ValueError, "Signing certificate changed"):
            validate_update(self.previous, self.candidate)

    def test_changing_package_would_create_an_empty_separate_app(self):
        self.candidate["package"] += ".other"
        with self.assertRaisesRegex(ValueError, "Application ID changed"):
            validate_update(self.previous, self.candidate)

    def test_instrumentation_apk_may_have_empty_version_fields(self):
        with TemporaryDirectory() as folder:
            apk = Path(folder) / "probe.apk"
            apk.write_bytes(b"unit-fixture")
            with patch("check_android_update.subprocess.check_output", side_effect=[
                "package: name='com.investmentradar.app.test' versionCode='' versionName=''",
                "Signer #1 certificate SHA-256 digest: aabb",
            ]):
                result = apk_identity(apk, Path(folder))
            self.assertEqual(result["package"], "com.investmentradar.app.test")
            self.assertEqual(result["versionCode"], 0)
            self.assertEqual(result["certificates"], ["aabb"])

    def test_equal_version_and_downgrade_are_rejected(self):
        for code in (8, 9):
            with self.subTest(code=code), self.assertRaisesRegex(ValueError, "increase versionCode"):
                validate_update(self.previous, dict(self.candidate, versionCode=code))


if __name__ == "__main__":
    unittest.main()
