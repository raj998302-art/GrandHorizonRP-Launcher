# REFERENCE RENDERING CONTRACT — old implementation behaviour → required data → GHRP engine

> Derived from game-data format reversing (verified on real data, worklog 22-CLOSE)
> and the original client's observed behaviour. Reference only; our engine
> implements the same contracts independently.

## 1. Asset chain (CONFIRMED FROM GAME DATA)

```
.bpc (ZIP/DEFLATE container)
  -> .mod (28B header: magic 0xAB921033, dsize, block-count)
       body: first cnt x 2048B blocks XTEA-8 encrypted
             key derived from engine constant (XOR 0x12913AFB, ROR 19)
       static meshes: f32 SoA streams
       character meshes: f16 positions + u16 edge buffer
                          (faces from shared-vertex edges; girl: 8764 v / 15733 t)
  -> .btx (4B prefix + KTX1 + ASTC; real footprint 6x6, tag lies 4x4)
  -> .ani (ANP3: Biped 26 bones, 936B track stride)
  -> .cls (CLST + bounds)
```

## 2. Vertex → GPU contract

| Requirement | Original behaviour | Required data | GHRP implementation |
|---|---|---|---|
| Vertex format | pos+normal+uv streams | SoA f32 / f16+u16 | interleaved 32B (pos3f,nrm3f,uv2f) — done |
| Index format | edge-buffer reconstruction | u16 indices | u16, threshold fix (10K) — done |
| Skinning | bone indices/weights in .mod character blocks | per-vertex bone refs | parsed; GPU skinning = M2 |
| Textures | KTX1 ASTC 6x6 + mips | block footprint math | done (data-driven footprint) |
| Materials | submesh tables + names | per-submesh tex refs | basic binding — done |
| Camera | orbit preview (char select) | bounds framing | done (bounds-centred orbit) |
| Lighting | simple directional + tint | shader params | done (N·L + fog hooks) |

## 3. Character-selection rendering pipeline (original behaviour → ours)

```
original: character id -> gender -> skin id -> model path -> BPC -> MOD ->
  decrypt -> mesh -> materials -> BTX -> GL texture -> skeleton (.ani) ->
  idle anim -> renderer -> orbit camera -> screen
ours (native, GHEngine): skin index -> AssetLibrary (br_skins_01.bpc +
  Characters.astc.bpc) -> ModMesh parse (both paths) -> Gfx::uploadMesh ->
  BtxTexture -> draw with mesh shader -> orbit camera -> JNI events
  (onSceneReady {skin,tris,verts,tex}) -> Java diagnostics strip
```

## 4. Device-side hardening added this session

- Cull face **disabled** for the preview (reconstructed winding not guaranteed
  per-triangle; wrong winding hid meshes on some drivers) — world scenes will
  re-enable once their meshes are verified.
- ASTC capability is synced **after** EGL init (ordering bug: renderer starts
  after setDataRoot; first texture load previously could use a stale flag) and
  the skin reloads once with the correct capability.
- Skin load fallback: if a mesh fails to parse/upload the engine advances to
  the next skin (catalogue-wide) instead of showing an empty scene.
- Diagnostics: `onEngineInfo {gl, astc, skins, abi}` + `onSceneReady
  {skin,tris,verts,tex}` events surface on-screen — the "triangle placeholder"
  report can now be pinpointed from the device itself (GL version, skin count,
  mesh stats, texture status).

## 5. STILL UNKNOWN / M2+

- GPU skinning matrix layout from .ani tracks (CPU pose math done at parse
  level; shader skinning pending)
- world/streaming layout (br_map_*.bpc sector structure) — format is the same
  .bpc/.mod contract, streaming architecture pending
- exact material flags/blend modes per submesh class
- remote-player rendering contract — depends on the SA-MP protocol milestone
  (RPC 139 InitGame skin/position fields are already documented in worklog 23-B)
