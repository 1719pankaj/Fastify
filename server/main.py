from fastapi import FastAPI, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import threading
import time
import asyncio

from config import DEFAULT_SIMULATION_SESSION_KEY, LIVE_POLL_INTERVAL
from f1_client import f1_data

app = FastAPI(title="F1 Android Widget Backend")

# Allow CORS for potential local web testing
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
def startup_event():
    # Fetch schedule for the year
    threading.Thread(target=f1_data.fetch_schedule).start()
    
    # Auto-start simulation for testing out-of-the-box
    print(f"Starting default simulation with session {DEFAULT_SIMULATION_SESSION_KEY}...")
    # Doing this in a background thread so we don't block server startup
    threading.Thread(target=f1_data.start_simulation, args=(DEFAULT_SIMULATION_SESSION_KEY, 10.0)).start()

@app.on_event("shutdown")
def shutdown_event():
    pass

@app.get("/api/schedule")
def get_schedule():
    """Returns the next scheduled session for the Android widget to wake up to"""
    session = f1_data.get_next_session()
    if session:
        return {"status": "scheduled", "next_session": session}
    return {"status": "no_upcoming_sessions"}

@app.get("/api/widget")
def get_widget_data():
    """Returns the unified race state for the Android widget"""
    return f1_data.get_widget_state()

@app.get("/api/status")
def get_status():
    """Returns the backend's current status (mode, session, virtual time)"""
    return {
        "mode": f1_data.mode,
        "session_key": f1_data.session_key,
        "virtual_time": f1_data._get_current_time().isoformat() if f1_data.mode == "simulation" else None,
        "speed": f1_data.simulation_speed
    }

class SimulationStartReq(BaseModel):
    session_key: int
    speed: float = 1.0

@app.post("/api/simulation/start")
def start_simulation(req: SimulationStartReq):
    # Fetch in background so we don't block the HTTP response
    threading.Thread(target=f1_data.start_simulation, args=(req.session_key, req.speed)).start()
    return {"message": f"Simulation started for session {req.session_key} at {req.speed}x speed"}

class LiveStartReq(BaseModel):
    session_key: str = "latest"

@app.post("/api/live/start")
def start_live(req: LiveStartReq):
    f1_data.set_session(req.session_key, mode="live")
    
    # Start the async live loop in the current asyncio event loop
    loop = asyncio.get_event_loop()
    loop.create_task(f1_data._live_loop())
    
    return {"message": f"Live tracking started for session {req.session_key} using SignalR websocket"}

class SeekReq(BaseModel):
    offset_seconds: int

class SpeedReq(BaseModel):
    speed: float

@app.post("/api/simulation/pause")
def pause_simulation():
    f1_data.pause_simulation()
    return {"message": "Simulation paused"}

@app.post("/api/simulation/resume")
def resume_simulation():
    f1_data.resume_simulation()
    return {"message": "Simulation resumed"}

@app.post("/api/simulation/seek")
def seek_simulation(req: SeekReq):
    f1_data.seek_simulation(req.offset_seconds)
    return {"message": f"Simulation seeked to offset {req.offset_seconds} seconds"}

@app.post("/api/simulation/speed")
def speed_simulation(req: SpeedReq):
    f1_data.set_simulation_speed(req.speed)
    return {"message": f"Simulation speed set to {req.speed}x"}

@app.get("/api/simulation/sessions")
def get_simulation_sessions():
    import datetime
    now = datetime.datetime.now(datetime.timezone.utc).isoformat()
    completed_sessions = []
    with f1_data.lock:
        for s in f1_data.schedule:
            de = s.get('date_end')
            if de and de.replace('Z', '+00:00') < now:
                completed_sessions.append({
                    "session_key": s.get("session_key"),
                    "session_name": s.get("session_name"),
                    "session_type": s.get("session_type"),
                    "location": s.get("location"),
                    "country_name": s.get("country_name"),
                    "date_start": s.get("date_start"),
                    "date_end": s.get("date_end")
                })
    return completed_sessions

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
