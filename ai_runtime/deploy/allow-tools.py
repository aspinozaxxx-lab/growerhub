#!/usr/bin/python3
import json
import sys
import pathlib

ALLOWED = {
    'gh_farm_report', 'gh_plant_details', 'gh_resource_history',
    'gh_prepare_change', 'gh_rule_details', 'gh_validate_rule',
}
try:
    event = json.load(sys.stdin)
    name = event.get('tool_name', '')
    try:
        audit = pathlib.Path('/var/lib/growerhub-ai-owner/tool-audit.log')
        if not audit.exists() or audit.stat().st_size < 1048576:
            with audit.open('a') as log:
                log.write(json.dumps({'tool': str(name)[:100]}) + '\n')
    except OSError:
        pass
    if name in ALLOWED or name in {'functions.' + item for item in ALLOWED}:
        sys.exit(0)
except Exception:
    pass
print('Only GrowerHub domain tools are available.', file=sys.stderr)
sys.exit(2)
