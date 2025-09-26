# Jetpack Compose Preview Automation Plan

## Objectives
- Generate PNG images for every `@Preview` composable defined in ControlX2's Android modules (`mobile` and `wear`).
- Publish the preview images as build artifacts from GitHub Actions.
- Automatically post a PR comment summarizing previews with collapsible sections, one per composable/component, each containing its associated images.
- Ensure the workflow is reproducible locally so contributors can validate previews prior to pushing changes.

## Current Status
- ✅ Bootstrapped a `buildSrc` Gradle convention plugin that registers metadata collection and rendering tasks, plus aggregate entry points (`collectAllComposePreviewMetadata`, `renderAllComposePreviews`). The plugin is applied to the `mobile` module so contributors can begin invoking the scaffolding; metadata collection emits structured JSON describing each preview while the render task now produces deterministic filenames, placeholder PNG assets, and a manifest for downstream automation. The placeholder renderer now compiles cleanly after swapping metadata parsing to `Json.decodeFromString` and hoisting JSON summary helpers out of nested data classes.
- 🟡 Replace the placeholder task implementations with actual Compose preview discovery and rendering. Discovery already scans compiled classes to enumerate `@Preview`-annotated composables and the renderer now uses Paparazzi to invoke Compose functions and write PNGs for both the mobile and wear modules. Parameterized previews are now expanded by instantiating their providers, and each invocation records manifest metadata (environment details, parameter descriptors, success/error state). Provider reflection now tolerates iterators, primitive arrays, and Java streams, and Paparazzi invocation reports clearer diagnostics when Compose runtime pieces are missing. Follow-up work focuses on verifying dependency resource harvesting, broadening device/theme coverage, and exercising end-to-end CI runs so the Paparazzi integration is production ready.
- ✅ Introduced an aggregate manifest task that collects the per-module render manifests, verifies expected files exist (including Paparazzi outputs and layoutlib resources), and produces a root-level `build/composePreviews/aggregate/manifest.json`. The task now runs automatically after `renderAllComposePreviews`, failing fast when resources or images are missing so PR automation can rely on consistent artifacts from both the mobile and wear modules.
- ✅ Authored Python helpers (`scripts/generate-compose-preview-comment.py`
  and `scripts/prepare-compose-preview-comment.py`) plus a GitHub Actions
  workflow that render previews on CI, upload the `build/composePreviews`
  artifacts, and post/update sticky PR comments linking back to the
  uploaded artifact instead of relying on comment attachments.
- ✅ Tightened the generated Markdown so preview images are referenced in
  compact tables with direct links into the uploaded artifact contents,
  trimming module sections when the report nears GitHub's comment length
  cap while pointing contributors to the full artifact gallery. The CI
  workflow now bundles each module's preview output alongside the
  aggregate manifest so every linked screenshot is downloadable.
- ✅ Verified `./gradlew renderAllComposePreviews` succeeds locally after wiring the
  corrected resource directories, compiled classpaths, and layoutlib detection into
  the preview renderer.
- ✅ Ensured Paparazzi can render previews on CI even when the Android SDK's
  layoutlib jar is unavailable by falling back to the Maven-distributed layoutlib
  runtime and logging the chosen source. The Compose preview workflow now uploads
  rendered PNGs as GitHub comment attachments so pull request reports display the
  actual images inline with working download links.
- ✅ Hardened the attachment upload helper so GitHub accepts the rendered PNGs by
  posting raw image bytes with explicit `Content-Type`/`Content-Length`
  headers. This resolves the previous 422 "Bad Size" failures and keeps inline
  preview images rendering in PR comments.

## Recommended Technical Approach

### 1. Render previews off-device using Compose tooling
The Compose tooling stack already exposes JVM-side helpers that Android Studio relies on to show previews. We can repurpose the same APIs to render previews during CI:

1. **Discovery**
   - After compilation, Compose produces metadata entries under `build/intermediates/compose_preview` for each variant. The metadata can be loaded via `androidx.compose.ui.tooling.preview.PreviewElementFinder` (from `ui-tooling-preview`) which yields `PreviewElement` descriptors for all `@Preview` annotated functions on the classpath.
   - We can build a lightweight Gradle plugin (in `buildSrc`) that, for every Android application module with Compose enabled, registers a `CollectComposePreviewsTask`. The task depends on the relevant `compileDebugKotlin` task so the compiled classes and preview metadata are available.

2. **Rendering**
   - Use `androidx.compose.ui.tooling.preview.PreviewRenderer` (shipped with `ui-tooling`) with `PreviewElementInstance` objects to render each preview into an off-screen buffer backed by LayoutLib (no emulator required).
   - For previews that rely on fonts, images, or resources, the renderer must be initialized with the module's compiled resource table (`aapt2` packaged assets). The module build already generates a `layoutlib.jar` compatible resource directory at `build/intermediates/compile_and_runtime_not_namespaced_r_class_jar`. Feed this plus the variant's merged resources into the renderer session.
   - Emit PNG files into a deterministic folder such as `build/composePreviews/<variant>/<qualifiedPreviewName>/<configuration>.png`. Handle multiple `@Preview` annotations on the same composable (e.g. size or night mode variations) by suffixing the output filenames with the parameterized configuration name returned by `PreviewElement`.

3. **Task wiring & convenience wrappers**
   - Introduce a shared Gradle convention plugin (`buildSrc/src/main/kotlin/ComposePreviewPlugin.kt`) that:
     - Applies to any Android module with Compose.
     - Registers `collectPreviewMetadata` (outputs JSON describing discovered previews) and `renderComposePreviews` (produces PNGs) per build variant.
     - Exposes an aggregate root-level task `renderAllComposePreviews` that depends on every module's `renderComposePreviewsDebug` task, giving a single entry point for CI and local use.
   - Provide a CLI helper script (e.g. `scripts/render-compose-previews.sh`) that sets `ANDROID_SDK_ROOT` if needed, invokes Gradle, and outputs the folder containing generated images.

### 2. Alternative: Paparazzi-based rendering (fallback plan)
If direct usage of `PreviewRenderer` proves brittle, Square's [Paparazzi](https://github.com/cashapp/paparazzi) offers JVM screenshot testing with Compose support.
- Paparazzi uses LayoutLib under the hood and integrates cleanly with Gradle.
- We can author a generated Paparazzi test class per module that iterates over all discovered `@Preview` functions (using the same metadata scanning) and calls `paparazzi.snapshot` for each configuration.
- Paparazzi automatically stores images under `src/test/snapshots`. The same GitHub Actions flow and PR comment logic apply.
- Drawback: requires Paparazzi plugin configuration and may need additional resources (fonts) to match on-device rendering. Prefer the direct renderer when possible; fall back to Paparazzi only if stability issues arise.

## GitHub Actions Workflow Outline
1. **Triggering**
   - Run on `pull_request` (opened, synchronize, reopened) and optionally `push` to default branch for baseline snapshots.
   - Skip if the changes do not touch Compose code using a path filter (optional optimization).

2. **Environment setup**
   - Use `actions/setup-java@v4` with the version required by the project (currently Java 17 per Gradle configuration).
   - Install Android SDK components via `android-actions/setup-android@v3` or manual `sdkmanager` calls. Configure licenses.
   - Generate `local.properties` with `sdk.dir=$ANDROID_SDK_ROOT` since the project build expects it. (The local Gradle run currently fails without that file.)
   - Cache Gradle wrapper and build caches using `actions/cache` keyed on Gradle files.

3. **Rendering step**
   - Execute the aggregate task: `./gradlew renderAllComposePreviews --stacktrace`.
   - After execution, capture the structured JSON/CSV manifest produced alongside the PNGs to describe which images belong to which preview.
   - Compress the images into an artifact (e.g., `compose-previews-${{ github.sha }}.zip`) using `tar` or `zip`.

4. **Artifact upload**
   - Use `actions/upload-artifact` to persist both the PNGs and the manifest file for manual inspection or downstream jobs.

5. **PR comment generation**
   - Add a subsequent job (or step) that downloads the artifact using `actions/download-artifact`.
   - Run a small Node.js or Python script (`scripts/post-compose-preview-comment.(js|py)`) that:
     - Reads the manifest to group images by previewable composable.
     - Generates Markdown with collapsible `<details>` sections, each named after the fully qualified composable and showing its PNG(s) in a table (supporting variations like light/dark or locale).
     - Posts/updates a sticky comment via `actions/github-script` or `peter-evans/create-or-update-comment`, ensuring older comments are replaced rather than duplicated.
   - Example Markdown skeleton:
     ```markdown
     <details>
       <summary>`LandingScreenPreview` (mobile, light)</summary>

       ![LandingScreenPreview](artifact-url)
     </details>
     ```

6. **Failure handling**
   - Mark the job as failed if preview rendering fails so contributors know to fix Compose errors.
   - If preview generation produces zero previews (e.g., due to metadata issues), fail with a descriptive message unless the module intentionally has none.

## Local Developer Workflow
- Document in `README.md` how to install the Android SDK, set `ANDROID_HOME`/`ANDROID_SDK_ROOT`, and run `./gradlew renderAllComposePreviews` to regenerate previews locally.
- Optionally provide `./gradlew renderAllComposePreviews --continuous` or a Gradle task alias for faster iteration.
- Encourage developers to inspect `build/composePreviews` outputs before pushing to ensure CI parity.

## Verification Strategy Prior to Final PR
- Once the implementation is in place, run `./gradlew renderAllComposePreviews` locally to confirm previews render correctly and produce assets.
- Execute the GitHub Actions workflow on a test branch (via `workflow_dispatch`) to ensure artifacts upload and the PR comment posts as expected before merging to main.

## Open Questions & Risks
- **Preview limitations**: Some previews might rely on runtime-only dependencies (e.g., Compose runtime data flows). We'll need to guard the renderer to provide fake data or catch exceptions, reporting failures per preview instead of aborting the entire task.
- **Resource loading**: Ensure fonts/images referenced via `painterResource` resolve correctly. If not, add pre-test hooks that copy necessary resource packages into the renderer's classpath.
- **Gradle/Compose version compatibility**: The direct `PreviewRenderer` API is relatively new and may change across Compose versions. Encapsulate usage behind our Gradle plugin so updates are localized.
- **Execution time**: Rendering dozens of previews may be slow. Parallelize across modules or variants via Gradle worker API and limit to `debug` variant on CI.
- **Security**: PR comment posting requires `GITHUB_TOKEN` with `pull-requests: write` scope. Ensure the workflow uses the default token and handles forks by running the commenting step only on trusted contexts (e.g., via `if: github.event.pull_request.head.repo.fork == false`).

## Next Steps
1. ✅ Prototype the Gradle preview tooling scaffolding by introducing a `buildSrc` convention plugin for the `mobile` module (placeholder metadata/render tasks + aggregate entry points). Metadata collection now emits structured JSON and the render task provides deterministic placeholder PNGs plus a manifest for downstream tooling.
2. ✅ Replace the placeholder renderer with real Compose rendering (via Paparazzi) so previews produce actual UI imagery. The renderer now instantiates composables via Compose reflection, drives Paparazzi with module resources/runtime classpaths, expands `@PreviewParameter` providers, and emits PNGs plus manifest metadata for each invocation.
3. ✅ Extend the plugin to handle the `wear` module and generate a consolidated manifest for PR consumption. The aggregate manifest task now runs after all module renders, merges manifests from both apps, resolves image paths relative to the repo root, and enforces that required resources and screenshots are present.
4. ✅ Author scripts for Markdown generation and integrate with GitHub Actions as outlined. The helpers now produce sticky-comment Markdown that uploads preview images as comment attachments, and the `compose-previews` workflow drives rendering, artifact uploads, and comment updates on pull requests.
5. ✅ Run the preview rendering command locally as proof-of-concept, then open the implementation PR with CI validation.
