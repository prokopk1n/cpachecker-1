#include <ldv-test.h>

void null_deref_assert_check(void *p) {
    char value = * ((char *) p);

    if (p == 0) {
        ldv_error();
    }
}
