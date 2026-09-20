// GHEngine — BPC archive reader (the game-data containers are ZIP files; see
// ENGINE_ARCHITECTURE.md §1.1). Uses the platform zlib for DEFLATE — the container
// logic is our own.
#pragma once
#include <string>
#include <vector>
#include <cstdint>

namespace gh {

struct BpcEntry {
    std::string name;
    uint32_t crc = 0, csize = 0, usize = 0;
    uint16_t method = 0;
    int64_t dataOffset = 0;   // absolute file offset of compressed data
};

class BpcArchive {
public:
    bool open(const std::string& path);
    void close() { entries_.clear(); path_.clear(); }
    bool isOpen() const { return !path_.empty(); }
    const std::vector<BpcEntry>& entries() const { return entries_; }
    const BpcEntry* find(const std::string& name) const;
    // Decompress one entry into out. Returns false on error.
    bool read(const BpcEntry& e, std::vector<uint8_t>& out) const;
    // Convenience: list entry names with a given prefix (e.g. ".mod").
    std::vector<const BpcEntry*> findBySuffix(const std::string& suffix) const;

private:
    std::string path_;
    std::vector<BpcEntry> entries_;
};

} // namespace gh
