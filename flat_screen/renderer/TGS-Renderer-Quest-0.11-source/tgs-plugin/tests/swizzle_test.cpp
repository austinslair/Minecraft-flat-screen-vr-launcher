// SPDX-License-Identifier: LGPL-2.1-only
#include "../../MobileGlues-cpp/gl/tgs_swizzle.h"
#include <vector>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <sys/mman.h>
#include <unistd.h>
static void require(bool ok) { if (!ok) std::abort(); }
int main() {
    for (size_t n=0;n<=257;++n) for (size_t offset=0;offset<16;++offset) {
        std::vector<uint8_t> src(n*4+32,0xA7), out(n*4+32,0xCC), expected(out);
        for (size_t i=0;i<n*4;++i) src[offset+i]=(i*71+n*13)&255;
        for (size_t i=0;i<n;++i) for (int c=0;c<4;++c)
            expected[offset+4*i+c]=src[offset+4*i+(c==0?2:c==2?0:c)];
        tgs::bgra_to_rgba(src.data()+offset,out.data()+offset,n);
        require(out==expected);
    }
    const size_t page=static_cast<size_t>(sysconf(_SC_PAGESIZE));
    auto* p=static_cast<uint8_t*>(mmap(nullptr,page*2,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0));
    require(p!=MAP_FAILED); require(mprotect(p+page,page,PROT_NONE)==0);
    std::memset(p,17,page);
    for(size_t n=0;n<100;++n) { std::vector<uint8_t> out(n*4+1); tgs::bgra_to_rgba(p+page-n*4,out.data(),n); }
    munmap(p,page*2);
    require(tgs::can_swizzle(0x80E1,0x1401,4,false));
    require(!tgs::can_swizzle(0x80E1,0x1401,3,false));
    require(!tgs::can_swizzle(0x1908,0x1401,4,false));
    require(!tgs::can_swizzle(0x80E1,0x8367,4,true));
    require(!tgs::can_swizzle(0x80E1,0x8035,4,false));
    std::puts("PASS: pixel identity, alpha, 4128 size/alignment cases, guards, format gating");
}
