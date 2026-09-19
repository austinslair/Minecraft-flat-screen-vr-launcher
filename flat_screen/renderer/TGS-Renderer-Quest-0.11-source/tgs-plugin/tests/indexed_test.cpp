// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
#include "../../MobileGlues-cpp/gl/tgs_indexed.h"
#include <cassert>
#include <cstdio>
#include <vector>
#include <array>
struct Draw{GLsizei count;GLenum type;const void* offset;GLint base;};
static std::vector<Draw> draws;static bool restart=false;static int toggles=0;
struct Driver{
 void (*glDrawElements)(GLenum,GLsizei,GLenum,const void*)=[](GLenum,GLsizei c,GLenum t,const void* o){draws.push_back({c,t,o,0});};
 void (*glDrawElementsBaseVertex)(GLenum,GLsizei,GLenum,const void*,GLint)=[](GLenum,GLsizei c,GLenum t,const void* o,GLint b){draws.push_back({c,t,o,b});};
 void glEnable(GLenum e){assert(e==GL_PRIMITIVE_RESTART_FIXED_INDEX);restart=true;++toggles;}
 void glDisable(GLenum e){assert(e==GL_PRIMITIVE_RESTART_FIXED_INDEX);restart=false;++toggles;}
};
int main(){
 int cases=0;
 for(bool bound:{false,true})for(bool caps:{false,true})for(bool pointer:{false,true})for(bool rewrite:{false,true})for(bool force:{false,true})for(bool basesPresent:{false,true})for(int base:{0,65536,-3})for(GLenum type:{GL_UNSIGNED_BYTE,GL_UNSIGNED_SHORT,GL_UNSIGNED_INT}){
  Driver d;if(!pointer)d.glDrawElementsBaseVertex=nullptr;
  const GLsizei counts[]={3,0,6};const void* offsets[]={nullptr,reinterpret_cast<void*>(4),reinterpret_cast<void*>(12)};const GLint bases[]={0,777,base};
  draws.clear();restart=false;toggles=0;
  const bool expected=bound&&!rewrite&&(!basesPresent||base==0||(caps&&pointer));
  assert(tgs::native_indexed_batch(d,bound?7:0,caps,rewrite,force,GL_TRIANGLES,counts,type,offsets,3,basesPresent?bases:nullptr)==expected);
  assert(!restart);assert(toggles==(expected&&force?2:0));
  assert(draws.size()==(expected?2:0));
  if(expected){assert(draws[0].count==3&&draws[0].base==0&&draws[0].offset==nullptr);assert(draws[1].count==6&&draws[1].offset==offsets[2]&&draws[1].base==(basesPresent?base:0)&&draws[1].type==type);}
  ++cases;
 }
 Driver d;const GLsizei bad[]={3,-1};const void* offsets[]={nullptr,nullptr};draws.clear();
 assert(!tgs::native_indexed_batch(d,7,true,false,false,GL_TRIANGLES,bad,GL_UNSIGNED_SHORT,offsets,2,nullptr));assert(draws.empty());
 printf("PASS: %d indexed dispatch combinations, restart restoration, no partial draws on fallback, offsets and signed/large bases preserved\n",cases);
}
