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
- [ ] Add an optional `mods/` folder where the community can make their own mods and expansions. For example, a mod could add extra nodes or whole new branches to the tree, or change texts, sounds or translations.
- [ ] Load mods when the game starts. A broken mod should show an error and be skipped instead of crashing the game.
- [ ] Write a short guide on how to make a mod. The tree editor can already be used to build mod trees.

## Localisation (i18n)
- [ ] Move all UI text into translation files (Java `ResourceBundle`).
- [ ] Make the tree content (node names and descriptions) translatable too.
- [ ] Add a language picker in Settings.
