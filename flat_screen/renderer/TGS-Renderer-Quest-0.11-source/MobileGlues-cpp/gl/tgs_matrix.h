// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
#pragma once
#include <GLES3/gl32.h>
#include <vector>
#include <cstring>
namespace tgs {
// GLES only accepts transpose=false. Desktop row-major input is transposed on
// CPU, preserving each float's bits, including rectangular matrices and arrays.
template<int Columns,int Rows,class Submit> void matrix_upload(GLint location,GLsizei count,
    GLboolean transpose,const GLfloat* value,Submit submit){
    if(location==-1){submit(GL_FALSE,value);return;}
    if(!transpose || count<=0 || !value){submit(transpose,value);return;}
    constexpr size_t elements=Columns*Rows;
    static thread_local std::vector<GLfloat> scratch;
    const size_t size=static_cast<size_t>(count)*elements;
    if(scratch.size()<size)scratch.resize(size);
    for(GLsizei n=0;n<count;++n)for(int c=0;c<Columns;++c)for(int r=0;r<Rows;++r)
        std::memcpy(&scratch[static_cast<size_t>(n)*elements+c*Rows+r],
                    &value[static_cast<size_t>(n)*elements+r*Columns+c],sizeof(GLfloat));
    submit(GL_FALSE,scratch.data());
    // Matrix arrays can be user-controlled. Do not retain arbitrarily large temporaries.
    if(scratch.capacity()>262144)std::vector<GLfloat>().swap(scratch);
}
}
