#include <ctime>
#include <cstdint>

namespace {
    bool isLeap(int year) {
        return (year % 4 == 0) && (year % 100 != 0 || year % 400 == 0);
    }

    bool fillTm(long long seconds, struct tm* out) {
        if (!out) {
            return false;
        }
        if (seconds < 0) {
            return false;
        }

        long long days = seconds / 86400;
        long long rem = seconds % 86400;
        int hour = static_cast<int>(rem / 3600);
        rem %= 3600;
        int minute = static_cast<int>(rem / 60);
        int sec = static_cast<int>(rem % 60);

        int year = 1970;
        long long dayCounter = days;
        while (true) {
            int daysInYear = isLeap(year) ? 366 : 365;
            if (dayCounter < daysInYear) {
                break;
            }
            dayCounter -= daysInYear;
            ++year;
            if (year > 9999) {
                return false;
            }
        }

        static const int monthDays[12] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int month = 0;
        int dayOfYear = static_cast<int>(dayCounter);
        for (; month < 12; ++month) {
            int dim = monthDays[month];
            if (month == 1 && isLeap(year)) {
                dim = 29;
            }
            if (dayCounter < dim) {
                break;
            }
            dayCounter -= dim;
        }
        int dayOfMonth = static_cast<int>(dayCounter) + 1;

        out->tm_sec = sec;
        out->tm_min = minute;
        out->tm_hour = hour;
        out->tm_mday = dayOfMonth;
        out->tm_mon = month;
        out->tm_year = year - 1900;
        out->tm_yday = dayOfYear;
        out->tm_isdst = 0;
        out->tm_wday = static_cast<int>((days + 4) % 7); // 1970-01-01 was Thursday (4)
        if (out->tm_wday < 0) {
            out->tm_wday += 7;
        }
        return true;
    }

    struct tm globalTm;
}

extern "C" struct tm* fake_gmtime_r(const time_t* timer, struct tm* result) {
    if (!timer || !result) {
        return nullptr;
    }
    if (!fillTm(static_cast<long long>(*timer), result)) {
        return nullptr;
    }
    return result;
}

extern "C" struct tm* fake_gmtime(const time_t* timer) {
    if (!timer) {
        return nullptr;
    }
    if (!fillTm(static_cast<long long>(*timer), &globalTm)) {
        return nullptr;
    }
    return &globalTm;
}
