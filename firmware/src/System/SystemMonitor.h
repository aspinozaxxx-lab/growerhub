// firmware/src/System/SystemMonitor.h
#pragma once
#include <Arduino.h>
#include <functional>

class SystemMonitor {
private:
    unsigned long startTime;
    unsigned long lastHeapCheck;
    unsigned long minFreeHeap;
    unsigned long maxUsedHeap;
    int restartCount;
    
public:
    // Интерфейс обертки над реальным рестартом.
    class IRebooter {
    public:
        virtual ~IRebooter() = default;
        virtual void restart() = 0;
    };
    
    SystemMonitor();
    
    void begin();
    void update();
    
    unsigned long getUptime();
    unsigned long getFreeHeap();
    unsigned long getMinFreeHeap();
    unsigned long getMaxUsedHeap();
    int getRestartCount();
    float getHeapFragmentation();
    
    String getStatus();
    void printSystemInfo();

    // Bezopasnaya perezagruzka s proverkoj sostoyanij.
    // reason - stroka, obyasnyayushchaya prichinu (napr. "server_command").
    // correlationId - optional'nyj identifikator dlya ACK.
    bool rebootIfSafe(const String& reason, const String& correlationId = "");

    // Setter dlya proverki sosotyanija nasosa (true = nasos rabotaet, reboot nel'zya).
    void setPumpStatusProvider(std::function<bool(void)> fn);
    // Setter dlya otpravki ACK (accepted/declined) esli est' correlationId.
    void setAckPublisher(std::function<void(const String&, const char*, bool)> fn);
    // Setter dlya publikacii state pered reboot.
    void setStatePublisher(std::function<void(bool)> fn);
    // Setter dlya obekta, kotoryj vypolnit fakticheskij restart.
    void setRebooter(IRebooter* r);
    
private:
    void checkMemory();
    void checkWatchdog();
    
    std::function<bool(void)> pumpStatusProvider;
    std::function<void(const String&, const char*, bool)> ackPublisher;
    std::function<void(bool)> statePublisher;
    
    class DefaultRebooter : public IRebooter {
    public:
        void restart() override {
            ESP.restart();
        }
    };
    
    DefaultRebooter defaultRebooter;
    IRebooter* rebooter;
    
#ifdef UNIT_TEST
    // V testah mozhno umen'shit' pauzu, chtoby ne tormozit' progony.
    static constexpr unsigned long REBOOT_GRACE_DELAY_MS = 5UL;
    // Marker dlya proverki fakta vyzova rebooter->restart() v testah.
    bool lastRebootCalled_ = false;
public:
    bool wasRebootCalledForTests() const { return lastRebootCalled_; }
    void clearRebootFlagForTests() { lastRebootCalled_ = false; }
private:
#else
    static constexpr unsigned long REBOOT_GRACE_DELAY_MS = 250UL; // Pauza dlya dootpravki MQTT pered restartom.
#endif
};
