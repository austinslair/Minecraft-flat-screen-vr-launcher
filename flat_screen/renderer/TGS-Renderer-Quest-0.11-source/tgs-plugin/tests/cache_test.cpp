// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
// Run from a directory containing test-work/. Tests the actual Cache implementation.
#include "../../MobileGlues-cpp/gl/glsl/cache.h"
#include "../../MobileGlues-cpp/gl/glsl/tgs_cache_policy.h"
#include <cassert>
#include <cstdio>
#include <fstream>
#include <limits>
#include <string>
global_settings_t global_settings{};
char* glsl_cache_file_path=nullptr;
int __android_log_print(int,const char*,const char*,...){return 0;}
void write_log(const char*,...){}
void write_log_n(const char*,...){}
int main(){
 using tgs::cache_save_due;
 assert(!cache_save_due(0,0,0,6000000000LL));
 assert(!cache_save_due(15,1500,1500,0));
 assert(cache_save_due(16,1600,1600,0));
 assert(!cache_save_due(16,1600,1000000,0));
 assert(cache_save_due(16,250000,1000000,0));
 assert(!cache_save_due(16,250000,1000001,0));
 assert(!cache_save_due(1,1,1000000,4999999999LL));
 assert(cache_save_due(1,1,1000000,5000000000LL));
 assert(!cache_save_due(1,1,1000000,-1));
 assert(cache_save_due(16,std::numeric_limits<size_t>::max(),std::numeric_limits<size_t>::max(),0));
 std::string path="test-work/cache-test.bin";std::remove(path.c_str());glsl_cache_file_path=path.data();global_settings.max_glsl_cache_size=30*1024*1024;
 auto value=[](int i){auto v="translated-"+std::to_string(i);v.resize(4095,'x');return v;};
 size_t last_count=0,bytes_written=0,saves=0;
 auto observe=[&](){std::ifstream f(path,std::ios::binary|std::ios::ate);if(!f)return;size_t bytes=(size_t)f.tellg(),count=0;f.seekg(0);f.read((char*)&count,sizeof(count));if(count!=last_count){bytes_written+=bytes;++saves;last_count=count;}};
 {Cache c;for(int i=0;i<1024;++i){auto key="shader-"+std::to_string(i);auto v=value(i);assert(!c.get(key.c_str()));c.put(key.c_str(),v.c_str());assert(std::string(c.get(key.c_str()))==v);observe();}}
 observe();assert(last_count==1024);
 {Cache c;for(int i=0;i<1024;++i){auto key="shader-"+std::to_string(i);assert(c.get(key.c_str()));assert(std::string(c.get(key.c_str()))==value(i));}}
 size_t old_policy_bytes=0;for(size_t n=16;n<=1024;n+=16)old_policy_bytes+=sizeof(size_t)+n*(32+sizeof(size_t)+4096);
 printf("PASS: policy boundaries, deadline and overflow; 1024 immediate hits and persisted/reloaded identical translations. Observed cache writes=%zu bytes in %zu saves; old 16-entry policy model=%zu bytes. Host test, not Quest FPS.\n",bytes_written,saves,old_policy_bytes);
 std::remove(path.c_str());
}
