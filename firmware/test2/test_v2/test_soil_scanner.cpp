#include <cstring>
#include <unity.h>

#include "drivers/soil/Rj9PortScanner.h"

static uint16_t g_samples[2][9];
static size_t g_index[2];

static void ResetSamples() {
  std::memset(g_samples, 0, sizeof(g_samples));
  g_index[0] = 0;
  g_index[1] = 0;
}

static void FillSamples(uint16_t port0, uint16_t port1) {
  for (size_t i = 0; i < 9; ++i) {
    g_samples[0][i] = port0;
    g_samples[1][i] = port1;
  }
  g_index[0] = 0;
  g_index[1] = 0;
}

static uint16_t FakeAdc(uint8_t pin) {
  size_t port = pin == 35 ? 1 : 0;
  uint16_t value = g_samples[port][g_index[port] % 9];
  g_index[port]++;
  return value;
}

void test_soil_scanner_detected() {
  Drivers::Rj9PortScanner scanner;
  const uint8_t ports[2] = {34, 35};
  scanner.Init(ports, 2);
  scanner.SetAdcReader(&FakeAdc);
  scanner.SetCalibration(4095, 1800);

  ResetSamples();
  FillSamples(2000, 4095);

  scanner.Scan();
  scanner.Scan();
  scanner.Scan();

  TEST_ASSERT_TRUE(scanner.IsDetected(0));
  TEST_ASSERT_FALSE(scanner.IsDetected(1));
  TEST_ASSERT_EQUAL_UINT16(2000, scanner.GetLastRaw(0));
  TEST_ASSERT_TRUE(scanner.GetLastPercent(0) > 0);
}

void test_soil_scanner_hysteresis() {
  Drivers::Rj9PortScanner scanner;
  const uint8_t ports[2] = {34, 35};
  scanner.Init(ports, 2);
  scanner.SetAdcReader(&FakeAdc);

  FillSamples(2000, 4095);
  scanner.Scan();
  scanner.Scan();
  scanner.Scan();
  TEST_ASSERT_TRUE(scanner.IsDetected(0));

  FillSamples(4095, 4095);
  scanner.Scan();
  scanner.Scan();
  TEST_ASSERT_TRUE(scanner.IsDetected(0));
  scanner.Scan();
  TEST_ASSERT_FALSE(scanner.IsDetected(0));
}
