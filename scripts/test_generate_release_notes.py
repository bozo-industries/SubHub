import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("generate_release_notes.py")
SPEC = importlib.util.spec_from_file_location("generate_release_notes", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ReleaseNotesTest(unittest.TestCase):
    def test_categorizes_subjects_and_uses_a_concise_body_sentence(self):
        values = MODULE.entries([
            MODULE.Commit("a" * 40, "[feat] Add a velvet dashboard",
                          "Give Home a deliberate hierarchy. Keep the second sentence out."),
            MODULE.Commit("b" * 40, "[fix] Restore update handoff",
                          "Checks: gradle\n\nAndroid now opens the verified installer."),
        ])
        notes = MODULE.changelog_markdown("0.6.0", values)
        self.assertIn("### New", notes)
        self.assertIn("**Add a velvet dashboard** — Give Home a deliberate hierarchy.", notes)
        self.assertIn("### Fixed", notes)
        self.assertIn("Android now opens the verified installer.", notes)
        self.assertNotIn("second sentence", notes)

    def test_repairs_literal_newlines_from_historical_powershell_commits(self):
        values = MODULE.entries([
            MODULE.Commit("c" * 40, "[fix] Respect protection state",
                          "Stop limits when protection stops.\\n\\nChecks: gradle"),
        ])
        self.assertEqual("Stop limits when protection stops.", values[0].detail)

    def test_omits_release_bump_noise_but_keeps_other_maintenance(self):
        values = MODULE.entries([
            MODULE.Commit("d" * 40, "[chore] Bump SubHub to 0.6.0", ""),
            MODULE.Commit("e" * 40, "[chore] Refresh bundled model metadata", ""),
        ])
        self.assertEqual(["Refresh bundled model metadata"], [value.title for value in values])

    def test_rejects_untyped_release_commits(self):
        with self.assertRaisesRegex(ValueError, "supported \\[type\\] prefix"):
            MODULE.entries([MODULE.Commit("f" * 40, "misc update", "")])

    def test_release_keeps_download_guidance_separate_from_changelog(self):
        changelog = MODULE.changelog_markdown(
            "0.6.0", [MODULE.Entry("feat", "Add release notes", "")]
        )
        release = MODULE.release_markdown(
            "0.6.0", changelog, "bozo-industries/SubHub", "v0.5.2", "v0.6.0"
        )
        self.assertLess(release.index("## What’s new"), release.index("## Choose your APK"))
        self.assertIn("compare/v0.5.2...v0.6.0", release)


if __name__ == "__main__":
    unittest.main()
