# Architecture Notes

HorizonShelf should keep its core behavior independent from any single VR runtime or UI framework.

## Suggested boundaries

### 1. App catalog

Responsible for discovering, indexing, and describing launchable applications.

Typical responsibilities:

- Application identity
- Display name and metadata
- Executable or launch target
- Artwork/icon reference
- Last-used information
- User-defined organization

### 2. Launcher service

Responsible for turning a catalog entry into a launch request and reporting success or failure back to the UI.

This layer should not own presentation logic.

### 3. Persistence

Stores user-facing state such as favorites, collections, preferences, and recent history.

Keep the format versioned so future builds can migrate old data safely.

### 4. VR/runtime adapter

Contains runtime-specific behavior such as overlays, focus rules, input translation, window/surface handling, or headset lifecycle events.

The rest of the application should communicate with this layer through a narrow interface.

### 5. Presentation

Owns library browsing, search, categories, launch feedback, settings, and empty/error states.

Avoid coupling UI components directly to platform APIs.

## Performance rules

- Do expensive scanning outside the render/update hot path.
- Cache metadata and artwork where safe.
- Avoid repeatedly probing unchanged applications.
- Load large artwork lazily.
- Keep per-frame allocations low.
- Prefer event-driven updates over constant polling when the platform allows it.

## Failure handling

The launcher should treat missing apps, broken metadata, unavailable runtimes, and launch failures as recoverable states. One bad catalog entry should not prevent the rest of the library from loading.
