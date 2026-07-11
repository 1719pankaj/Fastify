# F1 Live Standings: Dashboard & SBC Aggregator

[![FastAPI](https://img.shields.io/badge/FastAPI-005571?style=for-the-badge&logo=fastapi)](https://fastapi.tiangolo.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/Compose-4285F4?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Cloudflare Tunnel](https://img.shields.io/badge/Cloudflare-F38020?style=for-the-badge&logo=cloudflare&logoColor=white)](https://www.cloudflare.com/)

A modern, high-performance Formula 1 dashboard system. The project features a FastAPI backend server designed to run on a Linux Single Board Computer (SBC) that acts as an intelligent aggregator for the OpenF1 API, and a premium Android application built in Kotlin & Jetpack Compose to display real-time race data.

---

## Architecture Overview

```
                                  +-----------------------+
                                  |      OpenF1 API       |
                                  |  (Telemetry & Laps)   |
                                  +-----------+-----------+
                                              | (Rate-limited requests)
                                              v
+------------------+             +------------+-----------+
|    Linux SBC     |             |     FastAPI Server     |
| (Home/SBC Host)  |  ========>  | - Caches Raw Data      |
+------------------+             | - Computes Standings   |
                                 +------------+-----------+
                                              | 
                                              v (Cloudflare Tunnel / Local IP)
+------------------+             +------------+-----------+
|  Android Device  |             |  Android Standing App  |
|  (Mobile App)    |  ========>  | - Jetpack Compose UI   |
+------------------+             | - Polling / Dynamic URL|
                                 +------------------------+
```

---

## Core Features

- 🏎️ **Standings of 22 Drivers**: Real-time position tracking, constructor team colors, completed laps, tyre compound types (Soft, Medium, Hard, Intermediate, Wet), and tyre ages.
- ⏱️ **F1 Timings**: Real-time last lap and session best lap times formatted as standard F1 times.
- 🟣 **Fastest Lap Recognition**: Computes and highlights the driver with the overall fastest session lap in purple.
- 🏁 **Track Status Banners**: Large dynamic banner showing current session name and color-coded flags (`GREEN`, `YELLOW`, `RED`, `BLUE`, `SAFETY CAR`, etc.).
- ⚠️ **Race Control ticker**: Interactive message board displaying real-time warnings, track limits, investigations, and penalties.
- ⚙️ **Configurable Backend Integration**: Instantly change connection endpoints (e.g. SBC local IP or Cloudflare tunnel URL) directly from the Settings dialog inside the app.
- 🔄 **Simulation Mode**: Backend auto-starts simulation of the Bahrain GP 2024 on startup, allowing out-of-the-box testing without waiting for a live race weekend.

---

## Directory Structure

This repository is split into two major components:

### 1. [`/server`](file:///g:/Codez/F1/Fastify/server) (FastAPI Backend)
- Aggregates endpoints from the OpenF1 API (drivers, stints, positions, laps, and race control).
- Feeds race snapshots cleanly to the Android app in a optimized format, protecting your widget's battery.
- Runs in the background as a `systemd` service on boot and can be securely exposed to the internet via Cloudflare Tunnels without port-forwarding.
- *Refer to [server/README.md](server/README.md) for server setup instructions.*

### 2. [`/android`](file:///g:/Codez/F1/Fastify/android) (Jetpack Compose Application)
- Follows reactive MVVM architecture.
- Fetches and polls aggregated race snapshots every 10 seconds.
- Persists user preferences dynamically (e.g., custom backend URL) in SharedPreferences.
- *Refer to [android/README.md](android/README.md) for technical design details.*

---

## Quickstart

### 1. Launch the Backend
Install dependencies and run the FastAPI server:
```bash
cd server
pip install -r requirements.txt
python main.py
```
*(The server will start a simulated Bahrain GP session on `http://localhost:8000`)*

### 2. Deploy to Android Device
Connect your device, define the JDK 17 environment path, compile, and run:
```powershell
cd android
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
android run --device=<device-serial> --apks="app/build/outputs/apk/debug/app-debug.apk" --activity="com.example.f1standings.MainActivity"
```

### 3. Connect the App
1. Open the **F1 Standings** app on your device.
2. Tap the **Settings (Gear)** icon in the top right.
3. Enter your custom backend IP or Cloudflare tunnel URL (e.g., `https://judicial-hampshire-sox-depot.trycloudflare.com/`) and tap **Save & Apply URL**.
