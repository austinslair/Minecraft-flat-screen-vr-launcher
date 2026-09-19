# TGS Quest 0.11 — adaptive geometry uploads

One APK: TGS-Renderer-Quest-0.11-arm64.apk. Install over 0.10 and restart your launcher;
select TGS Quest 0.11. The build retains the 0.10 package and signing certificate.
Resolution, render distance, mesh contents, shader effects and frame limits are unchanged.
All previous Quest, matrix, command-streaming and shader-cache fixes remain.

## What changed

Large glBufferSubData updates can wait for GPU readers of the destination. For mutable,
unmapped vertex/index/copy-write buffers, a fresh staging buffer lets a GPU copy perform
the destination update in command order. The destination storage, VAO references and
bytes outside the update remain unchanged. Temporary sources are deleted after copying;
the driver retains in-flight storage as necessary. No unsynchronized mappings, skipped
uploads, dropped draws or geometry simplifications are introduced.

Only updates from 64 KiB through 16 MiB are candidates. Start on the original path. After
an eligible call takes at least 0.5 ms on the CPU, try up to four staged calls in that
context's target/size bucket. If staging takes longer than the triggering direct call
or cannot run, return immediately to direct uploads; otherwise probe direct again after
four calls. This heuristic does not establish end-to-end GPU or Minecraft FPS improvement.
A driver may already stage uploads internally. Temporary GPU storage and extra copies
can increase bandwidth use; the largest individual staging allocation is 16 MiB, not a
bound on all in-flight driver memory. No GPU fence waits are inserted.

Mapped/immutable buffers, active transform feedback, unsupported targets, invalid ranges,
missing entry points and staging-allocation failures retain the original upload path.
No permanent native object names are cached across contexts. The game can continue using
its own persistent mappings and buffer management without this path intercepting them.

## What is and is not verified

Actual Mesa GLES byte comparisons verify partial-update guards and restored copy-read
bindings. Repeated upload/draw tests produce identical pixels. Routing tests cover the
threshold, bounded trial count and fallback. Allocation failure, mapped/immutable cases
and missing entry points are exercised. Previous matrix/indirect/presentation tests pass.

Host timings varied by test order; there is no demonstrated consistent speedup there.
Quest/Adreno FPS and the reported new-chunk hitch have NOT been measured. This is a
performance candidate targeting upload stalls, not a proven fix for all chunk lag.
Java world generation, meshing, GC, shader GPU cost and thermal throttling remain outside
this patch. A pack that uploads using other entry points may never use staging.

TGS_FRAME reports staged_uploads, staged_bytes and direct_large_uploads on the presenting
thread alongside FPS and p95 presentation intervals. Zero staged_uploads means it was
not used in that reporting window. Other threads' counters are not included.
TGS_CHUNK_STAGING=0 disables the adaptive path; 1 enables it. The APK enables it.

Minecraft selection starts at 1.20.1 with no maximum, as in 0.10. This OpenGL plugin has
no Vulkan backend and does not certify every Minecraft/Forge/Fabric/NeoForge combination.

## Build and source

Run tgs-plugin/build.py with --toolchain PATH --java-home PATH --output PATH
--native-build PATH. Requires Android NDK r27d, CMake 3.22.1/Ninja, build-tools 35.0.0,
platform 35 and JDK 17. Tool paths: android-ndk-r27d/, cmake/bin/,
build-tools/android-15/, platforms/android-35/. Complete source/dependencies included.
ARM64, static C++ runtime, Android API 26 minimum, 16 KiB ELF alignment.
Builds use a local test key; preserve your key for subsequent updates.
Tests and exact limitations are in VERIFICATION.txt and gles011.log.

API basis:
https://registry.khronos.org/OpenGL-Refpages/es3/html/glBufferSubData.xhtml
https://registry.khronos.org/OpenGL-Refpages/gl4/html/glCopyBufferSubData.xhtml
https://github.com/KhronosGroup/OpenGL-Registry/blob/main/extensions/EXT/EXT_buffer_storage.txt

MobileGlues and TGS changes: LGPL-2.1-only. Full corresponding source and third-party
notices accompany this APK. https://github.com/MobileGL-Dev/MobileGlues
Independent renderer plugin; no Minecraft assets/code bundled.
