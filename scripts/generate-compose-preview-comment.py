#!/usr/bin/env python3
"""Generate a Markdown summary for Compose preview renders.

This script consumes the aggregate manifest produced by the
``aggregateComposePreviewManifests`` Gradle task and emits a Markdown
report suitable for posting as a pull-request comment. The report groups
previews by module and composable, emits attachment placeholders (or
optionally inlines PNG images using data URIs), and highlights any
rendering issues.

Usage:
    python generate-compose-preview-comment.py \
        --manifest build/composePreviews/aggregate/manifest.json \
        --image-root . \
        --output build/composePreviews/aggregate/comment.md
"""

from __future__ import annotations

import argparse
import base64
import html
import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple


DEFAULT_IDENTIFIER = "controlx2-compose-previews"
DEFAULT_MAX_INLINE_BYTES = 700_000  # ~0.7 MB
IMAGE_MODE_INLINE = "inline"
IMAGE_MODE_ATTACHMENT = "attachment"
IMAGE_MODE_LINK = "link"
IMAGE_MODE_CHOICES = [IMAGE_MODE_ATTACHMENT, IMAGE_MODE_INLINE, IMAGE_MODE_LINK]

# GitHub issue comments are limited to 65,536 characters. Keep a safety
# margin so the rendered Markdown (after attachment substitution) stays
# comfortably below that ceiling.
MAX_COMMENT_CHARS = 60_000


@dataclass(frozen=True)
class PreviewKey:
    """Stable grouping key for previews that share a composable."""

    fqcn: str
    method_name: str

    @property
    def label(self) -> str:
        simple_name = self.fqcn.split(".")[-1]
        return f"{simple_name}.{self.method_name}" if self.method_name else simple_name


@dataclass
class PreviewEntry:
    module_path: str
    variant: str
    display_name: str
    configuration: str
    output_path: Optional[str]
    resolved_output: Optional[str]
    placeholder: bool
    render_error: Optional[str]
    parameters: Sequence[Dict[str, object]]
    parameter_instance_index: Optional[int]
    image_missing: bool

    def variation_summary(self) -> str:
        parts: List[str] = []
        if self.configuration:
            parts.append(self.configuration)
        if self.parameter_instance_index is not None:
            parts.append(f"parameter #{self.parameter_instance_index}")
        for param in self.parameters:
            name = (
                str(param.get("displayName") or param.get("name") or f"param {param.get('index')}")
            )
            summary = str(param.get("valueSummary") or "(value unavailable)")
            parts.append(f"{name}: {summary}")
        if self.placeholder:
            parts.append("placeholder image")
        if not parts:
            return "Default configuration"
        return ", ".join(parts)


@dataclass
class ModuleReport:
    module_path: str
    variant: str
    manifest_path: Optional[str]
    previews: Dict[PreviewKey, List[PreviewEntry]]
    environment_issues: Sequence[str]
    missing_images: Sequence[str]

    @property
    def title(self) -> str:
        variant_label = self.variant if self.variant else "<unspecified variant>"
        return f"{self.module_path} — {variant_label}"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compose preview comment generator")
    parser.add_argument("--manifest", required=True, help="Path to aggregate manifest JSON")
    parser.add_argument(
        "--image-root",
        default=None,
        help="Base directory for preview image lookups (defaults to repo root)",
    )
    parser.add_argument(
        "--output",
        default=None,
        help="Optional path to write the generated Markdown (stdout if omitted)",
    )
    parser.add_argument(
        "--identifier",
        default=DEFAULT_IDENTIFIER,
        help="Unique identifier embedded in the comment body for updates",
    )
    parser.add_argument(
        "--max-inline-bytes",
        type=int,
        default=DEFAULT_MAX_INLINE_BYTES,
        help="Maximum PNG file size (in bytes) to inline as a data URI",
    )
    parser.add_argument(
        "--image-mode",
        choices=IMAGE_MODE_CHOICES,
        default=IMAGE_MODE_ATTACHMENT,
        help=(
            "How preview images should appear in the Markdown output. 'attachment' "
            "emits attachment placeholders, 'inline' encodes data URIs, and 'link' "
            "records filesystem paths without embedding."
        ),
    )
    return parser.parse_args()


def load_manifest(path: Path) -> Dict[str, object]:
    if not path.exists():
        raise FileNotFoundError(f"Aggregate manifest not found: {path}")
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def build_module_reports(data: Dict[str, object]) -> Tuple[List[ModuleReport], List[str]]:
    manifests = data.get("manifests") or []
    module_reports: List[ModuleReport] = []
    global_issues: List[str] = []

    for entry in manifests:
        manifest_obj = entry.get("manifest") or {}
        module_path = str(entry.get("modulePath") or manifest_obj.get("modulePath") or "<unknown module>")
        variant = str(entry.get("variant") or manifest_obj.get("variant") or "")
        manifest_path = entry.get("manifestFile")
        environment_issues = list(entry.get("environmentIssues") or [])
        missing_images = list(entry.get("missingImages") or [])

        preview_entries: Dict[PreviewKey, List[PreviewEntry]] = {}
        for preview in manifest_obj.get("previews", []):
            key = PreviewKey(
                fqcn=str(preview.get("fqcn") or ""),
                method_name=str(preview.get("methodName") or ""),
            )
            entry_obj = PreviewEntry(
                module_path=module_path,
                variant=variant,
                display_name=str(preview.get("displayName") or key.label),
                configuration=str(preview.get("configurationSummary") or ""),
                output_path=preview.get("output"),
                resolved_output=preview.get("resolvedOutput"),
                placeholder=bool(preview.get("placeholder")),
                render_error=str(preview.get("renderError") or "") or None,
                parameters=list(preview.get("parameters") or []),
                parameter_instance_index=preview.get("parameterInstanceIndex"),
                image_missing=not bool(preview.get("outputExists", True)),
            )
            preview_entries.setdefault(key, []).append(entry_obj)

        # Preserve deterministic ordering within groups for readability.
        for entries in preview_entries.values():
            entries.sort(key=lambda item: (item.display_name.lower(), item.configuration.lower()))

        module_reports.append(
            ModuleReport(
                module_path=module_path,
                variant=variant,
                manifest_path=manifest_path,
                previews=preview_entries,
                environment_issues=environment_issues,
                missing_images=missing_images,
            )
        )

    if data.get("errors"):
        global_issues.extend(str(item) for item in data["errors"])

    module_reports.sort(key=lambda module: (module.module_path, module.variant))
    return module_reports, global_issues


def resolve_image_path(entry: PreviewEntry, manifest_root: Path, image_root: Path) -> Optional[Path]:
    candidates: List[Optional[str]] = [entry.resolved_output, entry.output_path]
    for candidate in candidates:
        if not candidate:
            continue
        normalized = (Path(candidate).resolve() if os.path.isabs(candidate) else manifest_root / candidate)
        if normalized.exists():
            return normalized
        relative = image_root / candidate
        if relative.exists():
            return relative
    return None


def encode_image(image_path: Path, max_inline_bytes: int) -> Optional[str]:
    try:
        if image_path.stat().st_size > max_inline_bytes:
            return None
        with image_path.open("rb") as fh:
            data = fh.read()
        return base64.b64encode(data).decode("ascii")
    except OSError:
        return None


def truncate_for_table(text: str, max_length: int = 200) -> str:
    text = text.strip()
    if len(text) <= max_length:
        return text
    return text[: max_length - 1].rstrip() + "\u2026"


def format_image_cell(
    *,
    preview: PreviewEntry,
    key: PreviewKey,
    inline: Optional[str],
    rel_path: Optional[str],
    image_mode: str,
) -> str:
    alt_text = html.escape(preview.display_name or key.label or "Preview")
    if inline:
        return (
            f'<img src="data:image/png;base64,{inline}" alt="{alt_text}" '
            "style=\"max-width: 100%; height: auto;\" />"
        )
    if image_mode == IMAGE_MODE_ATTACHMENT and rel_path:
        return f"![{alt_text}](attachment://{rel_path})"
    if image_mode == IMAGE_MODE_LINK and rel_path:
        return f"`{rel_path}`"
    return "_Image unavailable_"


def format_variation_cell(preview: PreviewEntry) -> str:
    variation = truncate_for_table(preview.variation_summary(), max_length=240)
    escaped = html.escape(variation).replace("\n", "<br>")
    extras: List[str] = []
    if preview.render_error:
        extras.append(f"`{html.escape(preview.render_error)}`")
    if extras:
        escaped = f"{escaped}<br><br>{'<br>'.join(extras)}"
    return escaped.replace("|", "\\|")


def format_module_section(
    module: ModuleReport,
    manifest_root: Path,
    image_root: Path,
    max_inline_bytes: int,
    image_mode: str,
) -> List[str]:
    lines: List[str] = []
    lines.append(f"### {module.title}")
    if module.manifest_path:
        lines.append(f"_Manifest_: `{module.manifest_path}`")
    if module.environment_issues:
        lines.append("")
        lines.append("**Environment issues:**")
        for issue in module.environment_issues:
            lines.append(f"- {issue}")
    if module.missing_images:
        lines.append("")
        lines.append("**Missing images:**")
        for issue in module.missing_images:
            lines.append(f"- {issue}")

    if not module.previews:
        lines.append("")
        lines.append("_No previews rendered for this module._")
        lines.append("")
        return lines

    for key, previews in module.previews.items():
        previews.sort(key=lambda entry: entry.display_name.lower())
        summary_label = previews[0].display_name or key.label
        details_title = f"`{summary_label}`" if summary_label else f"`{key.label}`"
        variation_count = len(previews)
        variation_label = "variation" if variation_count == 1 else "variations"
        lines.append("")
        lines.append("<details>")
        lines.append(f"<summary>{details_title} — {variation_count} {variation_label}</summary>")
        lines.append("")
        lines.append("| Variation | Preview |")
        lines.append("| --- | --- |")

        for preview in previews:
            image_path = resolve_image_path(preview, manifest_root, image_root)
            inline = None
            rel_path = None
            if image_path and image_path.exists():
                rel_path = os.path.relpath(image_path, image_root)
            if image_mode == IMAGE_MODE_INLINE and not preview.image_missing and image_path is not None:
                inline = encode_image(image_path, max_inline_bytes)

            variation_cell = format_variation_cell(preview)
            image_cell = format_image_cell(
                preview=preview,
                key=key,
                inline=inline,
                rel_path=rel_path,
                image_mode=image_mode,
            )
            lines.append(f"| {variation_cell} | {image_cell} |")

        lines.append("")
        lines.append("</details>")
        lines.append("")
    return lines



def generate_comment(
    manifest_path: Path,
    module_reports: Sequence[ModuleReport],
    global_issues: Sequence[str],
    identifier: str,
    image_root: Optional[Path],
    max_inline_bytes: int,
    image_mode: str,
) -> str:
    image_root = image_root or manifest_path.parent
    manifest_root = manifest_path.parent
    total_modules = len(module_reports)
    total_previews = sum(len(entries) for module in module_reports for entries in module.previews.values())

    header_lines: List[str] = []
    if identifier:
        header_lines.append(f"<!-- {identifier} -->")
    header_lines.append("## Compose Preview Results")
    header_lines.append("")
    summary = f"Rendered {total_previews} previews across {total_modules} module(s)."
    header_lines.append(summary)
    if global_issues:
        header_lines.append("")
        header_lines.append("**Global issues:**")
        for issue in global_issues:
            header_lines.append(f"- {issue}")

    header_text = "\n".join(header_lines).rstrip() + "\n\n"

    module_sections: List[Tuple[str, str]] = []
    for module in module_reports:
        section_lines = format_module_section(
            module=module,
            manifest_root=manifest_root,
            image_root=image_root,
            max_inline_bytes=max_inline_bytes,
            image_mode=image_mode,
        )
        section_text = "\n".join(section_lines).rstrip()
        if section_text:
            section_text += "\n\n"
        module_sections.append((module.title, section_text))

    comment = header_text
    omitted_modules: List[str] = []
    for title, section in module_sections:
        if not section:
            continue
        prospective = comment + section
        if len(prospective) <= MAX_COMMENT_CHARS:
            comment = prospective
        else:
            omitted_modules.append(title)

    comment = comment.rstrip() + "\n"

    if omitted_modules:
        note_lines = [
            "",
            "⚠️ Some module previews were omitted because the comment approached GitHub's size limit.",
            "The full gallery is available in the `compose-previews` workflow artifact.",
            "",
            "**Omitted modules:**",
        ]
        for title in omitted_modules:
            note_lines.append(f"- {title}")
        note_text = "\n".join(note_lines).rstrip() + "\n"
        if len(comment) + len(note_text) > MAX_COMMENT_CHARS:
            # If we still exceed the limit, keep trimming module names until the note fits.
            while omitted_modules and len(comment) + len(note_text) > MAX_COMMENT_CHARS:
                omitted_modules.pop()
                note_lines = note_lines[:5] + [f"- {name}" for name in omitted_modules]
                note_text = "\n".join(note_lines).rstrip() + "\n"
            if not omitted_modules:
                note_lines = note_lines[:4]
                note_text = "\n".join(note_lines).rstrip() + "\n"
        comment += note_text

    return comment


def main() -> None:
    args = parse_args()
    manifest_path = Path(args.manifest).resolve()
    data = load_manifest(manifest_path)
    module_reports, global_issues = build_module_reports(data)

    image_root = Path(args.image_root).resolve() if args.image_root else manifest_path.parent
    comment = generate_comment(
        manifest_path=manifest_path,
        module_reports=module_reports,
        global_issues=global_issues,
        identifier=args.identifier,
        image_root=image_root,
        max_inline_bytes=args.max_inline_bytes,
        image_mode=args.image_mode,
    )

    if args.output:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(comment, encoding="utf-8")
    else:
        print(comment)


if __name__ == "__main__":
    main()
