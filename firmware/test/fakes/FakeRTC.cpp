#include "FakeRTC.h"

FakeRTC::FakeRTC()
    : current(0), valid(false), lastSet(0) {}

bool FakeRTC::begin() {
    return true;
}

bool FakeRTC::isTimeValid() const {
    return valid;
}

bool FakeRTC::getTime(time_t& outUtc) const {
    if (!valid) {
        return false;
    }
    outUtc = current;
    return true;
}

bool FakeRTC::setTime(time_t utc) {
    current = utc;
    lastSet = utc;
    valid = true;
    return true;
}

void FakeRTC::setCurrent(time_t value) {
    current = value;
}

void FakeRTC::setValid(bool value) {
    valid = value;
}

time_t FakeRTC::lastSetTime() const {
    return lastSet;
}

