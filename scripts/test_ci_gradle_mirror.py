import tempfile
import unittest
from pathlib import Path
from ci_gradle_mirror import prepare, SHA256, MIRROR


class GradleMirrorTests(unittest.TestCase):
    def test_only_expected_wrapper_is_modified(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "gradle-wrapper.properties"
            path.write_text(
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.9-bin.zip\n"
                f"distributionSha256Sum={SHA256}\nvalidateDistributionUrl=true\n",
                encoding="utf-8")
            prepare(path)
            self.assertIn("distributionUrl=" + MIRROR, path.read_text())
            prepare(path)  # Idempotent
            self.assertEqual(path.read_text().count(MIRROR), 1)

    def test_cannot_change_distribution_without_pinned_hash(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "gradle-wrapper.properties"
            path.write_text(
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.9-bin.zip\n"
                "distributionSha256Sum=WRONG\nvalidateDistributionUrl=true\n")
            with self.assertRaises(ValueError):
                prepare(path)


if __name__ == "__main__":
    unittest.main()
