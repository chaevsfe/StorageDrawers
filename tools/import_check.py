#!/usr/bin/env python3
"""Check every Minecraft/Mojang import in the mod against a real Minecraft jar.

Usage: import_check.py <jar> <src-dir> [<src-dir> ...]
"""
import collections
import os
import re
import sys
import zipfile

jar_path, src_dirs = sys.argv[1], sys.argv[2:]
classes = {n[:-6].replace("/", ".") for n in zipfile.ZipFile(jar_path).namelist()
           if n.endswith(".class")}

by_simple = collections.defaultdict(list)
for c in classes:
    by_simple[c.rsplit(".", 1)[-1].split("$")[-1]].append(c)

# DataFixerUpper and authlib ship separately, so they are not checkable against this jar.
WATCHED = ("net.minecraft.", "com.mojang.blaze3d.", "com.mojang.math.")
IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w.$]+)\s*;", re.M)

missing = collections.defaultdict(list)   # import -> [file:line]
ok_count = 0
star_imports = []

for src in src_dirs:
    for root, _, files in os.walk(src):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(root, fn)
            rel = os.path.relpath(path, ".")
            for i, line in enumerate(open(path, encoding="utf-8"), 1):
                m = IMPORT_RE.match(line)
                if not m:
                    continue
                fqn = m.group(1)
                if not fqn.startswith(WATCHED):
                    continue
                if fqn.endswith(".*"):
                    star_imports.append(f"{rel}:{i}  {fqn}")
                    continue
                # A static import's last segment is a member, not a type.
                candidates = [fqn, fqn.rsplit(".", 1)[0]]
                # Nested types are written with '.' in source but '$' in the jar.
                parts = fqn.split(".")
                for k in range(len(parts) - 1, 0, -1):
                    candidates.append(".".join(parts[:k]) + "$" + "$".join(parts[k:]))
                if any(c in classes for c in candidates):
                    ok_count += 1
                else:
                    missing[fqn].append(f"{rel}:{i}")

print(f"=== {ok_count} imports resolve, {len(missing)} distinct imports DEAD ===")
print(f"({sum(len(v) for v in missing.values())} total dead import sites)\n")

for fqn in sorted(missing, key=lambda f: (-len(missing[f]), f)):
    sites = missing[fqn]
    simple = fqn.rsplit(".", 1)[-1]
    hint = ""
    same_name = [c for c in by_simple.get(simple, []) if c != fqn]
    if same_name:
        hint = "  -> MOVED? " + ", ".join(sorted(same_name)[:3])
    print(f"{len(sites):>3}x  {fqn}{hint}")
    for s in sites[:3]:
        print(f"       {s}")
    if len(sites) > 3:
        print(f"       ... and {len(sites) - 3} more")

if star_imports:
    print(f"\n=== {len(star_imports)} wildcard imports (not checkable) ===")
    for s in star_imports[:15]:
        print("  " + s)
