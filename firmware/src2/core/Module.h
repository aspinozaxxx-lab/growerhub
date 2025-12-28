#pragma once

#include <cstdint>
#include "core/EventQueue.h"

namespace Core {

struct Context;

class Module {
 public:
  virtual ~Module() {}
  virtual void Init(Context& ctx) = 0;
  virtual void OnEvent(Context& ctx, const Event& event) = 0;
  virtual void OnTick(Context& ctx, uint32_t now_ms) = 0;
};

}
