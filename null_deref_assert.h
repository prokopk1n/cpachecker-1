#ifndef __NULL_DEREF_ASSERT_H
#define __NULL_DEREF_ASSERT_H

extern char __VERIFIER_nondet_char(void);
extern int __VERIFIER_nondet_int(void);
extern float __VERIFIER_nondet_float(void);
extern long __VERIFIER_nondet_long(void);
extern size_t __VERIFIER_nondet_size_t(void);
extern loff_t __VERIFIER_nondet_loff_t(void);
extern u32 __VERIFIER_nondet_u32(void);
extern u16 __VERIFIER_nondet_u16(void);
extern u8 __VERIFIER_nondet_u8(void);
extern unsigned char __VERIFIER_nondet_uchar(void);
extern unsigned int __VERIFIER_nondet_uint(void);
extern unsigned short __VERIFIER_nondet_ushort(void);
extern unsigned __VERIFIER_nondet_unsigned(void);
extern unsigned long __VERIFIER_nondet_ulong(void);
extern unsigned long long __VERIFIER_nondet_ulonglong(void);

extern void *external_allocated_data(void);

extern void __VERIFIER_assume(int expr);

extern void null_deref_assert_check(void *p);

#endif
