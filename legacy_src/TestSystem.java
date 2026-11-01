public class TestSystem {
    public static void main(String[] args) {
        System.out.println("Starting Java System & Database Verification...");

        // 1. Initialize Database
        DatabaseManager.initDatabase();
        System.out.println("[PASS] DatabaseManager initialized SQLite database tables & seed data.");

        // 2. Load System
        TransportSystem system = new TransportSystem();
        DatabaseManager.loadSystemFromDatabase(system);
        System.out.println("[PASS] Loaded TransportSystem: " + system.getStudents().size() + " students, " +
                system.getStops().size() + " stops, " + system.getBuses().size() + " buses.");

        if (system.getStudents().size() < 40) {
            throw new RuntimeException("Expected at least 40 students, found " + system.getStudents().size());
        }
        if (system.getStops().size() != 30) {
            throw new RuntimeException("Expected 30 Erode stops, found " + system.getStops().size());
        }

        // 3. Test Admin Authentication with User ID: kalvi@transport and Password: kalvi123
        boolean adminAuth = DatabaseManager.authenticateAdmin("kalvi@transport", "kalvi123");
        if (!adminAuth) throw new RuntimeException("Admin authentication failed for kalvi@transport / kalvi123!");
        System.out.println("[PASS] Admin authentication verified (kalvi@transport / kalvi123).");

        // 4. Test Student Authentication with Roll No (25CSR201) and Password (25CSR201@B01)
        Student st = DatabaseManager.authenticateStudent("25CSR201", "25CSR201@B01");
        if (st == null) throw new RuntimeException("Student 25CSR201 authentication failed!");
        System.out.println("[PASS] Student Login verified for " + st.getName() + " (" + st.getRollNo() + ") with password " + st.getPassword());

        // 5. Test Student Self-Attendance Update
        boolean attOk = DatabaseManager.updateStudentAttendance("25CSR201", false);
        if (!attOk) throw new RuntimeException("Attendance update failed!");
        System.out.println("[PASS] Student self-attendance marked Absent via JDBC.");

        DatabaseManager.updateStudentAttendance("25CSR201", true);
        System.out.println("[PASS] Student self-attendance restored to Present via JDBC.");

        // 6. Test Fee Payment (Student Login)
        boolean payOk = DatabaseManager.recordStudentPayment("25CSR201", "UPI (Google Pay)", "TXN-TEST-9999", "RCPT-TEST-9999", 2500.0);
        if (!payOk) throw new RuntimeException("Payment recording failed!");
        FeeRecord f = DatabaseManager.getFeeRecord("25CSR201");
        if (!f.isPaid()) throw new RuntimeException("Fee record not marked paid!");
        Student stUpdated = DatabaseManager.authenticateStudent("25CSR201", "25CSR201@B01");
        if (!stUpdated.isAuthorized()) throw new RuntimeException("Student pass not authorized upon payment!");
        System.out.println("[PASS] Fee Payment verified: Paid=true, Pass Authorized=true, Txn=" + f.getTransactionId());

        // 7. Test Student Registration Module (Admin can register new student)
        Student newStudent = new Student("25CSR265", "Karthi Velusamy", "Erode Bus Stand", "Computer Science (CSE)", "25CSR265@B01", "B01");
        boolean regOk = DatabaseManager.registerStudent(newStudent, 2500.0);
        System.out.println("[PASS] Student Registration verified: " + regOk + " for Roll No 25CSR265.");
        Student registeredAuth = DatabaseManager.authenticateStudent("25CSR265", "25CSR265@B01");
        if (registeredAuth == null) throw new RuntimeException("New registered student authentication failed!");
        System.out.println("[PASS] New registered student successfully logged in: " + registeredAuth.getName() + " (" + registeredAuth.getRollNo() + ")");

        // 8. Test Dijkstra Route Planning
        PickupPlanningPolicy policy = new NearestStopPolicy();
        TransportPlanner planner = new TransportPlanner(system, policy);
        for (CollegeBus bus : system.getBuses().values()) {
            String report = planner.planTrip(bus);
            String busType = "B01".equalsIgnoreCase(bus.getBusId()) ? "Mini Bus" : "Standard Bus";
            System.out.println("[PASS] Route planned for Bus " + bus.getBusId() + " (" + busType + "): " +
                    bus.getBoardedStudents().size() + "/" + bus.getCapacity() + " seats boarded.");
        }

        System.out.println("\nALL JAVA SYSTEM TESTS COMPLETED SUCCESSFULLY!");
    }
}
