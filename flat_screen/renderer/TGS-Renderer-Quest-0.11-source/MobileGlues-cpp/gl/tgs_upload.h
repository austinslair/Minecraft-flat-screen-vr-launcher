// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
#pragma once
#include <GLES3/gl32.h>
#include <array>
#include <cstddef>
namespace tgs {
template<class Buffer> void grow_upload_scratch(Buffer& buffer, size_t bytes) {
    if (buffer.size() < bytes) buffer.resize(bytes);
}
inline GLint tight_upload_alignment(GLint previous, size_t row_bytes) {
    return (previous == 1 || previous == 2 || previous == 4 || previous == 8) &&
           row_bytes % static_cast<size_t>(previous) == 0 ? previous : 1;
}
// Use already-captured state; no persistent state cache or driver queries.
template<class Driver> void tight_upload_state(Driver& d, const std::array<GLint,6>& previous,
                                               GLint alignment, bool restore) {
    constexpr GLenum names[] = {GL_UNPACK_ALIGNMENT, GL_UNPACK_ROW_LENGTH,
        GL_UNPACK_SKIP_ROWS, GL_UNPACK_SKIP_PIXELS, GL_UNPACK_IMAGE_HEIGHT, GL_UNPACK_SKIP_IMAGES};
    const GLint tight[] = {alignment, 0, 0, 0, 0, 0};
    for (size_t i=0; i<6; ++i)
        if (previous[i] != tight[i]) d.glPixelStorei(names[i], restore ? previous[i] : tight[i]);
}
}
