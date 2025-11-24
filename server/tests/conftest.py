from __future__ import annotations

import os

# Translitem: ispolzuem otdelnyj testovyj fail BD, chtoby ne zadevat production local.db.
os.environ.setdefault('DATABASE_URL', 'sqlite:///./test.db')
