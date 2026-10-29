"""Publish the tagged version's changelog entry after CI succeeds."""

import os
import re
import subprocess
from pathlib import Path


def main():
    tag = os.environ["GITHUB_REF_NAME"]
    repository = os.environ["GITHUB_REPOSITORY"]
    if not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag):
        raise SystemExit("Release tags must be vMAJOR.MINOR.PATCH")
    version = tag[1:]
    configured = re.search(
        r'^version = .*\?: "([^"]+)"$',
        Path("sdk/build.gradle.kts").read_text(),
        re.MULTILINE,
    )
    if configured is None or configured[1] != version:
        raise SystemExit("Release tag must match the SDK version")
    entry = re.search(
        rf"^## \[{re.escape(version)}\] - [^\n]+\n(.*?)(?=^## |\Z)",
        Path("CHANGELOG.md").read_text(),
        re.MULTILINE | re.DOTALL,
    )
    if entry is None or not entry[1].strip():
        raise SystemExit("Release requires a nonempty changelog entry")
    existing = subprocess.run(
        ["gh", "release", "view", tag, "--repo", repository],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if existing.returncode == 0:
        print(f"Release {tag} already exists")
        return
    subprocess.run(
        ["gh", "release", "create", tag, "--repo", repository,
         "--verify-tag", "--title", tag, "--notes-file", "-"],
        input=entry[1].strip() + "\n",
        text=True,
        check=True,
    )


if __name__ == "__main__":
    main()
