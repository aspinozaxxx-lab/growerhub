UPDATE automation_scenario_configs
SET config_json = (
    COALESCE(NULLIF(config_json, '')::jsonb, '{}'::jsonb)
        - 'min_c'
        - 'off_delay_minutes'
        - 'min_toggle_minutes'
)::text
WHERE scenario_type IN ('BOX_CLIMATE', 'ROOM_CLIMATE')
  AND COALESCE(NULLIF(config_json, '')::jsonb, '{}'::jsonb)
      ?| ARRAY['min_c', 'off_delay_minutes', 'min_toggle_minutes'];
