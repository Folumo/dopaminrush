# Making mods for Dopamin Rush

A mod is a folder inside the game's `mods` folder, which sits next to `tree.properties`. In the game, **Settings → Open mods folder** opens it for you, and creates it if it doesn't exist yet. Restart the game after adding, changing or removing a mod.

```
mods/
  my-mod/
    mod.properties     (optional) name, version, author
    tree.properties    (optional) new nodes, or changes to existing ones
    sounds/            (optional) replacement sound effects
      gather.wav
```

There's a ready-made example in [`docs/example-mod`](docs/example-mod). Copy that folder into `mods/` to see it in the game: it adds a small branch to the right of Exploration.

## mod.properties

```properties
name=Thrill Seekers
version=1.0
author=Your name
```

This is shown in **Settings → Mods**. Without it, the folder name is shown instead.

## tree.properties: adding nodes

The format is the same as the main `tree.properties`. List your new node ids in `nodes=`, then give each node its keys:

| Key | Meaning |
|---|---|
| `name` | Name shown on the node |
| `desc` | Description in the info card (use `\n` for a new line) |
| `cost` | Price of the first level, in Dopamine |
| `income` | Dopamine per second, per level |
| `click` | Extra Dopamine per Gather click, per level |
| `shape` | `square` (normal), `circle` (important) or `hexagon` (milestone) |
| `max` | How many times it can be bought. Each level adds the income and click bonus again and costs 1.3× the one before. Default: 10 for squares, 5 for circles, 1 for hexagons |
| `x`, `y` | Position of the node's centre. The main tree's rows are 180 apart. |
| `requires` | Comma-separated ids that must be bought first |

`requires` can point at any node in the main tree, or in a mod that loads before yours.

For prices, look at the main-tree nodes in the same row: costs grow about 7–8× per row (180 pixels down), and a node usually earns its cost back in a few minutes.

**Start your ids with your mod's name** (for example `thrill_sports`), so two mods never pick the same id by accident.

## tree.properties: changing existing nodes

Set only the keys you want to change. Everything else stays as it was:

```properties
exploration.desc=A new description written by my mod.
tyrosine.cost=5
currency=Serotonin
```

You don't need to list existing nodes in `nodes=`.

## Translations

The game ships in English (`en`), Croatian (`hr`), German (`de`), Spanish (`es`) and Portuguese (`pt`). To translate your mod's nodes, add a `tree_<language>.properties` file next to your mod's `tree.properties`. It only needs names and descriptions:

```properties
# mods/my-mod/tree_de.properties
thrill_sports.name=Extremsport
thrill_sports.desc=Absichtlich von Dingen springen.
```

Players who use another language see your English texts. A mod can also consist of only a translation file, for example to improve the wording of the main tree's nodes in one language. The main game's own translations are the `tree_<language>.properties` files next to its `tree.properties`.

## Sounds

Put `.wav` files in `sounds/`, named after the sound they replace:

| File | Plays when |
|---|---|
| `gather.wav` | clicking Gather |
| `select.wav` | selecting a node |
| `buy.wav` | buying a square node |
| `buy_important.wav` | buying a circle node |
| `milestone.wav` | buying a hexagon node |
| `denied.wav` | trying to buy something you can't afford |

Sounds must be **PCM WAV at 44100 Hz**, mono or stereo. Keep them short; they play every time the action happens.

## Load order and errors

- Mods load in **alphabetical folder order**. If two mods change the same key, the later one wins. You can prefix folder names with numbers (`10-base-mod`, `20-addon`) to control the order.
- A mod with a problem is **skipped as a whole**, and the game lists what went wrong when it starts. Common problems:
  - a number that isn't a number
  - `requires` naming a node that doesn't exist
  - two nodes requiring each other (a loop)
  - a sound that isn't a 44100 Hz WAV
- If you uninstall a mod, players keep their progress, apart from the nodes that mod added.

## Building a mod tree with the editor

The tree editor (`gradlew runEditor`) lays out nodes visually. Click **Open mod...** and pick your mod's folder (it can be a new, empty folder inside `mods/`), or start it with `gradlew runEditor --args="--mod mods/my-mod"`.

- The main tree is shown in grey. You can't change it here, but you can link to it: select one of your nodes and **Shift+click** a grey node to make it a requirement.
- **Add node** gives new nodes ids that start with your folder name.
- **Save** writes only your nodes into `mods/my-mod/tree.properties`. Anything else already in that file, such as changed texts of main-tree nodes or translations, is kept.
