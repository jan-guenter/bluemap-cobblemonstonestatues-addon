import importlib.util
from pathlib import Path
import unittest


MODULE_PATH = Path(__file__).parents[1] / "verify_pinned_artifacts.py"
SPEC = importlib.util.spec_from_file_location("verify_pinned_artifacts", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class ArtifactContractTest(unittest.TestCase):
    def test_representative_closure_is_intentionally_24_files(self):
        self.assertEqual(24, len(MODULE.REPRESENTATIVE_RESOURCES))
        self.assertEqual(
            351_875,
            sum(size for size, _ in MODULE.REPRESENTATIVE_RESOURCES.values()),
        )

    def test_exact_outer_identities(self):
        self.assertEqual(170_977, MODULE.EXPECTED["stone"]["size"])
        self.assertEqual(128_748_941, MODULE.EXPECTED["cobblemon"]["size"])


if __name__ == "__main__":
    unittest.main()
