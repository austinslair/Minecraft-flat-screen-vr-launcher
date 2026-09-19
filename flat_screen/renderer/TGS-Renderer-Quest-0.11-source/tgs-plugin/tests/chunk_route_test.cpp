// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
#include "../../MobileGlues-cpp/gl/tgs_chunk_upload.h"
#include <cassert>
#include <cstdio>
int main(){
 tgs::UploadRoute r;assert(!r.use_staging());r.direct(499999);assert(!r.use_staging());
 r.direct(500000);for(int i=0;i<4;++i){assert(r.use_staging());r.staged(200000,true);}assert(!r.use_staging());
 r.direct(600000);r.staged(600001,true);assert(!r.use_staging());
 r.direct(600000);r.staged(0,false);assert(!r.use_staging());
 assert(tgs::chunk_bucket(GL_ARRAY_BUFFER,65535)==-1);
 assert(tgs::chunk_bucket(GL_ARRAY_BUFFER,65536)==0);
 assert(tgs::chunk_bucket(GL_ARRAY_BUFFER,16*1024*1024)==8);
 assert(tgs::chunk_bucket(GL_ARRAY_BUFFER,16*1024*1024+1)==-1);
 assert(tgs::chunk_bucket(GL_ELEMENT_ARRAY_BUFFER,65536)==9);
 assert(tgs::chunk_bucket(GL_COPY_WRITE_BUFFER,65536)==18);
 assert(tgs::chunk_bucket(GL_PIXEL_UNPACK_BUFFER,65536)==-1);
 puts("PASS: direct-first routing, slow-upload threshold, bounded trials, slower/failure fallback and bucket bounds");
}
