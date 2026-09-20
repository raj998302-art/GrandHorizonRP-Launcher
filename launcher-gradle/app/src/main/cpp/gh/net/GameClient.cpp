#include "GameClient.h"
#include "../core/GHLog.h"
#include <chrono>
#include <cstdio>
#include <cstring>

namespace gh::net {

static uint64_t nowMs() {
    return (uint64_t)std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

// ------------------------------------------------------------------ transport

bool GameClient::openSocket() {
    sock_ = ::socket(AF_INET, SOCK_DGRAM, 0);
    if (sock_ < 0) return false;
#ifdef _WIN32
    u_long nb = 1; ::ioctlsocket(sock_, FIONBIO, &nb);
#else
    ::fcntl(sock_, F_SETFL, ::fcntl(sock_, F_GETFL, 0) | O_NONBLOCK);
#endif
    return true;
}

void GameClient::closeSocket() {
    if (sock_ >= 0) {
#ifdef _WIN32
        ::closesocket(sock_);
#else
        ::close(sock_);
#endif
        sock_ = -1;
    }
    if (voiceSock_ >= 0) {
#ifdef _WIN32
        ::closesocket(voiceSock_);
#else
        ::close(voiceSock_);
#endif
        voiceSock_ = -1;
    }
}

void GameClient::obfuscate(std::vector<uint8_t>& p) const {
    // hash byte first
    uint8_t h = 0;
    for (uint8_t b : p) h ^= (uint8_t)(b & 0xAA);
    std::vector<uint8_t> out;
    out.reserve(p.size() + 1);
    out.push_back(h);
    for (size_t i = 0; i < p.size(); ++i) {
        uint8_t key = (i & 1) ? xorKey_ : 0;
        out.push_back((uint8_t)(SBOX_INV[p[i]] ^ key));
    }
    p.swap(out);
}

bool GameClient::sendRaw(const uint8_t* data, size_t len) {
    std::vector<uint8_t> pkt(data, data + len);
    obfuscate(pkt);
    sockaddr_in sa{};
    sa.sin_family = AF_INET;
    sa.sin_port = htons(port_);
    sa.sin_addr.s_addr = ::inet_addr(host_.c_str());
    std::lock_guard<std::mutex> lk(sendMtx_);
    return ::sendto(sock_, (const char*)pkt.data(), (int)pkt.size(), 0,
                    (const sockaddr*)&sa, sizeof(sa)) == (int)pkt.size();
}

// ------------------------------------------------------------------ handshake

bool GameClient::connect(const std::string& host, uint16_t port, const std::string& playerName) {
    host_ = host; port_ = port; name_ = playerName;
    xorKey_ = (uint8_t)((port ^ 0xCCCC) & 0xFF);
    // deterministic-per-install serial: numeric % 1001 == 0 (open.mp contract)
    // built from a simple hash so it is stable across reconnects of this device.
    uint64_t h = 1469598103934665603ull;
    for (char c : playerName) { h ^= (uint8_t)c; h *= 1099511628211ull; }
    uint64_t serial = (h % 1000000000000ull) * 1001ull;
    char buf[40];
    snprintf(buf, sizeof(buf), "%llX", (unsigned long long)serial);
    gpci_ = buf;

    if (!openSocket()) { emit("onNetError", "{\"error\":\"socket\"}"); return false; }
    if (!handshake()) {
        closeSocket();
        emit("onNetError", "{\"error\":\"handshake\"}");
        return false;
    }
    running_ = true;
    state_ = State::Requesting;
    emit("onNetState", "{\"state\":\"transport\"}");
    // Kick off the RakNet connection: framed CONNECTION_REQUEST (no password).
    uint8_t req[1] = { kConnectionRequest };
    sendFramed(req, 1, kReliable);
    return true;
}

bool GameClient::handshake() {
    // 1. cookie request
    uint8_t req[3] = { kOpenConnectionRequest, 0, 0 };
    if (!sendRaw(req, 3)) return false;
    // wait for cookie
    for (int i = 0; i < 20; ++i) {
        uint8_t buf[2048];
        sockaddr_in sa{}; socklen_t sl = sizeof(sa);
        int n = ::recvfrom(sock_, (char*)buf, sizeof(buf), 0, (sockaddr*)&sa, &sl);
        if (n == 3 && buf[0] == kOpenConnectionCookie) {
            uint16_t ch = (uint16_t)(buf[1] | (buf[2] << 8));
            uint16_t echo = (uint16_t)(ch ^ 0x6969);
            uint8_t req2[3] = { kOpenConnectionRequest, (uint8_t)(echo & 0xFF), (uint8_t)(echo >> 8) };
            if (!sendRaw(req2, 3)) return false;
            for (int j = 0; j < 20; ++j) {
                int m = ::recvfrom(sock_, (char*)buf, sizeof(buf), 0, (sockaddr*)&sa, &sl);
                if (m >= 2 && buf[0] == kOpenConnectionReply) return true;
                pollWait(10);
            }
            return false;
        }
        pollWait(50);
    }
    return false;
}

void GameClient::pollWait(int ms) {
#ifdef _WIN32
    Sleep(ms);
#else
    struct timespec ts{ 0, ms * 1000000L };
    nanosleep(&ts, nullptr);
#endif
}

void GameClient::disconnect() {
    if (!running_.exchange(false)) return;
    if (sock_ >= 0 && state_ >= State::Accepted) {
        uint8_t d[1] = { kDisconnectionNotification };
        sendFramed(d, 1, kReliable);
    }
    closeSocket();
    state_ = State::Idle;
    emit("onNetState", "{\"state\":\"disconnected\"}");
}

// ------------------------------------------------------------------ framing

bool GameClient::sendFramed(const uint8_t* data, size_t len, uint8_t reliability, uint8_t channel) {
    BitStream bs;
    bs.writeBool(false);            // no acks
    bs.writeU16(msgNum_); msgNum_ = (uint16_t)((msgNum_ + 1) & 0xFFFF);
    bs.writeBits(reliability, 4);
    if (reliability == kUnreliableSequenced || reliability == kReliableOrdered ||
        reliability == kReliableSequenced) {
        bs.writeBits(channel, 5);
        bs.writeU16(0);
    }
    bs.writeBool(false);            // not split
    bs.writeCompressedU16((uint16_t)(len * 8));
    while (bs.wpos & 7) bs.writeBit(false);   // byte-align the payload
    bs.writeBytes(data, len);
    return sendRaw(bs.data.data(), bs.data.size());
}

bool GameClient::sendRpcFramed(uint8_t rpcId, const BitStream& payload, uint8_t reliability, uint8_t channel) {
    BitStream env;
    env.writeU8(kIdRpc);
    env.writeU8(rpcId);
    env.writeCompressedU32((uint32_t)(payload.data.size() * 8));
    env.writeBytes(payload.data);
    return sendFramed(env.data.data(), env.data.size(), reliability, channel);
}

bool GameClient::sendRpc(uint8_t rpcId, const BitStream& payload) {
    return sendRpcFramed(rpcId, payload, kReliableOrdered, 0);
}

bool GameClient::sendClientJoin() {
    // NEW_INCOMING_CONNECTION
    {
        uint32_t ip = ::inet_addr(host_.c_str());
        uint8_t d[7];
        d[0] = kNewIncomingConnection;
        memcpy(d + 1, &ip, 4);
        uint16_t lp = 0; // source port unknown from our socket view; server accepts 0
        // (the server only uses this for bookkeeping)
        memcpy(d + 5, &lp, 2);
        sendFramed(d, sizeof(d), kReliable);
    }
    // static data
    {
        uint8_t d[1] = { kReceivedStaticData };
        sendFramed(d, 1, kReliable);
    }
    // ClientJoin + sampvoice ConnectData magic
    BitStream bs;
    bs.writeU32(4057);
    bs.writeU8(1);
    bs.writeStr8(name_);
    bs.writeU32(challenge_ ^ 4057);
    bs.writeStr8(gpci_);
    bs.writeStr8("0.3.7-R2");
    static const uint8_t magic[4] = { 0xEF, 0xBE, 0xAD, 0xDE };
    bs.writeBytes(magic, 4);
    bs.writeU8(11);   // sampvoice version
    bs.writeU8(1);    // micro present
    if (!sendRpcFramed(25, bs, kReliable, 0)) return false;
    state_ = State::Joined;
    emit("onNetState", "{\"state\":\"join_sent\"}");
    return true;
}

bool GameClient::sendGuiJson(uint16_t guiid, const std::string& json) {
    std::vector<uint8_t> pkt;
    pkt.push_back(kGuiJson);
    pkt.push_back((uint8_t)(guiid & 0xFF));
    pkt.push_back((uint8_t)(guiid >> 8));
    uint32_t len = (uint32_t)json.size();
    pkt.push_back((uint8_t)(len & 0xFF));
    pkt.push_back((uint8_t)((len >> 8) & 0xFF));
    pkt.push_back((uint8_t)((len >> 16) & 0xFF));
    pkt.push_back((uint8_t)((len >> 24) & 0xFF));
    pkt.insert(pkt.end(), json.begin(), json.end());
    return sendFramed(pkt.data(), pkt.size(), kReliable);
}

void GameClient::sendAcks() {
    if (toAck_.empty()) return;
    std::vector<uint16_t> nums;
    { std::lock_guard<std::mutex> lk(sendMtx_); nums.swap(toAck_); }
    std::sort(nums.begin(), nums.end());
    nums.erase(std::unique(nums.begin(), nums.end()), nums.end());
    std::vector<std::pair<uint16_t, uint16_t>> ranges;
    for (uint16_t mn : nums) {
        if (!ranges.empty() && mn == (uint16_t)(ranges.back().second + 1)) ranges.back().second = mn;
        else ranges.emplace_back(mn, mn);
    }
    BitStream bs;
    bs.writeBool(true);
    bs.writeCompressedU16((uint16_t)ranges.size());
    for (auto& r : ranges) {
        bs.writeBool(r.first == r.second);
        bs.writeU16(r.first);
        if (r.first != r.second) bs.writeU16(r.second);
    }
    sendRaw(bs.data.data(), bs.data.size());
}

// ------------------------------------------------------------------ receive

void GameClient::parseDatagram(const uint8_t* buf, size_t len) {
#ifdef GH_NET_DEBUG
    { printf("[recv %zu] ", len); for (size_t i = 0; i < len && i < 40; ++i) printf("%02x ", buf[i]); printf("\n"); }
#endif
    // Plain (unframed) connection-level datagrams first.
    if (len >= 13 && buf[0] == kConnectionRequestAccepted) {
        ReceivedMessage m;
        m.type = kConnectionRequestAccepted;
        m.payload.assign(buf + 1, buf + len);
        handleRakNetMessage(m);
        return;
    }
    if (len >= 5 && buf[0] == kInternalPing) {
        // plain ping: reply framed pong (server accepts either)
        std::vector<uint8_t> pong;
        pong.push_back(kConnectedPong);
        pong.insert(pong.end(), buf + 1, buf + 5);
        uint32_t t = (uint32_t)(nowMs() & 0xFFFFFFFF);
        pong.push_back(t & 0xFF); pong.push_back((t >> 8) & 0xFF);
        pong.push_back((t >> 16) & 0xFF); pong.push_back((t >> 24) & 0xFF);
        sendFramed(pong.data(), pong.size(), kReliable);
        return;
    }
    if (len >= 2 && (buf[0] == kOpenConnectionCookie || buf[0] == kOpenConnectionReply
                      || buf[0] == 32 /* disconnection */ || buf[0] == 36 /* banned */)) {
        // handshake leftovers / disconnect notifications (plain)
        if (buf[0] == 32) { running_ = false; emit("onNetState", "{\"state\":\"server_closed\"}"); }
        return;
    }
    BitStream bs(std::vector<uint8_t>(buf, buf + len));
    std::vector<ReceivedMessage> msgs;
    if (bs.readBit()) {  // ACK/NAK block
        uint16_t count = bs.readCompressedU16();
        for (uint16_t i = 0; i < count && !bs.overrun(); ++i) {
            bool single = bs.readBit();
            bs.readU16();
            if (!single) bs.readU16();
        }
    }
    while (bs.bitsLeft() >= 16 + 4 + 1 + 6 && !bs.overrun()) {
        size_t save = bs.rpos;
        ReceivedMessage m;
        m.msgNum = bs.readU16();
        m.reliability = (uint8_t)bs.readBits(4);
        if (m.reliability == kUnreliableSequenced || m.reliability == kReliableOrdered ||
            m.reliability == kReliableSequenced) {
            bs.readBits(5);
            bs.readU16();
        }
        bool split = bs.readBit();
        if (split) {
            uint16_t spid = bs.readU16();
            uint32_t spi = bs.readCompressedU32();
            uint32_t spc = bs.readCompressedU32();
            uint16_t nbits = bs.readCompressedU16();
            size_t nb = (nbits + 7) / 8;
            bs.alignRead();                      // payloads are byte-aligned
            if (bs.bitsLeft() < nb * 8) break;
            auto data = bs.readBytes(nb);
            auto& slot = splits_[spid];
            slot.count = spc;
            slot.parts[spi] = std::move(data);
            if (slot.parts.size() == spc) {
                std::vector<uint8_t> whole;
                for (uint32_t i = 0; i < spc; ++i) {
                    auto it = slot.parts.find(i);
                    if (it == slot.parts.end()) { whole.clear(); break; }
                    whole.insert(whole.end(), it->second.begin(), it->second.end());
                }
                splits_.erase(spid);
                if (!whole.empty()) {
                    m.type = whole[0];
                    m.payload.assign(whole.begin() + 1, whole.end());
                    msgs.push_back(std::move(m));
                }
            }
            continue;
        }
        uint16_t nbits = bs.readCompressedU16();
        size_t nb = (nbits + 7) / 8;
        bs.alignRead();                          // payloads are byte-aligned
        if (bs.bitsLeft() < nb * 8) { bs.rpos = save; break; }
        auto data = bs.readBytes(nb);
        if (data.empty()) break;
        m.type = data[0];
        m.payload.assign(data.begin() + 1, data.end());
        msgs.push_back(std::move(m));
    }
    if (!msgs.empty()) handleMessages(msgs);
}

void GameClient::handleMessages(std::vector<ReceivedMessage>& msgs) {
    for (auto& m : msgs) {
        if (m.reliability == kReliable || m.reliability == kReliableOrdered ||
            m.reliability == kReliableSequenced) {
            std::lock_guard<std::mutex> lk(sendMtx_);
            toAck_.push_back(m.msgNum);
        }
        handleRakNetMessage(m);
    }
}

void GameClient::handleRakNetMessage(const ReceivedMessage& m) {
    switch (m.type) {
    case kInternalPing: {
        if (m.payload.size() >= 4) {
            std::vector<uint8_t> pong;
            pong.push_back(kConnectedPong);
            pong.insert(pong.end(), m.payload.begin(), m.payload.begin() + 4);
            uint32_t t = (uint32_t)(nowMs() & 0xFFFFFFFF);
            pong.push_back(t & 0xFF); pong.push_back((t >> 8) & 0xFF);
            pong.push_back((t >> 16) & 0xFF); pong.push_back((t >> 24) & 0xFF);
            sendFramed(pong.data(), pong.size(), kReliable);
        }
        break;
    }
    case kAuthKey: {
        // [len u8][challenge string] -> reply from the table
        if (m.payload.size() >= 2) {
            uint8_t ln = m.payload[0];
            if ((size_t)ln + 1 > m.payload.size()) ln = (uint8_t)(m.payload.size() - 1);
            std::string ch((const char*)m.payload.data() + 1,
                           strnlen((const char*)m.payload.data() + 1, ln));
#ifdef GH_NET_DEBUG
            printf("[auth] challenge '%s' -> %s\n", ch.c_str(), authLookup(ch.c_str()) ? "FOUND" : "MISSING");
#endif
            const char* resp = authLookup(ch.c_str());
            if (resp) {
                std::vector<uint8_t> out;
                out.push_back(kAuthKey);
                out.push_back((uint8_t)strlen(resp));
                out.insert(out.end(), resp, resp + strlen(resp));
                sendFramed(out.data(), out.size(), kReliable);
            } else {
                GHLOG("net: unknown auth challenge %s", ch.c_str());
            }
        }
        break;
    }
    case kConnectionRequestAccepted: {
        // [ip u32][port u16][playerIndex u16][challenge u32]
        if (m.payload.size() >= 12) {
            playerId_ = (uint16_t)(m.payload[6] | (m.payload[7] << 8));
            challenge_ = (uint32_t)(m.payload[8] | (m.payload[9] << 8) |
                                    (m.payload[10] << 16) | (m.payload[11] << 24));
            state_ = State::Accepted;
            char json[96];
            snprintf(json, sizeof(json), "{\"state\":\"accepted\",\"player\":%u}",
                     playerId_);
            emit("onNetState", json);
            sendClientJoin();
        }
        break;
    }
    case kSvCtl:
        handleServerInfo(m);
        break;
    case kGuiJson:
        handleGuiJson(m);
        break;
    default:
        break;
    }
}

// ------------------------------------------------------------------ sampvoice

uint32_t GameClient::crc32c(const uint8_t* d, size_t n) {
    uint32_t crc = 0xFFFFFFFFu;
    for (size_t i = 0; i < n; ++i) {
        crc ^= d[i];
        for (int b = 0; b < 8; ++b)
            crc = (crc >> 1) ^ ((crc & 1) ? 0x82F63B78u : 0);
    }
    return crc ^ 0xFFFFFFFFu;
}

void GameClient::handleServerInfo(const ReceivedMessage& m) {
    // [type u8][b1 u8][len u16][payload...] after the 0xDE tag.
    if (m.payload.size() < 6) return;
    uint16_t len = (uint16_t)(m.payload[2] | (m.payload[3] << 8));
    if (len < 6 || m.payload.size() < 4u + len) return;
    voiceKey32_ = (uint32_t)(m.payload[4] | (m.payload[5] << 8) |
                             (m.payload[6] << 16) | (m.payload[7] << 24));
    voicePort_ = (uint16_t)(m.payload[8] | (m.payload[9] << 8));
    // Build the identification/keepalive packet (24-byte header, no payload).
    voiceKa_.assign(24, 0);
    voiceKa_[4] = voiceKey32_ & 0xFF;
    voiceKa_[5] = (voiceKey32_ >> 8) & 0xFF;
    voiceKa_[6] = (voiceKey32_ >> 16) & 0xFF;
    voiceKa_[7] = (voiceKey32_ >> 24) & 0xFF;
    voiceKa_[0x10] = playerId_ & 0xFF;
    voiceKa_[0x11] = (playerId_ >> 8) & 0xFF;
    uint32_t crc = crc32c(voiceKa_.data() + 4, 20);
    voiceKa_[0] = crc & 0xFF; voiceKa_[1] = (crc >> 8) & 0xFF;
    voiceKa_[2] = (crc >> 16) & 0xFF; voiceKa_[3] = (crc >> 24) & 0xFF;
    if (voiceSock_ < 0) {
        voiceSock_ = ::socket(AF_INET, SOCK_DGRAM, 0);
#ifdef _WIN32
        u_long nb = 1; ::ioctlsocket(voiceSock_, FIONBIO, &nb);
#else
        ::fcntl(voiceSock_, F_SETFL, ::fcntl(voiceSock_, F_GETFL, 0) | O_NONBLOCK);
#endif
    }
    voiceKeepalive();
    char json[96];
    snprintf(json, sizeof(json), "{\"voice_port\":%u,\"key\":\"%08x\"}", voicePort_, voiceKey32_);
    emit("onVoiceInfo", json);
}

void GameClient::voiceKeepalive() {
    if (voiceSock_ < 0 || voiceKa_.empty()) return;
    sockaddr_in sa{};
    sa.sin_family = AF_INET;
    sa.sin_port = htons(voicePort_);
    sa.sin_addr.s_addr = ::inet_addr(host_.c_str());
    ::sendto(voiceSock_, (const char*)voiceKa_.data(), (int)voiceKa_.size(), 0,
             (const sockaddr*)&sa, sizeof(sa));
    lastVoiceKa_ = nowMs();
}

// ------------------------------------------------------------------ gui json

void GameClient::handleGuiJson(const ReceivedMessage& m) {
    // payload (after the 252 tag): [guiid u16][len u32][json]
    if (m.payload.size() < 6) return;
    uint16_t guiid = (uint16_t)(m.payload[0] | (m.payload[1] << 8));
    uint32_t len = (uint32_t)(m.payload[2] | (m.payload[3] << 8) |
                              (m.payload[4] << 16) | (m.payload[5] << 24));
    if (m.payload.size() < 6u + len) return;
    std::string json((const char*)m.payload.data() + 6, len);
    char head[64];
    snprintf(head, sizeof(head), "{\"gui\":%u,\"json\":", guiid);
    emit("onGuiPacket", head + json + "}");

    if (guiid == 38) {
        // auth state machine
        bool r0 = json.find("\"r\":0") != std::string::npos;
        bool r1 = json.find("\"r\":1") != std::string::npos;
        if (authStep_ == 0 && (r0 || r1)) {
            authStep_ = 1;
            if (r0) {
                // registration: t=1 email+password, then t=3 gender, then t=5 skin
                std::string t1 = "{\"t\":1,\"s\":\"" + email_ + "\",\"p\":\"" + password_ + "\"}";
                sendGuiJson(38, t1);
                emit("onNetState", "{\"state\":\"register_step1\"}");
            } else {
                // existing account: t=6 password login
                std::string t6 = "{\"t\":6,\"s\":\"" + password_ + "\",\"r\":1}";
                sendGuiJson(38, t6);
                emit("onNetState", "{\"state\":\"login_sent\"}");
            }
        } else if (authStep_ == 1 && json.find("\"t\":1") == std::string::npos &&
                   json.find("\"o\"") != std::string::npos) {
            // server ack of step 1 — but we only get here for registration
            // when the next packet arrives; drive it on a timer instead (tick).
        }
    } else if (guiid == 50) {
        // spawn selection menu — take the train station (t=1)
        sendGuiJson(50, "{\"t\":1}");
        emit("onNetState", "{\"state\":\"spawn_selected\"}");
    }
}

void GameClient::tick() {
    if (!running_ || sock_ < 0) return;
    // receive pump
    for (int i = 0; i < 16; ++i) {
        uint8_t buf[2048];
        sockaddr_in sa{}; socklen_t sl = sizeof(sa);
        int n = ::recvfrom(sock_, (char*)buf, sizeof(buf), 0, (sockaddr*)&sa, &sl);
        if (n <= 0) break;
        if (n < 2) continue;
        parseDatagram(buf, (size_t)n);
    }
    sendAcks();
    // voice keepalive every second
    if (nowMs() - lastVoiceKa_ > 1000) voiceKeepalive();
    // registration progression (after t=1, send t=3 then t=5)
    if (authStep_ == 1) {
        uint64_t now = nowMs();
        if (regT1Ms_ == 0) { regT1Ms_ = now; }
        else if (now - regT1Ms_ > 2500) {
            authStep_ = 2;
            sendGuiJson(38, "{\"t\":3,\"r\":1}");
            emit("onNetState", "{\"state\":\"register_create\"}");
            regT1Ms_ = now;
        }
    } else if (authStep_ == 2 && nowMs() - regT1Ms_ > 4000) {
        authStep_ = 3;
        sendGuiJson(38, "{\"t\":5,\"r\":12}");
        emit("onNetState", "{\"state\":\"register_skin\"}");
    }
    if (authStep_ == 3 && !spawned_.exchange(true)) {
        emit("onNetState", "{\"state\":\"spawned\"}");
    }
}

void GameClient::emit(const char* method, const std::string& json) {
    GHLOG("net: %s %s", method, json.c_str());
    if (event_) event_(method, json.c_str());
}

} // namespace gh::net
