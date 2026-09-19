// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
#pragma once
#include <GLES3/gl32.h>
#include <vector>
#include <cstdint>
#include <cstdlib>
#include <cstring>
namespace tgs {
struct StreamTotals { uint64_t mapped=0, fallback=0, reference=0, bytes=0; };
inline thread_local StreamTotals stream_totals;
inline bool stream_uploads_enabled(){static const bool yes=[] {const char* v=std::getenv("TGS_STREAM_UPLOADS");return v&&std::strcmp(v,"1")==0;}();return yes;}
// Only for OUR mutable scratch command buffer. The caller has already allocated
// and bound sufficient storage. Never applied to a mod's buffer or a partial update.
// INVALIDATE lets the driver rename a busy allocation; no UNSYNCHRONIZED flag.
template<class Command,class Driver,class Fill> void upload_commands(Driver& d, size_t count,bool enabled,Fill fill){
    const GLsizeiptr bytes=static_cast<GLsizeiptr>(count*sizeof(Command));
    stream_totals.bytes+=bytes;
    if(enabled && d.glMapBufferRange && d.glUnmapBuffer){
        auto* mapped=static_cast<Command*>(d.glMapBufferRange(GL_DRAW_INDIRECT_BUFFER,0,bytes,
            GL_MAP_WRITE_BIT|GL_MAP_INVALIDATE_BUFFER_BIT));
        if(mapped){
            fill(mapped);
            if(d.glUnmapBuffer(GL_DRAW_INDIRECT_BUFFER)==GL_TRUE){++stream_totals.mapped;return;}
        }
    }
    // Retain the backend after a transient map/unmap failure; rewrite the entire
    // used range before drawing, even if the driver reported its contents lost.
    static thread_local std::vector<Command> scratch;
    if(scratch.size()<count)scratch.resize(count);
    fill(scratch.data());d.glBufferSubData(GL_DRAW_INDIRECT_BUFFER,0,bytes,scratch.data());
    if(enabled)++stream_totals.fallback;else ++stream_totals.reference;
}
}
