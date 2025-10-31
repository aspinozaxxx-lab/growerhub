#include "FakeMillis.h"

namespace {
    unsigned long g_millis = 0;
}

namespace FakeMillis {
    void set(unsigned long value) {
        g_millis = value;
    }

    unsigned long get() {
        return g_millis;
    }

    void advance(unsigned long delta) {
        g_millis += delta;
    }
}

unsigned long millis() {
    return FakeMillis::get();
}

