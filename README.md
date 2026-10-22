# Genesis

See the world before you make it. Genesis puts a live biome map, seed tools and world pre-generation on the Create New World screen, so you can compare seeds and pre-build the terrain before you ever spawn in.

> **One file per loader covers Minecraft 1.20.1 to 1.21.1 on Fabric, 1.20.1 to 1.21 on Forge and 1.20.4 to 1.21.1 on NeoForge.**

---

## Availability

- [Nexus Mods](https://www.nexusmods.com/minecraft/mods/1287)
- [Modrinth](https://modrinth.com/project/xXuFelru)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/genesis-world-previewer-pregenerator)

## What It Does

- **A biome map of the world you are about to create** - opened from a GENESIS button on the Create New World screen, sampled from the real generator with your seed and every mod you have installed.
- **Readable in seconds** - a coarse pass fills the layout immediately, then two finer passes complete the detail, on 2 to 4 background threads by default and up to 16 in the settings.
- **Fixed at Y = 320** - so cave biomes never bleed into the surface map.
- **Colored so you can actually read it** - 16 biome families share a fill color, and every individual biome gets its own border color in the legend.
- **Modded biomes get real colors too** - matched by registry name, then grass color, then a hash of the name, so nothing shows up as anonymous gray.
- **Pan, zoom and measure** - drag to pan, scroll to zoom, NEAR, MID, FAR and WIDE for fixed levels, with a scale bar, a live legend and a coordinate and biome readout under the cursor.
- **Export the map** - EXPORT MAP saves the map in view to your screenshots folder as a PNG labeled with x and z coordinates every 512 blocks, the seed, the world origin and a legend naming every color in it.
- **Find structures before you go** - villages, strongholds, temples and modded structures are marked where the seed places them, named on hover; STRUCTURES hides them. Terrain can stop one, so a mark is a likely spot.
- **Choose your spawn** - right-click the map to place the new world's spawn point there, used when you start the world with CREATE WORLD or PRE-GENERATE; RESET goes back to the world's own spawn.
- **A map of the world you are playing** - press G in game (rebind it under Controls) for a biome map of your current dimension, centered on you with the world spawn marked, in singleplayer or on a server running Genesis.
- **Seed tools where you need them** - type a number or any text, RANDOM for a fresh one, COPY to the clipboard, R to reroll. Leaving the screen writes the seed back to Create New World for you.
- **Pre-generate before you play** - set a radius and thread count and watch a progress bar with a chunk counter, a rolling ETA and the active dimension.
- **On your terms** - PAUSE and RESUME without losing progress, CANCEL to stop, or ENTER WORLD to keep it running in the background.
- **A HUD while it runs** - dimension, chunks, percentage and ETA at the top of the screen, gold while paused, gone when the job finishes.
- **Or drive it from commands** - `/genesis pregen` by dimension or `all`, optionally centered on explicit coordinates, with `cancel`, `pause`, `resume` and `status`, plus `/genesis info` for the seed and generator.
- **Honest about what it cannot read** - a spot whose biome cannot be read is drawn gray and named so in the legend, and an amber note above the legend says when part of the map comes from a simpler stand-in, and why.

---

## Requirements

- Minecraft 1.20.1 to 1.21.1 on Fabric.
- Minecraft 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.6 or 1.21 on Forge, including the NeoForge 1.20.1 fork.
- Minecraft 1.20.4, 1.20.5, 1.20.6, 1.21 or 1.21.1 on NeoForge.
- Fabric API and Cloth Config on Fabric, with Fabric Loader 0.14.23 or later on 1.20.1, 0.19.5 or later on 1.20.2 and 1.20.3, 0.15.11 or later on 1.20.4 to 1.20.6, and 0.19.2 or later on 1.21 and 1.21.1.
- Forge 47.1 or later on 1.20.1, 48 or later on 1.20.2, 49 or later on 1.20.3 and 1.20.4, 50 or later on 1.20.6, and 51 or later on 1.21.
- NeoForge 20.4 or later on 1.20.4, 20.5 or later on 1.20.5, 20.6 or later on 1.20.6, and 21.0 or later on 1.21 and 1.21.1.
- Java 17 or later on Minecraft 1.20.1 to 1.20.4, and Java 21 or later from 1.20.5.

## Installation

1. Install your loader, plus Fabric API and Cloth Config if you are on Fabric.
2. Open Create New World. A GENESIS button sits on the screen.

## Configuration

On Forge or NeoForge, edit `config/genesis-client.toml`, and `config/genesis-common.toml` for the two `server_` settings and `check_for_updates`. On Fabric, edit `config/genesis.json`.

| Setting | Default | What it does |
|---|---|---|
| `preview_enabled` | `true` | Turns the biome preview on or off entirely. |
| `preview_threads` | `0` | Biome sampling threads, 0 to 16. 0 lets the mod choose. |
| `preview_initial_radius` | `64` | Chunks covered by the initial three-pass spiral, 16 to 512. |
| `preview_expand_radius` | `16` | Chunks added on each expansion after that spiral, 4 to 128. |
| `spawn_from_map` | `true` | Lets a right-click on the preview map choose the new world's spawn point. |
| `show_structures` | `true` | Marks on both maps where structures can start. |
| `server_world_map` | `true` | On a server, answers players' world maps with the world's biomes. Off keeps the world's layout from players. |
| `server_world_map_structures` | `true` | On a server, includes structure locations in what players' world maps receive. |
| `check_for_updates` | `true` | Once per game start, reads https://alkeari.com/updates/genesis.json and, only when a newer Genesis is out for your Minecraft version and loader, says so in chat with a link. Nothing else is sent; turn it off to skip the check. |
| `pregen_default_radius` | `128` | Pre-generation radius prefilled in the overlay, 1 to 2048. |
| `pregen_default_threads` | `4` | Pre-generation threads prefilled in the overlay and used as the command default, 1 to 16. |

On Forge and NeoForge a change takes effect the next time the preview restarts, on a seed change or when the screen reopens, and the ranges are enforced. On Fabric a change takes effect when you restart the game, and a value outside its range is moved to the nearest end of it.

## Compatibility

- **Existing worlds.** The preview only runs on the Create New World screen, so it changes nothing about a world you already have. Pre-generation can be pointed at one from a command.
- **Client or server.** The biome preview is client-side and safe to install on the client alone. Pre-generation, choosing the spawn point and the world map need Genesis on the server too; singleplayer already has it.
- **It does not touch generation.** The preview reads the generator; it changes no chunk, structure or loot table.
- GeoGradient is detected automatically: the map shows its latitude-based climate bands from your GeoGradient settings. At its default globe size a band spans thousands of blocks, so zoom out to FAR or WIDE to see them.
- Superflat previews exactly. Other custom generators are drawn with vanilla climate and marked approximate on the map.
- Commands are operator level 2 on a server and unrestricted in singleplayer; `status` and `info` are open to everyone.

## Support

- Include your Minecraft version, your loader and its version, the mod version, and your other mods.

## License

License terms are in the [Alkeari License Agreement](https://gist.github.com/Alkeari/2c6ec0cdf3dafee375b1a00b28ca190a).
