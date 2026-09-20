#!/usr/bin/env python3
"""UDP relay proxy: logs all packets between probe and server."""
import socket, threading, time, sys

SERVER = ("127.0.0.1", 14448)
LOG = []

def relay(listen_port):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.bind(("127.0.0.1", listen_port))
    s.settimeout(0.05)
    peer = None
    last_activity = time.time()
    while time.time() - last_activity < 30:
        try:
            data, addr = s.recvfrom(65536)
            last_activity = time.time()
            if addr == SERVER:
                # from server -> forward to peer
                if peer:
                    s.sendto(data, peer)
                    LOG.append(("S->C", data.hex()))
            else:
                peer = addr
                # from client -> forward to server
                s.sendto(data, SERVER)
                LOG.append(("C->S", data.hex()))
        except socket.timeout:
            continue
    s.close()

if __name__ == "__main__":
    port = int(sys.argv[1])
    t = threading.Thread(target=relay, args=(port,), daemon=True)
    t.start()
    t.join()
    for d, h in LOG:
        print(f"{d}: {h}")
