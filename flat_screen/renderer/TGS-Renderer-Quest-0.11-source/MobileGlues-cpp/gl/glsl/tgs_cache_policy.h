// TGS 2026. SPDX-License-Identifier: LGPL-2.1-only
#pragma once
#include <cstddef>
#include <cstdint>
namespace tgs {
// Amortize full-cache writes over proportional new data, with the existing
// five-second deadline checked by cache operations. No rendering activation delay.
inline bool cache_save_due(size_t entries, size_t bytes, size_t cache_bytes, int64_t elapsed_ns) {
    if (!entries) return false;
    if (elapsed_ns >= 5000000000LL) return true;
    const size_t quarter = cache_bytes / 4 + (cache_bytes % 4 != 0);
    return entries >= 16 && bytes >= quarter;
}
}
