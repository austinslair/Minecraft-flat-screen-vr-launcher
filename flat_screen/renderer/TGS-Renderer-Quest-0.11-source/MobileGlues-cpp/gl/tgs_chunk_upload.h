// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
#pragma once
#include <GLES3/gl32.h>
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <array>
#include <chrono>
namespace tgs {
inline bool chunk_staging_enabled(){static const bool on=[](){const char* s=std::getenv("TGS_CHUNK_STAGING");return s&&std::strcmp(s,"1")==0;}();return on;}
struct ChunkUploads {uint64_t staged=0,bytes=0,direct=0;};
inline thread_local ChunkUploads chunk_uploads;
// Bounded, context-owned route state. Try four staged updates only after a
// >=0.5 ms direct update; abandon immediately if staging is slower than that
// reference. Then probe direct again. These are CPU call times, not GPU times.
struct UploadRoute {
    uint64_t direct_ns=0;unsigned trials=0;
    bool use_staging() const{return trials>0;}
    void direct(uint64_t ns){direct_ns=ns;trials=ns>=500000?4:0;}
    void staged(uint64_t ns,bool ok){if(!ok||ns>direct_ns)trials=0;else if(trials)--trials;}
};
inline int chunk_bucket(GLenum target,GLsizeiptr size){
    if(size<65536||size>16*1024*1024)return -1;
    int t=target==GL_ARRAY_BUFFER?0:target==GL_ELEMENT_ARRAY_BUFFER?1:target==GL_COPY_WRITE_BUFFER?2:-1;
    if(t<0)return -1;
    unsigned b=0;for(GLsizeiptr n=size/65536;n>1;n>>=1)++b;
    return t*9+int(b);
}
// Large mutable, unmapped geometry updates only. Preserve the destination object,
// allocation, untouched bytes, VAO references and all binding state. A temporary
// source has no outstanding GPU readers; copy ordering is handled by GLES.
// Deletion after the copy queues releases the name, not its in-flight contents.
template<class D> bool stage_chunk_upload(D& d,GLenum target,GLintptr offset,GLsizeiptr size,const void* data,bool storage_ext){
 GLenum binding;
 switch(target){case GL_ARRAY_BUFFER:binding=GL_ARRAY_BUFFER_BINDING;break;
 case GL_ELEMENT_ARRAY_BUFFER:binding=GL_ELEMENT_ARRAY_BUFFER_BINDING;break;
 case GL_COPY_WRITE_BUFFER:binding=GL_COPY_WRITE_BUFFER_BINDING;break;default:return false;}
 if(!data||offset<0||size<65536||size>16*1024*1024)return false;
 if(!d.glCopyBufferSubData||!d.glGetBufferParameteri64v||!d.glGenBuffers||!d.glDeleteBuffers)return false;
 GLint bound=0,active=0,mapped=0;d.glGetIntegerv(binding,&bound);if(!bound)return false;
 d.glGetIntegerv(GL_TRANSFORM_FEEDBACK_ACTIVE,&active);if(active)return false;
 d.glGetBufferParameteriv(target,GL_BUFFER_MAPPED,&mapped);if(mapped)return false;
 if(storage_ext){GLint immutable=0;d.glGetBufferParameteriv(target,0x821f /* GL_BUFFER_IMMUTABLE_STORAGE_EXT */,&immutable);if(immutable)return false;}
 GLint64 capacity=0;d.glGetBufferParameteri64v(target,GL_BUFFER_SIZE,&capacity);
 if(capacity<0||offset>capacity||size>capacity-offset)return false;
 GLint previous=0;d.glGetIntegerv(GL_COPY_READ_BUFFER_BINDING,&previous);
 GLuint scratch=0;d.glGenBuffers(1,&scratch);if(!scratch)return false;
 d.glBindBuffer(GL_COPY_READ_BUFFER,scratch);
 d.glBufferData(GL_COPY_READ_BUFFER,size,data,GL_STREAM_DRAW);
 GLint allocated=0;d.glGetBufferParameteriv(GL_COPY_READ_BUFFER,GL_BUFFER_SIZE,&allocated);
 const bool ready=allocated==size;
 if(ready)d.glCopyBufferSubData(GL_COPY_READ_BUFFER,target,0,offset,size);
 d.glBindBuffer(GL_COPY_READ_BUFFER,static_cast<GLuint>(previous));
 d.glDeleteBuffers(1,&scratch);
 return ready;
}
}
