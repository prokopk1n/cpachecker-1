static inline void *ERR_PTR(long error) {
    return (long) error;
}

static inline int IS_ERR(const void *ptr)
{
    return (unsigned long) ptr >= (unsigned long) -4095;
}

int *f1(int p) {
    if (p & 1) {
        return (void *) 0;
    } else {
        return (void *) 12345;
    }
}

int *f2(int p) {
    int *retp = f1(p);

    if (!retp) {
        return retp;
    }

    return retp + 10;
}

int *f3(int p) {
    int *retp = f2(p);

    if (!retp) {
        return ERR_PTR(-11);
    }

    return retp;
}

int *f4(int p) {
    int *retp = f3(p);

    if (IS_ERR(retp)) {
        return (void *) 0;
    } else {
        return retp;
    }
}
