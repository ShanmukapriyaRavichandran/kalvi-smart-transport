import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class StudentPortalGUI extends JFrame {

    private Student student;
    private FeeRecord feeRecord;
    private LoginFrame parentLoginFrame;

    private JLabel lblAttendanceStatus;
    private JButton btnToggleAttendance;
    private JLabel lblPassStatus;
    private JLabel lblFeeStatus;
    private JLabel lblPaymentDetails;
    private JPanel feeActionPanel;
    private JComboBox<String> cmbPaymentMethod;

    public StudentPortalGUI(Student student, LoginFrame parentLoginFrame) {
        this.student = student;
        this.parentLoginFrame = parentLoginFrame;
        this.feeRecord = DatabaseManager.getFeeRecord(student.getStudentId());

        setTitle("Kalvi College Transport Management - Student Portal (" + student.getRollNo() + ")");
        setSize(940, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(15, 15));
        root.setBackground(new Color(248, 250, 252));
        root.setBorder(new EmptyBorder(15, 15, 15, 15));

        // ----------------- TOP BANNER ----------------- //
        JPanel topBanner = new JPanel(new BorderLayout(10, 10));
        topBanner.setBackground(new Color(15, 23, 42)); // Deep Navy
        topBanner.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(30, 41, 59), 1, true),
                new EmptyBorder(16, 20, 16, 20)
        ));

        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 4, 4));
        titlePanel.setOpaque(false);

        JLabel lblCollege = new JLabel("KALVI COLLEGE TRANSPORT & MANAGEMENT");
        lblCollege.setFont(new Font("Arial", Font.BOLD, 18));
        lblCollege.setForeground(new Color(245, 158, 11)); // Amber

        String busTypeName = "B01".equalsIgnoreCase(student.getAssignedBusId()) ? "Mini Bus" : "Standard Bus";
        JLabel lblStudentTitle = new JLabel("Welcome, " + student.getName() + " | Roll No: " + student.getRollNo() + " | " + student.getDepartment() + " | Bus: " + student.getAssignedBusId() + " (" + busTypeName + ")");
        lblStudentTitle.setFont(new Font("Arial", Font.PLAIN, 12));
        lblStudentTitle.setForeground(new Color(226, 232, 240));

        titlePanel.add(lblCollege);
        titlePanel.add(lblStudentTitle);
        topBanner.add(titlePanel, BorderLayout.CENTER);

        // Logout Button
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
        topBanner.add(btnLogout, BorderLayout.EAST);

        root.add(topBanner, BorderLayout.NORTH);

        // ----------------- MAIN CONTENT (GRID OF CARDS) ----------------- //
        JPanel contentGrid = new JPanel(new GridLayout(2, 2, 15, 15));
        contentGrid.setOpaque(false);

        // CARD 1: ATTENDANCE SELF-MARKING
        JPanel cardAttendance = createCardPanel("DAILY TRANSPORT ATTENDANCE");
        JPanel attBody = new JPanel(new GridLayout(3, 1, 8, 8));
        attBody.setOpaque(false);

        JLabel lblAttDesc = new JLabel("<html>Mark your transport attendance for today's college pickup.<br>This status is live-synchronized with the College Admin Dashboard.</html>");
        lblAttDesc.setFont(new Font("Arial", Font.PLAIN, 12));
        lblAttDesc.setForeground(new Color(100, 116, 139));

        lblAttendanceStatus = new JLabel();
        lblAttendanceStatus.setFont(new Font("Arial", Font.BOLD, 15));
        updateAttendanceLabel();

        btnToggleAttendance = new JButton();
        btnToggleAttendance.setFont(new Font("Arial", Font.BOLD, 12));
        btnToggleAttendance.setFocusPainted(false);
        btnToggleAttendance.setBackground(new Color(16, 185, 129));
        btnToggleAttendance.setForeground(Color.WHITE);
        updateAttendanceButton();
        btnToggleAttendance.addActionListener(e -> toggleAttendance());

        attBody.add(lblAttDesc);
        attBody.add(lblAttendanceStatus);
        attBody.add(btnToggleAttendance);
        cardAttendance.add(attBody, BorderLayout.CENTER);

        // CARD 2: DIGITAL TRANSPORT PASS & BUS ASSIGNMENT
        JPanel cardPass = createCardPanel("DIGITAL TRANSPORT PASS STATUS");
        JPanel passBody = new JPanel(new GridLayout(4, 1, 6, 6));
        passBody.setOpaque(false);

        lblPassStatus = new JLabel();
        lblPassStatus.setFont(new Font("Arial", Font.BOLD, 14));
        updatePassStatusLabel();

        JLabel lblBusAssigned = new JLabel("Assigned Fleet: " + student.getAssignedBusId() + " (" + busTypeName + ")");
        lblBusAssigned.setFont(new Font("Arial", Font.PLAIN, 13));
        lblBusAssigned.setForeground(new Color(51, 65, 85));

        JLabel lblPickupInfo = new JLabel("Assigned Pickup Stop: " + student.getStopId());
        lblPickupInfo.setFont(new Font("Arial", Font.BOLD, 13));
        lblPickupInfo.setForeground(new Color(30, 41, 59));

        JLabel lblBoardingStatus = new JLabel("Boarding Status: " + (student.isBoarded() ? "Boarded on " + student.getAssignedBusId() : "Waiting at Pickup Point"));
        lblBoardingStatus.setFont(new Font("Arial", Font.PLAIN, 12));
        lblBoardingStatus.setForeground(new Color(100, 116, 139));

        passBody.add(lblPassStatus);
        passBody.add(lblBusAssigned);
        passBody.add(lblPickupInfo);
        passBody.add(lblBoardingStatus);
        cardPass.add(passBody, BorderLayout.CENTER);

        // CARD 3: FEE PAYMENT (PAY FEE IN STUDENT LOGIN)
        JPanel cardFee = createCardPanel("SEMESTER TRANSPORT FEE PAYMENT");
        JPanel feeBody = new JPanel(new BorderLayout(8, 8));
        feeBody.setOpaque(false);

        JPanel feeInfoPanel = new JPanel(new GridLayout(3, 1, 4, 4));
        feeInfoPanel.setOpaque(false);

        JLabel lblAmount = new JLabel("Semester Fee Amount: ₹2,500.00");
        lblAmount.setFont(new Font("Arial", Font.BOLD, 14));
        lblAmount.setForeground(new Color(15, 23, 42));

        lblFeeStatus = new JLabel();
        lblFeeStatus.setFont(new Font("Arial", Font.BOLD, 13));

        lblPaymentDetails = new JLabel();
        lblPaymentDetails.setFont(new Font("Arial", Font.PLAIN, 11));
        lblPaymentDetails.setForeground(new Color(100, 116, 139));

        feeInfoPanel.add(lblAmount);
        feeInfoPanel.add(lblFeeStatus);
        feeInfoPanel.add(lblPaymentDetails);
        feeBody.add(feeInfoPanel, BorderLayout.NORTH);

        feeActionPanel = new JPanel(new BorderLayout(6, 6));
        feeActionPanel.setOpaque(false);
        renderFeeActionUI();
        feeBody.add(feeActionPanel, BorderLayout.CENTER);

        cardFee.add(feeBody, BorderLayout.CENTER);

        // CARD 4: PICKUP STOP & SCHEDULE (ERODE DISTRICT)
        JPanel cardStop = createCardPanel("PICKUP STOP & SCHEDULE (ERODE)");
        JPanel stopBody = new JPanel(new GridLayout(4, 1, 6, 6));
        stopBody.setOpaque(false);

        JLabel lblStopTitle = new JLabel("Pickup Location: " + student.getStopId());
        lblStopTitle.setFont(new Font("Arial", Font.BOLD, 13));
        lblStopTitle.setForeground(new Color(30, 41, 59));

        JLabel lblMorningSlot = new JLabel("Morning Pickup Time: 07:45 AM - 08:00 AM");
        lblMorningSlot.setFont(new Font("Arial", Font.PLAIN, 12));
        lblMorningSlot.setForeground(new Color(16, 185, 129));

        String driverName = "B01".equalsIgnoreCase(student.getAssignedBusId()) ? "R. Murugesan (+91 98421 11201)" :
                           ("B02".equalsIgnoreCase(student.getAssignedBusId()) ? "K. Selvam (+91 98421 11202)" : "S. Arumugam (+91 98421 11203)");
        JLabel lblDriver = new JLabel("Driver Contact: " + driverName);
        lblDriver.setFont(new Font("Arial", Font.PLAIN, 12));
        lblDriver.setForeground(new Color(71, 85, 105));

        JLabel lblHelpdesk = new JLabel("Transport Helpdesk: +91 424 2980100 / Erode Campus");
        lblHelpdesk.setFont(new Font("Arial", Font.PLAIN, 11));
        lblHelpdesk.setForeground(new Color(100, 116, 139));

        stopBody.add(lblStopTitle);
        stopBody.add(lblMorningSlot);
        stopBody.add(lblDriver);
        stopBody.add(lblHelpdesk);
        cardStop.add(stopBody, BorderLayout.CENTER);

        contentGrid.add(cardAttendance);
        contentGrid.add(cardPass);
        contentGrid.add(cardFee);
        contentGrid.add(cardStop);

        root.add(contentGrid, BorderLayout.CENTER);

        add(root);
        refreshFeeStatusView();
    }

    private JPanel createCardPanel(String title) {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1, true),
                new EmptyBorder(14, 16, 14, 16)
        ));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 12));
        lblTitle.setForeground(new Color(71, 85, 105));
        card.add(lblTitle, BorderLayout.NORTH);

        return card;
    }

    private void toggleAttendance() {
        boolean newStatus = !student.isPresent();
        boolean success = DatabaseManager.updateStudentAttendance(student.getStudentId(), newStatus);
        if (success) {
            student.setPresent(newStatus);
            updateAttendanceLabel();
            updateAttendanceButton();
            JOptionPane.showMessageDialog(this,
                    "Attendance successfully updated to: " + (newStatus ? "PRESENT" : "ABSENT") +
                    "\nThis status is now live on the Admin Dashboard.",
                    "Attendance Updated", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Failed to update attendance in database.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateAttendanceLabel() {
        if (student.isPresent()) {
            lblAttendanceStatus.setText("Status: PRESENT (Eligible for Pickup)");
            lblAttendanceStatus.setForeground(new Color(16, 185, 129));
        } else {
            lblAttendanceStatus.setText("Status: ABSENT (Not Travelling Today)");
            lblAttendanceStatus.setForeground(new Color(239, 68, 68));
        }
    }

    private void updateAttendanceButton() {
        if (student.isPresent()) {
            btnToggleAttendance.setText("Mark as ABSENT for Today");
            btnToggleAttendance.setBackground(new Color(239, 68, 68));
        } else {
            btnToggleAttendance.setText("Mark as PRESENT for Pickup");
            btnToggleAttendance.setBackground(new Color(16, 185, 129));
        }
    }

    private void updatePassStatusLabel() {
        if (student.isAuthorized()) {
            lblPassStatus.setText("Transport Pass: AUTHORIZED (Cleared)");
            lblPassStatus.setForeground(new Color(16, 185, 129));
        } else {
            lblPassStatus.setText("Transport Pass: UNAUTHORIZED (Fee Due)");
            lblPassStatus.setForeground(new Color(239, 68, 68));
        }
    }

    private void renderFeeActionUI() {
        feeActionPanel.removeAll();

        if (feeRecord.isPaid()) {
            JButton btnReceipt = new JButton("View & Print Official E-Receipt");
            btnReceipt.setFont(new Font("Arial", Font.BOLD, 12));
            btnReceipt.setBackground(new Color(15, 23, 42));
            btnReceipt.setForeground(Color.WHITE);
            btnReceipt.setFocusPainted(false);
            btnReceipt.addActionListener(e -> showReceiptDialog());
            feeActionPanel.add(btnReceipt, BorderLayout.CENTER);
        } else {
            JPanel payOptionsBox = new JPanel(new GridLayout(2, 1, 6, 6));
            payOptionsBox.setOpaque(false);

            String[] paymentOptions = {
                "UPI (Google Pay / PhonePe / Paytm / QR Scanner)",
                "Debit / Credit Card (Visa / MasterCard / RuPay)",
                "Internet Banking (SBI / HDFC / ICICI / Canara Bank)"
            };
            cmbPaymentMethod = new JComboBox<>(paymentOptions);
            cmbPaymentMethod.setFont(new Font("Arial", Font.PLAIN, 12));

            JButton btnPayNow = new JButton("Pay Transport Fee Now (₹2,500.00)");
            btnPayNow.setFont(new Font("Arial", Font.BOLD, 13));
            btnPayNow.setBackground(new Color(37, 99, 235));
            btnPayNow.setForeground(Color.WHITE);
            btnPayNow.setFocusPainted(false);
            btnPayNow.addActionListener(e -> processFeePayment());

            payOptionsBox.add(cmbPaymentMethod);
            payOptionsBox.add(btnPayNow);
            feeActionPanel.add(payOptionsBox, BorderLayout.CENTER);
        }

        feeActionPanel.revalidate();
        feeActionPanel.repaint();
    }

    private void refreshFeeStatusView() {
        if (feeRecord.isPaid()) {
            lblFeeStatus.setText("Fee Status: PAID & VERIFIED");
            lblFeeStatus.setForeground(new Color(16, 185, 129));
            lblPaymentDetails.setText("Mode: " + feeRecord.getPaymentMethod() + " | Txn: " + feeRecord.getTransactionId());
        } else {
            lblFeeStatus.setText("Fee Status: PENDING (Action Required)");
            lblFeeStatus.setForeground(new Color(239, 68, 68));
            lblPaymentDetails.setText("Please choose a payment method below to authorize your pass.");
        }
        updatePassStatusLabel();
        renderFeeActionUI();
    }

    private void processFeePayment() {
        String method = (String) cmbPaymentMethod.getSelectedItem();
        int confirm = JOptionPane.showConfirmDialog(this,
                "Confirm Payment of ₹2,500.00 via " + method + "?",
                "Payment Confirmation", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String txnId = "TXN-KLV-" + timeStamp;
            String rcptNo = "RCPT-2026-" + (int)(Math.random() * 9000 + 1000);

            boolean ok = DatabaseManager.recordStudentPayment(
                    student.getStudentId(), method, txnId, rcptNo, 2500.0);

            if (ok) {
                student.setAuthorized(true);
                feeRecord.setPaid(true);
                feeRecord.setPaymentMethod(method);
                feeRecord.setTransactionId(txnId);
                feeRecord.setReceiptNo(rcptNo);
                feeRecord.setPaymentDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

                refreshFeeStatusView();

                JOptionPane.showMessageDialog(this,
                        "Payment Successful!\nTransaction ID: " + txnId +
                        "\nYour transport pass is now AUTHORIZED for bus boarding.",
                        "Payment Approved", JOptionPane.INFORMATION_MESSAGE);

                showReceiptDialog();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to record payment in database.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showReceiptDialog() {
        JDialog dialog = new JDialog(this, "Official Fee Receipt - Kalvi College Transport", true);
        dialog.setSize(520, 500);
        dialog.setLocationRelativeTo(this);

        JTextArea receiptText = new JTextArea();
        receiptText.setFont(new Font("Monospaced", Font.PLAIN, 12));
        receiptText.setEditable(false);
        receiptText.setBackground(Color.WHITE);
        receiptText.setBorder(new EmptyBorder(15, 15, 15, 15));

        String busTypeName = "B01".equalsIgnoreCase(student.getAssignedBusId()) ? "Mini Bus" : "Standard Bus";

        StringBuilder sb = new StringBuilder();
        sb.append("==================================================\n");
        sb.append("        KALVI COLLEGE OF ENGINEERING & TECH       \n");
        sb.append("         DEPARTMENT OF TRANSPORT MANAGEMENT       \n");
        sb.append("            OFFICIAL ELECTRONIC RECEIPT           \n");
        sb.append("==================================================\n\n");
        sb.append("Receipt Number : ").append(feeRecord.getReceiptNo()).append("\n");
        sb.append("Date & Time    : ").append(feeRecord.getPaymentDate()).append("\n");
        sb.append("Transaction ID : ").append(feeRecord.getTransactionId()).append("\n");
        sb.append("Payment Method : ").append(feeRecord.getPaymentMethod()).append("\n\n");
        sb.append("--------------------------------------------------\n");
        sb.append("Student Roll No: ").append(student.getRollNo()).append("\n");
        sb.append("Student Name   : ").append(student.getName()).append("\n");
        sb.append("Department     : ").append(student.getDepartment()).append("\n");
        sb.append("Assigned Bus   : ").append(student.getAssignedBusId()).append(" (").append(busTypeName).append(")\n");
        sb.append("Assigned Stop  : ").append(student.getStopId()).append(" (Erode District)\n");
        sb.append("--------------------------------------------------\n");
        sb.append("Fee Particulars: Semester Bus Transport Clearance\n");
        sb.append("Amount Paid    : ₹2,500.00\n");
        sb.append("Pass Status    : AUTHORIZED (Valid for 2026-27)\n");
        sb.append("--------------------------------------------------\n\n");
        sb.append("     Verified & Digitally Signed by Logistics    \n");
        sb.append("==================================================\n");

        receiptText.setText(sb.toString());

        JButton btnPrint = new JButton("Print / Close");
        btnPrint.setFont(new Font("Arial", Font.BOLD, 12));
        btnPrint.addActionListener(e -> dialog.dispose());

        JPanel pnl = new JPanel(new BorderLayout());
        pnl.add(new JScrollPane(receiptText), BorderLayout.CENTER);
        pnl.add(btnPrint, BorderLayout.SOUTH);

        dialog.add(pnl);
        dialog.setVisible(true);
    }

    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to log out?", "Logout", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            dispose();
            parentLoginFrame.setVisible(true);
        }
    }
}
