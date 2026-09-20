#!/usr/bin/env python3
"""
GHRP SA-MP 0.3.7-R2 protocol client (from-scratch, proven against real samp03svr binary).
Protocol sources: samp03svr disassembly + runtime probing (obfuscation/cookie),
RakSAMP 0.3.7-R2 public reference (reliability/RPC layout), open.mp NetCode (packet structures).
"""
import socket, struct, os, time, sys

SBOX = open('/home/z/ghrp-scratch/server-test/sbox.bin', 'rb').read()
INV = [0] * 256
for i, v in enumerate(SBOX): INV[v] = i

# RakNet message IDs (0.3.7-R2 scheme)
ID_INTERNAL_PING = 6; ID_PING = 7; ID_CONNECTED_PONG = 9
ID_CONNECTION_REQUEST = 11; ID_AUTH_KEY = 12
ID_RPC = 20  # RakNet 3.x: 20=ID_RPC, 21=ID_RPC_REPLY
ID_OPEN_CONNECTION_REQUEST = 24; ID_OPEN_CONNECTION_REPLY = 25
ID_OPEN_CONNECTION_COOKIE = 26; ID_NEW_INCOMING_CONNECTION = 30
ID_DISCONNECTION_NOTIFICATION = 32; ID_CONNECTION_REQUEST_ACCEPTED = 34
ID_PONG = 39; ID_TIMESTAMP = 40
ID_VEHICLE_SYNC = 200; ID_AIM_SYNC = 203; ID_STATS_UPDATE = 204
ID_BULLET_SYNC = 206; ID_PLAYER_SYNC = 207; ID_MARKERS_SYNC = 208

UNRELIABLE = 6; UNRELIABLE_SEQUENCED = 7; RELIABLE = 8
RELIABLE_ORDERED = 9; RELIABLE_SEQUENCED = 10

NETGAME_VERSION = 4057


class BitStream:
    def __init__(self, data=b''):
        self.data = bytearray(data)
        self.rpos = 0   # bit read position
        self.wpos = 0   # bit write position

    # ---- write ----
    def write_bits(self, value, nbits):
        v = value & ((1 << nbits) - 1)
        for i in range(nbits - 1, -1, -1):
            idx = self.wpos >> 3
            if idx >= len(self.data):
                self.data.append(0)
            bit = (v >> i) & 1
            self.data[idx] |= bit << (7 - (self.wpos & 7))
            self.wpos += 1

    def write_bytes(self, b):
        for byte in b:
            self.write_bits(byte, 8)

    def write_u8(self, v): self.write_bits(v, 8)
    def write_u16(self, v): self.write_bits(v, 16)
    def write_u32(self, v): self.write_bits(v, 32)
    def write_bool(self, v): self.write_bits(1 if v else 0, 1)

    def write_compressed_u32(self, v):
        b = struct.pack('<I', v)
        for i in range(3, 0, -1):
            if b[i] == 0:
                self.write_bits(1, 1)
            else:
                self.write_bits(0, 1)
                for j in range(0, i + 1):
                    self.write_bits(b[j], 8)
                return
        if (b[0] & 0xF0) == 0:
            self.write_bits(1, 1)
            self.write_bits(b[0] & 0x0F, 4)
        else:
            self.write_bits(0, 1)
            self.write_bits(b[0], 8)

    def write_f32(self, v): self.write_bytes(struct.pack('<f', v))
    def write_str8(self, s):
        b = s.encode('utf-8') if isinstance(s, str) else s
        self.write_u8(len(b)); self.write_bytes(b)

    # ---- read ----
    def read_bits(self, nbits):
        v = 0
        for _ in range(nbits):
            idx = self.rpos >> 3
            if idx >= len(self.data):
                raise EOFError('bitstream overrun')
            bit = (self.data[idx] >> (7 - (self.rpos & 7))) & 1
            v = (v << 1) | bit
            self.rpos += 1
        return v

    def read_bytes(self, n):
        return bytes(self.read_bits(8) for _ in range(n))

    def read_u8(self): return self.read_bits(8)
    def read_u16(self): return self.read_bits(16)
    def read_u32(self): return self.read_bits(32)
    def read_bool(self): return self.read_bits(1) == 1

    def read_compressed_u32(self):
        for i in range(3, 0, -1):
            if self.read_bool():
                continue
            v = 0
            for j in range(i + 1):
                v |= self.read_u8() << (8 * j)
            return v
        if self.read_bool():
            return self.read_bits(4)
        return self.read_u8()

    def read_f32(self): return struct.unpack('<f', self.read_bytes(4))[0]
    def read_str8(self):
        n = self.read_u8()
        return self.read_bytes(n).decode('utf-8', 'replace')

    def bits_left(self):
        return len(self.data) * 8 - self.rpos


class SAMPConnection:
    def __init__(self, ip, port, cookie_key):
        self.ip_str, self.port = ip, port
        self.cookie_key = cookie_key
        self.s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.s.settimeout(3.0)
        self.msg_num = 0
        self.remote_addr = (ip, port)
        self.state = 'DISCONNECTED'
        self.player_id = None
        self.challenge = None

    def encode_datagram(self, payload):
        h = 0
        for b in payload:
            h ^= (b & 0xAA)
        out = bytearray([h & 0xFF])
        for i, b in enumerate(payload):
            key = 0 if i % 2 == 0 else self.cookie_key
            out.append(INV[b] ^ key)
        return bytes(out)

    def send_raw(self, payload):
        self.s.sendto(self.encode_datagram(payload), self.remote_addr)

    def frame_message(self, data, reliability=RELIABLE, ordering_channel=0):
        bs = BitStream()
        bs.write_bool(False)  # hasAcks
        bs.write_u16(self.msg_num)
        self.msg_num = (self.msg_num + 1) & 0xFFFF
        bs.write_bits(reliability, 4)
        if reliability in (UNRELIABLE_SEQUENCED, RELIABLE_ORDERED, RELIABLE_SEQUENCED):
            bs.write_bits(ordering_channel, 5)
            bs.write_u16(0)
        bs.write_bool(False)  # not split
        bs.write_u16(len(data) * 8)
        bs.write_bytes(data)
        return bytes(bs.data)

    def send_message(self, data, reliability=RELIABLE, ordering_channel=0):
        self.send_raw(self.frame_message(data, reliability, ordering_channel))

    def send_rpc(self, rpc_id, payload_bs, reliability=RELIABLE_ORDERED, channel=0):
        bs = BitStream()
        bs.write_u8(ID_RPC)
        bs.write_u8(rpc_id)
        bs.write_compressed_u32(len(payload_bs.data) * 8)
        bs.write_bytes(bytes(payload_bs.data))
        self.send_message(bytes(bs.data), reliability, channel)

    def handshake(self):
        self.send_raw(bytes([ID_OPEN_CONNECTION_REQUEST, 0, 0]))
        r, _ = self.s.recvfrom(2048)
        assert r[0] == ID_OPEN_CONNECTION_COOKIE, f'want cookie got {r.hex()}'
        challenge = int.from_bytes(r[1:3], 'little')
        echo = (challenge ^ 0x6969) & 0xFFFF
        self.send_raw(bytes([ID_OPEN_CONNECTION_REQUEST, echo & 0xFF, echo >> 8]))
        r, _ = self.s.recvfrom(2048)
        assert r[0] == ID_OPEN_CONNECTION_REPLY, f'want reply got {r.hex()}'
        self.state = 'TRANSPORT_UP'
        return True

    def parse_datagram(self, r):
        bs = BitStream(r)
        msgs = []
        has_acks = bs.read_bool()
        if has_acks:
            count = bs.read_compressed_u32()
            for _ in range(count):
                single = bs.read_bool()
                mn = bs.read_u16()
                if not single:
                    mx = bs.read_u16()
        while bs.bits_left() >= 16 + 4 + 1 + 16:
            try:
                mn = bs.read_u16()
                rel = bs.read_bits(4)
                if rel in (UNRELIABLE_SEQUENCED, RELIABLE_ORDERED, RELIABLE_SEQUENCED):
                    oc = bs.read_bits(5)
                    oi = bs.read_u16()
                is_split = bs.read_bool()
                if is_split:
                    spid = bs.read_u16()
                    spi = bs.read_compressed_u32()
                    spc = bs.read_compressed_u32()
                nbits = bs.read_u16()
                nbytes = (nbits + 7) // 8
                data = bytes(bs.read_bits(8) for _ in range(nbytes))
                msgs.append((data[0], data[1:], mn, rel, nbits))
            except EOFError:
                break
        return msgs

    def send_ack(self, msg_nums):
        bs = BitStream()
        bs.write_bool(True)
        ranges = []
        for mn in sorted(set(msg_nums)):
            if ranges and mn == ranges[-1][1] + 1:
                ranges[-1][1] = mn
            else:
                ranges.append([mn, mn])
        bs.write_compressed_u32(len(ranges))
        for lo, hi in ranges:
            bs.write_bool(lo == hi)
            bs.write_u16(lo)
            if lo != hi:
                bs.write_u16(hi)
        self.send_raw(bytes(bs.data))


def read_cookie_key():
    pid = int(open('/home/z/ghrp-scratch/server-test/run/server.pid').read().strip())
    mem = os.open(f'/proc/{pid}/mem', os.O_RDONLY)
    os.lseek(mem, 0x81a2704, 0)
    keyw = os.read(mem, 2)
    os.close(mem)
    return keyw[0]


def main():
    key = read_cookie_key()
    print(f'[*] cookie key: 0x{key:02x}')
    c = SAMPConnection('127.0.0.1', 14448, key)
    print('[*] transport handshake...')
    c.handshake()
    print('[+] TRANSPORT UP')

    print('[*] sending CONNECTION_REQUEST (reliability-framed)...')
    c.send_message(bytes([ID_CONNECTION_REQUEST]))
    accepted = None
    deadline = time.time() + 8
    while time.time() < deadline:
        try:
            r, _ = c.s.recvfrom(2048)
        except socket.timeout:
            c.send_message(bytes([ID_CONNECTION_REQUEST]))
            continue
        for (mt, payload, mn, rel, nbits) in c.parse_datagram(r):
            print(f'    <- msg id=0x{mt:02x} len={len(payload)} mn={mn} rel={rel}')
            if mt == ID_CONNECTION_REQUEST_ACCEPTED:
                accepted = payload
                c.send_ack([mn])
        if accepted is not None:
            break
    if accepted is None:
        print('[-] no ACCEPTED')
        return

    addr, port16, pid16, challenge = struct.unpack_from('<IHII', accepted, 0)
    print(f'[+] ACCEPTED: addr=0x{addr:08x} port={port16} playerIndex={pid16} challenge=0x{challenge:08x}')
    c.player_id = pid16
    c.challenge = challenge

    myip = socket.inet_aton('127.0.0.1')
    nic = bytes([ID_NEW_INCOMING_CONNECTION]) + myip + struct.pack('<H', c.s.getsockname()[1])
    c.send_message(nic)
    print('[*] NEW_INCOMING sent')

    name = 'GHRPProbe'
    bs = BitStream()
    bs.write_u32(NETGAME_VERSION)
    bs.write_u8(1)
    bs.write_str8(name)
    bs.write_u32(challenge ^ NETGAME_VERSION)
    bs.write_str8('B1CD2E34F5A6B7C8D9E0F1A2B3C4D5E6')
    bs.write_str8('0.3.7-R2')
    c.send_rpc(25, bs)
    print('[*] RPC 25 ClientJoin sent - waiting for game flow...')

    deadline = time.time() + 25
    while time.time() < deadline:
        try:
            r, _ = c.s.recvfrom(2048)
        except socket.timeout:
            continue
        for (mt, payload, mn, rel, nbits) in c.parse_datagram(r):
            if rel in (RELIABLE, RELIABLE_ORDERED, RELIABLE_SEQUENCED):
                c.send_ack([mn])
            if mt == ID_RPC:
                rbs = BitStream(payload)
                try:
                    rpc_id = rbs.read_u8()
                    blen = rbs.read_compressed_u32()
                    print(f'    <- RPC {rpc_id} ({blen} bits)')
                    if rpc_id == 139:
                        print('    [+] RPC 139 InitGame RECEIVED!')
                except EOFError:
                    pass
            elif mt == ID_INTERNAL_PING:
                p = bytes([ID_CONNECTED_PONG]) + payload[:4] + struct.pack('<I', int(time.time() * 1000) & 0xFFFFFFFF)
                c.send_message(p)
            else:
                print(f'    <- msg 0x{mt:02x} len={len(payload)}')

    print('[*] probe done')


if __name__ == '__main__':
    main()
