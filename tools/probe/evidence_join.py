#!/usr/bin/env python3
"""Precise evidence: which datagrams does the server send after our ClientJoin?"""
import sys, time, struct, socket, random
sys.path.insert(0, '/home/z/ghrp-scratch/probe')
import full_connect as FC
from samp_full import BitStream, RELIABLE

name = 'Evidence_' + str(random.randint(100, 999))
gpci_n = random.getrandbits(100) * 1001
gpci = format(gpci_n, 'X')

c = FC.connect(name=name, send_join=False)
if not c:
    print("[-] connection failed"); sys.exit(1)
print(f"[+] accepted. playerIndex={c.player_id} challenge=0x{c.challenge:08x}")

# send NEW_INCOMING first (like before)
myip = socket.inet_aton('127.0.0.1')
c.send_message(bytes([30]) + myip + struct.pack('<H', c.s.getsockname()[1]), reliability=RELIABLE)
print("[*] NEW_INCOMING sent (msg#%d)" % (c.msg_num - 1))
time.sleep(0.7)

# now ClientJoin with valid serial
bs = BitStream()
bs.write_u32(4057); bs.write_u8(1); bs.write_str8(name)
bs.write_u32(c.challenge ^ 4057)
bs.write_str8(gpci); bs.write_str8('0.3.7-R2')
join_msg = c.msg_num
c.send_rpc(25, bs, reliability=RELIABLE, channel=0)
print(f"[*] ClientJoin sent (msg#{join_msg}, gpci={gpci[:12]}.., %1001=0)")

t0 = time.time()
while time.time() - t0 < 14:
    c.s.settimeout(1.5)
    try:
        r, _ = c.s.recvfrom(65536)
    except socket.timeout:
        print(f"    [{time.time()-t0:5.1f}s] (timeout)")
        continue
    tag = 'PLAIN' if r[0] in (0x22,) else 'enc?'
    print(f"    [{time.time()-t0:5.1f}s] {len(r):3d}B {tag}: {r[:28].hex()}{'...' if len(r) > 28 else ''}")
    # auto-pong
    if r and r[0] == 0x06 and len(r) >= 5:
        c.send_message(bytes([9]) + r[1:5] + struct.pack('<I', int(time.time() * 1000) & 0xFFFFFFFF), reliability=RELIABLE)
