#!/usr/bin/env python3
"""Static verification for the TEST-2 (original re-signed) APK (CI).

Usage: python3 tools/verify_test2.py <apk>
Checks: exactly 7 DEX (original, no extra), 26 native libs on both ABIs,
resources.arsc stored, original package com.launcher.brgame, NO
requiredSplitTypes ATTRIBUTE on the <manifest> element (real AXML parse - the
attribute name may remain as an unreferenced string-pool entry, which is
harmless), versionCode 1529, targetSdk 36.
"""
import sys, struct, zipfile, collections

def parse_axml(data):
    if data[:4] != b"\x03\x00\x08\x00":
        raise ValueError("not an AXML binary")
    off = 8
    typ, hdr_sz, sz = struct.unpack("<HHI", data[off:off + 8])
    if typ != 0x0001:
        raise ValueError("expected string pool first")
    str_count, _sc, flags, str_start, _ss = struct.unpack("<IIIII", data[off + 8:off + 28])
    is_utf8 = bool(flags & (1 << 8))
    offsets = [struct.unpack("<I", data[off + 28 + i * 4:off + 32 + i * 4])[0]
               for i in range(str_count)]
    base = off + str_start
    strings = []
    for o in offsets:
        p = base + o
        if is_utf8:
            l1 = data[p]; p += 1
            if l1 & 0x80:
                l1 = ((l1 & 0x7f) << 8) | data[p]; p += 1
            l2 = data[p]; p += 1
            if l2 & 0x80:
                l2 = ((l2 & 0x7f) << 8) | data[p]; p += 1
            strings.append(data[p:p + l2].decode("utf-8", "replace"))
        else:
            l = struct.unpack("<H", data[p:p + 2])[0]; p += 2
            if l & 0x8000:
                l = ((l & 0x7fff) << 16) | struct.unpack("<H", data[p:p + 2])[0]; p += 2
            strings.append(data[p:p + l * 2].decode("utf-16-le", "replace"))
    off += sz
    root = {}
    while off < len(data):
        typ, _hs, csz = struct.unpack("<HHI", data[off:off + 8])
        if typ == 0x0102:
            _line, _c, _ns, name_idx = struct.unpack("<IIII", data[off + 8:off + 24])
            attr_start, attr_size, attr_count, _i, _cl, _st = struct.unpack(
                "<HHHHHH", data[off + 24:off + 36])
            name = strings[name_idx] if name_idx < len(strings) else "?"
            for i in range(attr_count):
                ao = off + 16 + attr_start + i * attr_size
                a_name = struct.unpack("<I", data[ao + 4:ao + 8])[0]
                a_raw = struct.unpack("<I", data[ao + 8:ao + 12])[0]
                tv_type = data[ao + 15]
                tv_data = struct.unpack("<I", data[ao + 16:ao + 20])[0]
                nm = strings[a_name] if a_name < len(strings) else "?"
                raw = strings[a_raw] if (a_raw != 0xFFFFFFFF and a_raw < len(strings)) else None
                if name == "manifest" and nm in ("package", "versionCode"):
                    root[nm] = (raw, tv_type, tv_data)
                if name == "manifest":
                    # record every attribute NAME present on <manifest>
                    root.setdefault("_attrs", []).append(nm)
                if name == "uses-sdk" and nm == "targetSdkVersion":
                    root["targetSdk"] = (raw, tv_type, tv_data)
        off += csz
    return root

def main(path):
    failures = []
    z = zipfile.ZipFile(path)
    names = z.namelist()

    dups = [n for n, c in collections.Counter(names).items() if c > 1]
    print(f"[{'PASS' if not dups else 'FAIL'}] no duplicate ZIP entries ({len(names)} entries)")
    if dups:
        failures.append("duplicate entries")

    dex = sorted(n for n in names if n.endswith(".dex"))
    c_dex = len(dex) == 7 and "classes8.dex" not in names
    print(f"[{'PASS' if c_dex else 'FAIL'}] exactly 7 original DEX files, no extras ({len(dex)})")
    if not c_dex:
        failures.append("dex count")

    libs = [n for n in names if n.startswith("lib/") and n.endswith(".so")]
    abis = sorted(set(n.split("/")[1] for n in libs))
    c_libs = len(libs) == 26 and abis == ["arm64-v8a", "armeabi-v7a"]
    print(f"[{'PASS' if c_libs else 'FAIL'}] 26 native libs across {abis}")
    if not c_libs:
        failures.append("libs")

    arsc = z.getinfo("resources.arsc")
    c_arsc = arsc.compress_type == zipfile.ZIP_STORED
    print(f"[{'PASS' if c_arsc else 'FAIL'}] resources.arsc stored uncompressed")
    if not c_arsc:
        failures.append("arsc")

    root = parse_axml(z.read("AndroidManifest.xml"))
    pkg = root["package"][0]
    vc = root["versionCode"][2]
    ts = root.get("targetSdk", (None, None, None))[2]
    attrs_on_manifest = root.get("_attrs", [])
    c_pkg = pkg == "com.launcher.brgame"
    print(f"[{'PASS' if c_pkg else 'FAIL'}] package stays original: {pkg!r}")
    if not c_pkg:
        failures.append("package")
    c_vc = vc == 1529
    print(f"[{'PASS' if c_vc else 'FAIL'}] versionCode 1529 (got {vc})")
    if not c_vc:
        failures.append("versionCode")
    c_ts = ts == 36
    print(f"[{'PASS' if c_ts else 'FAIL'}] targetSdk 36 (got {ts})")
    if not c_ts:
        failures.append("targetSdk")
    c_split = "requiredSplitTypes" not in attrs_on_manifest and "splitTypes" not in attrs_on_manifest
    print(f"[{'PASS' if c_split else 'FAIL'}] no requiredSplitTypes/splitTypes attribute on <manifest>")
    if not c_split:
        failures.append("split attrs present")

    print()
    if failures:
        print("VERIFY FAILED:", failures)
        return 1
    print("ALL STATIC CHECKS PASSED (TEST-2 ORIGINAL RE-SIGNED APK)")
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
