"""Writes app/src/main/assets/licenses/third_party.txt from the release dependency tree.

Usage:
  ./gradlew :app:dependencies --configuration releaseRuntimeClasspath > deps.txt
  python scripts/third_party_notices.py deps.txt
"""
import glob
import io
import os
import re
import sys
import zipfile

HEADER = """Rocket Chess - third-party notices

Rocket Chess is free software under the GNU General Public License v3.0 (see GPL-3.0).
App source: https://github.com/uncosens/rocket-chess

Stockfish
  Copyright (C) The Stockfish developers (see AUTHORS in the Stockfish source).
  License: GNU General Public License v3.0 (see GPL-3.0).
  Used unmodified, built from the tag and commit shown on the licenses screen.
  Source: https://github.com/official-stockfish/Stockfish
  Build script: scripts/build-stockfish.sh in the app source.

Stockfish NNUE network (embedded in the engine)
  From https://github.com/official-stockfish/networks, released under CC0-1.0.
  Stockfish's networks are trained on data from the Leela Chess Zero project,
  which is available under the Open Database License (ODbL).

Piece images (assets/pieces), from the Lichess project: https://github.com/lichess-org/lila
  cburnett - Colin M.L. Burnett - GNU GPL v2 or later, used under GPL v3 (see GPL-3.0).
  merida - Armando Hernandez Marroquin - GNU GPL v2 or later, used under GPL v3 (see GPL-3.0).
  mpchess - Maxime Chupin - GNU GPL v3 or later (see GPL-3.0).
  chessnut - Alexis Luengas - Apache License 2.0 (see Apache-2.0).

Board images (assets/boards), from the Lichess project: https://github.com/lichess-org/lila
  wood4, maple, marble, blue-marble - the lila authors and pirouetti - GNU AGPL v3 or later (see AGPL-3.0).
  Combined with this GPL v3 app as allowed by section 13 of both licenses.

Opening data (assets/opening)
  book.bin - move statistics from the Lichess open database (https://database.lichess.org),
    rated rapid games of 2026-06 to 2026-09, released under CC0. Made by scripts/opening_book.py.
  names.tsv - opening names from https://github.com/lichess-org/chess-openings, CC0.

Fonts (res/font), under the SIL Open Font License 1.1 (see OFL-1.1)
  Unbounded - Copyright 2022 The Unbounded Project Authors (https://github.com/googlefonts/unbounded)
  IBM Plex Sans - Copyright 2017 IBM Corp. with Reserved Font Name "Plex"
  JetBrains Mono - Copyright 2020 The JetBrains Mono Project Authors (https://github.com/JetBrains/JetBrainsMono)

Java and Kotlin libraries
  All libraries below are under the Apache License 2.0 (see Apache-2.0).
"""

# Every shipped group must be listed here with its license. Unknown groups stop the script.
APACHE_GROUPS = ("androidx.", "org.jetbrains", "com.squareup.", "com.google.guava", "org.jspecify",
                 "org.apache.commons", "com.github.bhlangonijr", "com.caverock")


def main(deps_path, out_path="app/src/main/assets/licenses/third_party.txt"):
    tree = open(deps_path, encoding="utf-8", errors="replace").read()
    deps = sorted(set(
        f"{g}:{a}:{v}" for g, a, v in re.findall(r"--- ([\w.\-]+):([\w.\-]+):(?:[\w.\-]+ -> )?([\w.\-]+)", tree)
    ))
    unknown = [d for d in deps if not d.startswith(APACHE_GROUPS)]
    if unknown:
        sys.exit("license unknown for: " + ", ".join(unknown))

    cache = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
    notices = []
    for d in deps:
        g, a, v = d.split(":")
        for f in glob.glob(f"{cache}/{g}/{a}/{v}/*/*"):
            if not f.endswith((".jar", ".aar")) or "sources" in f:
                continue
            z = zipfile.ZipFile(f)
            if f.endswith(".aar") and "classes.jar" in z.namelist():
                z = zipfile.ZipFile(io.BytesIO(z.read("classes.jar")))
            for n in z.namelist():
                if n.split("/")[-1].upper().startswith("NOTICE"):
                    text = z.read(n).decode("utf-8", "replace").strip()
                    if text not in notices:
                        notices.append(text)

    with open(out_path, "w", encoding="utf-8", newline="\n") as out:
        out.write(HEADER + "\n")
        out.writelines(f"  {d}\n" for d in deps)
        out.write("\nNOTICE files from these libraries\n\n")
        out.writelines(t + "\n\n" for t in notices)
    print(f"{len(deps)} libraries, {len(notices)} NOTICE files -> {out_path}")


if __name__ == "__main__":
    main(*sys.argv[1:])
