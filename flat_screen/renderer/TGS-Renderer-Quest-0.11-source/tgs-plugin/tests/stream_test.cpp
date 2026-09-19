// SPDX-License-Identifier: LGPL-2.1-only
#include "../../MobileGlues-cpp/gl/tgs_stream.h"
#include "../../MobileGlues-cpp/gl/tgs_matrix.h"
#include <cassert>
#include <cstdio>
#include <array>
static std::array<unsigned,8192> memory;
static bool map_ok,unmap_ok;static int maps,unmaps,submits;
struct Driver{
 void* (*glMapBufferRange)(GLenum,GLintptr,GLsizeiptr,GLbitfield)=[](GLenum t,GLintptr o,GLsizeiptr n,GLbitfield flags)->void*{assert(t==GL_DRAW_INDIRECT_BUFFER&&o==0&&n<=GLsizeiptr(memory.size()*4)&&flags==(GL_MAP_WRITE_BIT|GL_MAP_INVALIDATE_BUFFER_BIT));++maps;memory.fill(0xdeadbeef);return map_ok?memory.data():nullptr;};
 GLboolean (*glUnmapBuffer)(GLenum)=[](GLenum t)->GLboolean{assert(t==GL_DRAW_INDIRECT_BUFFER);++unmaps;if(!unmap_ok)memory.fill(0xdeadbeef);return unmap_ok;};
 void glBufferSubData(GLenum t,GLintptr o,GLsizeiptr n,const void* p){assert(t==GL_DRAW_INDIRECT_BUFFER&&o==0);++submits;std::memcpy(memory.data(),p,n);}
};
template<int C,int R> void matrix(){
 std::array<float,C*R*3> input;for(size_t i=0;i<input.size();++i)input[i]=float(i)+.25f;
 tgs::matrix_upload<C,R>(4,3,GL_TRUE,input.data(),[&](GLboolean tr,const float* out){assert(!tr);for(int n=0;n<3;++n)for(int c=0;c<C;++c)for(int r=0;r<R;++r)assert(out[n*C*R+c*R+r]==input[n*C*R+r*C+c]);});
 tgs::matrix_upload<C,R>(4,3,GL_FALSE,input.data(),[&](GLboolean tr,const float* out){assert(!tr&&out==input.data());});
}
int main(){int cases=0;
 for(bool enabled:{false,true})for(bool missing:{false,true})for(bool map:{false,true})for(bool unmap:{false,true})for(size_t n:{1,16,4096}){
  Driver d;if(missing)d.glMapBufferRange=nullptr;maps=unmaps=submits=0;map_ok=map;unmap_ok=unmap;
  tgs::upload_commands<unsigned>(d,n,enabled,[&](unsigned* out){for(size_t i=0;i<n;++i)out[i]=unsigned(i*71+n);});
  for(size_t i=0;i<n;++i)assert(memory[i]==unsigned(i*71+n));
  bool direct=enabled&&!missing&&map&&unmap;assert(submits==(direct?0:1));assert(unmaps==(enabled&&!missing&&map?1:0));++cases;
 }
 matrix<2,2>();matrix<3,3>();matrix<4,4>();matrix<2,3>();matrix<3,2>();matrix<2,4>();matrix<4,2>();matrix<3,4>();matrix<4,3>();
 printf("PASS: %d streaming/failure cases, nine matrix shapes with arrays and unchanged direct uploads\n",cases);
}
