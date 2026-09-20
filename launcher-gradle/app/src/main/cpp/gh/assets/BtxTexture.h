// GHEngine — BTX texture loader. Shipped game textures are .btx = 4-byte prefix +
// standard KTX v1 with ASTC-compressed mip chains (ENGINE_ARCHITECTURE.md §1.4).
#pragma once
#include <vector>
#include <cstdint>
#include <string>

namespace gh {

struct GlTexture {
    uint32_t id = 0;
    int width = 0, height = 0;
    bool valid = false;
    void destroy();
};

class BtxTexture {
public:
    // data: raw .btx bytes. isAstcSupported: runtime extension check.
    static bool load(const uint8_t* data, size_t size, bool astcSupported, GlTexture& out,
                     std::string* err = nullptr);
    // Query KTX dims without creating a GL texture (for CPU-side inspection).
    static bool probe(const uint8_t* data, size_t size, int& w, int& h, uint32_t& internalFormat);
};

} // namespace gh
