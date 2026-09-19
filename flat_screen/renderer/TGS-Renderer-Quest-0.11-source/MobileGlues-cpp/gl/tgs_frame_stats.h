// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
#pragma once
#include "tgs_stream.h"
#include "tgs_chunk_upload.h"
#include <chrono>
#include <array>
#include <algorithm>
namespace tgs {
struct FrameReport{double fps,mean_ms,p95_ms,worst_ms;StreamTotals uploads;ChunkUploads chunks;};
class FrameStats {
 using Clock=std::chrono::steady_clock;
 Clock::time_point last{};uintptr_t display=0,surface=0;
 std::array<double,300> samples{};size_t count=0;StreamTotals before{};ChunkUploads before_chunks{};
public:
 bool frame(uintptr_t d,uintptr_t s,FrameReport& report){
  const auto now=Clock::now();
  if(last==Clock::time_point{} || display!=d || surface!=s){display=d;surface=s;count=0;last=now;before=stream_totals;before_chunks=chunk_uploads;return false;}
  samples[count++]=std::chrono::duration<double,std::milli>(now-last).count();last=now;
  if(count<samples.size())return false;
  double sum=0;for(double ms:samples)sum+=ms;std::sort(samples.begin(),samples.end());
  report={sum>0?300000.0/sum:0,sum/300.0,samples[284],samples[299],
    {stream_totals.mapped-before.mapped,stream_totals.fallback-before.fallback,stream_totals.reference-before.reference,stream_totals.bytes-before.bytes},
    {chunk_uploads.staged-before_chunks.staged,chunk_uploads.bytes-before_chunks.bytes,chunk_uploads.direct-before_chunks.direct}};
  before=stream_totals;before_chunks=chunk_uploads;count=0;return true;
 }
};
}
