import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeightedGraph {

    private Map<String, List<Edge>> graph;

    public WeightedGraph() {
        graph = new HashMap<>();
    }

    public void addVertex(String vertex) {

        graph.putIfAbsent(vertex, new ArrayList<>());
    }

    public void addEdge(String source,
                        String destination,
                        int travelTime) {

        addVertex(source);
        addVertex(destination);

        graph.get(source).add(
                new Edge(destination, travelTime)
        );

        graph.get(destination).add(
                new Edge(source, travelTime)
        );
    }

    public List<Edge> getNeighbors(String vertex) {

        return graph.getOrDefault(
                vertex,
                new ArrayList<>()
        );
    }

    public Map<String, List<Edge>> getGraph() {
        return graph;
    }
}