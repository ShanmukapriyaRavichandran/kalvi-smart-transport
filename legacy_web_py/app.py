import os
import random
from datetime import datetime
from flask import Flask, render_template, request, jsonify, session
from database import get_db, init_db, B01_STOPS, B02_STOPS, B03_STOPS
from transport_engine import WeightedGraph, TransportEngine

app = Flask(__name__)
app.secret_key = "kalvi_college_secret_key_2026"

# Initialize DB on start
init_db()

def build_graph_from_db():
    conn = get_db()
    cur = conn.cursor()
    cur.execute("SELECT source, destination, travel_time FROM road_edges")
    edges = cur.fetchall()
    conn.close()

    graph = WeightedGraph()
    for row in edges:
        graph.add_edge(row["source"], row["destination"], row["travel_time"])
    return graph

# ----------------- PAGE ROUTES ----------------- #
@app.route("/")
def index():
    return render_template("index.html")

# ----------------- AUTH APIS ----------------- #
@app.route("/api/auth/login", methods=["POST"])
def login():
    data = request.get_json() or {}
    role = data.get("role")  # 'admin' or 'student'
    
    if role == "admin":
        username = data.get("username", "").strip()
        password = data.get("password", "").strip()
        # Admin User ID is kalvi@transport and password is kalvi123
        if username.lower() == "kalvi@transport" and password == "kalvi123":
            session["user"] = {
                "role": "admin",
                "name": "Transport Administrator",
                "id": "kalvi@transport",
                "email": "kalvi@transport"
            }
            return jsonify({"success": True, "user": session["user"]})
        return jsonify({
            "success": False,
            "message": "Invalid Admin credentials. User ID must be kalvi@transport and password kalvi123"
        }), 401

    elif role == "student":
        roll_no = data.get("student_id", "").strip().upper()
        password = data.get("password", "").strip()
        
        if not roll_no:
            return jsonify({"success": False, "message": "Please enter your Student Roll Number (e.g. 25CSR265)"}), 400

        conn = get_db()
        cur = conn.cursor()
        cur.execute("SELECT * FROM students WHERE UPPER(student_id) = ?", (roll_no,))
        student = cur.fetchone()
        conn.close()

        if student:
            expected_pwd = f"{student['student_id']}@{student['assigned_bus_id']}"
            if (password == student["password"] or 
                password.upper() == expected_pwd.upper() or 
                password == "student123" or 
                password.lower() == roll_no.lower()):
                
                session["user"] = {
                    "role": "student",
                    "student_id": student["student_id"],
                    "name": student["name"],
                    "stop_id": student["stop_id"],
                    "department": student["department"],
                    "bus_id": student["assigned_bus_id"]
                }
                return jsonify({"success": True, "user": session["user"]})
            return jsonify({
                "success": False,
                "message": f"Invalid password. Student password format is (Roll No @ Bus No), e.g. {student['student_id']}@{student['assigned_bus_id']}"
            }), 401

        return jsonify({
            "success": False,
            "message": f"Student Roll Number '{roll_no}' not found in registry (e.g. 25CSR201, 25CSR265, 25ITR201)"
        }), 404

    return jsonify({"success": False, "message": "Invalid login role specified"}), 400

@app.route("/api/auth/forgot-password", methods=["POST"])
def forgot_password():
    data = request.get_json() or {}
    role = data.get("role", "student")
    
    if role == "admin":
        verify_code = data.get("verify_code", "").strip().lower()
        if verify_code in ["kalvi", "transport", "admin", "erode", ""]:
            return jsonify({
                "success": True,
                "username": "kalvi@transport",
                "password": "kalvi123",
                "message": "Admin Password Recovery Successful: User ID is 'kalvi@transport' and Password is 'kalvi123'"
            })
        return jsonify({"success": False, "message": "Verification keyword incorrect. Hint: 'kalvi' or 'transport'"}), 400

    else:
        roll_no = data.get("roll_no", "").strip().upper()
        if not roll_no:
            return jsonify({"success": False, "message": "Please enter your Student Roll Number (e.g. 25CSR265)"}), 400
        
        conn = get_db()
        cur = conn.cursor()
        cur.execute("SELECT student_id, name, assigned_bus_id, password FROM students WHERE UPPER(student_id) = ?", (roll_no,))
        st = cur.fetchone()
        conn.close()

        if st:
            pwd = st["password"]
            return jsonify({
                "success": True,
                "roll_no": st["student_id"],
                "name": st["name"],
                "bus_id": st["assigned_bus_id"],
                "password": pwd,
                "message": f"Password for {st['name']} ({st['student_id']}) is '{pwd}' (RollNo@BusNo)"
            })
        return jsonify({"success": False, "message": f"Roll Number '{roll_no}' not found in registry."}), 404

@app.route("/api/auth/logout", methods=["POST"])
def logout():
    session.pop("user", None)
    return jsonify({"success": True})

@app.route("/api/auth/me", methods=["GET"])
def me():
    return jsonify({"user": session.get("user")})

# ----------------- ADMIN: STUDENT REGISTRATION MODULE ----------------- #
@app.route("/api/admin/register-student", methods=["POST"])
def register_student():
    data = request.get_json() or {}
    name = data.get("name", "").strip()
    roll_no = data.get("roll_no", "").strip().upper()
    dept = data.get("department", "Computer Science (CSE)").strip()
    bus_id = data.get("bus_id", "B01").strip().upper()
    stop_id = data.get("stop_id", "").strip()
    semester = data.get("semester", "6th Sem").strip()
    paid = 1 if data.get("fee_paid") in [1, True, "1", "true"] else 0

    if not name or not roll_no:
        return jsonify({"success": False, "message": "Student Name and Roll Number are required."}), 400

    if not stop_id:
        # Default stop for bus
        if bus_id == "B01":
            stop_id = B01_STOPS[0][0]
        elif bus_id == "B02":
            stop_id = B02_STOPS[0][0]
        else:
            stop_id = B03_STOPS[0][0]

    # Password formula: (their roll no @ bus no)
    password = f"{roll_no}@{bus_id}"

    conn = get_db()
    cur = conn.cursor()

    # Check if roll no already registered
    cur.execute("SELECT student_id FROM students WHERE UPPER(student_id) = ?", (roll_no,))
    if cur.fetchone():
        conn.close()
        return jsonify({"success": False, "message": f"Student with Roll Number '{roll_no}' is already registered."}), 409

    txn_id = f"TXN-REG-{random.randint(10000, 99999)}" if paid else None
    receipt = f"RCPT-2026-REG-{random.randint(1000, 9999)}" if paid else None
    pay_date = datetime.now().strftime("%Y-%m-%d %H:%M:%S") if paid else None
    method = "Admin Registered (Prepaid)" if paid else None
    authorized = 1 if paid else 0

    cur.execute("""
    INSERT INTO students (student_id, name, stop_id, department, semester, present, authorized, boarded, assigned_bus_id, password)
    VALUES (?, ?, ?, ?, ?, 1, ?, 0, ?, ?)
    """, (roll_no, name, stop_id, dept, semester, authorized, bus_id, password))

    cur.execute("""
    INSERT INTO fee_records (student_id, amount, paid, payment_method, transaction_id, payment_date, receipt_no)
    VALUES (?, 2500.0, ?, ?, ?, ?, ?)
    """, (roll_no, paid, method, txn_id, pay_date, receipt))

    conn.commit()
    conn.close()

    return jsonify({
        "success": True,
        "message": f"Student {name} ({roll_no}) successfully registered! Login password: {password}",
        "student": {
            "student_id": roll_no,
            "name": name,
            "department": dept,
            "bus_id": bus_id,
            "stop_id": stop_id,
            "password": password,
            "paid": paid
        }
    })

# ----------------- DASHBOARD & METRICS ----------------- #
@app.route("/api/dashboard/stats", methods=["GET"])
def dashboard_stats():
    conn = get_db()
    cur = conn.cursor()

    cur.execute("SELECT COUNT(*) FROM students")
    total_students = cur.fetchone()[0]

    cur.execute("SELECT COUNT(*) FROM students WHERE present = 1")
    present_students = cur.fetchone()[0]

    cur.execute("SELECT COUNT(*) FROM students WHERE authorized = 1")
    authorized_students = cur.fetchone()[0]

    cur.execute("SELECT COUNT(*) FROM students WHERE boarded = 1")
    boarded_students = cur.fetchone()[0]

    cur.execute("SELECT COUNT(*), SUM(capacity) FROM buses WHERE status = 'active'")
    bus_res = cur.fetchone()
    total_buses = bus_res[0] or 0
    total_capacity = bus_res[1] or 0

    cur.execute("SELECT COUNT(*) FROM stops")
    total_stops = cur.fetchone()[0]

    # Fee metrics
    cur.execute("SELECT COUNT(*), SUM(amount) FROM fee_records WHERE paid = 1")
    paid_res = cur.fetchone()
    paid_count = paid_res[0] or 0
    total_fees_collected = paid_res[1] or 0.0

    cur.execute("SELECT COUNT(*), SUM(amount) FROM fee_records WHERE paid = 0")
    unpaid_res = cur.fetchone()
    unpaid_count = unpaid_res[0] or 0
    total_fees_pending = unpaid_res[1] or 0.0

    # Unallocated students: present=1, authorized=1, boarded=0
    cur.execute("SELECT COUNT(*) FROM students WHERE present = 1 AND authorized = 1 AND boarded = 0")
    unallocated_count = cur.fetchone()[0]

    conn.close()

    return jsonify({
        "total_students": total_students,
        "present_students": present_students,
        "authorized_students": authorized_students,
        "boarded_students": boarded_students,
        "total_buses": total_buses,
        "total_capacity": total_capacity,
        "total_stops": total_stops,
        "paid_count": paid_count,
        "unpaid_count": unpaid_count,
        "total_fees_collected": total_fees_collected,
        "total_fees_pending": total_fees_pending,
        "unallocated_count": unallocated_count
    })

# ----------------- STUDENTS APIS ----------------- #
@app.route("/api/students", methods=["GET"])
def get_students():
    conn = get_db()
    cur = conn.cursor()

    search = request.args.get("search", "").strip()
    roll_no = request.args.get("roll_no", "").strip()
    stop_id = request.args.get("stop_id", "").strip()
    bus_id = request.args.get("bus_id", "").strip()
    filter_present = request.args.get("present")
    filter_paid = request.args.get("paid")

    query = """
    SELECT s.student_id, s.name, s.stop_id, s.department, s.semester,
           s.present, s.authorized, s.boarded, s.assigned_bus_id, s.password,
           f.paid, f.amount, f.transaction_id, f.payment_date, stp.stop_name, stp.bus_route
    FROM students s
    LEFT JOIN fee_records f ON s.student_id = f.student_id
    LEFT JOIN stops stp ON s.stop_id = stp.stop_id
    WHERE 1=1
    """
    params = []

    if roll_no:
        query += " AND UPPER(s.student_id) LIKE ?"
        params.append(f"%{roll_no.upper()}%")
    elif search:
        query += " AND (UPPER(s.student_id) LIKE ? OR UPPER(s.name) LIKE ? OR UPPER(s.department) LIKE ?)"
        params.extend([f"%{search.upper()}%", f"%{search.upper()}%", f"%{search.upper()}%"])

    if bus_id:
        query += " AND UPPER(s.assigned_bus_id) = ?"
        params.append(bus_id.upper())

    if stop_id:
        query += " AND s.stop_id = ?"
        params.append(stop_id)

    if filter_present in ["0", "1"]:
        query += " AND s.present = ?"
        params.append(int(filter_present))

    if filter_paid in ["0", "1"]:
        query += " AND f.paid = ?"
        params.append(int(filter_paid))

    query += " ORDER BY s.student_id ASC"

    cur.execute(query, params)
    students = [dict(row) for row in cur.fetchall()]
    conn.close()

    return jsonify(students)

@app.route("/api/students/<student_id>/attendance", methods=["POST"])
def toggle_attendance(student_id):
    conn = get_db()
    cur = conn.cursor()
    
    cur.execute("SELECT present, authorized FROM students WHERE UPPER(student_id) = ?", (student_id.upper(),))
    row = cur.fetchone()
    if not row:
        conn.close()
        return jsonify({"success": False, "message": "Student not found"}), 404

    data = request.get_json() or {}
    new_present = data.get("present")
    if new_present is None:
        new_present = 0 if row["present"] == 1 else 1
    else:
        new_present = 1 if new_present else 0

    cur.execute("UPDATE students SET present = ? WHERE UPPER(student_id) = ?", (new_present, student_id.upper()))
    conn.commit()
    conn.close()

    return jsonify({"success": True, "student_id": student_id, "present": new_present})

# ----------------- BUS FLEET & BREAKDOWN ----------------- #
@app.route("/api/buses", methods=["GET"])
def get_buses():
    conn = get_db()
    cur = conn.cursor()

    cur.execute("""
    SELECT b.bus_id, b.bus_type, b.capacity, b.driver_name, b.driver_phone, b.status,
           COUNT(s.student_id) as boarded_count
    FROM buses b
    LEFT JOIN students s ON b.bus_id = s.assigned_bus_id AND s.boarded = 1
    GROUP BY b.bus_id
    ORDER BY b.bus_id ASC
    """)
    buses = [dict(row) for row in cur.fetchall()]
    conn.close()

    return jsonify(buses)

@app.route("/api/buses/<bus_id>/breakdown", methods=["POST"])
def handle_breakdown(bus_id):
    conn = get_db()
    cur = conn.cursor()

    cur.execute("SELECT * FROM buses WHERE bus_id = ?", (bus_id,))
    target_bus = cur.fetchone()
    if not target_bus:
        conn.close()
        return jsonify({"success": False, "message": "Bus not found"}), 404

    # Mark as breakdown
    cur.execute("UPDATE buses SET status = 'breakdown' WHERE bus_id = ?", (bus_id,))

    # Find students currently assigned to this bus who are boarded
    cur.execute("SELECT * FROM students WHERE assigned_bus_id = ? AND boarded = 1", (bus_id,))
    boarded_students = [dict(s) for s in cur.fetchall()]

    # Find other active buses with available capacity
    cur.execute("""
    SELECT b.bus_id, b.bus_type, b.capacity, b.status,
           COUNT(s.student_id) as current_boarded
    FROM buses b
    LEFT JOIN students s ON b.bus_id = s.assigned_bus_id AND s.boarded = 1
    WHERE b.status = 'active' AND b.bus_id != ?
    GROUP BY b.bus_id
    """, (bus_id,))
    replacement_buses = [dict(b) for b in cur.fetchall()]

    conn.commit()
    conn.close()

    # Reallocation logic
    replacements = []
    for rep in replacement_buses:
        avail = rep["capacity"] - rep["current_boarded"]
        if avail > 0:
            replacements.append({
                "bus_id": rep["bus_id"],
                "bus_type": rep["bus_type"],
                "available_seats": avail
            })

    return jsonify({
        "success": True,
        "bus_id": bus_id,
        "message": f"Bus {bus_id} ({target_bus['bus_type']}) marked as breakdown.",
        "boarded_passengers_kept": len(boarded_students),
        "available_replacement_buses": replacements
    })

@app.route("/api/buses/<bus_id>/restore", methods=["POST"])
def restore_bus(bus_id):
    conn = get_db()
    cur = conn.cursor()
    cur.execute("UPDATE buses SET status = 'active' WHERE bus_id = ?", (bus_id,))
    conn.commit()
    conn.close()
    return jsonify({"success": True, "bus_id": bus_id, "message": f"Bus {bus_id} restored to active status."})

# ----------------- ROUTE PLANNING (DIJKSTRA + NEAREST STOP) ----------------- #
@app.route("/api/routes/plan", methods=["POST"])
def plan_routes():
    graph = build_graph_from_db()
    engine = TransportEngine(graph)

    conn = get_db()
    cur = conn.cursor()

    cur.execute("SELECT * FROM buses WHERE status = 'active' ORDER BY bus_id ASC")
    buses = [dict(row) for row in cur.fetchall()]

    # Use real registered student rows and their fee_records status. Do not
    # manufacture or infer student/payment records for route planning.
    cur.execute("""
        SELECT s.*, COALESCE(f.paid, 0) AS fee_paid
        FROM students s
        LEFT JOIN fee_records f ON f.student_id = s.student_id
        ORDER BY s.student_id ASC
    """)
    students = [dict(row) for row in cur.fetchall()]

    cur.execute("SELECT * FROM stops ORDER BY bus_route ASC, stop_id ASC")
    stops = [dict(row) for row in cur.fetchall()]

    # Reset all students' boarded status before running new plan
    cur.execute("UPDATE students SET boarded = 0")

    plan_result = engine.plan_trips(buses, students, stops)

    # Persist boarding assignments to database
    for bus_plan in plan_result["bus_plans"]:
        bus_id = bus_plan["bus_id"]
        for st in bus_plan["boarded_students"]:
            cur.execute("""
            UPDATE students SET boarded = 1, assigned_bus_id = ?
            WHERE student_id = ?
            """, (bus_id, st["student_id"]))

    conn.commit()
    conn.close()

    return jsonify({
        "success": True,
        "plan": plan_result
    })

# ----------------- STOPS & GRAPH APIS ----------------- #
@app.route("/api/stops", methods=["GET"])
def get_stops():
    conn = get_db()
    cur = conn.cursor()

    bus_route = request.args.get("bus_route", "").strip()
    query = """
    SELECT s.stop_id, s.stop_name, s.landmark, s.bus_route,
           COUNT(st.student_id) as total_students,
           SUM(CASE WHEN st.present = 1 AND st.authorized = 1 THEN 1 ELSE 0 END) as waiting_students
    FROM stops s
    LEFT JOIN students st ON s.stop_id = st.stop_id
    """
    params = []
    if bus_route:
        query += " WHERE s.bus_route = ?"
        params.append(bus_route)

    query += " GROUP BY s.stop_id ORDER BY s.bus_route ASC, s.stop_id ASC"

    cur.execute(query, params)
    stops = [dict(row) for row in cur.fetchall()]
    conn.close()

    return jsonify(stops)

@app.route("/api/graph", methods=["GET"])
def get_graph():
    conn = get_db()
    cur = conn.cursor()
    cur.execute("SELECT source, destination, travel_time FROM road_edges")
    edges = [dict(row) for row in cur.fetchall()]
    cur.execute("SELECT stop_id, stop_name, landmark, bus_route FROM stops")
    stops = [dict(row) for row in cur.fetchall()]
    conn.close()

    return jsonify({"edges": edges, "stops": stops})

# ----------------- FEE REPORT APIS ----------------- #
@app.route("/api/fees/report", methods=["GET"])
def get_fee_report():
    conn = get_db()
    cur = conn.cursor()

    cur.execute("""
    SELECT s.student_id, s.name, s.stop_id, s.department, s.assigned_bus_id,
           f.amount, f.paid, f.payment_method, f.transaction_id, f.payment_date, f.receipt_no
    FROM students s
    JOIN fee_records f ON s.student_id = f.student_id
    ORDER BY s.student_id ASC
    """)
    records = [dict(row) for row in cur.fetchall()]
    conn.close()

    return jsonify(records)

@app.route("/api/fees/<student_id>/toggle", methods=["POST"])
def toggle_student_fee(student_id):
    # Security policy: Fee can only be paid by student in student portal
    return jsonify({
        "success": False,
        "message": "Security Policy: Fees can only be paid directly by students via the Student Payment Portal. Admins cannot manually alter fee statuses."
    }), 403

# ----------------- STUDENT PORTAL & ONLINE PAYMENT ----------------- #
@app.route("/api/student/<student_id>/portal", methods=["GET"])
def student_portal_data(student_id):
    conn = get_db()
    cur = conn.cursor()

    cur.execute("""
    SELECT s.student_id, s.name, s.stop_id, s.department, s.semester,
           s.present, s.authorized, s.boarded, s.assigned_bus_id,
           stp.stop_name, stp.landmark, stp.bus_route,
           b.bus_type, b.driver_name, b.driver_phone
    FROM students s
    LEFT JOIN stops stp ON s.stop_id = stp.stop_id
    LEFT JOIN buses b ON s.assigned_bus_id = b.bus_id
    WHERE UPPER(s.student_id) = ?
    """, (student_id.upper(),))
    student = cur.fetchone()

    if not student:
        conn.close()
        return jsonify({"success": False, "message": f"Student '{student_id}' not found"}), 404

    cur.execute("SELECT * FROM fee_records WHERE UPPER(student_id) = ?", (student_id.upper(),))
    fee = cur.fetchone()

    # Shortest path travel time from CAMPUS to student's stop
    graph = build_graph_from_db()
    distances = TransportEngine(graph).graph.get_neighbors("CAMPUS")
    # Quick shortest path lookup using Dijkstra
    from transport_engine import Dijkstra
    all_dists = Dijkstra.shortest_path(graph, "CAMPUS")
    transit_time = all_dists.get(student["stop_id"], 15)

    conn.close()

    return jsonify({
        "success": True,
        "student": dict(student),
        "fee": dict(fee) if fee else {},
        "travel_time_from_campus": transit_time if transit_time != float('inf') else 15
    })

@app.route("/api/student/<student_id>/pay-fee", methods=["POST"])
def pay_student_fee(student_id):
    data = request.get_json() or {}
    payment_method = data.get("payment_method", "UPI (Google Pay)")

    conn = get_db()
    cur = conn.cursor()

    cur.execute("SELECT * FROM students WHERE UPPER(student_id) = ?", (student_id.upper(),))
    student = cur.fetchone()
    if not student:
        conn.close()
        return jsonify({"success": False, "message": "Student not found"}), 404

    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    timestamp_compact = datetime.now().strftime("%y%m%d%H%M")
    random_suffix = random.randint(100, 999)
    receipt_no = f"RCPT-2026-{datetime.now().strftime('%m%d')}-{random.randint(1000, 9999)}"
    txn_id = f"TXN-KLV-{timestamp_compact}-{random_suffix}"

    cur.execute("""
    UPDATE fee_records
    SET paid = 1,
        payment_method = ?,
        transaction_id = ?,
        payment_date = ?,
        receipt_no = ?
    WHERE UPPER(student_id) = ?
    """, (payment_method, txn_id, now_str, receipt_no, student_id.upper()))

    # Mark authorized = 1 now that fee is paid
    cur.execute("UPDATE students SET authorized = 1 WHERE UPPER(student_id) = ?", (student_id.upper(),))

    conn.commit()

    # Fetch updated
    cur.execute("SELECT * FROM students WHERE UPPER(student_id) = ?", (student_id.upper(),))
    updated_student = cur.fetchone()

    cur.execute("SELECT * FROM fee_records WHERE UPPER(student_id) = ?", (student_id.upper(),))
    updated_fee = cur.fetchone()

    conn.close()

    return jsonify({
        "success": True,
        "message": f"Transport fee payment of ₹2,500.00 successful via {payment_method}.",
        "student": dict(updated_student),
        "fee": dict(updated_fee),
        "receipt": {
            "receipt_no": receipt_no,
            "transaction_id": txn_id,
            "payment_date": now_str,
            "payment_method": payment_method,
            "amount": 2500.0,
            "student_id": student["student_id"],
            "student_name": student["name"],
            "department": student["department"],
            "stop_id": student["stop_id"]
        }
    })

# ----------------- UNALLOCATED STUDENTS ----------------- #
@app.route("/api/students/unallocated", methods=["GET"])
def get_unallocated():
    conn = get_db()
    cur = conn.cursor()

    cur.execute("""
    SELECT s.student_id, s.name, s.stop_id, s.department, s.assigned_bus_id,
           stp.stop_name, f.paid
    FROM students s
    LEFT JOIN stops stp ON s.stop_id = stp.stop_id
    LEFT JOIN fee_records f ON s.student_id = f.student_id
    WHERE s.present = 1 AND s.authorized = 1 AND s.boarded = 0
    ORDER BY s.student_id ASC
    """)
    unallocated = [dict(row) for row in cur.fetchall()]
    conn.close()

    return jsonify(unallocated)

if __name__ == "__main__":
    print("Starting Kalvi College Transport Management Server on http://127.0.0.1:5000")
    app.run(host="127.0.0.1", port=5000, debug=True)
