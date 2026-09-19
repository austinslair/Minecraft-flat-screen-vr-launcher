<div align="center">

# HorizonShelf

**A cleaner way to bring flat-screen apps into a VR-first workflow.**

HorizonShelf is a launcher concept built around one simple idea: using normal apps in VR should feel intentional, fast, and native to the headset experience—not like digging through a desktop from inside a visor.

</div>

---

## Why HorizonShelf?

Most flat-screen launchers describe *what* they are. HorizonShelf is built around *how it should feel*: a focused shelf for the apps you actually want while you are in VR.

The project is aimed at making traditional 2D applications easier to discover, organize, and launch without turning the headset into a cluttered desktop replacement.

## Project direction

HorizonShelf is intended to grow around a few core principles:

- **VR-first navigation** — controls and layout should make sense from inside a headset.
- **Fast access** — fewer steps between entering VR and opening the app you want.
- **Clean presentation** — flat apps should be presented as a deliberate library, not a file dump.
- **Flexible organization** — favorites, categories, recent apps, and custom collections should fit naturally.
- **Low overhead** — the launcher should stay out of the way once an app is running.
- **Platform-friendly design** — keep the core launcher logic separated from headset/runtime-specific integrations where possible.

## Planned experience

The long-term experience is centered around a simple flow:

```text
Enter VR
   ↓
Open HorizonShelf
   ↓
Find an app by shelf, search, category, or recent activity
   ↓
Launch it
   ↓
Return to the shelf when you're done
```

## Repository layout

```text
.
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.yml
│   │   └── feature_request.yml
│   └── PULL_REQUEST_TEMPLATE.md
├── assets/
│   └── .gitkeep
├── docs/
│   ├── ARCHITECTURE.md
│   └── ROADMAP.md
├── .editorconfig
├── .gitignore
├── CONTRIBUTING.md
├── SECURITY.md
└── README.md
```

## Development status

This repository is structured as the home for the launcher, its documentation, release notes, and future runtime integrations. Implementation details can evolve without tying the project identity to a specific framework or VR runtime.

## Design goals

A good HorizonShelf build should make these answers easy:

1. **Can I get to my app quickly?**
2. **Can I understand the interface without leaving VR?**
3. **Can I organize the apps I care about without micromanaging the launcher?**
4. **Does the launcher disappear from the way when I no longer need it?**

If a feature does not improve one of those areas, it probably does not belong in the core experience.

## Roadmap

The working roadmap lives in [`docs/ROADMAP.md`](docs/ROADMAP.md). It is intentionally organized by milestones rather than fixed dates so the project can move without publishing fake deadlines.

## Contributing

Ideas, bug reports, implementation work, and VR UX feedback are welcome. Read [`CONTRIBUTING.md`](CONTRIBUTING.md) before opening a pull request.

For security-sensitive reports, use the process in [`SECURITY.md`](SECURITY.md) instead of a public issue.

---

<div align="center">

**HorizonShelf** — flat apps, placed where they belong in VR.

</div>
