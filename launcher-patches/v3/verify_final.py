#!/usr/bin/env python3
"""Final Grand Horizon RP launcher APK verification (clean original-based build).
Usage: python3 verify_final.py <apk-path>
"""
import sys, os, struct, zipfile, hashlib

APK = sys.argv[1]
REQUIRED_PKG = "com.grandhorizonrp.launcher"
VC = 1529
VN = "16.102.14498"
ORIG_LIB_DIR = "/tmp/audit/launcher_src/lib"

def parse_axml(data):
    if data[:4] != b"\x03\x00\x08\x00":
        raise ValueError("not AXML")
    off = 8
    typ, hdr_sz, sz = struct.unpack("<HHI", data[off:off+8])
    str_count, _sc, flags, str_start, _ss = struct.unpack("<IIIII", data[off+8:off+28])
    is_utf8 = bool(flags & (1 << 8))
    offsets = [struct.unpack("<I", data[off+28+i*4:off+32+i*4])[0] for i in range(str_count)]
    base = off + str_start
    strings = []
    for o in offsets:
        p = base + o
        if is_utf8:
            l1 = data[p]; p += 1
            if l1 & 0x80: l1 = ((l1&0x7f)<<8)|data[p]; p += 1
            l2 = data[p]; p += 1
            if l2 & 0x80: l2 = ((l2&0x7f)<<8)|data[p]; p += 1
            strings.append(data[p:p+l2].decode("utf-8","replace"))
        else:
            l = struct.unpack("<H", data[p:p+2])[0]; p += 2
            if l & 0x8000:
                l = ((l&0x7fff)<<16)|struct.unpack("<H", data[p:p+2])[0]; p += 2
            strings.append(data[p:p+l*2].decode("utf-16-le","replace"))
    off += sz
    root_attrs = {}
    uses_sdk = {}
    components = []
    app_name = None
    app_attrs = {}
    provider_auths = []
    while off < len(data):
        if off + 8 > len(data): break
        typ, hdr_sz, sz = struct.unpack("<HHI", data[off:off+8])
        if typ == 0x0102:  # start element
            if sz < 16 or off+sz > len(data): break
            _line, _comment, ns, name_idx = struct.unpack("<IIII", data[off+8:off+24])
            attr_count = struct.unpack("<H", data[off+28:off+30])[0]
            name = strings[name_idx] if name_idx < len(strings) else "?"
            attrs = {}
            for i in range(attr_count):
                ao = off + 36 + i*20
                if ao+20 > len(data): break
                a_name = struct.unpack("<I", data[ao+4:ao+8])[0]
                a_raw = struct.unpack("<I", data[ao+8:ao+12])[0]
                tv_type = data[ao+15]
                tv_data = struct.unpack("<I", data[ao+16:ao+20])[0]
                key = strings[a_name] if a_name < len(strings) else "?"
                raw = strings[a_raw] if a_raw != 0xFFFFFFFF and a_raw < len(strings) else None
                if tv_type == 0x03 and raw is not None:
                    attrs[key] = raw
                elif tv_type == 0x10:
                    attrs[key] = tv_data if tv_data < 0x80000000 else tv_data - 0x100000000
                elif tv_type == 0x12:
                    attrs[key] = bool(tv_data)
                else:
                    attrs[key] = tv_data
            if name == "manifest":
                root_attrs = attrs
            elif name == "uses-sdk":
                uses_sdk = attrs
            elif name == "application":
                app_name = attrs.get("name")
                app_attrs.update(attrs)
            elif name in ("activity", "service", "receiver", "provider"):
                components.append((name, attrs.get("name")))
                if name == "provider" and "authorities" in attrs:
                    provider_auths.append(attrs["authorities"])
        off += sz
    return root_attrs, uses_sdk, components, app_name, strings, app_attrs, provider_auths

fails = []
def check(cond, msg):
    print(("[OK] " if cond else "[FAIL] ") + msg)
    if not cond: fails.append(msg)

z = zipfile.ZipFile(APK)
names = z.namelist()

# 1. container
check(len(names) == len(set(names)), f"no duplicate entries ({len(names)} entries)")
check(z.testzip() is None, "zip CRC integrity")

# 2. manifest
axml = z.read("AndroidManifest.xml")
attrs, sdk, comps, app, strings, app_attrs, provider_auths = parse_axml(axml)
check(attrs.get("package") == REQUIRED_PKG, f"package = {attrs.get('package')!r}")
check(attrs.get("versionCode") == VC, f"versionCode = {attrs.get('versionCode')}")
check(attrs.get("versionName") == VN, f"versionName = {attrs.get('versionName')!r}")
check(sdk.get("targetSdkVersion") == 36, f"targetSdk = {sdk.get('targetSdkVersion')}")
check(sdk.get("minSdkVersion") == 26, f"minSdk = {sdk.get('minSdkVersion')}")
check(app == "com.blackhub.bronline.launcher.App", f"application class = {app!r}")
comp_names = [c[1] for c in comps]
check("com.blackhub.bronline.game.core.JNIActivity" in comp_names, "JNIActivity declared")
check("com.blackhub.bronline.launcher.download.DownloadForegroundGuardService" in comp_names, "download service declared")
check(all("com.br.top" not in str(a) for a in provider_auths), "no com.br.top provider authorities")
check(any("com.grandhorizonrp.launcher.provider" in str(a) for a in provider_auths), f"renamed FileProvider authority present ({len(provider_auths)} providers)")
# gwpAsanMode encoding check (application element, INT_DEC -1)
check(app_attrs.get("gwpAsanMode") == -1, f"gwpAsanMode = {app_attrs.get('gwpAsanMode')} (INT_DEC -1)")

# 3. stale refs + engine intact
stale = 0
for n in names:
    if n.endswith(".dex") or n.endswith(".xml") or n.endswith(".arsc") or n.endswith(".json") or n.endswith(".properties") or n.endswith(".list") or n.endswith(".prof") or n.endswith(".profm"):
        data = z.read(n)
        stale += data.count(b"com.br.top") + data.count(b"com/br/top")
check(stale == 0, f"stale com.br.top refs in text entries: {stale}")

dexes = [n for n in names if n.endswith(".dex")]
check(len(dexes) == 7, f"dex count = {len(dexes)}")
valid_dex = all(z.read(n)[:4] == b"dex\n" for n in dexes)
check(valid_dex, "all dex headers valid")

# engine intact + patches present in dex
c4 = z.read("classes4.dex")
check(b"com/blackhub/bronline/game/core/JNILib" in c4, "engine JNILib class present in classes4")
check(b"cdn.jsdelivr.net/gh/raj998302-art/GrandHorizonRP-Launcher@main/client-api/" in c4, "jsDelivr API URL in classes4")
check(b"blackrussia.online" not in c4 and b"black-russia.com" not in c4, "no old API urls in classes4")
check(b"com.grandhorizonrp.launcher/files" in c4, "files path constant patched")
check(b"releases/download/latest/GrandHorizonRP-Launcher.apk" in c4, "self-update GitHub link patched")

# 4. resources.arsc
info = z.getinfo("resources.arsc")
check(info.compress_type == zipfile.ZIP_STORED, "resources.arsc stored uncompressed")
arsc = z.read("resources.arsc")
check(b"GRAND HORIZON RP" in arsc, "app label GRAND HORIZON RP in resources")
check(b"Grand Horizon" in arsc, "brand strings in resources")
# English sanity: a translated string present, Russian counterpart absent from default
check("Enter email or social media".encode("utf-8") in arsc, "EN translated string in resources")
check("Введите почту".encode("utf-8") not in arsc, "RU counterpart removed from default resources")

# 5. native libs byte-identical
lib_names = [n for n in names if n.startswith("lib/")]
check(len(lib_names) == 26, f"native lib count = {len(lib_names)}")
mismatch = []
for n in lib_names:
    rel = n[4:]  # strip lib/
    orig_path = os.path.join(ORIG_LIB_DIR, rel)
    if not os.path.exists(orig_path):
        mismatch.append(n + " (no original)")
        continue
    h1 = hashlib.sha256(z.read(n)).hexdigest()
    h2 = hashlib.sha256(open(orig_path,"rb").read()).hexdigest()
    if h1 != h2:
        mismatch.append(n)
check(not mismatch, f"native libs byte-identical to original ({mismatch[:3]})")

# 6. compression sanity
so_info = [z.getinfo(n) for n in names if n.endswith(".so")]
check(all(i.compress_type == zipfile.ZIP_DEFLATED for i in so_info), f".so deflated ({len(so_info)} files)")
pngs = [z.getinfo(n) for n in names if n.endswith(".png")][:5]
check(all(i.compress_type == zipfile.ZIP_STORED for i in pngs), "sample pngs stored")

# 7. assets present
for a in ["assets/dexopt/baseline.prof", "assets/PublicSuffixDatabase.list", "kotlin/kotlin.kotlin_builtins"]:
    check(a in names, f"{a} present")

# 8. signing blocks (APK Signing Block v2/v3)
raw = open(APK, "rb").read()
check(b"APK Sig Block 42" in raw, "APK Signing Block v2+ present")

print()
print(f"VERIFICATION: {'ALL PASS' if not fails else str(len(fails)) + ' FAILURES'}")
sys.exit(0 if not fails else 1)
