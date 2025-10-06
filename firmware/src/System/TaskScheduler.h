// firmware/src/System/TaskScheduler.h
#pragma once
#include <Arduino.h>
#include <functional>

struct ScheduledTask {
    String name;
    std::function<void()> callback;
    unsigned long interval;
    unsigned long lastRun;
    bool enabled;
};

class TaskScheduler {
private:
    ScheduledTask* tasks;
    int taskCount;
    int maxTasks;
    
public:
    TaskScheduler(int maxTasks = 10);
    ~TaskScheduler();
    
    bool addTask(const String& name, std::function<void()> callback, 
                 unsigned long intervalMs, bool enabled = true);
    void removeTask(const String& name);
    void enableTask(const String& name);
    void disableTask(const String& name);
    void update();
    
    String getStatus();
    
private:
    int findTaskIndex(const String& name);
};