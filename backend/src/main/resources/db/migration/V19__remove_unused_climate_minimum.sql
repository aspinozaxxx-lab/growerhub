UPDATE automation_scenario_configs
SET config_json = (
    COALESCE(NULLIF(config_json, '')::jsonb, '{}'::jsonb) - 'min_c'
)::text
WHERE scenario_type = 'BOX_CLIMATE'
  AND COALESCE(NULLIF(config_json, '')::jsonb, '{}'::jsonb) ? 'min_c';
