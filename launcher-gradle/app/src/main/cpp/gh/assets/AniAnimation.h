// GHEngine — ANP3 animation reader (format contract: ENGINE_ARCHITECTURE.md §1.5).
// Parses the observed ANP3 structure: magic, names, then bone tracks at a fixed
// stride. Keyframe sampling + full skeletal evaluation is staged for M2.
#pragma once
#include <vector>
#include <string>
#include <cstdint>
#include <cstring>

namespace gh {

struct AniTrack {
    std::string boneName;
    int64_t offset = 0;   // byte offset of the track header inside the file
};

struct AniAnimation {
    std::string animName;     // e.g. "Stand_anim"
    std::string trackName;    // e.g. "Stand1"
    std::vector<AniTrack> tracks;
    int64_t trackStride = 0;  // observed 936 bytes
    bool valid = false;
};

class AniAnimationFile {
public:
    static bool parse(const uint8_t* data, size_t size, AniAnimation& out);
};

} // namespace gh
