#!/usr/bin/env python3
"""Precision join: full two-way ACKs + verbose parse of every server datagram."""
import sys, time, struct, socket, random
sys.path.insert(0, '/home/z/ghrp-scratch/probe')
import full_connect as FC
from samp_full import BitStream, RELIABLE


def parse_verbose(bs, label):
    """Parse one datagram bit-by-bit with positions printed."""
    out = []
    try:
        has_acks = bs.read_bool()
        if has_acks:
            # ReadCompressed(u16) count
            ubz = bs.read_bool()
            if ubz:
                unz = bs.read_bool()
                if unz:
                    count = bs.read_bits(4)
                else:
                    count = bs.read_bits(8)
            else:
                count = bs.read_bits(16)
            ranges = []
            for _ in range(count):
                single = bs.read_bool()
                mn = bs.read_bits(16)
                if single:
                    ranges.append((mn, mn))
                else:
                    mx = bs.read_bits(16)
                    ranges.append((mn, mx))
            out.append(('ACK', ranges))
        # messages
        while bs.bits_left() >= 20:
            start = bs.rpos
            mn = bs.read_bits(16)
            rel = bs.read_bits(4)
            oc = oi = None
            if rel in (7, 9, 10):
                oc = bs.read_bits(5)
                oi = bs.read_bits(16)
            split = bs.read_bool()
            spid = spi = spc = None
            if split:
                spid = bs.read_bits(16)
                spi = bs.read_compressed_u32()
                spc = bs.read_compressed_u32()
            ubz = bs.read_bool()
            if ubz:
                unz = bs.read_bool()
                if unz:
                    nbits = bs.read_bits(4)
                else:
                    nbits = bs.read_bits(8)
            else:
                nbits = bs.read_bits(16)
            # byte-align
            while (bs.rpos & 7) != 0:
                bs.read_bits(1)
            nbytes = (nbits + 7) // 8
            if nbytes == 0 or bs.bits_left() < nbits - (8 - nbytes * 8 if nbytes else 0):
                break
            data = bytes(bs.read_bits(8) for _ in range(nbytes))
            out.append(('MSG', mn, rel, oc, oi, nbits, data))
            if not data:
                break
    except EOFError:
        pass
    return out


name = 'JoinFull_' + str(random.randint(100, 999))
gpci = format(random.getrandbits(100) * 1001, 'X')

c = FC.connect(name=name, send_join=False)
if not c:
    print("[-] connection failed"); sys.exit(1)
print(f"[+] accepted playerIndex={c.player_id} challenge=0x{c.challenge:08x}")

# NEW_INCOMING
myip = socket.inet_aton('127.0.0.1')
c.send_message(bytes([30]) + myip + struct.pack('<H', c.s.getsockname()[1]), reliability=RELIABLE)
time.sleep(0.5)

# ClientJoin (valid serial)
bs = BitStream()
bs.write_u32(4057); bs.write_u8(1); bs.write_str8(name)
bs.write_u32(c.challenge ^ 4057)
bs.write_str8(gpci); bs.write_str8('0.3.7-R2')
c.send_rpc(25, bs, reliability=RELIABLE, channel=0)
print(f"[*] ClientJoin sent (gpci ok)")

pending_acks = set()
t0 = time.time()
while time.time() - t0 < 20:
    c.s.settimeout(1.5)
    try:
        r, _ = c.s.recvfrom(65536)
    except socket.timeout:
        continue
    if not r:
        continue
    if r[0] == 0x22:
        continue
    bsr = BitStream(r)
    items = parse_verbose(bsr, f'{time.time()-t0:5.1f}s')
    for it in items:
        if it[0] == 'ACK':
            print(f"    [{time.time()-t0:5.1f}s] ACK ranges={it[1]}")
        else:
            _, mn, rel, oc, oi, nbits, data = it
            print(f"    [{time.time()-t0:5.1f}s] MSG#{mn} rel={rel} oc={oc} oi={oi} bits={nbits} data={data.hex()}")
            if rel in (8, 9, 10):
                pending_acks.add(mn)
            if data and data[0] == 0x06 and len(data) >= 5:
                # PING -> PONG
                c.send_message(bytes([9]) + data[1:5] + struct.pack('<I', int(time.time() * 1000) & 0xFFFFFFFF), reliability=RELIABLE)
                print(f"        -> ponged")
    # ACK everything reliable we've seen
    if pending_acks:
        c.send_ack(sorted(pending_acks))
        print(f"    -> ACKed {sorted(pending_acks)}")
        pending_acks.clear()
