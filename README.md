# 🚌 Kalvi College Smart Transport Management System
### Autonomous Dijkstra Route Optimizer & Fleet Management Platform

[![Deploy with Vercel](https://vercel.com/button)](https://vercel.com/new)
![License](https://img.shields.io/badge/License-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Node](https://img.shields.io/badge/Node.js-24+-green.svg)
![Vercel](https://img.shields.io/badge/Deploy-Vercel-black.svg)

An intelligent, autonomous college transit platform that integrates **Dijkstra algorithm optimization**, real-time **OSRM road routing**, and **OpenStreetMap (Leaflet)** mapping to provide dynamic hazard detours, role-based workflows, and fleet management for students, drivers, and transport administrators.

---

## 🌟 Key Features

- **Autonomous Dijkstra Routing Engine**:
  - Dynamically calculates shortest path itineraries connecting all designated stops to the campus depot.
  - Automatically identifies hazards (heavy traffic, construction, road closures) and computes alternative detour bypasses in real time.
- **Real Road Geometries with OSRM & OpenStreetMap**:
  - Leg-by-leg road geometry rendered with Leaflet and OSRM (Open Source Routing Machine), avoiding artificial straight-line segments.
- **Role-Based Portals**:
  - 🎓 **Student Portal**: View assigned bus routes, pickup schedules, campus arrival ETA, fellow stop members, and update registered pickup locations.
  - 🚌 **Driver Module**: Interactive turn-by-turn driving itinerary, stop checklist with passenger counts, morning pickup and evening drop route toggles.
  - 🛡️ **Admin Dashboard**: Real-time fleet health metrics, student registration approvals, driver license verification, stop management, and live road condition hazard simulators.
- **Dual Runtime Support**:
  - **Vercel Cloud Deployment**: High-performance Serverless Function backend in Node.js/Express with zero external setup required.
  - **Local Java Engine**: Native high-concurrency Java 21 web server with SQLite database and virtual thread execution (`run.bat`).

---

## 🔑 Demo Access Credentials

| Portal | Identifier / Username | Password | Notes |
| :--- | :--- | :--- | :--- |
| **🛡️ Admin Dashboard** | `admin` | `admin123` | Full management, approvals & hazard simulation |
| **🚌 Driver Module** | `driver1` | `driver123` | Assigned to Bus B01 |
| **🎓 Student Portal** | `26CSR001` | `student123` | Verified 3rd Year CSE student |

---

## 🏗️ Project Architecture

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

## 🚀 Getting Started

### Option 1: Run with Node.js
```bash
# 1. Install dependencies
npm install

# 2. Start the local server
npm start

# 3. Open in your browser
http://localhost:8080
```

### Option 2: Run with Java & SQLite (Windows)
Double-click `run.bat` or run:
```bat
javac -cp "lib/*;src" -d "bin" src\DatabaseManager.java src\Dijkstra.java src\TransportServer.java src\Main.java
java -cp "lib/*;bin" Main
```
Navigate to `http://localhost:8080`.

---

## ☁️ Deployment on Vercel

This repository is optimized for one-click deployment on **Vercel**:
1. Push this repository to your GitHub account.
2. Go to [Vercel Dashboard](https://vercel.com/new).
3. Import the repository and click **Deploy**.
4. Vercel automatically deploys the static assets and mounts `/api/*` to the serverless backend.

---

## 📄 License
This project is licensed under the MIT License.
