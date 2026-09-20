#!/usr/bin/env python3
"""GHRP full SA-MP 0.3.7-R2 connection: transport -> auth -> accepted -> join."""
import socket, struct, os, sys, time, json
sys.path.insert(0, '/home/z/ghrp-scratch/probe')
from samp_full import SAMPConnection, read_cookie_key, RELIABLE, ID_CONNECTION_REQUEST, ID_CONNECTION_REQUEST_ACCEPTED, ID_AUTH_KEY
from samp_full import BitStream

AUTH = json.load(open('/home/z/ghrp-scratch/probe/auth_table_r2.json'))

def wc16(bs, v):
    b = struct.pack('<H', v)
    if b[1] == 0:
        bs.write_bits(1, 1)
        if (b[0] & 0xF0) == 0: bs.write_bits(1, 1); bs.write_bits(b[0] & 0x0F, 4)
        else: bs.write_bits(0, 1); bs.write_bits(b[0], 8)
    else:
        bs.write_bits(0, 1); bs.write_bits(b[0], 8); bs.write_bits(b[1], 8)

def frame_aligned(self, data, reliability=RELIABLE, ordering_channel=0):
    bs = BitStream()
    bs.write_bool(False)
    bs.write_u16(self.msg_num); self.msg_num = (self.msg_num + 1) & 0xFFFF
    bs.write_bits(reliability, 4)
    if reliability in (7, 9, 10): bs.write_bits(ordering_channel, 5); bs.write_u16(0)
    bs.write_bool(False)
    wc16(bs, len(data) * 8)
    while (bs.wpos % 8) != 0: bs.write_bits(0, 1)
    bs.write_bytes(data)
    return bytes(bs.data)
SAMPConnection.frame_message = frame_aligned

def send_ack_fixed(self, msg_nums):
    bs = BitStream()
    bs.write_bool(True)
    ranges = []
    for mn in sorted(set(msg_nums)):
        if ranges and mn == ranges[-1][1] + 1: ranges[-1][1] = mn
        else: ranges.append([mn, mn])
    wc16(bs, len(ranges))
    for lo, hi in ranges:
        bs.write_bool(lo == hi); bs.write_u16(lo)
        if lo != hi: bs.write_u16(hi)
    self.send_raw(bytes(bs.data))
SAMPConnection.send_ack = send_ack_fixed

def parse_fixed(self, r):
    bs = BitStream(r)
    msgs = []
    if bs.read_bool():
        if bs.read_bool():
            count = bs.read_bits(4) if bs.read_bool() else bs.read_u8()
        else:
            count = bs.read_u16()
        for _ in range(count):
            single = bs.read_bool(); mn = bs.read_u16()
            if not single: mx = bs.read_u16()
    while bs.bits_left() >= 27:
        try:
            mn = bs.read_u16()
            rel = bs.read_bits(4)
            if rel in (7, 9, 10): oc = bs.read_bits(5); oi = bs.read_u16()
            is_split = bs.read_bool()
            if is_split:
                spid = bs.read_u16(); spi = bs.read_compressed_u32(); spc = bs.read_compressed_u32()
            if bs.read_bool():
                nbits = bs.read_bits(4) if bs.read_bool() else bs.read_u8()
            else:
                nbits = bs.read_u16()
            nbytes = (nbits + 7) // 8
            data = bytes(bs.read_bits(8) for _ in range(nbytes))
            msgs.append((data[0], data[1:], mn, rel, nbits))
        except EOFError: break
    return msgs
SAMPConnection.parse_datagram = parse_fixed


def connect(name='GHRPProbe', settle=0.5, timeout=30, send_join=True):
    key = read_cookie_key()
    c = SAMPConnection('127.0.0.1', 14448, key)
    c.handshake()
    time.sleep(settle)
    c.send_message(bytes([ID_CONNECTION_REQUEST]), reliability=RELIABLE)
    stage = 'auth'
    accepted = None
    deadline = time.time() + timeout
    while time.time() < deadline and not accepted:
        c.s.settimeout(1.5)
        try:
            r, _ = c.s.recvfrom(65536)
        except socket.timeout:
            continue
        if r[0] == 0x22 and len(r) >= 13:
            accepted = r; break
        try:
            for (mt, payload, mn, rel, nbits) in parse_fixed(c, r):
                if rel in (8,9,10): c.send_ack([mn])
                if mt == 0x0C and len(payload) >= 2 and stage == 'auth':
                    ln = payload[0]
                    ch = payload[1:1+ln].split(b'\0')[0].decode('ascii','replace')
                    if ch in AUTH:
                        print(f"[*] AUTH challenge: {ch}")
                        resp = AUTH[ch]
                        c.send_message(bytes([ID_AUTH_KEY, len(resp)]) + resp.encode(), reliability=RELIABLE)
                        stage = 'wait'
                    else:
                        print(f"[!] unknown challenge {ch}")
                elif mt == ID_CONNECTION_REQUEST_ACCEPTED:
                    accepted = payload; break
        except Exception:
            idx = r.find(b'\x0c')
            if idx >= 0 and stage == 'auth' and idx+1 < len(r):
                ln = r[idx+1]
                ch = r[idx+2:idx+2+ln].split(b'\0')[0].decode('ascii','replace')
                if ch in AUTH:
                    resp = AUTH[ch]
                    c.send_message(bytes([ID_AUTH_KEY, len(resp)]) + resp.encode(), reliability=RELIABLE)
                    stage = 'wait'
    if not accepted:
        return None
    if isinstance(accepted, bytes) and accepted[0] == 0x22:
        addr, port16, pid16, ch32 = struct.unpack_from('<IHHI', accepted, 1)
    else:
        addr, port16, pid16, ch32 = struct.unpack_from('<IHHI', accepted, 0)
    print(f"[+] CONNECTION ACCEPTED! playerIndex={pid16} challenge=0x{ch32:08x}")
    c.player_id = pid16; c.challenge = ch32
    myip = socket.inet_aton('127.0.0.1')
    c.send_message(bytes([30]) + myip + struct.pack('<H', c.s.getsockname()[1]), reliability=RELIABLE)
    print("[*] NEW_INCOMING sent")
    if not send_join:
        return c
    bs = BitStream()
    bs.write_u32(4057); bs.write_u8(1); bs.write_str8(name)
    bs.write_u32(ch32 ^ 4057)
    bs.write_str8('B1CD2E34F5A6B7C8D9E0F1A2B3C4D5E6'); bs.write_str8('0.3.7-R2')
    c.send_rpc(25, bs)
    print("[*] RPC 25 ClientJoin sent")
    return c


if __name__ == '__main__':
    c = connect()
    if c:
        print("[*] GAME FLOW:")
        deadline = time.time() + 30
        while time.time() < deadline:
            c.s.settimeout(2)
            try: r, _ = c.s.recvfrom(65536)
            except socket.timeout: continue
            try:
                for (mt, payload, mn, rel, nbits) in parse_fixed(c, r):
                    if rel in (8,9,10): c.send_ack([mn])
                    if mt == 21:  # ID_RPC
                        rbs = BitStream(payload)
                        try:
                            rpc_id = rbs.read_u8(); blen = rbs.read_compressed_u32()
                            print(f"    <- RPC {rpc_id} ({blen} bits)")
                            if rpc_id == 139: print("    [+] RPC 139 InitGame!")
                            if rpc_id == 61: print("    [+] RPC 61 ShowDialog — LOGIN FLOW!")
                        except EOFError: pass
                    elif mt == 6:
                        c.send_message(bytes([9]) + payload[:4] + struct.pack('<I', int(time.time()*1000)&0xFFFFFFFF), reliability=RELIABLE)
            except Exception: pass
    else:
        print("[-] connection failed")
