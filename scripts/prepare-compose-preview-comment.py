#!/usr/bin/env python3
"""Resolve attachment placeholders in Compose preview comments."""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import re
import uuid
from pathlib import Path
from typing import Dict
from urllib import error, parse, request

ATTACHMENT_PATTERN = re.compile(r"!\[(?P<alt>[^\]]*)\]\(attachment://(?P<path>[^)]+)\)")

# GitHub comment attachments are limited to 10 MB per file.
MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Upload comment attachments and rewrite Markdown")
    parser.add_argument("--comment", required=True, help="Path to the Markdown file to rewrite")
    parser.add_argument("--image-root", required=True, help="Root directory used for relative attachment paths")
    parser.add_argument("--repo", required=True, help="GitHub repository in the form owner/repo")
    parser.add_argument("--issue-number", type=int, required=True, help="Pull request or issue number for the comment")
    parser.add_argument(
        "--token",
        required=False,
        default=os.getenv("GITHUB_TOKEN"),
        help="GitHub token (defaults to GITHUB_TOKEN env)",
    )
    return parser.parse_args()


def upload_attachment(*, token: str, owner: str, repo: str, issue_number: int, file_path: Path) -> str:
    if not token:
        raise RuntimeError("A GitHub token is required to upload attachments")
    url = (
        f"https://uploads.github.com/repos/{owner}/{repo}/issues/{issue_number}/comments/"
        f"assets?name={parse.quote(file_path.name)}"
    )
    content_type = mimetypes.guess_type(file_path.name)[0] or "application/octet-stream"
    boundary = f"----compose-preview-boundary-{uuid.uuid4().hex}"
    boundary_bytes = boundary.encode("utf-8")
    file_bytes = file_path.read_bytes()
    file_size = len(file_bytes)
    if file_size > MAX_ATTACHMENT_BYTES:
        raise RuntimeError(
            f"Attachment {file_path} exceeds GitHub's comment upload limit: "
            f"{file_size} bytes > {MAX_ATTACHMENT_BYTES} bytes"
        )

    disposition = (
        f'Content-Disposition: form-data; name="file"; filename="{file_path.name}"\r\n'
    ).encode("utf-8")
    part_headers = (
        f"Content-Type: {content_type}\r\n"
        f"Content-Length: {file_size}\r\n"
        "Content-Transfer-Encoding: binary\r\n\r\n"
    ).encode("utf-8")
    body = (
        b"--" + boundary_bytes + b"\r\n"
        + disposition
        + part_headers
        + file_bytes
        + b"\r\n--" + boundary_bytes + b"--\r\n"
    )
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "Content-Length": str(len(body)),
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "compose-preview-comment-uploader",
    }
    req = request.Request(url, data=body, method="POST", headers=headers)
    try:
        with request.urlopen(req) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except error.HTTPError as exc:
        message = exc.read().decode("utf-8", errors="ignore")
        hint = ""
        if exc.status == 422 and "Bad Size" in message:
            size = file_path.stat().st_size
            hint = (
                " GitHub reported \"Bad Size\" for the uploaded payload. "
                "Verify the Compose renderer downscaled outputs correctly, confirm the attachment is under the "
                f"{MAX_ATTACHMENT_BYTES} byte limit, and ensure the multipart request body matches GitHub's expected format. "
                f"Current file size: {size} bytes."
            )
        raise RuntimeError(
            f"Failed to upload {file_path}: {exc.status} {exc.reason}: {message}{hint}"
        ) from exc

    for key in ("download_url", "browser_download_url", "url"):
        candidate = payload.get(key)
        if candidate and candidate.startswith("http"):
            return candidate
    raise RuntimeError(f"Attachment upload response missing URL fields for {file_path}")


def rewrite_comment(
    *,
    comment_path: Path,
    image_root: Path,
    owner: str,
    repo: str,
    issue_number: int,
    token: str,
) -> None:
    content = comment_path.read_text(encoding="utf-8")
    cache: Dict[Path, str] = {}

    def replacement(match: re.Match[str]) -> str:
        rel_path = Path(match.group("path"))
        alt = match.group("alt") or "Preview"
        candidate = (image_root / rel_path).resolve()
        if not candidate.exists():
            return f"_{alt} image unavailable ({rel_path})_"
        if candidate not in cache:
            cache[candidate] = upload_attachment(
                token=token,
                owner=owner,
                repo=repo,
                issue_number=issue_number,
                file_path=candidate,
            )
        return f"![{alt}]({cache[candidate]})"

    updated = ATTACHMENT_PATTERN.sub(replacement, content)
    comment_path.write_text(updated, encoding="utf-8")


def main() -> None:
    args = parse_args()
    if "/" not in args.repo:
        raise SystemExit("--repo must be formatted as owner/repo")
    owner, repo = args.repo.split("/", 1)
    image_root = Path(args.image_root).resolve()
    comment_path = Path(args.comment).resolve()
    token = args.token or ""
    rewrite_comment(
        comment_path=comment_path,
        image_root=image_root,
        owner=owner,
        repo=repo,
        issue_number=args.issue_number,
        token=token,
    )


if __name__ == "__main__":
    main()
