// Host-side verification of the C++ GameClient against the live local server.
// Mirrors the proven Python probe chain: transport -> auth -> accepted ->
// ClientJoin+DEADBEEF -> voice identify -> GUI auth -> login/register.
#include "gh/net/GameClient.h"
#include <cstdio>
#include <cstring>
#include <thread>
#include <chrono>
#include <string>

using namespace gh::net;

int main(int argc, char** argv) {
    const char* host = argc > 1 ? argv[1] : "21.0.16.75";
    int port = argc > 2 ? atoi(argv[2]) : 14448;
    std::string name = argc > 3 ? argv[3] : "CppTest";
    std::string password = argc > 4 ? argv[4] : "TestPass123";
    std::string email = name + "@test.ghrp.local";

    GameClient c;
    std::string lastState;
    std::string lastGui;
    c.setEventCallback([&](const char* method, const char* json) {
        printf("[evt] %s %s\n", method, json);
        if (std::strcmp(method, "onNetState") == 0) lastState = json;
        if (std::strcmp(method, "onGuiPacket") == 0) lastGui = json;
    });
    c.setCredentials(password, email);

    printf("[*] connecting %s:%d as %s\n", host, port, name.c_str());
    if (!c.connect(host, (uint16_t)port, name)) {
        printf("[-] FAIL: connect\n"); return 1;
    }
    printf("[+] transport up\n");
    // note: ClientJoin is sent automatically on ACCEPTED.

    // drive the protocol for up to 45 s
    for (int i = 0; i < 450; ++i) {
        c.tick();
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        if (i % 50 == 49) printf("[*] %ds state=%s\n", (i + 1) / 10, lastState.c_str());
    }
    printf("[*] final state: %s\n", lastState.c_str());
    printf("[*] last gui: %s\n", lastGui.c_str());
    bool ok = lastState.find("spawned") != std::string::npos ||
              lastState.find("register_skin") != std::string::npos ||
              lastState.find("login") != std::string::npos;
    printf("%s\n", ok ? "[+] C++ NET STACK VERIFIED" : "[-] incomplete (check states)");
    c.disconnect();
    return ok ? 0 : 2;
}
