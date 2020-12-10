// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

typedef unsigned char __u8;
typedef unsigned long long __u64;
typedef long long __s64;

typedef unsigned long __kernel_ulong_t;
typedef __kernel_ulong_t __kernel_size_t;
typedef __kernel_size_t size_t;

void *calloc(size_t, size_t);
void free(void *);
void *memcpy(void *dest, const void *src, size_t n);

struct xstate_header {
   __u64 xfeatures ;
   __u64 xcomp_bv ;
   __u64 reserved[5U] ;
};

struct xregs_state {
   struct xstate_header header ;
   __u8 extended_state_area[0U] ;
} __attribute__((__aligned__(128)));

struct dtv_frontend_properties {
   struct xregs_state strength ;
   struct xregs_state cnr ;
   struct xregs_state pre_bit_error ;
   struct xregs_state pre_bit_count ;
   struct xregs_state post_bit_error ;
   struct xregs_state post_bit_count ;
   struct xregs_state block_error ;
   struct xregs_state block_count ;
};

struct dvb_frontend {
   struct dtv_frontend_properties dtv_property_cache ;
};


struct si2168_dev {
   struct dvb_frontend fe ;
   unsigned int chip_id ;
};

static int si2168_probe(void)
{
  struct si2168_dev *dev;
  struct si2168_dev copy_dev;
  int ret;

  // sizeof(struct si2168_dev) is 1152 according gcc compiler
  dev = (struct si2168_dev *)calloc(1UL,1152UL);

  if (dev == (struct si2168_dev *)0) {
    ret = -12;
    goto err;
  }
  dev->chip_id = 5;
  ret = 0;
  memcpy(&copy_dev, dev, 1152);

  free((void const *)dev);

err:
  return ret;
}

int main(void)
{
  int ret;
  ret = si2168_probe();
  return ret;
}