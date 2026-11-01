import java.util.List;
import java.util.Map;

public class NearestStopPolicy
        implements PickupPlanningPolicy {

    @Override
    public String selectNextStop(
            String currentStop,
            List<String> availableStops,
            WeightedGraph graph) {

        Map<String, Integer> distances =
                Dijkstra.shortestPath(
                        graph,
                        currentStop
                );

        String nearestStop = null;

        int minimumDistance =
                Integer.MAX_VALUE;

        for (String stop : availableStops) {

            int distance =
                    distances.getOrDefault(
                            stop,
                            Integer.MAX_VALUE
                    );

            if (distance < minimumDistance) {

                minimumDistance = distance;
                nearestStop = stop;

            } else if (distance == minimumDistance) {

                if (nearestStop == null ||
                        stop.compareTo(nearestStop) < 0) {

                    nearestStop = stop;
                }
            }
        }

        return nearestStop;
    }
}