#include "BpcArchive.h"
#include "../core/GHLog.h"
#include <cstdio>
#include <cstring>
#include <zlib.h>

namespace gh {

#pragma pack(push, 1)
struct ZipLocalHeader {
    uint32_t sig;
    uint16_t version, flags, method, mtime, mdate;
    uint32_t crc, csize, usize;
    uint16_t nameLen, extraLen;
};
#pragma pack(pop)

bool BpcArchive::open(const std::string& path) {
    close();
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) { GHERR("BpcArchive: cannot open %s", path.c_str()); return false; }

    // Prefer the central directory (end of file) for exact entry list; fall back to
    // streaming local headers (bpc files keep valid local sizes, no data descriptors).
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    char buf[512];
    long back = size < 66000 ? size : 66000;
    bool cdParsed = false;
    if (back > 22) {
        std::vector<uint8_t> tail(back);
        fseek(f, size - back, SEEK_SET);
        if (fread(tail.data(), 1, back, f) == (size_t)back) {
            for (long i = back - 22; i >= 0; i--) {
                if (memcmp(tail.data() + i, "PK\x05\x06", 4) == 0) {
                    uint16_t count; memcpy(&count, tail.data() + i + 10, 2);
                    uint32_t cdOff; memcpy(&cdOff, tail.data() + i + 16, 4);
                    (void)count; (void)cdOff;
                    cdParsed = true; // CD located; still stream local headers for robustness
                    break;
                }
            }
        }
    }
    (void)cdParsed; (void)buf;

    // Stream local file headers (works for both intact and truncated files).
    fseek(f, 0, SEEK_SET);
    long pos = 0;
    while (true) {
        ZipLocalHeader h;
        if (fseek(f, pos, SEEK_SET) != 0) break;
        if (fread(&h, sizeof(h), 1, f) != 1) break;
        if (h.sig != 0x04034b50u) break;
        if (h.nameLen == 0 || h.nameLen > 512) break;
        char name[513];
        if (fread(name, 1, h.nameLen, f) != h.nameLen) break;
        name[h.nameLen] = 0;
        if (h.extraLen && fseek(f, h.extraLen, SEEK_CUR) != 0) break;
        long dataOff = ftell(f);

        BpcEntry e;
        e.name = name;
        e.crc = h.crc; e.csize = h.csize; e.usize = h.usize;
        e.method = h.method;
        e.dataOffset = dataOff;
        entries_.push_back(std::move(e));

        if (h.csize == 0 && (h.flags & 0x8)) break; // streaming entry without sizes: stop
        pos = dataOff + h.csize;
        if (pos >= size) break;
    }
    fclose(f);
    path_ = path;
    GHLOG("BpcArchive: %s -> %zu entries", path.c_str(), entries_.size());
    return !entries_.empty();
}

const BpcEntry* BpcArchive::find(const std::string& name) const {
    for (auto& e : entries_) if (e.name == name) return &e;
    return nullptr;
}

std::vector<const BpcEntry*> BpcArchive::findBySuffix(const std::string& suffix) const {
    std::vector<const BpcEntry*> out;
    for (auto& e : entries_) {
        if (e.name.size() >= suffix.size() &&
            e.name.compare(e.name.size() - suffix.size(), suffix.size(), suffix) == 0)
            out.push_back(&e);
    }
    return out;
}

bool BpcArchive::read(const BpcEntry& e, std::vector<uint8_t>& out) const {
    FILE* f = fopen(path_.c_str(), "rb");
    if (!f) return false;
    std::vector<uint8_t> comp(e.csize);
    if (fseek(f, (long)e.dataOffset, SEEK_SET) != 0 ||
        fread(comp.data(), 1, e.csize, f) != e.csize) {
        fclose(f);
        GHERR("BpcArchive: short read for %s", e.name.c_str());
        return false;
    }
    fclose(f);

    out.resize(e.usize);
    if (e.method == 0) {
        if (e.csize != e.usize) return false;
        memcpy(out.data(), comp.data(), e.usize);
        return true;
    }
    if (e.method != 8) return false;
    z_stream zs; memset(&zs, 0, sizeof(zs));
    if (inflateInit2(&zs, -15) != Z_OK) return false;
    zs.next_in = comp.data();
    zs.avail_in = e.csize;
    zs.next_out = out.data();
    zs.avail_out = e.usize;
    int rc = inflate(&zs, Z_FINISH);
    inflateEnd(&zs);
    if (rc != Z_STREAM_END && !(rc == Z_OK && zs.total_out == e.usize)) {
        GHERR("BpcArchive: inflate failed rc=%d for %s", rc, e.name.c_str());
        return false;
    }
    return true;
}

} // namespace gh
