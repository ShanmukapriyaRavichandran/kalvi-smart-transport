import java.util.ArrayList;
import java.util.HashSet;

public class DailyTrip {

    private CollegeBus bus;

    private ArrayList<String> route;

    private HashSet<String> assignedStudents;

    private boolean completed;

    public DailyTrip(CollegeBus bus) {

        this.bus = bus;

        route = new ArrayList<>();

        assignedStudents = new HashSet<>();

        completed = false;
    }

    public CollegeBus getBus() {
        return bus;
    }

    public ArrayList<String> getRoute() {
        return route;
    }

    public HashSet<String> getAssignedStudents() {
        return assignedStudents;
    }

    public boolean assignStudent(Student student) {

        if (assignedStudents.contains(student.getStudentId())) {
            return false;
        }

        if (bus.isFull()) {
            return false;
        }

        assignedStudents.add(student.getStudentId());

        return true;
    }

    public void addStop(String stopId) {

        if (!route.contains(stopId)) {
            route.add(stopId);
        }
    }

    public void completeTrip() {
        completed = true;
    }

    public boolean isCompleted() {
        return completed;
    }
}