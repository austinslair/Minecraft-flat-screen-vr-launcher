# Play modes, installations, and mods

In Instances, choose a supported catalog version and an instance name, then Install.
The catalog contains the Minecraft versions with bundled Vivecraft/runtime mod entries;
it is not an unrestricted list of every Minecraft release. Downloads run off the UI thread.
Use Repair / resume selected to rerun installation for an existing instance with the same
version. Use a separate instance for a different Minecraft version.

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

The Android launcher also searches Modrinth for Fabric mods filtered to the selected
instance's Minecraft version. Choose a result and Install selected. The bridge selects
a compatible version, resolves required Modrinth dependencies, checks SHA-512 hashes
and Fabric metadata, and refuses to overwrite installed files. Search and downloads
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
panorama. Later changes you make in-game are kept. Repair no longer overwrites
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
