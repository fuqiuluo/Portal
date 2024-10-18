#ifndef PORTAL_DOBBY_HOOK_H
#define PORTAL_DOBBY_HOOK_H

#include <unistd.h>
#include <dobby.h>
#include <sys/mman.h>

#define uintval(p)              reinterpret_cast<uintptr_t>(p)
#define ptr(p)                  (reinterpret_cast<void *>(p))
#define align_up(x, n)          (((x) + ((n) - 1)) & ~((n) - 1))
#define align_down(x, n)        ((x) & -(n))
#define page_size               getpagesize()
#define page_align(n)           align_up(static_cast<uintptr_t>(n), page_size)
#define ptr_align(x)            ptr(align_down(reinterpret_cast<uintptr_t>(x), page_size))
#define make_rwx(p, n)          ::mprotect(ptr_align(p), \
                                            page_align(uintval(p) + (n)) != page_align(uintval(p)) \
                                                ? page_align(n) + page_size : page_align(n),       \
                                            PROT_READ | PROT_WRITE | PROT_EXEC)

void* InlineHook(void* target, void* hooker) {
    make_rwx(target, page_size);
    void* origin_call;
    if (DobbyHook(target, hooker, &origin_call) == RS_SUCCESS) {
        return origin_call;
    } else {
        return nullptr;
    }
}

#endif //PORTAL_DOBBY_HOOK_H
