#!/usr/bin/env python3
"""
fetch-upstream.py — assemble the original Millénaire art/data assets for a local build.

This project (the MC 26.2 / Fabric port) is open-sourced under MIT, but it contains ONLY our own
upgrade code + the 26.2-format resource definitions we authored. The original Millénaire's creative
content — building templates, cultures, quests, language text, textures and sounds — is © Kinniken
and is NOT redistributed here (it has no open-source licence). This script downloads the official
Millénaire 1.12.2 release and extracts ONLY those author-owned art/data assets into the
gitignored `upstream-assets/` source root, which the Gradle build overlays on top of our own code.

Run:  python scripts/fetch-upstream.py [--jar PATH] [--url URL]

By default it expects you to place the official `millenaire-8.1.2.jar` next to this repo (or pass
--jar / --url), because we do not host the author's file. Nothing fetched by this script is ever
committed — see .gitignore.
"""
import argparse
import os
import re
import shutil
import sys
import zipfile

# Where the author's assets land (a second resources root; gitignored; overlaid by build.gradle).
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
OUT = os.path.join(REPO, "upstream-assets")

# 1.12 → 26.2 vanilla-independent path migration for the author's TEXTURES (image data is the
# author's; the 26.2 folder layout is ours). Only the directory prefix + a few flattening renames.
TEX_DIR_RENAME = (("textures/blocks/", "textures/block/"), ("textures/items/", "textures/item/"))
# A few colour/name renames the 1.13 flattening forced on Mill's own texture names:
TEX_NAME_RENAME = {"silver": "light_gray"}

# Author-owned trees to pull straight out of the official jar (paths inside the jar).
COPY_PREFIXES = [
    "todeploy/millenaire/",          # building templates, cultures, goals, quests, languages, villagerconfig
    "assets/millenaire/sounds/",     # author's sound files
    # NOTE: the official jar's assets/millenaire/lang/*.lang is the 1.12 format, which 26.2 does NOT read.
    # The 26.2 .json language files (our reformatting) live in src/main/resources and are committed, so we
    # do NOT fetch the author's .lang here.
]
TEXTURE_PREFIX = "assets/millenaire/textures/"

# Public, CI-friendly direct download of the official Millénaire 8.1.2 release (CurseForge CDN; file id
# 3887515 -> files/3887/515). We do NOT host it; this just points the fetch at the author's official
# public distribution. (Author's source is also at https://github.com/Kinniken/Millenaire — source-available,
# no licence — but the release jar carries every art/data asset in the deployed layout, so we use it.)
DEFAULT_URL = "https://mediafilez.forgecdn.net/files/3887/515/millenaire-8.1.2.jar"


def migrate_texture_path(p: str) -> str:
    for old, new in TEX_DIR_RENAME:
        p = p.replace(old, new)
    base = os.path.basename(p)
    for old, new in TEX_NAME_RENAME.items():
        if old in base:
            p = p[: -len(base)] + base.replace(old, new)
            break
    return p


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", default=os.path.join(os.path.dirname(REPO), "millenaire-8.1.2.jar"),
                    help="path to the official millenaire-8.1.2.jar (we do not host it)")
    ap.add_argument("--url", default=None, help="optional URL to download the official jar from")
    args = ap.parse_args()

    jar = args.jar
    if not os.path.exists(jar):
        import urllib.request
        url = args.url or DEFAULT_URL
        print(f"official jar not found locally; downloading from {url} ...")
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "millenaire-262-fetch"})
            with urllib.request.urlopen(req) as r, open(jar, "wb") as f:
                shutil.copyfileobj(r, f)
        except Exception as e:
            print(f"ERROR: could not download the official Millénaire jar from {url} ({e}).\n"
                  f"Download millenaire-8.1.2.jar yourself from https://www.curseforge.com/minecraft/mc-mods/"
                  f"millenaire/files/3887515 and pass --jar PATH. This project does NOT host the author's file.",
                  file=sys.stderr)
            return 2

    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    copied = 0
    textures = 0
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if name.endswith("/"):
                continue
            dest = None
            if name.startswith(TEXTURE_PREFIX) and name.endswith(".png"):
                dest = migrate_texture_path(name)
                textures += 1
            elif any(name.startswith(p) for p in COPY_PREFIXES):
                dest = name
            if dest is None:
                continue
            target = os.path.join(OUT, dest.replace("/", os.sep))
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with z.open(name) as src, open(target, "wb") as out:
                shutil.copyfileobj(src, out)
            copied += 1

    print(f"OK: extracted {copied} author-owned files into {OUT}\n"
          f"    ({textures} textures path-migrated to the 26.2 layout, plus todeploy/sounds/lang).\n"
          f"    The Gradle build overlays this on top of src/main/resources. Nothing here is committed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
