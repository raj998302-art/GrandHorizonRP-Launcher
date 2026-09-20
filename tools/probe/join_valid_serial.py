#!/usr/bin/env python3
"""Full game join with VALID gpci (serial % 1001 == 0) — the RPC 25 blocker fix."""
import sys, time, struct, socket, random
sys.path.insert(0, '/home/z/ghrp-scratch/probe')
import full_connect as FC
from samp_full import BitStream, RELIABLE


def valid_gpci():
    n = random.getrandbits(100) * 1001
    return format(n, 'X')


name = sys.argv[1] if len(sys.argv) > 1 else 'GHRPProbe'
gpci = valid_gpci()
print(f"[*] gpci: {gpci} (%1001 == 0)")

c = FC.connect(name=name, send_join=False)
if not c:
    print("[-] connection failed"); sys.exit(1)

bs = BitStream()
bs.write_u32(4057); bs.write_u8(1); bs.write_str8(name)
bs.write_u32(c.challenge ^ 4057)
bs.write_str8(gpci); bs.write_str8('0.3.7-R2')

c.send_rpc(25, bs, reliability=RELIABLE, channel=0)
print("[*] RPC 25 ClientJoin sent (RELIABLE, valid serial)")

deadline = time.time() + 30
got = []
while time.time() < deadline:
    c.s.settimeout(2)
    try:
        r, _ = c.s.recvfrom(65536)
    except socket.timeout:
        continue
    if not r or r[0] == 0x22:
        continue
    try:
        msgs = FC.parse_fixed(c, r)
        for (mt, payload, mn, rel, nbits) in msgs:
            if rel in (8, 9, 10): c.send_ack([mn])
            if mt == 21:
                rbs = BitStream(payload)
                try:
                    rpc_id = rbs.read_u8(); blen = rbs.read_compressed_u32()
                    print(f"    <- RPC {rpc_id} ({blen} bits)")
                    got.append(rpc_id)
                    if rpc_id == 139: print("    [+] *** RPC 139 InitGame ***")
                    if rpc_id == 61: print("    [+] *** RPC 61 ShowDialog (LOGIN FLOW!) ***")
                except EOFError: pass
            elif mt == 6:
                c.send_message(bytes([9]) + payload[:4] + struct.pack('<I', int(time.time() * 1000) & 0xFFFFFFFF), reliability=RELIABLE)
            elif mt in (32, 33, 34):
                print(f"    <- DISCONNECT type {mt}")
    except Exception:
        pass

print(f"[*] done. RPCs received: {got}")
