#include "BtxTexture.h"
#include "../render/GlFuncs.h"
#include "../core/GHLog.h"
#include <cstring>
#include <string>
#include <cmath>

namespace gh {

// ASTC block footprint per format id.
static bool astcBlockDims(uint32_t internalFormat, int& bw, int& bh) {
    switch (internalFormat) {
        case 0x93B0: bw = 4;  bh = 4;  return true;   // GL_COMPRESSED_RGBA_ASTC_4x4_KHR
        case 0x93B1: bw = 5;  bh = 4;  return true;
        case 0x93B2: bw = 5;  bh = 5;  return true;
        case 0x93B3: bw = 6;  bh = 5;  return true;
        case 0x93B4: bw = 6;  bh = 6;  return true;   // observed in Characters.bpc
        case 0x93B5: bw = 8;  bh = 5;  return true;
        case 0x93B6: bw = 8;  bh = 6;  return true;
        case 0x93B7: bw = 8;  bh = 8;  return true;
        case 0x93B8: bw = 10; bh = 5;  return true;
        case 0x93B9: bw = 10; bh = 6;  return true;
        case 0x93BA: bw = 10; bh = 8;  return true;
        case 0x93BB: bw = 10; bh = 10; return true;
        case 0x93BC: bw = 12; bh = 10; return true;
        case 0x93BD: bw = 12; bh = 12; return true;
        default: return false;
    }
}

static const uint8_t KTX_MAGIC[12] = {0xAB, 0x4B, 0x54, 0x58, 0x20, 0x31, 0x31, 0xBB, 0x0D, 0x0A, 0x1A, 0x0A};

void GlTexture::destroy() {
    if (id && gh_glDeleteTextures) { gh_glDeleteTextures(1, &id); }
    id = 0; valid = false;
}

bool BtxTexture::probe(const uint8_t* data, size_t size, int& w, int& h, uint32_t& internalFormat) {
    if (size < 72 || memcmp(data + 4, KTX_MAGIC, 12) != 0) return false;
    uint32_t endian; memcpy(&endian, data + 16, 4);
    if (endian != 0x04030201u) return false;
    uint32_t glInternal; memcpy(&glInternal, data + 32, 4);
    uint32_t width, height; memcpy(&width, data + 40, 4); memcpy(&height, data + 44, 4);
    if (!width || !height) return false;
    internalFormat = glInternal; w = (int)width; h = (int)height;
    return true;
}

bool BtxTexture::load(const uint8_t* data, size_t size, bool astcSupported, GlTexture& out,
                      std::string* err) {
    out.destroy();
    int w, h; uint32_t internalFormat;
    if (!probe(data, size, w, h, internalFormat)) {
        if (err) *err = "not a KTX/btx container";
        return false;
    }
    uint32_t mips; memcpy(&mips, data + 60, 4);
    uint32_t kvBytes; memcpy(&kvBytes, data + 64, 4);
    if (mips == 0 || mips > 16) mips = 1;

    if (!astcSupported) {
        if (err) *err = "device lacks ASTC support";
        return false;
    }

    // Walk mip chain: u32 imageSize + payload (padded to 4).
    size_t off = 68 + kvBytes;
    // Data-driven footprint: the shipped .btx files tag 0x93B4 regardless of the
    // real block footprint, so derive it from the first mip size (blocks are 16 B).
    int bw = 6, bh = 6; // observed default (512x512 -> 118336 B = 86^2 blocks)
    {
        uint32_t mip0 = 0;
        if (off + 4 <= size) memcpy(&mip0, data + off, 4);
        if (mip0 >= 16 && w > 0 && h > 0) {
            double blocks = mip0 / 16.0;
            double area = (double)w * h;
            double side = sqrt(area / blocks);
            int cand = (int)(side + 0.5);
            bool matched = false;
            for (int c = 4; c <= 12 && !matched; c++) {
                int bx = (w + c - 1) / c, by = (h + c - 1) / c;
                if ((uint32_t)(bx * by * 16) == mip0) { bw = c; bh = c; matched = true; }
            }
            if (!matched) {
                // non-square footprints
                static const int fp[][2] = {{5,4},{6,5},{8,5},{8,6},{10,5},{10,6},{10,8},{10,10},{12,10},{12,12}};
                for (auto& f : fp) {
                    int bx = (w + f[0] - 1) / f[0], by = (h + f[1] - 1) / f[1];
                    if ((uint32_t)(bx * by * 16) == mip0) { bw = f[0]; bh = f[1]; matched = true; break; }
                }
            }
            if (!matched && cand >= 4 && cand <= 12) { bw = cand; bh = cand; }
        }
    }
    gh_glGenTextures(1, &out.id);
    gh_glBindTexture(GL_TEXTURE_2D, out.id);
    gh_glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    gh_glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    gh_glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    gh_glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    gh_glTexParameteri(GL_TEXTURE_2D, 0x813D /*GL_TEXTURE_MAX_LEVEL*/, (GLint)mips - 1);

    int mipW = w, mipH = h;
    bool ok = false;
    for (uint32_t m = 0; m < mips && off + 4 <= size; m++) {
        uint32_t imgSize; memcpy(&imgSize, data + off, 4); off += 4;
        if (off + imgSize > size) break;
        int blocksX = (mipW + bw - 1) / bw;
        int blocksY = (mipH + bh - 1) / bh;
        uint32_t expect = (uint32_t)(blocksX * blocksY * 16);
        if (imgSize != expect) {
            // Trust the stored size if it is block-consistent; otherwise stop the chain here.
            if (imgSize < expect) { break; }
        }
        uint32_t glFmt = internalFormat;
        if (bw == 4 && bh == 4) glFmt = 0x93B0;
        else if (bw == 5 && bh == 4) glFmt = 0x93B1;
        else if (bw == 5 && bh == 5) glFmt = 0x93B2;
        else if (bw == 6 && bh == 5) glFmt = 0x93B3;
        else if (bw == 6 && bh == 6) glFmt = 0x93B4;
        else if (bw == 8 && bh == 5) glFmt = 0x93B5;
        else if (bw == 8 && bh == 6) glFmt = 0x93B6;
        else if (bw == 8 && bh == 8) glFmt = 0x93B7;
        else if (bw == 10 && bh == 5) glFmt = 0x93B8;
        else if (bw == 10 && bh == 6) glFmt = 0x93B9;
        else if (bw == 10 && bh == 8) glFmt = 0x93BA;
        else if (bw == 10 && bh == 10) glFmt = 0x93BB;
        else if (bw == 12 && bh == 10) glFmt = 0x93BC;
        else if (bw == 12 && bh == 12) glFmt = 0x93BD;
        gh_glCompressedTexImage2D(GL_TEXTURE_2D, (GLint)m, (GLenum)glFmt,
                                  mipW, mipH, 0, (GLsizei)imgSize, data + off);
        GLenum e = gh_glGetError();
        if (e != GL_NO_ERROR) {
            GHERR("BtxTexture: glCompressedTexImage2D mip %u err 0x%x", m, e);
            break;
        }
        ok = (m == mips - 1) ? true : ok;
        off += imgSize;
        off = (off + 3) & ~(size_t)3;
        mipW = mipW > 1 ? mipW / 2 : 1;
        mipH = mipH > 1 ? mipH / 2 : 1;
    }
    if (!ok) {
        // At least mip 0 must have landed.
        gh_glGetError();
        out.destroy();
        if (err) *err = "mip chain incomplete";
        return false;
    }
    out.width = w; out.height = h; out.valid = true;
    return true;
}

} // namespace gh
