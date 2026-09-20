// GHEngine net — SA-MP 0.3.7-R2 + BR-netcode client (complete protocol).
//
// Wire contract (all live-verified against the deployed gamemode; see
// ghrp-scratch/probe/GAME_LOGIN_PROTOCOL.md + SAMPVOICE_HANDSHAKE.md):
//   * C->S datagrams obfuscated: [hash byte][SBOX_INV[p] ^ (i odd ? key : 0)]
//     hash = XOR of (payload byte & 0xAA); key = (serverPort ^ 0xCCCC) & 0xFF.
//   * S->C datagrams plain.
//   * Cookie handshake (vanilla 0.3.7): [24,0,0] -> [26,ch u16] ->
//     [24, ch^0x6969] -> [25,0].
//   * CONNECTION_REQUEST -> AUTH_KEY table challenge -> ACCEPTED -> join.
//   * sampvoice: ClientJoin carries EF BE AD DE + [11,1]; server-info arrives
//     as [0xDE][0][0][6][key32][voice_port]; identify via 24-byte VoicePacket
//     on <serverIP>:<voice_port> every second.
//   * gamemode UI: [252][guiid u16][len u32][json] both directions.
#pragma once
#include "BitStream.h"
#include "SampTables.h"
#include <algorithm>
#include <atomic>
#include <cstdint>
#include <deque>
#include <functional>
#include <map>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#ifdef _WIN32
#include <winsock2.h>
typedef int socklen_t;
#else
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#endif

namespace gh::net {

// ---- RakNet / SA-MP message ids ----
enum : uint8_t {
    kInternalPing = 6,
    kConnectedPong = 9,
    kConnectionRequest = 11,
    kAuthKey = 12,
    kDetectLost = 23,
    kOpenConnectionRequest = 24,
    kOpenConnectionCookie = 26,
    kOpenConnectionReply = 25,
    kNewIncomingConnection = 30,
    kDisconnectionNotification = 32,
    kConnectionRequestAccepted = 34,
    kReceivedStaticData = 41,
    kIdRpc = 20,
    kIdRpcReply = 21,
    kSvCtl = 0xDE,
    kGuiJson = 252,
};

enum Reliability : uint8_t {
    kUnreliable = 0,
    kUnreliableSequenced = 1,
    kReliable = 8,
    kReliableOrdered = 9,
    kReliableSequenced = 10,
};

struct ReceivedMessage {
    uint8_t type = 0;
    std::vector<uint8_t> payload;   // data[1:]
    uint16_t msgNum = 0;
    uint8_t reliability = 0;
};

class GameClient {
public:
    // Events surfaced to the engine/JNI layer.
    using EventFn = std::function<void(const char* method, const char* json)>;
    void setEventCallback(EventFn fn) { event_ = std::move(fn); }

    bool connect(const std::string& host, uint16_t port, const std::string& playerName);
    void disconnect();

    // Full login automation: waits for the auth GUI packet and completes
    // either registration (new account) or login (existing + password).
    void setCredentials(const std::string& password, const std::string& email) {
        password_ = password; email_ = email;
    }

    // Manual game-level operations (mirrors the Python probe spec).
    bool sendClientJoin();
    bool sendGuiJson(uint16_t guiid, const std::string& json);
    bool sendRpc(uint8_t rpcId, const BitStream& payload);
    void sendChatless() {} // (chat is RPC 101; not needed for the login chain)

    void tick();               // pump receive path + keepalives (call ~10 Hz)
    bool isConnected() const { return state_ >= State::Accepted; }
    bool isSpawned() const { return spawned_; }
    uint16_t playerId() const { return playerId_; }

    ~GameClient() { disconnect(); }

private:
    enum class State { Idle, Transport, Requesting, Authenticating, Accepted, Joined };
    void pollWait(int ms);

    // --- transport ---
    bool openSocket();
    void closeSocket();
    bool sendRaw(const uint8_t* data, size_t len);       // obfuscated datagram
    bool sendPlain(const uint8_t* data, size_t len);     // plain datagram (cookie phase is obfuscated too)
    bool handshake();
    void obfuscate(std::vector<uint8_t>& payload) const; // in place C->S codec

    // --- framing ---
    bool sendFramed(const uint8_t* data, size_t len, uint8_t reliability, uint8_t channel = 0);
    bool sendRpcFramed(uint8_t rpcId, const BitStream& payload, uint8_t reliability, uint8_t channel);
    void sendAcks();
    void parseDatagram(const uint8_t* buf, size_t len);
    void handleMessages(std::vector<ReceivedMessage>& msgs);
    void handleRakNetMessage(const ReceivedMessage& m);
    void handleGuiJson(const ReceivedMessage& m);

    // --- voice ---
    void handleServerInfo(const ReceivedMessage& m);
    void voiceKeepalive();
    static uint32_t crc32c(const uint8_t* d, size_t n);

    void emit(const char* method, const std::string& json);

    // --- state ---
    State state_ = State::Idle;
    std::string host_;
    uint16_t port_ = 0;
    std::string name_;
    std::string password_;
    std::string email_;
    int sock_ = -1;
    uint8_t xorKey_ = 0;             // (port ^ 0xCCCC) & 0xFF
    uint16_t msgNum_ = 0;
    uint16_t playerId_ = 0;
    uint32_t challenge_ = 0;
    std::string gpci_;
    std::atomic<bool> running_{false};
    std::atomic<bool> spawned_{false};
    std::mutex sendMtx_;

    // reliability bookkeeping
    std::vector<uint16_t> toAck_;
    struct SplitPart { std::map<uint32_t, std::vector<uint8_t>> parts; uint32_t count = 0; };
    std::map<uint16_t, SplitPart> splits_;

    // voice state
    int voiceSock_ = -1;
    uint32_t voiceKey32_ = 0;
    uint16_t voicePort_ = 0;
    uint64_t lastVoiceKa_ = 0;
    std::vector<uint8_t> voiceKa_;

    // auth gui state machine
    int authStep_ = 0;
    uint64_t regT1Ms_ = 0;
    uint64_t lastPingMs_ = 0;
    EventFn event_;
};

} // namespace gh::net
