import java.util.ArrayDeque;

public class PickupStop {

    private String stopId;
    private String stopName;

    private ArrayDeque<Student> waitingStudents;

    public PickupStop(String stopId, String stopName) {

        this.stopId = stopId;
        this.stopName = stopName;

        waitingStudents = new ArrayDeque<>();
    }

    public String getStopId() {
        return stopId;
    }

    public String getStopName() {
        return stopName;
    }

    public ArrayDeque<Student> getWaitingStudents() {
        return waitingStudents;
    }

    public void addStudent(Student student) {
        waitingStudents.add(student);
    }

    public Student removeStudent() {
        return waitingStudents.poll();
    }

    public boolean isEmpty() {
        return waitingStudents.isEmpty();
    }

    public int getWaitingCount() {
        return waitingStudents.size();
    }
}