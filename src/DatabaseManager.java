
import java.sql.*;
import java.util.*;

/**
 * Robust Database Manager for Kalvi College Smart Transport Management System.
 * Supports MySQL with automatic graceful fallback to embedded SQLite
 * (kalvi_transport.db)
 * if MySQL server is offline.
 * Manages stops, buses, verified students, driver applications with license
 * approvals,
 * road network edges with detour interconnects, and simulated road condition
 * toggles.
 */
public class DatabaseManager {

    private static final String DB_HOST = System.getProperty("db.host", "localhost");
    private static final String DB_PORT = System.getProperty("db.port", "3306");
    private static final String DB_NAME = System.getProperty("db.name", "kalvi_transport");
    private static final String DB_USER = System.getProperty("db.user", "root");
    private static final String DB_PASS = System.getProperty("db.pass", "");

    private static boolean isSqlite = false;

    private static String getJdbcUrl(boolean includeDb) {
        return "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + (includeDb ? "/" + DB_NAME : "") +
                "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
    }

    public static synchronized Connection getConnection() throws SQLException {
        if (isSqlite) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored) {
            }
            return DriverManager.getConnection("jdbc:sqlite:kalvi_transport.db");
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(getJdbcUrl(true), DB_USER, DB_PASS);
        } catch (Exception e) {
            // MySQL unavailable, switch permanently to SQLite for the session
            isSqlite = true;
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored) {
            }
            System.out.println(
                    "[DB] MySQL connection failed. Seamlessly running with embedded SQLite database (kalvi_transport.db)");
            return DriverManager.getConnection("jdbc:sqlite:kalvi_transport.db");
        }
    }

    public static void initDatabase() {
        System.out.println(
                "[DB] Initializing Database connectivity (checking MySQL at " + DB_HOST + ":" + DB_PORT + ")...");
        try {
            // 1. Try MySQL database creation
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                try (Connection conn = DriverManager.getConnection(getJdbcUrl(false), DB_USER, DB_PASS);
                        Statement stmt = conn.createStatement()) {
                    stmt.execute("CREATE DATABASE IF NOT EXISTS " + DB_NAME
                            + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                    isSqlite = false;
                    System.out.println("[DB] Connected to MySQL database '" + DB_NAME + "'.");
                }
            } catch (Exception mysqlEx) {
                isSqlite = true;
                System.out.println("[DB] MySQL server offline. Using local SQLite database (kalvi_transport.db).");
            }

            // 2. Connect to active database (MySQL or SQLite) and ensure tables
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

                String autoIdDef = isSqlite ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "INT AUTO_INCREMENT PRIMARY KEY";

                // Buses table
                stmt.execute("CREATE TABLE IF NOT EXISTS buses (" +
                        "bus_id VARCHAR(20) PRIMARY KEY, " +
                        "bus_type VARCHAR(50) NOT NULL, " +
                        "capacity INT NOT NULL, " +
                        "driver_name VARCHAR(100) NOT NULL, " +
                        "driver_phone VARCHAR(20), " +
                        "morning_departure VARCHAR(20) DEFAULT '07:00 AM', " +
                        "evening_departure VARCHAR(20) DEFAULT '05:00 PM', " +
                        "status VARCHAR(20) DEFAULT 'Active')");

                // Stops table (includes real coordinates in Erode District)
                stmt.execute("CREATE TABLE IF NOT EXISTS stops (" +
                        "stop_id VARCHAR(100) PRIMARY KEY, " +
                        "stop_name VARCHAR(100) NOT NULL, " +
                        "bus_route VARCHAR(20) NOT NULL, " +
                        "stop_order INT DEFAULT 0, " +
                        "latitude DOUBLE NOT NULL, " +
                        "longitude DOUBLE NOT NULL, " +
                        "morning_time VARCHAR(20) DEFAULT '07:30 AM', " +
                        "evening_time VARCHAR(20) DEFAULT '05:15 PM')");

                // Students table
                stmt.execute("CREATE TABLE IF NOT EXISTS students (" +
                        "id " + autoIdDef + ", " +
                        "roll_no VARCHAR(50) UNIQUE NOT NULL, " +
                        "full_name VARCHAR(100) NOT NULL, " +
                        "department VARCHAR(50) NOT NULL, " +
                        "current_year VARCHAR(20) NOT NULL, " +
                        "stop_name VARCHAR(100) NOT NULL, " +
                        "bus_id VARCHAR(20) NOT NULL, " +
                        "email VARCHAR(100) NOT NULL, " +
                        "phone VARCHAR(20) NOT NULL, " +
                        "password VARCHAR(100) NOT NULL, " +
                        "is_verified INT DEFAULT 0, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

                // Road network edges
                stmt.execute("CREATE TABLE IF NOT EXISTS road_edges (" +
                        "id " + autoIdDef + ", " +
                        "source VARCHAR(100) NOT NULL, " +
                        "destination VARCHAR(100) NOT NULL, " +
                        "distance_km DOUBLE DEFAULT 3.0, " +
                        "travel_time INT NOT NULL)");

                // Incidents / Hazards table
                stmt.execute("CREATE TABLE IF NOT EXISTS incidents (" +
                        "id " + autoIdDef + ", " +
                        "road_source VARCHAR(100) NOT NULL, " +
                        "road_destination VARCHAR(100) NOT NULL, " +
                        "incident_type VARCHAR(50) NOT NULL, " +
                        "penalty_mins INT DEFAULT 15, " +
                        "description VARCHAR(255), " +
                        "active INT DEFAULT 1, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

                // Admins table
                stmt.execute("CREATE TABLE IF NOT EXISTS admins (" +
                        "username VARCHAR(50) PRIMARY KEY, " +
                        "password VARCHAR(100) NOT NULL, " +
                        "role VARCHAR(20) DEFAULT 'ADMIN')");

                // Drivers table with driver verification & driving license details
                stmt.execute("CREATE TABLE IF NOT EXISTS drivers (" +
                        "driver_id VARCHAR(50) PRIMARY KEY, " +
                        "password VARCHAR(100) NOT NULL, " +
                        "name VARCHAR(100) NOT NULL, " +
                        "phone VARCHAR(20), " +
                        "license_number VARCHAR(100), " +
                        "license_doc VARCHAR(255), " +
                        "experience_years INT DEFAULT 0, " +
                        "assigned_bus VARCHAR(20) NOT NULL, " +
                        "is_approved INT DEFAULT 0, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

                // Upgrade drivers table if missing license columns (for existing databases)
                upgradeDriversTableIfRequired(conn);

                // Simulated road condition toggles table
                stmt.execute("CREATE TABLE IF NOT EXISTS simulated_conditions (" +
                        "condition_key VARCHAR(50) PRIMARY KEY, " +
                        "condition_name VARCHAR(100) NOT NULL, " +
                        "is_enabled INT DEFAULT 0, " +
                        "penalty_mins INT DEFAULT 15, " +
                        "affected_source VARCHAR(100), " +
                        "affected_destination VARCHAR(100), " +
                        "color_code VARCHAR(30), " +
                        "icon VARCHAR(20), " +
                        "description VARCHAR(255))");

                // Seed initial data if tables are empty
                seedDatabaseIfEmpty(conn);

                // Clean up any unassigned stops or cross-corridor edges from previous testing
                cleanupUnwantedRoadEdgesAndStops(conn);

                System.out.println("[DB] Database initialized successfully. Ready!");
            }
        } catch (SQLException e) {
            System.err.println("[DB ERROR] Failed initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void upgradeDriversTableIfRequired(Connection conn) {
        String[] cols = {
                "ALTER TABLE drivers ADD COLUMN phone VARCHAR(20)",
                "ALTER TABLE drivers ADD COLUMN license_number VARCHAR(100)",
                "ALTER TABLE drivers ADD COLUMN license_doc VARCHAR(255)",
                "ALTER TABLE drivers ADD COLUMN experience_years INT DEFAULT 0",
                "ALTER TABLE drivers ADD COLUMN is_approved INT DEFAULT 0",
                "ALTER TABLE drivers ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
        };
        for (String sql : cols) {
            try (Statement s = conn.createStatement()) {
                s.execute(sql);
            } catch (SQLException ignored) {
                // Column already exists
            }
        }

        // Ensure default seeded drivers have approved status & license numbers
        try (Statement s = conn.createStatement()) {
            s.execute(
                    "UPDATE drivers SET is_approved = 1, phone = '+91 98421 11001', license_number = 'TN-33-2015-001001', license_doc = 'license_shanmugaraj.pdf', experience_years = 9 WHERE driver_id = 'driver1'");
            s.execute(
                    "UPDATE drivers SET is_approved = 1, phone = '+91 98421 11002', license_number = 'TN-33-2016-002002', license_doc = 'license_murugesan.pdf', experience_years = 8 WHERE driver_id = 'driver2'");
            s.execute(
                    "UPDATE drivers SET is_approved = 1, phone = '+91 98421 11003', license_number = 'TN-33-2014-003003', license_doc = 'license_palanisamy.pdf', experience_years = 11 WHERE driver_id = 'driver3'");
        } catch (SQLException ignored) {
        }
    }

    public static void cleanupUnwantedRoadEdgesAndStops(Connection conn) {
        try (Statement s = conn.createStatement()) {
            // 1. Remove any unassigned test stops from stops table
            s.execute("DELETE FROM stops WHERE bus_route NOT IN ('ALL', 'B01', 'B02', 'B03') OR stop_order > 10 OR stop_name LIKE '%sampath%' OR stop_name LIKE '%veppampalayam%'");

            // 2. Clear corrupted/cross-corridor edges so buses only follow their assigned corridor
            s.execute("DELETE FROM road_edges WHERE source LIKE '%sampath%' OR destination LIKE '%sampath%' OR source LIKE '%veppampalayam%' OR destination LIKE '%veppampalayam%'");
            s.execute("DELETE FROM road_edges WHERE (source = 'Chithode Junction' AND destination = 'Nasiyanur Junction') OR (source = 'Nasiyanur Junction' AND destination = 'Chithode Junction')");
            s.execute("DELETE FROM road_edges WHERE (source = 'Railway Station Gate' AND destination = 'Erode Bus Stand') OR (source = 'Erode Bus Stand' AND destination = 'Railway Station Gate')");
            s.execute("DELETE FROM road_edges WHERE (source = 'Surampatti Valasu' AND destination = 'Solar Pirivu') OR (source = 'Solar Pirivu' AND destination = 'Surampatti Valasu')");
            s.execute("DELETE FROM road_edges WHERE (source = 'SIPCOT Perundurai Gate' AND destination = 'Vijayamangalam Toll') OR (source = 'Vijayamangalam Toll' AND destination = 'SIPCOT Perundurai Gate')");
            s.execute("DELETE FROM road_edges WHERE (source = 'Bhavani Old Bus Stand' AND destination = 'Nasiyanur Junction') OR (source = 'Nasiyanur Junction' AND destination = 'Bhavani Old Bus Stand')");
            s.execute("DELETE FROM road_edges WHERE (source = 'Modakkurichi Town' AND destination = 'Solar Pirivu') OR (source = 'Solar Pirivu' AND destination = 'Modakkurichi Town')");
            s.execute("DELETE FROM road_edges WHERE (source = 'Thindal Murugan Temple' AND destination = 'Chithode Junction') OR (source = 'Chithode Junction' AND destination = 'Thindal Murugan Temple')");
            s.execute("DELETE FROM road_edges WHERE (source = 'Villarasampatti' AND destination = 'Nasiyanur Junction') OR (source = 'Nasiyanur Junction' AND destination = 'Villarasampatti')");
            s.execute("DELETE FROM road_edges WHERE (source = 'Kodumudi Road Pirivu' AND destination = 'Chennimalai Bus Stand') OR (source = 'Chennimalai Bus Stand' AND destination = 'Kodumudi Road Pirivu')");

            // 3. Ensure B02 SIPCOT Perundurai Gate connects back to CAMPUS
            s.execute("INSERT INTO road_edges (source, destination, distance_km, travel_time) SELECT 'SIPCOT Perundurai Gate', 'CAMPUS', 9.0, 15 WHERE NOT EXISTS (SELECT 1 FROM road_edges WHERE (source = 'SIPCOT Perundurai Gate' AND destination = 'CAMPUS') OR (source = 'CAMPUS' AND destination = 'SIPCOT Perundurai Gate'))");

            // 4. Ensure B03 Nasiyanur Junction connects back to CAMPUS
            s.execute("INSERT INTO road_edges (source, destination, distance_km, travel_time) SELECT 'Nasiyanur Junction', 'CAMPUS', 6.5, 11 WHERE NOT EXISTS (SELECT 1 FROM road_edges WHERE (source = 'Nasiyanur Junction' AND destination = 'CAMPUS') OR (source = 'CAMPUS' AND destination = 'Nasiyanur Junction'))");

            // 5. Remove Monsoon / Rainfall condition and incidents completely from database
            s.execute("DELETE FROM simulated_conditions WHERE condition_key = 'RAINFALL' OR UPPER(condition_name) LIKE '%MONSOON%' OR UPPER(condition_name) LIKE '%RAIN%'");
            s.execute("DELETE FROM incidents WHERE UPPER(incident_type) = 'RAINFALL' OR UPPER(incident_type) LIKE '%RAIN%' OR UPPER(description) LIKE '%RAIN%' OR UPPER(description) LIKE '%MONSOON%'");
        } catch (SQLException e) {
            System.err.println("[DB] Cleanup note: " + e.getMessage());
        }
    }

    private static void seedDatabaseIfEmpty(Connection conn) throws SQLException {
        // 1. Seed Buses
        try (Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM buses")) {
            if (rs.next() && rs.getInt(1) == 0) {
                System.out.println("[DB] Seeding buses...");
                String insertBus = "INSERT INTO buses (bus_id, bus_type, capacity, driver_name, driver_phone, morning_departure, evening_departure, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertBus)) {
                    ps.setString(1, "B01");
                    ps.setString(2, "Mini Bus");
                    ps.setInt(3, 30);
                    ps.setString(4, "Shanmugaraj K");
                    ps.setString(5, "+91 98421 11001");
                    ps.setString(6, "06:45 AM");
                    ps.setString(7, "05:15 PM");
                    ps.setString(8, "Active");
                    ps.executeUpdate();

                    ps.setString(1, "B02");
                    ps.setString(2, "Standard Bus");
                    ps.setInt(3, 45);
                    ps.setString(4, "Murugesan P");
                    ps.setString(5, "+91 98421 11002");
                    ps.setString(6, "06:40 AM");
                    ps.setString(7, "05:15 PM");
                    ps.setString(8, "Active");
                    ps.executeUpdate();

                    ps.setString(1, "B03");
                    ps.setString(2, "Standard Bus");
                    ps.setInt(3, 45);
                    ps.setString(4, "Palanisamy V");
                    ps.setString(5, "+91 98421 11003");
                    ps.setString(6, "06:30 AM");
                    ps.setString(7, "05:15 PM");
                    ps.setString(8, "Active");
                    ps.executeUpdate();
                }
            }
        }

        // 2. Seed Stops with real Erode GPS coordinates
        try (Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM stops")) {
            if (rs.next() && rs.getInt(1) == 0) {
                System.out.println("[DB] Seeding 30 real-world stops with coordinates...");
                String insertStop = "INSERT INTO stops (stop_id, stop_name, bus_route, stop_order, latitude, longitude, morning_time, evening_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertStop)) {
                    // Campus Stop
                    addStopBatch(ps, "CAMPUS", "Kalvi College Campus", "ALL", 0, 11.3150, 77.6780, "08:15 AM",
                            "05:00 PM");

                    // B01: Erode North Corridor (10 stops)
                    addStopBatch(ps, "B01-01", "Erode Bus Stand", "B01", 1, 11.3410, 77.7172, "07:00 AM", "05:30 PM");
                    addStopBatch(ps, "B01-02", "Brough Road", "B01", 2, 11.3435, 77.7210, "07:05 AM", "05:35 PM");
                    addStopBatch(ps, "B01-03", "GH Roundana", "B01", 3, 11.3470, 77.7245, "07:10 AM", "05:40 PM");
                    addStopBatch(ps, "B01-04", "Paneerselvam Park", "B01", 4, 11.3490, 77.7270, "07:15 AM", "05:45 PM");
                    addStopBatch(ps, "B01-05", "Karungalpalayam", "B01", 5, 11.3540, 77.7390, "07:22 AM", "05:52 PM");
                    addStopBatch(ps, "B01-06", "Pallipalayam", "B01", 6, 11.3600, 77.7470, "07:28 AM", "05:58 PM");
                    addStopBatch(ps, "B01-07", "Kumarapalayam", "B01", 7, 11.4420, 77.7120, "07:38 AM", "06:08 PM");
                    addStopBatch(ps, "B01-08", "Bhavani Old Bus Stand", "B01", 8, 11.4500, 77.6830, "07:46 AM",
                            "06:16 PM");
                    addStopBatch(ps, "B01-09", "Kalingarayanpalayam", "B01", 9, 11.4280, 77.6690, "07:52 AM",
                            "06:22 PM");
                    addStopBatch(ps, "B01-10", "Chithode Junction", "B01", 10, 11.4010, 77.6650, "07:58 AM",
                            "06:28 PM");

                    // B02: Erode South & West Corridor (10 stops)
                    addStopBatch(ps, "B02-01", "Solar Pirivu", "B02", 1, 11.3110, 77.7380, "06:55 AM", "05:35 PM");
                    addStopBatch(ps, "B02-02", "Lakkapuram", "B02", 2, 11.2970, 77.7550, "07:02 AM", "05:42 PM");
                    addStopBatch(ps, "B02-03", "Kollampalayam Bypass", "B02", 3, 11.3190, 77.7220, "07:10 AM",
                            "05:50 PM");
                    addStopBatch(ps, "B02-04", "Railway Station Gate", "B02", 4, 11.3280, 77.7250, "07:16 AM",
                            "05:56 PM");
                    addStopBatch(ps, "B02-05", "Marapalam", "B02", 5, 11.3320, 77.7120, "07:22 AM", "06:02 PM");
                    addStopBatch(ps, "B02-06", "Surampatti Valasu", "B02", 6, 11.3220, 77.7010, "07:29 AM", "06:09 PM");
                    addStopBatch(ps, "B02-07", "Thindal Murugan Temple", "B02", 7, 11.3090, 77.6720, "07:37 AM",
                            "06:17 PM");
                    addStopBatch(ps, "B02-08", "Villarasampatti", "B02", 8, 11.3250, 77.6650, "07:44 AM", "06:24 PM");
                    addStopBatch(ps, "B02-09", "Perundurai Old Bus Stand", "B02", 9, 11.2760, 77.5830, "07:53 AM",
                            "06:33 PM");
                    addStopBatch(ps, "B02-10", "SIPCOT Perundurai Gate", "B02", 10, 11.2650, 77.5620, "07:59 AM",
                            "06:39 PM");

                    // B03: Erode Rural & Industrial Corridor (10 stops)
                    addStopBatch(ps, "B03-01", "Modakkurichi Town", "B03", 1, 11.2310, 77.7550, "06:45 AM", "05:35 PM");
                    addStopBatch(ps, "B03-02", "Elumathur Junction", "B03", 2, 11.2150, 77.7950, "06:53 AM",
                            "05:43 PM");
                    addStopBatch(ps, "B03-03", "Sivagiri Bus Stop", "B03", 3, 11.1210, 77.7880, "07:03 AM", "05:53 PM");
                    addStopBatch(ps, "B03-04", "Kodumudi Road Pirivu", "B03", 4, 11.0800, 77.8820, "07:11 AM",
                            "06:01 PM");
                    addStopBatch(ps, "B03-05", "Muthur Corner", "B03", 5, 11.0850, 77.6830, "07:20 AM", "06:10 PM");
                    addStopBatch(ps, "B03-06", "Chennimalai Bus Stand", "B03", 6, 11.1680, 77.6110, "07:30 AM",
                            "06:20 PM");
                    addStopBatch(ps, "B03-07", "Ingur Industrial Area", "B03", 7, 11.2290, 77.6010, "07:38 AM",
                            "06:28 PM");
                    addStopBatch(ps, "B03-08", "Vijayamangalam Toll", "B03", 8, 11.2350, 77.4950, "07:47 AM",
                            "06:37 PM");
                    addStopBatch(ps, "B03-09", "Uthukuli Road", "B03", 9, 11.2200, 77.4500, "07:54 AM", "06:44 PM");
                    addStopBatch(ps, "B03-10", "Nasiyanur Junction", "B03", 10, 11.3650, 77.6320, "08:02 AM",
                            "06:52 PM");

                    ps.executeBatch();
                }
            }
        }

        // 3. Seed Road Network Edges with comprehensive Detour / Bypass Interconnects
        try (Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM road_edges")) {
            if (rs.next() && rs.getInt(1) == 0) {
                System.out.println("[DB] Seeding road edges with distances & travel times...");
                String insertEdge = "INSERT INTO road_edges (source, destination, distance_km, travel_time) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertEdge)) {
                    // Campus Connections
                    addEdgeBatch(ps, "CAMPUS", "Erode Bus Stand", 5.5, 10);
                    addEdgeBatch(ps, "CAMPUS", "Solar Pirivu", 6.2, 11);
                    addEdgeBatch(ps, "CAMPUS", "Modakkurichi Town", 7.8, 13);
                    addEdgeBatch(ps, "CAMPUS", "Thindal Murugan Temple", 3.8, 8);
                    addEdgeBatch(ps, "CAMPUS", "Chithode Junction", 8.5, 14);

                    // B01 Route Segments
                    addEdgeBatch(ps, "Erode Bus Stand", "Brough Road", 1.2, 3);
                    addEdgeBatch(ps, "Brough Road", "GH Roundana", 1.5, 4);
                    addEdgeBatch(ps, "GH Roundana", "Paneerselvam Park", 1.1, 3);
                    addEdgeBatch(ps, "Paneerselvam Park", "Karungalpalayam", 2.4, 5);
                    addEdgeBatch(ps, "Karungalpalayam", "Pallipalayam", 1.8, 4);
                    addEdgeBatch(ps, "Pallipalayam", "Kumarapalayam", 4.5, 8);
                    addEdgeBatch(ps, "Kumarapalayam", "Bhavani Old Bus Stand", 3.2, 6);
                    addEdgeBatch(ps, "Bhavani Old Bus Stand", "Kalingarayanpalayam", 2.5, 5);
                    addEdgeBatch(ps, "Kalingarayanpalayam", "Chithode Junction", 3.0, 6);

                    // B02 Route Segments
                    addEdgeBatch(ps, "Solar Pirivu", "Lakkapuram", 2.0, 4);
                    addEdgeBatch(ps, "Lakkapuram", "Kollampalayam Bypass", 2.8, 5);
                    addEdgeBatch(ps, "Kollampalayam Bypass", "Railway Station Gate", 2.1, 4);
                    addEdgeBatch(ps, "Railway Station Gate", "Marapalam", 1.3, 3);
                    addEdgeBatch(ps, "Marapalam", "Surampatti Valasu", 2.5, 5);
                    addEdgeBatch(ps, "Surampatti Valasu", "Thindal Murugan Temple", 3.2, 6);
                    addEdgeBatch(ps, "Thindal Murugan Temple", "Villarasampatti", 2.6, 5);
                    addEdgeBatch(ps, "Villarasampatti", "Perundurai Old Bus Stand", 4.2, 7);
                    addEdgeBatch(ps, "Perundurai Old Bus Stand", "SIPCOT Perundurai Gate", 2.2, 4);

                    // B03 Route Segments
                    addEdgeBatch(ps, "Modakkurichi Town", "Elumathur Junction", 3.5, 6);
                    addEdgeBatch(ps, "Elumathur Junction", "Sivagiri Bus Stop", 4.0, 7);
                    addEdgeBatch(ps, "Sivagiri Bus Stop", "Kodumudi Road Pirivu", 3.1, 5);
                    addEdgeBatch(ps, "Kodumudi Road Pirivu", "Muthur Corner", 3.6, 6);
                    addEdgeBatch(ps, "Muthur Corner", "Chennimalai Bus Stand", 4.8, 8);
                    addEdgeBatch(ps, "Chennimalai Bus Stand", "Ingur Industrial Area", 3.4, 6);
                    addEdgeBatch(ps, "Ingur Industrial Area", "Vijayamangalam Toll", 4.1, 7);
                    addEdgeBatch(ps, "Vijayamangalam Toll", "Uthukuli Road", 2.9, 5);
                    addEdgeBatch(ps, "Uthukuli Road", "Nasiyanur Junction", 3.3, 6);

                    // Detour / Bypass Interconnects for Dijkstra routing around roadblocks
                    addEdgeBatch(ps, "Brough Road", "Paneerselvam Park", 1.8, 4); // Direct detour around GH Roundana
                    addEdgeBatch(ps, "Erode Bus Stand", "Paneerselvam Park", 2.2, 5);
                    addEdgeBatch(ps, "Karungalpalayam", "Bhavani Old Bus Stand", 4.8, 8); // Bypass Pallipalayam bridge
                    addEdgeBatch(ps, "Chithode Junction", "Nasiyanur Junction", 4.0, 7);
                    addEdgeBatch(ps, "Railway Station Gate", "Erode Bus Stand", 1.8, 4);
                    addEdgeBatch(ps, "Railway Station Gate", "Surampatti Valasu", 2.4, 5); // Bypass Marapalam
                    addEdgeBatch(ps, "Surampatti Valasu", "Solar Pirivu", 2.7, 5);
                    addEdgeBatch(ps, "SIPCOT Perundurai Gate", "Vijayamangalam Toll", 3.5, 6);
                    addEdgeBatch(ps, "Bhavani Old Bus Stand", "Nasiyanur Junction", 5.5, 9);
                    addEdgeBatch(ps, "Modakkurichi Town", "Solar Pirivu", 3.8, 6);
                    addEdgeBatch(ps, "Kodumudi Road Pirivu", "Chennimalai Bus Stand", 5.2, 9); // Bypass Muthur Corner
                    addEdgeBatch(ps, "Thindal Murugan Temple", "Chithode Junction", 5.9, 10);
                    addEdgeBatch(ps, "Villarasampatti", "Nasiyanur Junction", 4.5, 8);

                    ps.executeBatch();
                }
            }
        }

        // 4. Seed 30 Default Students
        try (Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM students")) {
            if (rs.next() && rs.getInt(1) < 30) {
                System.out.println("[DB] Seeding 30 verified students across buses...");
                String insertStudent = (isSqlite ? "INSERT OR IGNORE INTO students " : "INSERT IGNORE INTO students ") +
                        "(roll_no, full_name, department, current_year, stop_name, bus_id, email, phone, password, is_verified) "
                        +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertStudent)) {
                    // Bus 1 Students (10)
                    addStudentBatch(ps, "26CSR001", "Aarav Kumar", "CSE", "3rd Year", "Erode Bus Stand", "B01",
                            "aarav.k@kalvi.edu", "9842100001", "student123", 1);
                    addStudentBatch(ps, "26CSR002", "Priya Dharshini", "ECE", "2nd Year", "Brough Road", "B01",
                            "priya.d@kalvi.edu", "9842100002", "student123", 1);
                    addStudentBatch(ps, "26CSR003", "Karthik Raja", "AI & DS", "1st Year", "GH Roundana", "B01",
                            "karthik.r@kalvi.edu", "9842100003", "student123", 1);
                    addStudentBatch(ps, "26CSR004", "Sneha Ramachandran", "IT", "4th Year", "Paneerselvam Park", "B01",
                            "sneha.r@kalvi.edu", "9842100004", "student123", 1);
                    addStudentBatch(ps, "26CSR005", "Vigneshwaran M", "MECH", "2nd Year", "Karungalpalayam", "B01",
                            "vignesh.m@kalvi.edu", "9842100005", "student123", 1);
                    addStudentBatch(ps, "26CSR006", "Deepika S", "CSE", "3rd Year", "Pallipalayam", "B01",
                            "deepika.s@kalvi.edu", "9842100006", "student123", 1);
                    addStudentBatch(ps, "26CSR007", "Gokul Prasad", "EEE", "1st Year", "Kumarapalayam", "B01",
                            "gokul.p@kalvi.edu", "9842100007", "student123", 1);
                    addStudentBatch(ps, "26CSR008", "Harini Balaji", "ECE", "4th Year", "Bhavani Old Bus Stand", "B01",
                            "harini.b@kalvi.edu", "9842100008", "student123", 1);
                    addStudentBatch(ps, "26CSR009", "Kavya Murugan", "AI & DS", "2nd Year", "Kalingarayanpalayam",
                            "B01", "kavya.m@kalvi.edu", "9842100009", "student123", 1);
                    addStudentBatch(ps, "26CSR010", "Manoj Kumar P", "CSE", "3rd Year", "Chithode Junction", "B01",
                            "manoj.k@kalvi.edu", "9842100010", "student123", 1);

                    // Bus 2 Students (10)
                    addStudentBatch(ps, "26CSR011", "Naveen Raj", "MECH", "2nd Year", "Solar Pirivu", "B02",
                            "naveen.r@kalvi.edu", "9842100011", "student123", 1);
                    addStudentBatch(ps, "26CSR012", "Abinaya V", "CSE", "1st Year", "Lakkapuram", "B02",
                            "abinaya.v@kalvi.edu", "9842100012", "student123", 1);
                    addStudentBatch(ps, "26CSR013", "Dinesh K", "IT", "3rd Year", "Kollampalayam Bypass", "B02",
                            "dinesh.k@kalvi.edu", "9842100013", "student123", 1);
                    addStudentBatch(ps, "26CSR014", "Divya Bharathi", "ECE", "4th Year", "Railway Station Gate", "B02",
                            "divya.b@kalvi.edu", "9842100014", "student123", 1);
                    addStudentBatch(ps, "26CSR015", "Gowtham S", "AI & DS", "2nd Year", "Marapalam", "B02",
                            "gowtham.s@kalvi.edu", "9842100015", "student123", 1);
                    addStudentBatch(ps, "26CSR016", "Keerthana M", "CSE", "3rd Year", "Surampatti Valasu", "B02",
                            "keerthana.m@kalvi.edu", "9842100016", "student123", 1);
                    addStudentBatch(ps, "26CSR017", "Lokesh Kannan", "EEE", "1st Year", "Thindal Murugan Temple", "B02",
                            "lokesh.k@kalvi.edu", "9842100017", "student123", 1);
                    addStudentBatch(ps, "26CSR018", "Monisha R", "IT", "4th Year", "Villarasampatti", "B02",
                            "monisha.r@kalvi.edu", "9842100018", "student123", 1);
                    addStudentBatch(ps, "26CSR019", "Prasanth N", "MECH", "3rd Year", "Perundurai Old Bus Stand", "B02",
                            "prasanth.n@kalvi.edu", "9842100019", "student123", 1);
                    addStudentBatch(ps, "26CSR020", "Rithika Selvi", "ECE", "2nd Year", "SIPCOT Perundurai Gate", "B02",
                            "rithika.s@kalvi.edu", "9842100020", "student123", 1);

                    // Bus 3 Students (10)
                    addStudentBatch(ps, "26CSR021", "Sanjay Kumar B", "CSE", "1st Year", "Modakkurichi Town", "B03",
                            "sanjay.b@kalvi.edu", "9842100021", "student123", 1);
                    addStudentBatch(ps, "26CSR022", "Shalini T", "AI & DS", "3rd Year", "Elumathur Junction", "B03",
                            "shalini.t@kalvi.edu", "9842100022", "student123", 1);
                    addStudentBatch(ps, "26CSR023", "Surya Prakash", "IT", "4th Year", "Sivagiri Bus Stop", "B03",
                            "surya.p@kalvi.edu", "9842100023", "student123", 1);
                    addStudentBatch(ps, "26CSR024", "Swathi Ananth", "ECE", "2nd Year", "Kodumudi Road Pirivu", "B03",
                            "swathi.a@kalvi.edu", "9842100024", "student123", 1);
                    addStudentBatch(ps, "26CSR025", "Tarun Adithya", "CSE", "3rd Year", "Muthur Corner", "B03",
                            "tarun.a@kalvi.edu", "9842100025", "student123", 1);
                    addStudentBatch(ps, "26CSR026", "Udhaya Sankar", "MECH", "1st Year", "Chennimalai Bus Stand", "B03",
                            "udhaya.s@kalvi.edu", "9842100026", "student123", 1);
                    addStudentBatch(ps, "26CSR027", "Vaishnavi K", "AI & DS", "4th Year", "Ingur Industrial Area",
                            "B03", "vaishnavi.k@kalvi.edu", "9842100027", "student123", 1);
                    addStudentBatch(ps, "26CSR028", "Vijay Anand", "EEE", "2nd Year", "Vijayamangalam Toll", "B03",
                            "vijay.a@kalvi.edu", "9842100028", "student123", 1);
                    addStudentBatch(ps, "26CSR029", "Yuvashree M", "IT", "3rd Year", "Uthukuli Road", "B03",
                            "yuvashree.m@kalvi.edu", "9842100029", "student123", 1);
                    addStudentBatch(ps, "26CSR030", "Shanmugapriya G", "CSE", "4th Year", "Nasiyanur Junction", "B03",
                            "shanmuga.g@kalvi.edu", "9842100030", "student123", 1);

                    ps.executeBatch();
                }
            }
        }

        // 5. Seed Admins & Approved Fleet Drivers
        try (Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM admins")) {
            if (rs.next() && rs.getInt(1) == 0) {
                s.execute("INSERT INTO admins (username, password, role) VALUES ('admin', 'admin123', 'ADMIN')");
            }
        }
        try (Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM drivers")) {
            if (rs.next() && rs.getInt(1) == 0) {
                s.execute(
                        "INSERT INTO drivers (driver_id, password, name, phone, license_number, license_doc, experience_years, assigned_bus, is_approved) VALUES "
                                +
                                "('driver1', 'driver123', 'Shanmugaraj K', '+91 98421 11001', 'TN-33-2015-001001', 'license_shanmugaraj.pdf', 9, 'B01', 1), "
                                +
                                "('driver2', 'driver123', 'Murugesan P', '+91 98421 11002', 'TN-33-2016-002002', 'license_murugesan.pdf', 8, 'B02', 1), "
                                +
                                "('driver3', 'driver123', 'Palanisamy V', '+91 98421 11003', 'TN-33-2014-003003', 'license_palanisamy.pdf', 11, 'B03', 1)");
            }
        }

        // 6. Seed 4 Simulated Dummy Road Conditions
        try (Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM simulated_conditions")) {
            if (rs.next() && rs.getInt(1) == 0) {
                System.out.println("[DB] Seeding 4 dummy road condition toggles...");
                String insertCond = "INSERT INTO simulated_conditions (condition_key, condition_name, is_enabled, penalty_mins, affected_source, affected_destination, color_code, icon, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertCond)) {
                    // 1. Heavy Traffic
                    addConditionBatch(ps, "HEAVY_TRAFFIC", "Heavy Traffic Congestion", 0, 18, "Karungalpalayam",
                            "Pallipalayam", "#f97316", "🚗",
                            "Simulated peak hour vehicle gridlock on Cauvery Bridge road");
                    // 2. Road Construction
                    addConditionBatch(ps, "ROAD_CONSTRUCTION", "Road Construction & Excavation", 0, 16, "GH Roundana",
                            "Paneerselvam Park", "#eab308", "🏗️",
                            "Simulated underpass digging and Smart City storm drain pipeline works");
                    // 3. Road Maintenance
                    addConditionBatch(ps, "ROAD_MAINTENANCE", "Road Maintenance & Resurfacing", 0, 10, "Kumarapalayam",
                            "Bhavani Old Bus Stand", "#a855f7", "🛠️",
                            "Simulated road milling and bitumen tarring in single-lane mode");
                    // 4. Road Block
                    addConditionBatch(ps, "ROAD_BLOCK", "Road Block / Complete Closure", 0, 99999, "Brough Road",
                            "GH Roundana", "#ef4444", "🚧",
                            "Simulated complete barricade closure; routing engine forces alternative bypass");
                    ps.executeBatch();
                }
            }
        }

        // 7. Seed Sample Incidents
        try (Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM incidents")) {
            if (rs.next() && rs.getInt(1) == 0) {
                s.execute(
                        "INSERT INTO incidents (road_source, road_destination, incident_type, penalty_mins, description, active) VALUES "
                                +
                                "('Brough Road', 'GH Roundana', 'ROAD_BLOCK', 99999, 'Brough Road Bridge Maintenance - Complete Road Block', 0), "
                                +
                                "('Karungalpalayam', 'Pallipalayam', 'HEAVY_TRAFFIC', 18, 'Cauvery Bridge Peak Hour Congestion', 0)");
            }
        }
    }

    private static void addStopBatch(PreparedStatement ps, String id, String name, String route, int order,
            double lat, double lng, String mTime, String eTime) throws SQLException {
        ps.setString(1, id);
        ps.setString(2, name);
        ps.setString(3, route);
        ps.setInt(4, order);
        ps.setDouble(5, lat);
        ps.setDouble(6, lng);
        ps.setString(7, mTime);
        ps.setString(8, eTime);
        ps.addBatch();
    }

    private static void addEdgeBatch(PreparedStatement ps, String src, String dst, double km, int mins)
            throws SQLException {
        ps.setString(1, src);
        ps.setString(2, dst);
        ps.setDouble(3, km);
        ps.setInt(4, mins);
        ps.addBatch();
    }

    private static void addStudentBatch(PreparedStatement ps, String roll, String name, String dept, String year,
            String stop, String bus, String email, String phone, String pass, int verified) throws SQLException {
        ps.setString(1, roll);
        ps.setString(2, name);
        ps.setString(3, dept);
        ps.setString(4, year);
        ps.setString(5, stop);
        ps.setString(6, bus);
        ps.setString(7, email);
        ps.setString(8, phone);
        ps.setString(9, pass);
        ps.setInt(10, verified);
        ps.addBatch();
    }

    private static void addConditionBatch(PreparedStatement ps, String key, String name, int enabled, int penalty,
            String src, String dst, String color, String icon, String desc) throws SQLException {
        ps.setString(1, key);
        ps.setString(2, name);
        ps.setInt(3, enabled);
        ps.setInt(4, penalty);
        ps.setString(5, src);
        ps.setString(6, dst);
        ps.setString(7, color);
        ps.setString(8, icon);
        ps.setString(9, desc);
        ps.addBatch();
    }

    // ==========================================
    // AUTHENTICATION API
    // ==========================================

    public static Map<String, Object> authenticateStudent(String rollNo, String password) {
        Map<String, Object> result = new HashMap<>();
        String sql = "SELECT * FROM students WHERE UPPER(roll_no) = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNo.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedPass = rs.getString("password");
                    int isVerified = rs.getInt("is_verified");

                    if (!storedPass.equals(password)) {
                        result.put("success", false);
                        result.put("message", "Incorrect password. Please try again.");
                        return result;
                    }

                    if (isVerified == 0) {
                        result.put("success", false);
                        result.put("pendingVerification", true);
                        result.put("message",
                                "Your student registration is PENDING approval by the Transport Administrator. Once approved, you can log in.");
                        return result;
                    }

                    result.put("success", true);
                    result.put("role", "STUDENT");
                    result.put("roll_no", rs.getString("roll_no"));
                    result.put("full_name", rs.getString("full_name"));
                    result.put("department", rs.getString("department"));
                    result.put("current_year", rs.getString("current_year"));
                    result.put("stop_name", rs.getString("stop_name"));
                    result.put("bus_id", rs.getString("bus_id"));
                    result.put("email", rs.getString("email"));
                    result.put("phone", rs.getString("phone"));
                    return result;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        result.put("success", false);
        result.put("message", "Student roll number not found. Please register first.");
        return result;
    }

    public static Map<String, Object> authenticateAdmin(String username, String password) {
        Map<String, Object> result = new HashMap<>();
        String sql = "SELECT * FROM admins WHERE username = ? AND password = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("success", true);
                    result.put("username", username);
                    result.put("role", "ADMIN");
                    return result;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        result.put("success", false);
        result.put("message", "Invalid Admin credentials.");
        return result;
    }

    public static Map<String, Object> authenticateDriver(String identifier, String password) {
        Map<String, Object> result = new HashMap<>();
        String sql = "SELECT * FROM drivers WHERE (LOWER(driver_id) = ? OR UPPER(assigned_bus) = ? OR phone = ?) AND password = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            String trimmed = identifier.trim();
            ps.setString(1, trimmed.toLowerCase());
            ps.setString(2, trimmed.toUpperCase());
            ps.setString(3, trimmed);
            ps.setString(4, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int isApproved = rs.getInt("is_approved");

                    if (isApproved == 0) {
                        result.put("success", false);
                        result.put("pendingApproval", true);
                        result.put("message",
                                "Your driver application is currently PENDING Admin approval. Once the administrator reviews and approves your driving license, you can log in.");
                        return result;
                    }

                    if (isApproved == -1) {
                        result.put("success", false);
                        result.put("message",
                                "Your driver registration was rejected by the Transport Administrator. Please contact management.");
                        return result;
                    }

                    result.put("success", true);
                    result.put("role", "DRIVER");
                    result.put("driver_id", rs.getString("driver_id"));
                    result.put("driver_name", rs.getString("name"));
                    result.put("phone", rs.getString("phone"));
                    result.put("license_number", rs.getString("license_number"));
                    result.put("license_doc", rs.getString("license_doc"));
                    result.put("assigned_bus", rs.getString("assigned_bus"));
                    result.put("experience_years", rs.getInt("experience_years"));
                    return result;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        result.put("success", false);
        result.put("message", "Invalid Driver credentials. Check your Driver ID, Phone, and Password.");
        return result;
    }

    // ==========================================
    // DRIVER SIGNUP & ADMIN APPROVAL
    // ==========================================

    public static Map<String, Object> registerDriver(String driverId, String password, String name,
            String phone, String licenseNumber, String licenseDoc,
            int experienceYears, String assignedBus) {
        Map<String, Object> result = new HashMap<>();
        if (driverId == null || driverId.trim().isEmpty()) {
            driverId = "drv_" + (System.currentTimeMillis() % 100000);
        } else {
            driverId = driverId.trim().toLowerCase();
        }

        if (assignedBus == null || assignedBus.trim().isEmpty()) {
            assignedBus = "B01";
        }

        String sql = "INSERT INTO drivers (driver_id, password, name, phone, license_number, license_doc, experience_years, assigned_bus, is_approved) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, driverId);
            ps.setString(2, password);
            ps.setString(3, name.trim());
            ps.setString(4, phone != null ? phone.trim() : "");
            ps.setString(5, licenseNumber != null ? licenseNumber.trim().toUpperCase() : "");
            ps.setString(6, licenseDoc != null ? licenseDoc.trim() : "uploaded_license_doc.pdf");
            ps.setInt(7, experienceYears);
            ps.setString(8, assignedBus.trim().toUpperCase());
            ps.executeUpdate();

            result.put("success", true);
            result.put("driver_id", driverId);
            result.put("message",
                    "Driver registration submitted successfully! Status: PENDING ADMIN APPROVAL. Once your driving license is verified by the administrator, your login will be activated.");
            return result;
        } catch (SQLException e) {
            result.put("success", false);
            result.put("message",
                    "Registration failed: Driver ID or phone already registered. Details: " + e.getMessage());
            return result;
        }
    }

    public static List<Map<String, Object>> getPendingDrivers() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM drivers WHERE is_approved = 0 ORDER BY created_at DESC";
        try (Connection conn = getConnection();
                Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("driver_id", rs.getString("driver_id"));
                m.put("name", rs.getString("name"));
                m.put("phone", rs.getString("phone"));
                m.put("license_number", rs.getString("license_number"));
                m.put("license_doc", rs.getString("license_doc"));
                m.put("experience_years", rs.getInt("experience_years"));
                m.put("assigned_bus", rs.getString("assigned_bus"));
                m.put("is_approved", rs.getInt("is_approved"));
                m.put("created_at", rs.getString("created_at"));
                list.add(m);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static List<Map<String, Object>> getAllDrivers() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM drivers ORDER BY is_approved ASC, created_at DESC";
        try (Connection conn = getConnection();
                Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("driver_id", rs.getString("driver_id"));
                m.put("name", rs.getString("name"));
                m.put("phone", rs.getString("phone"));
                m.put("license_number", rs.getString("license_number"));
                m.put("license_doc", rs.getString("license_doc"));
                m.put("experience_years", rs.getInt("experience_years"));
                m.put("assigned_bus", rs.getString("assigned_bus"));
                m.put("is_approved", rs.getInt("is_approved"));
                m.put("created_at", rs.getString("created_at"));
                list.add(m);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean approveDriver(String driverId, String assignedBus) {
        String sql = "UPDATE drivers SET is_approved = 1" +
                (assignedBus != null && !assignedBus.isEmpty() ? ", assigned_bus = ?" : "") +
                " WHERE LOWER(driver_id) = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (assignedBus != null && !assignedBus.isEmpty()) {
                ps.setString(1, assignedBus.trim().toUpperCase());
                ps.setString(2, driverId.trim().toLowerCase());
            } else {
                ps.setString(1, driverId.trim().toLowerCase());
            }
            int updated = ps.executeUpdate();
            if (updated > 0) {
                // Also update the buses table driver name if assigned
                syncBusDriver(driverId);
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean rejectDriver(String driverId) {
        String sql = "UPDATE drivers SET is_approved = -1 WHERE LOWER(driver_id) = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, driverId.trim().toLowerCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void syncBusDriver(String driverId) {
        String q = "SELECT name, phone, assigned_bus FROM drivers WHERE LOWER(driver_id) = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, driverId.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    String phone = rs.getString("phone");
                    String bus = rs.getString("assigned_bus");
                    try (PreparedStatement update = conn
                            .prepareStatement("UPDATE buses SET driver_name = ?, driver_phone = ? WHERE bus_id = ?")) {
                        update.setString(1, name);
                        update.setString(2, phone);
                        update.setString(3, bus);
                        update.executeUpdate();
                    }
                }
            }
        } catch (SQLException ignored) {
        }
    }

    // ==========================================
    // STUDENT REGISTRATION & VERIFY
    // ==========================================

    public static Map<String, Object> registerStudent(String rollNo, String fullName, String department,
            String currentYear, String stopName, String email,
            String phone, String password) {
        Map<String, Object> result = new HashMap<>();
        String busId = getBusForStop(stopName);
        if (busId == null)
            busId = "B01";

        String sql = "INSERT INTO students (roll_no, full_name, department, current_year, stop_name, bus_id, email, phone, password, is_verified) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNo.trim().toUpperCase());
            ps.setString(2, fullName.trim());
            ps.setString(3, department.trim());
            ps.setString(4, currentYear.trim());
            ps.setString(5, stopName.trim());
            ps.setString(6, busId);
            ps.setString(7, email.trim());
            ps.setString(8, phone.trim());
            ps.setString(9, password);
            ps.executeUpdate();

            result.put("success", true);
            result.put("message",
                    "Student registration submitted! Status: PENDING ADMIN VERIFICATION. Once approved by the transport administrator, you can log in.");
            result.put("bus_id", busId);
            return result;
        } catch (SQLException e) {
            result.put("success", false);
            result.put("message", "Roll number " + rollNo + " is already registered.");
            return result;
        }
    }

    public static List<Map<String, Object>> getPendingStudents() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM students WHERE is_verified = 0 ORDER BY created_at DESC";
        try (Connection conn = getConnection();
                Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("roll_no", rs.getString("roll_no"));
                m.put("full_name", rs.getString("full_name"));
                m.put("department", rs.getString("department"));
                m.put("current_year", rs.getString("current_year"));
                m.put("stop_name", rs.getString("stop_name"));
                m.put("bus_id", rs.getString("bus_id"));
                m.put("email", rs.getString("email"));
                m.put("phone", rs.getString("phone"));
                m.put("created_at", rs.getString("created_at"));
                list.add(m);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean verifyStudent(String rollNo) {
        String sql = "UPDATE students SET is_verified = 1 WHERE UPPER(roll_no) = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNo.trim().toUpperCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateStudentStop(String rollNo, String newStopName) {
        String busId = getBusForStop(newStopName);
        String sql = "UPDATE students SET stop_name = ?, bus_id = ? WHERE UPPER(roll_no) = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStopName);
            ps.setString(2, busId);
            ps.setString(3, rollNo.trim().toUpperCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean removeStudent(String rollNo) {
        String sql = "DELETE FROM students WHERE UPPER(roll_no) = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNo.trim().toUpperCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean rejectStudent(String rollNo) {
        String sql = "DELETE FROM students WHERE UPPER(roll_no) = ? AND is_verified = 0";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNo.trim().toUpperCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<Map<String, Object>> getAllStudents(String busFilter) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM students WHERE is_verified = 1 " +
                (busFilter != null && !busFilter.isEmpty() && !"ALL".equalsIgnoreCase(busFilter) ? "AND bus_id = ? "
                        : "")
                +
                "ORDER BY bus_id, stop_name, full_name";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (busFilter != null && !busFilter.isEmpty() && !"ALL".equalsIgnoreCase(busFilter)) {
                ps.setString(1, busFilter);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("roll_no", rs.getString("roll_no"));
                    m.put("full_name", rs.getString("full_name"));
                    m.put("department", rs.getString("department"));
                    m.put("current_year", rs.getString("current_year"));
                    m.put("stop_name", rs.getString("stop_name"));
                    m.put("bus_id", rs.getString("bus_id"));
                    m.put("email", rs.getString("email"));
                    m.put("phone", rs.getString("phone"));
                    list.add(m);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ==========================================
    // STOPS & BUSES API
    // ==========================================

    public static String getBusForStop(String stopName) {
        String sql = "SELECT bus_route FROM stops WHERE stop_name = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stopName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString("bus_route");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "B01";
    }

    public static List<Map<String, Object>> getAllStops() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT s.*, (SELECT COUNT(*) FROM students st WHERE st.stop_name = s.stop_name AND st.is_verified = 1) AS student_count "
                +
                "FROM stops s ORDER BY bus_route, stop_order";
        try (Connection conn = getConnection();
                Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("stop_id", rs.getString("stop_id"));
                m.put("stop_name", rs.getString("stop_name"));
                m.put("bus_route", rs.getString("bus_route"));
                m.put("stop_order", rs.getInt("stop_order"));
                m.put("latitude", rs.getDouble("latitude"));
                m.put("longitude", rs.getDouble("longitude"));
                m.put("morning_time", rs.getString("morning_time"));
                m.put("evening_time", rs.getString("evening_time"));
                m.put("student_count", rs.getInt("student_count"));
                list.add(m);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static Map<String, Object> getCampusStop() {
        Map<String, Object> m = new HashMap<>();
        m.put("stop_id", "CAMPUS");
        m.put("stop_name", "Kalvi College Campus");
        m.put("bus_route", "ALL");
        m.put("stop_order", 0);
        m.put("latitude", 11.3150);
        m.put("longitude", 77.6780);
        m.put("morning_time", "08:15 AM");
        m.put("evening_time", "05:00 PM");
        m.put("student_count", 0);
        return m;
    }

    public static List<Map<String, Object>> getStopsForBus(String busId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT s.*, (SELECT COUNT(*) FROM students st WHERE st.stop_name = s.stop_name AND st.is_verified = 1) AS student_count "
                +
                "FROM stops s WHERE bus_route = ? AND stop_id != 'CAMPUS' ORDER BY stop_order ASC";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, busId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("stop_id", rs.getString("stop_id"));
                    m.put("stop_name", rs.getString("stop_name"));
                    m.put("bus_route", rs.getString("bus_route"));
                    m.put("stop_order", rs.getInt("stop_order"));
                    m.put("latitude", rs.getDouble("latitude"));
                    m.put("longitude", rs.getDouble("longitude"));
                    m.put("morning_time", rs.getString("morning_time"));
                    m.put("evening_time", rs.getString("evening_time"));
                    m.put("student_count", rs.getInt("student_count"));
                    list.add(m);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean addStop(String stopName, String busRoute, double lat, double lng,
            String morningTime, String eveningTime) {
        try (Connection conn = getConnection()) {
            int nextOrder = 1;
            try (PreparedStatement ps = conn
                    .prepareStatement("SELECT MAX(stop_order) FROM stops WHERE bus_route = ?")) {
                ps.setString(1, busRoute);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        nextOrder = rs.getInt(1) + 1;
                }
            }

            String stopId = busRoute + "-" + String.format("%02d", nextOrder);
            String sql = "INSERT INTO stops (stop_id, stop_name, bus_route, stop_order, latitude, longitude, morning_time, evening_time) "
                    +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, stopId);
                ps.setString(2, stopName);
                ps.setString(3, busRoute);
                ps.setInt(4, nextOrder);
                ps.setDouble(5, lat);
                ps.setDouble(6, lng);
                ps.setString(7, morningTime);
                ps.setString(8, eveningTime);
                ps.executeUpdate();
            }

            // Connect to previous stop in road_edges
            String prevStop = null;
            try (PreparedStatement ps = conn
                    .prepareStatement("SELECT stop_name FROM stops WHERE bus_route = ? AND stop_order = ?")) {
                ps.setString(1, busRoute);
                ps.setInt(2, nextOrder - 1);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        prevStop = rs.getString(1);
                }
            }

            if (prevStop != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO road_edges (source, destination, distance_km, travel_time) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, prevStop);
                    ps.setString(2, stopName);
                    ps.setDouble(3, 2.5);
                    ps.setInt(4, 5);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO road_edges (source, destination, distance_km, travel_time) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, "CAMPUS");
                    ps.setString(2, stopName);
                    ps.setDouble(3, 4.0);
                    ps.setInt(4, 8);
                    ps.executeUpdate();
                }
            }

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<Map<String, Object>> getAllBuses() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT b.*, " +
                "(SELECT COUNT(*) FROM students s WHERE s.bus_id = b.bus_id AND s.is_verified = 1) AS registered_count, "
                +
                "(SELECT COUNT(*) FROM stops st WHERE st.bus_route = b.bus_id) AS stop_count " +
                "FROM buses b ORDER BY b.bus_id";
        try (Connection conn = getConnection();
                Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("bus_id", rs.getString("bus_id"));
                m.put("bus_type", rs.getString("bus_type"));
                m.put("capacity", rs.getInt("capacity"));
                m.put("driver_name", rs.getString("driver_name"));
                m.put("driver_phone", rs.getString("driver_phone"));
                m.put("morning_departure", rs.getString("morning_departure"));
                m.put("evening_departure", rs.getString("evening_departure"));
                m.put("status", rs.getString("status"));
                m.put("registered_count", rs.getInt("registered_count"));
                m.put("stop_count", rs.getInt("stop_count"));
                list.add(m);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ==========================================
    // SIMULATED ROAD CONDITION TOGGLES API
    // ==========================================

    public static List<Map<String, Object>> getSimulatedConditions() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM simulated_conditions ORDER BY condition_key";
        try (Connection conn = getConnection();
                Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("condition_key", rs.getString("condition_key"));
                m.put("condition_name", rs.getString("condition_name"));
                m.put("is_enabled", rs.getInt("is_enabled"));
                m.put("penalty_mins", rs.getInt("penalty_mins"));
                m.put("affected_source", rs.getString("affected_source"));
                m.put("affected_destination", rs.getString("affected_destination"));
                m.put("color_code", rs.getString("color_code"));
                m.put("icon", rs.getString("icon"));
                m.put("description", rs.getString("description"));
                list.add(m);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean toggleSimulatedCondition(String conditionKey, int isEnabled) {
        String sql = "UPDATE simulated_conditions SET is_enabled = ? WHERE condition_key = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, isEnabled);
            ps.setString(2, conditionKey);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<Map<String, Object>> getActiveSimulatedConditions() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM simulated_conditions WHERE is_enabled = 1";
        try (Connection conn = getConnection();
                Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("condition_key", rs.getString("condition_key"));
                m.put("condition_name", rs.getString("condition_name"));
                m.put("is_enabled", 1);
                m.put("penalty_mins", rs.getInt("penalty_mins"));
                m.put("affected_source", rs.getString("affected_source"));
                m.put("affected_destination", rs.getString("affected_destination"));
                m.put("color_code", rs.getString("color_code"));
                m.put("icon", rs.getString("icon"));
                m.put("description", rs.getString("description"));
                list.add(m);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ==========================================
    // ROAD EDGES & INCIDENTS API
    // ==========================================

    public static Dijkstra.WeightedGraph loadRoadGraph(String busId) {
        Dijkstra.WeightedGraph graph = new Dijkstra.WeightedGraph();
        String sql = "SELECT * FROM road_edges";
        // If busId is given, restrict strictly to stops belonging to this bus or CAMPUS
        Set<String> allowedStops = null;
        if (busId != null && !busId.isEmpty() && !"ALL".equalsIgnoreCase(busId)) {
            allowedStops = new HashSet<>();
            allowedStops.add("CAMPUS");
            allowedStops.add("Kalvi College Campus");
            for (Map<String, Object> st : getStopsForBus(busId)) {
                allowedStops.add((String) st.get("stop_name"));
            }
        }

        try (Connection conn = getConnection();
                Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                String src = rs.getString("source");
                String dst = rs.getString("destination");
                int time = rs.getInt("travel_time");
                double km = rs.getDouble("distance_km");

                if (allowedStops != null) {
                    if (!allowedStops.contains(src) || !allowedStops.contains(dst)) {
                        continue; // Strictly omit any edge outside this bus corridor
                    }
                }
                graph.addEdge(src, dst, time, km);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return graph;
    }

    public static Dijkstra.WeightedGraph loadRoadGraph() {
        return loadRoadGraph(null);
    }

    public static List<Map<String, Object>> getActiveIncidents() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM incidents WHERE active = 1 ORDER BY created_at DESC";
        try (Connection conn = getConnection();
                Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("road_source", rs.getString("road_source"));
                m.put("road_destination", rs.getString("road_destination"));
                m.put("incident_type", rs.getString("incident_type"));
                m.put("penalty_mins", rs.getInt("penalty_mins"));
                m.put("description", rs.getString("description"));
                m.put("active", rs.getInt("active"));
                list.add(m);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static List<Map<String, Object>> getAllIncidents() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM incidents ORDER BY active DESC, created_at DESC";
        try (Connection conn = getConnection();
                Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("road_source", rs.getString("road_source"));
                m.put("road_destination", rs.getString("road_destination"));
                m.put("incident_type", rs.getString("incident_type"));
                m.put("penalty_mins", rs.getInt("penalty_mins"));
                m.put("description", rs.getString("description"));
                m.put("active", rs.getInt("active"));
                list.add(m);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean toggleIncident(int id, int active) {
        String sql = "UPDATE incidents SET active = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean addIncident(String src, String dst, String type, int penalty, String desc) {
        String sql = "INSERT INTO incidents (road_source, road_destination, incident_type, penalty_mins, description, active) "
                +
                "VALUES (?, ?, ?, ?, ?, 1)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, src);
            ps.setString(2, dst);
            ps.setString(3, type);
            ps.setInt(4, penalty);
            ps.setString(5, desc);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Map<String, Object> getSystemStats() {
        Map<String, Object> stats = new HashMap<>();
        try (Connection conn = getConnection(); Statement s = conn.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM buses")) {
                if (rs.next())
                    stats.put("total_buses", rs.getInt(1));
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM stops")) {
                if (rs.next())
                    stats.put("total_stops", rs.getInt(1));
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM students WHERE is_verified = 1")) {
                if (rs.next())
                    stats.put("verified_students", rs.getInt(1));
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM students WHERE is_verified = 0")) {
                if (rs.next())
                    stats.put("pending_students", rs.getInt(1));
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM drivers WHERE is_approved = 0")) {
                if (rs.next())
                    stats.put("pending_drivers", rs.getInt(1));
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM drivers WHERE is_approved = 1")) {
                if (rs.next())
                    stats.put("active_drivers", rs.getInt(1));
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM simulated_conditions WHERE is_enabled = 1")) {
                if (rs.next())
                    stats.put("active_conditions", rs.getInt(1));
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM incidents WHERE active = 1")) {
                if (rs.next())
                    stats.put("active_incidents", rs.getInt(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }
}
