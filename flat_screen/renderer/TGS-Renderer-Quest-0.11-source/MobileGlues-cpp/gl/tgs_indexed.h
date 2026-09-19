// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
#pragma once
#include <GLES3/gl32.h>
namespace tgs {
// Called after frontend validation and prepareForDraw. Never borrow a buffer or
// retain GL state across calls. A custom restart index still needs CPU rewriting.
template<class Driver> bool native_indexed_batch(Driver& d, GLuint bound_ibo,
    bool base_supported, bool rewrite_restart, bool force_fixed,
    GLenum mode, const GLsizei* counts, GLenum type, const void* const* indices,
    GLsizei drawcount, const GLint* bases) {
    if (!bound_ibo || rewrite_restart || !d.glDrawElements || drawcount<=0 || !counts || !indices)
        return false;
    const bool native_base=base_supported && d.glDrawElementsBaseVertex;
    // Reject the entire batch before submitting any draws if any sub-draw needs
    // unsupported base vertex. Otherwise falling back would draw earlier entries twice.
    for(GLsizei i=0;i<drawcount;++i) {
        if(counts[i]<0)return false;
        if(counts[i]>0 && bases && bases[i]!=0 && !native_base)return false;
    }
    if(force_fixed)d.glEnable(GL_PRIMITIVE_RESTART_FIXED_INDEX);
    for(GLsizei i=0;i<drawcount;++i) {
        if(counts[i]==0)continue;
        const GLint base=bases ? bases[i] : 0;
        if(base)d.glDrawElementsBaseVertex(mode,counts[i],type,indices[i],base);
        else d.glDrawElements(mode,counts[i],type,indices[i]);
    }
    if(force_fixed)d.glDisable(GL_PRIMITIVE_RESTART_FIXED_INDEX);
    return true;
}
}
