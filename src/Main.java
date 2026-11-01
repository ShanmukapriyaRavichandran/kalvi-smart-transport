import java.awt.Desktop;
import java.net.*;
import java.util.*;

/**
 * Main Entry Point for Kalvi College Smart Transport Web Application.
 * Starts embedded Java web server, initializes database and seed data,
 * automatically launches the browser, and provides an interactive terminal console.
 */
public class Main {

    public static final int PORT = 8080;

    public static void main(String[] args) {
        printBanner();

        // 1. Initialize Database (MySQL with seamless SQLite fallback)
        System.out.println("[1/3] Initializing Database connectivity...");
        DatabaseManager.initDatabase();

        // 2. Start Embedded Java Web Server on 0.0.0.0:8080
        System.out.println("[2/3] Starting Java Web Server on port " + PORT + " (binding to 0.0.0.0)...");
        TransportServer server = new TransportServer(PORT);
        try {
            server.start();
            String localIp = getLocalIpAddress();

            System.out.println("[3/3] System Online and Running!");
            System.out.println("------------------------------------------------------------------------");
            System.out.println("  > Local Access:    http://localhost:" + PORT);
            System.out.println("  > Network LAN IP:  http://" + localIp + ":" + PORT);
            System.out.println("  > Web Technology:  Java 21 Embedded Server + OpenStreetMap & OSRM + DB");
            System.out.println("------------------------------------------------------------------------");
            System.out.println("  * Admin Login:     admin / admin123");
            System.out.println("  * Driver Login:    driver1 / driver123 (Shanmugaraj K - Bus B01)");
            System.out.println("  * Default Student: 26CSR001 / student123 (Aarav Kumar)");
            System.out.println("  * Driver Signup:   Available via Web UI (Admin Verification Workflow)");
            System.out.println("========================================================================");

            // 3. Automatically launch browser
            openBrowser("http://localhost:" + PORT);

            // 4. Interactive Command Prompt Loop in VS Code terminal
            runTerminalInteractiveLoop(server);

        } catch (Exception e) {
            System.err.println("[FATAL] Failed starting server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("========================================================================");
        System.out.println("     KALVI COLLEGE SMART TRANSPORT MANAGEMENT SYSTEM");
        System.out.println("   Road-Based Route Optimizer, Driver Verification & Dynamic Detours");
        System.out.println("========================================================================");
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                System.out.println("[UI] Opened web application in your default browser.");
            } else {
                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                System.out.println("[UI] Launched browser via Windows shell protocol.");
            }
        } catch (Exception ignored) {
            System.out.println("[NOTE] Open in browser: " + url);
        }
    }

    private static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private static void runTerminalInteractiveLoop(TransportServer server) {
        System.out.println("\n[TERMINAL CONSOLE ACTIVE] Type 'help' for commands, or interact with website.");
        System.out.println("Commands: status | pending | pending-drivers | verify <roll> | approve-driver <id> | conditions | open | exit");
        System.out.print("transport> ");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            if (!scanner.hasNextLine()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    break;
                }
                continue;
            }
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                System.out.print("transport> ");
                continue;
            }

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase();

            switch (cmd) {
                case "help":
                    System.out.println("\n--- Available Commands ---");
                    System.out.println("  status                : View real-time transport stats & metrics");
                    System.out.println("  pending               : View students awaiting admin verification");
                    System.out.println("  pending-drivers       : View driver signup applications awaiting license approval");
                    System.out.println("  verify <roll>         : Approve/Verify a student (e.g. verify 26CSR031)");
                    System.out.println("  reject <roll>         : Reject an invalid pending student registration");
                    System.out.println("  remove-student <roll> : Permanently remove a student (e.g. remove-student 26CSR001)");
                    System.out.println("  approve-driver <id>   : Approve a registered driver (e.g. approve-driver drv_123)");
                    System.out.println("  reject-driver <id>    : Reject a driver application");
                    System.out.println("  drivers               : List all fleet drivers and approval status");
                    System.out.println("  conditions            : List 4 simulated road condition toggles");
                    System.out.println("  toggle <key> <0|1>    : Toggle a condition (e.g. toggle ROAD_BLOCK 1)");
                    System.out.println("  students              : List verified students");
                    System.out.println("  buses                 : List bus statuses and passenger counts");
                    System.out.println("  stops                 : List all registered stops with coordinates");
                    System.out.println("  open                  : Open web portal in default browser");
                    System.out.println("  exit                  : Shutdown the web server and exit");
                    break;

                case "status":
                    Map<String, Object> stats = DatabaseManager.getSystemStats();
                    System.out.println("\n--- System Status ---");
                    System.out.println("  Buses: " + stats.get("total_buses"));
                    System.out.println("  Stops: " + stats.get("total_stops"));
                    System.out.println("  Verified Students: " + stats.get("verified_students"));
                    System.out.println("  Pending Students: " + stats.get("pending_students"));
                    System.out.println("  Active Drivers: " + stats.get("active_drivers"));
                    System.out.println("  Pending Drivers: " + stats.get("pending_drivers"));
                    System.out.println("  Active Simulated Conditions: " + stats.get("active_conditions"));
                    break;

                case "pending":
                    List<Map<String, Object>> pending = DatabaseManager.getPendingStudents();
                    System.out.println("\n--- Students Pending Verification (" + pending.size() + ") ---");
                    if (pending.isEmpty()) {
                        System.out.println("  No registrations pending verification.");
                    } else {
                        for (Map<String, Object> s : pending) {
                            System.out.printf("  [%s] %s | %s | Stop: %s | Bus: %s%n",
                                    s.get("roll_no"), s.get("full_name"), s.get("department"),
                                    s.get("stop_name"), s.get("bus_id"));
                        }
                        System.out.println("  -> Use 'verify <roll>' to approve.");
                    }
                    break;

                case "pending-drivers":
                    List<Map<String, Object>> pendingDrivers = DatabaseManager.getPendingDrivers();
                    System.out.println("\n--- Drivers Pending License Verification (" + pendingDrivers.size() + ") ---");
                    if (pendingDrivers.isEmpty()) {
                        System.out.println("  No driver applications pending approval.");
                    } else {
                        for (Map<String, Object> d : pendingDrivers) {
                            System.out.printf("  [%s] %s | Phone: %s | License: %s | Exp: %s yrs | Bus: %s%n",
                                    d.get("driver_id"), d.get("name"), d.get("phone"),
                                    d.get("license_number"), d.get("experience_years"), d.get("assigned_bus"));
                        }
                        System.out.println("  -> Use 'approve-driver <id>' to verify & approve.");
                    }
                    break;

                case "verify":
                    if (parts.length < 2) {
                        System.out.println("Usage: verify <roll_number>");
                    } else {
                        String roll = parts[1].toUpperCase();
                        boolean ok = DatabaseManager.verifyStudent(roll);
                        if (ok) {
                            System.out.println("  [SUCCESS] Student " + roll + " has been VERIFIED and can now log in!");
                        } else {
                            System.out.println("  [ERROR] Could not verify student. Check roll number.");
                        }
                    }
                    break;

                case "reject":
                    if (parts.length < 2) {
                        System.out.println("Usage: reject <roll_number>");
                    } else {
                        String roll = parts[1].toUpperCase();
                        boolean ok = DatabaseManager.rejectStudent(roll);
                        if (ok) System.out.println("  [REJECTED] Student " + roll + " registration removed.");
                        else System.out.println("  [ERROR] Student not found or already verified.");
                    }
                    break;

                case "remove-student":
                    if (parts.length < 2) {
                        System.out.println("Usage: remove-student <roll_number>");
                    } else {
                        String roll = parts[1].toUpperCase();
                        boolean ok = DatabaseManager.removeStudent(roll);
                        if (ok) System.out.println("  [REMOVED] Student " + roll + " has been permanently removed.");
                        else System.out.println("  [ERROR] Student not found.");
                    }
                    break;

                case "approve-driver":
                    if (parts.length < 2) {
                        System.out.println("Usage: approve-driver <driver_id> [optional_bus_id]");
                    } else {
                        String id = parts[1].toLowerCase();
                        String b = parts.length > 2 ? parts[2].toUpperCase() : null;
                        boolean ok = DatabaseManager.approveDriver(id, b);
                        if (ok) System.out.println("  [SUCCESS] Driver " + id + " has been APPROVED and can now log in!");
                        else System.out.println("  [ERROR] Could not approve driver. Check driver ID.");
                    }
                    break;

                case "reject-driver":
                    if (parts.length < 2) {
                        System.out.println("Usage: reject-driver <driver_id>");
                    } else {
                        String id = parts[1].toLowerCase();
                        boolean ok = DatabaseManager.rejectDriver(id);
                        if (ok) System.out.println("  [REJECTED] Driver application for " + id + " has been rejected.");
                        else System.out.println("  [ERROR] Driver not found.");
                    }
                    break;

                case "drivers":
                    List<Map<String, Object>> allDrv = DatabaseManager.getAllDrivers();
                    System.out.println("\n--- Fleet Drivers (" + allDrv.size() + ") ---");
                    for (Map<String, Object> d : allDrv) {
                        int appr = (int) d.getOrDefault("is_approved", 0);
                        String statusStr = appr == 1 ? "APPROVED" : (appr == 0 ? "PENDING" : "REJECTED");
                        System.out.printf("  [%s] %-20s | License: %-18s | Bus: %s | %s%n",
                                d.get("driver_id"), d.get("name"), d.get("license_number"),
                                d.get("assigned_bus"), statusStr);
                    }
                    break;

                case "conditions":
                    List<Map<String, Object>> conds = DatabaseManager.getSimulatedConditions();
                    System.out.println("\n--- Simulated Road Conditions (Dummy Simulation) ---");
                    for (Map<String, Object> c : conds) {
                        int en = (int) c.getOrDefault("is_enabled", 0);
                        System.out.printf("  [%s] %s %-30s | State: %s | Penalty: +%sm | Segment: %s -> %s%n",
                                c.get("condition_key"), c.get("icon"), c.get("condition_name"),
                                en == 1 ? "ON (ACTIVE)" : "OFF", c.get("penalty_mins"),
                                c.get("affected_source"), c.get("affected_destination"));
                    }
                    break;

                case "toggle":
                    if (parts.length < 3) {
                        System.out.println("Usage: toggle <condition_key> <0|1> (e.g. toggle ROAD_BLOCK 1)");
                    } else {
                        String k = parts[1].toUpperCase();
                        int en = Integer.parseInt(parts[2]);
                        boolean ok = DatabaseManager.toggleSimulatedCondition(k, en);
                        if (ok) System.out.println("  [UPDATED] Condition " + k + " is now " + (en == 1 ? "ENABLED (ON)" : "DISABLED (OFF)"));
                        else System.out.println("  [ERROR] Condition key not found.");
                    }
                    break;

                case "students":
                    List<Map<String, Object>> students = DatabaseManager.getAllStudents("ALL");
                    System.out.println("\n--- Registered & Verified Students (" + students.size() + ") ---");
                    for (int i = 0; i < Math.min(15, students.size()); i++) {
                        Map<String, Object> s = students.get(i);
                        System.out.printf("  %s | %-20s | %-10s | Bus: %s | Stop: %s%n",
                                s.get("roll_no"), s.get("full_name"), s.get("department"),
                                s.get("bus_id"), s.get("stop_name"));
                    }
                    if (students.size() > 15) {
                        System.out.println("  ... and " + (students.size() - 15) + " more (view all in website)");
                    }
                    break;

                case "buses":
                    List<Map<String, Object>> buses = DatabaseManager.getAllBuses();
                    System.out.println("\n--- Bus Fleet & Drivers ---");
                    for (Map<String, Object> b : buses) {
                        System.out.printf("  Bus %s: %s | Cap: %s | Passengers: %s | Driver: %s (%s)%n",
                                b.get("bus_id"), b.get("bus_type"), b.get("capacity"),
                                b.get("registered_count"), b.get("driver_name"), b.get("driver_phone"));
                    }
                    break;

                case "stops":
                    List<Map<String, Object>> stops = DatabaseManager.getAllStops();
                    System.out.println("\n--- Bus Stops (" + stops.size() + ") ---");
                    for (Map<String, Object> st : stops) {
                        System.out.printf("  [%s] %-25s (Route: %s) Coords: %.4f, %.4f | Students: %s%n",
                                st.get("stop_id"), st.get("stop_name"), st.get("bus_route"),
                                st.get("latitude"), st.get("longitude"), st.get("student_count"));
                    }
                    break;

                case "open":
                    openBrowser("http://localhost:" + PORT);
                    break;

                case "exit":
                case "quit":
                    System.out.println("\nShutting down Transport Web Server...");
                    server.stop();
                    System.out.println("Server stopped. Goodbye!");
                    System.exit(0);
                    return;

                default:
                    System.out.println("Unknown command: '" + cmd + "'. Type 'help' for options.");
                    break;
            }

            System.out.print("transport> ");
        }
    }
}
