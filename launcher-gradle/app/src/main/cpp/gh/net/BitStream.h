// GHEngine net — RakNet 3.x bitstream (SA-MP 0.3.7-R2 wire format).
// MSB-first bit order; multi-byte values in memory (little-endian) byte order;
// compressed u16/u32 use the RakNet WriteCompressed encoding.
#pragma once
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

namespace gh::net {

class BitStream {
public:
    std::vector<uint8_t> data;
    size_t wpos = 0;   // bit position (write)
    size_t rpos = 0;   // bit position (read)

    BitStream() = default;
    explicit BitStream(const std::vector<uint8_t>& d) : data(d) {}
    explicit BitStream(const uint8_t* d, size_t n) : data(d, d + n) {}

    void writeBit(bool v) {
        size_t idx = wpos >> 3;
        if (idx >= data.size()) data.resize(idx + 1, 0);
        if (v) data[idx] |= (uint8_t)(0x80u >> (wpos & 7));
        wpos++;
    }
    void writeBits(uint32_t v, int n) {
        for (int i = n - 1; i >= 0; --i) writeBit((v >> i) & 1);
    }
    void writeU8(uint8_t v) { writeBits(v, 8); }
    void writeU16(uint16_t v) { writeBits(v & 0xFF, 8); writeBits((v >> 8) & 0xFF, 8); }
    void writeU32(uint32_t v) {
        writeBits(v & 0xFF, 8); writeBits((v >> 8) & 0xFF, 8);
        writeBits((v >> 16) & 0xFF, 8); writeBits((v >> 24) & 0xFF, 8);
    }
    void writeBool(bool v) { writeBit(v); }
    void writeBytes(const uint8_t* b, size_t n) { for (size_t i = 0; i < n; ++i) writeBits(b[i], 8); }
    void writeBytes(const std::vector<uint8_t>& v) { writeBytes(v.data(), v.size()); }
    void writeStr8(const std::string& s) {
        writeU8((uint8_t)s.size());
        writeBytes((const uint8_t*)s.data(), s.size());
    }

    // RakNet WriteCompressed<u16>: [msbZero][if0: nibbleFlag + 4|8 bits][else 16 bits]
    void writeCompressedU16(uint16_t v) {
        if (v < 256) {
            writeBit(true);
            if ((v & 0xF0) == 0) { writeBit(true); writeBits(v, 4); }
            else { writeBit(false); writeBits(v, 8); }
        } else {
            writeBit(false);
            writeU16(v);
        }
    }
    // RakNet WriteCompressed<u32>
    void writeCompressedU32(uint32_t v) {
        uint8_t b[4] = { (uint8_t)v, (uint8_t)(v >> 8), (uint8_t)(v >> 16), (uint8_t)(v >> 24) };
        int i = 3;
        while (i > 0) {
            if (b[i] == 0) { writeBit(true); }
            else {
                writeBit(false);
                for (int j = 0; j <= i; ++j) writeBits(b[j], 8);
                return;
            }
            --i;
        }
        if ((b[0] & 0xF0) == 0) { writeBit(true); writeBits(b[0] & 0x0F, 4); }
        else { writeBit(false); writeBits(b[0], 8); }
    }

    // ---- read ----
    bool readBit() {
        size_t idx = rpos >> 3;
        if (idx >= data.size()) { overrun_ = true; return false; }
        bool v = (data[idx] >> (7 - (rpos & 7))) & 1;
        rpos++;
        return v;
    }
    uint32_t readBits(int n) {
        uint32_t v = 0;
        for (int i = 0; i < n; ++i) v = (v << 1) | (readBit() ? 1 : 0);
        return v;
    }
    uint8_t readU8() { return (uint8_t)readBits(8); }
    uint16_t readU16() { uint16_t lo = readBits(8); uint16_t hi = readBits(8); return (uint16_t)(lo | (hi << 8)); }
    uint32_t readU32() {
        uint32_t v = 0;
        for (int i = 0; i < 4; ++i) v |= readBits(8) << (8 * i);
        return v;
    }
    std::vector<uint8_t> readBytes(size_t n) {
        std::vector<uint8_t> out(n);
        for (size_t i = 0; i < n; ++i) out[i] = readU8();
        return out;
    }
    std::string readStr8() {
        uint8_t n = readU8();
        auto b = readBytes(n);
        return std::string((const char*)b.data(), b.size());
    }
    uint16_t readCompressedU16() {
        if (readBit()) {                 // MSB byte zero
            if (readBit()) return (uint16_t)readBits(4);
            return (uint16_t)readU8();
        }
        return readU16();
    }
    uint32_t readCompressedU32() {
        for (int i = 3; i > 0; --i) {
            if (readBit()) continue;     // high byte zero
            uint32_t v = 0;
            for (int j = 0; j <= i; ++j) v |= readU8() << (8 * j);
            return v;
        }
        if (readBit()) return readBits(4);
        return readU8();
    }

    size_t bitsLeft() const { return (data.size() << 3) > rpos ? ((data.size() << 3) - rpos) : 0; }
    bool overrun() const { return overrun_; }
    void alignRead() { if (rpos & 7) rpos += 8 - (rpos & 7); }

private:
    bool overrun_ = false;
};

} // namespace gh::net
