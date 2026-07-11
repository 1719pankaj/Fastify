#!/bin/bash

# Navigate to backend directory
cd /home/radxa/F1/server

# Stop any running instances of main.py or cloudflared
pkill -f "python3 main.py" || true
pkill -f "cloudflared tunnel run" || true

# Start FastAPI server and redirect logs
python3 main.py > server.log 2>&1 &

# Give the server a few seconds to start
sleep 3

# Start Cloudflare Tunnel and redirect logs
# This runs in the foreground so systemd can track the process
cloudflared tunnel run --url http://localhost:8000 f1-widget > tunnel.log 2>&1
