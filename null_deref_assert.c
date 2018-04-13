#include <ldv-test.h>

void null_deref_assert_check(void *p) {
    if (p == 0) {
        ldv_error();
    }
}
