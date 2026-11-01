import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public class LoginFrame extends JFrame {

    private JTextField txtStudentRollNo;
    private JPasswordField txtStudentPwd;

    private JTextField txtAdminUser;
    private JPasswordField txtAdminPwd;

    private TransportSystem system;
    private TransportPlanner planner;

    public LoginFrame(TransportSystem system, TransportPlanner planner) {
        this.system = system;
        this.planner = planner;

        setTitle("Kalvi College Transport Management System - Login");
        setSize(580, 560);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        initUI();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(15, 15));
        root.setBackground(new Color(248, 250, 252));
        root.setBorder(new EmptyBorder(18, 24, 18, 24));

        // ----------------- HEADER BANNER ----------------- //
        JPanel header = new JPanel(new GridLayout(2, 1, 4, 4));
        header.setBackground(new Color(15, 23, 42)); // Deep Navy
        header.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(30, 41, 59), 1, true),
                new EmptyBorder(16, 20, 16, 20)
        ));

        JLabel lblTitle = new JLabel("KALVI COLLEGE TRANSPORT SYSTEM", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 17));
        lblTitle.setForeground(new Color(245, 158, 11)); // Amber

        JLabel lblSubtitle = new JLabel("Erode District Fleet Routes & Student Transport Clearance", SwingConstants.CENTER);
        lblSubtitle.setFont(new Font("Arial", Font.PLAIN, 12));
        lblSubtitle.setForeground(new Color(226, 232, 240));

        header.add(lblTitle);
        header.add(lblSubtitle);
        root.add(header, BorderLayout.NORTH);

        // ----------------- TABBED PANE (STUDENT vs ADMIN) ----------------- //
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Arial", Font.BOLD, 13));

        // TAB 1: STUDENT LOGIN (USER ID IS THEIR ROLL NO, e.g. 25CSR201)
        JPanel pnlStudent = new JPanel(new BorderLayout(12, 12));
        pnlStudent.setBackground(Color.WHITE);
        pnlStudent.setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel stForm = new JPanel(new GridLayout(6, 1, 6, 6));
        stForm.setOpaque(false);

        JLabel lblStDesc = new JLabel("<html><b>Student Sign In:</b> Enter your Roll Number and password to access your bus pass, mark attendance, and pay fees.</html>");
        lblStDesc.setFont(new Font("Arial", Font.PLAIN, 12));
        lblStDesc.setForeground(new Color(71, 85, 105));

        txtStudentRollNo = new JTextField("25CSR201");
        txtStudentRollNo.setFont(new Font("Arial", Font.BOLD, 13));
        txtStudentRollNo.setBorder(BorderFactory.createTitledBorder("Student Roll Number"));

        txtStudentPwd = new JPasswordField("25CSR201@B01");
        txtStudentPwd.setFont(new Font("Arial", Font.PLAIN, 13));
        txtStudentPwd.setBorder(BorderFactory.createTitledBorder("Password"));

        // Forgot password link for student
        JPanel pnlForgotSt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pnlForgotSt.setOpaque(false);
        JButton btnForgotStudent = new JButton("Forgot Password?");
        btnForgotStudent.setFont(new Font("Arial", Font.PLAIN, 11));
        btnForgotStudent.setForeground(new Color(37, 99, 235));
        btnForgotStudent.setBorderPainted(false);
        btnForgotStudent.setContentAreaFilled(false);
        btnForgotStudent.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnForgotStudent.addActionListener(e -> forgotStudentPassword());
        pnlForgotSt.add(btnForgotStudent);

        JButton btnLoginStudent = new JButton("Login to Student Portal");
        btnLoginStudent.setFont(new Font("Arial", Font.BOLD, 14));
        btnLoginStudent.setBackground(new Color(16, 185, 129)); // Emerald Green
        btnLoginStudent.setForeground(Color.WHITE);
        btnLoginStudent.setFocusPainted(false);
        btnLoginStudent.addActionListener(e -> loginStudent());

        JLabel lblStHint = new JLabel("Unified Student Login: All students use this same login form.", SwingConstants.CENTER);
        lblStHint.setFont(new Font("Arial", Font.ITALIC, 11));
        lblStHint.setForeground(new Color(148, 163, 184));

        stForm.add(lblStDesc);
        stForm.add(txtStudentRollNo);
        stForm.add(txtStudentPwd);
        stForm.add(pnlForgotSt);
        stForm.add(btnLoginStudent);
        stForm.add(lblStHint);

        pnlStudent.add(stForm, BorderLayout.CENTER);
        tabs.addTab("Student Login", pnlStudent);

        // TAB 2: ADMIN LOGIN
        JPanel pnlAdmin = new JPanel(new BorderLayout(12, 12));
        pnlAdmin.setBackground(Color.WHITE);
        pnlAdmin.setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel adminForm = new JPanel(new GridLayout(6, 1, 6, 6));
        adminForm.setOpaque(false);

        JLabel lblAdminDesc = new JLabel("<html><b>Administrator Access:</b> Fleet routing, student registration, Dijkstra route planning, and read-only fee audit.</html>");
        lblAdminDesc.setFont(new Font("Arial", Font.PLAIN, 12));
        lblAdminDesc.setForeground(new Color(71, 85, 105));

        txtAdminUser = new JTextField("kalvi@transport");
        txtAdminUser.setFont(new Font("Arial", Font.BOLD, 13));
        txtAdminUser.setBorder(BorderFactory.createTitledBorder("Admin User ID"));

        txtAdminPwd = new JPasswordField("kalvi123");
        txtAdminPwd.setFont(new Font("Arial", Font.PLAIN, 13));
        txtAdminPwd.setBorder(BorderFactory.createTitledBorder("Admin Password"));

        // Forgot password link for admin
        JPanel pnlForgotAdmin = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pnlForgotAdmin.setOpaque(false);
        JButton btnForgotAdmin = new JButton("Forgot Password?");
        btnForgotAdmin.setFont(new Font("Arial", Font.PLAIN, 11));
        btnForgotAdmin.setForeground(new Color(37, 99, 235));
        btnForgotAdmin.setBorderPainted(false);
        btnForgotAdmin.setContentAreaFilled(false);
        btnForgotAdmin.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnForgotAdmin.addActionListener(e -> forgotAdminPassword());
        pnlForgotAdmin.add(btnForgotAdmin);

        JButton btnLoginAdmin = new JButton("Login to Admin Dashboard");
        btnLoginAdmin.setFont(new Font("Arial", Font.BOLD, 14));
        btnLoginAdmin.setBackground(new Color(37, 99, 235)); // Blue
        btnLoginAdmin.setForeground(Color.WHITE);
        btnLoginAdmin.setFocusPainted(false);
        btnLoginAdmin.addActionListener(e -> loginAdmin());

        JLabel lblAdminHint = new JLabel("Administrator: kalvi@transport / kalvi123", SwingConstants.CENTER);
        lblAdminHint.setFont(new Font("Arial", Font.ITALIC, 11));
        lblAdminHint.setForeground(new Color(148, 163, 184));

        adminForm.add(lblAdminDesc);
        adminForm.add(txtAdminUser);
        adminForm.add(txtAdminPwd);
        adminForm.add(pnlForgotAdmin);
        adminForm.add(btnLoginAdmin);
        adminForm.add(lblAdminHint);

        pnlAdmin.add(adminForm, BorderLayout.CENTER);
        tabs.addTab("Admin Dashboard", pnlAdmin);

        root.add(tabs, BorderLayout.CENTER);

        add(root);
    }

    private void loginStudent() {
        String rollNo = txtStudentRollNo.getText().trim();
        String pwd = new String(txtStudentPwd.getPassword()).trim();

        if (rollNo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter your Student Roll Number.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Student student = DatabaseManager.authenticateStudent(rollNo, pwd);
        if (student != null) {
            setVisible(false);
            StudentPortalGUI portal = new StudentPortalGUI(student, this);
            portal.setVisible(true);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Invalid Roll Number or Password.\n" +
                    "• User ID: Your Roll Number (e.g. 25CSR201)\n" +
                    "• Password format: RollNo@BusNo (e.g. 25CSR201@B01)\n\n" +
                    "Click 'Forgot Password?' if you need assistance.",
                    "Student Login Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loginAdmin() {
        String user = txtAdminUser.getText().trim();
        String pwd = new String(txtAdminPwd.getPassword()).trim();

        if (DatabaseManager.authenticateAdmin(user, pwd)) {
            setVisible(false);
            TransportGUI adminGUI = new TransportGUI(system, planner, this);
            adminGUI.setVisible(true);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Invalid Admin Credentials.\n" +
                    "• User ID: kalvi@transport\n" +
                    "• Password: kalvi123",
                    "Admin Access Denied", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Forgot password handler for Student
    private void forgotStudentPassword() {
        String roll = JOptionPane.showInputDialog(this,
                "Enter your Student Roll Number (e.g. 25CSR201):",
                "Forgot Password - Student Recovery", JOptionPane.QUESTION_MESSAGE);

        if (roll != null && !roll.trim().isEmpty()) {
            roll = roll.trim().toUpperCase();
            Student s = DatabaseManager.getStudentByRollNo(roll);
            if (s != null) {
                JOptionPane.showMessageDialog(this,
                        "Password Recovery for " + s.getName() + " (" + s.getRollNo() + "):\n\n" +
                        "• Department   : " + s.getDepartment() + "\n" +
                        "• Assigned Bus : " + s.getAssignedBusId() + "\n" +
                        "• Password Rule: <RollNo>@<BusNo>\n" +
                        "• Your Password: " + s.getPassword(),
                        "Password Details", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Roll Number '" + roll + "' was not found in registered transport records.",
                        "Student Not Found", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    // Forgot password handler for Admin
    private void forgotAdminPassword() {
        String code = JOptionPane.showInputDialog(this,
                "Admin Password Recovery:\nEnter institutional verification phrase (e.g. 'kalvi'):",
                "Forgot Password - Admin Recovery", JOptionPane.QUESTION_MESSAGE);

        if (code != null && !code.trim().isEmpty()) {
            if (code.trim().equalsIgnoreCase("kalvi") || code.trim().equalsIgnoreCase("transport")) {
                JOptionPane.showMessageDialog(this,
                        "Administrator Access Verified!\n\n" +
                        "• User ID : kalvi@transport\n" +
                        "• Password: kalvi123",
                        "Admin Credentials Recovered", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Incorrect institutional verification phrase.",
                        "Verification Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
