// firmware/src/System/TaskScheduler.cpp
#include "TaskScheduler.h"

TaskScheduler::TaskScheduler(int maxTasksCount) 
    : maxTasks(maxTasksCount), taskCount(0) {
    tasks = new ScheduledTask[maxTasks];
}

TaskScheduler::~TaskScheduler() {
    delete[] tasks;
}

bool TaskScheduler::addTask(const String& name, std::function<void()> callback, 
                           unsigned long intervalMs, bool enabled) {
    if (taskCount >= maxTasks) {
        Serial.println("TaskScheduler: Maximum tasks reached");
        return false;
    }
    
    // Проверяем нет ли задачи с таким именем
    if (findTaskIndex(name) >= 0) {
        Serial.println("TaskScheduler: Task with name '" + name + "' already exists");
        return false;
    }
    
    tasks[taskCount] = {
        name,
        callback,
        intervalMs,
        0,
        enabled
    };
    
    taskCount++;
    Serial.println("TaskScheduler: Added task '" + name + "' with interval " + String(intervalMs) + "ms");
    return true;
}

void TaskScheduler::removeTask(const String& name) {
    int index = findTaskIndex(name);
    if (index >= 0) {
        // Сдвигаем задачи
        for (int i = index; i < taskCount - 1; i++) {
            tasks[i] = tasks[i + 1];
        }
        taskCount--;
        Serial.println("TaskScheduler: Removed task '" + name + "'");
    }
}

void TaskScheduler::enableTask(const String& name) {
    int index = findTaskIndex(name);
    if (index >= 0) {
        tasks[index].enabled = true;
    }
}

void TaskScheduler::disableTask(const String& name) {
    int index = findTaskIndex(name);
    if (index >= 0) {
        tasks[index].enabled = false;
    }
}

void TaskScheduler::update() {
    unsigned long currentTime = millis();
    
    for (int i = 0; i < taskCount; i++) {
        if (tasks[i].enabled && (currentTime - tasks[i].lastRun >= tasks[i].interval)) {
            tasks[i].callback();
            tasks[i].lastRun = currentTime;
        }
    }
}

String TaskScheduler::getStatus() {
    String status = "TaskScheduler[" + String(taskCount) + "/" + String(maxTasks) + " tasks]:\n";
    for (int i = 0; i < taskCount; i++) {
        status += "  " + tasks[i].name + ": " + 
                 (tasks[i].enabled ? "Enabled" : "Disabled") + 
                 ", Interval: " + String(tasks[i].interval) + "ms\n";
    }
    return status;
}

int TaskScheduler::findTaskIndex(const String& name) {
    for (int i = 0; i < taskCount; i++) {
        if (tasks[i].name == name) {
            return i;
        }
    }
    return -1;
}