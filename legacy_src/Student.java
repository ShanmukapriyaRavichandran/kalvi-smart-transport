public class Student {

    private String studentId; // Roll Number (e.g. 25CSR265)
    private String name;
    private String stopId;    // Erode Stop Name (e.g. Erode Bus Stand)
    private String department;
    private String password;  // Format: RollNo@BusNo
    private String assignedBusId;

    private boolean present;
    private boolean authorized;
    private boolean boarded;

    public Student(String studentId, String name, String stopId) {
        this(studentId, name, stopId, "Computer Science (CSE)", "student123", "B01");
    }

    public Student(String studentId, String name, String stopId, String department, String password, String assignedBusId) {
        this.studentId = studentId;
        this.name = name;
        this.stopId = stopId;
        this.department = department != null ? department : "Computer Science (CSE)";
        this.password = password != null ? password : (studentId + "@" + (assignedBusId != null ? assignedBusId : "B01"));
        this.assignedBusId = assignedBusId != null ? assignedBusId : "B01";

        this.present = false;
        this.authorized = false;
        this.boarded = false;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getRollNo() {
        return studentId;
    }

    public String getName() {
        return name;
    }

    public String getStopId() {
        return stopId;
    }

    public String getDepartment() {
        return department;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAssignedBusId() {
        return assignedBusId;
    }

    public void setAssignedBusId(String assignedBusId) {
        this.assignedBusId = assignedBusId;
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isAuthorized() {
        return authorized;
    }

    public boolean isBoarded() {
        return boarded;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

    public void setBoarded(boolean boarded) {
        this.boarded = boarded;
    }

    @Override
    public String toString() {
        return studentId + " - " + name + " - Stop: " + stopId + " (" + assignedBusId + ")";
    }
}