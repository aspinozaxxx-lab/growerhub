/*
 * Chto v faile: obyavleniya planirivshchika periodicheskih zadach.
 * Rol v arhitekture: core.
 * Naznachenie: publichnyi API i tipy dlya sloya core.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

namespace Core {

struct Context;

class Scheduler {
 public:
  // Tip callback dlya periodicheskih zadach.
  using TaskCallback = void (*)(Context& ctx, uint32_t now_ms);

  /**
   * Dobavlyaet periodicheskuyu zadachu v scheduler.
   * @param name Idenfikator zadachi dlya otladki.
   * @param interval_ms Period zapuska v millisekundah.
   * @param callback Ukazatel na funkciyu obrabotki.
   */
  bool AddPeriodic(const char* name, uint32_t interval_ms, TaskCallback callback);
  /**
   * Vypolnyaet prohod po zadacham i zapuskaet ih po raspisaniyu.
   * @param ctx Kontekst dlya peredachi v callback.
   * @param now_ms Tekuschee vremya v millisekundah.
   */
  void Tick(Context& ctx, uint32_t now_ms);
  /**
   * Kolichestvo aktivnyh zadach.
   */
  size_t Count() const;

 private:
  struct Task {
    const char* name;
    uint32_t interval_ms;
    uint32_t last_run_ms;
    TaskCallback callback;
    bool active;
  };

  static const size_t kMaxTasks = 8;

  std::array<Task, kMaxTasks> tasks_;
  size_t count_ = 0;
};

}
