import java.util.HashSet;

public abstract class CollegeBus {

    protected String busId;
    protected int capacity;

    protected HashSet<String> boardedStudents;

    public CollegeBus(String busId, int capacity) {

        this.busId = busId;
        this.capacity = capacity;

        boardedStudents = new HashSet<>();
    }

    public String getBusId() {
        return busId;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getAvailableSeats() {
        return capacity - boardedStudents.size();
    }

    public HashSet<String> getBoardedStudents() {
        return boardedStudents;
    }

    public boolean boardStudent(Student student) {

        // Prevent duplicate boarding
        if (boardedStudents.contains(student.getStudentId())) {
            return false;
        }

        // Prevent overbooking
        if (boardedStudents.size() >= capacity) {
            return false;
        }

        boardedStudents.add(student.getStudentId());

        student.setBoarded(true);

        return true;
    }

    public boolean isFull() {
        return boardedStudents.size() >= capacity;
    }

    public abstract boolean canUseRoad(String roadType);
}