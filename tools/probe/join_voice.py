#!/usr/bin/env python3
"""Game join + sampvoice port identification probe.
sampvoice disconnects unidentified players; we probe the voice port
from the SAME socket (same source port = identification key)."""
import sys, time, struct, socket, random
sys.path.insert(0, '/home/z/ghrp-scratch/probe')
import full_connect as FC
from samp_full import BitStream, RELIABLE
from join_full import parse_verbose

name = 'VoiceProbe_' + str(random.randint(100, 999))
gpci = format(random.getrandbits(100) * 1001, 'X')
c = FC.connect(name=name, send_join=False)
if not c:
    print("[-] connection failed"); sys.exit(1)
print(f"[+] accepted playerIndex={c.player_id}")

myip = socket.inet_aton('127.0.0.1')
myport = c.s.getsockname()[1]
c.send_message(bytes([30]) + myip + struct.pack('<H', myport), reliability=RELIABLE)
c.send_message(bytes([0x29]), reliability=RELIABLE)

bs = BitStream()
bs.write_u32(4057); bs.write_u8(1); bs.write_str8(name)
bs.write_u32(c.challenge ^ 4057)
bs.write_str8(gpci); bs.write_str8('0.3.7-R2')
c.send_rpc(25, bs, reliability=RELIABLE, channel=0)
print(f"[*] ClientJoin sent (port {myport})")

# --- sampvoice port probes from the SAME socket ---
VOICE_PORT = 36576
probes = [
    ('empty', b''),
    ('SV0', b'SV'),
    ('zero', b'\x00'),
    ('one', b'\x01'),
    ('port16', struct.pack('<H', myport)),
]
for tag, payload in probes:
    c.s.sendto(payload, ('127.0.0.1', VOICE_PORT))
    print(f"[*] voice probe '{tag}' ({payload.hex()}) sent to :{VOICE_PORT}")
    time.sleep(0.4)

pending = set()
t0 = time.time()
rpcs = []
while time.time() - t0 < 20:
    c.s.settimeout(1.0)
    try:
        r, addr = c.s.recvfrom(65536)
    except socket.timeout:
        continue
    src = addr[1]
    if not r:
        continue
    if src == VOICE_PORT:
        print(f"    [{time.time()-t0:5.1f}s] VOICE reply from :{src}: {r[:32].hex()}")
        continue
    if r[0] == 0x22:
        continue
    bsr = BitStream(r)
    for it in parse_verbose(bsr, ''):
        if it[0] == 'ACK':
            continue
        _, mn, rel, oc, oi, nbits, data = it
        print(f"    [{time.time()-t0:5.1f}s] MSG#{mn} rel={rel} data={data[:32].hex()}")
        if rel in (8, 9, 10):
            pending.add(mn)
        if data and data[0] == 0x06 and len(data) >= 5:
            c.send_message(bytes([9]) + data[1:5] + struct.pack('<I', int(time.time() * 1000) & 0xFFFFFFFF), reliability=RELIABLE)
        if data and data[0] == 0x14 and len(data) > 1:  # ID_RPC = 20!
            try:
                rbs = BitStream(data[1:])
                rpc_id = rbs.read_u8()
                blen = rbs.read_compressed_u32()
                rpcs.append(rpc_id)
                print(f"        *** RPC {rpc_id} ({blen} bits) ***")
            except EOFError:
                pass
    if pending:
        c.send_ack(sorted(pending))
        pending.clear()

print(f"[*] RPCs received: {rpcs}")
