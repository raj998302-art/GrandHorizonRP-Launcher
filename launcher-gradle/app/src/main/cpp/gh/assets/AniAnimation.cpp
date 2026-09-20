#include "AniAnimation.h"
#include "../core/GHLog.h"
#include <algorithm>

namespace gh {

static std::string readCString(const uint8_t* p, size_t max) {
    size_t n = 0;
    while (n < max && p[n]) n++;
    return std::string((const char*)p, n);
}

bool AniAnimationFile::parse(const uint8_t* data, size_t size, AniAnimation& out) {
    out = AniAnimation{};
    if (size < 80 || memcmp(data, "ANP3", 4) != 0) return false;
    out.animName = readCString(data + 4, 28);

    // Second name field ("Stand1") observed at ~+36, followed by "nim" marker at ~+43.
    if (size > 44) out.trackName = readCString(data + 36, 12);

    // Bone tracks: names observed at regular strides from ~+72 ("Root", " Pelvis", ...).
    // Detect stride by locating two consecutive name runs and measuring their distance.
    const size_t nameScanStart = 64;
    std::vector<std::pair<int64_t, std::string>> names;
    size_t i = nameScanStart;
    while (i < size) {
        // names may start with a space (" Pelvis") or letter
        if ((data[i] == ' ' || (data[i] >= 'A' && data[i] <= 'z')) &&
            i + 4 < size && data[i + 1] >= 'A' && data[i + 1] <= 'z') {
            std::string s = readCString(data + i, 32);
            bool wordish = s.size() >= 2 && s.size() <= 28;
            if (wordish) {
                names.emplace_back((int64_t)i, s);
                i += s.size() + 1;
                continue;
            }
        }
        i++;
    }
    if (names.size() < 2) { GHERR("AniAnimation: too few track names"); return false; }

    // Dominant stride between consecutive name offsets.
    std::vector<int64_t> diffs;
    for (size_t k = 1; k < names.size(); k++) diffs.push_back(names[k].first - names[k - 1].first);
    std::sort(diffs.begin(), diffs.end());
    int64_t stride = diffs[diffs.size() / 2];
    if (stride < 64 || stride > 4096) { GHERR("AniAnimation: implausible stride %lld", (long long)stride); return false; }

    // Keep names that fit the stride grid.
    for (auto& n : names) {
        if (n.first % 2 == 0) out.tracks.push_back({n.second, n.first});
    }
    out.trackStride = stride;
    out.valid = !out.tracks.empty();
    GHLOG("AniAnimation: %s (%s) tracks=%zu stride=%lld",
          out.animName.c_str(), out.trackName.c_str(), out.tracks.size(), (long long)stride);
    return out.valid;
}

} // namespace gh
