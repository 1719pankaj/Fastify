from fastapi import FastAPI, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import threading
import time

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

stop_live_poll = threading.Event()

def live_poll_worker():
    while not stop_live_poll.is_set():
        if f1_data.mode == "live" and f1_data.session_key:
            f1_data.fetch_all_data()
        time.sleep(LIVE_POLL_INTERVAL)

@app.on_event("startup")
def startup_event():
    # Start the background poller thread
    t = threading.Thread(target=live_poll_worker, daemon=True)
    t.start()
    
    # Auto-start simulation for testing out-of-the-box
    print(f"Starting default simulation with session {DEFAULT_SIMULATION_SESSION_KEY}...")
    # Doing this in a background thread so we don't block server startup
    threading.Thread(target=f1_data.start_simulation, args=(DEFAULT_SIMULATION_SESSION_KEY, 10.0)).start()

@app.on_event("shutdown")
def shutdown_event():
    stop_live_poll.set()

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
    # trigger an immediate fetch
    threading.Thread(target=f1_data.fetch_all_data).start()
    return {"message": f"Live tracking started for session {req.session_key}"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
