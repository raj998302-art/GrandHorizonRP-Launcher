#!/usr/bin/env python3
"""Static verification for the GHRP DIAGNOSTIC APK (CI).

Usage: python3 tools/verify_diag.py <apk>
Checks: package, versionCode/Name, targetSdk=36, application class,
GhrpDiagActivity declared (new launcher entry), GhrpDiagProvider declared,
8 valid DEX files, diagnostic classes + markers present in classes8.dex,
26 native libs across both ABIs, resources.arsc stored, no duplicate ZIP
entries, zero stale com.br.top references.
"""
import sys, os, struct, zipfile, collections

REQUIRED_PKG = "com.grandhorizonrp.launcher"
REQUIRED_VC = 1529
REQUIRED_VN = "16.102.14498"

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
    activities, providers, services = [], [], []
    app_name = None
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
                if name == "manifest" and nm in ("versionCode", "versionName", "package"):
                    root[nm] = (raw, tv_type, tv_data)
                if name == "uses-sdk" and nm == "targetSdkVersion":
                    root["targetSdk"] = (raw, tv_type, tv_data)
                if name == "application" and nm == "name":
                    app_name = raw
                if name == "activity" and nm == "name":
                    activities.append(raw)
                if name == "provider" and nm == "name":
                    providers.append(raw)
                if name == "service" and nm == "name":
                    services.append(raw)
        off += csz
    return root, app_name, activities, providers, services

def main(path):
    failures = []
    z = zipfile.ZipFile(path)
    names = z.namelist()

    dups = [n for n, c in collections.Counter(names).items() if c > 1]
    print(f"[{'PASS' if not dups else 'FAIL'}] no duplicate ZIP entries ({len(names)} entries)")
    if dups:
        failures.append("duplicate entries")

    attrs, app_name, activities, providers, services = parse_axml(z.read("AndroidManifest.xml"))
    pkg = attrs["package"][0]
    vc = attrs["versionCode"]
    vn = attrs["versionName"][0]
    ts = attrs.get("targetSdk", (None, None, None))
    checks = [
        ("package", pkg == REQUIRED_PKG, f"{pkg!r}"),
        ("versionCode 1529 INT_DEC", vc[2] == REQUIRED_VC and vc[1] == 0x10, f"{vc[2]}"),
        ("versionName", vn == REQUIRED_VN, f"{vn!r}"),
        ("targetSdk == 36 (original value)", ts[2] == 36, f"{ts[2]}"),
        ("application class", app_name == "com.blackhub.bronline.launcher.App", f"{app_name!r}"),
        ("GhrpDiagActivity declared (launcher entry)",
         "com.blackhub.bronline.launcher.diag.GhrpDiagActivity" in activities, ""),
        ("GhrpDiagProvider declared",
         "com.blackhub.bronline.launcher.diag.GhrpDiagProvider" in providers, ""),
        ("JNIActivity still declared",
         "com.blackhub.bronline.game.core.JNIActivity" in activities, ""),
    ]
    for label, ok, got in checks:
        print(f"[{'PASS' if ok else 'FAIL'}] {label} {got}")
        if not ok:
            failures.append(label)

    stale = []
    for info in z.infolist():
        n = info.filename
        if n.endswith((".dex", ".xml", ".json", ".txt", ".properties", ".version",
                       ".kotlin_module", ".proto", ".js", ".arsc")):
            if b"com.br.top" in z.read(n) or b"com/br/top" in z.read(n):
                stale.append(n)
    print(f"[{'PASS' if not stale else 'FAIL'}] stale com.br.top references: {len(stale)}")
    if stale:
        failures.append("stale refs: %s" % stale[:5])

    dex_files = sorted(x for x in names if x.endswith(".dex"))
    has8 = "classes8.dex" in dex_files
    print(f"[{'PASS' if has8 else 'FAIL'}] classes8.dex (diagnostic classes) present")
    if not has8:
        failures.append("classes8.dex missing")
    dex_ok = len(dex_files) == 8
    for n in dex_files:
        d = z.read(n)[:36]
        magic_ok = d[:4] == b"dex\n" and d[4:7] in (b"035", b"037", b"038", b"039")
        if not magic_ok or struct.unpack("<I", d[32:36])[0] != z.getinfo(n).file_size:
            dex_ok = False
    print(f"[{'PASS' if dex_ok else 'FAIL'}] {len(dex_files)} DEX files valid")
    if not dex_ok:
        failures.append("dex validity")

    if has8:
        c8 = z.read("classes8.dex")
        need = [b"GhrpDiagActivity", b"GhrpDiagProvider", b"GhrpNativeTester",
                b"GhrpRendererTester", b"GhrpEngineTester", b"GhrpNetworkTester",
                b"GhrpReportExporter", b"BEFORE_LOAD_LIB_", b"ghrp-diag-report.txt",
                b"BEFORE_NATIVE_INIT", b"BEFORE_RENDERER"]
        missing = [x for x in need if x not in c8]
        print(f"[{'PASS' if not missing else 'FAIL'}] diagnostic classes + stage markers in classes8.dex")
        if missing:
            failures.append("missing in classes8: %s" % missing)

    libs = [n for n in names if n.startswith("lib/") and n.endswith(".so")]
    abis = sorted(set(n.split("/")[1] for n in libs))
    c_abis = abis == ["arm64-v8a", "armeabi-v7a"]
    c_count = len(libs) == 26
    print(f"[{'PASS' if c_abis else 'FAIL'}] ABIs = {abis}")
    print(f"[{'PASS' if c_count else 'FAIL'}] native library count = {len(libs)} (expect 26)")
    if not c_abis:
        failures.append("ABI set")
    if not c_count:
        failures.append("native lib count")

    arsc = z.getinfo("resources.arsc")
    c_arsc = arsc.compress_type == zipfile.ZIP_STORED
    print(f"[{'PASS' if c_arsc else 'FAIL'}] resources.arsc stored uncompressed")
    if not c_arsc:
        failures.append("arsc compression")

    print()
    if failures:
        print("VERIFY FAILED:", failures)
        return 1
    print("ALL STATIC CHECKS PASSED (DIAGNOSTIC APK)")
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
