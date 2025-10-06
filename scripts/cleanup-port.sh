#!/bin/bash
# Освобождаем порт 8000 перед запуском

echo "Checking port 8000..."
PID=$(lsof -ti:8000 2>/dev/null)

if [ ! -z "$PID" ]; then
    echo "Killing process $PID using port 8000"
    kill -9 $PID
    sleep 2
else
    echo "Port 8000 is free"
fi
