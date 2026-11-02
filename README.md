#  Kalvi College Transport Management System
### Autonomous Dijkstra Route Optimizer & Fleet Management Platform




##  Key Features

- **Autonomous Dijkstra Routing Engine**:
  - Dynamically calculates shortest path itineraries connecting all designated stops to the campus depot.
  - Automatically identifies hazards (heavy traffic, construction, road closures) and computes alternative detour bypasses in real time.
- **Real Road Geometries with OSRM & OpenStreetMap**:
  - Leg-by-leg road geometry rendered with Leaflet and OSRM (Open Source Routing Machine), avoiding artificial straight-line segments.
- **Role-Based Portals**:
  -  **Student Portal**: View assigned bus routes, pickup schedules, campus arrival ETA, fellow stop members, and update registered pickup locations.
  -  **Driver Module**: Interactive turn-by-turn driving itinerary, stop checklist with passenger counts, morning pickup and evening drop route toggles.
  -  **Admin Dashboard**: Real-time fleet health metrics, student registration approvals, driver license verification, stop management, and live road condition hazard simulators.
- **Dual Runtime Support**:
  - **Local Java Engine**: Native high-concurrency Java 21 web server with SQLite database and virtual thread execution (`run.bat`).

---










---

## 📄 License
This project is licensed under the MIT License.

