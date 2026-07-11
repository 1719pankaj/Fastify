import requests
import datetime
from config import OPENF1_BASE_URL
import threading
import time

class F1DataAggregator:
    def __init__(self):
        self.session_key = None
        self.mode = "live" # or "simulation"
        
        # Raw Data caches
        self.drivers = {}
        self.positions = []
        self.laps = []
        self.stints = []
        self.race_control = []
        self.session_info = {}
        
        # Virtual clock for simulation
        self.virtual_time = None
        self.simulation_speed = 1.0
        self.simulation_start_real = None
        self.simulation_start_virtual = None
        
        self.lock = threading.Lock()

    def set_session(self, session_key, mode="live", speed=1.0):
        with self.lock:
            self.session_key = session_key
            self.mode = mode
            self.simulation_speed = speed
            # Clear caches
            self.drivers = {}
            self.positions = []
            self.laps = []
            self.stints = []
            self.race_control = []
            self.session_info = {}

    def fetch_all_data(self):
        """Fetch all necessary data for the current session. Blocking call."""
        if not self.session_key:
            return
            
        print(f"Fetching data for session {self.session_key}...")
        
        def fetch(endpoint):
            try:
                res = requests.get(f"{OPENF1_BASE_URL}/{endpoint}?session_key={self.session_key}")
                if res.status_code == 200:
                    return res.json()
            except Exception as e:
                print(f"Error fetching {endpoint}: {e}")
            return []

        drivers_data = fetch("drivers")
        with self.lock:
            for d in drivers_data:
                self.drivers[d.get('driver_number')] = d
                
        session_data = fetch("sessions")
        if session_data:
            with self.lock:
                self.session_info = session_data[0]

        positions = fetch("position")
        laps = fetch("laps")
        stints = fetch("stints")
        race_control = fetch("race_control")
        
        with self.lock:
            self.positions = positions
            self.laps = laps
            self.stints = stints
            self.race_control = race_control
            
        print(f"Data fetched: {len(self.positions)} positions, {len(self.laps)} laps, {len(self.stints)} stints, {len(self.race_control)} RC messages")

    def start_simulation(self, session_key, speed=1.0):
        self.set_session(session_key, mode="simulation", speed=speed)
        self.fetch_all_data()
        
        with self.lock:
            if self.session_info and 'date_start' in self.session_info:
                # Start virtual time at the session start
                try:
                    # Clean up Z or +00:00 for fromisoformat
                    ds = self.session_info['date_start'].replace('Z', '+00:00')
                    self.simulation_start_virtual = datetime.datetime.fromisoformat(ds)
                except Exception as e:
                    print("Date parsing error", e)
                    self.simulation_start_virtual = datetime.datetime.now(datetime.timezone.utc)
            else:
                self.simulation_start_virtual = datetime.datetime.now(datetime.timezone.utc)
                
            self.simulation_start_real = datetime.datetime.now(datetime.timezone.utc)

    def _get_current_time(self):
        if self.mode == "simulation" and self.simulation_start_real and self.simulation_start_virtual:
            now = datetime.datetime.now(datetime.timezone.utc)
            elapsed = (now - self.simulation_start_real).total_seconds()
            return self.simulation_start_virtual + datetime.timedelta(seconds=elapsed * self.simulation_speed)
        else:
            return datetime.datetime.now(datetime.timezone.utc)

    def get_widget_state(self):
        with self.lock:
            if not self.session_key:
                return {"status": "No active session"}

            current_time = self._get_current_time()
            ct_iso = current_time.isoformat()

            # Filter data based on current_time
            def is_past(item):
                if 'date' in item and item['date']:
                    return item['date'] <= ct_iso
                return True # If no date, include it

            # Aggregate drivers
            standings_dict = {}
            for drv_num, drv in self.drivers.items():
                standings_dict[drv_num] = {
                    "driver_number": drv_num,
                    "name_acronym": drv.get("name_acronym", ""),
                    "full_name": drv.get("full_name", ""),
                    "team_name": drv.get("team_name", ""),
                    "team_colour": drv.get("team_colour", ""),
                    "position": 99,
                    "laps_completed": 0,
                    "last_lap_time": None,
                    "best_lap_time": None,
                    "tyre_compound": None,
                    "tyre_age": 0
                }

            # Latest positions
            valid_positions = [p for p in self.positions if is_past(p)]
            # We want the latest position for each driver
            latest_pos = {}
            for p in valid_positions:
                drv_num = p.get('driver_number')
                # Assumes positions are chronologically ordered by the API (or close enough)
                latest_pos[drv_num] = p.get('position')
                
            for drv_num, pos in latest_pos.items():
                if drv_num in standings_dict:
                    standings_dict[drv_num]['position'] = pos

            # Laps
            valid_laps = [l for l in self.laps if l.get('date_start') and l['date_start'] <= ct_iso]
            driver_laps = {}
            for l in valid_laps:
                drv_num = l.get('driver_number')
                if drv_num not in driver_laps:
                    driver_laps[drv_num] = []
                driver_laps[drv_num].append(l)

            for drv_num, d_laps in driver_laps.items():
                if drv_num in standings_dict and d_laps:
                    completed_laps = [l for l in d_laps if l.get('lap_duration')]
                    standings_dict[drv_num]['laps_completed'] = len(d_laps)
                    if completed_laps:
                        last_lap = completed_laps[-1]
                        standings_dict[drv_num]['last_lap_time'] = last_lap.get('lap_duration')
                        best_lap = min(completed_laps, key=lambda x: x.get('lap_duration', 9999))
                        standings_dict[drv_num]['best_lap_time'] = best_lap.get('lap_duration')

            # Stints
            # The API doesn't have a 'date' for stints, but we can estimate based on lap counts or just take the max stint number that has less laps than laps_completed
            for drv_num in standings_dict.keys():
                drv_stints = [s for s in self.stints if s.get('driver_number') == drv_num]
                # Filter stints that started before the driver's current lap
                current_lap = standings_dict[drv_num]['laps_completed']
                valid_stints = [s for s in drv_stints if s.get('lap_start', 0) <= current_lap]
                if valid_stints:
                    current_stint = sorted(valid_stints, key=lambda x: x.get('stint_number', 0))[-1]
                    standings_dict[drv_num]['tyre_compound'] = current_stint.get('compound')
                    stint_start_lap = current_stint.get('lap_start', 0)
                    tyre_age_at_start = current_stint.get('tyre_age_at_start', 0)
                    # Current age = tyre_age_at_start + (current_lap - stint_start_lap)
                    standings_dict[drv_num]['tyre_age'] = tyre_age_at_start + (current_lap - stint_start_lap)

            # Race control
            valid_rc = [rc for rc in self.race_control if is_past(rc)]
            flags = [rc for rc in valid_rc if rc.get('category') == 'Flag']
            current_flag = flags[-1].get('flag') if flags else "GREEN"
            
            recent_messages = [rc.get('message') for rc in valid_rc[-5:]] # Last 5 messages

            # Format list, sort by position
            standings_list = list(standings_dict.values())
            standings_list.sort(key=lambda x: x['position'] if x['position'] is not None else 999)

            return {
                "session": {
                    "name": self.session_info.get("session_name", "Unknown"),
                    "status": self.mode,
                    "virtual_time": ct_iso,
                    "flag": current_flag
                },
                "standings": standings_list,
                "race_control": recent_messages
            }

f1_data = F1DataAggregator()
