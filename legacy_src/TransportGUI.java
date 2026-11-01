import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.*;

public class TransportGUI extends JFrame {

    private TransportSystem system;
    private TransportPlanner planner;
    private LoginFrame parentLoginFrame;

    private JTextArea output;
    private JTextField txtSearchRoll;
    private JComboBox<String> cmbFilterBus;
    private JPanel searchBarPanel;
    private JPanel centerPanel;

    public TransportGUI(TransportSystem system, TransportPlanner planner) {
        this(system, planner, null);
    }

    public TransportGUI(TransportSystem system, TransportPlanner planner, LoginFrame parentLoginFrame) {
        this.system = system;
        this.planner = planner;
        this.parentLoginFrame = parentLoginFrame;

        setTitle("Kalvi College Transport Management - Admin Dashboard");
        setSize(1100, 760);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        createGUI();
    }

    private void createGUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBackground(new Color(248, 250, 252));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // ----------------- TOP HEADER ----------------- //
        JPanel topHeader = new JPanel(new BorderLayout(10, 10));
        topHeader.setBackground(new Color(15, 23, 42)); // Deep Navy
        topHeader.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(30, 41, 59), 1, true),
                new EmptyBorder(14, 20, 14, 20)
        ));

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBox.setOpaque(false);

        JLabel title = new JLabel("KALVI COLLEGE TRANSPORT MANAGEMENT SYSTEM");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setForeground(new Color(245, 158, 11)); // Amber

        JLabel subtitle = new JLabel("ADMINISTRATOR DISPATCH & AUDIT DASHBOARD (ERODE DISTRICT FLEET)");
        subtitle.setFont(new Font("Arial", Font.BOLD, 12));
        subtitle.setForeground(new Color(226, 232, 240));

        titleBox.add(title);
        titleBox.add(subtitle);
        topHeader.add(titleBox, BorderLayout.CENTER);

        // Header Logout Button
        JButton btnLogout = new JButton("Logout");
        btnLogout.setFont(new Font("Arial", Font.BOLD, 12));
        btnLogout.setBackground(new Color(51, 65, 85));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.setFocusPainted(false);
        btnLogout.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(71, 85, 105), 1, true),
                new EmptyBorder(8, 16, 8, 16)
        ));
        btnLogout.addActionListener(e -> logout());
        topHeader.add(btnLogout, BorderLayout.EAST);

        mainPanel.add(topHeader, BorderLayout.NORTH);

        // ----------------- CENTER AREA WITH EMBEDDED SEARCH TOOLBAR ----------------- //
        centerPanel = new JPanel(new BorderLayout(8, 8));
        centerPanel.setOpaque(false);

        // Search Bar Toolbar for Student Module (Only visible in Student Directory)
        searchBarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        searchBarPanel.setBackground(new Color(241, 245, 249));
        searchBarPanel.setVisible(false);
        searchBarPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(4, 10, 4, 10)
        ));

        JLabel lblSearch = new JLabel("Search Student by Roll No:");
        lblSearch.setFont(new Font("Arial", Font.BOLD, 12));
        lblSearch.setForeground(new Color(30, 41, 59));

        txtSearchRoll = new JTextField(15);
        txtSearchRoll.setFont(new Font("Arial", Font.BOLD, 13));
        txtSearchRoll.setToolTipText("Enter student roll number, e.g. 25CSR265, 25CSR201, 25ITR201");

        JButton btnSearch = new JButton("Search Roll No");
        btnSearch.setFont(new Font("Arial", Font.BOLD, 12));
        btnSearch.setBackground(new Color(37, 99, 235));
        btnSearch.setForeground(Color.WHITE);
        btnSearch.setFocusPainted(false);

        JButton btnClear = new JButton("Clear / Show All");
        btnClear.setFont(new Font("Arial", Font.BOLD, 12));
        btnClear.setBackground(new Color(100, 116, 139));
        btnClear.setForeground(Color.WHITE);
        btnClear.setFocusPainted(false);

        JLabel lblFilter = new JLabel("Filter Bus:");
        lblFilter.setFont(new Font("Arial", Font.BOLD, 12));
        lblFilter.setForeground(new Color(30, 41, 59));

        cmbFilterBus = new JComboBox<>(new String[]{"All Buses", "B01 (Mini Bus)", "B02 (Standard Bus)", "B03 (Standard Bus)"});
        cmbFilterBus.setFont(new Font("Arial", Font.PLAIN, 12));
        cmbFilterBus.setBackground(Color.WHITE);

        searchBarPanel.add(lblSearch);
        searchBarPanel.add(txtSearchRoll);
        searchBarPanel.add(btnSearch);
        searchBarPanel.add(btnClear);
        searchBarPanel.add(new JSeparator(SwingConstants.VERTICAL));
        searchBarPanel.add(lblFilter);
        searchBarPanel.add(cmbFilterBus);

        // Search Action Listeners
        btnSearch.addActionListener(e -> filterStudents(txtSearchRoll.getText().trim(), (String) cmbFilterBus.getSelectedItem()));
        txtSearchRoll.addActionListener(e -> filterStudents(txtSearchRoll.getText().trim(), (String) cmbFilterBus.getSelectedItem()));
        btnClear.addActionListener(e -> {
            txtSearchRoll.setText("");
            cmbFilterBus.setSelectedIndex(0);
            filterStudents("", "All Buses");
        });
        cmbFilterBus.addActionListener(e -> filterStudents(txtSearchRoll.getText().trim(), (String) cmbFilterBus.getSelectedItem()));

        // Center Output Console
        output = new JTextArea();
        output.setFont(new Font("Monospaced", Font.PLAIN, 13));
        output.setEditable(false);
        output.setBackground(Color.WHITE);
        output.setBorder(new EmptyBorder(12, 12, 12, 12));

        JScrollPane scroll = new JScrollPane(output);
        scroll.setBorder(new LineBorder(new Color(226, 232, 240), 1, true));

        centerPanel.add(searchBarPanel, BorderLayout.NORTH);
        centerPanel.add(scroll, BorderLayout.CENTER);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // ----------------- BOTTOM ACTION BUTTONS ----------------- //
        // ATTENDANCE MODULE IS REMOVED; STUDENT REGISTRATION IS ADDED
        JPanel buttons = new JPanel(new GridLayout(2, 4, 10, 10));
        buttons.setOpaque(false);

        JButton btnDashboard = createStyledButton("Dashboard", new Color(37, 99, 235));
        JButton btnStudents = createStyledButton("Students Directory", new Color(15, 23, 42));
        JButton btnRegister = createStyledButton("Student Registration", new Color(16, 185, 129));
        JButton btnRoute = createStyledButton("Plan Routes (Dijkstra)", new Color(217, 119, 6));
        JButton btnBreakdown = createStyledButton("Breakdown Protocol", new Color(225, 29, 72));
        JButton btnFee = createStyledButton("Fee Audit Report", new Color(5, 150, 105));
        JButton btnUnallocated = createStyledButton("Unallocated Passengers", new Color(124, 58, 237));
        JButton btnExit = createStyledButton("Exit", new Color(100, 116, 139));

        buttons.add(btnDashboard);
        buttons.add(btnStudents);
        buttons.add(btnRegister);
        buttons.add(btnRoute);
        buttons.add(btnBreakdown);
        buttons.add(btnFee);
        buttons.add(btnUnallocated);
        buttons.add(btnExit);

        mainPanel.add(buttons, BorderLayout.SOUTH);

        // Action Listeners
        btnDashboard.addActionListener(e -> showDashboard());
        btnStudents.addActionListener(e -> filterStudents(txtSearchRoll.getText().trim(), (String) cmbFilterBus.getSelectedItem()));
        btnRegister.addActionListener(e -> openStudentRegistrationDialog());
        btnRoute.addActionListener(e -> planRoutes());
        btnBreakdown.addActionListener(e -> breakdown());
        btnFee.addActionListener(e -> showFees());
        btnUnallocated.addActionListener(e -> showUnallocated());
        btnExit.addActionListener(e -> System.exit(0));

        add(mainPanel);

        // Show dashboard on startup
        showDashboard();
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Arial", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(10, 12, 10, 12));
        return btn;
    }

    private void refreshSystemFromDB() {
        TransportSystem freshSystem = new TransportSystem();
        DatabaseManager.loadSystemFromDatabase(freshSystem);
        this.system = freshSystem;
        this.planner = new TransportPlanner(freshSystem, new NearestStopPolicy());
    }

    private void setSearchBarVisible(boolean visible) {
        if (searchBarPanel != null) {
            searchBarPanel.setVisible(visible);
            if (centerPanel != null) {
                centerPanel.revalidate();
                centerPanel.repaint();
            }
        }
    }

    private void showDashboard() {
        setSearchBarVisible(false);
        refreshSystemFromDB();

        int present = 0;
        int authorized = 0;
        int boarded = 0;

        for (Student s : system.getStudents().values()) {
            if (s.isPresent()) present++;
            if (s.isAuthorized()) authorized++;
            if (s.isBoarded()) boarded++;
        }

        int capacity = 0;
        for (CollegeBus bus : system.getBuses().values()) {
            capacity += bus.getCapacity();
        }

        output.setText("");
        output.append("========================================================================================\n");
        output.append("                       KALVI COLLEGE TRANSPORT MANAGEMENT - DASHBOARD                   \n");
        output.append("========================================================================================\n\n");
        output.append(String.format("  Total Registered Students : %d\n", system.getStudents().size()));
        output.append(String.format("  Present Students Today    : %d / %d (Self-marked by students)\n", present, system.getStudents().size()));
        output.append(String.format("  Fee Authorized Students   : %d (Paid online via Student Portal)\n", authorized));
        output.append(String.format("  Currently Boarded Seats   : %d / %d total bus seats\n", boarded, capacity));
        output.append(String.format("  Total College Bus Fleet   : %d Active Buses (Mini Bus & Standard Bus)\n", system.getBuses().size()));
        output.append(String.format("  Total Stops (Erode Region): %d Unique Stops (10 separate stops per bus)\n\n", system.getStops().size()));

        output.append("----------------------------------------------------------------------------------------\n");
        output.append("   FLEET STATUS OVERVIEW (ONLY MINI BUS AND STANDARD BUS)                               \n");
        output.append("----------------------------------------------------------------------------------------\n");
        for (CollegeBus bus : system.getBuses().values()) {
            String busType = "B01".equalsIgnoreCase(bus.getBusId()) ? "Mini Bus" : "Standard Bus";
            output.append(String.format("• Bus %s (%-12s, %d seats) : Driver: %-15s | Boarded: %d / %d | Status: ACTIVE\n",
                    bus.getBusId(),
                    busType,
                    bus.getCapacity(),
                    bus.getBusId().equals("B01") ? "R. Murugesan" : (bus.getBusId().equals("B02") ? "K. Selvam" : "S. Arumugam"),
                    bus.getBoardedStudents().size(),
                    bus.getCapacity()));
        }
        output.append("\n");

        output.append("----------------------------------------------------------------------------------------\n");
        output.append("   ROAD NETWORK ARTERIAL ROUTES (30 DISTINCT STOPS NEAR ERODE)                          \n");
        output.append("----------------------------------------------------------------------------------------\n");
        output.append("• Bus B01 (Mini Bus, 12 seats)     : Erode Bus Stand -> Brough Road -> GH Roundana -> Paneerselvam Park\n");
        output.append("                                     -> Karungalpalayam -> Pallipalayam -> Kumarapalayam -> Bhavani Old Bus Stand\n");
        output.append("                                     -> Kalingarayanpalayam -> Chithode Junction (10 stops)\n\n");
        output.append("• Bus B02 (Standard Bus, 15 seats) : Solar Pirivu -> Lakkapuram -> Kollampalayam Bypass -> Railway Station Gate\n");
        output.append("                                     -> Marapalam -> Surampatti Valasu -> Thindal Murugan Temple\n");
        output.append("                                     -> Villarasampatti -> Perundurai Old Bus Stand -> SIPCOT Perundurai Gate (10 stops)\n\n");
        output.append("• Bus B03 (Standard Bus, 15 seats) : Modakkurichi Town -> Elumathur Junction -> Sivagiri Bus Stop\n");
        output.append("                                     -> Kodumudi Road Pirivu -> Muthur Corner -> Chennimalai Bus Stand\n");
        output.append("                                     -> Ingur Industrial Area -> Vijayamangalam Toll -> Uthukuli Road -> Nasiyanur Junction (10 stops)\n\n");
        output.append("• Semester Bus Transport Fee       : ₹2,500.00 / Semester\n");
        output.append("========================================================================================\n");
    }

    // ----------------- STUDENT MODULE WITH PROMINENT ROLL NUMBER SEARCH BAR ----------------- //
    public void filterStudents(String searchRoll, String selectedBus) {
        setSearchBarVisible(true);
        refreshSystemFromDB();
        if (searchRoll == null) searchRoll = "";
        searchRoll = searchRoll.trim().toUpperCase();

        String busFilterId = "";
        if (selectedBus != null && selectedBus.startsWith("B0")) {
            busFilterId = selectedBus.substring(0, 3);
        }

        output.setText("");
        output.append("========================================================================================================================\n");
        output.append("                                           STUDENT TRANSPORTATION DIRECTORY                                             \n");
        output.append("========================================================================================================================\n\n");
        if (!searchRoll.isEmpty()) {
            output.append("Filtering by Roll Number: " + searchRoll + "\n");
        }
        if (!busFilterId.isEmpty()) {
            output.append("Filtering by Bus Fleet  : " + selectedBus + "\n");
        }
        if (!searchRoll.isEmpty() || !busFilterId.isEmpty()) {
            output.append("\n");
        }

        output.append(String.format("%-12s | %-24s | %-28s | %-5s | %-26s | %-9s | %-13s | %-8s\n",
                "ROLL NO", "STUDENT NAME", "DEPARTMENT", "BUS", "ASSIGNED STOP (ERODE)", "PRESENT", "FEE STATUS", "BOARDED"));
        output.append("------------------------------------------------------------------------------------------------------------------------\n");

        int matchCount = 0;
        for (Student s : system.getStudents().values()) {
            boolean matchRoll = searchRoll.isEmpty() || s.getRollNo().toUpperCase().contains(searchRoll) || s.getName().toUpperCase().contains(searchRoll);
            boolean matchBus = busFilterId.isEmpty() || s.getAssignedBusId().equalsIgnoreCase(busFilterId);

            if (matchRoll && matchBus) {
                matchCount++;
                FeeRecord f = system.getFeeRecords().get(s.getStudentId());
                boolean isPaid = f != null && f.isPaid();

                output.append(String.format("%-12s | %-24s | %-28s | %-5s | %-26s | %-9s | %-13s | %-8s\n",
                        s.getRollNo(),
                        s.getName(),
                        s.getDepartment(),
                        s.getAssignedBusId(),
                        s.getStopId(),
                        s.isPresent() ? "Present" : "Absent",
                        isPaid ? "Paid ₹2,500" : "Pending",
                        s.isBoarded() ? s.getAssignedBusId() : "Waiting"
                ));
            }
        }

        if (matchCount == 0) {
            output.append("No student records found matching Roll Number '" + searchRoll + "'.\n");
        } else {
            output.append("------------------------------------------------------------------------------------------------------------------------\n");
            output.append("Total Displayed Students: " + matchCount + "\n");
        }
        output.append("========================================================================================================================\n");
    }

    // ----------------- STUDENT REGISTRATION MODULE (BY ADMIN) ----------------- //
    private void openStudentRegistrationDialog() {
        JDialog regDialog = new JDialog(this, "New Student Transport Registration (Signup)", true);
        regDialog.setSize(540, 540);
        regDialog.setLocationRelativeTo(this);
        regDialog.setLayout(new BorderLayout(15, 15));

        JPanel form = new JPanel(new GridLayout(8, 2, 10, 10));
        form.setBorder(new EmptyBorder(20, 20, 20, 20));

        JTextField txtName = new JTextField();
        String[] depts = {
            "Computer Science (CSE)",
            "Information Technology (IT)",
            "Electronics & Communication (ECE)",
            "Mechanical Engineering (MECH)",
            "AI & Data Science (AIDS)",
            "Civil Engineering (CIVIL)",
            "Electrical & Electronics (EEE)"
        };
        JComboBox<String> cmbDept = new JComboBox<>(depts);

        JTextField txtRollNo = new JTextField("25CSR265");

        String[] buses = {"B01 (Mini Bus)", "B02 (Standard Bus)", "B03 (Standard Bus)"};
        JComboBox<String> cmbBus = new JComboBox<>(buses);

        JComboBox<String> cmbStop = new JComboBox<>(DatabaseManager.B01_STOPS);

        JLabel lblPwdPreview = new JLabel("25CSR265@B01");
        lblPwdPreview.setFont(new Font("Arial", Font.BOLD, 13));
        lblPwdPreview.setForeground(new Color(16, 185, 129));

        Runnable updatePassword = () -> {
            String roll = txtRollNo.getText().trim().toUpperCase();
            if (roll.isEmpty()) roll = "25CSR265";
            String busFull = (String) cmbBus.getSelectedItem();
            String bId = (busFull != null && busFull.length() >= 3) ? busFull.substring(0, 3) : "B01";
            lblPwdPreview.setText(roll + "@" + bId);
        };

        // Update stops dynamically when bus changes (10 separate stops per bus)
        cmbBus.addActionListener(e -> {
            int sel = cmbBus.getSelectedIndex();
            cmbStop.removeAllItems();
            String[] targetStops = (sel == 0) ? DatabaseManager.B01_STOPS :
                                  ((sel == 1) ? DatabaseManager.B02_STOPS : DatabaseManager.B03_STOPS);
            for (String s : targetStops) cmbStop.addItem(s);
            updatePassword.run();
        });

        // Auto update roll prefix when department changes
        cmbDept.addActionListener(e -> {
            int idx = cmbDept.getSelectedIndex();
            String prefix = (idx == 0) ? "25CSR" :
                           ((idx == 1) ? "25ITR" :
                           ((idx == 2) ? "25ECR" :
                           ((idx == 3) ? "25MER" :
                           ((idx == 4) ? "25ADR" :
                           ((idx == 5) ? "25CER" : "25EER")))));
            txtRollNo.setText(prefix + (260 + (int)(Math.random() * 30)));
            updatePassword.run();
        });

        txtRollNo.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent e) {
                updatePassword.run();
            }
        });

        JComboBox<String> cmbFeeStatus = new JComboBox<>(new String[]{"Unpaid (Student pays online)", "Paid (Immediate pass clearance)"});
        JTextField txtFee = new JTextField("2500.00");
        txtFee.setEditable(false);

        form.add(new JLabel("Full Name:")); form.add(txtName);
        form.add(new JLabel("Department:")); form.add(cmbDept);
        form.add(new JLabel("Roll Number (User ID):")); form.add(txtRollNo);
        form.add(new JLabel("Assigned Bus:")); form.add(cmbBus);
        form.add(new JLabel("Pickup Stop (Erode):")); form.add(cmbStop);
        form.add(new JLabel("Semester Fee (₹):")); form.add(txtFee);
        form.add(new JLabel("Initial Fee Status:")); form.add(cmbFeeStatus);
        form.add(new JLabel("Generated Password (Roll@Bus):")); form.add(lblPwdPreview);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton btnSubmit = new JButton("Register & Create Signup");
        btnSubmit.setFont(new Font("Arial", Font.BOLD, 12));
        btnSubmit.setBackground(new Color(16, 185, 129));
        btnSubmit.setForeground(Color.WHITE);
        btnSubmit.setFocusPainted(false);

        btnSubmit.addActionListener(e -> {
            String name = txtName.getText().trim();
            String roll = txtRollNo.getText().trim().toUpperCase();
            String dept = (String) cmbDept.getSelectedItem();
            String busFull = (String) cmbBus.getSelectedItem();
            String busId = busFull.substring(0, 3);
            String stop = (String) cmbStop.getSelectedItem();
            boolean isPaid = cmbFeeStatus.getSelectedIndex() == 1;

            if (name.isEmpty() || roll.isEmpty()) {
                JOptionPane.showMessageDialog(regDialog, "Please enter both Student Name and Roll Number.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String password = roll + "@" + busId;
            Student newStudent = new Student(roll, name, stop, dept, password, busId);
            newStudent.setPresent(true);
            newStudent.setAuthorized(isPaid);

            boolean success = DatabaseManager.registerStudent(newStudent, 2500.0);
            if (success) {
                if (isPaid) {
                    DatabaseManager.recordStudentPayment(roll, "Admin Registered (Prepaid)", "TXN-ADMIN-" + roll, "RCPT-2026-" + roll, 2500.0);
                }
                system.addStudent(newStudent);
                PickupStop pStop = system.getStops().get(stop);
                if (pStop != null) pStop.addStudent(newStudent);

                JOptionPane.showMessageDialog(regDialog,
                        "Student Signup Successfully Created!\n\n" +
                        "• Roll Number (User ID): " + roll + "\n" +
                        "• Full Name            : " + name + "\n" +
                        "• Department           : " + dept + "\n" +
                        "• Assigned Bus         : " + busId + " (" + ("B01".equals(busId) ? "Mini Bus" : "Standard Bus") + ")\n" +
                        "• Assigned Stop        : " + stop + "\n" +
                        "• Student Login Pwd    : " + password + "\n\n" +
                        "The student can now log in using Roll No: " + roll + " and Password: " + password,
                        "Registration Complete", JOptionPane.INFORMATION_MESSAGE);
                regDialog.dispose();
                showDashboard();
            } else {
                JOptionPane.showMessageDialog(regDialog, "Failed to register student.", "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> regDialog.dispose());

        btnPanel.add(btnCancel);
        btnPanel.add(btnSubmit);

        regDialog.add(form, BorderLayout.CENTER);
        regDialog.add(btnPanel, BorderLayout.SOUTH);
        regDialog.setVisible(true);
    }

    private void planRoutes() {
        setSearchBarVisible(false);
        refreshSystemFromDB();

        output.setText("");
        output.append("========================================================================================\n");
        output.append("               DIJKSTRA ROUTE PLANNING & CAPACITY PASSENGER ALLOCATION                 \n");
        output.append("========================================================================================\n");

        Object[] directionOptions = {"Morning: first pickup stop -> Campus", "Evening: Campus -> first pickup stop"};
        int directionChoice = JOptionPane.showOptionDialog(this,
                "Select the trip direction for this route calculation:",
                "Select Morning or Evening Route", JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, directionOptions, directionOptions[0]);
        if (directionChoice == JOptionPane.CLOSED_OPTION) return;
        boolean morningMode = directionChoice == 0;
        planner.setMorningMode(morningMode);

        planner.prepareWaitingStudents();
        output.append("\nROUTE RULE: Present + fee paid = pickup. Absent = skip pickup and recalculate the shortest path with Dijkstra.\n");
        output.append("DIRECTION: " + (morningMode ? "MORNING — first eligible pickup stop to CAMPUS" : "EVENING — CAMPUS to stops, finishing at first pickup stop") + "\n");
        output.append("Registered students loaded from database: " + system.getStudents().size() + " | Fee-paid records: ");
        int paidCount = 0;
        for (FeeRecord fee : system.getFeeRecords().values()) if (fee.isPaid()) paidCount++;
        output.append(paidCount + "\n");

        // Deterministic order: plan B01 and B02 first so B03 can take overflow
        // passengers only after their original bus capacity has been filled.
        java.util.List<String> busOrder = java.util.Arrays.asList("B01", "B02", "B03");
        for (String busId : busOrder) {
            CollegeBus bus = system.getBuses().get(busId);
            if (bus == null) continue;
            String report = planner.planTrip(bus);
            output.append(report);
            output.append("----------------------------------------------------------------------------------------\n");
        }

        // Sync boarding assignments to database
        DatabaseManager.syncBoardingAssignments(system);

        output.append("\n===== UNALLOCATED PASSENGERS (Present & Fee-Authorized but bus capacity full) =====\n");
        boolean found = false;
        for (Student student : system.getStudents().values()) {
            FeeRecord fee = system.getFeeRecords().get(student.getStudentId());
            if (student.isPresent() && fee != null && fee.isPaid() && !student.isBoarded()) {
                output.append(String.format("• %-10s - %-24s - Stop: %s (Capacity limit reached)\n",
                        student.getRollNo(), student.getName(), student.getStopId()));
                found = true;
            }
        }
        if (!found) {
            output.append("All present and fee-authorized students were successfully allocated bus seats!\n");
        }
        output.append("========================================================================================\n");
    }

    private void breakdown() {
        setSearchBarVisible(false);
        refreshSystemFromDB();

        String busId = JOptionPane.showInputDialog(this, "Enter breakdown Bus ID (B01, B02, or B03):");
        if (busId == null || busId.trim().isEmpty()) return;

        busId = busId.trim().toUpperCase();
        CollegeBus brokenBus = system.getBuses().get(busId);

        if (brokenBus == null) {
            JOptionPane.showMessageDialog(this, "Bus ID '" + busId + "' not found.", "Bus Not Found", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String brokenType = "B01".equalsIgnoreCase(busId) ? "Mini Bus" : "Standard Bus";

        output.setText("");
        output.append("========================================================================================\n");
        output.append("                             FLEET BREAKDOWN PROTOCOL ACTIVATED                         \n");
        output.append("========================================================================================\n\n");
        output.append("Bus " + busId + " (" + brokenType + ") has reported a breakdown.\n\n");
        output.append("• Already boarded students (" + brokenBus.getBoardedStudents().size() + " passengers) are safely preserved on vehicle.\n");
        output.append("• Waiting students on Erode route stops are reassigned to available replacement buses.\n\n");
        output.append("Available Replacement Fleet Options:\n");

        boolean hasReplacement = false;
        for (CollegeBus bus : system.getBuses().values()) {
            if (!bus.getBusId().equalsIgnoreCase(busId)) {
                String repType = "B01".equalsIgnoreCase(bus.getBusId()) ? "Mini Bus" : "Standard Bus";
                if (!bus.isFull()) {
                    output.append(String.format("✓ Replacement Bus: %s (%s) - Available Seats: %d / %d\n",
                            bus.getBusId(), repType, bus.getAvailableSeats(), bus.getCapacity()));
                    hasReplacement = true;
                }
            }
        }
        if (!hasReplacement) {
            output.append("! WARNING: No replacement buses currently have spare seats. Standby bus required.\n");
        }
        output.append("========================================================================================\n");
    }

    private void showFees() {
        setSearchBarVisible(false);
        refreshSystemFromDB();

        output.setText("");
        output.append("========================================================================================\n");
        output.append("                 COLLEGE TRANSPORT FEE AUDIT REPORT (READ-ONLY FOR ADMIN)                \n");
        output.append("========================================================================================\n");
        output.append(" POLICY: Fees must be paid directly by students via Student Login.\n");
        output.append("         Admins have read-only audit access and CANNOT alter student payment records.\n");
        output.append("----------------------------------------------------------------------------------------\n\n");
        output.append(String.format("%-12s | %-10s | %-14s | %-24s | %-20s\n",
                "ROLL NO", "AMOUNT", "STATUS", "TRANSACTION ID", "PAYMENT METHOD"));
        output.append("----------------------------------------------------------------------------------------\n");

        int paid = 0;
        double total = 0;

        for (FeeRecord record : system.getFeeRecords().values()) {
            if (record.isPaid()) {
                paid++;
                total += record.getAmount();
            }
            output.append(String.format("%-12s | ₹%-9.2f | %-14s | %-24s | %-20s\n",
                    record.getStudentId(),
                    record.getAmount(),
                    record.isPaid() ? "PAID & CLEAR" : "PENDING",
                    record.getTransactionId() != null ? record.getTransactionId() : "--",
                    record.getPaymentMethod() != null ? record.getPaymentMethod() : "Unpaid"
            ));
        }

        output.append("----------------------------------------------------------------------------------------\n");
        output.append(String.format("SUMMARY: %d Paid (₹%,.2f Collected)  |  %d Pending (₹%,.2f Outstanding)\n",
                paid, total, (system.getFeeRecords().size() - paid), ((system.getFeeRecords().size() - paid) * 2500.0)));
        output.append("========================================================================================\n");
    }

    private void showUnallocated() {
        setSearchBarVisible(false);
        refreshSystemFromDB();

        output.setText("");
        output.append("========================================================================================\n");
        output.append("                     UNALLOCATED PASSENGERS (CAPACITY EXCEEDED)                         \n");
        output.append("========================================================================================\n\n");

        boolean found = false;
        for (Student student : system.getStudents().values()) {
            if (student.isPresent() && student.isAuthorized() && !student.isBoarded()) {
                output.append(String.format("• %-12s | %-24s | Stop: %-26s | Reason: Bus Capacity Full\n",
                        student.getRollNo(), student.getName(), student.getStopId()));
                found = true;
            }
        }

        if (!found) {
            output.append("All present and fee-authorized students currently have confirmed bus seat allocations.\n");
        }
        output.append("========================================================================================\n");
    }

    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to log out of Admin Dashboard?", "Admin Logout", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            dispose();
            if (parentLoginFrame != null) {
                parentLoginFrame.setVisible(true);
            }
        }
    }
}