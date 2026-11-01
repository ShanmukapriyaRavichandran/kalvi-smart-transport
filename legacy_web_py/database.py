import sqlite3
import os
from datetime import datetime

DB_PATH = os.path.join(os.path.dirname(__file__), "kalvi_transport.db")

SAMPLE_STUDENT_NAMES = [
    "Aarav Sharma", "Aditi Patel", "Akash Nair", "Ananya Reddy",
    "Bhavin Kumar", "Chandini Rao", "Deepak Verma", "Divya Menon",
    "Gautam Iyer", "Harini Sundaram", "Ishaan Joshi", "Jaya Priya",
    "Karthik Subramanian", "Kavya Pillai", "Madhavan Swamy", "Meera Krishnan",
    "Naveen Rajan", "Nandhini Mohan", "Pranav Hegde", "Pooja Venkatesh",
    "Rahul Deshmukh", "Rithika Nambiar", "Sai Charan", "Sangeetha Natarajan",
    "Sanjay Varma", "Shreya Sen", "Siddharth Bhat", "Sneha Kulkarni",
    "Surya Prakash", "Swathi Narayanan", "Tarun Kapoor", "Tejaswini Patil",
    "Uday Kiran", "Uma Maheshwari", "Varun Chandran", "Vidya Balan",
    "Vignesh Balaji", "Vinitha Sekar", "Yashwant Das", "Yuktha Srinivas"
]

DEPARTMENTS = [
    "Computer Science (CSE)",
    "Information Technology (IT)",
    "Electronics & Communication (ECE)",
    "Mechanical Engineering (MECH)",
    "AI & Data Science (AIDS)",
    "Civil Engineering (CIVIL)",
    "Electrical & Electronics (EEE)"
]

# 30 SEPARATE STOPS NEAR ERODE (10 DISTINCT STOPS PER BUS)
B01_STOPS = [
    ("Erode Bus Stand", "Erode Bus Stand Terminal", "B01"),
    ("Brough Road", "Near Clock Tower Junction", "B01"),
    ("GH Roundana", "Government Hospital Circle", "B01"),
    ("Paneerselvam Park", "Opposite Municipality Complex", "B01"),
    ("Karungalpalayam", "Near Cauvery River Bridge", "B01"),
    ("Pallipalayam", "Cauvery RS Main Road", "B01"),
    ("Kumarapalayam", "Bypass Service Road Entry", "B01"),
    ("Bhavani Old Bus Stand", "Near Sangameshwarar Temple Cross", "B01"),
    ("Kalingarayanpalayam", "Kalingarayan Canal Bridge", "B01"),
    ("Chithode Junction", "Salem-CBE Highway Entry", "B01"),
]

B02_STOPS = [
    ("Solar Pirivu", "Karur Road Junction", "B02"),
    ("Lakkapuram", "Near Panchayat Office", "B02"),
    ("Kollampalayam Bypass", "Railway Overbridge Entry", "B02"),
    ("Railway Station Gate", "Erode Junction Station Gate 2", "B02"),
    ("Marapalam", "Near Old Market Road", "B02"),
    ("Surampatti Valasu", "Surampatti Four Roads", "B02"),
    ("Thindal Murugan Temple", "Thindal Hill Foot Arch", "B02"),
    ("Villarasampatti", "Near Nallagoundenpalayam Cross", "B02"),
    ("Perundurai Old Bus Stand", "Near Taluk Office", "B02"),
    ("SIPCOT Perundurai Gate", "Industrial Park Main Gate", "B02"),
]

B03_STOPS = [
    ("Modakkurichi Town", "Modakkurichi Bus Shelter", "B03"),
    ("Elumathur Junction", "Kandikattu Valasu Road", "B03"),
    ("Sivagiri Bus Stop", "Near Amman Temple Arch", "B03"),
    ("Kodumudi Road Pirivu", "Near Railway Crossing", "B03"),
    ("Muthur Corner", "Near Vellakovil Road Cross", "B03"),
    ("Chennimalai Bus Stand", "Murugan Temple Foot Terminal", "B03"),
    ("Ingur Industrial Area", "Railway Feeder Road", "B03"),
    ("Vijayamangalam Toll", "NH 544 Toll Plaza Point", "B03"),
    ("Uthukuli Road", "Near Sanitorium Junction", "B03"),
    ("Nasiyanur Junction", "Perundurai-Erode Bypass Cut", "B03"),
]

ALL_STOPS = B01_STOPS + B02_STOPS + B03_STOPS

ROAD_EDGES = [
    # Direct Campus connections to arterial route anchors
    ("CAMPUS", "Erode Bus Stand", 10),
    ("CAMPUS", "Solar Pirivu", 11),
    ("CAMPUS", "Modakkurichi Town", 13),
    ("CAMPUS", "Thindal Murugan Temple", 8),
    ("CAMPUS", "Chithode Junction", 14),

    # Bus 1 (B01) Erode North route connections
    ("Erode Bus Stand", "Brough Road", 3),
    ("Brough Road", "GH Roundana", 4),
    ("GH Roundana", "Paneerselvam Park", 3),
    ("Paneerselvam Park", "Karungalpalayam", 5),
    ("Karungalpalayam", "Pallipalayam", 4),
    ("Pallipalayam", "Kumarapalayam", 8),
    ("Kumarapalayam", "Bhavani Old Bus Stand", 6),
    ("Bhavani Old Bus Stand", "Kalingarayanpalayam", 5),
    ("Kalingarayanpalayam", "Chithode Junction", 6),

    # Bus 2 (B02) Erode South route connections
    ("Solar Pirivu", "Lakkapuram", 4),
    ("Lakkapuram", "Kollampalayam Bypass", 5),
    ("Kollampalayam Bypass", "Railway Station Gate", 4),
    ("Railway Station Gate", "Marapalam", 3),
    ("Marapalam", "Surampatti Valasu", 5),
    ("Surampatti Valasu", "Thindal Murugan Temple", 6),
    ("Thindal Murugan Temple", "Villarasampatti", 5),
    ("Villarasampatti", "Perundurai Old Bus Stand", 7),
    ("Perundurai Old Bus Stand", "SIPCOT Perundurai Gate", 4),

    # Bus 3 (B03) Erode Rural & Industrial route connections
    ("Modakkurichi Town", "Elumathur Junction", 6),
    ("Elumathur Junction", "Sivagiri Bus Stop", 7),
    ("Sivagiri Bus Stop", "Kodumudi Road Pirivu", 5),
    ("Kodumudi Road Pirivu", "Muthur Corner", 6),
    ("Muthur Corner", "Chennimalai Bus Stand", 8),
    ("Chennimalai Bus Stand", "Ingur Industrial Area", 6),
    ("Ingur Industrial Area", "Vijayamangalam Toll", 7),
    ("Vijayamangalam Toll", "Uthukuli Road", 5),
    ("Uthukuli Road", "Nasiyanur Junction", 6),

    # Cross connections for Dijkstra algorithm
    ("Chithode Junction", "Nasiyanur Junction", 7),
    ("Railway Station Gate", "Erode Bus Stand", 4),
    ("Surampatti Valasu", "Solar Pirivu", 5),
    ("SIPCOT Perundurai Gate", "Vijayamangalam Toll", 6),
]

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db(force_reseed=False):
    conn = get_db()
    cur = conn.cursor()

    if force_reseed:
        cur.execute("DROP TABLE IF EXISTS fee_records")
        cur.execute("DROP TABLE IF EXISTS students")
        cur.execute("DROP TABLE IF EXISTS buses")
        cur.execute("DROP TABLE IF EXISTS stops")
        cur.execute("DROP TABLE IF EXISTS road_edges")

    cur.execute("""
    CREATE TABLE IF NOT EXISTS stops (
        stop_id TEXT PRIMARY KEY,
        stop_name TEXT NOT NULL,
        landmark TEXT,
        bus_route TEXT
    )
    """)

    cur.execute("""
    CREATE TABLE IF NOT EXISTS buses (
        bus_id TEXT PRIMARY KEY,
        bus_type TEXT NOT NULL,
        capacity INTEGER NOT NULL,
        driver_name TEXT,
        driver_phone TEXT,
        status TEXT DEFAULT 'active'
    )
    """)

    cur.execute("""
    CREATE TABLE IF NOT EXISTS students (
        student_id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        stop_id TEXT NOT NULL,
        department TEXT,
        semester TEXT DEFAULT '6th Sem',
        present INTEGER DEFAULT 0,
        authorized INTEGER DEFAULT 0,
        boarded INTEGER DEFAULT 0,
        assigned_bus_id TEXT,
        password TEXT NOT NULL,
        FOREIGN KEY (stop_id) REFERENCES stops(stop_id)
    )
    """)

    cur.execute("""
    CREATE TABLE IF NOT EXISTS fee_records (
        student_id TEXT PRIMARY KEY,
        amount REAL NOT NULL DEFAULT 2500.0,
        paid INTEGER NOT NULL DEFAULT 0,
        payment_method TEXT,
        transaction_id TEXT,
        payment_date TEXT,
        receipt_no TEXT,
        FOREIGN KEY (student_id) REFERENCES students(student_id)
    )
    """)

    cur.execute("""
    CREATE TABLE IF NOT EXISTS road_edges (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        source TEXT NOT NULL,
        destination TEXT NOT NULL,
        travel_time INTEGER NOT NULL
    )
    """)

    # Check if stops exist; if not, or if force_reseed, seed data
    cur.execute("SELECT COUNT(*) FROM stops")
    stop_count = cur.fetchone()[0]
    # Also check if S1 is present (old schema), if so force reseed to Erode stops
    cur.execute("SELECT COUNT(*) FROM stops WHERE stop_id = 'S1'")
    old_stops_exist = cur.fetchone()[0] > 0

    if stop_count == 0 or old_stops_exist or force_reseed:
        # Keep student registrations and fee history intact. Rebuild only the
        # road-network tables needed by route planning.
        cur.execute("DELETE FROM buses")
        cur.execute("DELETE FROM stops")
        cur.execute("DELETE FROM road_edges")
        seed_data(cur)

    conn.commit()
    conn.close()

def seed_data(cur):
    # 1. 30 Separate Stops near Erode (10 distinct stops per bus)
    for stop_id, landmark, bus_route in ALL_STOPS:
        cur.execute("""
        INSERT OR REPLACE INTO stops (stop_id, stop_name, landmark, bus_route)
        VALUES (?, ?, ?, ?)
        """, (stop_id, stop_id, landmark, bus_route))

    # 2. 3 Buses: Explicitly Mini Bus or Standard Bus (never standard/mini bus)
    buses = [
        ("B01", "Mini Bus", 12, "R. Murugesan", "+91 98421 11201", "active"),
        ("B02", "Standard Bus", 15, "K. Selvam", "+91 98421 11202", "active"),
        ("B03", "Standard Bus", 15, "S. Arumugam", "+91 98421 11203", "active"),
    ]
    for b in buses:
        cur.execute("""
        INSERT OR REPLACE INTO buses (bus_id, bus_type, capacity, driver_name, driver_phone, status)
        VALUES (?, ?, ?, ?, ?, ?)
        """, b)

    # Student registrations and fee payments are real application data.
    # Do not insert sample/dummy students or fake fee transactions here.
    # Students must be registered through the Student Registration module.

    # 4. Road Edges
    for src, dst, wt in ROAD_EDGES:
        cur.execute("INSERT INTO road_edges (source, destination, travel_time) VALUES (?, ?, ?)",
                    (src, dst, wt))

if __name__ == "__main__":
    init_db(force_reseed=True)
    print("Database initialized successfully at", DB_PATH)
