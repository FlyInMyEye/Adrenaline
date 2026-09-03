#if defined(__x86_64__) || defined(_M_X64)
#include <cpuid.h>

static unsigned long long adrenaline_xgetbv(unsigned int index) {
    unsigned int low;
    unsigned int high;
    __asm__ volatile(".byte 0x0f, 0x01, 0xd0" : "=a"(low), "=d"(high) : "c"(index));
    return ((unsigned long long) high << 32) | low;
}
#endif

int adrenaline_has_avx2(void) {
#if defined(__x86_64__) || defined(_M_X64)
    unsigned int eax;
    unsigned int ebx;
    unsigned int ecx;
    unsigned int edx;
    if (!__get_cpuid(1, &eax, &ebx, &ecx, &edx)
        || !(ecx & (1u << 28))
        || !(ecx & (1u << 27))
        || (adrenaline_xgetbv(0) & 6u) != 6u
        || !__get_cpuid_count(7, 0, &eax, &ebx, &ecx, &edx)) {
        return 0;
    }
    return (ebx & (1u << 5)) != 0;
#else
    return 0;
#endif
}
