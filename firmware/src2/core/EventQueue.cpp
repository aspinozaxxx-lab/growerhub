#include "core/EventQueue.h"

namespace Core {

bool EventQueue::Push(const Event& event) {
  if (count_ >= kCapacity) {
    return false;
  }
  buffer_[tail_] = event;
  tail_ = (tail_ + 1) % kCapacity;
  ++count_;
  return true;
}

bool EventQueue::Pop(Event& event) {
  if (count_ == 0) {
    return false;
  }
  event = buffer_[head_];
  head_ = (head_ + 1) % kCapacity;
  --count_;
  return true;
}

size_t EventQueue::Size() const {
  return count_;
}

}
