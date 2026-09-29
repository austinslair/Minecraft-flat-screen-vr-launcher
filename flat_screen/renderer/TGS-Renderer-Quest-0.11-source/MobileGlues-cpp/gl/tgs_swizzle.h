// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
// Fast path for the most common desktop upload GLES cannot take directly:
// GL_BGRA + GL_UNSIGNED_BYTE into an RGBA texture.
#pragma once
#include <cstddef>
#include <cstdint>

namespace tgs {

// Only the byte-exact case: four unswapped bytes per pixel in B,G,R,A order,
// written out as four RGBA channels. Everything else keeps the generic decoder.
inline bool can_swizzle(unsigned format, unsigned type, int out_channels, bool swap_bytes) {
    constexpr unsigned kBgra = 0x80E1;         // GL_BGRA
    constexpr unsigned kUnsignedByte = 0x1401; // GL_UNSIGNED_BYTE
    return format == kBgra && type == kUnsignedByte && out_channels == 4 && !swap_bytes;
}

// Swaps the B and R bytes of `pixels` pixels. Reads exactly pixels*4 bytes from
// src; neither pointer needs to be aligned. Plain byte moves vectorize well.
inline void bgra_to_rgba(const uint8_t* __restrict src, uint8_t* __restrict dst, size_t pixels) {
    for (size_t i = 0; i < pixels * 4; i += 4) {
        dst[i + 0] = src[i + 2];
        dst[i + 1] = src[i + 1];
        dst[i + 2] = src[i + 0];
        dst[i + 3] = src[i + 3];
    }
}

} // namespace tgs
