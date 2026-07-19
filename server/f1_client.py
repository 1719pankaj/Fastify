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
        self.intervals = []
        self.session_info = {}
        self.schedule = [] # Caches all sessions for the year
        
        
        # Virtual clock for simulation
        self.virtual_time = None
        self.simulation_speed = 1.0
        self.simulation_start_real = None
        self.simulation_start_virtual = None
        self.simulation_paused = False
        self.paused_virtual_time = None
        
        self.lock = threading.Lock()

    def set_session(self, session_key, mode="live", speed=1.0):
        with self.lock:
            self.session_key = session_key
            self.mode = mode
            self.simulation_speed = speed
            self.simulation_paused = False
            self.paused_virtual_time = None
            # Clear caches
            self.drivers = {}
            self.positions = []
            self.laps = []
            self.stints = []
            self.race_control = []
            self.intervals = []
            self.session_info = {}

    def fetch_schedule(self):
        """Fetches the current year's sessions to determine upcoming schedule"""
        try:
            year = datetime.datetime.now(datetime.timezone.utc).year
            res = requests.get(f"{OPENF1_BASE_URL}/sessions?year={year}", timeout=10)
            if res.status_code == 200:
                with self.lock:
                    self.schedule = res.json()
                print(f"Schedule fetched from OpenF1: {len(self.schedule)} sessions found for {year}")
                return
            else:
                print(f"OpenF1 schedule fetch returned status {res.status_code}. Trying Jolpica fallback...")
        except Exception as e:
            print(f"Error fetching schedule from OpenF1: {e}. Trying Jolpica fallback...")

        # Fallback to Jolpica API (Ergast replacement) which is free and has no lockdown
        try:
            year = datetime.datetime.now(datetime.timezone.utc).year
            res = requests.get(f"https://api.jolpi.ca/ergast/f1/{year}.json", timeout=10)
            if res.status_code == 200:
                data = res.json()
                races = data.get("MRData", {}).get("RaceTable", {}).get("Races", [])
                mapped_sessions = []
                
                def parse_dt(d, t):
                    if not d or not t: return None
                    return f"{d}T{t}".replace("Z", "+00:00")
                
                for r in races:
                    location = r.get("Circuit", {}).get("Location", {}).get("locality", "")
                    country = r.get("Circuit", {}).get("Location", {}).get("country", "")
                    
                    # Helper to append sub-sessions
                    def add_sub(sub_name, sub_data, duration_hours=1.0):
                        if sub_data:
                            dt_start = parse_dt(sub_data.get("date"), sub_data.get("time"))
                            if dt_start:
                                try:
                                    dt_end = (datetime.datetime.fromisoformat(dt_start) + datetime.timedelta(hours=duration_hours)).isoformat()
                                except:
                                    dt_end = dt_start
                                mapped_sessions.append({
                                    "session_name": f"{r.get('raceName')} - {sub_name}",
                                    "session_type": sub_name,
                                    "date_start": dt_start,
                                    "date_end": dt_end,
                                    "location": location,
                                    "country_name": country
                                })

                    add_sub("FP1", r.get("FirstPractice"), 1.0)
                    add_sub("FP2", r.get("SecondPractice"), 1.0)
                    add_sub("FP3", r.get("ThirdPractice"), 1.0)
                    add_sub("Sprint Shootout", r.get("SprintQualifying") or r.get("SprintShootout"), 0.75)
                    add_sub("Sprint", r.get("Sprint"), 1.0)
                    add_sub("Qualifying", r.get("Qualifying"), 1.0)
                    
                    # Add main Race
                    dt_start = parse_dt(r.get("date"), r.get("time"))
                    if dt_start:
                        try:
                            dt_end = (datetime.datetime.fromisoformat(dt_start) + datetime.timedelta(hours=2.0)).isoformat()
                        except:
                            dt_end = dt_start
                        mapped_sessions.append({
                            "session_name": f"{r.get('raceName')} - Race",
                            "session_type": "Race",
                            "date_start": dt_start,
                            "date_end": dt_end,
                            "location": location,
                            "country_name": country
                        })

                with self.lock:
                    self.schedule = mapped_sessions
                print(f"Schedule successfully loaded from Jolpica fallback: {len(self.schedule)} sessions.")
        except Exception as ex:
            print(f"Error fetching schedule from Jolpica: {ex}")

    def get_next_session(self):
        """Returns the next scheduled session based on current time"""
        now = datetime.datetime.now(datetime.timezone.utc).isoformat()
        upcoming = []
        with self.lock:
            for s in self.schedule:
                # Use date_start
                ds = s.get('date_start')
                if ds:
                    ds_iso = ds.replace('Z', '+00:00')
                    if ds_iso > now:
                        upcoming.append(s)
            
            if upcoming:
                upcoming.sort(key=lambda x: x.get('date_start'))
                return upcoming[0]
            return None

    def fetch_all_data(self):
        """Fetch all necessary data for the current session. Blocking call. Only used for simulation/historical now."""
        if not self.session_key:
            return
            
        print(f"Fetching historical data from OpenF1 for session {self.session_key}...")
        
        def fetch(endpoint):
            try:
                res = requests.get(f"{OPENF1_BASE_URL}/{endpoint}?session_key={self.session_key}", timeout=10)
                if res.status_code == 200:
                    return res.json()
                else:
                    print(f"OpenF1 returned status {res.status_code} for {endpoint}")
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
        intervals = fetch("intervals")
        
        with self.lock:
            self.positions = positions
            self.laps = laps
            self.stints = stints
            self.race_control = race_control
            self.intervals = intervals
            
        print(f"Data fetched: {len(self.positions)} positions, {len(self.laps)} laps, {len(self.stints)} stints, {len(self.race_control)} RC messages")

    async def _live_loop(self):
        import asyncio
        import json
        import websockets
        hub = "Streaming"
        negotiate_url = f"https://livetiming.formula1.com/signalr/negotiate?clientProtocol=1.5&connectionData=[%7B%22name%22:%22{hub}%22%7D]"
        try:
            res = requests.get(negotiate_url)
            token = res.json()['ConnectionToken']
            url = f"wss://livetiming.formula1.com/signalr/connect?clientProtocol=1.5&transport=webSockets&connectionToken={token}&connectionData=[%7B%22name%22:%22{hub}%22%7D]"
            
            async with websockets.connect(url) as ws:
                subscribe_msg = {
                    "H": hub,
                    "M": "Subscribe",
                    "A": [["TimingData", "TrackStatus", "RaceControlMessages", "DriverList"]],
                    "I": 1
                }
                await ws.send(json.dumps(subscribe_msg))
                print("Connected to F1 SignalR for Live Data")
                
                while self.mode == "live":
                    msg = await ws.recv()
                    data = json.loads(msg)
                    if 'M' in data:
                        for m in data['M']:
                            if m['M'] == 'feed':
                                topic = m['A'][0]
                                payload = m['A'][1]
                                self._handle_live_msg(topic, payload)
        except Exception as e:
            print("Live WS error", e)

    def _handle_live_msg(self, topic, payload):
        ct_iso = datetime.datetime.now(datetime.timezone.utc).isoformat()
        with self.lock:
            if topic == "DriverList":
                for k, v in payload.items():
                    try:
                        num = int(k)
                        if num not in self.drivers:
                            self.drivers[num] = {"driver_number": num}
                        self.drivers[num]["name_acronym"] = v.get("Tla", "")
                        self.drivers[num]["full_name"] = v.get("FirstName", "") + " " + v.get("LastName", "")
                        self.drivers[num]["team_name"] = v.get("TeamName", "")
                        self.drivers[num]["team_colour"] = v.get("TeamColour", "")
                    except: pass
            elif topic == "TrackStatus":
                val = payload.get("Status", "1")
                flags = {"1":"GREEN", "2":"YELLOW", "4":"SC", "5":"RED", "6":"VSC"}
                self.race_control.append({"date": ct_iso, "category": "Flag", "flag": flags.get(val, "GREEN")})
            elif topic == "RaceControlMessages":
                if "Messages" in payload:
                    for msg in payload["Messages"]:
                        self.race_control.append({"date": ct_iso, "message": msg.get("Message", "")})
            elif topic == "TimingData":
                if "Lines" in payload:
                    for k, v in payload["Lines"].items():
                        try:
                            num = int(k)
                            
                            # Positions
                            if "Position" in v: 
                                self.positions.append({"date": ct_iso, "driver_number": num, "position": int(v["Position"])})
                            
                            # Intervals
                            if "GapToLeader" in v or "IntervalToPositionAhead" in v:
                                interval_obj = {"date": ct_iso, "driver_number": num}
                                if "GapToLeader" in v: 
                                    interval_obj["gap_to_leader"] = v["GapToLeader"]
                                if "IntervalToPositionAhead" in v:
                                    val = v["IntervalToPositionAhead"]
                                    interval_obj["interval"] = val.get("Value") if isinstance(val, dict) else val
                                self.intervals.append(interval_obj)
                            
                            # Laps
                            if "NumberOfLaps" in v or "BestLapTime" in v or "LastLapTime" in v:
                                lap_obj = {"date_start": ct_iso, "driver_number": num}
                                if "LastLapTime" in v and isinstance(v["LastLapTime"], dict):
                                    lap_obj["lap_duration"] = v["LastLapTime"].get("Value")
                                self.laps.append(lap_obj)
                                
                            # Stints
                            if "Stints" in v and isinstance(v["Stints"], list) and len(v["Stints"]) > 0:
                                last_stint = v["Stints"][-1]
                                stint_obj = {"driver_number": num, "stint_number": len(v["Stints"]), "lap_start": 0, "tyre_age_at_start": 0}
                                if "Compound" in last_stint: stint_obj["compound"] = last_stint["Compound"]
                                if "TotalLaps" in last_stint: stint_obj["lap_start"] = int(v.get("NumberOfLaps", 0)) - int(last_stint["TotalLaps"])
                                self.stints.append(stint_obj)

                        except Exception as e:
                            print("Timing parse err", e)

    def start_simulation(self, session_key, speed=1.0):
        self.set_session(session_key, mode="simulation", speed=speed)
        self.fetch_all_data()
        
        with self.lock:
            self.simulation_paused = False
            self.paused_virtual_time = None
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
        if self.mode == "simulation":
            if self.simulation_paused:
                return self.paused_virtual_time
            if self.simulation_start_real and self.simulation_start_virtual:
                now = datetime.datetime.now(datetime.timezone.utc)
                elapsed = (now - self.simulation_start_real).total_seconds()
                return self.simulation_start_virtual + datetime.timedelta(seconds=elapsed * self.simulation_speed)
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
                    "tyre_age": 0,
                    "gap_to_leader": None,
                    "interval": None
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

            # Intervals
            valid_intervals = [i for i in self.intervals if is_past(i)]
            latest_intervals = {}
            for i in valid_intervals:
                drv_num = i.get('driver_number')
                latest_intervals[drv_num] = i
                
            def format_gap(val):
                if val is None: return None
                if isinstance(val, (int, float)):
                    return f"+{val:.3f}s"
                return str(val)

            for drv_num, interval_data in latest_intervals.items():
                if drv_num in standings_dict:
                    standings_dict[drv_num]['gap_to_leader'] = format_gap(interval_data.get('gap_to_leader'))
                    standings_dict[drv_num]['interval'] = format_gap(interval_data.get('interval'))

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
                    "flag": current_flag,
                    "date_start": self.session_info.get("date_start"),
                    "date_end": self.session_info.get("date_end"),
                    "speed": self.simulation_speed,
                    "paused": self.simulation_paused
                },
                "standings": standings_list,
                "race_control": recent_messages
            }

    def pause_simulation(self):
        with self.lock:
            if self.mode == "simulation" and not self.simulation_paused:
                self.paused_virtual_time = self._get_current_time()
                self.simulation_paused = True

    def resume_simulation(self):
        with self.lock:
            if self.mode == "simulation" and self.simulation_paused:
                self.simulation_start_virtual = self.paused_virtual_time
                self.simulation_start_real = datetime.datetime.now(datetime.timezone.utc)
                self.simulation_paused = False

    def seek_simulation(self, offset_seconds):
        with self.lock:
            if self.mode == "simulation" and self.session_info and 'date_start' in self.session_info:
                try:
                    ds = self.session_info['date_start'].replace('Z', '+00:00')
                    start_virtual = datetime.datetime.fromisoformat(ds)
                    target_virtual = start_virtual + datetime.timedelta(seconds=offset_seconds)
                    
                    if self.simulation_paused:
                        self.paused_virtual_time = target_virtual
                    else:
                        self.simulation_start_virtual = target_virtual
                        self.simulation_start_real = datetime.datetime.now(datetime.timezone.utc)
                except Exception as e:
                    print("Seek error", e)

    def set_simulation_speed(self, speed):
        with self.lock:
            if self.mode == "simulation":
                current_virtual = self._get_current_time()
                self.simulation_speed = speed
                if not self.simulation_paused:
                    self.simulation_start_virtual = current_virtual
                    self.simulation_start_real = datetime.datetime.now(datetime.timezone.utc)

f1_data = F1DataAggregator()
