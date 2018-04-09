#include <verifier/common.h>

void null_deref_assume_check(void *p) {
    ldv_assume(p != 0);
}

void null_deref_assert_check(void *p) {
    ldv_assert(p != 0);
}
