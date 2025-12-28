#include <unity.h>

void test_pump_timeout();
void test_light_state();
void test_pump_start_ack();
void test_pump_stop_ack();
void test_reboot_declined_when_pump_running();
void test_reboot_accepted_when_idle();
void test_config_codec_valid();
void test_config_codec_invalid();
void test_event_queue_order();
void test_parse_pump_start();
void test_parse_pump_stop();
void test_parse_reboot();
void test_parse_invalid_json();
void test_parse_missing_type();
void test_parse_invalid_duration();
void test_ack_payloads();
void test_scheduler_periodic();
void test_config_sync_apply_retained();
void test_wifi_defaults_builtin();
void test_wifi_defaults_user_override();
void test_wifi_config_codec_storage();
void test_wifi_config_codec_invalid();
void test_webconfig_build_json();
void test_soil_scanner_detected();
void test_soil_scanner_hysteresis();
void test_sensor_hub_pump_block();
void test_state_soil_serialization();

void setUp() {}
void tearDown() {}

int main(int argc, char** argv) {
  (void)argc;
  (void)argv;
  UNITY_BEGIN();
  RUN_TEST(test_pump_timeout);
  RUN_TEST(test_light_state);
  RUN_TEST(test_pump_start_ack);
  RUN_TEST(test_pump_stop_ack);
  RUN_TEST(test_reboot_declined_when_pump_running);
  RUN_TEST(test_reboot_accepted_when_idle);
  RUN_TEST(test_config_codec_valid);
  RUN_TEST(test_config_codec_invalid);
  RUN_TEST(test_event_queue_order);
  RUN_TEST(test_parse_pump_start);
  RUN_TEST(test_parse_pump_stop);
  RUN_TEST(test_parse_reboot);
  RUN_TEST(test_parse_invalid_json);
  RUN_TEST(test_parse_missing_type);
  RUN_TEST(test_parse_invalid_duration);
  RUN_TEST(test_ack_payloads);
  RUN_TEST(test_scheduler_periodic);
  RUN_TEST(test_config_sync_apply_retained);
  RUN_TEST(test_wifi_defaults_builtin);
  RUN_TEST(test_wifi_defaults_user_override);
  RUN_TEST(test_wifi_config_codec_storage);
  RUN_TEST(test_wifi_config_codec_invalid);
  RUN_TEST(test_webconfig_build_json);
  RUN_TEST(test_soil_scanner_detected);
  RUN_TEST(test_soil_scanner_hysteresis);
  RUN_TEST(test_sensor_hub_pump_block);
  RUN_TEST(test_state_soil_serialization);
  return UNITY_END();
}
