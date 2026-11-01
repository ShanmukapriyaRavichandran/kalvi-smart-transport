public class Edge {

    private String destination;
    private int travelTime;

    public Edge(String destination, int travelTime) {

        this.destination = destination;
        this.travelTime = travelTime;
    }

    public String getDestination() {
        return destination;
    }

    public int getTravelTime() {
        return travelTime;
    }
}