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

union __anonunion_188 {
   __u64 uvalue ;
   __s64 svalue ;
};

struct dtv_stats {
   __u8 scale ;
   union __anonunion_188 __anonCompField_dtv_stats_56 ;
} __attribute__((__packed__));

struct dtv_fe_stats {
   __u8 len ;
   struct dtv_stats stat[4U] ;
} __attribute__((__packed__));

struct dtv_frontend_properties {
   struct dtv_fe_stats strength ;
   struct dtv_fe_stats cnr ;
   struct dtv_fe_stats pre_bit_error ;
   struct dtv_fe_stats pre_bit_count ;
   struct dtv_fe_stats post_bit_error ;
   struct dtv_fe_stats post_bit_count ;
   struct dtv_fe_stats block_error ;
   struct dtv_fe_stats block_count ;
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
  int ret;

  // sizeof(struct si2168_dev) is 300 according gcc compiler
  dev = (struct si2168_dev *)calloc(1UL,300UL);

  if (dev == (struct si2168_dev *)0) {
    ret = -12;
    goto err;
  }
  dev->chip_id = 5;
  ret = 0;

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