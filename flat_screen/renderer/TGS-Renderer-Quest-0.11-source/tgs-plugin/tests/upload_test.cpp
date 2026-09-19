// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
#include "../../MobileGlues-cpp/gl/tgs_upload.h"
#include <cassert>
#include <cstdio>
#include <vector>
struct Driver {
 std::array<GLint,6> state;int calls=0;
 void glPixelStorei(GLenum name,GLint value){
  constexpr GLenum names[]={GL_UNPACK_ALIGNMENT,GL_UNPACK_ROW_LENGTH,GL_UNPACK_SKIP_ROWS,GL_UNPACK_SKIP_PIXELS,GL_UNPACK_IMAGE_HEIGHT,GL_UNPACK_SKIP_IMAGES};
  for(int i=0;i<6;++i)if(names[i]==name){state[i]=value;++calls;return;}assert(false);
 }
};
int main(){int cases=0;
 for(int align:{1,2,4,8})for(int width=1;width<=65;++width)for(int channels:{3,4})for(int bits=0;bits<32;++bits){
  std::array<GLint,6> old={align,0,0,0,0,0};for(int i=1;i<6;++i)if(bits&(1<<(i-1)))old[i]=i*3;
  Driver d{old};size_t row=width*channels;int tight=tgs::tight_upload_alignment(align,row);tgs::tight_upload_state(d,old,tight,false);
  size_t stride=(row+d.state[0]-1)/d.state[0]*d.state[0];assert(stride==row);
  for(int i=1;i<6;++i)assert(d.state[i]==0);
  tgs::tight_upload_state(d,old,tight,true);assert(d.state==old);if(align==4&&channels==4&&bits==0)assert(d.calls==0);++cases;
 }
 std::vector<unsigned char> bytes;tgs::grow_upload_scratch(bytes,1048576);auto* address=bytes.data();bytes.back()=71;
 for(int i=0;i<100;++i){tgs::grow_upload_scratch(bytes,1024);tgs::grow_upload_scratch(bytes,1048576);assert(bytes.data()==address&&bytes.back()==71);}
 printf("PASS: %d unpack layouts, 12 -> 0 default RGBA state calls, scratch reused without re-zeroing\n",cases);
}
