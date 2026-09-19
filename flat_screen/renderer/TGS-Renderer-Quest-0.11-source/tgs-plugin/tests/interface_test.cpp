// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
#include "../../MobileGlues-cpp/gl/glsl/tgs_interface.h"
#include <vector>
#include <cassert>
#include <cstdio>
#include <string>
static std::vector<unsigned> module(bool vertex){
 std::vector<unsigned> v={0x07230203,0x00010000,0,30,0};
 auto op=[&](unsigned code,std::initializer_list<unsigned> words){v.push_back(((words.size()+1)<<16)|code);v.insert(v.end(),words);};
 op(17,{1});op(14,{0,1});op(15,{vertex?0u:4u,10,0x6e69616d,0,6,7});if(!vertex)op(16,{10,7});
 op(5,{6,0x7475706e,0});op(5,{7,0x7074756f,0x7475});
 op(71,{6,30,5});op(71,{7,30,3});
 op(19,{1});op(33,{2,1});op(22,{3,32});op(23,{4,3,4});
 op(32,{5,1,4});op(32,{8,3,4});op(59,{5,6,1});op(59,{8,7,3});
 op(54,{1,10,0,2});op(248,{11});op(61,{4,12,6});op(62,{7,12});op(253,{});op(56,{});return v;
}
int main(){
 assert(tgs::legacy_named_interface("#version 150\nin vec4 Tint;",150));
 assert(!tgs::legacy_named_interface("layout(location=3) in vec4 Tint;",150));
 assert(!tgs::legacy_named_interface("#extension GL_ARB_separate_shader_objects : enable",150));
 assert(!tgs::legacy_named_interface("in vec4 Tint;",330));
 for(bool vertex:{false,true}){
  auto words=module(vertex);spvc_context ctx;assert(spvc_context_create(&ctx)==SPVC_SUCCESS);spvc_parsed_ir ir;assert(spvc_context_parse_spirv(ctx,words.data(),words.size(),&ir)==SPVC_SUCCESS);
  spvc_compiler compiler;assert(spvc_context_create_compiler(ctx,SPVC_BACKEND_GLSL,ir,SPVC_CAPTURE_MODE_TAKE_OWNERSHIP,&compiler)==SPVC_SUCCESS);
  spvc_compiler_options options;assert(spvc_compiler_create_compiler_options(compiler,&options)==SPVC_SUCCESS);
  assert(spvc_compiler_options_set_uint(options,SPVC_COMPILER_OPTION_GLSL_VERSION,320)==SPVC_SUCCESS);assert(spvc_compiler_options_set_bool(options,SPVC_COMPILER_OPTION_GLSL_ES,SPVC_TRUE)==SPVC_SUCCESS);assert(spvc_compiler_install_compiler_options(compiler,options)==SPVC_SUCCESS);
  const char* text;assert(spvc_compiler_compile(compiler,&text)==SPVC_SUCCESS);assert(std::string(text).find("location = 5")!=std::string::npos);
  assert(tgs::restore_named_interface(compiler,vertex));assert(!spvc_compiler_has_decoration(compiler,6,SpvDecorationLocation));
  assert(bool(spvc_compiler_has_decoration(compiler,7,SpvDecorationLocation))==!vertex);
  assert(spvc_compiler_compile(compiler,&text)==SPVC_SUCCESS);assert(std::string(text).find("location = 5")==std::string::npos);
  if(!vertex)assert(std::string(text).find("location = 3")!=std::string::npos);
  spvc_context_destroy(ctx);
 }
 puts("PASS: real SPIRV-Cross generated interface locations removed; fragment outputs and explicit/newer shader exclusions preserved");
}
