# Changelog

What changed in Genesis, newest first. Only changes you can actually notice are listed:
internal and build work is deliberately left out. This file is the source for the release
notes published on the Modrinth, CurseForge and Nexus Mods pages.

Entries begin after v1.1.0-1.21.1, the release current when this file was created on
2026-08-14. This repository's earlier history was intentionally cleared, so notes for
releases up to and including that one exist only on the published page.

From v2.0.0 one release serves every Minecraft version the mod supports, so this file is the only changelog.

## v2.1.0 - 2026-10-20

- Added: Genesis tells you in chat when a newer version is out for your Minecraft version and loader, once per game start, and says nothing otherwise; the check_for_updates setting turns it off.

## v2.0.0 - 2026-10-18

- Added: A WIDE zoom level, an EXPORT MAP button that saves the map in view, with x and z coordinates, to your screenshots folder, right-clicking the map to choose the new world's spawn point, a world map of the world you are playing, opened with G, structure markers on both maps, and an amber note whenever part of the map is approximate.
- Added: Support for every Minecraft version from 1.20.1 to 1.21.1, with one download in place of a separate file for each Minecraft version: Fabric on all of them, Forge on 1.20.1 to 1.21 except 1.20.5, which Forge never released, and NeoForge on 1.20.4 to 1.21.1.
- Changed: The Forge build accepts Forge 47.1 and later, so it also runs on the NeoForge 1.20.1 fork.
- Removed: Genesis no longer requires Architectury API.
- Fixed: The preview shows the biomes that mods such as Biomes O' Plenty, Terralith, Nature's Spirit, Traverse and William Wythers' Expanded Ecosphere add, through TerraBlender, Lithostitched, Biolith or a replaced overworld, instead of placeholder or vanilla biomes.
- Fixed: Quitting or crashing during pre-generation no longer leaves you stuck in spectator mode: your game mode is restored when the world closes or the next time you join.
- Fixed: Pre-generation reports its progress to players on a dedicated server, counts only chunks that actually generated, and no longer stalls when a chunk fails or the server stops.
- Fixed: With GeoGradient installed, the world preview uses its latitude-aware biomes and your GeoGradient settings, instead of silently falling back to the vanilla sampler.
- Changed: The preview and pre-generation screens use neutral grays, even spacing, smooth hover transitions and a gold outline around the control the keyboard has selected, and every button and text field explains itself in a one-sentence tooltip on hover.
- Fixed: A preview that fails to start says so and points to the game log, instead of claiming your world type has no preview, and the legend and hover readout show a modded biome's name, such as Moonlight Valley, instead of its raw translation key.
- Fixed: The preview and world map color each chunk by the biome at its center instead of its corner, so a chunk shows the biome that covers most of it far more often.

## v1.1.1-1.21.1 - 2026-09-04

- Changed: The preview world screen title uses a plain hyphen instead of a long dash.
- Changed: Map tiles you have not sampled yet use the standard dark background color.
