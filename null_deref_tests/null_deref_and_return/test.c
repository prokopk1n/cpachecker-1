int *f1(int p) {
    return (int *) (p % 2 != 0);
}

int *f2(int p) {
    if (p != 0) {
        return (int *) p;
    } else {
        return (int *) 123;
    }
}

int *f3(int p) {
    return f1(p);
}

int *f4(int p) {
    return f2(p);
}

int f5(int *p) {
    int v = (p == 0) ? 0 : *p;
    int *retp = f4(v);
    int ret = *retp;
    return ret;
}
