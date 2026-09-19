# TGS Quest 0.10 — single renderer app

Install TGS-Renderer-Quest-0.10-arm64.apk, restart your launcher and select **TGS Quest 0.10**.
This updates the 0.8 Streaming package with the same package identity; no second variant is built.
If the separate 0.8 Reference app is installed, uninstall it manually. Android does not
let this update silently remove a separately installed app.

Minecraft selection starts at **1.20.1**, inclusive, with no maximum metadata version.
This removes the old launcher cutoff; it does not certify every newer game or modpack.
Use the Java runtime required by the game version. Only OpenGL rendering is handled.

The native core retains streaming with automatic upload fallback,
matrix compatibility and all prior Quest fixes. No resolution, quality or frame limit changes.

## Startup change in 0.10

The old policy rewrote the whole shader cache every 16 inserted shaders. Saves now
require at least 16 new entries AND dirty bytes totaling a quarter of current cache
size, or the existing five-second deadline. This deadline is checked by cache
operations, not a timer. Translations remain immediately usable in memory. Due writes
remain synchronous. Cache format, directory, translation output and size limit stay
unchanged. The final batch is saved on orderly exit. An Android process kill can lose
the unsaved batch, requiring its translation again next launch.

This addresses repeated disk writes during shader bursts. It does not establish that
the reported Quest FPS recovery is fixed. Chunk work, Java startup and driver shader
compilation are outside this change. No graphics quality or resolution is reduced.

This rebuild needs a new test signing key because the old private key is absent from
the supplied source. If installation reports a signature conflict, uninstall only the
old TGS renderer plugin before installing this APK. This does not require uninstalling
the launcher or Minecraft. One renderer APK is produced.

## Performance change

Element and array indirect command buffers are mutable renderer-owned scratch
buffers. Streaming maps the complete used range with WRITE and INVALIDATE_BUFFER,
then writes commands directly. The driver can rename busy storage instead of waiting
for previous draws to finish. No UNSYNCHRONIZED flag, no geometry changes, no modification
of mod-owned buffer storage. Map failure or lost contents on unmap triggers a complete
CPU-staged re-upload; the backend is not permanently disabled after a transient failure.

This helps only workloads using those paths, and some drivers may be slower with
mapping. No headset FPS gain is established. The upload counters indicate whether a workload uses this path.

Every 300 successful presentation intervals, `TGS_FRAME` in the renderer log reports
FPS, mean/p95/worst frame interval, mapped/fallback/reference upload counts and bytes.
These are presentation intervals including pacing/compositor waits, NOT GPU execution
times. Different surfaces start a new window of samples. A zero mapped count means
the new path was not used during that measurement. No frame cap is changed.
Set TGS_FRAME_STATS=0 to disable reporting; TGS_STREAM_UPLOADS=0 selects reference uploads.

Compare the same stationary scene, world, resolution, chunks and mods after equal
warm-up. Repeat both orders to limit thermal bias. Lower p95 frame time is better;
world generation, Java GC, simulation and compositor caps can dominate either upload path.

## Mod and version compatibility

This native plugin does not discriminate between Forge, Fabric and NeoForge. It
cannot guarantee every version or modpack: each may require GL features the core
lacks. Newly implemented compatibility: all nine square/rectangular float matrix
shapes, including arrays, support desktop transpose=true in both glUniformMatrix*
and glProgramUniformMatrix*. GLES receives equivalent column-major data with false.
Ordinary transpose=false uploads pass through unchanged. Previous Quest shader,
opaque-alpha, resize, upload and indexed-draw fixes remain.

**Every-version compatibility is not verified.** Some advanced GL entry points remain
unimplemented. Native core tests are not Minecraft or modpack tests. Forge, Fabric and
NeoForge are not blocked by loader name, but mods must use supported graphics features.
The plugin has no Vulkan backend. Mojang has announced a transition toward Vulkan:
https://www.minecraft.net/en-us/article/another-step-towards-vibrant-visuals-for-java-edition

Zalith's RendererPlugin and RendererPluginManager accept nullable version bounds;
maxMCVer is omitted rather than replaced with an invented upper version:
https://github.com/ZalithLauncher/ZalithLauncher2/blob/main/ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/plugin/renderer/RendererPlugin.kt
https://github.com/ZalithLauncher/ZalithLauncher2/blob/main/ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/plugin/renderer/RendererPluginManager.kt
Other launchers may apply their own additional filters.

## Build and validation

Full corresponding source and dependencies are included. Requires Android NDK r27d,
CMake 3.22.1/Ninja, build-tools 35.0.0, platform 35 and JDK 17. Paths under toolchain:
android-ndk-r27d/, cmake/bin/, build-tools/android-15/, platforms/android-35/.
Run tgs-plugin/build.py --toolchain PATH --java-home PATH --output PATH --native-build PATH.
It produces one APK using a local test signing key and ARM64/static C++/16 KiB ELF alignment.
Tests and limitations are recorded in VERIFICATION.txt. Run renderer check in the
app for the native split-shader, named-varying, texture and indirect upload test.

MobileGlues and TGS additions: LGPL-2.1-only (LICENSE). Third-party licenses accompany
source and APK assets. https://github.com/MobileGL-Dev/MobileGlues
This is not an official MobileGlues, launcher, Mojang or Microsoft release.
