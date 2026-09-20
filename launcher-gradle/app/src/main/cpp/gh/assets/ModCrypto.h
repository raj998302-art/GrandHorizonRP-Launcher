// GHEngine — MOD container decryption.
//
// Contract (reverse-engineered by observation of the shipped game data, documented
// in ENGINE_ARCHITECTURE.md §1.2): the first N x 2048-byte blocks of a .mod body are
// encrypted with a custom XTEA-8 variant; the 4-word key is derived from a constant
// embedded in the ORIGINAL engine binary (XOR 0x12913AFB, ROR 19 per word — one
// derived word equals the .mod magic 0xAB921033, which validates the derivation).
//
// This file is an independent re-implementation of that observed format contract
// for interoperability with the shipped game-data files. No original code is used.
#pragma once
#include <cstdint>
#include <cstring>

namespace gh {

struct ModCrypto {
    static constexpr uint32_t DELTA = 0x9E3779B9u;
    // Key observed in the wild; word[1] == the .mod magic 0xAB921033.
    static constexpr uint32_t K[4] = {0x1a902f89u, 0xab921033u, 0xe5921033u, 0x1a904ab3u};

    static uint32_t ror32(uint32_t v, int n) { return (v >> n) | (v << (32 - n)); }

    // Derive the 4-word key from a 16-byte engine constant (kept for documentation
    // and future key rotations; the shipped files use the constant key above).
    static void deriveKey(const uint8_t raw[16], uint32_t out[4]) {
        for (int i = 0; i < 4; i++) {
            uint32_t w;
            memcpy(&w, raw + i * 4, 4);
            out[i] = ror32(w ^ 0x12913AFBu, 19);
        }
    }

    // One 8-byte pair, 8 rounds, sum starts at DELTA*8 and decrements each round.
    static void decryptPair(uint32_t& v0, uint32_t& v1) {
        uint32_t s = 8u * DELTA;
        for (int r = 0; r < 8; r++) {
            uint32_t f1 = (K[2] + (v0 << 4)) ^ (v0 + s) ^ (K[3] + (v0 >> 5));
            v1 -= f1;
            uint32_t f0 = (K[0] + (v1 << 4)) ^ (v1 + s) ^ (K[1] + (v1 >> 5));
            v0 -= f0;
            s -= DELTA;
        }
    }

    // Decrypt the first nBlocks*2048 bytes of the body in place.
    static void decryptBlocks(uint8_t* body, size_t bodyLen, uint32_t nBlocks) {
        size_t full = bodyLen / 2048;
        if (nBlocks > full) nBlocks = (uint32_t)full;
        for (uint32_t b = 0; b < nBlocks; b++) {
            uint8_t* blk = body + (size_t)b * 2048;
            for (int p = 0; p < 256; p++) {
                uint32_t v0, v1;
                memcpy(&v0, blk + p * 8, 4);
                memcpy(&v1, blk + p * 8 + 4, 4);
                decryptPair(v0, v1);
                memcpy(blk + p * 8, &v0, 4);
                memcpy(blk + p * 8 + 4, &v1, 4);
            }
        }
    }
};

} // namespace gh
