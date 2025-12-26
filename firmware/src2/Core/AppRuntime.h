#pragma once

// Minimalnaya zaglushka yadra dlya budushchego razvitiya.
namespace Core {

class AppRuntime {
 public:
  void Init();
  void Tick();
};

}  // namespace Core