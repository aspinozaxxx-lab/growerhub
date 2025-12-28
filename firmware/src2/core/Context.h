#pragma once

namespace Core {

class Scheduler;
class EventQueue;

struct Context {
  Scheduler* scheduler;
  EventQueue* event_queue;
};

}
