# F1 Android Widget Backend

This is the backend service that powers your F1 Android Widget. It acts as an intelligent aggregator for the OpenF1 API, parsing rate-limited live F1 data and serving it seamlessly to your Android widget.

## Architecture & Client Strategy

To save your Android widget's battery life and simplify its development, the Android app does **not** need to store race calendars or do any caching. Instead, follow this strategy in your Android code:

1. **Wake up and check schedule:** 
   When the Android widget updates, query `GET /api/schedule`. This returns the precise start date and time of the next session (e.g. `2026-07-17T11:30:00+00:00`).
2. **Sleep until race time:**
   Schedule your Android widget (using `WorkManager` or `AlarmManager`) to wake up 5 minutes before this `date_start`.
3. **Live Polling:**
   Once the widget wakes up at the start time, switch to a high-frequency loop and poll `GET /api/widget` every 10–15 seconds.
4. **Race End:**
   When the `/api/widget` payload indicates `status: idle` or `flag: CHECKERED`, the race is over. Stop polling and go back to Step 1 to fetch the next schedule.

## API Endpoints

### 1. `GET /api/schedule`
Returns the next upcoming F1 session so your Android widget knows exactly when to wake up.
```json
{
  "status": "scheduled",
  "next_session": {
    "session_name": "Practice 1",
    "date_start": "2026-07-17T11:30:00+00:00"
  }
}
```

### 2. `GET /api/widget`
This is the primary data payload containing all driver standings, lap times, gaps, tyre ages, and race control flags. It is designed to be parsed directly by the Android widget without any additional processing.

**Response Schema:**
```json
{
  "session": {
    "name": "Race",
    "status": "simulation",
    "virtual_time": "2024-03-02T15:51:19.164350+00:00",
    "flag": "BLUE"
  },
  "standings": [
    {
      "driver_number": 1,
      "name_acronym": "VER",
      "full_name": "Max VERSTAPPEN",
      "team_name": "Red Bull Racing",
      "team_colour": "3671c6",
      "position": 1,
      "laps_completed": 30,
      "last_lap_time": 95.771,
      "best_lap_time": 95.16,
      "tyre_compound": "HARD",
      "tyre_age": 12
    }
  ],
  "race_control": [
    "WAVED BLUE FLAG FOR CAR 2 (SAR) TIMED AT 18:30:28",
    "CAR 22 (TSU) TIME 1:38.083 DELETED - TRACK LIMITS AT TURN 13 LAP 27 18:48:03"
  ]
}
```

#### Field Dictionary

| Object | Field | Type | Description |
|---|---|---|---|
| **session** | `name` | String | Name of the session (e.g., "Race", "Practice 1") |
| **session** | `status` | String | Current mode. Can be `"live"`, `"simulation"`, or `"idle"` |
| **session** | `virtual_time` | String (ISO-8601) | The time the backend used to generate this snapshot |
| **session** | `flag` | String | Global track flag (e.g., `"GREEN"`, `"YELLOW"`, `"RED"`, `"DOUBLE YELLOW"`, `"BLUE"`) |
| **standings** | `driver_number` | Integer | The driver's permanent racing number |
| **standings** | `name_acronym` | String | 3-letter acronym (e.g., "VER", "HAM") |
| **standings** | `full_name` | String | Full name of the driver |
| **standings** | `team_name` | String | Name of the constructor/team |
| **standings** | `team_colour` | String (Hex) | Team's primary hex color (without the `#` prefix) |
| **standings** | `position` | Integer | Current race position |
| **standings** | `laps_completed` | Integer | Total number of laps fully completed by the driver |
| **standings** | `last_lap_time` | Float | The driver's most recent lap time (in seconds). Can be `null`. |
| **standings** | `best_lap_time` | Float | The driver's fastest lap time of the session (in seconds). Can be `null`. |
| **standings** | `tyre_compound` | String | The compound of tyre currently fitted (e.g., `"SOFT"`, `"MEDIUM"`, `"HARD"`, `"INTERMEDIATE"`, `"WET"`). Can be `null`. |
| **standings** | `tyre_age` | Integer | The number of laps completed on the current set of tyres. |
| **race_control** | (Array Items) | String | The last 5 chronological race control messages (e.g., penalties, investigations, track limits, warnings, flags). The 0th index is the oldest, the last index is the newest. |

---

## Exposing via Cloudflare (Global Access)

To make this server accessible from anywhere in the world without exposing your home router's ports, we use **Cloudflare Tunnel (`cloudflared`)**.

### Setup Instructions
1. Install cloudflared on this SBC:
   ```bash
   wget -q https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64.deb
   sudo dpkg -i cloudflared-linux-arm64.deb
   ```
2. Login to Cloudflare:
   ```bash
   cloudflared tunnel login
   ```
   *(This will provide a URL that you must click on another device to authorize your account)*
3. Create and route the tunnel to your backend (running on port 8000):
   ```bash
   cloudflared tunnel create f1-widget
   cloudflared tunnel route dns f1-widget f1.yourdomain.com
   cloudflared tunnel run --url http://localhost:8000 f1-widget
   ```
   
Alternatively, for a **quick, temporary URL** without an account, you can just run:
```bash
cloudflared tunnel --url http://localhost:8000
```
This will instantly generate a free, secure `https://<random-words>.trycloudflare.com` URL that forwards to your backend. Put this URL in your Android app!
