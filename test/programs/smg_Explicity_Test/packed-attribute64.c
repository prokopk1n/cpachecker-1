// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

typedef _Bool bool;
typedef unsigned char __u8;
typedef __u8 u8;
typedef unsigned short __u16;
typedef __u16 u16;
typedef unsigned int __u32;
typedef __u32 u32;
typedef unsigned long long __u64;
typedef int __s32;
typedef __s32 s32;
typedef long long __s64;
typedef __s64 s64;


struct __anonstruct_atomic64_t_7 {
   s64 counter ;
};

typedef struct __anonstruct_atomic64_t_7 atomic64_t;
typedef atomic64_t atomic_long_t;

struct __anonstruct_atomic_t_6 {
   int counter ;
};

typedef struct __anonstruct_atomic_t_6 atomic_t;

struct __anonstruct_43 {
   u8 locked ;
   u8 pending ;
};

struct __anonstruct_44 {
   u16 locked_pending ;
   u16 tail ;
};

union __anonunion_42 {
   atomic_t val ;
   struct __anonstruct_43 __anonCompField___anonunion_42_7 ;
   struct __anonstruct_44 __anonCompField___anonunion_42_8 ;
};

struct qspinlock {
   union __anonunion_42 __anonCompField_qspinlock_9 ;
};

typedef struct qspinlock arch_spinlock_t;

struct lockdep_map {
   char *key ;
   char *class_cache[2U] ;
   char const *name ;
   short wait_type_outer ;
   short wait_type_inner ;
   int cpu ;
   unsigned long ip ;
};

struct raw_spinlock {
   arch_spinlock_t raw_lock ;
   unsigned int magic ;
   unsigned int owner_cpu ;
   void *owner ;
   struct lockdep_map dep_map ;
};

struct __anonstruct_66 {
   u8 __padding[24U] ;
   struct lockdep_map dep_map ;
};

union __anonunion_65 {
   struct raw_spinlock rlock ;
   struct __anonstruct_66 __anonCompField___anonunion_65_24 ;
};

struct spinlock {
   union __anonunion_65 __anonCompField_spinlock_25 ;
};

typedef struct spinlock spinlock_t;

struct optimistic_spin_queue {
   atomic_t tail ;
};

struct list_head {
   struct list_head *next ;
   struct list_head *prev ;
};

struct mutex {
   atomic_long_t owner ;
   spinlock_t wait_lock ;
   struct optimistic_spin_queue osq ;
   struct list_head wait_list ;
   void *magic ;
   struct lockdep_map dep_map ;
};

struct refcount_struct {
   atomic_t refs ;
};

typedef struct refcount_struct refcount_t;

struct kref {
   refcount_t refcount ;
};

enum fe {
    FE_IS_STUPID = 0,
    FE_CAN_INVERSION_AUTO = 1,
    FE_CAN_FEC_1_2 = 2,
    FE_CAN_FEC_2_3 = 4,
    FE_CAN_FEC_3_4 = 8,
    FE_CAN_FEC_4_5 = 16,
    FE_CAN_FEC_5_6 = 32,
    FE_CAN_FEC_6_7 = 64,
    FE_CAN_FEC_7_8 = 128,
    FE_CAN_FEC_8_9 = 256,
    FE_CAN_FEC_AUTO = 512,
    FE_CAN_QPSK = 1024,
    FE_CAN_QAM_16 = 2048,
    FE_CAN_QAM_32 = 4096,
    FE_CAN_QAM_64 = 8192,
    FE_CAN_QAM_128 = 16384,
    FE_CAN_QAM_256 = 32768,
    FE_CAN_QAM_AUTO = 65536,
    FE_CAN_TRANSMISSION_MODE_AUTO = 131072,
    FE_CAN_BANDWIDTH_AUTO = 262144,
    FE_CAN_GUARD_INTERVAL_AUTO = 524288,
    FE_CAN_HIERARCHY_AUTO = 1048576,
    FE_CAN_8VSB = 2097152,
    FE_CAN_16VSB = 4194304,
    FE_HAS_EXTENDED_CAPS = 8388608,
    FE_CAN_MULTISTREAM = 67108864,
    FE_CAN_TURBO_FEC = 134217728,
    FE_CAN_2G_MODULATION = 268435456,
    FE_NEEDS_BENDING = 536870912,
    FE_CAN_RECOVER = 1073741824,
    FE_CAN_MUTE_TS = 2147483648
};

struct dvb_frontend_internal_info {
   char name[128U] ;
   u32 frequency_min_hz ;
   u32 frequency_max_hz ;
   u32 frequency_stepsize_hz ;
   u32 frequency_tolerance_hz ;
   u32 symbol_rate_min ;
   u32 symbol_rate_max ;
   u32 symbol_rate_tolerance ;
   enum fe caps ;
};

struct dvb_tuner_info {
   char name[128U] ;
   u32 frequency_min_hz ;
   u32 frequency_max_hz ;
   u32 frequency_step_hz ;
   u32 bandwidth_min ;
   u32 bandwidth_max ;
   u32 bandwidth_step ;
};

struct dvb_frontend;

struct dvb_tuner_ops {
   struct dvb_tuner_info info ;
   void (*release)(struct dvb_frontend *) ;
   int (*init)(struct dvb_frontend *) ;
   int (*sleep)(struct dvb_frontend *) ;
   int (*suspend)(struct dvb_frontend *) ;
   int (*resume)(struct dvb_frontend *) ;
   int (*set_params)(struct dvb_frontend *) ;
   int (*set_analog_params)(struct dvb_frontend *, u32 *) ;
   int (*set_config)(struct dvb_frontend *, void *) ;
   int (*get_frequency)(struct dvb_frontend *, u32 *) ;
   int (*get_bandwidth)(struct dvb_frontend *, u32 *) ;
   int (*get_if_frequency)(struct dvb_frontend *, u32 *) ;
   int (*get_status)(struct dvb_frontend *, u32 *) ;
   int (*get_rf_strength)(struct dvb_frontend *, u16 *) ;
   int (*get_afc)(struct dvb_frontend *, s32 *) ;
   int (*calc_regs)(struct dvb_frontend *, u8 *, int ) ;
   int (*set_frequency)(struct dvb_frontend *, u32 ) ;
   int (*set_bandwidth)(struct dvb_frontend *, u32 ) ;
};

struct analog_demod_info {
   char *name ;
};

struct analog_demod_ops {
   struct analog_demod_info info ;
   void (*set_params)(struct dvb_frontend *, u32 *) ;
   int (*has_signal)(struct dvb_frontend *, u16 *) ;
   int (*get_afc)(struct dvb_frontend *, s32 *) ;
   void (*tuner_status)(struct dvb_frontend *) ;
   void (*standby)(struct dvb_frontend *) ;
   void (*release)(struct dvb_frontend *) ;
   int (*i2c_gate_ctrl)(struct dvb_frontend *, int ) ;
   int (*set_config)(struct dvb_frontend *, void *) ;
};

struct dvb_frontend_ops {
   struct dvb_frontend_internal_info info ;
   u8 delsys[8U] ;
   void (*detach)(struct dvb_frontend *) ;
   void (*release)(struct dvb_frontend *) ;
   void (*release_sec)(struct dvb_frontend *) ;
   int (*init)(struct dvb_frontend *) ;
   int (*sleep)(struct dvb_frontend *) ;
   int (*write)(struct dvb_frontend *, u8 const *, int ) ;
   int (*tune)(struct dvb_frontend *, bool , unsigned int , unsigned int *, enum fe *) ;
   enum fe (*get_frontend_algo)(struct dvb_frontend *) ;
   int (*set_frontend)(struct dvb_frontend *) ;
   int (*get_tune_settings)(struct dvb_frontend *, u32 *) ;
   int (*get_frontend)(struct dvb_frontend *, struct dtv_frontend_properties *) ;
   int (*read_status)(struct dvb_frontend *, enum fe *) ;
   int (*read_ber)(struct dvb_frontend *, u32 *) ;
   int (*read_signal_strength)(struct dvb_frontend *, u16 *) ;
   int (*read_snr)(struct dvb_frontend *, u16 *) ;
   int (*read_ucblocks)(struct dvb_frontend *, u32 *) ;
   int (*diseqc_reset_overload)(struct dvb_frontend *) ;
   int (*diseqc_send_master_cmd)(struct dvb_frontend *, u32 *) ;
   int (*diseqc_recv_slave_reply)(struct dvb_frontend *, u32 *) ;
   int (*diseqc_send_burst)(struct dvb_frontend *, enum fe ) ;
   int (*set_tone)(struct dvb_frontend *, enum fe ) ;
   int (*set_voltage)(struct dvb_frontend *, enum fe ) ;
   int (*enable_high_lnb_voltage)(struct dvb_frontend *, long ) ;
   int (*dishnetwork_send_legacy_command)(struct dvb_frontend *, unsigned long ) ;
   int (*i2c_gate_ctrl)(struct dvb_frontend *, int ) ;
   int (*ts_bus_ctrl)(struct dvb_frontend *, int ) ;
   int (*set_lna)(struct dvb_frontend *) ;
   enum fe (*search)(struct dvb_frontend *) ;
   struct dvb_tuner_ops tuner_ops ;
   struct analog_demod_ops analog_ops ;
};

struct __anonstruct_layer_324 {
   u8 segment_count ;
   enum fe fec ;
   enum fe modulation ;
   u8 interleaving ;
};

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
   u32 frequency ;
   enum fe modulation ;
   enum fe voltage ;
   enum fe sectone ;
   enum fe inversion ;
   enum fe fec_inner ;
   enum fe transmission_mode ;
   u32 bandwidth_hz ;
   enum fe guard_interval ;
   enum fe hierarchy ;
   u32 symbol_rate ;
   enum fe code_rate_HP ;
   enum fe code_rate_LP ;
   enum fe pilot ;
   enum fe rolloff ;
   enum fe delivery_system ;
   enum fe interleaving ;
   u8 isdbt_partial_reception ;
   u8 isdbt_sb_mode ;
   u8 isdbt_sb_subchannel ;
   u32 isdbt_sb_segment_idx ;
   u32 isdbt_sb_segment_count ;
   u8 isdbt_layer_enabled ;
   struct __anonstruct_layer_324 layer[3U] ;
   u32 stream_id ;
   u32 scrambling_sequence_index ;
   u8 atscmh_fic_ver ;
   u8 atscmh_parade_id ;
   u8 atscmh_nog ;
   u8 atscmh_tnog ;
   u8 atscmh_sgn ;
   u8 atscmh_prc ;
   u8 atscmh_rs_frame_mode ;
   u8 atscmh_rs_frame_ensemble ;
   u8 atscmh_rs_code_mode_pri ;
   u8 atscmh_rs_code_mode_sec ;
   u8 atscmh_sccc_block_mode ;
   u8 atscmh_sccc_code_mode_a ;
   u8 atscmh_sccc_code_mode_b ;
   u8 atscmh_sccc_code_mode_c ;
   u8 atscmh_sccc_code_mode_d ;
   u32 lna ;
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
   struct kref refcount ;
   struct dvb_frontend_ops ops ;
   char *dvb ;
   void *demodulator_priv ;
   void *tuner_priv ;
   void *frontend_priv ;
   void *sec_priv ;
   void *analog_demod_priv ;
   struct dtv_frontend_properties dtv_property_cache ;
   int (*callback)(void *, int , int , int ) ;
   int id ;
   unsigned int exit ;
};


struct si2168_dev {
   struct mutex i2c_mutex ;
   char const *muxc ;
   struct dvb_frontend fe ;
   enum fe delivery_system ;
   enum fe fe_status ;
   unsigned int chip_id ;
   unsigned int version ;
   char const *firmware_name ;
   u8 ts_mode ;
   unsigned int active : 1 ;
   unsigned int warm : 1 ;
   unsigned int ts_clock_inv : 1 ;
   unsigned int ts_clock_gapped : 1 ;
   unsigned int spectral_inversion : 1 ;
};

typedef unsigned long __kernel_ulong_t;
typedef __kernel_ulong_t __kernel_size_t;
typedef __kernel_size_t size_t;

void *calloc(size_t, size_t);
void free(void *);

static int si2168_probe(void)
{
  struct si2168_dev *dev;
  int ret;

  // sizeof(struct si2168_dev) is 1480 according gcc compiler
  dev = (struct si2168_dev *)calloc(1UL,1480UL);

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