#!/usr/bin/env python3
"""Keep a connection open while we inspect remoteSystem+0xc67 (authType)."""
import sys, time, struct, socket, random, threading
sys.path.insert(0, '/home/z/ghrp-scratch/probe')
import full_connect as FC
from samp_full import BitStream, RELIABLE
from join_full import parse_verbose

name = 'AuthProbe_' + str(random.randint(100, 999))
gpci = format(random.getrandbits(100) * 1001, 'X')
c = FC.connect(name=name, send_join=False)
if not c:
    print("[-] connection failed"); sys.exit(1)
print(f"[+] accepted playerIndex={c.player_id}")

myip = socket.inet_aton('127.0.0.1')
c.send_message(bytes([30]) + myip + struct.pack('<H', c.s.getsockname()[1]), reliability=RELIABLE)
c.send_message(bytes([0x29]), reliability=RELIABLE)
bs = BitStream()
bs.write_u32(4057); bs.write_u8(1); bs.write_str8(name)
bs.write_u32(c.challenge ^ 4057)
bs.write_str8(gpci); bs.write_str8('0.3.7-R2')
c.send_rpc(25, bs, reliability=RELIABLE, channel=0)
print("[*] join sent — holding connection open 60s")

# marker file with our port so the memory reader can find us
open('/tmp/authprobe_port', 'w').write(str(c.s.getsockname()[1]))

pending = set()
stop = time.time() + 60
while time.time() < stop:
    c.s.settimeout(1.0)
    try:
        r, _ = c.s.recvfrom(65536)
    except socket.timeout:
        continue
    if not r or r[0] == 0x22:
        continue
    bsr = BitStream(r)
    for it in parse_verbose(bsr, ''):
        if it[0] == 'ACK':
            continue
        _, mn, rel, oc, oi, nbits, data = it
        print(f"    MSG#{mn} rel={rel} data={data[:24].hex()}")
        if rel in (8, 9, 10):
            pending.add(mn)
        if data and data[0] == 0x06 and len(data) >= 5:
            c.send_message(bytes([9]) + data[1:5] + struct.pack('<I', int(time.time() * 1000) & 0xFFFFFFFF), reliability=RELIABLE)
    if pending:
        c.send_ack(sorted(pending))
        pending.clear()
print("[*] done")
