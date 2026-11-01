import java.util.List;

public interface PickupPlanningPolicy {

    String selectNextStop(
            String currentStop,
            List<String> availableStops,
            WeightedGraph graph
    );
}