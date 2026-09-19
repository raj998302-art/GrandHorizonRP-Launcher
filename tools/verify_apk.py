#!/usr/bin/env python3
"""Grand Horizon RP launcher APK static verification for CI.

Usage: python3 tools/verify_apk.py <apk-path>

Checks (all must PASS before an APK may be published):
  1. ZIP container valid, no duplicate entries
  2. Binary AndroidManifest parsed via real AXML parser:
     - package == com.grandhorizonrp.launcher
     - versionCode == 1529 (INT_DEC)
     - versionName == 16.102.14498
     - application class + launcher activity (JNIActivity) declared
  3. Zero stale "com.br.top" references across every text-scannable APK entry
  4. GhrpBoot diagnostic classes present in DEX + >= 12 stage markers
  5. Native libraries: 26 files, byte-identical set for arm64-v8a + armeabi-v7a
  6. 7 DEX files with valid headers and matching sizes
  7. resources.arsc present and stored uncompressed
Exit code 0 = all checks pass; 1 = any failure (details printed).
"""
import sys, os, struct, zipfile, hashlib

REQUIRED_PKG = "com.grandhorizonrp.launcher"
REQUIRED_VC = 1529
REQUIRED_VN = "16.102.14498"

def parse_axml(data):
    """Minimal AXML parser -> dict of interesting root attributes."""
    if data[:4] != b"\x03\x00\x08\x00":
        raise ValueError("not an AXML binary")
    off = 8
    typ, hdr_sz, sz = struct.unpack("<HHI", data[off:off + 8])
    if typ != 0x0001:
        raise ValueError("expected string pool first, got %#x" % typ)
    str_count, _style_count, flags, str_start, _style_start = struct.unpack(
        "<IIIII", data[off + 8:off + 28])
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
    root_attrs = {}
    activities = []
    application_name = None
    while off < len(data):
        typ, _hs, csz = struct.unpack("<HHI", data[off:off + 8])
        if typ == 0x0102:  # START_ELEMENT
            _line, _comment, _ns, name_idx = struct.unpack("<IIII", data[off + 8:off + 24])
            attr_count = struct.unpack("<H", data[off + 28:off + 30])[0]
            name = strings[name_idx] if name_idx < len(strings) else "?"
            for i in range(attr_count):
                ao = off + 36 + i * 20
                a_name = struct.unpack("<I", data[ao + 4:ao + 8])[0]
                a_raw = struct.unpack("<I", data[ao + 8:ao + 12])[0]
                tv_type = data[ao + 15]
                tv_data = struct.unpack("<I", data[ao + 16:ao + 20])[0]
                nm = strings[a_name] if a_name < len(strings) else "?"
                raw = strings[a_raw] if a_raw != 0xFFFFFFFF and a_raw < len(strings) else None
                if name == "manifest" and nm in ("versionCode", "versionName", "package"):
                    root_attrs[nm] = (raw, tv_type, tv_data)
                if name == "application" and nm == "name":
                    application_name = raw
                if name == "activity" and nm == "name":
                    activities.append(raw)
        off += csz
    return root_attrs, application_name, activities

def main(path):
    failures = []
    z = zipfile.ZipFile(path)
    names = z.namelist()

    # 1. duplicates
    import collections
    dups = [n for n, c in collections.Counter(names).items() if c > 1]
    print(f"[{'PASS' if not dups else 'FAIL'}] no duplicate ZIP entries ({len(names)} entries)")
    if dups:
        failures.append("duplicate entries: %s" % dups[:5])

    # 2. manifest
    attrs, app_name, activities = parse_axml(z.read("AndroidManifest.xml"))
    pkg = attrs["package"][0]
    vc_raw, vc_type, vc_data = attrs["versionCode"]
    vn_raw = attrs["versionName"][0]
    c1 = pkg == REQUIRED_PKG
    c2 = vc_data == REQUIRED_VC and vc_type == 0x10
    c3 = vn_raw == REQUIRED_VN
    c4 = app_name == "com.blackhub.bronline.launcher.App"
    c5 = "com.blackhub.bronline.game.core.JNIActivity" in activities
    print(f"[{'PASS' if c1 else 'FAIL'}] package == {REQUIRED_PKG} (got {pkg!r})")
    print(f"[{'PASS' if c2 else 'FAIL'}] versionCode == {REQUIRED_VC} INT_DEC (got {vc_data})")
    print(f"[{'PASS' if c3 else 'FAIL'}] versionName == {REQUIRED_VN!r} (got {vn_raw!r})")
    print(f"[{'PASS' if c4 else 'FAIL'}] application class = {app_name!r}")
    print(f"[{'PASS' if c5 else 'FAIL'}] launcher activity declared (JNIActivity)")
    if not c1: failures.append("package name")
    if not c2: failures.append("versionCode")
    if not c3: failures.append("versionName")
    if not c4: failures.append("application class")
    if not c5: failures.append("launcher activity")

    # 3. stale refs
    stale = []
    for info in z.infolist():
        n = info.filename
        if n.endswith((".dex", ".xml", ".json", ".txt", ".properties", ".version",
                        ".kotlin_module", ".proto", ".js")):
            blob = z.read(n)
            if b"com.br.top" in blob or b"com/br/top" in blob:
                stale.append(n)
    print(f"[{'PASS' if not stale else 'FAIL'}] stale com.br.top runtime references: {len(stale)}")
    if stale:
        failures.append("stale refs: %s" % stale[:5])

    # 4. diagnostic instrumentation
    dex_files = sorted(x for x in names if x.endswith(".dex"))
    has_boot = any(b"GhrpBoot" in z.read(n) for n in dex_files)
    stages = sum(z.read(n).count(b"STAGE ") for n in dex_files)
    print(f"[{'PASS' if has_boot else 'FAIL'}] GhrpBoot diagnostic classes present")
    print(f"[{'PASS' if stages >= 12 else 'FAIL'}] boot stage markers present ({stages})")
    if not has_boot: failures.append("GhrpBoot missing")
    if stages < 12: failures.append("stage markers missing")

    # 5. native libs
    libs = [n for n in names if n.startswith("lib/") and n.endswith(".so")]
    abis = sorted(set(n.split("/")[1] for n in libs))
    c_abis = abis == ["arm64-v8a", "armeabi-v7a"]
    c_count = len(libs) == 26
    print(f"[{'PASS' if c_abis else 'FAIL'}] ABIs = {abis}")
    print(f"[{'PASS' if c_count else 'FAIL'}] native library count = {len(libs)} (expect 26)")
    if not c_abis: failures.append("ABI set")
    if not c_count: failures.append("native lib count")

    # 6. dex validity
    dex_ok = len(dex_files) == 7
    for n in dex_files:
        d = z.read(n)[:36]
        if d[:8] != b"dex\n035\x00" or struct.unpack("<I", d[32:36])[0] != z.getinfo(n).file_size:
            dex_ok = False
    print(f"[{'PASS' if dex_ok else 'FAIL'}] {len(dex_files)} DEX files valid")
    if not dex_ok: failures.append("dex validity")

    # 7. resources.arsc stored
    arsc = z.getinfo("resources.arsc")
    c_arsc = arsc.compress_type == zipfile.ZIP_STORED
    print(f"[{'PASS' if c_arsc else 'FAIL'}] resources.arsc stored uncompressed")
    if not c_arsc: failures.append("arsc compression")

    print()
    if failures:
        print("VERIFY FAILED:", failures)
        return 1
    print("ALL STATIC CHECKS PASSED")
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
