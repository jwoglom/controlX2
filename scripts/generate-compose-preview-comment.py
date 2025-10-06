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
        --artifact-url <artifact-url> \
        --output build/composePreviews/aggregate/comment.md
"""

from __future__ import annotations

import argparse
import base64
import json
import os
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


DEFAULT_IDENTIFIER = "controlx2-compose-previews"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compose preview comment generator")
    parser.add_argument(
        "--manifest",
        required=True,
        help="Path to the aggregate manifest JSON file",
    )
    parser.add_argument(
        "--image-root",
        required=True,
        help="Root directory for resolving image paths",
    )
    parser.add_argument(
        "--image-mode",
        choices=["link", "inline", "attachment"],
        default="attachment",
        help="How to include images: link (markdown links), inline (data URIs), or attachment (placeholder)",
    )
    parser.add_argument(
        "--artifact-url",
        default=None,
        help="URL to the uploaded compose preview artifact",
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
    return parser.parse_args()


def load_manifest(manifest_path: Path) -> Dict[str, Any]:
    """Load and parse the aggregate manifest JSON."""
    if not manifest_path.exists():
        raise FileNotFoundError(f"Manifest file not found: {manifest_path}")
    
    with open(manifest_path, "r", encoding="utf-8") as f:
        return json.load(f)


def encode_image_as_data_uri(image_path: Path) -> str:
    """Encode an image file as a data URI."""
    if not image_path.exists():
        return f"<!-- Image not found: {image_path} -->"
    
    try:
        with open(image_path, "rb") as f:
            image_data = f.read()
        
        # Determine MIME type from file extension
        suffix = image_path.suffix.lower()
        mime_type = {
            ".png": "image/png",
            ".jpg": "image/jpeg",
            ".jpeg": "image/jpeg",
            ".gif": "image/gif",
            ".webp": "image/webp",
        }.get(suffix, "image/png")
        
        encoded = base64.b64encode(image_data).decode("ascii")
        return f"data:{mime_type};base64,{encoded}"
    except Exception as e:
        return f"<!-- Failed to encode image {image_path}: {e} -->"


def format_image_reference(
    image_path: Path,
    image_root: Path,
    image_mode: str,
    artifact_url: Optional[str] = None,
) -> str:
    """Format an image reference based on the specified mode."""
    if image_mode == "inline":
        return f"![Preview]({encode_image_as_data_uri(image_path)})"
    elif image_mode == "link":
        relative_path = image_path.relative_to(image_root)
        return f"![Preview]({relative_path})"
    elif image_mode == "attachment":
        if artifact_url:
            relative_path = image_path.relative_to(image_root)
            download_url = f"{artifact_url}/download/{relative_path}"
            return f"![Preview]({download_url})"
        else:
            return f"![Preview]({image_path.name})"
    else:
        raise ValueError(f"Unknown image mode: {image_mode}")


def generate_comment(
    manifest: Dict[str, Any],
    image_root: Path,
    image_mode: str,
    artifact_url: Optional[str] = None,
    identifier: str = DEFAULT_IDENTIFIER,
) -> str:
    """Generate the comment content."""
    lines: List[str] = []
    
    # Header
    if identifier:
        lines.append(f"<!-- {identifier} -->")
    lines.append("## Compose Preview Results")
    lines.append("")
    
    # Summary
    total_previews = sum(len(module_data.get("previews", [])) for module_data in manifest.get("modules", {}).values())
    total_modules = len(manifest.get("modules", {}))
    summary = f"Rendered {total_previews} previews across {total_modules} module(s)."
    lines.append(summary)
    lines.append("")
    lines.append("📝 **Paparazzi test files generated:**")
    lines.append("- `mobile/src/test/java/com/jwoglom/controlx2/test/snapshots/ComposePreviewPaparazziTest.kt`")
    lines.append("- `wear/src/test/java/com/jwoglom/controlx2/test/snapshots/ComposePreviewPaparazziTest.kt`")
    lines.append("")
    
    # Process each module
    for module_name, module_data in manifest.get("modules", {}).items():
        previews = module_data.get("previews", [])
        if not previews:
            continue
            
        lines.append(f"### {module_name.title()} Module")
        lines.append("")
        
        # Group previews by composable
        composable_groups: Dict[str, List[Dict[str, Any]]] = {}
        for preview in previews:
            composable_name = preview.get("composable", "Unknown")
            if composable_name not in composable_groups:
                composable_groups[composable_name] = []
            composable_groups[composable_name].append(preview)
        
        for composable_name, composable_previews in composable_groups.items():
            lines.append(f"#### {composable_name}")
            lines.append("")
            
            for preview in composable_previews:
                preview_name = preview.get("name", "Unknown")
                image_path = preview.get("imagePath")
                
                if image_path:
                    full_image_path = image_root / image_path
                    image_ref = format_image_reference(
                        full_image_path, image_root, image_mode, artifact_url
                    )
                    lines.append(f"**{preview_name}**")
                    lines.append("")
                    lines.append(image_ref)
                    lines.append("")
                else:
                    lines.append(f"**{preview_name}** - No image generated")
                    lines.append("")
    
    return "\n".join(lines)


def main() -> None:
    args = parse_args()
    
    manifest_path = Path(args.manifest)
    image_root = Path(args.image_root)
    
    manifest = load_manifest(manifest_path)
    comment = generate_comment(
        manifest, image_root, args.image_mode, args.artifact_url, args.identifier
    )
    
    if args.output:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(comment, encoding="utf-8")
    else:
        print(comment)


if __name__ == "__main__":
    main()
