// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
#pragma once
#include <spirv_cross/spirv_cross_c.h>
#include <cstring>
namespace tgs {
inline bool legacy_named_interface(const char* original, int version) {
    return version <= 150 && !std::strstr(original,"location") && !std::strstr(original,"separate_shader_objects");
}
inline bool restore_named_interface(spvc_compiler compiler, bool vertex) {
    spvc_resources resources=nullptr;
    if(spvc_compiler_create_shader_resources(compiler,&resources)!=SPVC_SUCCESS)return false;
    const spvc_resource_type types[]={SPVC_RESOURCE_TYPE_STAGE_INPUT,SPVC_RESOURCE_TYPE_STAGE_OUTPUT};
    for(int stage=0;stage<(vertex?2:1);++stage){
        const spvc_reflected_resource* list=nullptr; size_t count=0;
        if(spvc_resources_get_resource_list_for_type(resources,types[stage],&list,&count)!=SPVC_SUCCESS)return false;
        for(size_t i=0;i<count;++i){
            spvc_compiler_unset_decoration(compiler,list[i].id,SpvDecorationLocation);
            spvc_compiler_unset_decoration(compiler,list[i].id,SpvDecorationComponent);
        }
    }
    return true;
}
}
