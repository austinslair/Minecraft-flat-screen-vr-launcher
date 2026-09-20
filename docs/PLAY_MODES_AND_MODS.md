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
for the selected Minecraft version and install their dependencies. Import is not a
compatibility resolver or a Modrinth browser. The importer refuses writes during installs
or gameplay. Mod removal and enable/disable controls are not part of this change.

Validation required on a headset: install a catalog version, interrupt and resume a
transfer, rename the instance, import a compatible mod, sign in, and launch each mode.
Confirm VR tracking, flat mouse capture/release, keyboard movement, sound, and return to
launcher. Headless UI tests cannot verify these device behaviors.
