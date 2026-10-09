# Ore Tracer

A client-only Fabric mod for Minecraft 26.2 that highlights ores visible to the client.

## Controls

- **F8** toggles every Ore Tracer feature on or off.
- **O** opens the ore selection menu.

Both keys can be changed in Minecraft's Controls screen.

## Features

- See-through translucent ore glow and bright block outlines.
- Tracer line to each detected vein.
- Billboard label above each vein with the ore name and block count.
- Per-ore toggles, saved in `config/oretracer.json`.
- Incremental scanning to avoid freezing the game.
- Optional The Sift support: Siftite and Charoite have their own toggles. Siftslate coal, diamond and emerald use the existing ore toggles. The Sift is not required to launch Ore Tracer.

The mod can only highlight blocks the server has sent to the client. Server-side anti-xray can hide ores until they are exposed.

## Build

Run `gradlew.bat build` on Windows. The installable jar is created in `build/libs`.
