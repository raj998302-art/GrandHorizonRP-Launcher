# SA-MP 0.3.7-R2 Protocol — Session Notes (PROGRESS)

## CRITICAL FIX (this session): ID_RPC = 20, NOT 21!

The RakNet 3.x enum order (verified against both RakSAMP and RakNet 3.x references):
```
0-5   ID_UNKNOWN_0..5
6     ID_INTERNAL_PING         (verified: server pings [06][u32 uptime-ms])
9     ID_CONNECTED_PONG
10    ID_REQUEST_STATIC_DATA
11    ID_CONNECTION_REQUEST    (verified: ACCEPTED follows)
12    ID_AUTH_KEY              (verified: table challenge/response)
15    ID_BROADCAST_PINGS
18    ID_RPC_MAPPING
19    ID_SET_RANDOM_NUMBER_SEED
20    ID_RPC                   ← !!! (0x14)
21    ID_RPC_REPLY
23    ID_DETECT_LOST_CONNECTIONS (server sends [17] reliably, needs ACK)
24    ID_OPEN_CONNECTION_REQUEST (verified transport handshake)
26    ID_OPEN_CONNECTION_COOKIE  (verified)
30    ID_NEW_INCOMING_CONNECTION (verified)
32    ID_DISCONNECTION_NOTIFICATION
34    ID_CONNECTION_REQUEST_ACCEPTED (0x22, verified)
41    ID_RECEIVED_STATIC_DATA  (0x29 — auto static-data exchange, verified)
```

**THE ROOT-CAUSE STORY:** the previous session's probes sent the RPC envelope with
type byte 21 (ID_RPC_REPLY!) — the server's dispatcher silently dropped every
"RPC" (it was a reply to an RPC that was never sent). That is why ClientJoin was
RakNet-ACKed but never dispatched. With type 20 the RPC layer dispatches
immediately: the server now processes ClientJoin, the gamemode-level logs appear
("[sv:dbg:network:connect] : disconnecting player (0) ..."), and the server
answers with **RPC 130 (connection denied, reason byte) + DISCONNECT (32)**.

## ClientJoin (RPC 25) payload (RakSAMP-verified)
```
u32  version          = 4057 (NETGAME_VERSION 0.3.7)
u8   modded           = 1
str8 name
u32  challengeResponse = challenge_from_ACCEPTED ^ 4057
str8 gpci (serial)    — MUST satisfy: hex_number % 1001 == 0  (open.mp: serial
                        check; invalid serial → "Invalid client" + ban 15s)
str8 versionString    = "0.3.7-R2"
```
Envelope: `[ID_RPC=20][rpcId u8][compressed-u32 bitLength][payload bytes]`,
sent RELIABLE (8), channel 0, byte-aligned payload.

## Live-memory verified facts (qemu server, /proc/pid/mem)
- `[0x81ca608]` = global rakpeer pointer (e.g. 0x4605a0)
- remoteSystemList pointer at **rakpeer+0x338** (NOT +0x334), stride 0xc69
- entry+0x00 isActive, +0x01 playerId (LE u32 ip + u16 port), +0xc62 connectMode
  (8 = CONNECTED), +0xc66 authIndex, +0xc67 authType (**1 = AuthType_Player —
  our AUTH_KEY table exchange sets it correctly!**)
- Server post-ACCEPTED sequence: ping [06][ts] (pong [09][ts][u32 local]) +
  static data [0x29] + keepalive [0x17] (both RELIABLE — must be ACKed)
- ACK datagram format (S->C, plain): [hasAcks bit][ReadCompressed(u16) count]
  [per range: single bit + u16 min (+ u16 max)] — MSB-first bit order
  (the whole wire format is MSB-first; e3 00 00 = single ACK for msg#0)

## THE REMAINING BLOCKER: sampvoice.so plugin
- The deployed gamemode loads `sampvoice.so` (voice chat, "SampVoice by MOR")
- It binds its own **voice server UDP port 36576** (see server log
  "[sv:dbg:network:bind] : voice server running on port 36576")
- On player connect it waits for the client to **identify on the voice port**
  ("[sv:dbg:network:receive] : player (%hu) identified (port:%hu)")
- Unidentified players are DISCONNECTED from the game:
  "[sv:dbg:network:connect] : disconnecting player (0) ..." + RPC 130 deny
- Probing the voice port from the same game socket with empty/SV/\x00/\x01/
  port16 packets did NOT trigger identification — the exact sampvoice client
  packet format is needed (next: disassemble sampvoice.so receive handler —
  PIC code, string at VA 0x13bb17 — or fetch the public SampVoice client source)
- PRODUCTION NOTE: the real BR client speaks sampvoice; our engine's network
  stack must implement the same identification to join the real server. This is
  a CLIENT-side obligation, NOT a server incompatibility — no server change
  is required (gameplay preserved).

## Files (all in /home/z/ghrp-scratch/probe/)
- samp_full.py       — codec + framing (ID_RPC now = 20)
- full_connect.py    — full RakNet connect (connect(send_join=...))
- join_full.py       — precise parser + two-way ACKs
- join_static.py     — static-data exchange + join
- join_voice.py      — voice-port identification probes
- auth_table_r2.json — 256-entry R2 auth table

## sampvoice packet format (first decode)
- Voice packet: 0x18-byte header + payload; total size = 0x18 + u16@header+0x12
- ReceiveVoicePacket (sampvoice.so @0x75050): size must be > 0x17, then
  CheckHeader() then GetFullSize() == recvfrom size
- CheckHeader (@0x8fd20): CRC-32C-style checksum (poly 0x82F63B78, init 0xFFFFFFFF,
  bit-by-bit reflected) over header bytes [4..0x18); compares against stored value
  (field at packet+0). Identification ("player (%hu) identified (port:%hu)")
  follows a valid control packet — exact control-packet type constants = next step.
