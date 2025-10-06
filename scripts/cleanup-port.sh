#!/bin/bash
# Освобождаем порт 8000 перед запуском

echo "$(date): Checking port 8000..."
PID=$(sudo lsof -ti:8000 2>/dev/null)

if [ ! -z "$PID" ]; then
    echo "$(date): Killing process $PID using port 8000"
    sudo kill -9 $PID
    sleep 2
else
    echo "$(date): Port 8000 is free"
fi
