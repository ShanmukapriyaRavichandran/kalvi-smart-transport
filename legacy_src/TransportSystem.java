import java.util.HashMap;
import java.util.Map;

public class TransportSystem {

    private Map<String, Student> students;
    private Map<String, PickupStop> stops;
    private Map<String, CollegeBus> buses;
    private Map<String, FeeRecord> feeRecords;

    private WeightedGraph graph;

    public TransportSystem() {

        students = new HashMap<>();
        stops = new HashMap<>();
        buses = new HashMap<>();
        feeRecords = new HashMap<>();

        graph = new WeightedGraph();
    }

    public void addStudent(Student student) {

        students.put(
                student.getStudentId(),
                student
        );
    }

    public void addStop(PickupStop stop) {

        stops.put(
                stop.getStopId(),
                stop
        );

        graph.addVertex(
                stop.getStopId()
        );
    }

    public void addBus(CollegeBus bus) {

        buses.put(
                bus.getBusId(),
                bus
        );
    }

    public void addFeeRecord(FeeRecord record) {

        feeRecords.put(
                record.getStudentId(),
                record
        );
    }

    public Map<String, Student> getStudents() {
        return students;
    }

    public Map<String, PickupStop> getStops() {
        return stops;
    }

    public Map<String, CollegeBus> getBuses() {
        return buses;
    }

    public Map<String, FeeRecord> getFeeRecords() {
        return feeRecords;
    }

    public WeightedGraph getGraph() {
        return graph;
    }
}