# Contributing to VoxyQuest

Contributions should keep VoxyQuest focused, maintainable, and reliable across both VR and Flat Screen gameplay.

## Before changing code

1. Check existing issues and pull requests for overlapping work.
2. Preserve working behavior unless the change intentionally replaces it.
3. Keep VR-specific, Flat Screen, input, instance, and mod-loader logic separated where practical.
4. Prefer small, testable changes over broad rewrites.
5. Measure performance changes instead of guessing.

## Pull requests

A useful pull request should include:

- What changed.
- Why the change is needed.
- How it was tested.
- Whether it affects VR, Flat Screen Mode, mods, instances, or a specific mod loader.
- Screenshots or short recordings for visible UI changes when practical.

## Bug reports

Include the smallest reproducible description you can provide. Mention the VoxyQuest build or commit, Minecraft version, runtime or platform, whether you were using VR or Flat Screen Mode, relevant mods or mod loader, expected behavior, actual behavior, and useful logs.

## Performance changes

Do not trade noticeable visual quality, gameplay quality, or stability for tiny benchmark wins. Performance work should target measurable bottlenecks such as startup time, instance loading, mod discovery, input latency, memory use, or frame-time overhead.

## Project scope

VoxyQuest is focused on making Minecraft easier to launch and configure for VR and Flat Screen gameplay, with better performance, instance management, and mod-loader support.
