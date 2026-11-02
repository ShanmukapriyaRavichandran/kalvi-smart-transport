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
  - **Vercel Cloud Deployment**: High-performance Serverless Function backend in Node.js/Express with zero external setup required.
  - **Local Java Engine**: Native high-concurrency Java 21 web server with SQLite database and virtual thread execution (`run.bat`).

---

##  Demo Access Credentials

| Portal | Identifier / Username | Password | Notes |
| :--- | :--- | :--- | :--- |
| ** Admin Dashboard** | `admin` | `admin123` | Full management, approvals & hazard simulation |
| ** Driver Module** | `driver1` | `driver123` | Assigned to Bus B01 |
| ** Student Portal** | `26CSR001` | `student123` | Verified 3rd Year CSE student |

---

##  Project Architecture

```
├── api/
│   └── index.js              # Vercel Serverless Function & Express API Backend
├── public/                   # Static web assets served by Vercel
│   ├── index.html            # Single Page Application (Leaflet, UI)
│   ├── style.css             # Responsive styling & themes
│   └── app.js                # Frontend client logic & API interactions
├── src/                      # Java backend source code
│   ├── Main.java             # Java console entry point
│   ├── TransportServer.java  # Java HTTP server & REST API
│   ├── DatabaseManager.java  # SQLite JDBC persistence layer
│   └── Dijkstra.java         # Weighted graph & routing algorithms
├── lib/                      # SQLite JDBC, MySQL, SLF4J Java JARs
├── web/                      # Source web templates and static assets
├── kalvi_transport.db        # SQLite database
├── initial_data.json         # Seed dataset for instant deployment
├── server.js                 # Standalone Node.js Express server
├── run.bat                   # 1-click Windows compilation & startup script
└── vercel.json               # Vercel deployment configuration & URL rewrites
```

---






---

## 📄 License
This project is licensed under the MIT License.

