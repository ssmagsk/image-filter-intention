# Project Notes

## January 2, 2026

### Claude CLI Exploration
- Setting a challenge to work with Claude CLI
- Downloading Claude CLI via Homebrew to test it out and explore its functionality
- Source: [Claude Code GitHub Repository](https://github.com/anthropics/claude-code)
  - Claude Code is an agentic coding tool that lives in the terminal
  - Understands codebase and helps code faster through natural language commands
  - Can execute routine tasks, explain complex code, and handle git workflows
  - Installation options: MacOS/Linux (curl), Homebrew, Windows (PowerShell), or NPM
  - Includes plugin system for extended functionality
  - Official documentation available at code.claude.com/docs/en/overview

#### First Launch Experience
- Successfully launched Claude Code v2.0.76
- Welcome screen features ASCII art with a character/pixel art design
- Setup process:
  - Theme selection offered (Dark mode, Light mode, colorblind-friendly options, ANSI colors only)
  - Selected Dark mode
  - Uses Monokai Extended syntax theme
- First interaction shows a code diff example:
  - Demonstrates changing `console.log("Hello, World!");` to `console.log("Hello, Claude!");`
  - Shows inline diff visualization with red (removed) and green (added) lines

![Claude Code Welcome Screen](screenshots/claude-code-welcome.png)
*Note: Screenshot shows initial setup and welcome experience*

### NDK + Image Filters Plan
- Bench Claude Code until API key status confirmed
- Focus: learn NDK with a custom image filter (start with grayscale)
- Milestones agreed:
  - Minimal JNI round-trip with CMake + Kotlin app, passing structs/arrays
  - Implement native hot path (e.g., grayscale/blur) and benchmark vs Kotlin
  - Wrap native function behind bound Service/AIDL with permission gating
  - Add diagnostics (perf/atrace) and native crash unwinding
  - Stretch: prototype HAL-like interface in user space to study framework↔HAL boundary

### Repo Strategy Notes
- Each Android app will live in its own git repo (e.g., `imagefilterintention`)
- Exploring a parent repo with child repos to manage multiple apps
- Questions to answer: single monorepo vs multiple repos; submodules/subtrees vs independent remotes; how to share common code across apps
- Current stance: keep one git per codebase; later, if consolidation is needed, revisit a parent repo using submodules (or alternative) to host everything in one place

### CameraX Switch & Learning
- Switched capture flow to CameraX with front-camera default instead of the framework preview intent
- Added live preview via `PreviewView` embedded in Compose, capture via `ImageCapture`, and display of last shot
- Manifest includes camera permission; Compose UI has a bottom “Open Camera” button
- TODO: pipe captured bitmap to NDK grayscale processing
- Read CameraX overview to understand benefits and use cases (Preview, ImageAnalysis, ImageCapture, Video) [source](https://developer.android.com/media/camera/camerax)

### Recent Coding Updates
- Added negative effect in native (NDK) and hooked it into the Compose pipeline
- Fixed several QoL issues (auto camera launch, theme tweaks, rotation handling)
- Barebones UI for camera lens flip (front/rear) and on-the-fly filter selection (negative/grayscale)

### Bitmap & Native Processing Learnings
- ARGB_8888 layout: 4 bytes/pixel (A,R,G,B). Extract a channel by shifting it into the low byte, then `& 0xFF` to isolate it.
- Grayscale math: `gray = 0.299*R + 0.587*G + 0.114*B`, reassemble with original alpha.
- Negative effect: invert each channel `(255 - R/G/B)`, preserve alpha.
- Bitwise shifts + masks: shift moves the target channel into the low 8 bits; `0xFF` zeroes everything else so you keep only that channel’s 0–255 value.
- Common configs: ARGB_8888 (best default for filters), RGB_565 (no alpha, smaller), ALPHA_8 (masks), RGBA_F16 (HDR/wide color). Avoid HARDWARE for CPU pixel access.
- ARGB_8888 stays the safest format for custom filters and matches the current native pipeline.

### CPU vs GPU Rendering Notes
- CPU path (Kotlin/NDK + SIMD): simpler integration; good for light/medium filters; can use Y plane directly; watch copies/latency at high res.
- GPU path (GL/GLSL): better for heavy/full-res effects (blur/bloom); do YUV->RGB in shader and keep processing on GPU to avoid readbacks.
- Hybrid: run detection (ML Kit) on downscaled CPU frames; render/filters on GPU; policy/override to pick backend.
- Current POC: CPUImageConverter for YUV->RGBA; GPUImageConverter with offscreen GL YUV->RGBA shader + CPU fallback.

### Live Capture Display
- CameraX direct output is now converted and displayed below the preview (live processed bitmap overlay).

### Face Recognition Notes
- Integrated ML Kit face detection; Landmark mode is lighter; Contour mode is heavier but gives more detail.
- Learned to toggle detection; overlays depend on mapping landmarks to view space.
- Current overlay is not fully responsive to view dimension changes; need better coordinate mapping (account for rotation, mirror, scaling) to match faces across different view sizes.

### Color Formats & YUV Learnings
- Compared ARGB_8888 (full 8-bit channels + alpha), RGB_565 (no alpha, lower precision/size), and YUV_420_888 (separate luma/chroma).
- For filters, ARGB_8888 keeps math simple and accurate; RGB_565 saves memory but needs bit packing and loses precision.
- Camera outputs are typically opaque; alpha is usually set to 255 when converting to ARGB_8888.
- YUV advantage: the Y plane already holds luma, so a grayscale filter can read Y directly without RGB conversion—faster and less bandwidth. Planning to try a YUV grayscale path using the Y plane only.