# Contributing to HorizonShelf

Contributions should keep the launcher focused, maintainable, and comfortable to use in VR.

## Before changing code

1. Check existing issues and pull requests for overlapping work.
2. Keep platform/runtime-specific behavior separated from shared launcher logic.
3. Prefer small changes with a clear reason over broad rewrites.
4. Preserve existing behavior unless the change intentionally replaces it.

## Pull requests

A useful pull request should include:

- What changed.
- Why the change is needed.
- How it was tested.
- Any VR runtime, platform, or hardware assumptions.
- Screenshots or short recordings for visible UI changes when practical.

## Bug reports

Include the smallest reproducible description you can provide. Mention the VR runtime/platform, build or version, expected behavior, actual behavior, and any relevant logs.

## Performance changes

Do not trade noticeable visual quality or interaction quality for tiny benchmark wins. Performance work should target measurable bottlenecks such as startup, library scanning, artwork loading, input latency, or per-frame work.

## Project scope

The core project is for discovering, organizing, and launching flat-screen applications from a VR-oriented interface. Large desktop-environment features should be proposed separately before implementation.
