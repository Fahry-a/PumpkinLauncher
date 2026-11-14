# Changelog

All notable changes to PumpkinLauncher are documented in this file.

The top section is the current release. Its version must match the `VERSION`
file; when both agree, `auto-tag.yml` creates the `v<version>` tag and the
release notes for that version are taken from its section below.

## [0.1.0] - 2026-11-14

### Added

- Themed sidebar drawer (☰) replacing the top-right popup menu, with a live
  status dot; the "Worlds" menu entry is removed (worlds are managed from
  Server Manager → Files).
- Server Manager screen with three tabs: server folder location, file
  explorer, and launch settings.
- Server folder location picker with a quick path
  (`/storage/emulated/0/PumpkinLauncher`), all-files access handling, and a
  reset to the default app folder.
- File explorer with navigation, create folder/file, rename, copy, cut,
  paste, delete, and "Set as server folder".
- Simple text editor opened from the file explorer for text files (binary
  files are rejected).
- Launch settings: RAM limit, pre-launch command, wrapper command, and args,
  persisted per install.
- Console stats cards for RAM, CPU, and network usage, plus Save, Restart,
  and Backup action buttons.
- Console quality of life: pause/resume auto-scroll, copy and clear log
  buttons, and command history (long-press the input, or arrow-up/down).

### Changed

- The engine, plugins folder, and `server.properties` now resolve from the
  selected server folder instead of the legacy worlds folder.
- New engine bridge entry point applies the RAM limit with `setrlimit` and
  restores it on stop; older engine `.so` files keep working through a
  fallback to the legacy entry point.
- Console output scrolling stops while paused and resumes on demand.

### Fixed

- The software keyboard no longer covers the console command input
  (`adjustResize` plus insets that account for both system bars and the IME).
- Storage access no longer crashes on Android 10: "all files access" is used
  on API 30+, while API 26–29 request `WRITE_EXTERNAL_STORAGE` with legacy
  external storage enabled.

## [0.0.1] - 2026-11-13

### Added

- Initial release: console UI, worlds and configuration management, plugin
  management, engine version manager, and engine distribution flow.
