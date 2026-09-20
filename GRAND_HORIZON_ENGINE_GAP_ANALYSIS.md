# GRAND HORIZON ENGINE — GAP ANALYSIS (honest)

Legend: DONE (implemented+verified) / PARTIAL (works, scope-limited) /
M2+ (planned milestone) / — (not started)

| Subsystem | Original | GHRP status | Missing | Priority |
|---|---|---|---|---|
| Android lifecycle | JNIActivity + fragments | DONE (single-activity, engine view + overlay) | — | — |
| Game-data updater | native patcher + fragment | DONE (pure-Java, size+TLS verified, resume) | — | — |
| Auth WebView (0x58 contract) | WebViewAuthFragment + WebAppInterface | DONE (AuthController + ghrp-auth; bridge `Android.initToken`) | — | — |
| Session/guest persistence | token saved engine-side | DONE this session (SessionStore + guest_secret re-auth + server character API) | real-device confirmation | P0 ✅ |
| Character persistence | account service | DONE this session (accounts.sex/skin + marker column, server-authoritative) | — | P0 ✅ |
| WebView UI quality | polished SSO site | DONE this session (glass UI, SVG icons, motion, bg art) | — | P0 ✅ |
| BPC loader | native | DONE (zlib, verified) | — | — |
| MOD loader + decrypt | native | DONE (XTEA-8, dual f32/f16 paths, host-verified) | — | — |
| BTX/ASTC loader | native | DONE (KTX1, data-driven 6x6) | — | — |
| Skeleton (.ani) | native | PARTIAL (ANP3 parse, 26 bones, tracks) | playback+blend | P1 |
| Animation (GPU skinning) | native | M2+ | skin shader, pose eval | P1 |
| Character renderer | native | DONE (native 3D preview, orbit, textures) + hardened this session (cull-off, fallback, ASTC sync) | skinning | P1 |
| Camera | native | PARTIAL (orbit; world/gameplay cam = M2) | third-person cam | P2 |
| Lighting/materials | native | PARTIAL (N·L + tint + fog hooks) | material flags | P2 |
| Native UI runtime (gui.bpc) | native GUI | — | 978 JSON screens runtime | P3 |
| World renderer + streaming | native | M2+ | sectors, culling, LOD | P2 |
| Collision | native (.cls) | PARTIAL (.cls parse) | runtime collision | P2 |
| Networking (SA-MP client) | ENet/RakNet | PARTIAL — protocol fully cracked + Python probe achieves full RakNet connection (worklog 23-B); C++ port pending | RPC 25 envelope detail, C++ NetworkManager | P1 |
| Multiplayer rendering | native | M2+ | entity manager, interp | P2 |
| Vehicles | native | M2+ | — | P3 |
| HUD | native | M2+ | — | P2 |
| Audio | native | M2+ | .bpc audio containers already downloadable | P3 |
| Input (touch) | native | DONE for preview (orbit/zoom); gameplay controls M2 | gameplay touch | P2 |
| Spawn flow | native | M2+ (server dialog flow documented in laird.pwn) | — | P2 |
| Error handling | native | DONE-ish (load errors surfaced to UI + logs + diagnostics strip) | — | — |
| Resource mgmt | native | PARTIAL (on-demand parse, single-skin residency) | streaming cache | P2 |
| CI/provenance | n/a | DONE (source build, host verify 4/4, gates, release) | — | — |

## Highest-priority next steps

1. **C++ NetworkManager** in libghengine.so — port the proven probe
   (SBOX codec, cookie handshake, reliability framing w/ alignment, AUTH_KEY
   table, RPC envelopes). One known detail: RPC 25 ClientJoin envelope
   (RakNet-ACKed but game layer didn't dispatch; disassemble samp03svr
   0x8067b60–0x8067e50).
2. RPC 139 InitGame -> dialog login flow (SHA256_PassHash contract already
   matches ghrp-auth).
3. World streaming (br_map_*.bpc) + ANP3 playback + GPU skinning.
4. Remote player rendering (server sync -> entity -> renderer).
5. Real-device A→Z test of this session's build (guest persistence + character
   preview diagnostics will report live evidence).
