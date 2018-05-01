int f1(int p) {
    return p;
}

int f2(int p) {
    if (p >= 0) {
        return p;
    } else {
        return -p;
    }
}

int f3(int p) {
    if (p <= 0) {
        return p;
    } else {
        return -p;
    }
}

int f4(void) {
    return 0;
}

int f5(int p) {
    return f2(p);
}

int f6(int p) {
    return f3(p) + f4();
}
