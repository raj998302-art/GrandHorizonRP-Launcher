#include "ModMesh.h"
#include "ModCrypto.h"
#include "../core/GHLog.h"
#include <cstring>
#include <cmath>
#include <algorithm>

namespace gh {

// f16 decode (IEEE 754 half) — own implementation.
static float halfToFloat(uint16_t h) {
    uint32_t sign = (h >> 15) & 1;
    uint32_t exp = (h >> 10) & 0x1F;
    uint32_t man = h & 0x3FF;
    float out;
    if (exp == 0) {
        out = (float)man * 1.0f / 16777216.0f; // subnormal: man * 2^-24
    } else if (exp == 31) {
        out = man ? NAN : INFINITY;
    } else {
        uint32_t bits = (sign << 31) | ((exp - 15 + 127) << 23) | (man << 13);
        memcpy(&out, &bits, 4);
    }
    return out;
}

static bool plausibleFloat(float v) {
    return std::isfinite(v) && std::fabs(v) < 1e4f;
}

// Find runs of u16 values that look like index/edge entries.
static void findU16Runs(const std::vector<uint8_t>& b, size_t searchStart, uint32_t maxVal,
                        size_t minLen, std::vector<std::pair<size_t, size_t>>& runs) {
    size_t i = searchStart;
    while (i + 2 < b.size()) {
        uint16_t v; memcpy(&v, &b[i], 2);
        if (v < maxVal) {
            size_t j = i, len = 0;
            while (j + 2 <= b.size()) {
                uint16_t u; memcpy(&u, &b[j], 2);
                if (u >= maxVal) break;
                len++;
                j += 2;
            }
            if (len >= minLen) runs.emplace_back(i, len);
            i = j + 2;
        } else {
            i += 2;
        }
    }
}

// Classify a float window: 'P' positions, 'N' normals, 'U' uvs, 0 unknown.
static char classifyFloatWindow(const std::vector<uint8_t>& b, size_t off, size_t floats) {
    int unitTriples = 0, totalTriples = 0, in01 = 0, outOf1 = 0;
    for (size_t k = 0; k + 3 <= floats; k += 3) {
        float x, y, z;
        memcpy(&x, &b[off + (k + 0) * 4], 4);
        memcpy(&y, &b[off + (k + 1) * 4], 4);
        memcpy(&z, &b[off + (k + 2) * 4], 4);
        if (!plausibleFloat(x) || !plausibleFloat(y) || !plausibleFloat(z)) return 0;
        totalTriples++;
        float l = sqrtf(x * x + y * y + z * z);
        if (std::fabs(l - 1.0f) < 0.03f) unitTriples++;
        for (float v : {x, y, z}) {
            if (v >= -0.001f && v <= 1.0f) in01++;
            if (v < -1.05f || v > 1.05f) outOf1++;
        }
    }
    if (!totalTriples) return 0;
    if (unitTriples * 4 >= totalTriples) return 'N';
    if (outOf1 > 0) return 'P';
    if (in01 >= totalTriples * 3 * 8 / 10) return 'U';
    return 'P';
}

// Find the largest f16-triple block with genuine 3D spread (character positions).
static bool findF16PositionBlock(const std::vector<uint8_t>& b, size_t& outStart, size_t& outCount) {
    struct Cand { size_t start, count; float diag; };
    std::vector<Cand> cands;
    for (size_t phase = 0; phase < 6; phase++) {
    size_t i = phase;
    while (i + 6 < b.size()) {
        float x = halfToFloat(*(const uint16_t*)&b[i]);
        float y = halfToFloat(*(const uint16_t*)&b[i + 2]);
        float z = halfToFloat(*(const uint16_t*)&b[i + 4]);
        bool head = std::isfinite(x) && std::isfinite(y) && std::isfinite(z)
                    && std::fabs(x) < 40 && std::fabs(y) < 40 && std::fabs(z) < 40
                    && (*(const uint16_t*)&b[i] != 0 || *(const uint16_t*)&b[i + 2] != 0
                        || *(const uint16_t*)&b[i + 4] != 0);
        if (head) {
            size_t j = i;
            float mn[3] = {1e9f, 1e9f, 1e9f}, mx[3] = {-1e9f, -1e9f, -1e9f};
            size_t n = 0;
            while (j + 6 <= b.size()) {
                float v[3] = {halfToFloat(*(const uint16_t*)&b[j]),
                              halfToFloat(*(const uint16_t*)&b[j + 2]),
                              halfToFloat(*(const uint16_t*)&b[j + 4])};
                bool okv = std::isfinite(v[0]) && std::isfinite(v[1]) && std::isfinite(v[2])
                           && std::fabs(v[0]) < 40 && std::fabs(v[1]) < 40 && std::fabs(v[2]) < 40
                           && (*(const uint16_t*)&b[j] != 0 || *(const uint16_t*)&b[j + 2] != 0
                               || *(const uint16_t*)&b[j + 4] != 0);
                if (!okv) break;
                for (int k = 0; k < 3; k++) {
                    mn[k] = std::min(mn[k], v[k]);
                    mx[k] = std::max(mx[k], v[k]);
                }
                n++;
                j += 6;
            }
            if (n >= 200) {
                float d = sqrtf((mx[0]-mn[0])*(mx[0]-mn[0]) + (mx[1]-mn[1])*(mx[1]-mn[1]) + (mx[2]-mn[2])*(mx[2]-mn[2]));
                // edge buffers read as f16 look like near-zero tiny values; reject flat blobs
                float spread[3] = {mx[0]-mn[0], mx[1]-mn[1], mx[2]-mn[2]};
                bool nonFlat = spread[0] > 0.25f && spread[1] > 0.25f && spread[2] > 0.25f;
                if (nonFlat && d > 0.8f && d < 60.0f)
                    cands.push_back({i, n, d});
            }
            i = j;
        } else {
            i += 6;
        }
    }
    }
    if (cands.empty()) return false;
    std::sort(cands.begin(), cands.end(), [](const Cand& a, const Cand& b2) { return a.count > b2.count; });
    outStart = cands[0].start;
    outCount = cands[0].count;
    return outCount >= 200;
}

bool ModMesh::parse(const uint8_t* data, size_t size, ModMeshData& out) {
    out = ModMeshData{};
    if (size < 64) return false;
    uint32_t magic, dsize, cnt;
    memcpy(&magic, data + 0, 4);
    memcpy(&dsize, data + 4, 4);
    memcpy(&cnt, data + 8, 4);
    if (magic != 0xAB921033u) { GHERR("ModMesh: bad magic %08x", magic); return false; }
    if ((size_t)dsize + 28 > size) { GHERR("ModMesh: dsize beyond file"); return false; }

    std::vector<uint8_t> body(data + 28, data + 28 + dsize);
    ModCrypto::decryptBlocks(body.data(), body.size(), cnt);

    // Embedded mesh name (ASCII run in the first 512 bytes).
    {
        size_t limit = std::min(body.size(), (size_t)512);
        size_t i = 64;
        while (i < limit) {
            if (body[i] >= 'A' && body[i] <= 'z') {
                size_t j = i;
                while (j < limit && ((body[j] >= 'A' && body[j] <= 'z') ||
                                     (body[j] >= '0' && body[j] <= '9') || body[j] == '_')) j++;
                if (j - i >= 4 && j - i <= 32) {
                    out.name.assign((const char*)&body[i], j - i);
                    break;
                }
                i = j;
            } else i++;
        }
    }

    // ---- Path B: f16 character mesh (positions + edge buffers) ----------
    size_t posStart = 0, posCount = 0;
    if (findF16PositionBlock(body, posStart, posCount)) {
        out.positions.resize(posCount * 3);
        for (size_t v = 0; v < posCount; v++) {
            out.positions[v * 3 + 0] = halfToFloat(*(const uint16_t*)&body[posStart + v * 6]);
            out.positions[v * 3 + 1] = halfToFloat(*(const uint16_t*)&body[posStart + v * 6 + 2]);
            out.positions[v * 3 + 2] = halfToFloat(*(const uint16_t*)&body[posStart + v * 6 + 4]);
        }
        size_t posEnd = posStart + posCount * 6;

        // Edge buffers: u16 pair runs outside the position block, values < posCount.
        std::vector<std::pair<size_t, size_t>> runs;
        findU16Runs(body, 384, (uint32_t)posCount, 400, runs);
        std::vector<uint16_t> tris;
        size_t bestEdges = 0;
        for (auto& r : runs) {
            if (r.first + 4 > posStart && r.first < posEnd + 8) continue; // inside positions
            // reconstruct faces from consecutive shared-vertex edges
            std::vector<uint16_t> faces;
            size_t edgeCount = r.second / 2;
            const uint16_t* e = (const uint16_t*)&body[r.first];
            size_t used = 0;
            for (size_t i = 0; i + 1 < edgeCount; i++) {
                uint16_t a1 = e[i * 2], b1 = e[i * 2 + 1];
                uint16_t a2 = e[(i + 1) * 2], b2 = e[(i + 1) * 2 + 1];
                int shared = -1;
                if (a1 == a2 || a1 == b2) shared = a1;
                else if (b1 == a2 || b1 == b2) shared = b1;
                if (shared < 0) continue;
                // two distinct non-shared endpoints
                uint16_t o1 = 0xFFFF, o2 = 0xFFFF;
                uint16_t cand[4] = {a1, b1, a2, b2};
                for (uint16_t c : cand) {
                    if (c == (uint16_t)shared) continue;
                    if (o1 == 0xFFFF) o1 = c;
                    else if (o2 == 0xFFFF && c != o1) o2 = c;
                }
                if (o1 == 0xFFFF || o2 == 0xFFFF) continue;
                faces.push_back(o1); faces.push_back(o2); faces.push_back((uint16_t)shared);
                used++;
            }
            if (faces.size() / 3 > tris.size() / 3) {
                tris = std::move(faces);
                bestEdges = used;
            }
        }
        if (tris.size() >= 300) {
            out.indices = std::move(tris);
            out.valid = true;
            computeBounds(out);
            computeNormals(out);
            GHLOG("ModMesh[f16]: %s verts=%zu tris=%zu (edges=%zu)",
                  out.name.c_str(), posCount, out.triangleCount(), bestEdges);
            return true;
        }
        GHLOG("ModMesh[f16]: %s had positions (%zu verts) but no edge buffer — falling back",
              out.name.c_str(), posCount);
        out.positions.clear();
        out = ModMeshData{};
        out.name.clear();
        // fall through to f32 path with a fresh body copy (body is unchanged)
    }

    // ---- Path A: f32 static mesh (positions + u16 triangle lists) -------
    std::vector<std::pair<size_t, size_t>> idxRuns;
    findU16Runs(body, 384, 0x400, 24, idxRuns);

    struct Seg { size_t off; size_t floats; char kind; };
    std::vector<Seg> segs;
    const size_t W = 24;
    size_t i = 384;
    while (i + W * 4 <= body.size()) {
        char c = classifyFloatWindow(body, i, W);
        if (c) {
            size_t j = i + W * 4;
            while (j + W * 4 <= body.size()) {
                if (classifyFloatWindow(body, j, W) != c) break;
                j += W * 4;
            }
            segs.push_back({i, (j - i) / 4, c});
            i = j;
        } else {
            i += 4;
        }
    }

    Seg* posSeg = nullptr;
    for (auto& s : segs)
        if (s.kind == 'P' && (!posSeg || s.floats > posSeg->floats)) posSeg = &s;
    if (!posSeg || posSeg->floats < 9) { GHERR("ModMesh: no position array found"); return false; }

    size_t vertCount = posSeg->floats / 3;
    out.positions.resize(vertCount * 3);
    memcpy(out.positions.data(), &body[posSeg->off], vertCount * 3 * 4);

    for (auto& s : segs) {
        if (s.kind == 'N' && s.floats / 3 == vertCount) {
            out.normals.resize(vertCount * 3);
            memcpy(out.normals.data(), &body[s.off], vertCount * 3 * 4);
            break;
        }
    }
    for (auto& s : segs) {
        if (s.kind == 'U' && s.floats / 2 == vertCount) {
            out.uvs.resize(vertCount * 2);
            memcpy(out.uvs.data(), &body[s.off], vertCount * 2 * 4);
            break;
        }
    }

    std::pair<size_t, size_t>* bestRun = nullptr;
    for (auto& r : idxRuns) {
        bool okv = true;
        for (size_t k = 0; k < r.second; k++) {
            uint16_t u; memcpy(&u, &body[r.first + k * 2], 2);
            if (u >= vertCount) { okv = false; break; }
        }
        if (okv && r.second >= 24 && (!bestRun || r.second > bestRun->second)) bestRun = &r;
    }
    if (!bestRun) { GHERR("ModMesh: no usable index run"); return false; }

    size_t nIdx = bestRun->second / 3 * 3;
    out.indices.resize(nIdx);
    memcpy(out.indices.data(), &body[bestRun->first], nIdx * 2);

    if (out.normals.empty()) computeNormals(out);
    computeBounds(out);
    out.valid = true;
    GHLOG("ModMesh[f32]: %s verts=%zu tris=%zu uv=%s nrm=%s",
          out.name.c_str(), vertCount, out.triangleCount(),
          out.uvs.empty() ? "-" : "y", out.normals.empty() ? "-" : "y");
    return true;
}

void ModMesh::computeBounds(ModMeshData& m) {
    m.boundsMin = Vec3(1e9f, 1e9f, 1e9f);
    m.boundsMax = Vec3(-1e9f, -1e9f, -1e9f);
    for (size_t v = 0; v < m.vertexCount(); v++) {
        for (int k = 0; k < 3; k++) {
            float val = m.positions[v * 3 + k];
            float& mn = k == 0 ? m.boundsMin.x : k == 1 ? m.boundsMin.y : m.boundsMin.z;
            float& mx = k == 0 ? m.boundsMax.x : k == 1 ? m.boundsMax.y : m.boundsMax.z;
            if (val < mn) mn = val;
            if (val > mx) mx = val;
        }
    }
}

void ModMesh::computeNormals(ModMeshData& m) {
    m.normals.assign(m.vertexCount() * 3, 0.0f);
    for (size_t t = 0; t + 2 < m.indices.size(); t += 3) {
        uint16_t a = m.indices[t], b = m.indices[t + 1], c = m.indices[t + 2];
        if (a >= m.vertexCount() || b >= m.vertexCount() || c >= m.vertexCount()) continue;
        Vec3 pa(m.positions[a * 3], m.positions[a * 3 + 1], m.positions[a * 3 + 2]);
        Vec3 pb(m.positions[b * 3], m.positions[b * 3 + 1], m.positions[b * 3 + 2]);
        Vec3 pc(m.positions[c * 3], m.positions[c * 3 + 1], m.positions[c * 3 + 2]);
        Vec3 n = cross(pb - pa, pc - pa);
        for (uint16_t idx : {a, b, c}) {
            m.normals[idx * 3] += n.x;
            m.normals[idx * 3 + 1] += n.y;
            m.normals[idx * 3 + 2] += n.z;
        }
    }
    for (size_t v = 0; v < m.vertexCount(); v++) {
        Vec3 n(m.normals[v * 3], m.normals[v * 3 + 1], m.normals[v * 3 + 2]);
        n = normalize(n);
        m.normals[v * 3] = n.x; m.normals[v * 3 + 1] = n.y; m.normals[v * 3 + 2] = n.z;
    }
}

} // namespace gh
