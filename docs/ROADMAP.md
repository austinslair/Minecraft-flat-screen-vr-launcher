# HorizonShelf Roadmap

This roadmap describes the intended order of work without locking the project to artificial release dates.

## Milestone 1 — Foundation

- Define the launcher data model.
- Establish app discovery and library indexing boundaries.
- Create a small, predictable configuration format.
- Build the first usable launcher shell.
- Keep runtime-specific code isolated behind clear interfaces.

## Milestone 2 — Library experience

- Favorites and recent apps.
- Categories or user-defined collections.
- Search and filtering.
- App metadata and artwork handling.
- Empty, loading, and failure states that are readable in VR.

## Milestone 3 — VR interaction

- Controller-friendly selection and navigation.
- Comfortable focus, scale, spacing, and motion behavior.
- Reliable return-to-launcher flow.
- Clear launch state feedback.
- Runtime integration layer for supported VR environments.

## Milestone 4 — Reliability

- Graceful handling of missing or moved applications.
- Configuration migration strategy.
- Logging that is useful without being noisy.
- Crash-safe state persistence.
- Performance profiling for startup, scanning, and library rendering.

## Milestone 5 — Release polish

- Repeatable builds.
- Versioned releases and changelog discipline.
- Screenshots and setup documentation.
- Contributor documentation for adding platform/runtime integrations.
- Issue triage and compatibility labels.

## Not a core goal

HorizonShelf should avoid becoming a full desktop environment. Features that add complexity without improving app discovery, organization, launching, or VR usability should live outside the core whenever possible.
