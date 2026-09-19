// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
#include "../../MobileGlues-cpp/gl/tgs_opaque.h"
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <dlfcn.h>
#include <cassert>
#include <cstdio>
#include <array>
#include <string>
#include "../../MobileGlues-cpp/gl/tgs_stream.h"
#include "../../MobileGlues-cpp/gl/tgs_matrix.h"
#include <vector>
#include <chrono>
#include "../../MobileGlues-cpp/gl/tgs_chunk_upload.h"
#include "../../MobileGlues-cpp/gl/tgs_indexed.h"
#define FN(name) decltype(&::name) name
struct Driver {
 FN(glCopyBufferSubData);FN(glDeleteBuffers);FN(glGetBufferParameteriv);FN(glGetBufferParameteri64v);FN(glFinish);
 FN(glMapBufferRange);FN(glUnmapBuffer);FN(glBufferSubData);FN(glDrawArraysIndirect);FN(glGetUniformLocation);FN(glGetUniformfv);FN(glUniformMatrix2fv);FN(glProgramUniformMatrix2fv);FN(glUniformMatrix3fv);FN(glProgramUniformMatrix3fv);FN(glUniformMatrix4fv);FN(glProgramUniformMatrix4fv);FN(glUniformMatrix2x3fv);FN(glProgramUniformMatrix2x3fv);FN(glUniformMatrix3x2fv);FN(glProgramUniformMatrix3x2fv);FN(glUniformMatrix2x4fv);FN(glProgramUniformMatrix2x4fv);FN(glUniformMatrix4x2fv);FN(glProgramUniformMatrix4x2fv);FN(glUniformMatrix3x4fv);FN(glProgramUniformMatrix3x4fv);FN(glUniformMatrix4x3fv);FN(glProgramUniformMatrix4x3fv);
 FN(glGenBuffers);FN(glBindBuffer);FN(glBufferData);FN(glCreateShader);FN(glShaderSource);FN(glCompileShader);FN(glGetShaderiv);FN(glCreateProgram);FN(glAttachShader);FN(glLinkProgram);FN(glGetProgramiv);FN(glUseProgram);FN(glGenVertexArrays);FN(glBindVertexArray);FN(glDrawElements);FN(glDrawElementsBaseVertex);FN(glViewport);
 FN(glColorMaski);FN(glGetBooleani_v);FN(glGetIntegerv);FN(glGetFloatv);FN(glIsEnabled);
 FN(glBindFramebuffer);FN(glDisable);FN(glEnable);FN(glClearColor);FN(glClear);FN(glClearBufferfv);
 FN(glReadPixels);FN(glGetError);FN(glGetString);FN(glScissor);FN(glGenFramebuffers);
 FN(glGenTextures);FN(glBindTexture);FN(glTexImage2D);FN(glFramebufferTexture2D);FN(glCheckFramebufferStatus);
};
int main(){
 void* lib=dlopen("libEGL.so.1",RTLD_NOW|RTLD_LOCAL);assert(lib);
 #define E(name) auto name=reinterpret_cast<decltype(&::name)>(dlsym(lib,#name));assert(name)
 E(eglGetProcAddress);E(eglInitialize);E(eglBindAPI);E(eglChooseConfig);E(eglCreateContext);E(eglCreatePbufferSurface);E(eglMakeCurrent);E(eglDestroySurface);E(eglDestroyContext);E(eglTerminate);
 auto platform=reinterpret_cast<PFNEGLGETPLATFORMDISPLAYEXTPROC>(eglGetProcAddress("eglGetPlatformDisplayEXT"));assert(platform);
 EGLDisplay display=platform(EGL_PLATFORM_SURFACELESS_MESA,nullptr,nullptr);assert(eglInitialize(display,nullptr,nullptr));assert(eglBindAPI(EGL_OPENGL_ES_API));
 EGLint ca[]={EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES3_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE};EGLConfig config;EGLint count;assert(eglChooseConfig(display,ca,&config,1,&count)&&count==1);
 EGLint ctxa[]={EGL_CONTEXT_MAJOR_VERSION,3,EGL_CONTEXT_MINOR_VERSION,2,EGL_NONE};auto ctx=eglCreateContext(display,config,EGL_NO_CONTEXT,ctxa);assert(ctx!=EGL_NO_CONTEXT);
 EGLint sa[]={EGL_WIDTH,16,EGL_HEIGHT,16,EGL_NONE};auto surface=eglCreatePbufferSurface(display,config,sa);assert(surface!=EGL_NO_SURFACE);assert(eglMakeCurrent(display,surface,surface,ctx));
 Driver d{};
 #define L(name) d.name=reinterpret_cast<decltype(d.name)>(eglGetProcAddress(#name));assert(d.name)
 L(glColorMaski);L(glGetBooleani_v);L(glGetIntegerv);L(glGetFloatv);L(glIsEnabled);L(glBindFramebuffer);L(glDisable);L(glEnable);L(glClearColor);L(glClear);L(glClearBufferfv);L(glReadPixels);L(glGetError);L(glGetString);L(glScissor);L(glGenFramebuffers);L(glGenTextures);L(glBindTexture);L(glTexImage2D);L(glFramebufferTexture2D);L(glCheckFramebufferStatus);
 L(glGenBuffers);L(glBindBuffer);L(glBufferData);L(glCreateShader);L(glShaderSource);L(glCompileShader);L(glGetShaderiv);L(glCreateProgram);L(glAttachShader);L(glLinkProgram);L(glGetProgramiv);L(glUseProgram);L(glGenVertexArrays);L(glBindVertexArray);L(glDrawElements);L(glDrawElementsBaseVertex);L(glViewport);
 L(glMapBufferRange);L(glUnmapBuffer);L(glBufferSubData);L(glDrawArraysIndirect);L(glGetUniformLocation);L(glGetUniformfv);L(glUniformMatrix2fv);L(glProgramUniformMatrix2fv);L(glUniformMatrix3fv);L(glProgramUniformMatrix3fv);L(glUniformMatrix4fv);L(glProgramUniformMatrix4fv);L(glUniformMatrix2x3fv);L(glProgramUniformMatrix2x3fv);L(glUniformMatrix3x2fv);L(glProgramUniformMatrix3x2fv);L(glUniformMatrix2x4fv);L(glProgramUniformMatrix2x4fv);L(glUniformMatrix4x2fv);L(glProgramUniformMatrix4x2fv);L(glUniformMatrix3x4fv);L(glProgramUniformMatrix3x4fv);L(glUniformMatrix4x3fv);L(glProgramUniformMatrix4x3fv);
 L(glCopyBufferSubData);L(glDeleteBuffers);L(glGetBufferParameteriv);L(glGetBufferParameteri64v);L(glFinish);
 printf("Driver: %s / %s\n",d.glGetString(GL_RENDERER),d.glGetString(GL_VERSION));
 GLuint fbo,tex;d.glGenFramebuffers(1,&fbo);d.glGenTextures(1,&tex);d.glBindTexture(GL_TEXTURE_2D,tex);d.glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,16,16,0,GL_RGBA,GL_UNSIGNED_BYTE,nullptr);d.glBindFramebuffer(GL_FRAMEBUFFER,fbo);d.glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,tex,0);assert(d.glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE);
 auto clearBuffer=d.glClearBufferfv;int cases=0;
 for(bool fallback:{false,true})for(bool bound:{false,true})for(bool scissor:{false,true})for(bool discard:{false,true})for(int bits=0;bits<16;++bits){
  d.glClearBufferfv=clearBuffer;d.glDisable(GL_SCISSOR_TEST);d.glDisable(GL_RASTERIZER_DISCARD);d.glColorMaski(0,1,1,1,1);
  std::array<unsigned char,1024> beforeOff,afterOff,before,after;
  d.glBindFramebuffer(GL_FRAMEBUFFER,fbo);d.glClearColor(.8f,.1f,.3f,.2f);d.glClear(GL_COLOR_BUFFER_BIT);d.glReadPixels(0,0,16,16,GL_RGBA,GL_UNSIGNED_BYTE,beforeOff.data());
  d.glBindFramebuffer(GL_FRAMEBUFFER,0);d.glClearColor(.2f,.6f,.8f,.25f);d.glClear(GL_COLOR_BUFFER_BIT);d.glReadPixels(0,0,16,16,GL_RGBA,GL_UNSIGNED_BYTE,before.data());
  d.glBindFramebuffer(GL_DRAW_FRAMEBUFFER,bound?fbo:0);std::array<GLboolean,4> mask;for(int i=0;i<4;++i)mask[i]=(bits>>i)&1;
  d.glColorMaski(0,mask[0],mask[1],mask[2],mask[3]);d.glColorMaski(1,0,1,0,1);d.glClearColor(.3f,.4f,.5f,.6f);d.glScissor(1,1,2,2);
  if(scissor)d.glEnable(GL_SCISSOR_TEST);if(discard)d.glEnable(GL_RASTERIZER_DISCARD);if(fallback)d.glClearBufferfv=nullptr;
  assert(tgs::opaque_default_framebuffer(d));assert(d.glGetError()==GL_NO_ERROR);
  GLint draw;d.glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING,&draw);assert(draw==GLint(bound?fbo:0));
  std::array<GLboolean,4> actual;d.glGetBooleani_v(GL_COLOR_WRITEMASK,0,actual.data());assert(actual==mask);d.glGetBooleani_v(GL_COLOR_WRITEMASK,1,actual.data());assert((actual==std::array<GLboolean,4>{0,1,0,1}));
  GLfloat color[4];d.glGetFloatv(GL_COLOR_CLEAR_VALUE,color);assert(color[0]==.3f&&color[1]==.4f&&color[2]==.5f&&color[3]==.6f);assert(bool(d.glIsEnabled(GL_SCISSOR_TEST))==scissor&&bool(d.glIsEnabled(GL_RASTERIZER_DISCARD))==discard);
  d.glReadPixels(0,0,16,16,GL_RGBA,GL_UNSIGNED_BYTE,after.data());for(size_t i=0;i<after.size();++i)assert(after[i]==(i%4==3?255:before[i]));
  d.glBindFramebuffer(GL_READ_FRAMEBUFFER,fbo);d.glReadPixels(0,0,16,16,GL_RGBA,GL_UNSIGNED_BYTE,afterOff.data());assert(beforeOff==afterOff);++cases;
 }
 d.glBindFramebuffer(GL_FRAMEBUFFER,0);d.glDisable(GL_SCISSOR_TEST);d.glDisable(GL_RASTERIZER_DISCARD);d.glColorMaski(0,1,1,1,1);d.glViewport(0,0,16,16);
 auto compile=[&](GLenum type,const char* code){GLuint shader=d.glCreateShader(type);d.glShaderSource(shader,1,&code,nullptr);d.glCompileShader(shader);GLint ok;d.glGetShaderiv(shader,GL_COMPILE_STATUS,&ok);assert(ok);return shader;};
 GLuint vs=compile(GL_VERTEX_SHADER,"#version 320 es\nflat out vec4 tint;void main(){int k=gl_VertexID%3;vec2 p=k==0?vec2(-1,-1):k==1?vec2(3,-1):vec2(-1,3);gl_Position=vec4(p,0,1);tint=gl_VertexID>=65536?vec4(1,0,0,1):vec4(0,1,0,1);}");
 GLuint fs=compile(GL_FRAGMENT_SHADER,"#version 320 es\nprecision highp float;flat in vec4 tint;out vec4 color;void main(){color=tint;}");
 GLuint program=d.glCreateProgram();d.glAttachShader(program,vs);d.glAttachShader(program,fs);d.glLinkProgram(program);GLint linked;d.glGetProgramiv(program,GL_LINK_STATUS,&linked);assert(linked);d.glUseProgram(program);
 GLuint vao,ibo;d.glGenVertexArrays(1,&vao);d.glBindVertexArray(vao);d.glGenBuffers(1,&ibo);d.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ibo);

 // Real driver updates: compare every destination byte, including untouched guards.
 {
 GLuint destination,previous;d.glGenBuffers(1,&destination);d.glGenBuffers(1,&previous);
 d.glBindBuffer(GL_COPY_READ_BUFFER,previous);d.glBufferData(GL_COPY_READ_BUFFER,128,nullptr,GL_STATIC_DRAW);
 const bool storage_ext=std::strstr((const char*)d.glGetString(GL_EXTENSIONS),"GL_EXT_buffer_storage")!=nullptr;
 int updates=0;
 for(GLenum target:{GL_ARRAY_BUFFER,GL_ELEMENT_ARRAY_BUFFER,GL_COPY_WRITE_BUFFER})
 for(size_t bytes:{size_t(65536),size_t(262144),size_t(1048576)})
 for(size_t offset:{size_t(0),size_t(4096)}){
  std::vector<unsigned char> expected(bytes+8192,0xA7),data(bytes);
  for(size_t i=0;i<bytes;++i)data[i]=(i*37+offset/4096)&255;
  std::copy(data.begin(),data.end(),expected.begin()+offset);
  std::vector<unsigned char> initial(expected.size(),0xA7);
  d.glBindBuffer(target,destination);d.glBufferData(target,initial.size(),initial.data(),GL_DYNAMIC_DRAW);
  assert(tgs::stage_chunk_upload(d,target,offset,bytes,data.data(),storage_ext));
  GLint binding=0;d.glGetIntegerv(GL_COPY_READ_BUFFER_BINDING,&binding);assert(binding==GLint(previous));
  auto* read=(unsigned char*)d.glMapBufferRange(target,0,expected.size(),GL_MAP_READ_BIT);assert(read);
  assert(std::memcmp(read,expected.data(),expected.size())==0);
  assert(!tgs::stage_chunk_upload(d,target,0,bytes,data.data(),storage_ext)); // mapped: original path
  assert(d.glUnmapBuffer(target));
  assert(!tgs::stage_chunk_upload(d,target,initial.size(),bytes,data.data(),storage_ext));
  assert(!tgs::stage_chunk_upload(d,target,-1,bytes,data.data(),storage_ext));
  assert(!tgs::stage_chunk_upload(d,target,0,32,data.data(),storage_ext));
  assert(!tgs::stage_chunk_upload(d,target,0,bytes,nullptr,storage_ext));
  assert(d.glGetError()==GL_NO_ERROR);++updates;
 }
 // Repeated upload/draw sequence. Same pixels and bytes, timing is host-only.
 d.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,destination);
 std::vector<GLuint> indices(65536,0);for(size_t i=0;i<indices.size();++i)indices[i]=65536+i%3;
 std::array<unsigned char,1024> reference{},actual{};
 for(bool staged:{false,true,true,false}){
  d.glBufferData(GL_ELEMENT_ARRAY_BUFFER,indices.size()*4,indices.data(),GL_DYNAMIC_DRAW);
  for(int warm=0;warm<32;++warm){
   if(staged)assert(tgs::stage_chunk_upload(d,GL_ELEMENT_ARRAY_BUFFER,0,indices.size()*4,indices.data(),storage_ext));
   else d.glBufferSubData(GL_ELEMENT_ARRAY_BUFFER,0,indices.size()*4,indices.data());
   d.glDrawElements(GL_TRIANGLES,3,GL_UNSIGNED_INT,nullptr);
  }
  d.glFinish();auto start=std::chrono::steady_clock::now();
  for(int i=0;i<256;++i){
   if(staged)assert(tgs::stage_chunk_upload(d,GL_ELEMENT_ARRAY_BUFFER,0,indices.size()*4,indices.data(),storage_ext));
   else d.glBufferSubData(GL_ELEMENT_ARRAY_BUFFER,0,indices.size()*4,indices.data());
   d.glDrawElements(GL_TRIANGLES,3,GL_UNSIGNED_INT,nullptr);
  }
  d.glFinish();double ms=std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-start).count();
  d.glReadPixels(0,0,16,16,GL_RGBA,GL_UNSIGNED_BYTE,actual.data());
  assert(actual[0]==255);if(staged)assert(actual==reference);else reference=actual;
  assert(d.glGetError()==GL_NO_ERROR);printf("HOST upload/draw test %s: %.3f ms, not Quest FPS\n",staged?"staged":"direct",ms);
 }
 auto originalData=d.glBufferData;
 d.glBufferData=+[](GLenum,GLsizeiptr,const void*,GLenum){};
 assert(!tgs::stage_chunk_upload(d,GL_ELEMENT_ARRAY_BUFFER,0,indices.size()*4,indices.data(),storage_ext));
 d.glBufferData=originalData;
 GLint retained=0;d.glGetIntegerv(GL_COPY_READ_BUFFER_BINDING,&retained);assert(retained==GLint(previous));
 auto originalCopy=d.glCopyBufferSubData;d.glCopyBufferSubData=nullptr;
 assert(!tgs::stage_chunk_upload(d,GL_ELEMENT_ARRAY_BUFFER,0,indices.size()*4,indices.data(),storage_ext));d.glCopyBufferSubData=originalCopy;
 if(storage_ext){
  auto storage=reinterpret_cast<void(*)(GLenum,GLsizeiptr,const void*,GLbitfield)>(eglGetProcAddress("glBufferStorageEXT"));assert(storage);
  GLuint immutable;d.glGenBuffers(1,&immutable);d.glBindBuffer(GL_ARRAY_BUFFER,immutable);storage(GL_ARRAY_BUFFER,indices.size()*4,indices.data(),0);
  assert(!tgs::stage_chunk_upload(d,GL_ARRAY_BUFFER,0,indices.size()*4,indices.data(),true));d.glDeleteBuffers(1,&immutable);
 }
 assert(d.glGetError()==GL_NO_ERROR);
 d.glBindBuffer(GL_COPY_READ_BUFFER,0);d.glBindBuffer(GL_ARRAY_BUFFER,0);d.glBindBuffer(GL_COPY_WRITE_BUFFER,0);
 d.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ibo);d.glDeleteBuffers(1,&destination);d.glDeleteBuffers(1,&previous);
 printf("PASS: %d full-byte/guard/state checks, mapped/invalid bypasses, 1152 upload/draw iterations with matching pixels\n",updates);
 }
 int indexed_cases=0;
 for(GLenum type:{GL_UNSIGNED_BYTE,GL_UNSIGNED_SHORT,GL_UNSIGNED_INT})for(GLint base:{0,65536,-3})for(bool fixed:{false,true}){
  const int size=type==GL_UNSIGNED_BYTE?1:type==GL_UNSIGNED_SHORT?2:4;
  const GLuint sentinel=size==1?255:size==2?65535:0xffffffffu;
  const GLuint start=base<0?3:0;
  const std::vector<GLuint> raw={start,start+1,start+2,sentinel,start,start+1,start+2};
  std::vector<GLuint> widened;for(GLuint i:raw)widened.push_back(i==sentinel?0xffffffffu:i+base);
  std::array<unsigned char,1024> reference,output;
  const GLsizei count=fixed?7:3;
  d.glBufferData(GL_ELEMENT_ARRAY_BUFFER,widened.size()*4,widened.data(),GL_STATIC_DRAW);
  d.glClearColor(0,0,0,1);d.glClear(GL_COLOR_BUFFER_BIT);if(fixed)d.glEnable(GL_PRIMITIVE_RESTART_FIXED_INDEX);
  d.glDrawElements(GL_TRIANGLE_STRIP,count,GL_UNSIGNED_INT,nullptr);if(fixed)d.glDisable(GL_PRIMITIVE_RESTART_FIXED_INDEX);
  d.glReadPixels(0,0,16,16,GL_RGBA,GL_UNSIGNED_BYTE,reference.data());
  assert(reference[base>=65536?0:1]==255);
  std::vector<unsigned char> packed;for(GLuint v:raw)for(int j=0;j<size;++j)packed.push_back((v>>(j*8))&255);
  d.glBufferData(GL_ELEMENT_ARRAY_BUFFER,packed.size(),packed.data(),GL_STATIC_DRAW);d.glClear(GL_COLOR_BUFFER_BIT);
  const GLsizei counts[]={count,0};const void* offsets[]={nullptr,nullptr};const GLint bases[]={base,999};
  assert(tgs::native_indexed_batch(d,ibo,true,false,fixed,GL_TRIANGLE_STRIP,counts,type,offsets,2,bases));
  assert(!d.glIsEnabled(GL_PRIMITIVE_RESTART_FIXED_INDEX));
  d.glReadPixels(0,0,16,16,GL_RGBA,GL_UNSIGNED_BYTE,output.data());assert(output==reference);assert(d.glGetError()==GL_NO_ERROR);
  ++indexed_cases;
 }
 printf("PASS: %d real GLES indexed pixel comparisons against widened CPU reference (3 index widths, negative/large bases, restart on/off)\n",indexed_cases);

 struct Command{GLuint count,instances,first,reserved;};
 GLuint commandBuffer;d.glGenBuffers(1,&commandBuffer);d.glBindBuffer(GL_DRAW_INDIRECT_BUFFER,commandBuffer);d.glBufferData(GL_DRAW_INDIRECT_BUFFER,64*sizeof(Command),nullptr,GL_STREAM_DRAW);
 std::array<unsigned char,1024> reference08,stream08;
 for(bool enabled:{false,true}){
  for(int frame=0;frame<100;++frame){
   tgs::upload_commands<Command>(d,64,enabled,[&](Command* out){for(unsigned i=0;i<64;++i)out[i]={3,1,(frame%2)?65536u:0u,0};});
   for(size_t i=0;i<64;++i)d.glDrawArraysIndirect(GL_TRIANGLES,reinterpret_cast<void*>(i*sizeof(Command)));
  }
  d.glReadPixels(0,0,16,16,GL_RGBA,GL_UNSIGNED_BYTE,enabled?stream08.data():reference08.data());assert(d.glGetError()==GL_NO_ERROR);
 }
 assert(stream08==reference08&&stream08[0]==255);
 puts("PASS: 12800 indirect draws, streaming/reference pixels identical; no GL errors");

 {
 GLuint vm=compile(GL_VERTEX_SHADER,"#version 320 es\nuniform mat2 m[2];void main(){gl_Position=vec4((m[0][0][0]+m[0][0][1]+m[0][1][0]+m[0][1][1]+m[1][0][0]+m[1][0][1]+m[1][1][0]+m[1][1][1])*0.0001,0,0,1);}");
 GLuint fm=compile(GL_FRAGMENT_SHADER,"#version 320 es\nprecision highp float;out vec4 color;void main(){color=vec4(1);}");
 GLuint pm=d.glCreateProgram();d.glAttachShader(pm,vm);d.glAttachShader(pm,fm);d.glLinkProgram(pm);d.glGetProgramiv(pm,GL_LINK_STATUS,&linked);assert(linked);d.glUseProgram(pm);
 std::array<float,8> values;for(size_t i=0;i<values.size();++i)values[i]=float(i)+.25f;
 GLint loc=d.glGetUniformLocation(pm,"m[0]");assert(loc>=0);
 for(bool direct:{false,true}){
  tgs::matrix_upload<2,2>(loc,2,GL_TRUE,values.data(),[&](GLboolean tr,const float* v){if(direct)d.glProgramUniformMatrix2fv(pm,loc,2,tr,v);else d.glUniformMatrix2fv(loc,2,tr,v);});
  for(int n=0;n<2;++n){float actual[4];auto name="m["+std::to_string(n)+"]";d.glGetUniformfv(pm,d.glGetUniformLocation(pm,name.c_str()),actual);
   for(int c=0;c<2;++c)for(int r=0;r<2;++r)assert(actual[c*2+r]==values[n*4+r*2+c]);}
  assert(d.glGetError()==GL_NO_ERROR);
 }
 }

 {
 GLuint vm=compile(GL_VERTEX_SHADER,"#version 320 es\nuniform mat3 m[2];void main(){gl_Position=vec4((m[0][0][0]+m[0][0][1]+m[0][0][2]+m[0][1][0]+m[0][1][1]+m[0][1][2]+m[0][2][0]+m[0][2][1]+m[0][2][2]+m[1][0][0]+m[1][0][1]+m[1][0][2]+m[1][1][0]+m[1][1][1]+m[1][1][2]+m[1][2][0]+m[1][2][1]+m[1][2][2])*0.0001,0,0,1);}");
 GLuint fm=compile(GL_FRAGMENT_SHADER,"#version 320 es\nprecision highp float;out vec4 color;void main(){color=vec4(1);}");
 GLuint pm=d.glCreateProgram();d.glAttachShader(pm,vm);d.glAttachShader(pm,fm);d.glLinkProgram(pm);d.glGetProgramiv(pm,GL_LINK_STATUS,&linked);assert(linked);d.glUseProgram(pm);
 std::array<float,18> values;for(size_t i=0;i<values.size();++i)values[i]=float(i)+.25f;
 GLint loc=d.glGetUniformLocation(pm,"m[0]");assert(loc>=0);
 for(bool direct:{false,true}){
  tgs::matrix_upload<3,3>(loc,2,GL_TRUE,values.data(),[&](GLboolean tr,const float* v){if(direct)d.glProgramUniformMatrix3fv(pm,loc,2,tr,v);else d.glUniformMatrix3fv(loc,2,tr,v);});
  for(int n=0;n<2;++n){float actual[9];auto name="m["+std::to_string(n)+"]";d.glGetUniformfv(pm,d.glGetUniformLocation(pm,name.c_str()),actual);
   for(int c=0;c<3;++c)for(int r=0;r<3;++r)assert(actual[c*3+r]==values[n*9+r*3+c]);}
  assert(d.glGetError()==GL_NO_ERROR);
 }
 }

 {
 GLuint vm=compile(GL_VERTEX_SHADER,"#version 320 es\nuniform mat4 m[2];void main(){gl_Position=vec4((m[0][0][0]+m[0][0][1]+m[0][0][2]+m[0][0][3]+m[0][1][0]+m[0][1][1]+m[0][1][2]+m[0][1][3]+m[0][2][0]+m[0][2][1]+m[0][2][2]+m[0][2][3]+m[0][3][0]+m[0][3][1]+m[0][3][2]+m[0][3][3]+m[1][0][0]+m[1][0][1]+m[1][0][2]+m[1][0][3]+m[1][1][0]+m[1][1][1]+m[1][1][2]+m[1][1][3]+m[1][2][0]+m[1][2][1]+m[1][2][2]+m[1][2][3]+m[1][3][0]+m[1][3][1]+m[1][3][2]+m[1][3][3])*0.0001,0,0,1);}");
 GLuint fm=compile(GL_FRAGMENT_SHADER,"#version 320 es\nprecision highp float;out vec4 color;void main(){color=vec4(1);}");
 GLuint pm=d.glCreateProgram();d.glAttachShader(pm,vm);d.glAttachShader(pm,fm);d.glLinkProgram(pm);d.glGetProgramiv(pm,GL_LINK_STATUS,&linked);assert(linked);d.glUseProgram(pm);
 std::array<float,32> values;for(size_t i=0;i<values.size();++i)values[i]=float(i)+.25f;
 GLint loc=d.glGetUniformLocation(pm,"m[0]");assert(loc>=0);
 for(bool direct:{false,true}){
  tgs::matrix_upload<4,4>(loc,2,GL_TRUE,values.data(),[&](GLboolean tr,const float* v){if(direct)d.glProgramUniformMatrix4fv(pm,loc,2,tr,v);else d.glUniformMatrix4fv(loc,2,tr,v);});
  for(int n=0;n<2;++n){float actual[16];auto name="m["+std::to_string(n)+"]";d.glGetUniformfv(pm,d.glGetUniformLocation(pm,name.c_str()),actual);
   for(int c=0;c<4;++c)for(int r=0;r<4;++r)assert(actual[c*4+r]==values[n*16+r*4+c]);}
  assert(d.glGetError()==GL_NO_ERROR);
 }
 }

 {
 GLuint vm=compile(GL_VERTEX_SHADER,"#version 320 es\nuniform mat2x3 m[2];void main(){gl_Position=vec4((m[0][0][0]+m[0][0][1]+m[0][0][2]+m[0][1][0]+m[0][1][1]+m[0][1][2]+m[1][0][0]+m[1][0][1]+m[1][0][2]+m[1][1][0]+m[1][1][1]+m[1][1][2])*0.0001,0,0,1);}");
 GLuint fm=compile(GL_FRAGMENT_SHADER,"#version 320 es\nprecision highp float;out vec4 color;void main(){color=vec4(1);}");
 GLuint pm=d.glCreateProgram();d.glAttachShader(pm,vm);d.glAttachShader(pm,fm);d.glLinkProgram(pm);d.glGetProgramiv(pm,GL_LINK_STATUS,&linked);assert(linked);d.glUseProgram(pm);
 std::array<float,12> values;for(size_t i=0;i<values.size();++i)values[i]=float(i)+.25f;
 GLint loc=d.glGetUniformLocation(pm,"m[0]");assert(loc>=0);
 for(bool direct:{false,true}){
  tgs::matrix_upload<2,3>(loc,2,GL_TRUE,values.data(),[&](GLboolean tr,const float* v){if(direct)d.glProgramUniformMatrix2x3fv(pm,loc,2,tr,v);else d.glUniformMatrix2x3fv(loc,2,tr,v);});
  for(int n=0;n<2;++n){float actual[6];auto name="m["+std::to_string(n)+"]";d.glGetUniformfv(pm,d.glGetUniformLocation(pm,name.c_str()),actual);
   for(int c=0;c<2;++c)for(int r=0;r<3;++r)assert(actual[c*3+r]==values[n*6+r*2+c]);}
  assert(d.glGetError()==GL_NO_ERROR);
 }
 }

 {
 GLuint vm=compile(GL_VERTEX_SHADER,"#version 320 es\nuniform mat3x2 m[2];void main(){gl_Position=vec4((m[0][0][0]+m[0][0][1]+m[0][1][0]+m[0][1][1]+m[0][2][0]+m[0][2][1]+m[1][0][0]+m[1][0][1]+m[1][1][0]+m[1][1][1]+m[1][2][0]+m[1][2][1])*0.0001,0,0,1);}");
 GLuint fm=compile(GL_FRAGMENT_SHADER,"#version 320 es\nprecision highp float;out vec4 color;void main(){color=vec4(1);}");
 GLuint pm=d.glCreateProgram();d.glAttachShader(pm,vm);d.glAttachShader(pm,fm);d.glLinkProgram(pm);d.glGetProgramiv(pm,GL_LINK_STATUS,&linked);assert(linked);d.glUseProgram(pm);
 std::array<float,12> values;for(size_t i=0;i<values.size();++i)values[i]=float(i)+.25f;
 GLint loc=d.glGetUniformLocation(pm,"m[0]");assert(loc>=0);
 for(bool direct:{false,true}){
  tgs::matrix_upload<3,2>(loc,2,GL_TRUE,values.data(),[&](GLboolean tr,const float* v){if(direct)d.glProgramUniformMatrix3x2fv(pm,loc,2,tr,v);else d.glUniformMatrix3x2fv(loc,2,tr,v);});
  for(int n=0;n<2;++n){float actual[6];auto name="m["+std::to_string(n)+"]";d.glGetUniformfv(pm,d.glGetUniformLocation(pm,name.c_str()),actual);
   for(int c=0;c<3;++c)for(int r=0;r<2;++r)assert(actual[c*2+r]==values[n*6+r*3+c]);}
  assert(d.glGetError()==GL_NO_ERROR);
 }
 }

 {
 GLuint vm=compile(GL_VERTEX_SHADER,"#version 320 es\nuniform mat2x4 m[2];void main(){gl_Position=vec4((m[0][0][0]+m[0][0][1]+m[0][0][2]+m[0][0][3]+m[0][1][0]+m[0][1][1]+m[0][1][2]+m[0][1][3]+m[1][0][0]+m[1][0][1]+m[1][0][2]+m[1][0][3]+m[1][1][0]+m[1][1][1]+m[1][1][2]+m[1][1][3])*0.0001,0,0,1);}");
 GLuint fm=compile(GL_FRAGMENT_SHADER,"#version 320 es\nprecision highp float;out vec4 color;void main(){color=vec4(1);}");
 GLuint pm=d.glCreateProgram();d.glAttachShader(pm,vm);d.glAttachShader(pm,fm);d.glLinkProgram(pm);d.glGetProgramiv(pm,GL_LINK_STATUS,&linked);assert(linked);d.glUseProgram(pm);
 std::array<float,16> values;for(size_t i=0;i<values.size();++i)values[i]=float(i)+.25f;
 GLint loc=d.glGetUniformLocation(pm,"m[0]");assert(loc>=0);
 for(bool direct:{false,true}){
  tgs::matrix_upload<2,4>(loc,2,GL_TRUE,values.data(),[&](GLboolean tr,const float* v){if(direct)d.glProgramUniformMatrix2x4fv(pm,loc,2,tr,v);else d.glUniformMatrix2x4fv(loc,2,tr,v);});
  for(int n=0;n<2;++n){float actual[8];auto name="m["+std::to_string(n)+"]";d.glGetUniformfv(pm,d.glGetUniformLocation(pm,name.c_str()),actual);
   for(int c=0;c<2;++c)for(int r=0;r<4;++r)assert(actual[c*4+r]==values[n*8+r*2+c]);}
  assert(d.glGetError()==GL_NO_ERROR);
 }
 }

 {
 GLuint vm=compile(GL_VERTEX_SHADER,"#version 320 es\nuniform mat4x2 m[2];void main(){gl_Position=vec4((m[0][0][0]+m[0][0][1]+m[0][1][0]+m[0][1][1]+m[0][2][0]+m[0][2][1]+m[0][3][0]+m[0][3][1]+m[1][0][0]+m[1][0][1]+m[1][1][0]+m[1][1][1]+m[1][2][0]+m[1][2][1]+m[1][3][0]+m[1][3][1])*0.0001,0,0,1);}");
 GLuint fm=compile(GL_FRAGMENT_SHADER,"#version 320 es\nprecision highp float;out vec4 color;void main(){color=vec4(1);}");
 GLuint pm=d.glCreateProgram();d.glAttachShader(pm,vm);d.glAttachShader(pm,fm);d.glLinkProgram(pm);d.glGetProgramiv(pm,GL_LINK_STATUS,&linked);assert(linked);d.glUseProgram(pm);
 std::array<float,16> values;for(size_t i=0;i<values.size();++i)values[i]=float(i)+.25f;
 GLint loc=d.glGetUniformLocation(pm,"m[0]");assert(loc>=0);
 for(bool direct:{false,true}){
  tgs::matrix_upload<4,2>(loc,2,GL_TRUE,values.data(),[&](GLboolean tr,const float* v){if(direct)d.glProgramUniformMatrix4x2fv(pm,loc,2,tr,v);else d.glUniformMatrix4x2fv(loc,2,tr,v);});
  for(int n=0;n<2;++n){float actual[8];auto name="m["+std::to_string(n)+"]";d.glGetUniformfv(pm,d.glGetUniformLocation(pm,name.c_str()),actual);
   for(int c=0;c<4;++c)for(int r=0;r<2;++r)assert(actual[c*2+r]==values[n*8+r*4+c]);}
  assert(d.glGetError()==GL_NO_ERROR);
 }
 }

 {
 GLuint vm=compile(GL_VERTEX_SHADER,"#version 320 es\nuniform mat3x4 m[2];void main(){gl_Position=vec4((m[0][0][0]+m[0][0][1]+m[0][0][2]+m[0][0][3]+m[0][1][0]+m[0][1][1]+m[0][1][2]+m[0][1][3]+m[0][2][0]+m[0][2][1]+m[0][2][2]+m[0][2][3]+m[1][0][0]+m[1][0][1]+m[1][0][2]+m[1][0][3]+m[1][1][0]+m[1][1][1]+m[1][1][2]+m[1][1][3]+m[1][2][0]+m[1][2][1]+m[1][2][2]+m[1][2][3])*0.0001,0,0,1);}");
 GLuint fm=compile(GL_FRAGMENT_SHADER,"#version 320 es\nprecision highp float;out vec4 color;void main(){color=vec4(1);}");
 GLuint pm=d.glCreateProgram();d.glAttachShader(pm,vm);d.glAttachShader(pm,fm);d.glLinkProgram(pm);d.glGetProgramiv(pm,GL_LINK_STATUS,&linked);assert(linked);d.glUseProgram(pm);
 std::array<float,24> values;for(size_t i=0;i<values.size();++i)values[i]=float(i)+.25f;
 GLint loc=d.glGetUniformLocation(pm,"m[0]");assert(loc>=0);
 for(bool direct:{false,true}){
  tgs::matrix_upload<3,4>(loc,2,GL_TRUE,values.data(),[&](GLboolean tr,const float* v){if(direct)d.glProgramUniformMatrix3x4fv(pm,loc,2,tr,v);else d.glUniformMatrix3x4fv(loc,2,tr,v);});
  for(int n=0;n<2;++n){float actual[12];auto name="m["+std::to_string(n)+"]";d.glGetUniformfv(pm,d.glGetUniformLocation(pm,name.c_str()),actual);
   for(int c=0;c<3;++c)for(int r=0;r<4;++r)assert(actual[c*4+r]==values[n*12+r*3+c]);}
  assert(d.glGetError()==GL_NO_ERROR);
 }
 }

 {
 GLuint vm=compile(GL_VERTEX_SHADER,"#version 320 es\nuniform mat4x3 m[2];void main(){gl_Position=vec4((m[0][0][0]+m[0][0][1]+m[0][0][2]+m[0][1][0]+m[0][1][1]+m[0][1][2]+m[0][2][0]+m[0][2][1]+m[0][2][2]+m[0][3][0]+m[0][3][1]+m[0][3][2]+m[1][0][0]+m[1][0][1]+m[1][0][2]+m[1][1][0]+m[1][1][1]+m[1][1][2]+m[1][2][0]+m[1][2][1]+m[1][2][2]+m[1][3][0]+m[1][3][1]+m[1][3][2])*0.0001,0,0,1);}");
 GLuint fm=compile(GL_FRAGMENT_SHADER,"#version 320 es\nprecision highp float;out vec4 color;void main(){color=vec4(1);}");
 GLuint pm=d.glCreateProgram();d.glAttachShader(pm,vm);d.glAttachShader(pm,fm);d.glLinkProgram(pm);d.glGetProgramiv(pm,GL_LINK_STATUS,&linked);assert(linked);d.glUseProgram(pm);
 std::array<float,24> values;for(size_t i=0;i<values.size();++i)values[i]=float(i)+.25f;
 GLint loc=d.glGetUniformLocation(pm,"m[0]");assert(loc>=0);
 for(bool direct:{false,true}){
  tgs::matrix_upload<4,3>(loc,2,GL_TRUE,values.data(),[&](GLboolean tr,const float* v){if(direct)d.glProgramUniformMatrix4x3fv(pm,loc,2,tr,v);else d.glUniformMatrix4x3fv(loc,2,tr,v);});
  for(int n=0;n<2;++n){float actual[12];auto name="m["+std::to_string(n)+"]";d.glGetUniformfv(pm,d.glGetUniformLocation(pm,name.c_str()),actual);
   for(int c=0;c<4;++c)for(int r=0;r<3;++r)assert(actual[c*3+r]==values[n*12+r*4+c]);}
  assert(d.glGetError()==GL_NO_ERROR);
 }
 }
 puts("PASS: all 18 regular/direct matrix upload forms verified against actual GLES uniform values");
 d.glColorMaski=nullptr;assert(!tgs::opaque_default_framebuffer(d));
 assert(eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT));assert(eglDestroySurface(display,surface));assert(eglDestroyContext(display,ctx));assert(eglTerminate(display));
 printf("PASS: %d real GLES presentation combinations; exact RGB/alpha, state restoration, fallback and no GL errors\n",cases);
}
