// GHEngine — own math library (no external deps).
#pragma once
#include <cmath>
#include <cstring>
#include <cstdint>

namespace gh {

struct Vec2 { float x = 0, y = 0; };
struct Vec3 {
    float x = 0, y = 0, z = 0;
    Vec3() = default;
    Vec3(float a, float b, float c) : x(a), y(b), z(c) {}
    Vec3 operator+(const Vec3& o) const { return {x + o.x, y + o.y, z + o.z}; }
    Vec3 operator-(const Vec3& o) const { return {x - o.x, y - o.y, z - o.z}; }
    Vec3 operator*(float s) const { return {x * s, y * s, z * s}; }
    Vec3 operator*(const Vec3& o) const { return {x * o.x, y * o.y, z * o.z}; }
    Vec3 operator-() const { return {-x, -y, -z}; }
    Vec3& operator+=(const Vec3& o) { x += o.x; y += o.y; z += o.z; return *this; }
};
inline float dot(const Vec3& a, const Vec3& b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
inline Vec3 cross(const Vec3& a, const Vec3& b) {
    return {a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x};
}
inline float length(const Vec3& a) { return sqrtf(dot(a, a)); }
inline Vec3 normalize(const Vec3& a) { float l = length(a); return l > 1e-8f ? a * (1.0f / l) : Vec3(0, 0, 0); }

struct Vec4 { float x = 0, y = 0, z = 0, w = 0; };

struct Quat {
    float x = 0, y = 0, z = 0, w = 1;
    Quat() = default;
    Quat(float ax, float ay, float az, float aw) : x(ax), y(ay), z(az), w(aw) {}
    static Quat fromAxisAngle(const Vec3& axis, float rad) {
        float h = rad * 0.5f, s = sinf(h);
        Vec3 a = normalize(axis);
        return Quat(a.x * s, a.y * s, a.z * s, cosf(h));
    }
    Quat operator*(const Quat& o) const {
        return Quat(
            w * o.x + x * o.w + y * o.z - z * o.y,
            w * o.y - x * o.z + y * o.w + z * o.x,
            w * o.z + x * o.y - y * o.x + z * o.w,
            w * o.w - x * o.x - y * o.y - z * o.z);
    }
};

// Column-major 4x4 for OpenGL.
struct Mat4 {
    float m[16];
    Mat4() { identity(); }
    void identity() { memset(m, 0, sizeof(m)); m[0] = m[5] = m[10] = m[15] = 1.0f; }

    static Mat4 perspective(float fovY, float aspect, float nearZ, float farZ) {
        Mat4 r; float f = 1.0f / tanf(fovY * 0.5f);
        r.m[0] = f / aspect; r.m[5] = f;
        r.m[10] = (farZ + nearZ) / (nearZ - farZ); r.m[11] = -1.0f;
        r.m[14] = 2.0f * farZ * nearZ / (nearZ - farZ); r.m[15] = 0.0f;
        return r;
    }
    static Mat4 ortho(float l, float r_, float b, float t, float n = -1, float f = 1) {
        Mat4 r;
        r.m[0] = 2.0f / (r_ - l); r.m[5] = 2.0f / (t - b); r.m[10] = -2.0f / (f - n);
        r.m[12] = -(r_ + l) / (r_ - l); r.m[13] = -(t + b) / (t - b); r.m[14] = -(f + n) / (f - n);
        return r;
    }
    static Mat4 lookAt(const Vec3& eye, const Vec3& at, const Vec3& up) {
        Vec3 z = normalize(eye - at);
        Vec3 x = normalize(cross(up, z));
        Vec3 y = cross(z, x);
        Mat4 r;
        r.m[0] = x.x; r.m[4] = x.y; r.m[8] = x.z;  r.m[12] = -dot(x, eye);
        r.m[1] = y.x; r.m[5] = y.y; r.m[9] = y.z;  r.m[13] = -dot(y, eye);
        r.m[2] = z.x; r.m[6] = z.y; r.m[10] = z.z; r.m[14] = -dot(z, eye);
        return r;
    }
    static Mat4 translate(const Vec3& t) {
        Mat4 r; r.m[12] = t.x; r.m[13] = t.y; r.m[14] = t.z; return r;
    }
    static Mat4 scale(const Vec3& s) {
        Mat4 r; r.m[0] = s.x; r.m[5] = s.y; r.m[10] = s.z; return r;
    }
    static Mat4 rotateY(float rad) {
        Mat4 r; float c = cosf(rad), s = sinf(rad);
        r.m[0] = c; r.m[2] = -s; r.m[8] = s; r.m[10] = c; return r;
    }
    static Mat4 fromQuat(const Quat& q) {
        Mat4 r;
        float xx = q.x * q.x, yy = q.y * q.y, zz = q.z * q.z;
        float xy = q.x * q.y, xz = q.x * q.z, yz = q.y * q.z;
        float wx = q.w * q.x, wy = q.w * q.y, wz = q.w * q.z;
        r.m[0] = 1 - 2 * (yy + zz); r.m[4] = 2 * (xy - wz);     r.m[8] = 2 * (xz + wy);
        r.m[1] = 2 * (xy + wz);     r.m[5] = 1 - 2 * (xx + zz); r.m[9] = 2 * (yz - wx);
        r.m[2] = 2 * (xz - wy);     r.m[6] = 2 * (yz + wx);     r.m[10] = 1 - 2 * (xx + yy);
        return r;
    }
    Mat4 operator*(const Mat4& o) const {
        Mat4 r;
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += m[k * 4 + rr] * o.m[c * 4 + k];
                r.m[c * 4 + rr] = s;
            }
        return r;
    }
    Vec3 transformPoint(const Vec3& p) const {
        return Vec3(
            m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12],
            m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13],
            m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14]);
    }
};

} // namespace gh
