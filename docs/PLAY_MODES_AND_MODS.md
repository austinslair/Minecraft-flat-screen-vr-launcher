# Play modes, installations, and mods

In Instances, choose a supported catalog version and an instance name, then Install.
The catalog contains the Minecraft versions with bundled Vivecraft/runtime mod entries;
it is not an unrestricted list of every Minecraft release. Downloads run off the UI thread.
Use Repair / resume selected to rerun installation for an existing instance with the same
version. Use a separate instance for a different Minecraft version.

Fabric instances go up to Minecraft 1.21.11, using the Quest OpenXR Vivecraft builds that
QuestCraft publishes for 1.21.8, 1.21.10 and 1.21.11. NeoForge instances go up to 1.21.8. CI
builds that Vivecraft from the fork's `OpenXR-1.21.8` branch, whose NeoForge support is not
otherwise released, and bundles the newest NeoForge 21.8 release. NeoForge 21.9 and later no
longer start through ModLauncher, which the launcher's NeoForge support relies on. Minecraft
26.x has no Quest Vivecraft build yet. None of the new versions have been tested on a headset.

Instances can be selected, renamed, removed with confirmation, or repaired. The play mode
selector chooses Virtual reality or Flatscreen for the next launch. Home's Quick Info and
Play tooltip reflect that choice. Sign in and finish installing before pressing Play.
The mode is currently a launcher-session choice, not a persistent per-instance setting.

VR launches the existing OpenXR game activity. Flatscreen launches a separate Android
SurfaceView activity backed by an EGL window surface, with keyboard and mouse input and
pointer capture. Flatscreen has no touch movement/gamepad layout yet. A destroyed game
surface restarts the launcher, because the embedded JVM cannot safely be started twice.
Test suspend/resume behavior on the target headset before treating this as release-ready.

In Mods, select Add mod JAR, choose a Fabric JAR with Android's file picker, then refresh
when you return. Import validates archive metadata, limits file/metadata size, rejects
existing filenames and duplicate mod IDs, and leaves managed files untouched. Use mods
for the selected Minecraft version and install their dependencies. The importer refuses
writes during installs or gameplay. Mod removal and enable/disable controls are not
available yet.

The Android launcher also searches Modrinth for mods filtered to the selected instance's
loader and Minecraft version. Results only include mods that have a build for both, because
Modrinth's filters match a mod's versions separately. Choose a result and Install selected. The
bridge selects a compatible version, resolves required Modrinth dependencies, checks SHA-512
hashes and mod metadata, and refuses to overwrite installed files.

A dependency pinned to a build for another Minecraft version is replaced by the newest
compatible build of the same mod. A dependency that has no build for the instance, or is
hosted outside Modrinth, no longer stops the install. The result message lists it, and
dependencies already in the mods folder are left out of that list. Unusual characters in
filenames are replaced. NeoForge library JARs that have no `neoforge.mods.toml` are accepted. Search and downloads
run off the UI thread. Network access is required; desktop previews cannot download
mods. Local JAR imports still require you to provide dependencies separately.

Validation required on a headset: install a catalog version, interrupt and resume a
transfer, rename the instance, import a compatible mod, sign in, and launch each mode.
Confirm VR tracking, flat mouse capture/release, keyboard movement, sound, and return to
launcher. Headless UI tests cannot verify these device behaviors.

## Performance defaults

Each launch sets vsync on for Flatscreen, which stops Android from rendering frames it
throws away, and off for VR, where OpenXR paces frames. The first launch of an instance
after this update also turns off GL debug output, moves nearby chunk rebuilds off the render
thread, fixes an out-of-range simulation distance, and switches the VR menu world to the
panorama. A later update turns off Vivecraft's desktop mirror, which nobody sees on a
headset. The mirror still cost a full-window copy every frame, and a whole extra world render
in its first- and third-person modes. The same update lowers the simulation distance to 8
if it was higher. Java gets only 3 CPU threads on the Quest, and they are shared by rendering,
the built-in server and chunk building. Render distance is not changed. New instances get
these defaults on their second launch, once Minecraft has written its settings. Later
changes you make in-game are kept. Repair no longer overwrites
`options.txt`, mod configs or the server list. The JVM now honors thread priorities, and
Minecraft's render thread starts with display priority.

## Renderer

Settings → Renderer chooses the OpenGL translation layer for VR and Flatscreen launches.
The default is MobileGlues (`flat_screen/renderer/`, built by CI as `libmobileglues.so`).
It keeps translated shaders in a persistent cache under the app's private `mobileglues/`
directory, which you can tune in `config.json` there. It also enables the TGS Quest
upload patches, which reduce the stalls heavy mod packs cause while streaming chunks and
textures. In VR, the OpenXR runtime's swapchain textures reach MobileGlues as names it did
not create. It passes them straight to the driver and starts tracking them on first use.
LightThinWrapper, the renderer VoxyQuest used before, is still available. Switch to it if a
mod renders incorrectly or VR fails to start. The launch log records which renderer ran.
MobileGlues has not yet been measured on a headset.

## Frame rate

Minecraft's frame cap defaults to Unlimited. Existing instances get this once, and a cap
you set yourself afterwards is kept. Frames are still paced to the display:
- **VR:** Vivecraft asks the headset for its highest refresh rate (120 Hz on Quest 3).
  If the request fails, VR keeps the headset's default instead of failing to start.
  Instances patched by earlier builds are restored from `voxyquest-backups` and patched again.
- **Flatscreen:** the game window asks Android for the display's fastest mode, and vsync
  follows it. The launch log records the rate you got.

The JVM heap can now grow to 3 GiB, but never past half of the memory free at launch.
Higher render distances need that headroom, and a full heap costs frames in collector work.
Whether a given mod pack and render distance reach 100 fps still depends on the headset,
the mods and the world. Minecraft's F3 screen shows the frame rate you actually get.

## Loading time

- **No bytecode verification** for game and mod classes, which a modded game loads by the
  tens of thousands.
- **MobileGlues shader cache:** the first launch translates every shader. Later launches
  reuse the cache.

Any of these flags that an older Java runtime does not recognise is ignored. Class Data
Sharing would help too, but the bundled Java 22 runtime ships without the base archive it needs.
The launch log timestamps each stage, so Settings → Export log shows where the time goes.

## NeoForge versions

NeoForge 1.21.1 instances run NeoForge 21.1.228, which Create 6's Sable dependency requires.
An instance installed with an older bundled NeoForge is moved to the bundled build the next
time you press Play. That launch downloads the new NeoForge libraries, so it needs network
access and takes longer. Worlds, mods and configs are kept.

## Mods with native libraries

Some mods ship native code built only for desktop computers. The Quest cannot load it, so
the launcher carries Android builds for these mods:

- **Distance Horizons (Fabric and NeoForge):** DH compresses its LOD data with zstd and
  stops the game at startup if the library does not load. The Android build is used
  automatically.
- **Sable, which Create 6 needs:** Sable unpacks its physics library into the game folder on
  shared storage and crashes when a world loads. Android does not allow loading native code
  from there. On launch, VoxyQuest patches Sable's loader to use the Android build. It keeps
  the original JAR in `voxyquest-backups`. Only Sable 2.0.5 is supported, because each Sable
  release changes the library's interface. The launch log says when another version is found.

Distance Horizons in VR has not been tested on a headset. It renders its LODs once per eye,
so expect a larger frame-rate cost than on a flat screen.

## Create Aeronautics in VR

Create Aeronautics' physics assembler lever, throttle lever and steering wheel move while
you hold use and move the mouse, and let go when you release the button. Vivecraft provides
neither, so in VR the assembler lever could not be pulled. On VR launches, VoxyQuest adds a
small class to Simulated, the library Aeronautics is built on, and calls it every tick.
Hold the trigger on the control and tilt the main-hand controller instead:
- **Levers:** tilt the controller up to pull the lever up.
- **Steering wheel:** turn the controller left or right.

The movement uses the same scale as mouse look, so about 40° of tilt pulls the assembler lever
all the way. Letting go of the trigger releases the control, which is when the assembler
builds the contraption. The original JAR is kept in `voxyquest-backups`. A Simulated release
whose code looks different is left unpatched, and the launch log says so.
