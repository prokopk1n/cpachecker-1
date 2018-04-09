#ifndef __NULL_DEREF_CHECKS_H
#define __NULL_DEREF_CHECKS_H

extern void null_deref_assume_check(void *p);
extern void null_deref_assert_check(void *p);

#endif
