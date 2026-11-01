import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TestOsrmLegs {
    public static void main(String[] args) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

            // Let's test Stop 6, 7, 8, 9, 10
            // Stop 6: Pallipalayam: 11.3600, 77.7470
            // Stop 7: Kumarapalayam: 11.4420, 77.7120
            // Stop 8: Bhavani: 11.4500, 77.6830
            // Stop 9: Kalingarayanpalayam: 11.4280, 77.6690
            // Stop 10: Chithode: 11.4010, 77.6650

            String url = "https://router.project-osrm.org/route/v1/driving/77.7470,11.3600;77.7120,11.4420;77.6830,11.4500;77.6690,11.4280;77.6650,11.4010?overview=full&geometries=geojson&steps=true";
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            System.out.println("OSRM Status: " + resp.statusCode());
            String body = resp.body();
            // Let's print the legs summary and distances
            int idx = 0;
            while ((idx = body.indexOf("\"summary\":\"", idx)) != -1) {
                int end = body.indexOf("\"", idx + 11);
                int distIdx = body.indexOf("\"distance\":", end);
                int distEnd = body.indexOf(",", distIdx);
                System.out.println("Leg summary: " + body.substring(idx + 11, end) + ", distance: " + body.substring(distIdx + 11, distEnd));
                idx = end + 1;
            }

            // Also let's check waypoints in the response
            int wpIdx = body.indexOf("\"waypoints\":[");
            if (wpIdx != -1) {
                System.out.println("Waypoints snippet: " + body.substring(wpIdx, Math.min(body.length(), wpIdx + 400)));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

