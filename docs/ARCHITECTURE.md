# VoxyQuest Architecture Notes

VoxyQuest should keep its core launcher behavior separated from VR-specific, Flat Screen, input, instance, and mod-loader integrations so new compatibility work does not destabilize the rest of the project.

## Suggested boundaries

### 1. Instance management

Responsible for representing Minecraft instances and their settings.

Typical responsibilities:

- Minecraft version
- Instance name and metadata
- Launch arguments
- Game directory
- Java/runtime configuration
- Per-instance settings
- Selected mod loader

### 2. Launch service

Responsible for turning an instance configuration into a launch request and reporting success or failure back to the UI.

VR and Flat Screen launch differences should be handled through clear launch options rather than duplicated launcher flows.

### 3. VR / Flat Screen mode adapter

Owns mode-specific behavior such as VR runtime integration, Flat Screen startup options, input mode selection, and mode-specific compatibility handling.

### 4. Mod-loader integration

Keeps Forge and future mod-loader support behind dedicated adapters where possible.

The rest of the launcher should not need to know loader-specific implementation details just to display or launch an instance.

### 5. Input

Keeps controller, headset, keyboard, and mouse handling separate from launcher state so Flat Screen keyboard and mouse support can evolve without breaking VR controls.

### 6. Presentation

Owns instance browsing, launch feedback, settings, compatibility messages, loading states, and errors.

Avoid coupling UI components directly to runtime- or loader-specific APIs.

## Performance rules

- Keep expensive scanning and discovery work outside render/update hot paths.
- Cache instance and mod metadata where safe.
- Avoid repeatedly probing unchanged files or installations.
- Keep per-frame allocations low in VR-sensitive code.
- Prefer event-driven updates over constant polling when possible.
- Profile before and after optimization work.

## Failure handling

A broken instance, unsupported mod loader, failed VR runtime, or bad mod should be treated as a recoverable error. One bad configuration should not stop other VoxyQuest instances from loading or launching.
