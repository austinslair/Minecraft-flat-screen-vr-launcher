// TGS experimental additions, 2026. SPDX-License-Identifier: LGPL-2.1-only
#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl32.h>
#include "../../MobileGlues-cpp/gl/tgs_stream.h"
#include <dlfcn.h>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstdlib>
#include <cstring>
#include "../../MobileGlues-cpp/gl/tgs_swizzle.h"

template<class T> T symbol(void* lib,const char* name) {
    auto p=reinterpret_cast<T>(dlsym(lib,name));
    if(!p) throw std::runtime_error(std::string("Missing export: ")+name);
    return p;
}
#define LOAD(name) auto name=symbol<decltype(&::name)>(lib,#name)
static std::string run(const char* directory) {
    for(size_t count=0;count<130;++count) for(size_t offset=0;offset<16;++offset) {
        std::vector<uint8_t> src(count*4+32),out(count*4+32,0xCD),expected(out);
        for(size_t i=0;i<src.size();++i) src[i]=static_cast<uint8_t>(i*67+count);
        for(size_t i=0;i<count;++i) for(int c=0;c<4;++c)
            expected[offset+4*i+c]=src[offset+4*i+(c==0?2:c==2?0:c)];
        tgs::bgra_to_rgba(src.data()+offset,out.data()+offset,count);
        if(out!=expected) throw std::runtime_error("NEON pixel check failed");
    }
    const std::string path=std::string(directory)+"/libmobileglues.so";
    void* lib=dlopen(path.c_str(),RTLD_NOW|RTLD_LOCAL);
    if(!lib) throw std::runtime_error(dlerror());
    LOAD(eglGetDisplay); LOAD(eglInitialize); LOAD(eglBindAPI); LOAD(eglChooseConfig);
    LOAD(eglCreateContext); LOAD(eglCreatePbufferSurface); LOAD(eglMakeCurrent);
    LOAD(eglDestroyContext); LOAD(eglDestroySurface); LOAD(eglTerminate); LOAD(eglGetError);
    EGLDisplay display=eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLSurface surface=EGL_NO_SURFACE; EGLContext context=EGL_NO_CONTEXT;
    auto cleanup=[&]{ eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
        if(surface!=EGL_NO_SURFACE) eglDestroySurface(display,surface);
        if(context!=EGL_NO_CONTEXT) eglDestroyContext(display,context);
        eglTerminate(display); };
    auto need=[&](bool ok,const char* stage){if(!ok) throw std::runtime_error(std::string(stage)+" failed; EGL error "+std::to_string(eglGetError()));};
    try {
        need(eglInitialize(display,nullptr,nullptr),"eglInitialize");
        need(eglBindAPI(EGL_OPENGL_API),"desktop API binding");
        EGLint attributes[]={EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE};
        EGLConfig config; EGLint count=0;
        need(eglChooseConfig(display,attributes,&config,1,&count)&&count>0,"config selection");
        EGLint contextAttributes[]={EGL_CONTEXT_MAJOR_VERSION,3,EGL_CONTEXT_MINOR_VERSION,2,EGL_CONTEXT_OPENGL_PROFILE_MASK,EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT,EGL_NONE};
        context=eglCreateContext(display,config,EGL_NO_CONTEXT,contextAttributes);
        need(context!=EGL_NO_CONTEXT,"context creation");
        EGLint pbuffer[]={EGL_WIDTH,16,EGL_HEIGHT,16,EGL_NONE};
        surface=eglCreatePbufferSurface(display,config,pbuffer);
        need(surface!=EGL_NO_SURFACE,"surface creation");
        need(eglMakeCurrent(display,surface,surface,context),"make current");
        LOAD(glGetString); LOAD(glCreateShader); LOAD(glShaderSource); LOAD(glCompileShader);
        LOAD(glGetShaderiv); LOAD(glGetShaderInfoLog); LOAD(glCreateProgram); LOAD(glAttachShader);
        LOAD(glLinkProgram); LOAD(glGetProgramiv); LOAD(glGetProgramInfoLog); LOAD(glUseProgram);
        LOAD(glGenVertexArrays); LOAD(glBindVertexArray); LOAD(glViewport); LOAD(glClearColor); LOAD(glClear);
        LOAD(glGenTextures); LOAD(glBindTexture); LOAD(glTexParameteri); LOAD(glPixelStorei); LOAD(glTexImage2D);
        LOAD(glDrawArrays); LOAD(glReadPixels); LOAD(glGetError);
        auto compile=[&](GLenum type,const char* source){
            GLuint s=glCreateShader(type); const char* newline=std::strchr(source,'\n');
            const GLint prefix=newline ? GLint(newline-source+1) : GLint(std::strlen(source));
            const char* parts[]={source,source+prefix}; const GLint lengths[]={prefix,-1};
            glShaderSource(s,2,parts,lengths); glCompileShader(s);
            GLint ok=0;glGetShaderiv(s,GL_COMPILE_STATUS,&ok);
            if(!ok){char log[2048]={}; glGetShaderInfoLog(s,sizeof(log),nullptr,log);throw std::runtime_error(std::string("Shader: ")+log);}return s;};
        GLuint vertex=compile(GL_VERTEX_SHADER,"#version 150\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0.0,1.0);}");
        GLuint fragment=compile(GL_FRAGMENT_SHADER,"#version 150\nuniform sampler2D image;out vec4 color;void main(){color=texture(image,vec2(0.5));}");
        GLuint program=glCreateProgram();glAttachShader(program,vertex);glAttachShader(program,fragment);glLinkProgram(program);
        GLint ok=0;glGetProgramiv(program,GL_LINK_STATUS,&ok);
        if(!ok){char log[2048]={};glGetProgramInfoLog(program,sizeof(log),nullptr,log);throw std::runtime_error(std::string("Link: ")+log);}
        glUseProgram(program);GLuint vao;glGenVertexArrays(1,&vao);glBindVertexArray(vao);
        GLuint texture;glGenTextures(1,&texture);glBindTexture(GL_TEXTURE_2D,texture);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glPixelStorei(GL_UNPACK_ALIGNMENT,1);
        // Odd width exercises vector blocks and a scalar tail on every row.
        uint8_t bgra[7*5*4];for(int i=0;i<35;++i){bgra[4*i]=204;bgra[4*i+1]=153;bgra[4*i+2]=51;bgra[4*i+3]=255;}
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,7,5,0,0x80E1,GL_UNSIGNED_BYTE,bgra);
        glViewport(0,0,16,16);glClearColor(1,0,0,1);glClear(GL_COLOR_BUFFER_BIT);glDrawArrays(GL_TRIANGLES,0,3);
        uint8_t pixel[4]={};glReadPixels(8,8,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
        if(std::abs(int(pixel[0])-51)>2||std::abs(int(pixel[1])-153)>2||std::abs(int(pixel[2])-204)>2||pixel[3]<253)
            throw std::runtime_error("Texture draw mismatch: "+std::to_string(pixel[0])+","+std::to_string(pixel[1])+","+std::to_string(pixel[2])+","+std::to_string(pixel[3]));
        LOAD(glBindAttribLocation);LOAD(glGetAttribLocation);LOAD(glVertexAttrib4f);
        GLuint v2=compile(GL_VERTEX_SHADER,"#version 150\nin vec4 Tint;out vec4 first;out vec4 second;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0.0,1.0);first=Tint;second=vec4(0.6,0.2,0.4,1.0);}");
        GLuint f2=compile(GL_FRAGMENT_SHADER,"#version 150\nin vec4 second;in vec4 first;out vec4 color;void main(){color=first*0.75+second*0.25;}");
        GLuint p2=glCreateProgram();glAttachShader(p2,v2);glAttachShader(p2,f2);glBindAttribLocation(p2,5,"Tint");glLinkProgram(p2);
        glGetProgramiv(p2,GL_LINK_STATUS,&ok);if(!ok)throw std::runtime_error("Named interface link failed");
        if(glGetAttribLocation(p2,"Tint")!=5)throw std::runtime_error("Vertex slot 5 not preserved");
        glUseProgram(p2);glVertexAttrib4f(5,.2f,.6f,.8f,1);glDrawArrays(GL_TRIANGLES,0,3);glReadPixels(8,8,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
        if(std::abs(int(pixel[0])-77)>2||std::abs(int(pixel[1])-128)>2||std::abs(int(pixel[2])-179)>2||pixel[3]<253)throw std::runtime_error("Named varying pixel mismatch");
        LOAD(glGenBuffers);LOAD(glBindBuffer);LOAD(glBufferData);LOAD(glBufferSubData);
        LOAD(glMapBufferRange);LOAD(glUnmapBuffer);LOAD(glDrawArraysIndirect);
        struct Command{GLuint count,instances,first,reserved;};
        struct UploadDriver{decltype(glMapBufferRange) glMapBufferRange;decltype(glUnmapBuffer) glUnmapBuffer;decltype(glBufferSubData) glBufferSubData;};
        UploadDriver driver{glMapBufferRange,glUnmapBuffer,glBufferSubData};GLuint commands;glGenBuffers(1,&commands);glBindBuffer(GL_DRAW_INDIRECT_BUFFER,commands);glBufferData(GL_DRAW_INDIRECT_BUFFER,sizeof(Command),nullptr,GL_STREAM_DRAW);
        tgs::upload_commands<Command>(driver,1,tgs::stream_uploads_enabled(),[](Command* c){*c={3,1,0,0};});
        glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);glDrawArraysIndirect(GL_TRIANGLES,nullptr);glReadPixels(8,8,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
        if(std::abs(int(pixel[0])-77)>2||std::abs(int(pixel[1])-128)>2||std::abs(int(pixel[2])-179)>2)throw std::runtime_error("Indirect streaming draw mismatch");
        GLenum error=glGetError();if(error!=GL_NO_ERROR)throw std::runtime_error("GL error: "+std::to_string(error));
        const char* renderer=reinterpret_cast<const char*>(glGetString(GL_RENDERER));
        const char* version=reinterpret_cast<const char*>(glGetString(GL_VERSION));
        std::string result="PASS: ARM64 conversion checks\nPASS: EGL desktop context\nPASS: GLSL 150 split sources, vertex slot 5 and named varyings\nPASS: BGRA upload, textured draw, indirect streaming and pixel readback\n\nRenderer: "+std::string(renderer?renderer:"unknown")+"\nGL: "+std::string(version?version:"unknown");
        cleanup(); return result;
    } catch(...) {cleanup();throw;}
}
extern "C" JNIEXPORT jstring JNICALL Java_dev_tgs_renderer_DiagnosticActivity_check(JNIEnv* env,jclass,jstring directory){
    const char* raw=env->GetStringUTFChars(directory,nullptr);if(!raw)return nullptr;
    std::string path(raw);env->ReleaseStringUTFChars(directory,raw);
    try{return env->NewStringUTF(run(path.c_str()).c_str());}
    catch(const std::exception& e){return env->NewStringUTF((std::string("FAIL: ")+e.what()).c_str());}
}
