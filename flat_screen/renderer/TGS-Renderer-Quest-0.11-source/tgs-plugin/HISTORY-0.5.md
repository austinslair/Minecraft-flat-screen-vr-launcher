# TGS Quest Renderer 0.5 — experimental rendering correction

Target: Minecraft Java 1.21.1 in a flat Android window on Quest 3S, through
ZalithLauncher 2 or DroidBridge. Based on the supplied TGS 0.1 MobileGlues fork.
This APK is a renderer plugin, not a launcher, Windows driver, or immersive VR app.
The black sky/menu symptoms have not been reproduced on a physical Quest here.
The changes below are tested code corrections and a compositor hypothesis;
complete Minecraft/headset compatibility is still unverified.

## Install

1. Install `TGS-Renderer-Quest-0.5-arm64.apk` on Quest using your existing method.
   Its separate package coexists with earlier TGS builds; do not remove your launcher.
2. Restart ZalithLauncher 2 or DroidBridge, then select
   **TGS Quest 0.5 (MobileGlues fork)** in the instance's renderer setting.
3. Open Minecraft 1.21.1 and test the title panorama, Settings, Singleplayer and sky.
   Retain your current Java 21 and visual settings. No quality reduction is required.
4. The plugin's **Run renderer check** can test the included native pipeline.

## Corrections

### Desktop shader linking semantics

The GLSL-to-SPIR-V translator enables automatic location mapping. Previously
SPIRV-Cross emitted those invented locations as explicit ESSL declarations. For
legacy desktop GLSL, vertex attributes must remain bindable with glBindAttribLocation,
and vertex/fragment varyings must match by name. Compiling stages independently
can assign different locations to the same name. This is a real translation defect;
it is not proof that this defect alone caused the supplied Quest screenshots.

For original GLSL 150 and earlier with no mention of location qualifiers or separate
shader objects, the new pass removes generated vertex input/output and fragment
input locations. Fragment output attachment locations and built-in variables are
preserved. Explicit-location and newer shaders remain unchanged. The cache key
includes the new translation revision and shader stage, preventing stale translated
sources from bypassing the correction.

Multi-string glShaderSource input is now submitted as exactly one translated string,
matching the actual one-pointer array. This removes an out-of-bounds driver read.

### Flat-window presentation experiment

The launcher requests an RGBA EGL configuration. This build tests forcing the final
window's alpha to one before presenting, to avoid transparency interpretation of
an otherwise opaque desktop game image. This is a hypothesis based on the difference
between a desktop image and Android window composition, not a documented universal
Quest bug. Zalith's TextureView already declares itself opaque; compositor behavior
may therefore be unrelated to the reported fault.

`TGS_OPAQUE_WINDOW=1` enables an alpha-only clear of framebuffer zero on known window
surfaces, only when that exact display/surface/context is current and the GLES 3.2
indexed-mask functions are available. RGB, intermediate render targets, depth and
stencil remain untouched. Draw framebuffer, indexed color mask, scissor/discard state
and clear color are restored. Pbuffers, pixmaps and unknown surfaces are excluded.
All three window-creation entry points are tracked; successful destroy/terminate
removes the records. Damage swaps use full presentation because the alpha plane is
updated across the window. This adds per-frame state queries and an alpha clear;
no FPS improvement is claimed. Set the environment value to 0 before loading the
renderer (or rebuild with 0) to disable this experiment.

### Preserved functionality

The original ARM64 NEON texture-upload conversion stays enabled. No frame skipping,
resolution cuts, reduced draw distance or disabled visual effects are introduced.
Invalid/zero surface extents no longer trigger FSR framebuffer recreation. Quest
plugin startup can use the launcher's absolute TMPDIR for its config directory when
MG_DIR_PATH is absent. Explicit MG_DIR_PATH takes precedence. This app's resizable
setup window and optional-touchscreen manifest do not modify the host launcher's
window, input, resizing or surface-lifecycle implementation.

## Validation

- Real SPIRV-Cross host test: reproduces generated input locations and verifies their
  removal for vertex/fragment stages, retaining fragment outputs and explicit-shader
  exclusion. This is a compiler-level test, not an on-device game run.
- Mock-driver presentation test: alpha becomes opaque; RGB, offscreen colors,
  other render-target masks and saved state remain unchanged; absent API bypasses.
- Host texture test: 4,128 size/alignment cases, alpha preservation and guard checks.
- Bundled device test: textured draw/readback, split sources, vertex attribute bound
  to slot 5, and vertex/fragment varying declarations deliberately reordered.
  Device tests have not been executed on Quest here.
- ARM64 build, APK signature, ZIP, native exports and 16 KiB ELF alignment checked.

## Research

Meta describes 2D apps as ordinary Android apps shown in resizable VR windows:
https://developers.meta.com/horizon/documentation/android-apps/horizon-os-apps/
Quest supports GLES/Vulkan, whereas PC desktop viewing displays a PC-rendered image:
https://developers.meta.com/horizon/documentation/native/android/os-vulkan-opengl/
https://learn.microsoft.com/en-us/windows/mixed-reality/enthusiast-guide/other-questions
Source inspected for the RGBA EGL config and opaque TextureView:
https://github.com/ZalithLauncher/ZalithLauncher2

## Rebuild

The source archive includes the core, initialized build dependencies and this
plugin. Obtain these Android tools from Google's Android SDK distribution:

```
toolchain/android-ndk-r27d/
toolchain/cmake/bin/{cmake,ninja}            # CMake 3.22.1
toolchain/build-tools/android-15/           # build-tools 35.0.0
toolchain/platforms/android-35/android.jar  # API 35
```

Use JDK 17. Run from the extracted source root:

```sh
python3 tgs-plugin/build.py --toolchain /absolute/path/to/toolchain \
  --java-home /absolute/path/to/jdk-17 --output /absolute/path/to/output \
  --native-build /absolute/path/to/native-build
g++ -std=c++17 -O2 tgs-plugin/tests/swizzle_test.cpp -o swizzle-test
./swizzle-test
```

The builder makes a local debug signing key. A fresh key cannot update an APK
signed with a different key; uninstall only the TGS test plugin if rebuilding
with a new key. The official launcher and renderer are separate packages.
Native libraries use a static C++ runtime and 16 KiB ELF page alignment.

## Attribution and source

MobileGlues: https://github.com/MobileGL-Dev/MobileGlues — LGPL-2.1-only.
TGS changes and test plugin: LGPL-2.1-only; see the supplied LICENSE.
Third-party licenses are retained alongside the dependency source and in APK
assets. The complete corresponding source accompanies these APKs; modified
files and build scripts can be used to rebuild or relink the renderer.

DroidBridge's published plugin metadata was inspected for interoperability;
no proprietary DroidBridge launcher code is included. This is not an official
MobileGlues, DroidBridge, Mojang or Microsoft release.
