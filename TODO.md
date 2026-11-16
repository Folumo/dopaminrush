# Dopamin Rush – TODO

## Ending (buying "The Final Rush", node id `final`)
- [x] Zoom the tree view all the way out so the whole journey is visible.
- [x] Show the player's journey first: time played, Gather clicks, upgrades bought and Dopamine collected. Clicking moves on.
- [x] Play a video **inside the game window** (JavaFX media). Put the video in `ending.mp4` next to `tree.properties`. If the file is missing or can't be played, the game skips straight to the credits.
- [ ] **Record the video**: a real person explains the effects of dopamine addiction, how it has ended lives, and tries to shake the player into changing something in their own life. Use MP4 with H.264 video and AAC audio.
- [x] After the video, roll the credits. Edit them in `credits.txt`; lines starting with `# ` are headings. Clicking skips the credits.
- [ ] Fill in the real names in `credits.txt`.
- [x] After the credits, the player chooses:
  - **Continue playing**: keep the current run.
  - **Get reborn**: start over with a permanent bonus. Rebirth 1 gives ×2 on all income and clicks, rebirth 2 gives ×3, and so on. Tune it with `REBIRTH_BONUS` in `GameState`.

## Progression
- [x] Nodes can be bought several times, up to a level cap (`max` in the tree; default 10 for squares, 5 for circles, 1 for hexagons). Each level costs 1.3× the one before (`LEVEL_COST_GROWTH` in `GameState`).
- [x] Pacing: `PacingTest` plays the whole tree with a bot that clicks once per second and always buys the quickest payoff. It reaches The Final Rush in about 5 hours. The test fails if that drifts outside 3–12 hours. To see the milestone times: `gradlew test --tests *PacingTest -i`.
- [ ] Play it yourself to check the feel. The bot doesn't get bored, so a few stretches may feel slower than the numbers suggest.

## Helping the player
- [x] "Next goal" button and a pulsing gold ring (or an arrow at the edge of the screen) on the best thing to buy next. It can be turned off in Settings.
- [x] Fog of war: you see what you own and what you can buy next. One step further shows as "???", and anything deeper is hidden. The ending reveals the whole tree. The depth is set by `SIGHT` in `GameApp`.
- [x] Minimap in the bottom-left corner. Click or drag in it to jump around the tree.
- [x] "Welcome back" card showing what was earned while the game was closed.
- [x] Offline earnings start at 5% of income for up to 1 hour. The separate Autopilot side tree (right of Tyrosine) raises them to 80% and 24 hours. The next-goal hint never points there; it's the player's choice. Base values: `BASE_OFFLINE_RATE` / `BASE_OFFLINE_HOURS` in `GameState`.

## The game watches back
- [x] Quiet notes on the left when the game notices something: opening it 5+ times a day, playing between midnight and 5 am, 40+ clicks in 10 seconds, high tolerance, taking a mystery box. Each kind shows at most once per session. Notes, break reminders and achievements queue up instead of replacing each other.
- [x] Tolerance: every purchase adds 2% (up to 80%), which fades over about 10 minutes. It makes purchase sounds lower and pop-ups greyer. Nodes with `calm=true` (Dopamine Detox and the Recovery branch) clear it.
- [x] Mystery box: every 4–8 minutes a box next to the HUD offers a reward for 25% of your Dopamine, with a 20-second countdown. It always pays back less than it costs. Ignoring it gives a minute of income and a note.

## Events, achievements and rebirth
- [x] Golden molecule: every 1.5–3 minutes one drifts across the screen for 14 seconds. Catching it gives either a rush (×3 income and clicks for 30 s) or two minutes of income.
- [x] 12 achievements, each +2% to everything forever. Some are self-aware ("Played after midnight on 3 different nights. Sleep is in the Recovery branch.").
- [x] Rebirth gives 5 Insight, spent in the Journal on 4 permanent perks: start with Dopamine, keep the Autopilot tree, 10% cheaper per level, see one more step into the fog.
- [x] Journal (bottom bar): lifetime stats, an income chart (log scale, last 4 hours of play), achievements and perks.

## Polish
- [x] Background sound for each era (drone and bubbles, heartbeat, fire, city, digital pings), made in code. It can be turned off in Settings.
- [x] The big counter rolls to its new value, and buying a hexagon sends a ring of light across the tree.

## Break reminders
- [x] After every 30 minutes of play (30 min, 1 h, 1 h 30, …), a card on the left side of the game window kindly suggests a break. It closes with "Okay" or hides by itself after a minute.

## Mods
- [x] Optional `mods/` folder next to `tree.properties`. Mods can add nodes or whole branches, change any key of existing nodes, and replace sound effects.
- [x] Mods load at startup in alphabetical order. A broken mod is skipped and listed in a warning. Settings shows the loaded mods and has an "Open mods folder" button.
- [x] Guide: [MODDING.md](MODDING.md), with an example mod in [docs/example-mod](docs/example-mod).
- [x] The tree editor opens the main tree plus a mod ("Open mod..."), so modders can link to main-tree nodes visually and save only the mod's part.
- [x] Mods can translate their nodes (or the main tree) with `tree_<lang>.properties`.

## Localisation (i18n)
- [x] All game UI text is in `src/main/resources/i18n/messages_<lang>.properties`: English, Croatian, German, Spanish and Portuguese. Croatian uses its three plural forms (1 sat, 2 sata, 5 sati).
- [x] The tree is translated in `tree_<lang>.properties` next to `tree.properties` (names, descriptions, currency). A test fails if a language is missing a node or UI text.
- [x] Language picker in Settings. It restarts the game to switch. The default is the system language if supported, otherwise English.
- [x] The ending looks for `ending_<lang>.mp4` and `credits_<lang>.txt` first, then falls back to `ending.mp4` and `credits.txt`.
- [ ] Have native speakers proofread the four translations. They were written by Claude.
- [ ] Translated credits (`credits_hr.txt`, …) once the real names are in `credits.txt`.
- [ ] The tree editor is still English only (it's a tool for creators, not players).

## Release
- [x] Icon drawn in code (`GameIcon`), used by both windows. PNGs and `icon.ico` are in [docs/icon](docs/icon).
- [x] `gradlew zipGame` builds `build/package/DopaminRush-windows.zip`: a "Dopamin Rush" folder with its own Java, so players don't need Java installed. They unzip it and start `Dopamin Rush.exe`. If the game folder isn't writable, progress is saved in `%USERPROFILE%\.dopaminrush\` instead.
- [ ] For a real installer (Start-menu shortcut, uninstaller), install the WiX Toolset and run `gradlew zipGame -PinstallerType=exe`. Note: mods would then live inside the install folder, which isn't writable under Program Files.
