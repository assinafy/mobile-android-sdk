"""Release guards and CLI calls, without network access or credentials."""

import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import create_release


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        previous = Path.cwd()
        os.chdir(self.directory.name)
        self.addCleanup(os.chdir, previous)
        Path("sdk").mkdir()
        Path("sdk/build.gradle.kts").write_text(
            'version = (findProperty("version") as String?) ?: "1.2.3"\n'
        )
        Path("CHANGELOG.md").write_text(
            "## [Unreleased]\n\nFuture change\n\n"
            "## [1.2.3] - 2026-10-28\n\n### Added\n\n- Release automation.\n\n"
            "## [1.2.2] - 2026-10-27\n\nPrevious change\n"
        )
        environment = patch.dict(os.environ, {
            "GITHUB_REF_NAME": "v1.2.3", "GITHUB_REPOSITORY": "example/sdk",
        })
        environment.start()
        self.addCleanup(environment.stop)
        runner = patch.object(create_release.subprocess, "run")
        self.run_cli = runner.start()
        self.addCleanup(runner.stop)
        self.run_cli.return_value.returncode = 1

    def test_publishes_only_matching_notes_and_requires_existing_tag(self):
        create_release.main()
        self.assertEqual(self.run_cli.call_count, 2)
        call = self.run_cli.call_args
        self.assertEqual(call.args[0], [
            "gh", "release", "create", "v1.2.3", "--repo", "example/sdk",
            "--verify-tag", "--title", "v1.2.3", "--notes-file", "-",
        ])
        self.assertEqual(call.kwargs["input"], "### Added\n\n- Release automation.\n")
        self.assertTrue(call.kwargs["check"])

    def test_preserves_an_existing_release(self):
        self.run_cli.return_value.returncode = 0
        with patch("builtins.print"):
            create_release.main()
        self.assertEqual(self.run_cli.call_count, 1)
        self.assertEqual(self.run_cli.call_args.args[0][1:3], ["release", "view"])

    def test_rejects_invalid_or_prerelease_tags_before_calling_github(self):
        for tag in ("main", "v1.2", "v1.2.3-rc.1", "v1.2.3;exit 0"):
            with self.subTest(tag=tag), patch.dict(os.environ, {"GITHUB_REF_NAME": tag}):
                with self.assertRaises(SystemExit):
                    create_release.main()
        self.run_cli.assert_not_called()

    def test_rejects_version_mismatch(self):
        with patch.dict(os.environ, {"GITHUB_REF_NAME": "v1.2.4"}):
            with self.assertRaises(SystemExit):
                create_release.main()
        self.run_cli.assert_not_called()

    def test_requires_nonempty_matching_notes(self):
        for text in ("## [9.9.9] - 2026-10-28\nOther version\n",
                     "## [1.2.3] - 2026-10-28\n\n## [1.2.2] - 2026-10-27\nOld\n"):
            Path("CHANGELOG.md").write_text(text)
            with self.subTest(text=text), self.assertRaises(SystemExit):
                create_release.main()
        self.run_cli.assert_not_called()

    def test_propagates_publish_failure(self):
        self.run_cli.side_effect = [
            subprocess.CompletedProcess([], 1), subprocess.CalledProcessError(1, "gh"),
        ]
        with self.assertRaises(subprocess.CalledProcessError):
            create_release.main()


if __name__ == "__main__":
    unittest.main()
