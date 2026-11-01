import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Lightweight, lightning-fast Java HTTP Web Server.
 * Runs independently from terminal/command prompt.
 * Binds to 0.0.0.0 (all network interfaces, accessible via LAN IP & localhost).
 * Serves website assets and provides full REST API for Student, Driver, and Admin.
 * Supports real road-based routing, driver signup with admin verification,
 * secure role-based login, and simulated road condition toggles with dynamic detours.
 */
public class TransportServer {

    private final int port;
    private HttpServer server;

    public TransportServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // Static Web Files Handler
        server.createContext("/", new StaticFileHandler());

        // Authentication & Role Validation Endpoints
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/auth/register", new RegisterHandler());

        // Student Portal API
        server.createContext("/api/student/dashboard", new StudentDashboardHandler());
        server.createContext("/api/student/update-stop", new UpdateStudentStopHandler());

        // Driver Module & Signup API
        server.createContext("/api/driver/register", new DriverRegisterHandler());
        server.createContext("/api/driver/route", new DriverRouteHandler());

        // Admin Management APIs
        server.createContext("/api/admin/pending", new PendingStudentsHandler());
        server.createContext("/api/admin/verify", new VerifyStudentHandler());
        server.createContext("/api/admin/reject", new RejectStudentHandler());
        server.createContext("/api/admin/remove-student", new RemoveStudentHandler());
        server.createContext("/api/admin/students", new AllStudentsHandler());

        // Admin Driver Approval APIs
        server.createContext("/api/admin/pending-drivers", new PendingDriversHandler());
        server.createContext("/api/admin/approve-driver", new ApproveDriverHandler());
        server.createContext("/api/admin/reject-driver", new RejectDriverHandler());
        server.createContext("/api/admin/drivers", new AllDriversHandler());

        // Stops, Fleet & Incident APIs
        server.createContext("/api/admin/stops", new StopsHandler());
        server.createContext("/api/admin/add-stop", new AddStopHandler());
        server.createContext("/api/admin/buses", new BusesHandler());
        server.createContext("/api/admin/incidents", new IncidentsHandler());
        server.createContext("/api/admin/toggle-incident", new ToggleIncidentHandler());
        server.createContext("/api/admin/add-incident", new AddIncidentHandler());
        server.createContext("/api/admin/stats", new StatsHandler());

        // Dummy Simulated Road Condition Toggles
        server.createContext("/api/admin/conditions", new SimulatedConditionsHandler());
        server.createContext("/api/admin/toggle-condition", new ToggleConditionHandler());

        // Dynamic Route Optimization API (Dijkstra + Real Road Coordinates)
        server.createContext("/api/routes/optimize", new RouteOptimizerHandler());

        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ==========================================
    //           STATIC FILE HANDLER
    // ==========================================

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            File file = new File("web" + path);
            if (!file.exists() || file.isDirectory()) {
                file = new File("web/index.html");
            }

            if (!file.exists()) {
                String notFound = "<h1>404 Not Found</h1>";
                sendResponse(exchange, 404, "text/html", notFound.getBytes(StandardCharsets.UTF_8));
                return;
            }

            String mimeType = "text/plain";
            String lower = file.getName().toLowerCase();
            if (lower.endsWith(".html")) mimeType = "text/html; charset=utf-8";
            else if (lower.endsWith(".css")) mimeType = "text/css; charset=utf-8";
            else if (lower.endsWith(".js")) mimeType = "application/javascript; charset=utf-8";
            else if (lower.endsWith(".json")) mimeType = "application/json; charset=utf-8";
            else if (lower.endsWith(".png")) mimeType = "image/png";
            else if (lower.endsWith(".svg")) mimeType = "image/svg+xml";

            byte[] bytes = Files.readAllBytes(file.toPath());
            sendResponse(exchange, 200, mimeType, bytes);
        }
    }

    // ==========================================
    //        ROLE-BASED AUTHENTICATION
    // ==========================================

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> body = parseJsonBody(exchange);
            String role = body.getOrDefault("role", "STUDENT").toUpperCase();
            String username = body.getOrDefault("username", body.getOrDefault("roll_no", body.getOrDefault("driver_id", body.getOrDefault("identifier", "")))).trim();
            String password = body.getOrDefault("password", "").trim();

            Map<String, Object> res;
            if ("ADMIN".equals(role)) {
                res = DatabaseManager.authenticateAdmin(username, password);
            } else if ("DRIVER".equals(role)) {
                res = DatabaseManager.authenticateDriver(username, password);
            } else {
                res = DatabaseManager.authenticateStudent(username, password);
            }

            int status = 401;
            if (Boolean.TRUE.equals(res.get("success"))) {
                status = 200;
            } else if (Boolean.TRUE.equals(res.get("pendingVerification")) || Boolean.TRUE.equals(res.get("pendingApproval"))) {
                status = 403;
            }

            sendJsonResponse(exchange, status, mapToJson(res));
        }
    }

    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> b = parseJsonBody(exchange);
            String roll = b.getOrDefault("roll_no", "");
            String name = b.getOrDefault("full_name", "");
            String dept = b.getOrDefault("department", "");
            String year = b.getOrDefault("current_year", "");
            String stop = b.getOrDefault("stop_name", "");
            String email = b.getOrDefault("email", "");
            String phone = b.getOrDefault("phone", "");
            String pass = b.getOrDefault("password", "");

            if (roll.isEmpty() || name.isEmpty() || stop.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Missing required fields.\"}");
                return;
            }

            Map<String, Object> res = DatabaseManager.registerStudent(roll, name, dept, year, stop, email, phone, pass);
            int code = Boolean.TRUE.equals(res.get("success")) ? 201 : 400;
            sendJsonResponse(exchange, code, mapToJson(res));
        }
    }

    // ==========================================
    //       DRIVER SIGNUP & REGISTRATION
    // ==========================================

    static class DriverRegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> b = parseJsonBody(exchange);
            String driverId = b.getOrDefault("driver_id", "");
            String name = b.getOrDefault("full_name", b.getOrDefault("name", ""));
            String phone = b.getOrDefault("phone", "");
            String licenseNumber = b.getOrDefault("license_number", "");
            String licenseDoc = b.getOrDefault("license_doc", "uploaded_driving_license.pdf");
            String assignedBus = b.getOrDefault("assigned_bus", "B01");
            String pass = b.getOrDefault("password", "");
            int experience = 0;
            try {
                experience = Integer.parseInt(b.getOrDefault("experience_years", "0"));
            } catch (NumberFormatException ignored) {}

            if (name.isEmpty() || licenseNumber.isEmpty() || pass.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Name, driving license number, and password are required.\"}");
                return;
            }

            Map<String, Object> res = DatabaseManager.registerDriver(driverId, pass, name, phone, licenseNumber, licenseDoc, experience, assignedBus);
            int code = Boolean.TRUE.equals(res.get("success")) ? 201 : 400;
            sendJsonResponse(exchange, code, mapToJson(res));
        }
    }

    static class PendingDriversHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> list = DatabaseManager.getPendingDrivers();
            sendJsonResponse(exchange, 200, listToJson(list));
        }
    }

    static class ApproveDriverHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> body = parseJsonBody(exchange);
            String driverId = body.getOrDefault("driver_id", "");
            String assignedBus = body.getOrDefault("assigned_bus", "");
            boolean ok = DatabaseManager.approveDriver(driverId, assignedBus);
            sendJsonResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"driver_id\":\"" + driverId + "\"}");
        }
    }

    static class RejectDriverHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> body = parseJsonBody(exchange);
            String driverId = body.getOrDefault("driver_id", "");
            boolean ok = DatabaseManager.rejectDriver(driverId);
            sendJsonResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"driver_id\":\"" + driverId + "\"}");
        }
    }

    static class AllDriversHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> list = DatabaseManager.getAllDrivers();
            sendJsonResponse(exchange, 200, listToJson(list));
        }
    }

    // ==========================================
    //       SIMULATED ROAD CONDITION TOGGLES
    // ==========================================

    static class SimulatedConditionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> list = DatabaseManager.getSimulatedConditions();
            sendJsonResponse(exchange, 200, listToJson(list));
        }
    }

    static class ToggleConditionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> b = parseJsonBody(exchange);
            String key = b.getOrDefault("condition_key", "").trim();
            String val = b.getOrDefault("is_enabled", "0").trim().toLowerCase();
            int isEnabled = ("true".equals(val) || "1".equals(val)) ? 1 : 0;

            boolean ok = DatabaseManager.toggleSimulatedCondition(key, isEnabled);
            sendJsonResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"condition_key\":\"" + key + "\",\"is_enabled\":" + isEnabled + "}");
        }
    }

    // ==========================================
    //           STUDENT DASHBOARD API
    // ==========================================

    static class StudentDashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(exchange);
            String roll = params.get("roll_no");
            if (roll == null || roll.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"roll_no is required\"}");
                return;
            }

            Map<String, Object> student = null;
            for (Map<String, Object> s : DatabaseManager.getAllStudents("ALL")) {
                if (roll.equalsIgnoreCase((String) s.get("roll_no"))) {
                    student = s;
                    break;
                }
            }

            if (student == null) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Student record not found or not verified yet.\"}");
                return;
            }

            String busId = (String) student.get("bus_id");
            String stopName = (String) student.get("stop_name");

            // Bus info
            Map<String, Object> busInfo = null;
            for (Map<String, Object> b : DatabaseManager.getAllBuses()) {
                if (busId.equalsIgnoreCase((String) b.get("bus_id"))) {
                    busInfo = b;
                    break;
                }
            }

            // Fellow students at this stop
            List<Map<String, Object>> fellowStudents = new ArrayList<>();
            for (Map<String, Object> s : DatabaseManager.getAllStudents(busId)) {
                if (stopName.equalsIgnoreCase((String) s.get("stop_name")) && !roll.equalsIgnoreCase((String) s.get("roll_no"))) {
                    Map<String, Object> f = new HashMap<>();
                    f.put("roll_no", s.get("roll_no"));
                    f.put("full_name", s.get("full_name"));
                    f.put("department", s.get("department"));
                    f.put("current_year", s.get("current_year"));
                    fellowStudents.add(f);
                }
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("student", student);
            resp.put("bus", busInfo);
            resp.put("fellow_students", fellowStudents);
            resp.put("stops", DatabaseManager.getStopsForBus(busId));

            sendJsonResponse(exchange, 200, mapToJson(resp));
        }
    }

    static class UpdateStudentStopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> body = parseJsonBody(exchange);
            String roll = body.getOrDefault("roll_no", "").trim();
            String stop = body.getOrDefault("stop_name", "").trim();
            if (roll.isEmpty() || stop.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"roll_no and stop_name required\"}");
                return;
            }
            boolean ok = DatabaseManager.updateStudentStop(roll, stop);
            String bus = DatabaseManager.getBusForStop(stop);
            sendJsonResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"bus_id\":\"" + bus + "\",\"stop_name\":\"" + stop + "\"}");
        }
    }

    // ==========================================
    //           ADMIN STUDENT APIS
    // ==========================================

    static class PendingStudentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> list = DatabaseManager.getPendingStudents();
            sendJsonResponse(exchange, 200, listToJson(list));
        }
    }

    static class VerifyStudentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> body = parseJsonBody(exchange);
            String roll = body.getOrDefault("roll_no", "");
            boolean ok = DatabaseManager.verifyStudent(roll);
            sendJsonResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"roll_no\":\"" + roll + "\"}");
        }
    }

    static class RejectStudentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> body = parseJsonBody(exchange);
            String roll = body.getOrDefault("roll_no", "");
            boolean ok = DatabaseManager.rejectStudent(roll);
            sendJsonResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"roll_no\":\"" + roll + "\"}");
        }
    }

    static class RemoveStudentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> body = parseJsonBody(exchange);
            String roll = body.getOrDefault("roll_no", "");
            boolean ok = DatabaseManager.removeStudent(roll);
            sendJsonResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"roll_no\":\"" + roll + "\"}");
        }
    }

    static class AllStudentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> q = parseQueryParams(exchange);
            String bus = q.getOrDefault("bus", "ALL");
            List<Map<String, Object>> list = DatabaseManager.getAllStudents(bus);
            sendJsonResponse(exchange, 200, listToJson(list));
        }
    }

    static class StopsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> q = parseQueryParams(exchange);
            String bus = q.get("bus");
            List<Map<String, Object>> list = (bus != null && !bus.isEmpty()) ?
                    DatabaseManager.getStopsForBus(bus) : DatabaseManager.getAllStops();
            sendJsonResponse(exchange, 200, listToJson(list));
        }
    }

    static class AddStopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> b = parseJsonBody(exchange);
            String name = b.getOrDefault("stop_name", "");
            String bus = b.getOrDefault("bus_route", "B01");
            double lat = Double.parseDouble(b.getOrDefault("latitude", "11.3400"));
            double lng = Double.parseDouble(b.getOrDefault("longitude", "77.7200"));
            String mTime = b.getOrDefault("morning_time", "07:30 AM");
            String eTime = b.getOrDefault("evening_time", "05:15 PM");

            boolean ok = DatabaseManager.addStop(name, bus, lat, lng, mTime, eTime);
            sendJsonResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"stop_name\":\"" + name + "\"}");
        }
    }

    static class BusesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> list = DatabaseManager.getAllBuses();
            sendJsonResponse(exchange, 200, listToJson(list));
        }
    }

    static class IncidentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> list = DatabaseManager.getAllIncidents();
            sendJsonResponse(exchange, 200, listToJson(list));
        }
    }

    static class ToggleIncidentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> b = parseJsonBody(exchange);
            int id = Integer.parseInt(b.getOrDefault("id", "0"));
            int active = Integer.parseInt(b.getOrDefault("active", "1"));
            boolean ok = DatabaseManager.toggleIncident(id, active);
            sendJsonResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"id\":" + id + ",\"active\":" + active + "}");
        }
    }

    static class AddIncidentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> b = parseJsonBody(exchange);
            String src = b.getOrDefault("road_source", "");
            String dst = b.getOrDefault("road_destination", "");
            String type = b.getOrDefault("incident_type", "ROAD_BLOCK");
            int penalty = Integer.parseInt(b.getOrDefault("penalty_mins", "15"));
            String desc = b.getOrDefault("description", "Road condition alert reported");

            boolean ok = DatabaseManager.addIncident(src, dst, type, penalty, desc);
            sendJsonResponse(exchange, ok ? 201 : 400, "{\"success\":" + ok + "}");
        }
    }

    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> stats = DatabaseManager.getSystemStats();
            sendJsonResponse(exchange, 200, mapToJson(stats));
        }
    }

    // ==========================================
    //     ROUTE OPTIMIZATION & DRIVER API
    // ==========================================

    // In-memory geometry cache for OSRM routes
    private static final Map<String, List<List<Double>>> osrmGeometryCache = new ConcurrentHashMap<>();

    public static List<List<Double>> fetchOsrmLeg(double lat1, double lng1, double lat2, double lng2) {
        String cacheKey = String.format(Locale.US, "%.5f,%.5f;%.5f,%.5f", lng1, lat1, lng2, lat2);
        if (osrmGeometryCache.containsKey(cacheKey)) {
            return osrmGeometryCache.get(cacheKey);
        }

        try {
            String url = "https://router.project-osrm.org/route/v1/driving/" + cacheKey + "?overview=full&geometries=geojson&steps=false";
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(6)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body().contains("\"code\":\"Ok\"")) {
                List<List<Double>> coords = parseGeoJsonCoordinates(resp.body());
                if (coords != null && !coords.isEmpty()) {
                    osrmGeometryCache.put(cacheKey, coords);
                    return coords;
                }
            }
        } catch (Exception e) {
            System.err.println("[OSRM Warning] Failed fetching leg (" + lat1 + "," + lng1 + " -> " + lat2 + "," + lng2 + "): " + e.getMessage());
        }

        List<List<Double>> fallback = new ArrayList<>();
        fallback.add(List.of(lat1, lng1));
        fallback.add(List.of(lat2, lng2));
        return fallback;
    }

    public static List<List<Double>> fetchOsrmDetourLeg(double lat1, double lng1, List<double[]> waypoints, double lat2, double lng2) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(String.format(Locale.US, "%.5f,%.5f;", lng1, lat1));
        if (waypoints != null) {
            for (double[] pt : waypoints) {
                keyBuilder.append(String.format(Locale.US, "%.5f,%.5f;", pt[1], pt[0]));
            }
        }
        keyBuilder.append(String.format(Locale.US, "%.5f,%.5f", lng2, lat2));
        String cacheKey = keyBuilder.toString();
        if (osrmGeometryCache.containsKey(cacheKey)) {
            return osrmGeometryCache.get(cacheKey);
        }

        try {
            String url = "https://router.project-osrm.org/route/v1/driving/" + cacheKey + "?overview=full&geometries=geojson&steps=false";
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(6)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body().contains("\"code\":\"Ok\"")) {
                List<List<Double>> coords = parseGeoJsonCoordinates(resp.body());
                if (coords != null && !coords.isEmpty()) {
                    osrmGeometryCache.put(cacheKey, coords);
                    return coords;
                }
            }
        } catch (Exception e) {
            System.err.println("[OSRM Warning] Failed fetching detour leg: " + e.getMessage());
        }

        List<List<Double>> fallback = new ArrayList<>();
        fallback.add(List.of(lat1, lng1));
        if (waypoints != null) {
            for (double[] pt : waypoints) {
                fallback.add(List.of(pt[0], pt[1]));
            }
        }
        fallback.add(List.of(lat2, lng2));
        return fallback;
    }

    public static List<List<Double>> fetchOsrmDetourLeg(double lat1, double lng1, double bLat, double bLng, double lat2, double lng2) {
        return fetchOsrmDetourLeg(lat1, lng1, Collections.singletonList(new double[]{bLat, bLng}), lat2, lng2);
    }

    public static List<List<Double>> fetchOsrmRoadGeometry(List<double[]> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) return Collections.emptyList();
        if (waypoints.size() == 1) {
            List<List<Double>> single = new ArrayList<>();
            single.add(List.of(waypoints.get(0)[0], waypoints.get(0)[1]));
            return single;
        }

        StringBuilder keyBuilder = new StringBuilder();
        for (double[] pt : waypoints) {
            keyBuilder.append(String.format(Locale.US, "%.5f,%.5f;", pt[1], pt[0]));
        }
        String cacheKey = keyBuilder.toString();
        if (osrmGeometryCache.containsKey(cacheKey)) {
            return osrmGeometryCache.get(cacheKey);
        }

        try {
            String coordsParam = cacheKey.substring(0, cacheKey.length() - 1);
            String url = "https://router.project-osrm.org/route/v1/driving/" + coordsParam + "?overview=full&geometries=geojson&steps=false";
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body().contains("\"code\":\"Ok\"")) {
                List<List<Double>> coords = parseGeoJsonCoordinates(resp.body());
                if (coords != null && !coords.isEmpty()) {
                    osrmGeometryCache.put(cacheKey, coords);
                    return coords;
                }
            }
        } catch (Exception e) {
            System.err.println("[OSRM Warning] Failed fetching OSRM road geometry: " + e.getMessage());
        }

        // Direct road connection fallback between waypoints
        List<List<Double>> fallback = new ArrayList<>();
        for (double[] pt : waypoints) {
            fallback.add(List.of(pt[0], pt[1]));
        }
        return fallback;
    }

    public static List<List<Double>> parseGeoJsonCoordinates(String body) {
        List<List<Double>> result = new ArrayList<>();
        int geomIdx = body.indexOf("\"geometry\":");
        if (geomIdx < 0) return result;
        int coordIdx = body.indexOf("\"coordinates\":[", geomIdx);
        if (coordIdx < 0) return result;
        int start = coordIdx + "\"coordinates\":[".length();
        int depth = 1;
        int end = -1;
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        if (end <= start) return result;
        String coordsStr = body.substring(start, end);
        String[] pairs = coordsStr.split("\\],\\s*\\[");
        for (String p : pairs) {
            p = p.replace("[", "").replace("]", "").trim();
            String[] parts = p.split(",");
            if (parts.length >= 2) {
                try {
                    double lon = Double.parseDouble(parts[0].trim());
                    double lat = Double.parseDouble(parts[1].trim());
                    result.add(List.of(lat, lon));
                } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    static class RouteOptimizerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> q = parseQueryParams(exchange);
            String busId = q.getOrDefault("bus_id", "B01").trim();
            String shift = q.getOrDefault("shift", "MORNING").trim().toUpperCase();
            if (!"MORNING".equals(shift) && !"EVENING".equals(shift)) {
                shift = "MORNING";
            }

            // 1. Load Road Graph strictly for this bus corridor
            Dijkstra.WeightedGraph graph = DatabaseManager.loadRoadGraph(busId);

            // 2. Load strictly the assigned stops for this bus (ordered 1 to 10)
            List<Map<String, Object>> stopRows = DatabaseManager.getStopsForBus(busId);
            Map<String, double[]> stopCoords = loadAllCoordinates();

            List<String> stopNames = new ArrayList<>();
            List<Map<String, Object>> displayStops = new ArrayList<>();

            if ("MORNING".equals(shift)) {
                // Morning pickup: starts from Stop 1 -> 2 -> ... -> 10 -> Campus
                for (Map<String, Object> s : stopRows) {
                    String name = (String) s.get("stop_name");
                    stopNames.add(name);
                    displayStops.add(new HashMap<>(s));
                }
                stopNames.add("Kalvi College Campus");

                Map<String, Object> campusRow = new HashMap<>();
                campusRow.put("stop_id", "CAMPUS");
                campusRow.put("stop_name", "Kalvi College Campus (Depot)");
                campusRow.put("bus_route", busId);
                campusRow.put("latitude", 11.3150);
                campusRow.put("longitude", 77.6780);
                campusRow.put("morning_time", "08:30 AM");
                campusRow.put("evening_time", "05:00 PM");
                campusRow.put("stop_order", 11);
                displayStops.add(campusRow);
            } else {
                // Evening drop: starts from Campus -> 10 -> 9 -> ... -> 2 -> 1
                stopNames.add("Kalvi College Campus");

                Map<String, Object> campusRow = new HashMap<>();
                campusRow.put("stop_id", "CAMPUS");
                campusRow.put("stop_name", "Kalvi College Campus (Depot)");
                campusRow.put("bus_route", busId);
                campusRow.put("latitude", 11.3150);
                campusRow.put("longitude", 77.6780);
                campusRow.put("morning_time", "08:30 AM");
                campusRow.put("evening_time", "05:00 PM");
                campusRow.put("stop_order", 0);
                displayStops.add(campusRow);

                for (int i = stopRows.size() - 1; i >= 0; i--) {
                    Map<String, Object> s = stopRows.get(i);
                    stopNames.add((String) s.get("stop_name"));
                    displayStops.add(new HashMap<>(s));
                }
            }

            // 3. Load Active Incidents and Active Simulated Conditions
            List<Map<String, Object>> activeIncidents = DatabaseManager.getActiveIncidents();
            List<Map<String, Object>> activeConditions = DatabaseManager.getActiveSimulatedConditions();

            // 4. Run Dijkstra Route Optimization Comparison
            Dijkstra.OptimizationComparison comp = Dijkstra.compareRoutes(graph, busId, stopNames, activeIncidents, activeConditions, stopCoords);

            // 5. Build Genuine Leg-by-Leg Road Geometries (Normal & Optimized Detour)
            // Routing leg-by-leg ensures 100% stop visitation (all stops 1..10 visited)
            // and generates distinct detour roads when any condition is triggered.
            List<List<Double>> normalGeometry = new ArrayList<>();
            List<List<Double>> optimizedGeometry = new ArrayList<>();

            for (Dijkstra.RouteLeg leg : comp.legs) {
                // Fetch normal driving leg along real road geometry
                List<List<Double>> normalLeg = fetchOsrmLeg(leg.fromLat, leg.fromLng, leg.toLat, leg.toLng);
                if (normalGeometry.isEmpty()) {
                    normalGeometry.addAll(normalLeg);
                } else {
                    for (int p = 1; p < normalLeg.size(); p++) {
                        normalGeometry.add(normalLeg.get(p));
                    }
                }

                // Fetch optimized detour leg (if affected, route via alternative smallest route to reach stop)
                List<double[]> waypoints = (leg.bypassWaypoints != null && !leg.bypassWaypoints.isEmpty())
                        ? leg.bypassWaypoints
                        : (leg.bypassWaypoint != null ? Collections.singletonList(leg.bypassWaypoint) : Collections.emptyList());

                if (leg.hasDetour && !waypoints.isEmpty()) {
                    List<List<Double>> detourLeg = fetchOsrmDetourLeg(
                        leg.fromLat, leg.fromLng,
                        waypoints,
                        leg.toLat, leg.toLng
                    );
                    if (optimizedGeometry.isEmpty()) {
                        optimizedGeometry.addAll(detourLeg);
                    } else {
                        for (int p = 1; p < detourLeg.size(); p++) {
                            optimizedGeometry.add(detourLeg.get(p));
                        }
                    }
                } else {
                    if (optimizedGeometry.isEmpty()) {
                        optimizedGeometry.addAll(normalLeg);
                    } else {
                        for (int p = 1; p < normalLeg.size(); p++) {
                            optimizedGeometry.add(normalLeg.get(p));
                        }
                    }
                }
            }

            // Coordinate nodes
            List<Map<String, Object>> normalCoords = new ArrayList<>();
            for (String node : comp.normalRoute.pathNodes) {
                double[] c = stopCoords.getOrDefault(node, new double[]{11.3150, 77.6780});
                Map<String, Object> pt = new HashMap<>();
                pt.put("node", node);
                pt.put("lat", c[0]);
                pt.put("lng", c[1]);
                normalCoords.add(pt);
            }

            List<Map<String, Object>> optimizedCoords = new ArrayList<>();
            for (String node : comp.optimizedRoute.pathNodes) {
                String cleanNode = node.contains("(") ? node.substring(0, node.indexOf("(")).trim() : node;
                double[] c = stopCoords.getOrDefault(cleanNode, new double[]{11.3150, 77.6780});
                Map<String, Object> pt = new HashMap<>();
                pt.put("node", node);
                pt.put("lat", c[0]);
                pt.put("lng", c[1]);
                optimizedCoords.add(pt);
            }

            // Affected segments for map visualization
            List<Map<String, Object>> affectedSegList = new ArrayList<>();
            for (Dijkstra.AffectedSegment seg : comp.affectedSegments) {
                double[] c1 = stopCoords.getOrDefault(seg.source, new double[]{11.3410, 77.7172});
                double[] c2 = stopCoords.getOrDefault(seg.destination, new double[]{11.3435, 77.7210});
                Map<String, Object> m = new HashMap<>();
                m.put("source", seg.source);
                m.put("destination", seg.destination);
                m.put("type", seg.type);
                m.put("colorCode", seg.colorCode);
                m.put("icon", seg.icon);
                m.put("penaltyMinutes", seg.penaltyMinutes);
                m.put("isRoadBlock", seg.isRoadBlock);
                m.put("description", seg.description);
                m.put("srcLat", c1[0]);
                m.put("srcLng", c1[1]);
                m.put("dstLat", c2[0]);
                m.put("dstLng", c2[1]);
                affectedSegList.add(m);
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("bus_id", busId);
            resp.put("shift", shift);
            resp.put("campus", DatabaseManager.getCampusStop());
            resp.put("stops", displayStops);
            resp.put("normal_route", comp.normalRoute);
            resp.put("optimized_route", comp.optimizedRoute);
            resp.put("legs", comp.legs);
            resp.put("normal_geometry", normalGeometry);
            resp.put("optimized_geometry", optimizedGeometry);
            resp.put("normal_coords", normalCoords);
            resp.put("optimized_coords", optimizedCoords);
            resp.put("active_incidents", activeIncidents);
            resp.put("active_conditions", activeConditions);
            resp.put("all_conditions", DatabaseManager.getSimulatedConditions());
            resp.put("affected_segments", affectedSegList);
            resp.put("time_saved_minutes", comp.timeSavedMinutes);
            resp.put("has_detour", comp.hasDetour);
            resp.put("is_road_block_active", comp.isRoadBlockActive);

            sendJsonResponse(exchange, 200, mapToJson(resp));
        }
    }

    static class DriverRouteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> q = parseQueryParams(exchange);
            String busId = q.getOrDefault("bus_id", "B01").trim();
            String shift = q.getOrDefault("shift", "MORNING").trim().toUpperCase();

            List<Map<String, Object>> stops = DatabaseManager.getStopsForBus(busId);
            if ("EVENING".equalsIgnoreCase(shift)) {
                Collections.reverse(stops);
            }
            List<Map<String, Object>> students = DatabaseManager.getAllStudents(busId);

            Map<String, Object> busInfo = null;
            for (Map<String, Object> b : DatabaseManager.getAllBuses()) {
                if (busId.equalsIgnoreCase((String) b.get("bus_id"))) {
                    busInfo = b;
                    break;
                }
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("bus", busInfo);
            resp.put("shift", shift);
            resp.put("stops", stops);
            resp.put("students", students);
            resp.put("incidents", DatabaseManager.getActiveIncidents());
            resp.put("active_conditions", DatabaseManager.getActiveSimulatedConditions());

            sendJsonResponse(exchange, 200, mapToJson(resp));
        }
    }

    private static Map<String, double[]> loadAllCoordinates() {
        Map<String, double[]> map = new HashMap<>();
        List<Map<String, Object>> stops = DatabaseManager.getAllStops();
        for (Map<String, Object> s : stops) {
            String name = (String) s.get("stop_name");
            double lat = (double) s.get("latitude");
            double lng = (double) s.get("longitude");
            map.put(name, new double[]{lat, lng});
        }
        map.put("CAMPUS", new double[]{11.3150, 77.6780});
        map.put("Kalvi College Campus", new double[]{11.3150, 77.6780});
        return map;
    }

    // ==========================================
    //            UTILITY HELPERS
    // ==========================================

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, byte[] data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, HEAD");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(statusCode, -1);
            return;
        }
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        sendResponse(exchange, statusCode, "application/json; charset=utf-8", bytes);
    }

    private static Map<String, String> parseQueryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();
        if (query == null || query.isEmpty()) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                           URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            } else if (kv.length == 1) {
                params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), "");
            }
        }
        return params;
    }

    private static Map<String, String> parseJsonBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String text = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        Map<String, String> map = new HashMap<>();
        if (text.isEmpty() || !text.startsWith("{") || !text.endsWith("}")) return map;

        String content = text.substring(1, text.length() - 1);
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        List<String> tokens = new ArrayList<>();

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\"' && (i == 0 || content.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            }
            if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) tokens.add(sb.toString().trim());

        for (String t : tokens) {
            int colon = t.indexOf(':');
            if (colon > 0) {
                String k = stripQuotes(t.substring(0, colon));
                String v = stripQuotes(t.substring(colon + 1));
                map.put(k, v);
            }
        }
        return map;
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        while ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length() < 2) break;
            s = s.substring(1, s.length() - 1).trim();
        }
        return s.replace("\\\"", "\"").replace("\\\\", "\\").trim();
    }

    @SuppressWarnings("unchecked")
    public static String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(e.getKey())).append("\":");
            sb.append(valueToJson(e.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static String listToJson(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(",");
            sb.append(valueToJson(item));
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String valueToJson(Object val) {
        if (val == null) return "null";
        if (val instanceof String) return "\"" + escapeJson((String) val) + "\"";
        if (val instanceof Number || val instanceof Boolean) return val.toString();
        if (val instanceof Map) return mapToJson((Map<String, Object>) val);
        if (val instanceof List) return listToJson((List<?>) val);
        if (val instanceof double[]) {
            double[] arr = (double[]) val;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(arr[i]);
            }
            sb.append("]");
            return sb.toString();
        }
        if (val instanceof Dijkstra.RouteLeg) {
            Dijkstra.RouteLeg leg = (Dijkstra.RouteLeg) val;
            Map<String, Object> m = new HashMap<>();
            m.put("legIndex", leg.legIndex);
            m.put("fromStop", leg.fromStop);
            m.put("toStop", leg.toStop);
            m.put("fromLat", leg.fromLat);
            m.put("fromLng", leg.fromLng);
            m.put("toLat", leg.toLat);
            m.put("toLng", leg.toLng);
            m.put("distanceKm", leg.distanceKm);
            m.put("travelTimeMins", leg.travelTimeMins);
            m.put("isAffected", leg.isAffected);
            m.put("isRoadBlock", leg.isRoadBlock);
            m.put("conditionType", leg.conditionType != null ? leg.conditionType : "");
            m.put("colorCode", leg.colorCode != null ? leg.colorCode : "");
            m.put("icon", leg.icon != null ? leg.icon : "");
            m.put("description", leg.description != null ? leg.description : "");
            m.put("penaltyMinutes", leg.penaltyMinutes);
            m.put("hasDetour", leg.hasDetour);
            if (leg.bypassWaypoint != null) {
                m.put("bypassWaypoint", leg.bypassWaypoint);
            } else {
                m.put("bypassWaypoint", null);
            }
            if (leg.bypassWaypoints != null && !leg.bypassWaypoints.isEmpty()) {
                m.put("bypassWaypoints", leg.bypassWaypoints);
            }
            return mapToJson(m);
        }
        if (val instanceof Dijkstra.RouteResult) {
            Dijkstra.RouteResult r = (Dijkstra.RouteResult) val;
            Map<String, Object> m = new HashMap<>();
            m.put("pathNodes", r.pathNodes);
            m.put("totalTimeMinutes", r.totalTimeMinutes);
            m.put("totalDistanceKm", r.totalDistanceKm);
            m.put("notes", r.notes);
            return mapToJson(m);
        }
        if (val instanceof Dijkstra.AffectedSegment) {
            Dijkstra.AffectedSegment a = (Dijkstra.AffectedSegment) val;
            Map<String, Object> m = new HashMap<>();
            m.put("source", a.source);
            m.put("destination", a.destination);
            m.put("type", a.type);
            m.put("colorCode", a.colorCode);
            m.put("icon", a.icon);
            m.put("penaltyMinutes", a.penaltyMinutes);
            m.put("isRoadBlock", a.isRoadBlock);
            m.put("description", a.description);
            return mapToJson(m);
        }
        return "\"" + escapeJson(val.toString()) + "\"";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
