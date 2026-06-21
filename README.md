# Millénaire for Minecraft 26.2 (Fabric) — unofficial port

An unofficial port of the [Millénaire](https://millenaire.org) mod (by Kinniken) from Minecraft
1.12.2 (Forge) to Minecraft 26.2 (Fabric).

> **Important — read this first.** This repository contains ONLY our own port code and the
> 26.2-format resource definitions we wrote. It does **not** contain Millénaire's original code or
> creative assets (building templates, cultures, quests, language text, textures, sounds), which are
> © Kinniken and have no redistribution licence. To build a working mod you run a script that fetches
> those author-owned assets from the official Millénaire release on your own machine. See **NOTICE**
> and **LICENSE**.

## What's MIT here vs fetched

| In this repo (MIT)                                   | Fetched by `scripts/fetch-upstream.py` (author's, gitignored) |
| ---------------------------------------------------- | ------------------------------------------------------------- |
| `src/main/java` — the 26.2 / Fabric port code        | `assets/millenaire/textures/**` (image data, path-migrated)   |
| `src/main/resources` blockstates / models / items /  | `assets/millenaire/sounds/**`                                 |
| loot_tables / recipes / advancements                 | `assets/millenaire/lang/**`                                   |
| the few textures we created ourselves                | `todeploy/millenaire/**` (buildings, cultures, quests, langs) |
| build tooling, fetch script, docs                    |                                                               |

Fetched assets land in the gitignored `upstream-assets/` source root, which the Gradle build
overlays on top of `src/main/resources`.

## Building

1. Obtain the official `millenaire-8.1.2.jar` from <https://millenaire.org> (we do not host it) and
   place it next to this repo, **or** note its URL.
2. Fetch the author's assets:
   ```
   python scripts/fetch-upstream.py            # expects ../millenaire-8.1.2.jar
   # or: python scripts/fetch-upstream.py --jar /path/to/millenaire-8.1.2.jar
   # or: python scripts/fetch-upstream.py --url https://.../millenaire-8.1.2.jar
   ```
3. Build:
   ```
   ./gradlew build
   ```
   The jar appears in `build/libs/`.

## Licence

Our work: **MIT** (see `LICENSE`). The underlying Millénaire mod is © its authors and is **not**
redistributed by this project — see `NOTICE`. This port is unofficial and unaffiliated.
