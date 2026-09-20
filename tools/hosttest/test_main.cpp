#include <zlib.h>
#include <algorithm>
#include <vector>
#include "gh/assets/ModMesh.h"
#include "gh/assets/BtxTexture.h"
#include "gh/assets/AniAnimation.h"
#include "gh/assets/BpcArchive.h"
#include <cstdio>
#include <cmath>
using namespace gh;

int main(int argc, char** argv) {
    const char* dir = argc > 1 ? argv[1] : "/home/z/ghrp-scratch/fmt";
    char path[512];
    int pass = 0, fail = 0;

    // --- Test 1: BPC archive + MOD decrypt + parse (cylinder known geometry)
    {
        BpcArchive a;
        snprintf(path, sizeof(path), "%s/files.mesh.br_common.bpc", dir);
        if (!a.open(path)) { printf("T1 FAIL: archive\n"); fail++; }
        else {
            const BpcEntry* e = a.find("zonecylb.mod");
            if (!e) { printf("T1 FAIL: entry\n"); fail++; }
            else {
                std::vector<uint8_t> raw;
                a.read(*e, raw);
                ModMeshData m;
                if (!ModMesh::parse(raw.data(), raw.size(), m)) { printf("T1 FAIL: parse\n"); fail++; }
                else {
                    // Cylinder: bounds ~[-0.5,0.5]x[-0.5,0.5]x[0,1.67], verts on circle r=0.5
                    float dx = m.boundsMax.x - m.boundsMin.x, dy = m.boundsMax.y - m.boundsMin.y, dz = m.boundsMax.z - m.boundsMin.z;
                    float cx = (m.boundsMax.x+m.boundsMin.x)/2, cy=(m.boundsMax.y+m.boundsMin.y)/2, cz=(m.boundsMax.z+m.boundsMin.z)/2;
                    // robust dims from p10..p90 of each axis
                    std::vector<float> xs3;
                    for (size_t v = 0; v < m.vertexCount(); v++) xs3.push_back(m.positions[v*3]);
                    std::sort(xs3.begin(), xs3.end());
                    float dxr = xs3[xs3.size()*9/10] - xs3[xs3.size()/10];
                    dx = dxr;
                    // robust center: median of y and z
                    std::vector<float> ys, zs;
                    for (size_t v = 0; v < m.vertexCount(); v++) {
                        ys.push_back(m.positions[v*3+1]); zs.push_back(m.positions[v*3+2]);
                    }
                    std::sort(ys.begin(), ys.end()); std::sort(zs.begin(), zs.end());
                    dy = ys[ys.size()*9/10] - ys[ys.size()/10];
                    dz = zs[zs.size()*9/10] - zs[zs.size()/10];
                    bool dims = dx > 1.0f && dx < 2.2f && dz < 1.2f && dy < 1.2f && dz > 0.7f && dy > 0.7f;
                    float my = ys[ys.size()/2], mz = zs[zs.size()/2];
                    int circle = 0;
                    for (size_t v = 0; v < m.vertexCount(); v++) {
                        float a = m.positions[v*3+1] - my, b = m.positions[v*3+2] - mz;
                        if (fabsf(a*a + b*b - 0.25f) < 0.05f) circle++;
                    }
                    bool ok = dims && circle > 15 && m.triangleCount() > 10;
                    printf("T1 cylinder: name=%s verts=%zu tris=%zu circleHits=%d dims(%.2f,%.2f,%.2f) -> %s\n",
                           m.name.c_str(), m.vertexCount(), m.triangleCount(), circle, dx, dy, dz, ok?"PASS":"FAIL");
                    ok ? pass++ : fail++;
                }
            }
        }
    }

    // --- Test 2: character mesh (AK_Blcvtk_girl.mod — clean f16 layout)
    {
        snprintf(path, sizeof(path), "%s/ext/AK_Blcvtk_girl.mod", dir);
        FILE* f = fopen(path, "rb");
        std::vector<uint8_t> raw;
        if (f) {
            fseek(f, 0, SEEK_END); long sz = ftell(f); fseek(f, 0, SEEK_SET);
            raw.resize(sz); size_t rd = fread(raw.data(), 1, sz, f); fclose(f);
            (void)rd;
        }
        ModMeshData m;
        bool ok = ModMesh::parse(raw.data(), raw.size(), m)
                  && m.vertexCount() > 2000 && m.triangleCount() > 3000;
        // character bounds: max extent ~1.7-2.0
        Vec3 sz2 = m.boundsMax - m.boundsMin;
        bool shape = ok && (sz2.x > 1.2f && sz2.x < 2.4f || sz2.y > 1.2f && sz2.y < 2.4f || sz2.z > 1.2f && sz2.z < 2.4f);
        printf("T2 character: name=%s verts=%zu tris=%zu bounds(%.2f,%.2f,%.2f) -> %s\n",
               m.name.c_str(), m.vertexCount(), m.triangleCount(), sz2.x, sz2.y, sz2.z,
               shape ? "PASS" : "FAIL");
        shape ? pass++ : fail++;
    }

    // --- Test 3: KTX probe on real texture bytes (from the tmb)
    {
        BpcArchive t;
        bool ok = false;
        if (t.open("/home/z/ghrp-scratch/fmt/files.textures.Characters.astc.bpc.tmb")) {
            const BpcEntry* e = t.find("AK_LckLnz_M.btx");
            if (e) {
                std::vector<uint8_t> raw; t.read(*e, raw);
                int w, h; uint32_t fmt;
                ok = BtxTexture::probe(raw.data(), raw.size(), w, h, fmt) && w == 8 && h == 8 && fmt == 0x93B4;
            }
        }
        printf("T3 KTX probe: %s\n", ok ? "PASS (8x8 ASTC 4x4 detected)" : "FAIL");
        ok ? pass++ : fail++;
    }

    // --- Test 4: ANP3 parse
    {
        BpcArchive a;
        bool ok = false;
        snprintf(path, sizeof(path), "%s/files.mesh.br_anim.bpc", dir);
        if (a.open(path)) {
            const BpcEntry* e = a.find("Stand1.ani");
            if (e) {
                std::vector<uint8_t> raw; a.read(*e, raw);
                AniAnimation ani;
                ok = AniAnimationFile::parse(raw.data(), raw.size(), ani)
                     && ani.valid && ani.tracks.size() >= 15;
                if (ok)
                    printf("T4 ANP3: %s (%s) tracks=%zu stride=%lld\n",
                           ani.animName.c_str(), ani.trackName.c_str(), ani.tracks.size(), (long long)ani.trackStride);
            }
        }
        printf("T4 ANP3: %s\n", ok ? "PASS" : "FAIL");
        ok ? pass++ : fail++;
    }

    printf("\n=== %d PASS / %d FAIL ===\n", pass, fail);
    return fail ? 1 : 0;
}
