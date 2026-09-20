#!/usr/bin/env python3
"""Test RPC 25 ClientJoin with RELIABLE(8) reliability (RakSAMP-exact envelope)."""
import sys, time, struct, socket
sys.path.insert(0, '/home/z/ghrp-scratch/probe')
import full_connect as FC
from samp_full import BitStream, RELIABLE

name = sys.argv[1] if len(sys.argv) > 1 else 'GHRPProbe'
c = FC.connect(name=name)
if not c:
    print("[-] connection failed"); sys.exit(1)

bs = BitStream()
bs.write_u32(4057); bs.write_u8(1); bs.write_str8(name)
bs.write_u32(c.challenge ^ 4057)
bs.write_str8('B1CD2E34F5A6B7C8D9E0F1A2B3C4D5E6'); bs.write_str8('0.3.7-R2')

# RakSAMP-exact: HIGH_PRIORITY + RELIABLE (8), channel 0, no timestamp
c.send_rpc(25, bs, reliability=RELIABLE, channel=0)
print("[*] RPC 25 ClientJoin sent with RELIABLE(8)")

deadline = time.time() + 25
got = []
while time.time() < deadline:
    c.s.settimeout(2)
    try:
        r, _ = c.s.recvfrom(65536)
    except socket.timeout:
        continue
    if not r: continue
    # plain datagrams
    if r[0] == 0x22 and len(r) >= 13:
        continue
    try:
        msgs = FC.parse_fixed(c, r)
        for (mt, payload, mn, rel, nbits) in msgs:
            if rel in (8, 9, 10): c.send_ack([mn])
            if mt == 21:
                rbs = BitStream(payload)
                try:
                    rpc_id = rbs.read_u8(); blen = rbs.read_compressed_u32()
                    print(f"    <- RPC {rpc_id} ({blen} bits) [msg#{mn}]")
                    got.append(rpc_id)
                    if rpc_id == 139: print("    [+] *** RPC 139 InitGame ***")
                    if rpc_id == 61: print("    [+] *** RPC 61 ShowDialog (login flow!) ***")
                except EOFError: pass
            elif mt == 6:
                c.send_message(bytes([9]) + payload[:4] + struct.pack('<I', int(time.time() * 1000) & 0xFFFFFFFF), reliability=RELIABLE)
            elif mt == 32:
                print(f"    <- DISCONNECT msg#{mn}")
            else:
                print(f"    <- msg type {mt} len={len(payload)} [msg#{mn}]")
    except Exception as e:
        pass

print(f"[*] RPCs received: {got}")
