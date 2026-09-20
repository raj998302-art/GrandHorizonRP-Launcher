import sys, time, struct, socket, random
sys.path.insert(0, '/home/z/ghrp-scratch/probe')
import full_connect as FC
from samp_full import BitStream, RELIABLE
from join_full import parse_verbose

name = 'HoldNoJoin_' + str(random.randint(100,999))
c = FC.connect(name=name, send_join=False)
myip = socket.inet_aton('127.0.0.1')
c.send_message(bytes([30]) + myip + struct.pack('<H', c.s.getsockname()[1]), reliability=RELIABLE)
c.send_message(bytes([0x29]), reliability=RELIABLE)
print("[+] held (no join):", c.s.getsockname()[1])
open('/tmp/holdport','w').write(str(c.s.getsockname()[1]))
pending=set(); stop=time.time()+50
while time.time()<stop:
    c.s.settimeout(1.0)
    try: r,_=c.s.recvfrom(65536)
    except socket.timeout: continue
    if not r or r[0]==0x22: continue
    bsr=BitStream(r)
    for it in parse_verbose(bsr,''):
        if it[0]=='ACK': continue
        _,mn,rel,oc,oi,nbits,data=it
        if rel in (8,9,10): pending.add(mn)
        if data and data[0]==0x06 and len(data)>=5:
            c.send_message(bytes([9])+data[1:5]+struct.pack('<I',int(time.time()*1000)&0xFFFFFFFF), reliability=RELIABLE)
    if pending: c.send_ack(sorted(pending)); pending.clear()
