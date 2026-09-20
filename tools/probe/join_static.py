#!/usr/bin/env python3
"""Join with STATIC DATA exchange: [0x29] after accept, then ClientJoin."""
import sys, time, struct, socket, random
sys.path.insert(0, '/home/z/ghrp-scratch/probe')
import full_connect as FC
from samp_full import BitStream, RELIABLE
from join_full import parse_verbose

name = 'StaticJoin_' + str(random.randint(100, 999))
gpci = format(random.getrandbits(100) * 1001, 'X')

c = FC.connect(name=name, send_join=False)
if not c:
    print("[-] connection failed"); sys.exit(1)
print(f"[+] accepted playerIndex={c.player_id}")

# 1. NEW_INCOMING
myip = socket.inet_aton('127.0.0.1')
c.send_message(bytes([30]) + myip + struct.pack('<H', c.s.getsockname()[1]), reliability=RELIABLE)

# 2. Our static data (empty, like RakSAMP) — [ID_RECEIVED_STATIC_DATA=41]
c.send_message(bytes([0x29]), reliability=RELIABLE)
print("[*] static data [0x29] sent")

time.sleep(0.5)

# 3. ClientJoin with valid serial
bs = BitStream()
bs.write_u32(4057); bs.write_u8(1); bs.write_str8(name)
bs.write_u32(c.challenge ^ 4057)
bs.write_str8(gpci); bs.write_str8('0.3.7-R2')
c.send_rpc(25, bs, reliability=RELIABLE, channel=0)
print(f"[*] ClientJoin sent (gpci={gpci[:10]}..)")

pending = set()
t0 = time.time()
rpcs = []
while time.time() - t0 < 25:
    c.s.settimeout(1.5)
    try:
        r, _ = c.s.recvfrom(65536)
    except socket.timeout:
        continue
    if not r or r[0] == 0x22:
        continue
    bsr = BitStream(r)
    for it in parse_verbose(bsr, ''):
        if it[0] == 'ACK':
            print(f"    [{time.time()-t0:5.1f}s] ACK {it[1]}")
        else:
            _, mn, rel, oc, oi, nbits, data = it
            print(f"    [{time.time()-t0:5.1f}s] MSG#{mn} rel={rel} bits={nbits} data={data[:32].hex()}")
            if rel in (8, 9, 10):
                pending.add(mn)
            if data and data[0] == 0x06 and len(data) >= 5:
                c.send_message(bytes([9]) + data[1:5] + struct.pack('<I', int(time.time() * 1000) & 0xFFFFFFFF), reliability=RELIABLE)
            if data and data[0] == 0x15 and len(data) > 1:  # ID_RPC
                try:
                    rbs = BitStream(data[1:])
                    rpc_id = rbs.read_u8()
                    blen = rbs.read_compressed_u32()
                    rpcs.append(rpc_id)
                    print(f"        RPC {rpc_id} ({blen} bits) <<<")
                except EOFError:
                    pass
    if pending:
        c.send_ack(sorted(pending))
        pending.clear()

print(f"[*] RPCs received: {rpcs}")
