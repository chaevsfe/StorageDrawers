#!/usr/bin/env python3
"""Validate every accesswidener entry against the real Minecraft jar.

An AW entry naming a class/member that no longer exists is a HARD mod-load failure,
so this has to pass before the client will even reach the main menu.

Usage: aw_check.py <file.accesswidener> [<file.accesswidener> ...]
Run with no arguments it used to check nothing and still exit 0, which reads as a pass.
"""
import re
import subprocess
import sys
import zipfile

import os
JAR = os.path.expanduser(f"~/.gradle/caches/fabric-loom/{os.environ.get('MCVER', '26.1')}/minecraft-client.jar")
AW_FILES = sys.argv[1:]

if not AW_FILES:
    sys.exit("usage: aw_check.py <file.accesswidener> ...  (no files given: nothing was checked)")

zf = zipfile.ZipFile(JAR)
classes = {n[:-6] for n in zf.namelist() if n.endswith(".class")}

_javap_cache = {}


def javap(cls):
    if cls not in _javap_cache:
        r = subprocess.run(["javap", "-p", "-cp", JAR, cls.replace("/", ".")],
                           capture_output=True, text=True)
        _javap_cache[cls] = r.stdout if r.returncode == 0 else ""
    return _javap_cache[cls]


def desc_to_java(d):
    """Turn a JVM type descriptor into javap's rendering, well enough to match on."""
    prims = {"V": "void", "Z": "boolean", "B": "byte", "C": "char",
             "S": "short", "I": "int", "J": "long", "F": "float", "D": "double"}
    arr = 0
    while d.startswith("["):
        arr += 1
        d = d[1:]
    if d.startswith("L"):
        base = d[1:-1].replace("/", ".").replace("$", ".")
    else:
        base = prims.get(d, d)
    return base + "[]" * arr


def simple_name(t):
    """Last segment of a type name, ignoring package and outer-class nesting.

    Generics are stripped FIRST: java.util.Set<net.minecraft...Block> must reduce to
    "Set", not to "Block>".
    """
    return t.split("<")[0].strip().split(".")[-1].split("$")[-1]


def split_args(args):
    """Split a javap argument list on top-level commas only (generics contain commas)."""
    out, depth, cur = [], 0, ""
    for ch in args:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur)
    return [a.strip() for a in out]


def split_params(sig):
    """Split the parameter portion of a method descriptor into type descriptors."""
    params = sig[1:sig.rindex(")")]
    out, i = [], 0
    while i < len(params):
        j = i
        while params[j] == "[":
            j += 1
        if params[j] == "L":
            j = params.index(";", j)
        out.append(params[i:j + 1])
        i = j + 1
    return out


results = []
for awf in AW_FILES:
    for lineno, raw in enumerate(open(awf), 1):
        line = raw.split("#")[0].strip()
        if not line or line.startswith("accessWidener"):
            continue
        parts = line.split()
        access, kind = parts[0], parts[1]
        cls = parts[2]
        label = f"{awf.split('/')[-1]}:{lineno}"

        if cls not in classes:
            results.append((label, "MISSING CLASS", f"{cls}", line))
            continue

        if kind == "class":
            results.append((label, "ok", cls, line))
            continue

        name, desc = parts[3], parts[4]
        body = javap(cls)
        if not body:
            results.append((label, "JAVAP FAILED", cls, line))
            continue

        if kind == "field":
            ftype = desc_to_java(desc)
            # Exact declared-type match. A substring test here silently passes
            # ItemStack against a field that is really ItemStackTemplate.
            decl = None
            for ln in body.splitlines():
                m = re.match(rf"\s*(?:[\w.$<>?\[\], ]+?\s+)?([\w.$]+(?:<[^;]*>)?(?:\[\])*)\s+{re.escape(name)}\s*;\s*$", ln)
                if m:
                    decl = m.group(1).split("<")[0]
                    break
            found = decl is not None and simple_name(decl) == simple_name(ftype)
            status = "ok" if found else ("MISSING FIELD" if decl is None else "TYPE MISMATCH")
            detail = f"{cls}.{name} : {ftype}"
            if status == "TYPE MISMATCH":
                detail += f"  (actual: {decl})"
            results.append((label, status, detail, line))
        else:  # method
            ptypes = [desc_to_java(p) for p in split_params(desc)]
            mname = cls.split("/")[-1].split("$")[-1] if name == "<init>" else name
            hits = []
            for ln in body.splitlines():
                if re.search(rf"[\s.]{re.escape(mname)}\s*\(", ln) or ln.strip().startswith(mname + "("):
                    hits.append(ln.strip())
            match = None
            for h in hits:
                args = h[h.index("(") + 1:h.rindex(")")]
                actual = split_args(args) if args.strip() else []
                if len(actual) != len(ptypes):
                    continue
                if all(simple_name(p) == simple_name(a) for p, a in zip(ptypes, actual)):
                    match = h
                    break
            if match:
                results.append((label, "ok", f"{cls}.{mname}", line))
            else:
                detail = f"{cls}.{mname}({', '.join(t.split('.')[-1] for t in ptypes)})"
                alt = "; candidates: " + " | ".join(hits[:3]) if hits else "; no method of that name"
                results.append((label, "SIGNATURE MISMATCH", detail + alt, line))

bad = [r for r in results if r[1] != "ok"]
print(f"=== {len(results)} entries checked against {JAR.split('/')[-1]} ===\n")
for label, status, detail, _ in results:
    mark = "  ok  " if status == "ok" else " FAIL "
    print(f"[{mark}] {label:<48} {status:<20} {detail[:150]}")
print(f"\n{len(results) - len(bad)} ok, {len(bad)} failing")
