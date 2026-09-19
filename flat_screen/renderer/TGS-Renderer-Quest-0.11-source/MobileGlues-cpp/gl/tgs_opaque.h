// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
#pragma once
#include <GLES3/gl32.h>
namespace tgs {
template<class Driver> bool opaque_default_framebuffer(Driver& d) {
    if(!d.glColorMaski || !d.glGetBooleani_v) return false;
    GLint draw=0; GLboolean mask[4];
    d.glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING,&draw);
    d.glGetBooleani_v(GL_COLOR_WRITEMASK,0,mask);
    const bool scissor=d.glIsEnabled(GL_SCISSOR_TEST);
    const bool discard=d.glIsEnabled(GL_RASTERIZER_DISCARD);
    if(draw != 0)d.glBindFramebuffer(GL_DRAW_FRAMEBUFFER,0);
    if(scissor)d.glDisable(GL_SCISSOR_TEST);
    if(discard)d.glDisable(GL_RASTERIZER_DISCARD);
    const bool change_mask=mask[0] || mask[1] || mask[2] || !mask[3];
    if(change_mask)d.glColorMaski(0,GL_FALSE,GL_FALSE,GL_FALSE,GL_TRUE);
    if(d.glClearBufferfv) {
        const GLfloat opaque[4]={0,0,0,1};
        d.glClearBufferfv(GL_COLOR,0,opaque);
    } else {
        // Preserve the working 0.5 path when the entry point is unavailable.
        GLfloat clear[4]; d.glGetFloatv(GL_COLOR_CLEAR_VALUE,clear);
        d.glClearColor(0,0,0,1);
        d.glClear(GL_COLOR_BUFFER_BIT);
        d.glClearColor(clear[0],clear[1],clear[2],clear[3]);
    }
    if(change_mask)d.glColorMaski(0,mask[0],mask[1],mask[2],mask[3]);
    if(discard)d.glEnable(GL_RASTERIZER_DISCARD);
    if(scissor)d.glEnable(GL_SCISSOR_TEST);
    if(draw != 0)d.glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw);
    return true;
}
}
