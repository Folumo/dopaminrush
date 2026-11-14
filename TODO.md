# Dopamin Rush – TODO

## Ending (buying "The Final Rush", node id `final`)
- [x] Zoom the tree view all the way out so the whole journey is visible.
- [x] Play a video **inside the game window** (JavaFX media). Put the video in `ending.mp4` next to `tree.properties`. If the file is missing or can't be played, the game skips straight to the credits.
- [ ] **Record the video**: a real person explains the effects of dopamine addiction, how it has ended lives, and tries to shake the player into changing something in their own life. Use MP4 with H.264 video and AAC audio.
- [x] After the video, roll the credits. Edit them in `credits.txt`; lines starting with `# ` are headings. Clicking skips the credits.
- [ ] Fill in the real names in `credits.txt`.
- [x] After the credits, the player chooses:
  - **Continue playing**: keep the current run.
  - **Get reborn**: start over with a permanent bonus. Rebirth 1 gives ×2 on all income and clicks, rebirth 2 gives ×3, and so on. Tune it with `REBIRTH_BONUS` in `GameState`.

## Break reminders
- [x] After every 30 minutes of play (30 min, 1 h, 1 h 30, …), a card on the left side of the game window kindly suggests a break. It closes with "Okay" or hides by itself after a minute.

## Mods
- [x] Optional `mods/` folder next to `tree.properties`. Mods can add nodes or whole branches, change any key of existing nodes, and replace sound effects. Translations come once i18n is done.
- [x] Mods load at startup in alphabetical order. A broken mod is skipped and listed in a warning. Settings shows the loaded mods and has an "Open mods folder" button.
- [x] Guide: [MODDING.md](MODDING.md), with an example mod in [docs/example-mod](docs/example-mod).
- [ ] Let the tree editor open the main tree plus a mod, so modders can link to main-tree nodes visually and save only the mod's part.
- [x] Mods can translate their nodes (or the main tree) with `tree_<lang>.properties`.

## Localisation (i18n)
- [x] All game UI text is in `src/main/resources/i18n/messages_<lang>.properties`: English, Croatian, German, Spanish and Portuguese. Croatian uses its three plural forms (1 sat, 2 sata, 5 sati).
- [x] The tree is translated in `tree_<lang>.properties` next to `tree.properties` (names, descriptions, currency). A test fails if a language is missing a node or UI text.
- [x] Language picker in Settings. It restarts the game to switch. The default is the system language if supported, otherwise English.
- [x] The ending looks for `ending_<lang>.mp4` and `credits_<lang>.txt` first, then falls back to `ending.mp4` and `credits.txt`.
- [ ] Have native speakers proofread the four translations. They were written by Claude.
- [ ] Translated credits (`credits_hr.txt`, …) once the real names are in `credits.txt`.
- [ ] The tree editor is still English only (it's a tool for creators, not players).
