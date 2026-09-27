"""Update the app-owned MediaStore config entry without exposing an API key in output."""

import argparse
import json
import pathlib
import re
import subprocess
import sys


def adb(*args: str, stdin=None) -> bytes:
    result = subprocess.run(
        ["adb", *args], stdin=stdin, stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    if result.returncode:
        raise RuntimeError(f"adb command failed: {' '.join(args[:3])}")
    return result.stdout


def find_media_id() -> str:
    listing = adb(
        "shell", "content", "query", "--uri", "content://media/external/downloads",
        "--projection", "_id:_display_name:_size:relative_path",
    ).decode(errors="replace")
    matches = []
    for line in listing.splitlines():
        if "relative_path=Download/E-Ujian/" not in line:
            continue
        name = re.search(r"_display_name=(config(?: \(\d+\))?\.json)", line)
        media_id = re.search(r"_id=(\d+)", line)
        if name and media_id:
            matches.append(media_id.group(1))
    if len(matches) != 1:
        raise RuntimeError(
            f"Found {len(matches)} config entries in MediaStore; pass --media-id explicitly"
        )
    return matches[0]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("config", type=pathlib.Path)
    parser.add_argument("--media-id", help="Explicit MediaStore Downloads row ID")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    payload = args.config.read_bytes()
    config = json.loads(payload)
    model = config.get("model")
    if not isinstance(model, str) or not re.fullmatch(r"[A-Za-z0-9._-]{3,100}", model):
        raise ValueError("Invalid model name")
    keys = config.get("apiKeys")
    if not isinstance(keys, list) or len(keys) > 10:
        raise ValueError("apiKeys must be an array of at most 10 entries")
    active = sum(
        isinstance(key, dict) and key.get("enabled") is True
        and isinstance(key.get("key"), str)
        and key["key"] not in ("", "PASTE_KEY_HERE")
        for key in keys
    )
    if active < 1:
        raise ValueError("No active API key slot")

    media_id = args.media_id or find_media_id()
    uri = f"content://media/external/downloads/{media_id}"
    if args.dry_run:
        print(f"Ready: MediaStore ID {media_id}, model {model}, {active} active slot(s)")
        return 0
    with args.config.open("rb") as file:
        adb("shell", "content", "write", "--uri", uri, stdin=file)
    remote = adb("exec-out", "content", "read", "--uri", uri)
    if remote != payload:
        raise RuntimeError("MediaStore read-back did not match the local file")
    print(f"Updated MediaStore ID {media_id}: model {model}, {active} active slot(s)")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as exc:
        print(f"Config transfer failed: {exc}", file=sys.stderr)
        sys.exit(1)
