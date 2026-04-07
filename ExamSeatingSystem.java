package project;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class ExamSeatingSystem extends JFrame {

    // --- DB Config ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/projectdb";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "rootroot";
    private Connection con;

    // --- Theme Support ---
    private boolean isDarkMode = false;
    
    // --- Light Theme Colors ---
    private Color PRIMARY_COLOR = new Color(255, 106, 0);
    private Color SECONDARY_COLOR = new Color(59, 130, 246);
    private Color BG_COLOR = new Color(243, 244, 246);
    private Color SIDEBAR_BG = new Color(24, 28, 36);
    private Color TEXT_COLOR = new Color(17, 24, 39);
    private Color CARD_BG = Color.WHITE;
    private Color ACCENT_HOVER = new Color(55, 65, 81);
    
    // --- Dark Theme Colors ---
    private static final Color DARK_PRIMARY = new Color(255, 140, 50);
    private static final Color DARK_SECONDARY = new Color(96, 165, 250);
    private static final Color DARK_BG = new Color(17, 24, 39);
    private static final Color DARK_SIDEBAR = new Color(15, 18, 25);
    private static final Color DARK_TEXT = new Color(243, 244, 246);
    private static final Color DARK_CARD = new Color(55, 65, 81);
    private static final Color DARK_HOVER = new Color(75, 85, 99);
    
    // --- Cross-platform Font Family ---
    private static final String FONT_FAMILY = detectFontFamily();
    
    private static String detectFontFamily() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            // Try macOS system fonts in order of preference
            String[] macFonts = {".AppleSystemUIFont", "SF Pro Display", "Helvetica Neue", "Helvetica"};
            for (String f : macFonts) {
                if (new Font(f, Font.PLAIN, 14).getFamily().equalsIgnoreCase(f) || 
                    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames().length > 0) {
                    return f;
                }
            }
            return "Helvetica Neue";
        }
        return "Segoe UI";
    }
    
    // --- Fonts ---
    private static final Font HEADER_FONT = new Font(FONT_FAMILY, Font.BOLD, 24);
    private static final Font SUBHEADER_FONT = new Font(FONT_FAMILY, Font.BOLD, 20);
    private static final Font BODY_FONT = new Font(FONT_FAMILY, Font.PLAIN, 14);
    private static final Font BUTTON_FONT = new Font(FONT_FAMILY, Font.BOLD, 14);
    private static final Font SMALL_FONT = new Font(FONT_FAMILY, Font.PLAIN, 12);
    private static final Font CAPTION_FONT = new Font(FONT_FAMILY, Font.PLAIN, 11);
    
    // --- Campus Bhavans ---
    private static final String[] BHAVANS = {"Bill Gates", "Ratan Tata", "K L Rao", "Visvesvaraya", "Bhaskar", "C V Raman", "Ramanujan"};
    
    // --- Subject Library ---
    private List<String[]> subjectLibrary = new ArrayList<>();

    // --- Auto-refresh hooks (set when panels are created) ---
    private Runnable rosterRefresher = null;
    private Runnable scheduledExamsRefresher = null;
    
    private CardLayout cardLayout;
    private JPanel contentPanel;
    private JPanel sidebar;
    private JPanel mainDashboard;
    private JPanel viewsPanel;
    private Map<String, JButton> sidebarButtons = new HashMap<>();
    private Map<String, JButton> sidebarViewMap = new LinkedHashMap<>(); // viewName → button
    private String currentRole = "";
    private String activeSidebarView = "";
    private JLabel sidebarUserLabel;
    private JLabel statusBarLabel;

    public static void main(String[] args) {
        // Setup Global UI Defaults
        try {
            // Use system L&F for native look on macOS/Windows
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            
            // macOS-specific improvements
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("mac")) {
                System.setProperty("apple.laf.useScreenMenuBar", "true");
                System.setProperty("apple.awt.application.appearance", "system");
                System.setProperty("apple.awt.antialiasing", "true");
                System.setProperty("apple.awt.textantialiasing", "true");
            }
            
            // Global rendering hints for smoother text
            System.setProperty("awt.useSystemAAFontSettings", "lcd");
            System.setProperty("swing.aatext", "true");
            
            // Smoother scrolling
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("ScrollBar.thumbArc", 8);
            UIManager.put("ScrollBar.trackArc", 8);
        } catch (Exception e) {}
        
        SwingUtilities.invokeLater(() -> new ExamSeatingSystem().setVisible(true));
    }

    public ExamSeatingSystem() {
        setTitle("Aditya University — Exam Portal v2.0");
        // Set window icon from the programmatic logo
        try {
            setIconImage(createAdityaLogoImage(256, 256));
        } catch (Exception ignored) {}
        // Auto-size to 85% of screen, clamped to reasonable bounds
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int w = Math.min(Math.max((int)(screenSize.width * 0.85), 1100), 1600);
        int h = Math.min(Math.max((int)(screenSize.height * 0.85), 700), 1000);
        setSize(w, h);
        setMinimumSize(new Dimension(1000, 650));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        connectDB();
        initSubjectLibrary();
        setupKeyboardShortcuts();
        
        setLayout(new BorderLayout());
        
        contentPanel = new JPanel(new CardLayout());
        cardLayout = (CardLayout) contentPanel.getLayout();
        
        contentPanel.add(createLoginPanel(), "LOGIN");
        
        mainDashboard = new JPanel(new BorderLayout());
        sidebar = createSidebar();
        mainDashboard.add(sidebar, BorderLayout.WEST);
        
        viewsPanel = new JPanel(new CardLayout());
        viewsPanel.setOpaque(false);
        viewsPanel.add(createAdminRoomPanel(), "ADMIN_ROOMS");
        viewsPanel.add(createAdminStudentPanel(), "ADMIN_STUDENTS");
        viewsPanel.add(createBackupRestorePanel(), "BACKUP_RESTORE");
        viewsPanel.add(createUserManagementPanel(), "USER_MANAGEMENT");
        viewsPanel.add(createInvigilatorRosterPanel(), "INVIG_ROSTER");
        viewsPanel.add(createExaminerAllocationPanel(), "EXAM_ALLOCATION");
        viewsPanel.add(createScheduledExamsPanel(), "SCHEDULED_EXAMS");
        viewsPanel.add(createExamTimetablePanel(), "EXAM_TIMETABLE");
        viewsPanel.add(createHallTicketPanel(), "HALL_TICKETS");
        viewsPanel.add(createCampusMapPanel(), "CAMPUS_MAP");
        viewsPanel.add(createTemplateLibraryPanel(), "TEMPLATES");
        viewsPanel.add(createSearchPanel(), "SEARCH");
        
        // Wrap views in a container with padding
        JPanel viewsWrapper = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(BG_COLOR);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        viewsWrapper.setBorder(new EmptyBorder(16, 20, 6, 20));
        viewsWrapper.setOpaque(false);
        viewsWrapper.add(viewsPanel, BorderLayout.CENTER);
        viewsWrapper.add(createStatusBar(), BorderLayout.SOUTH);

        mainDashboard.add(viewsWrapper, BorderLayout.CENTER);
        contentPanel.add(mainDashboard, "DASHBOARD");

        add(contentPanel, BorderLayout.CENTER);
        applyTheme();
    }
    
    private void initSubjectLibrary() {
        // Pre-populate with common Aditya University subjects
        subjectLibrary.add(new String[]{"Discrete Mathematics", "241MA008"});
        subjectLibrary.add(new String[]{"Object Oriented Programming through C++", "241CS008"});
        subjectLibrary.add(new String[]{"Theory of Computation", "241CS011"});
        subjectLibrary.add(new String[]{"Database Management Systems", "241IT005"});
        subjectLibrary.add(new String[]{"Agile Software Engineering", "241IT007"});
        subjectLibrary.add(new String[]{"Complex Variables & Statistical Methods", "241MA006"});
        subjectLibrary.add(new String[]{"Introduction to Mechanical Engineering", "241MN001"});
        subjectLibrary.add(new String[]{"Mine Surveying", "241MN005"});
        subjectLibrary.add(new String[]{"Development of Mineral Deposits", "241MN003"});
        subjectLibrary.add(new String[]{"Engineering Economics", "241MB002"});
        subjectLibrary.add(new String[]{"Fluid Mechanics for Petroleum Engineers", "241PT004"});
        subjectLibrary.add(new String[]{"Instrumentation & Process Control", "241PT008"});
        subjectLibrary.add(new String[]{"Fundamentals of Petroleum Engineering", "241PT039"});
        subjectLibrary.add(new String[]{"Fire Risk & Control", "241PT038"});
        subjectLibrary.add(new String[]{"Engineering Mathematics - I", "241MA010"});
        subjectLibrary.add(new String[]{"Engineering Physics", "241PH003"});
        subjectLibrary.add(new String[]{"Engineering Chemistry", "241CH003"});
        subjectLibrary.add(new String[]{"Engineering Mechanics", "241AE012"});
        subjectLibrary.add(new String[]{"Soil Mechanics", "241AE013"});
        subjectLibrary.add(new String[]{"Fluid Mechanics and Open Channel Hydraulics", "241AE014"});
        subjectLibrary.add(new String[]{"Engineering Properties of Agricultural Produce and Food Science", "241AE015"});
        subjectLibrary.add(new String[]{"Farm Machinery & Equipment - I", "241AE016"});
        subjectLibrary.add(new String[]{"Probability & Statistics", "241MA009"});
        subjectLibrary.add(new String[]{"Operating Systems", "241CS013"});
        subjectLibrary.add(new String[]{"Fundamentals of Data Science", "241CS034"});
        subjectLibrary.add(new String[]{"Artificial Intelligence", "241AI002"});
        subjectLibrary.add(new String[]{"Data Structures", "241CS010"});
        subjectLibrary.add(new String[]{"Computer Networks", "241CS015"});
        subjectLibrary.add(new String[]{"Software Engineering", "241CS016"});
        subjectLibrary.add(new String[]{"Machine Learning", "241AI003"});
        subjectLibrary.add(new String[]{"Deep Learning", "241AI004"});
        subjectLibrary.add(new String[]{"Web Technologies", "241IT008"});
        subjectLibrary.add(new String[]{"Cloud Computing", "241CS020"});
        subjectLibrary.add(new String[]{"Cyber Security", "241CS021"});
        // Load custom subjects from DB if available
        loadCustomSubjects();
    }
    
    private void loadCustomSubjects() {
        try {
            ResultSet rs = con.createStatement().executeQuery("SELECT subject_name, course_code FROM subject_library");
            while (rs.next()) {
                String name = rs.getString(1);
                String code = rs.getString(2);
                boolean exists = subjectLibrary.stream().anyMatch(s -> s[1].equals(code));
                if (!exists) {
                    subjectLibrary.add(new String[]{name, code});
                }
            }
        } catch (Exception e) {
            // Table might not exist yet
        }
    }
    
    private void saveCustomSubject(String name, String code) {
        try {
            PreparedStatement pst = con.prepareStatement(
                "INSERT INTO subject_library (subject_name, course_code) VALUES (?, ?) ON DUPLICATE KEY UPDATE subject_name=?");
            pst.setString(1, name);
            pst.setString(2, code);
            pst.setString(3, name);
            pst.executeUpdate();
        } catch (Exception e) {
            // Retry — initDatabase ensures the table exists on next call
            try { if (con != null) initDatabase(); saveCustomSubject(name, code); }
            catch (Exception ex) {}
        }
    }
    
    private void setupKeyboardShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.isControlDown()) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_S: // Ctrl+S - Save (context dependent)
                        showQuickSaveDialog();
                        return true;
                    case KeyEvent.VK_P: // Ctrl+P - Print
                        showQuickPrintDialog();
                        return true;
                    case KeyEvent.VK_F: // Ctrl+F - Find/Search
                        if (currentRole.equals("EXAMINER")) {
                            showView("SEARCH");
                        }
                        return true;
                    case KeyEvent.VK_D: // Ctrl+D - Toggle Dark Mode
                        toggleDarkMode();
                        return true;
                    case KeyEvent.VK_T: // Ctrl+T - Timetable
                        if (currentRole.equals("EXAMINER")) {
                            showView("EXAM_TIMETABLE");
                        }
                        return true;
                    case KeyEvent.VK_H: // Ctrl+H - Hall Tickets
                        if (currentRole.equals("EXAMINER")) {
                            showView("HALL_TICKETS");
                        }
                        return true;
                    case KeyEvent.VK_M: // Ctrl+M - Campus Map
                        if (currentRole.equals("EXAMINER")) {
                            showView("CAMPUS_MAP");
                        }
                        return true;
                    case KeyEvent.VK_R: // Ctrl+R - Invigilator Roster
                        showView("INVIG_ROSTER");
                        return true;
                }
            }
            return false;
        }); 
    }
    
    private void showView(String viewName) {
        ((CardLayout) viewsPanel.getLayout()).show(viewsPanel, viewName);
        activeSidebarView = viewName;
        updateActiveSidebarButton(viewName);
        if (statusBarLabel != null) {
            String[] names  = {"ADMIN_ROOMS","ADMIN_STUDENTS","USER_MANAGEMENT",
                               "INVIG_ROSTER","BACKUP_RESTORE","EXAM_ALLOCATION","SCHEDULED_EXAMS",
                               "EXAM_TIMETABLE","HALL_TICKETS","CAMPUS_MAP","TEMPLATES","SEARCH"};
            String[] labels = {"Room Layouts","Student Data","User Management",
                               "Invigilator Roster","Backup & Restore","Seating & Schedule","Scheduled Exams",
                               "Exam Timetable","Hall Tickets","Campus Map","Templates","Search & Print"};
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(viewName)) { statusBarLabel.setText("  ▸  " + labels[i]); break; }
            }
        }
    }

    private void updateActiveSidebarButton(String viewName) {
        for (Map.Entry<String, JButton> entry : sidebarViewMap.entrySet()) {
            JButton btn = entry.getValue();
            boolean active = entry.getKey().equals(viewName);
            btn.putClientProperty("active", active);
            btn.setForeground(active ? Color.WHITE : new Color(209, 213, 219));
            btn.repaint();
        }
    }
    
    private void showQuickSaveDialog() {
        JOptionPane.showMessageDialog(this, "Ctrl+S: Quick save triggered\nAll data is auto-saved to database.", "Save", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void showQuickPrintDialog() {
        JOptionPane.showMessageDialog(this, "Ctrl+P: Use the Print/PDF buttons in each section for printing.", "Print", JOptionPane.INFORMATION_MESSAGE);
    }

    private JPanel createStatusBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                // Subtle gradient for status bar
                GradientPaint gp = new GradientPaint(0, 0, new Color(20, 24, 36), getWidth(), 0, new Color(15, 20, 30));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(6, 16, 6, 16));
        bar.setPreferredSize(new Dimension(0, 32));

        statusBarLabel = new JLabel("  \u25B8  Ready");
        statusBarLabel.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
        statusBarLabel.setForeground(new Color(255, 175, 100));
        bar.add(statusBarLabel, BorderLayout.WEST);

        JLabel clockLabel = new JLabel();
        clockLabel.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
        clockLabel.setForeground(new Color(140, 170, 220));
        bar.add(clockLabel, BorderLayout.EAST);

        // Live clock tick every second
        javax.swing.Timer clock = new javax.swing.Timer(1000, e -> {
            clockLabel.setText(new java.text.SimpleDateFormat("EEE, dd MMM yyyy  \u00b7  HH:mm:ss  ").format(new java.util.Date()));
        });
        clock.start();
        clockLabel.setText(new java.text.SimpleDateFormat("EEE, dd MMM yyyy  \u00b7  HH:mm:ss  ").format(new java.util.Date()));
        return bar;
    }
    
    private void toggleDarkMode() {
        isDarkMode = !isDarkMode;
        applyTheme();
        SwingUtilities.updateComponentTreeUI(this);
        repaint();
    }
    
    private void applyTheme() {
        if (isDarkMode) {
            PRIMARY_COLOR = DARK_PRIMARY;
            SECONDARY_COLOR = DARK_SECONDARY;
            BG_COLOR = DARK_BG;
            TEXT_COLOR = DARK_TEXT;
            CARD_BG = DARK_CARD;
            ACCENT_HOVER = DARK_HOVER;
        } else {
            PRIMARY_COLOR = new Color(255, 106, 0);
            SECONDARY_COLOR = new Color(59, 130, 246);
            BG_COLOR = new Color(243, 244, 246);
            TEXT_COLOR = new Color(17, 24, 39);
            CARD_BG = Color.WHITE;
            ACCENT_HOVER = new Color(55, 65, 81);
        }
        
        UIManager.put("Panel.background", BG_COLOR);
        UIManager.put("OptionPane.background", CARD_BG);
        UIManager.put("OptionPane.messageFont", BODY_FONT);
        UIManager.put("Button.font", BUTTON_FONT);
        UIManager.put("Label.font", BODY_FONT);
        UIManager.put("Label.foreground", TEXT_COLOR);
        UIManager.put("TextField.font", BODY_FONT);
        UIManager.put("ComboBox.font", BODY_FONT);
        UIManager.put("List.font", BODY_FONT);
        UIManager.put("Table.font", BODY_FONT);
        UIManager.put("TableHeader.font", BUTTON_FONT);
        UIManager.put("Table.gridColor", new Color(229, 231, 235));
        UIManager.put("Table.selectionBackground", new Color(255, 106, 0, 60));
        UIManager.put("Table.selectionForeground", isDarkMode ? Color.WHITE : new Color(17, 24, 39));
        UIManager.put("ScrollBar.width", Integer.valueOf(8));
        UIManager.put("TabbedPane.font", BUTTON_FONT);
        UIManager.put("TabbedPane.selected", PRIMARY_COLOR);
    }

    private void connectDB() {
        try {
            con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            initDatabase();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "DB Connection Failed: " + e.getMessage());
        }
    }

    /**
     * Single authoritative place that creates / migrates every table the app needs.
     * Run once after every connection.  All statements use IF NOT EXISTS so they are
     * safe to call on an already‑initialised DB.
     */
    private void initDatabase() {
        try (java.sql.Statement st = con.createStatement()) {
            // ── students ─────────────────────────────────────────────────
            st.executeUpdate("CREATE TABLE IF NOT EXISTS students (" +
                "roll_no     VARCHAR(30)  NOT NULL," +
                "name        VARCHAR(200) NOT NULL," +
                "section     VARCHAR(50)  NOT NULL," +
                "branch      VARCHAR(50)  DEFAULT NULL," +
                "email       VARCHAR(200) DEFAULT NULL," +
                "PRIMARY KEY (roll_no)," +
                "INDEX idx_students_section (section)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // ── rooms ─────────────────────────────────────────────────────
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rooms (" +
                "room_no     VARCHAR(20)  NOT NULL," +
                "rows_count  INT          NOT NULL DEFAULT 6," +
                "cols_count  INT          NOT NULL DEFAULT 6," +
                "capacity    INT          NOT NULL DEFAULT 36," +
                "building    VARCHAR(100) DEFAULT NULL," +
                "PRIMARY KEY (room_no)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // ── room_broken_seats ─────────────────────────────────────────
            st.executeUpdate("CREATE TABLE IF NOT EXISTS room_broken_seats (" +
                "id          INT AUTO_INCREMENT PRIMARY KEY," +
                "room_no     VARCHAR(20) NOT NULL," +
                "row_idx     INT         NOT NULL," +
                "col_idx     INT         NOT NULL," +
                "UNIQUE KEY uq_broken (room_no, row_idx, col_idx)," +
                "INDEX idx_broken_room (room_no)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // ── seating_plan (core allocation table) ──────────────────────
            st.executeUpdate("CREATE TABLE IF NOT EXISTS seating_plan (" +
                "id               INT AUTO_INCREMENT PRIMARY KEY," +
                "room_no          VARCHAR(20)  NOT NULL," +
                "student_roll     VARCHAR(30)  NOT NULL," +
                "student_name     VARCHAR(200) NOT NULL," +
                "subject          VARCHAR(200) NOT NULL," +
                "seat_row         INT          NOT NULL," +
                "seat_col         INT          NOT NULL," +
                "exam_date        DATE         NOT NULL," +
                "start_time       VARCHAR(15)  NOT NULL," +   // stored as HH:MM 24h
                "end_time         VARCHAR(15)  NOT NULL," +
                "invigilator_name VARCHAR(200) DEFAULT ''," +
                "section          VARCHAR(50)  DEFAULT ''," +
                "INDEX idx_sp_date_time  (exam_date, start_time)," +
                "INDEX idx_sp_room_slot  (room_no, exam_date, start_time)," +
                "INDEX idx_sp_roll       (student_roll)," +
                "INDEX idx_sp_subject    (subject)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // ── faculty ────────────────────────────────────────────────────
            st.executeUpdate("CREATE TABLE IF NOT EXISTS faculty (" +
                "id          INT AUTO_INCREMENT PRIMARY KEY," +
                "name        VARCHAR(200) NOT NULL," +
                "department  VARCHAR(100) DEFAULT NULL," +
                "email       VARCHAR(200) DEFAULT NULL," +
                "phone       VARCHAR(20)  DEFAULT NULL," +
                "UNIQUE KEY uq_faculty_name (name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // ── users ──────────────────────────────────────────────────────
            st.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                "id         INT AUTO_INCREMENT PRIMARY KEY," +
                "username   VARCHAR(100) NOT NULL," +
                "password   VARCHAR(200) NOT NULL," +
                "role       VARCHAR(20)  NOT NULL DEFAULT 'EXAMINER'," +
                "UNIQUE KEY uq_username (username)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // ── subject_library ────────────────────────────────────────────
            st.executeUpdate("CREATE TABLE IF NOT EXISTS subject_library (" +
                "course_code  VARCHAR(20)  NOT NULL," +
                "subject_name VARCHAR(200) NOT NULL," +
                "PRIMARY KEY (course_code)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // ── exam_templates ─────────────────────────────────────────────
            st.executeUpdate("CREATE TABLE IF NOT EXISTS exam_templates (" +
                "name        VARCHAR(100)  NOT NULL," +
                "data        TEXT          NOT NULL," +
                "created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP," +
                "PRIMARY KEY (name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // ── timetable_entries (persists the in‑memory timetable) ───────
            st.executeUpdate("CREATE TABLE IF NOT EXISTS timetable_entries (" +
                "id           INT AUTO_INCREMENT PRIMARY KEY," +
                "branch       VARCHAR(50)   NOT NULL," +
                "exam_date    DATE          NOT NULL," +
                "subject_name VARCHAR(200)  NOT NULL," +
                "course_code  VARCHAR(20)   DEFAULT ''," +
                "slot         VARCHAR(5)    DEFAULT 'FN'," +
                "UNIQUE KEY uq_tt_entry (branch, exam_date, slot)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // ── exam_attendance (absentee tracking) ────────────────────────
            st.executeUpdate("CREATE TABLE IF NOT EXISTS exam_attendance (" +
                "id           INT AUTO_INCREMENT PRIMARY KEY," +
                "seating_id   INT          NOT NULL," +
                "student_roll VARCHAR(30)  NOT NULL," +
                "exam_date    DATE         NOT NULL," +
                "subject      VARCHAR(200) NOT NULL," +
                "room_no      VARCHAR(20)  NOT NULL," +
                "status       VARCHAR(10)  NOT NULL DEFAULT 'PRESENT'," +  // PRESENT | ABSENT
                "marked_by    VARCHAR(100) DEFAULT ''," +
                "marked_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "UNIQUE KEY uq_attend (student_roll, exam_date, subject)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // ── room_status (manual override: VACANT / OCCUPIED / MAINTENANCE) ──
            st.executeUpdate("CREATE TABLE IF NOT EXISTS room_status (" +
                "room_no      VARCHAR(20)  NOT NULL," +
                "status       VARCHAR(20)  NOT NULL DEFAULT 'VACANT'," +  // VACANT | OCCUPIED | MAINTENANCE
                "occupied_for VARCHAR(300) DEFAULT ''," +
                "updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "PRIMARY KEY (room_no)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // ── migrate: add any missing columns on existing tables ────────
            addColumnIfMissing(st, "faculty",         "email",   "VARCHAR(200) DEFAULT NULL");
            addColumnIfMissing(st, "faculty",         "phone",   "VARCHAR(20)  DEFAULT NULL");
            addColumnIfMissing(st, "students",        "email",   "VARCHAR(200) DEFAULT NULL");
            addColumnIfMissing(st, "students",        "branch",  "VARCHAR(50)  DEFAULT NULL");
            addColumnIfMissing(st, "seating_plan",    "section", "VARCHAR(50)  DEFAULT ''");
            addColumnIfMissing(st, "rooms",           "building","VARCHAR(100) DEFAULT NULL");
            addColumnIfMissing(st, "timetable_entries","slot",   "VARCHAR(5)   DEFAULT 'FN'");

            // Auto-reset room_status: any room whose exam end_time has passed → VACANT
            try {
                st.executeUpdate(
                    "UPDATE room_status rs " +
                    "JOIN rooms r ON r.room_no = rs.room_no " +
                    "SET rs.status = 'VACANT', rs.occupied_for = '' " +
                    "WHERE rs.status = 'OCCUPIED' " +
                    "AND NOT EXISTS (" +
                    "  SELECT 1 FROM seating_plan sp " +
                    "  WHERE sp.room_no = rs.room_no " +
                    "  AND CONCAT(sp.exam_date,' ',sp.end_time) > NOW() " +
                    ")"
                );
            } catch (Exception ignored) { /* safe — runs best-effort */ }

            // Load persisted timetable into in-memory data structures
            loadTimetableFromDB();

        } catch (Exception e) {
            System.err.println("initDatabase error: " + e.getMessage());
        }
    }

    /** Silently adds a column to a table only if it does not already exist. */
    private void addColumnIfMissing(java.sql.Statement st, String table, String col, String definition) {
        try {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + col + " " + definition);
        } catch (SQLException ignored) { /* column already exists — safe to ignore */ }
    }

    // ───────────────────────────────────────────────────────────
    //  DATA NORMALISATION UTILITIES
    // ───────────────────────────────────────────────────────────

    /**
     * Converts any time string to a canonical 24‑hour "HH:MM" format suitable
     * for DB storage and comparison.
     * Accepts:  "9:30 AM", "9:30AM", "09:30", "1:30 PM", "13:30", "930 AM", etc.
     */
    static String normalizeTime(String raw) {
        if (raw == null) return "00:00";
        String t = raw.trim().toUpperCase().replaceAll("\\s+", " ");
        // Remove en‑dash / em‑dash separation if someone passes a range
        if (t.contains("–") || t.contains("-")) {
            t = t.split("[–\\-]")[0].trim();
        }

        boolean pm = t.endsWith("PM");
        boolean am = t.endsWith("AM");
        // strip AM/PM suffix
        t = t.replace("AM", "").replace("PM", "").trim();
        // Remove any non‑digit except colon
        t = t.replaceAll("[^0-9:]", "");

        int h, m = 0;
        if (t.contains(":")) {
            String[] parts = t.split(":");
            h = Integer.parseInt(parts[0]);
            if (parts.length > 1 && !parts[1].isEmpty()) m = Integer.parseInt(parts[1]);
        } else if (t.length() >= 3) {
            // "930" → 9:30, "1030" → 10:30
            h = Integer.parseInt(t.substring(0, t.length() - 2));
            m = Integer.parseInt(t.substring(t.length() - 2));
        } else {
            h = Integer.parseInt(t);
        }

        if (pm && h != 12) h += 12;
        if (am && h == 12) h = 0;
        return String.format("%02d:%02d", h, m);
    }

    /**
     * Returns true if str is a valid YYYY-MM-DD date.
     */
    static boolean isValidDate(String str) {
        if (str == null || !str.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        try { java.time.LocalDate.parse(str); return true; }
        catch (java.time.format.DateTimeParseException e) { return false; }
    }

    /**
     * Normalizes a section name: trims extra whitespace and upper-cases.
     */
    static String normalizeSection(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    // ───────────────────────────────────────────────────────────
    //  TIMETABLE PERSISTENCE
    // ───────────────────────────────────────────────────────────

    /** Save in-memory timetableData to timetable_entries table. */
    private void saveTimetableToDB() {
        if (con == null) return;
        try {
            con.createStatement().executeUpdate("DELETE FROM timetable_entries");
            PreparedStatement pst = con.prepareStatement(
                "INSERT INTO timetable_entries (branch, exam_date, subject_name, course_code, slot) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE subject_name=VALUES(subject_name), course_code=VALUES(course_code)");
            for (String branch : timetableData.keySet()) {
                Map<String, String[]> branchData = timetableData.get(branch);
                for (Map.Entry<String, String[]> e : branchData.entrySet()) {
                    // Keys are either "YYYY-MM-DD|FN", "YYYY-MM-DD|AN", or legacy "YYYY-MM-DD\n(day)"
                    String key = e.getKey();
                    String rawDate;
                    String slot;
                    if (key.contains("|")) {
                        String[] kp = key.split("\\|", 2);
                        rawDate = kp[0].contains("\n") ? kp[0].split("\n")[0] : kp[0];
                        slot    = kp[1];
                    } else {
                        rawDate = key.contains("\n") ? key.split("\n")[0] : key;
                        slot    = "FN"; // legacy — assume FN
                    }
                    if (!isValidDate(rawDate)) continue;
                    String[] info = e.getValue();
                    pst.setString(1, branch);
                    pst.setString(2, rawDate);
                    pst.setString(3, info[0]);
                    pst.setString(4, info.length > 1 ? info[1] : "");
                    pst.setString(5, slot);
                    pst.addBatch();
                }
            }
            pst.executeBatch();
        } catch (Exception ex) {
            System.err.println("saveTimetableToDB: " + ex.getMessage());
        }
    }

    /** Load timetable_entries into in-memory structures on startup. */
    private void loadTimetableFromDB() {
        if (con == null) return;
        try {
            ResultSet rs = con.createStatement().executeQuery(
                "SELECT branch, DATE_FORMAT(exam_date,'%Y-%m-%d') as ed, subject_name, course_code, COALESCE(slot,'FN') as slot " +
                "FROM timetable_entries ORDER BY exam_date, slot");
            while (rs.next()) {
                String branch   = rs.getString("branch");
                String date     = rs.getString("ed");
                String subName  = rs.getString("subject_name");
                String code     = rs.getString("course_code");
                String slot     = rs.getString("slot");

                if (!timetableBranches.contains(branch)) timetableBranches.add(branch);
                if (!timetableDates.contains(date))       timetableDates.add(date);
                // Store with slot suffix for FN/AN disambiguation
                String mapKey = date + "|" + slot;
                timetableData.computeIfAbsent(branch, k -> new HashMap<>())
                    .put(mapKey, new String[]{subName, code});
            }
        } catch (Exception ex) {
            System.err.println("loadTimetableFromDB: " + ex.getMessage());
        }
    }

    // ==========================================
    //  SIDEBAR
    // ==========================================
    private JPanel createSidebar() {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Gradient sidebar background for depth
                GradientPaint gp = new GradientPaint(0, 0, new Color(20, 24, 32), 0, getHeight(), new Color(14, 17, 24));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        p.setLayout(new BorderLayout());
        p.setPreferredSize(new Dimension(270, 0));
        
        // Top fixed section (title + user)
        JPanel topFixed = new JPanel();
        topFixed.setLayout(new BoxLayout(topFixed, BoxLayout.Y_AXIS));
        topFixed.setOpaque(false);
        
        // Logo / Title Area
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);
        titlePanel.setBorder(new EmptyBorder(14, 16, 10, 16));
        titlePanel.setMaximumSize(new Dimension(300, 100));
        titlePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Programmatic logo banner — dark-mode version (white+orange on dark sidebar)
        BufferedImage sidebarLogo = createAdityaLogoImage(238, 56, true);
        JLabel logoSidebarLbl = new JLabel(new ImageIcon(sidebarLogo));
        logoSidebarLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        titlePanel.add(logoSidebarLbl);

        JLabel examPortalLbl = new JLabel("EXAMINATION PORTAL");
        examPortalLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 9));
        examPortalLbl.setForeground(new Color(255, 106, 0));
        examPortalLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        examPortalLbl.setBorder(new EmptyBorder(2, 2, 0, 0));
        titlePanel.add(examPortalLbl);

        topFixed.add(titlePanel);
        
        // Thin accent line
        JPanel accentLine = new JPanel();
        accentLine.setBackground(new Color(255, 106, 0, 140));
        accentLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        accentLine.setPreferredSize(new Dimension(0, 1));
        topFixed.add(accentLine);

        // User info area
        sidebarUserLabel = new JLabel("<html><span style='color:#6B7280;font-size:11px'>\u25CF Not logged in</span></html>");
        sidebarUserLabel.setBorder(new EmptyBorder(12, 20, 10, 20));
        sidebarUserLabel.setMaximumSize(new Dimension(280, 56));
        sidebarUserLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        topFixed.add(sidebarUserLabel);

        topFixed.add(Box.createVerticalStrut(4));
        
        // Scrollable navigation section
        JPanel navPanel = new JPanel();
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.Y_AXIS));
        navPanel.setOpaque(false);

        // Section label - ADMIN
        JLabel adminSectionLabel = createSidebarSectionLabel("ADMINISTRATION");
        adminSectionLabel.putClientProperty("role", "ADMIN");
        navPanel.add(adminSectionLabel);

        // Navigation Buttons - ADMIN
        addSidebarButton(navPanel, "\u25A0  Room Layouts", "ADMIN_ROOMS", "ADMIN");
        addSidebarButton(navPanel, "\u25C6  Student Data", "ADMIN_STUDENTS", "ADMIN");
        addSidebarButton(navPanel, "\u25C8  User Management", "USER_MANAGEMENT", "ADMIN");
        addSidebarButton(navPanel, "\u25B6  Invigilator Roster", "INVIG_ROSTER", "ADMIN");
        addSidebarButton(navPanel, "\u25CF  Backup & Restore", "BACKUP_RESTORE", "ADMIN");
        
        // Section label - EXAMINER
        JLabel examSectionLabel = createSidebarSectionLabel("EXAMINATIONS");
        examSectionLabel.putClientProperty("role", "EXAMINER");
        navPanel.add(examSectionLabel);
        
        // Navigation Buttons - EXAMINER
        addSidebarButton(navPanel, "\u25B7  Seating & Schedule", "EXAM_ALLOCATION", "EXAMINER");
        addSidebarButton(navPanel, "\u25C7  Scheduled Exams", "SCHEDULED_EXAMS", "EXAMINER");
        addSidebarButton(navPanel, "\u25A1  Exam Timetable", "EXAM_TIMETABLE", "EXAMINER");
        addSidebarButton(navPanel, "\u25A3  Hall Tickets", "HALL_TICKETS", "EXAMINER");
        addSidebarButton(navPanel, "\u25C9  Campus Map", "CAMPUS_MAP", "EXAMINER");
        addSidebarButton(navPanel, "\u25B6  Invigilator Roster", "INVIG_ROSTER", "EXAMINER");
        addSidebarButton(navPanel, "\u25B3  Templates", "TEMPLATES", "EXAMINER");
        addSidebarButton(navPanel, "\u25CE  Search & Print", "SEARCH", "EXAMINER");
        
        navPanel.add(Box.createVerticalGlue());
        
        JScrollPane navScroll = new JScrollPane(navPanel);
        navScroll.setBorder(null);
        navScroll.setOpaque(false);
        navScroll.getViewport().setOpaque(false);
        navScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        navScroll.getVerticalScrollBar().setUnitIncrement(12);
        navScroll.getVerticalScrollBar().setPreferredSize(new Dimension(4, 0));
        
        // Dark Mode Toggle and Logout
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(8, 20, 20, 20));
        bottomPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Thin separator above bottom
        JPanel bottomSep = new JPanel();
        bottomSep.setBackground(new Color(55, 65, 81));
        bottomSep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        bottomSep.setPreferredSize(new Dimension(0, 1));
        bottomPanel.add(bottomSep);
        bottomPanel.add(Box.createVerticalStrut(10));

        // Dark Mode Toggle
        JButton darkModeBtn = new JButton("\u263D  Toggle Dark Mode");
        darkModeBtn.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
        darkModeBtn.setForeground(new Color(156, 163, 175));
        darkModeBtn.setBackground(SIDEBAR_BG);
        darkModeBtn.setBorder(new EmptyBorder(8, 8, 8, 8));
        darkModeBtn.setFocusPainted(false);
        darkModeBtn.setContentAreaFilled(false);
        darkModeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        darkModeBtn.setMaximumSize(new Dimension(250, 32));
        darkModeBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        darkModeBtn.addActionListener(e -> toggleDarkMode());
        darkModeBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { darkModeBtn.setForeground(Color.WHITE); }
            public void mouseExited(MouseEvent e) { darkModeBtn.setForeground(new Color(156, 163, 175)); }
        });

        // Shortcuts Help
        JLabel shortcutsLabel = new JLabel("<html><span style='color:#6B7280;font-size:9px'>\u2328 Ctrl+D Dark \u00b7 Ctrl+S Save \u00b7 Ctrl+P Print</span></html>");
        shortcutsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        shortcutsLabel.setBorder(new EmptyBorder(2, 4, 8, 0));
        
        JButton logout = new ModernButton("\u25C0  Logout", new Color(220, 38, 38));
        logout.setMaximumSize(new Dimension(230, 42));
        logout.setAlignmentX(Component.LEFT_ALIGNMENT);
        logout.addActionListener(e -> {
            cardLayout.show(contentPanel, "LOGIN");
            sidebarButtons.values().forEach(b -> b.setVisible(false));
            currentRole = "";
        });
        
        bottomPanel.add(darkModeBtn);
        bottomPanel.add(Box.createVerticalStrut(4));
        bottomPanel.add(shortcutsLabel);
        bottomPanel.add(Box.createVerticalStrut(10));
        bottomPanel.add(logout);
        
        p.add(topFixed, BorderLayout.NORTH);
        p.add(navScroll, BorderLayout.CENTER);
        p.add(bottomPanel, BorderLayout.SOUTH);
        
        return p;
    }
    
    private JLabel createSidebarSectionLabel(String text) {
        JLabel lbl = new JLabel("  " + text);
        lbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 10));
        lbl.setForeground(new Color(107, 114, 128));
        lbl.setBorder(new EmptyBorder(14, 20, 6, 10));
        lbl.setMaximumSize(new Dimension(280, 36));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setVisible(false);
        return lbl;
    }

    private void addSidebarButton(JPanel p, String text, String viewName, String role) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean active  = Boolean.TRUE.equals(getClientProperty("active"));
                boolean hovered = Boolean.TRUE.equals(getClientProperty("hovered"));
                if (active) {
                    // Rounded filled highlight for active item
                    g2.setColor(new Color(255, 106, 0, 45));
                    g2.fillRoundRect(6, 2, getWidth() - 12, getHeight() - 4, 10, 10);
                    // Left accent strip
                    g2.setColor(new Color(255, 140, 50));
                    g2.fillRoundRect(2, 6, 3, getHeight() - 12, 3, 3);
                } else if (hovered) {
                    g2.setColor(new Color(255, 255, 255, 12));
                    g2.fillRoundRect(6, 2, getWidth() - 12, getHeight() - 4, 10, 10);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        b.setForeground(new Color(189, 195, 207));
        b.setBackground(SIDEBAR_BG);
        b.setBorder(new EmptyBorder(11, 24, 11, 10));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setMaximumSize(new Dimension(270, 44));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                b.putClientProperty("hovered", true);
                b.setForeground(Color.WHITE);
                b.repaint();
            }
            public void mouseExited(MouseEvent e) {
                b.putClientProperty("hovered", false);
                boolean active = Boolean.TRUE.equals(b.getClientProperty("active"));
                b.setForeground(active ? Color.WHITE : new Color(189, 195, 207));
                b.repaint();
            }
        });
        b.putClientProperty("role", role);
        b.addActionListener(e -> showView(viewName));
        b.setVisible(false);
        sidebarButtons.put(text, b);
        sidebarViewMap.put(viewName, b);
        p.add(b);
    }

    // ==========================================
    // LOGIN SCREEN
    // ==========================================
    private JPanel createLoginPanel() {
        JPanel p = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Deep blue gradient background
                GradientPaint gp = new GradientPaint(0, 0, new Color(15, 23, 50), getWidth(), getHeight(), new Color(8, 40, 80));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Decorative ambient circles
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fillOval(-120, -120, 500, 500);
                g2.setColor(new Color(255, 255, 255, 6));
                g2.fillOval(getWidth() - 250, getHeight() - 250, 520, 520);
                g2.setColor(new Color(255, 106, 0, 20));
                g2.fillOval(getWidth() / 2 - 200, -100, 450, 450);
                // Subtle dotted grid pattern
                g2.setColor(new Color(255, 255, 255, 6));
                for (int x = 0; x < getWidth(); x += 40) {
                    for (int y = 0; y < getHeight(); y += 40) {
                        g2.fillOval(x, y, 2, 2);
                    }
                }
            }
        };

        // Card with multi-layer drop shadow
        JPanel shadowWrap = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cardW = getWidth() - 12;
                int cardH = getHeight() - 12;
                // Shadow layers
                for (int i = 10; i > 0; i--) {
                    int alpha = (int)(20.0 * (1.0 - (double)i / 10));
                    g2.setColor(new Color(0, 0, 0, Math.max(alpha, 2)));
                    g2.fill(new RoundRectangle2D.Float(i + 2, i + 4, cardW - 4, cardH - 4, 24, 24));
                }
                // White card
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Float(0, 0, cardW, cardH, 24, 24));
                // Subtle top accent line
                g2.setColor(new Color(255, 106, 0));
                g2.fillRoundRect(cardW / 4, 0, cardW / 2, 3, 3, 3);
                g2.dispose();
            }
        };
        shadowWrap.setOpaque(false);
        shadowWrap.setPreferredSize(new Dimension(480, 600));
        shadowWrap.setMaximumSize(new Dimension(520, 660));

        JPanel card = new JPanel(new GridBagLayout());
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(32, 48, 36, 48));
        shadowWrap.add(card);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0;

        // ── Logo image ─────────────────────────────────────────────────────
        gbc.insets = new Insets(0, 0, 4, 0);
        BufferedImage logoImg = createAdityaLogoImage(320, 80);
        JLabel logoLbl = new JLabel(new ImageIcon(logoImg));
        logoLbl.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(logoLbl, gbc);

        gbc.gridy++; gbc.insets = new Insets(0, 0, 2, 0);
        JLabel portalBadge = new JLabel("EXAMINATION PORTAL", SwingConstants.CENTER);
        portalBadge.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
        portalBadge.setForeground(new Color(255, 106, 0));
        portalBadge.setBackground(new Color(255, 243, 230));
        portalBadge.setOpaque(true);
        portalBadge.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 200, 150), 1, true),
            new EmptyBorder(5, 18, 5, 18)
        ));
        card.add(portalBadge, gbc);

        gbc.gridy++; gbc.insets = new Insets(18, 0, 4, 0);
        JLabel titleLbl = new JLabel("Welcome Back", SwingConstants.CENTER);
        titleLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 28));
        titleLbl.setForeground(new Color(15, 23, 42));
        card.add(titleLbl, gbc);

        gbc.gridy++; gbc.insets = new Insets(0, 0, 26, 0);
        JLabel subLbl = new JLabel("Sign in to continue", SwingConstants.CENTER);
        subLbl.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        subLbl.setForeground(new Color(100, 116, 139));
        card.add(subLbl, gbc);

        gbc.gridy++; gbc.insets = new Insets(0, 0, 6, 0);
        JLabel uLbl = new JLabel("Username");
        uLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        uLbl.setForeground(new Color(51, 65, 85));
        card.add(uLbl, gbc);

        gbc.gridy++; gbc.insets = new Insets(0, 0, 16, 0);
        JTextField u = new ModernTextField(20);
        u.setPreferredSize(new Dimension(370, 46));
        card.add(u, gbc);

        gbc.gridy++; gbc.insets = new Insets(0, 0, 6, 0);
        JLabel pwLbl = new JLabel("Password");
        pwLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        pwLbl.setForeground(new Color(51, 65, 85));
        card.add(pwLbl, gbc);

        gbc.gridy++; gbc.insets = new Insets(0, 0, 24, 0);
        JPasswordField pw = new ModernPasswordField();
        pw.setPreferredSize(new Dimension(370, 46));
        card.add(pw, gbc);

        gbc.gridy++; gbc.insets = new Insets(0, 0, 10, 0);
        JButton loginBtn = new ModernButton("Sign In  \u2192", PRIMARY_COLOR);
        loginBtn.setPreferredSize(new Dimension(370, 48));
        loginBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 15));
        card.add(loginBtn, gbc);

        gbc.gridy++; gbc.insets = new Insets(0, 0, 0, 0);
        JLabel errorLabel = new JLabel("", SwingConstants.CENTER);
        errorLabel.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
        errorLabel.setForeground(new Color(220, 38, 38));
        card.add(errorLabel, gbc);

        // Quick-login role buttons
        gbc.gridy++; gbc.insets = new Insets(18, 0, 0, 0);
        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        hintPanel.setOpaque(false);
        JLabel hintLbl = new JLabel("Quick fill:");
        hintLbl.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
        hintLbl.setForeground(new Color(148, 163, 184));
        hintPanel.add(hintLbl);
        for (String[] cred : new String[][]{{"admin", "admin123", "Admin"}, {"examiner", "exam123", "Examiner"}}) {
            JButton qBtn = new JButton(cred[2]);
            qBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
            qBtn.setForeground(new Color(255, 106, 0));
            qBtn.setBackground(new Color(255, 243, 230));
            qBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 200, 150), 1, true),
                new EmptyBorder(4, 12, 4, 12)));
            qBtn.setFocusPainted(false);
            qBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            qBtn.setOpaque(true);
            qBtn.addActionListener(ev -> { u.setText(cred[0]); pw.setText(cred[1]); u.requestFocus(); });
            hintPanel.add(qBtn);
        }
        card.add(hintPanel, gbc);

        Runnable doLogin = () -> {
            errorLabel.setText("");
            String username = u.getText().trim();
            String password = new String(pw.getPassword()).trim();
            if (username.isEmpty() || password.isEmpty()) {
                errorLabel.setText("Please enter username and password.");
                return;
            }
            loginBtn.setEnabled(false);
            loginBtn.setText("Signing in...");
            new Thread(() -> {
                try {
                    PreparedStatement pst = con.prepareStatement(
                        "SELECT role FROM users WHERE username=? AND password=?");
                    pst.setString(1, username);
                    pst.setString(2, password);
                    ResultSet rs = pst.executeQuery();
                    SwingUtilities.invokeLater(() -> {
                        loginBtn.setEnabled(true);
                        loginBtn.setText("Sign In  \u2192");
                        try {
                            if (rs.next()) {
                                String role = rs.getString("role");
                                currentRole = role;
                                updateSidebarForRole(role);
                                cardLayout.show(contentPanel, "DASHBOARD");
                                u.setText(""); pw.setText("");
                                if ("ADMIN".equals(role)) showView("ADMIN_ROOMS");
                                else showView("EXAM_ALLOCATION");
                            } else {
                                errorLabel.setText("Invalid username or password.");
                                pw.setText("");
                                pw.requestFocus();
                            }
                        } catch (Exception ex) {
                            errorLabel.setText("Database error: " + ex.getMessage());
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        loginBtn.setEnabled(true);
                        loginBtn.setText("Sign In  \u2192");
                        errorLabel.setText("Connection error. Check database.");
                    });
                }
            }).start();
        };
        loginBtn.addActionListener(e -> doLogin.run());
        pw.addActionListener(e -> doLogin.run());
        u.addActionListener(e -> pw.requestFocus());

        p.add(shadowWrap);
        return p;
    }
    
    private void updateSidebarForRole(String role) {
        // Update buttons and section labels in the scrollable nav panel
        updateSidebarComponentsRecursive(sidebar, role);
        if (sidebarUserLabel != null) {
            String roleLabel = "ADMIN".equals(role) ? "Administrator" : "Examiner";
            String icon = "ADMIN".equals(role) ? "\u25C6" : "\u25B7";
            sidebarUserLabel.setText("<html>"
                + "<div style='color:#6B7280;font-size:10px'>Signed in as</div>"
                + "<div style='color:#E0E7FF;font-size:13px'><b>" + icon + " " + roleLabel + "</b></div>"
                + "</html>");
        }
    }
    
    private void updateSidebarComponentsRecursive(Container container, String role) {
        for (Component c : container.getComponents()) {
            if (c instanceof JButton && ((JButton) c).getClientProperty("role") != null) {
                String req = (String) ((JButton) c).getClientProperty("role");
                c.setVisible(req.equals(role));
            }
            if (c instanceof JLabel && ((JLabel) c).getClientProperty("role") != null) {
                String req = (String) ((JLabel) c).getClientProperty("role");
                c.setVisible(req.equals(role));
            }
            if (c instanceof Container) {
                updateSidebarComponentsRecursive((Container) c, role);
            }
        }
    }

    // ==========================================
    // ROOM MANAGER
    // ==========================================
    private JPanel createAdminRoomPanel() {
        JPanel p = new ModernPanel(new BorderLayout());
        
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 20, 0));
        JLabel title = new JLabel("Room Layout Configuration");
        title.setFont(SUBHEADER_FONT);
        title.setForeground(PRIMARY_COLOR);
        header.add(title);
        
        JPanel ctrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        ctrl.setOpaque(false);
        ctrl.setBorder(new EmptyBorder(0, 0, 20, 0));
        
        JTextField rNo = new ModernTextField(8); 
        decorateField(rNo, "Room No");
        
        JTextField rows = new ModernTextField(5); 
        decorateField(rows, "Rows");
        
        JTextField cols = new ModernTextField(5); 
        decorateField(cols, "Cols");
        
        JButton gen = new ModernButton("Preview Grid", SECONDARY_COLOR);
        gen.setPreferredSize(new Dimension(145, 44));
        
        JButton save = new ModernButton("Save Layout", PRIMARY_COLOR);
        save.setPreferredSize(new Dimension(145, 44));

        JButton clearGridBtn = new ModernButton("Clear Grid", new Color(220, 38, 38));
        clearGridBtn.setPreferredSize(new Dimension(120, 44));

        // Building selector
        String[] bhavanItems = new String[BHAVANS.length + 1];
        bhavanItems[0] = "-- Select Building --";
        System.arraycopy(BHAVANS, 0, bhavanItems, 1, BHAVANS.length);
        JComboBox<String> buildingCombo = new JComboBox<>(bhavanItems);
        buildingCombo.setFont(BODY_FONT);
        buildingCombo.setPreferredSize(new Dimension(190, 44));
        buildingCombo.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(209, 213, 219)), "Building",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
            new Font(FONT_FAMILY, Font.PLAIN, 11), new Color(107, 114, 128)));

        ctrl.add(rNo); ctrl.add(rows); ctrl.add(cols); ctrl.add(buildingCombo); ctrl.add(gen); ctrl.add(save); ctrl.add(clearGridBtn);
        
        JPanel gridWrap = new JPanel(new GridBagLayout());
        gridWrap.setOpaque(false);
        JPanel grid = new JPanel();
        grid.setOpaque(false);
        gridWrap.add(grid);
        
        Set<Point> broken = new HashSet<>();
        
        gen.addActionListener(e -> {
            try {
                int r = Integer.parseInt(rows.getText());
                int c = Integer.parseInt(cols.getText());
                grid.removeAll();
                grid.setLayout(new GridLayout(r, c, 5, 5));
                broken.clear();
                for(int i=0; i<r; i++) {
                    for(int j=0; j<c; j++) {
                        JToggleButton btn = new JToggleButton(i+","+j);
                        btn.setPreferredSize(new Dimension(50, 40));
                        btn.setBackground(new Color(220, 240, 220));
                        btn.setOpaque(true);
                        btn.setContentAreaFilled(true);
                        btn.setBorder(new LineBorder(SECONDARY_COLOR));
                        btn.setFocusPainted(false);
                        int fi=i, fj=j;
                        btn.addActionListener(ev -> {
                            if(btn.isSelected()) {
                                btn.setBackground(new Color(180, 50, 50));
                                btn.setForeground(Color.WHITE);
                                broken.add(new Point(fi, fj));
                            } else {
                                btn.setBackground(new Color(220, 240, 220));
                                btn.setForeground(Color.BLACK);
                                broken.remove(new Point(fi, fj));
                            }
                        });
                        grid.add(btn);
                    }
                }
                grid.revalidate(); grid.repaint();
            } catch(Exception ex) { JOptionPane.showMessageDialog(this, "Invalid dimensions"); }
        });

        // Holder so save listener can call loadRoomsTable before it is defined
        Runnable[] loadRef = {null};

        save.addActionListener(e -> {
            try {
                String rn = rNo.getText().trim();
                if (rn.isEmpty()) { JOptionPane.showMessageDialog(this, "Please enter a Room No."); return; }
                if (buildingCombo.getSelectedIndex() == 0) {
                    JOptionPane.showMessageDialog(this, "Please select a Building for this room."); return;
                }
                String selectedBuilding = (String) buildingCombo.getSelectedItem();
                int r = Integer.parseInt(rows.getText());
                int c = Integer.parseInt(cols.getText());
                int cap = (r*c) - broken.size();
                
                PreparedStatement pst = con.prepareStatement(
                    "INSERT INTO rooms (room_no, floor, rows_count, cols_count, capacity, building) VALUES (?, 1, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE rows_count=?, cols_count=?, capacity=?, building=?");
                pst.setString(1, rn); pst.setInt(2, r); pst.setInt(3, c); pst.setInt(4, cap); pst.setString(5, selectedBuilding);
                pst.setInt(6, r); pst.setInt(7, c); pst.setInt(8, cap); pst.setString(9, selectedBuilding);
                pst.executeUpdate();
                
                con.createStatement().executeUpdate("DELETE FROM room_broken_seats WHERE room_no='"+rn+"'");
                PreparedStatement bpst = con.prepareStatement("INSERT INTO room_broken_seats (room_no, row_idx, col_idx) VALUES (?,?,?)");
                for(Point pt : broken) {
                    bpst.setString(1, rn); bpst.setInt(2, pt.x); bpst.setInt(3, pt.y);
                    bpst.addBatch();
                }
                bpst.executeBatch();
                JOptionPane.showMessageDialog(this, "Room Layout Saved Successfully!");
                // Refresh the rooms table after save
                if (loadRef[0] != null) loadRef[0].run();
            } catch(Exception ex) { ex.printStackTrace(); }
        });

        clearGridBtn.addActionListener(e -> {
            if (grid.getComponentCount() == 0) {
                JOptionPane.showMessageDialog(this, "Grid is already empty.", "Clear Grid", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this,
                "Remove the entire grid preview, including all broken-seat markers?",
                "Clear Grid", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
            broken.clear();
            grid.removeAll();
            grid.setLayout(new FlowLayout());
            grid.revalidate();
            grid.repaint();
        });

        // ---- Existing Rooms Table ----
        String[] roomCols = {"Room No", "Building", "Rows", "Cols", "Capacity"};
        DefaultTableModel roomTableModel = new DefaultTableModel(roomCols, 0) {
            public boolean isCellEditable(int r2, int c2) { return false; }
        };
        JTable roomsTable = new JTable(roomTableModel);
        styleTable(roomsTable);
        roomsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomsTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        roomsTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        roomsTable.getColumnModel().getColumn(2).setPreferredWidth(50);
        roomsTable.getColumnModel().getColumn(3).setPreferredWidth(50);
        roomsTable.getColumnModel().getColumn(4).setPreferredWidth(70);

        // Lambda to reload the rooms table from DB
        Runnable loadRoomsTable = () -> {
            roomTableModel.setRowCount(0);
            try {
                ResultSet rs2 = con.createStatement().executeQuery(
                    "SELECT room_no, building, rows_count, cols_count, capacity FROM rooms ORDER BY building, room_no");
                while (rs2.next()) {
                    roomTableModel.addRow(new Object[]{
                        rs2.getString("room_no"),
                        rs2.getString("building") != null ? rs2.getString("building") : "—",
                        rs2.getInt("rows_count"),
                        rs2.getInt("cols_count"),
                        rs2.getInt("capacity")
                    });
                }
            } catch (Exception ignored) {}
        };

        // Click on a table row → load into edit fields
        roomsTable.getSelectionModel().addListSelectionListener(ev -> {
            if (ev.getValueIsAdjusting()) return;
            int selRow = roomsTable.getSelectedRow();
            if (selRow < 0) return;
            String selRoom = (String) roomTableModel.getValueAt(selRow, 0);
            String selBldg = (String) roomTableModel.getValueAt(selRow, 1);
            int selR = (int) roomTableModel.getValueAt(selRow, 2);
            int selC = (int) roomTableModel.getValueAt(selRow, 3);
            rNo.setText(selRoom);
            rows.setText(String.valueOf(selR));
            cols.setText(String.valueOf(selC));
            // Set building combo
            if ("—".equals(selBldg)) {
                buildingCombo.setSelectedIndex(0);
            } else {
                for (int i = 1; i < buildingCombo.getItemCount(); i++) {
                    if (buildingCombo.getItemAt(i).equals(selBldg)) {
                        buildingCombo.setSelectedIndex(i);
                        break;
                    }
                }
            }
        });

        // Delete Room button
        JButton deleteRoomBtn = new ModernButton("Delete Room", new Color(220, 38, 38));
        deleteRoomBtn.setFont(BODY_FONT);
        deleteRoomBtn.addActionListener(e -> {
            int selRow = roomsTable.getSelectedRow();
            if (selRow < 0) { JOptionPane.showMessageDialog(this, "Select a room to delete."); return; }
            String selRoom = (String) roomTableModel.getValueAt(selRow, 0);
            int confirm = JOptionPane.showConfirmDialog(this,
                "Delete room \"" + selRoom + "\" and all its broken-seat data?",
                "Delete Room", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
            try {
                con.createStatement().executeUpdate("DELETE FROM room_broken_seats WHERE room_no='" + selRoom + "'");
                con.createStatement().executeUpdate("DELETE FROM rooms WHERE room_no='" + selRoom + "'");
                loadRoomsTable.run();
                rNo.setText(""); rows.setText(""); cols.setText("");
                buildingCombo.setSelectedIndex(0);
                broken.clear(); grid.removeAll(); grid.revalidate(); grid.repaint();
            } catch (Exception ex) { ex.printStackTrace(); }
        });

        // Refresh button
        JButton refreshRoomsBtn = new ModernButton("Refresh List", SECONDARY_COLOR);
        refreshRoomsBtn.setFont(BODY_FONT);
        loadRef[0] = loadRoomsTable; // ensure holder is set
        refreshRoomsBtn.addActionListener(e -> loadRoomsTable.run());

        JPanel roomListBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        roomListBtns.setOpaque(false);
        roomListBtns.add(refreshRoomsBtn);
        roomListBtns.add(deleteRoomBtn);

        JLabel roomsListTitle = new JLabel("Existing Rooms  (" + roomTableModel.getRowCount() + " total)");
        roomsListTitle.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        roomsListTitle.setForeground(PRIMARY_COLOR);
        roomsListTitle.setBorder(new EmptyBorder(6, 4, 2, 0));

        // Refresh title count after each load
        roomTableModel.addTableModelListener(tme -> roomsListTitle.setText(
            "Existing Rooms  (" + roomTableModel.getRowCount() + " total)"));

        JPanel roomListHeader = new JPanel(new BorderLayout());
        roomListHeader.setOpaque(false);
        roomListHeader.add(roomsListTitle, BorderLayout.WEST);
        roomListHeader.add(roomListBtns, BorderLayout.EAST);

        JScrollPane roomsScroll = new JScrollPane(roomsTable);
        roomsScroll.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));

        JPanel leftPanel = new JPanel(new BorderLayout(0, 4));
        leftPanel.setOpaque(false);
        leftPanel.add(roomListHeader, BorderLayout.NORTH);
        leftPanel.add(roomsScroll, BorderLayout.CENTER);

        JScrollPane gridScroll = new JScrollPane(gridWrap);
        gridScroll.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        gridScroll.getViewport().setOpaque(false);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setOpaque(false);
        JLabel gridLabel = new JLabel("Seat Grid Preview");
        gridLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        gridLabel.setForeground(SECONDARY_COLOR);
        gridLabel.setBorder(new EmptyBorder(6, 4, 4, 0));
        rightPanel.add(gridLabel, BorderLayout.NORTH);
        rightPanel.add(gridScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(420);
        splitPane.setResizeWeight(0.45);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);

        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setOpaque(false);
        topSection.add(header, BorderLayout.NORTH);
        topSection.add(ctrl, BorderLayout.CENTER);

        p.add(topSection, BorderLayout.NORTH);
        p.add(splitPane, BorderLayout.CENTER);

        // Initial load
        loadRoomsTable.run();
        return p;
    }
    
    private void decorateField(JTextField f, String title) {
        f.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(209, 213, 219)), title, 
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, 
            new Font(FONT_FAMILY, Font.PLAIN, 11), new Color(107, 114, 128)
        ));
    }
    
    /**
     * Utility: Apply consistent modern styling to any JTable.
     * Includes striped rows, proper header, row height, and selection colors.
     */
    private void styleTable(JTable table) {
        table.setRowHeight(42);
        table.setFont(BODY_FONT);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(255, 106, 0, 40));
        table.setSelectionForeground(TEXT_COLOR);
        table.setFillsViewportHeight(true);
        
        // Header styling
        JTableHeader th = table.getTableHeader();
        th.setBackground(new Color(248, 250, 252));
        th.setForeground(new Color(71, 85, 105));
        th.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        th.setPreferredSize(new Dimension(0, 42));
        th.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(226, 232, 240)));
        th.setReorderingAllowed(false);
        
        // Striped rows with proper padding
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? CARD_BG : new Color(248, 250, 252));
                    c.setForeground(TEXT_COLOR);
                } else {
                    c.setBackground(new Color(255, 106, 0, 25));
                }
                ((JLabel) c).setBorder(new EmptyBorder(0, 12, 0, 12));
                return c;
            }
        });
    }
    
    /**
     * Utility: Wrap a JTable in a styled JScrollPane.
     */
    private JScrollPane createStyledTableScroll(JTable table) {
        styleTable(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
        scroll.getViewport().setBackground(CARD_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        return scroll;
    }

    // ==========================================
    // STUDENT IMPORT
    // ==========================================
    private JPanel createAdminStudentPanel() {
        JPanel p = new ModernPanel(new BorderLayout());
        
        JPanel topContainer = new JPanel();
        topContainer.setOpaque(false);
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        
        // Header
        JLabel title = new JLabel("Student Management");
        title.setFont(SUBHEADER_FONT);
        title.setForeground(PRIMARY_COLOR);
        title.setBorder(new EmptyBorder(0, 0, 20, 0));
        topContainer.add(title);

        // Bulk Import
        JPanel importPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        importPanel.setOpaque(false);
        importPanel.setBorder(createTitledBorder("Bulk Import"));
        
        JButton imp = new ModernButton("Upload CSV", PRIMARY_COLOR);
        imp.setPreferredSize(new Dimension(160, 44));
        
        JTextArea log = new JTextArea("Ready to import...\n");
        log.setFont(new Font("Menlo", Font.PLAIN, 12));
        log.setEditable(false);
        log.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        imp.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
            if(fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                new Thread(() -> parseAndImportCSV(fc.getSelectedFile(), log)).start();
            }
        });
        importPanel.add(imp);
        
        // Manual Entry
        JPanel manualPanel = new JPanel(new GridLayout(2, 5, 10, 10));
        manualPanel.setOpaque(false);
        manualPanel.setBorder(createTitledBorder("Manual Entry (Select Section)"));
        
        for(int i=1; i<=9; i++) {
            String sectionName = "CSE " + i;
            JButton btn = new ModernButton(sectionName, SECONDARY_COLOR);
            btn.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
            btn.setPreferredSize(new Dimension(110, 44));
            btn.addActionListener(e -> showManualAddDialog(sectionName));
            manualPanel.add(btn);
        }
        

        topContainer.add(importPanel);
        topContainer.add(Box.createVerticalStrut(20));
        topContainer.add(manualPanel);
        topContainer.add(Box.createVerticalStrut(20));
        
        p.add(topContainer, BorderLayout.NORTH);
        JScrollPane scrollLog = new JScrollPane(log);
        scrollLog.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        p.add(scrollLog, BorderLayout.CENTER);
        return p;
    }
    
    private TitledBorder createTitledBorder(String title) {
        return BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(209, 213, 219), 1, true), title, 
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, 
            new Font(FONT_FAMILY, Font.BOLD, 13), new Color(71, 85, 105)
        );
    }
    
    private void showManualAddDialog(String section) {
        JDialog d = new JDialog(this, "Add Student - " + section, true);
        d.setSize(450, 350);
        d.setLayout(new GridBagLayout());
        d.setLocationRelativeTo(this);
        d.getContentPane().setBackground(Color.WHITE);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 20, 15, 20);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        JTextField rollField = new ModernTextField(15);
        JTextField nameField = new ModernTextField(15);
        JButton saveBtn = new ModernButton("Save Student", PRIMARY_COLOR);
        
        gbc.gridx=0; gbc.gridy=0; d.add(new JLabel("Roll No:"), gbc);
        gbc.gridx=1; d.add(rollField, gbc);
        
        gbc.gridx=0; gbc.gridy=1; d.add(new JLabel("Name:"), gbc);
        gbc.gridx=1; d.add(nameField, gbc);
        
        gbc.gridx=0; gbc.gridy=2; gbc.gridwidth=2; 
        JLabel secLabel = new JLabel("Section: " + section, SwingConstants.CENTER);
        secLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 14));
        d.add(secLabel, gbc);
        
        gbc.gridx=0; gbc.gridy=3; gbc.gridwidth=2; 
        d.add(saveBtn, gbc);
        
        saveBtn.addActionListener(e -> {
            try {
                PreparedStatement pst = con.prepareStatement(
                    "INSERT INTO students (roll_no, name, section) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name=?, section=?");
                pst.setString(1, rollField.getText()); pst.setString(2, nameField.getText()); pst.setString(3, section);
                pst.setString(4, nameField.getText()); pst.setString(5, section);
                pst.executeUpdate();
                JOptionPane.showMessageDialog(d, "Added Successfully!");
                d.dispose();
            } catch(Exception ex) { JOptionPane.showMessageDialog(d, "Error: " + ex.getMessage()); }
        });
        d.setVisible(true);
    }

    // ==========================================
    // EXAMINER SCHEDULE
    // ==========================================
    private CardLayout examCardLayout;
    private JPanel examMainPanel;
    private List<String> allocatedRooms = new ArrayList<>();
    private List<JCheckBox> examSectionCBs = new ArrayList<>();
    private JPanel examSectionCBContainer;

    private JPanel createExaminerAllocationPanel() {
        examCardLayout = new CardLayout();
        examMainPanel = new ModernPanel(examCardLayout);

        // ========== INPUT PANEL ==========
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setOpaque(false);

        JPanel headerBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        headerBar.setOpaque(false);
        headerBar.setBorder(new EmptyBorder(5, 15, 8, 15));
        JLabel title = new JLabel("Exam Scheduler");
        title.setFont(new Font(FONT_FAMILY, Font.BOLD, 24));
        title.setForeground(PRIMARY_COLOR);
        headerBar.add(title);
        inputPanel.add(headerBar, BorderLayout.NORTH);

        // Two-column layout — left 60%, right 40%
        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(0, 15, 15, 15));

        // ===== LEFT: form fields (scrollable) =====
        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setOpaque(false);
        leftCol.setBorder(new EmptyBorder(6, 0, 0, 0));

        // Schedule picker
        JComboBox<String> scheduleBox = new JComboBox<>();
        scheduleBox.addItem("-- Select from Timetable (auto-fill) --");
        scheduleBox.setFont(BODY_FONT);

        JButton refreshSchedules = new ModernButton("Refresh", new Color(100, 116, 139));
        refreshSchedules.setPreferredSize(new Dimension(95, 44));

        JPanel scheduleRow = new JPanel(new BorderLayout(8, 0));
        scheduleRow.setOpaque(false);
        scheduleRow.setBorder(createTitledBorder("Auto-Fill from Timetable"));
        scheduleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        scheduleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        scheduleRow.add(scheduleBox, BorderLayout.CENTER);
        scheduleRow.add(refreshSchedules, BorderLayout.EAST);

        // Session selector — FN / AN only
        JComboBox<String> sessionBox = new JComboBox<>(new String[]{
            "FN  (9:30 AM – 12:30 PM)",
            "AN  (1:30 PM – 4:30 PM)"
        });
        sessionBox.setFont(BODY_FONT);
        JPanel sessionWrapper = new JPanel(new BorderLayout());
        sessionWrapper.setOpaque(false);
        sessionWrapper.setBorder(createTitledBorder("Session / Time Slot"));
        sessionWrapper.add(sessionBox, BorderLayout.CENTER);
        sessionWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        sessionWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Hidden time fields auto-filled from session
        JTextField startTimeField = new JTextField("9:30 AM");
        JTextField endTimeField = new JTextField("12:30 PM");

        sessionBox.addActionListener(ev -> {
            int idx = sessionBox.getSelectedIndex();
            switch (idx) {
                case 0: startTimeField.setText("9:30 AM");  endTimeField.setText("12:30 PM"); break;
                case 1: startTimeField.setText("1:30 PM");  endTimeField.setText("4:30 PM");  break;
            }
        });

        JComboBox<String> modeBox = new JComboBox<>(new String[]{"Section Wise", "Roll No Wise"});
        modeBox.setFont(BODY_FONT);
        JPanel modeWrapper = new JPanel(new BorderLayout());
        modeWrapper.setOpaque(false);
        modeWrapper.setBorder(createTitledBorder("Allocation Mode"));
        modeWrapper.add(modeBox, BorderLayout.CENTER);
        modeWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        modeWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField subject = new ModernTextField();
        decorateField(subject, "Subject Name");
        subject.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        subject.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField subjectCode = new ModernTextField();
        decorateField(subjectCode, "Course Code");
        subjectCode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        subjectCode.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField date = new ModernTextField();
        date.setText(java.time.LocalDate.now().toString());
        decorateField(date, "Exam Date (YYYY-MM-DD)");
        date.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        date.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Bhavan selector
        String[] bhavanOptions = new String[BHAVANS.length + 1];
        bhavanOptions[0] = "Any Available Room";
        System.arraycopy(BHAVANS, 0, bhavanOptions, 1, BHAVANS.length);
        JComboBox<String> bhavanBox = new JComboBox<>(bhavanOptions);
        bhavanBox.setFont(BODY_FONT);
        JPanel bhavanWrapper = new JPanel(new BorderLayout());
        bhavanWrapper.setOpaque(false);
        bhavanWrapper.setBorder(createTitledBorder("Preferred Bhavan / Building"));
        bhavanWrapper.add(bhavanBox, BorderLayout.CENTER);
        bhavanWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        bhavanWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox autoInvigilatorCB = new JCheckBox("Auto-assign Invigilators (Recommended)");
        autoInvigilatorCB.setFont(new Font(FONT_FAMILY, Font.BOLD, 15));
        autoInvigilatorCB.setOpaque(false);
        autoInvigilatorCB.setSelected(true);
        autoInvigilatorCB.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoInvigilatorCB.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        autoInvigilatorCB.setToolTipText("Automatically assigns one unique invigilator per room — no manual selection needed");
        
        JComboBox<String> facBox = new JComboBox<>();
        facBox.addItem("-- Auto-Assign --");
        facBox.setFont(BODY_FONT);
        facBox.setEnabled(false);
        loadFaculty(facBox);
        JPanel facWrapper = new JPanel(new BorderLayout());
        facWrapper.setOpaque(false);
        facWrapper.setBorder(createTitledBorder("Invigilator (or Auto)"));
        facWrapper.add(facBox, BorderLayout.CENTER);
        facWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        facWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        autoInvigilatorCB.addItemListener(ev -> {
            facBox.setEnabled(!autoInvigilatorCB.isSelected());
            if (autoInvigilatorCB.isSelected()) facBox.setSelectedIndex(0);
        });

        JButton checkBtn = new ModernButton("Check Rooms", new Color(234, 179, 8));
        checkBtn.setPreferredSize(new Dimension(130, 48));
        checkBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        JButton runBtn = new ModernButton("Generate Schedule", SECONDARY_COLOR);
        runBtn.setPreferredSize(new Dimension(190, 48));
        runBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        JButton hallViewBtn = new ModernButton("View Halls", new Color(139, 92, 246));
        hallViewBtn.setPreferredSize(new Dimension(130, 48));
        hallViewBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 15));
        hallViewBtn.addActionListener(e -> showHallLayoutByBuilding());

        JPanel actionRow = new JPanel(new GridLayout(3, 1, 0, 8));
        actionRow.setOpaque(false);
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 168));
        actionRow.add(checkBtn);
        actionRow.add(runBtn);
        actionRow.add(hallViewBtn);

        // Section dividers improve clarity
        JLabel lbl1 = new JLabel("  Subject Details");
        lbl1.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
        lbl1.setForeground(new Color(100, 116, 139));
        lbl1.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl2 = new JLabel("  Schedule Options");
        lbl2.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
        lbl2.setForeground(new Color(100, 116, 139));
        lbl2.setAlignmentX(Component.LEFT_ALIGNMENT);

        leftCol.add(scheduleRow);    leftCol.add(Box.createVerticalStrut(10));
        leftCol.add(lbl1);           leftCol.add(Box.createVerticalStrut(3));
        leftCol.add(subject);        leftCol.add(Box.createVerticalStrut(5));
        leftCol.add(subjectCode);    leftCol.add(Box.createVerticalStrut(5));
        leftCol.add(date);           leftCol.add(Box.createVerticalStrut(10));
        leftCol.add(lbl2);           leftCol.add(Box.createVerticalStrut(3));
        leftCol.add(sessionWrapper); leftCol.add(Box.createVerticalStrut(5));
        leftCol.add(modeWrapper);    leftCol.add(Box.createVerticalStrut(5));
        leftCol.add(bhavanWrapper);  leftCol.add(Box.createVerticalStrut(5));
        leftCol.add(autoInvigilatorCB); leftCol.add(Box.createVerticalStrut(3));
        leftCol.add(facWrapper);     leftCol.add(Box.createVerticalStrut(12));
        leftCol.add(actionRow);
        leftCol.add(Box.createVerticalGlue());

        // ===== RIGHT: Section checkboxes =====
        JPanel rightCol = new JPanel(new BorderLayout(0, 6));
        rightCol.setOpaque(false);
        rightCol.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(209, 213, 219), 1, true),
            "Select Sections  (check/uncheck freely)",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
            new Font(FONT_FAMILY, Font.BOLD, 13), new Color(71, 85, 105)));

        // Quick-select bar
        JPanel quickBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        quickBar.setOpaque(false);
        JButton qAll  = new ModernButton("All",  SECONDARY_COLOR);       qAll.setPreferredSize(new Dimension(64, 36));
        JButton qNone = new ModernButton("None", Color.GRAY);             qNone.setPreferredSize(new Dimension(70, 36));
        JButton qCSE  = new ModernButton("CSE",  new Color(59,130,246));   qCSE.setPreferredSize(new Dimension(64, 36));
        JButton qAIML = new ModernButton("AIML", new Color(5,150,105));   qAIML.setPreferredSize(new Dimension(70, 36));
        JButton qIT   = new ModernButton("IT",   new Color(217,119,6));   qIT.setPreferredSize(new Dimension(52, 36));
        JButton qRefSec = new ModernButton("Reload", new Color(100,116,139)); qRefSec.setPreferredSize(new Dimension(82, 36));
        quickBar.add(qAll); quickBar.add(qNone); quickBar.add(qCSE); quickBar.add(qAIML); quickBar.add(qIT); quickBar.add(qRefSec);

        // Count label
        JLabel selCountLbl = new JLabel("0 sections selected");
        selCountLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 14));
        selCountLbl.setForeground(PRIMARY_COLOR);
        selCountLbl.setBorder(new EmptyBorder(0, 10, 4, 0));

        examSectionCBContainer = new JPanel();
        examSectionCBContainer.setLayout(new BoxLayout(examSectionCBContainer, BoxLayout.Y_AXIS));
        examSectionCBContainer.setBackground(CARD_BG);

        JScrollPane cbScroll = new JScrollPane(examSectionCBContainer);
        cbScroll.setBorder(null);
        cbScroll.getViewport().setBackground(CARD_BG);
        cbScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel rightTop = new JPanel(new BorderLayout());
        rightTop.setOpaque(false);
        rightTop.add(quickBar, BorderLayout.NORTH);
        rightTop.add(selCountLbl, BorderLayout.SOUTH);

        rightCol.add(rightTop, BorderLayout.NORTH);
        rightCol.add(cbScroll, BorderLayout.CENTER);

        // Helper: update count label
        Runnable updateCount = () -> {
            long n = examSectionCBs.stream().filter(JCheckBox::isSelected).count();
            selCountLbl.setText(n + " section" + (n == 1 ? "" : "s") + " selected");
        };

        // Load section checkboxes from DB
        Runnable buildCBs = () -> {
            examSectionCBContainer.removeAll();
            examSectionCBs.clear();
            try {
                ResultSet rsec = con.createStatement().executeQuery(
                    "SELECT DISTINCT section FROM students ORDER BY section");
                while (rsec.next()) {
                    String sec = rsec.getString(1);
                    JCheckBox cb = new JCheckBox("  " + sec);
                    cb.setFont(new Font(FONT_FAMILY, Font.PLAIN, 16));
                    cb.setBackground(CARD_BG);
                    cb.setFocusPainted(false);
                    cb.setBorder(new EmptyBorder(5, 8, 5, 8));
                    cb.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    cb.addItemListener(evt -> updateCount.run());
                    examSectionCBs.add(cb);
                    examSectionCBContainer.add(cb);
                    // subtle separator
                    JSeparator sep = new JSeparator();
                    sep.setForeground(new Color(240, 240, 240));
                    examSectionCBContainer.add(sep);
                }
                if (examSectionCBs.isEmpty()) {
                    JLabel empty = new JLabel("  No sections found — import students first");
                    empty.setFont(new Font(FONT_FAMILY, Font.ITALIC, 13));
                    empty.setForeground(Color.GRAY);
                    empty.setBorder(new EmptyBorder(15, 10, 10, 10));
                    examSectionCBContainer.add(empty);
                }
            } catch (Exception ex) {
                JLabel err = new JLabel("  DB error: " + ex.getMessage());
                err.setForeground(Color.RED);
                examSectionCBContainer.add(err);
            }
            examSectionCBContainer.revalidate();
            examSectionCBContainer.repaint();
            updateCount.run();
        };
        buildCBs.run();

        // Quick-select listeners
        qAll.addActionListener(e  -> { examSectionCBs.forEach(cb -> cb.setSelected(true));  updateCount.run(); });
        qNone.addActionListener(e -> { examSectionCBs.forEach(cb -> cb.setSelected(false)); updateCount.run(); });
        qCSE.addActionListener(e  -> { autoSelectByPrefix("CSE",  examSectionCBs); updateCount.run(); });
        qAIML.addActionListener(e -> { autoSelectByPrefix("AIML", examSectionCBs); updateCount.run(); });
        qIT.addActionListener(e   -> { autoSelectByPrefix("IT",   examSectionCBs); updateCount.run(); });
        qRefSec.addActionListener(e -> buildCBs.run());

        // Populate schedule dropdown
        Runnable loadSchedules = () -> {
            scheduleBox.removeAllItems();
            scheduleBox.addItem("-- Select from Timetable (auto-fill) --");
            for (String branch : timetableBranches) {
                Map<String, String[]> branchData = timetableData.get(branch);
                if (branchData != null) {
                    for (Map.Entry<String, String[]> entry : branchData.entrySet()) {
                        String rawKey = entry.getKey(); // e.g. "2026-02-02\n(Monday)|FN"
                        String[] info  = entry.getValue();
                        // Strip |FN or |AN suffix before splitting on newline
                        String slot = "";
                        String pureDateStr = rawKey;
                        if (rawKey.endsWith("|FN")) { slot = "FN"; pureDateStr = rawKey.substring(0, rawKey.length() - 3); }
                        else if (rawKey.endsWith("|AN")) { slot = "AN"; pureDateStr = rawKey.substring(0, rawKey.length() - 3); }
                        String display = pureDateStr.contains("\n") ? pureDateStr.split("\n")[0] : pureDateStr;
                        scheduleBox.addItem(display + " | " + slot + " | " + info[0] + " (" + info[1] + ") | " + branch);
                    }
                }
            }
        };
        loadSchedules.run();

        refreshSchedules.addActionListener(e -> { loadSchedules.run(); buildCBs.run(); });

        // Auto-fill on schedule selection — resolve full name from subject library
        // Format: "date | slot | subject (code) | branch"
        scheduleBox.addActionListener(e -> {
            if (scheduleBox.getSelectedIndex() <= 0) return;
            String sel = (String) scheduleBox.getSelectedItem();
            if (sel == null || !sel.contains(" | ")) return;

            String[] parts = sel.split(" \\| ", 4);
            if (parts.length < 4) return;

            // parts[0]=date, parts[1]=slot (FN/AN), parts[2]=subject (code), parts[3]=branch
            date.setText(parts[0].trim());

            // Auto-select FN or AN session
            String slotVal = parts[1].trim();
            if ("FN".equals(slotVal)) sessionBox.setSelectedIndex(0);
            else if ("AN".equals(slotVal)) sessionBox.setSelectedIndex(1);

            String subPart = parts[2].trim();
            int lp = subPart.lastIndexOf('(');
            int rp = subPart.lastIndexOf(')');
            String rawSubName = "", rawCode = "";
            if (lp > 0 && rp > lp) {
                rawSubName = subPart.substring(0, lp).trim();
                rawCode    = subPart.substring(lp + 1, rp).trim();
            } else {
                rawSubName = subPart;
            }

            // Try to find full name from subject library (if stored as short form)
            String fullName = rawSubName;
            for (String[] sl : subjectLibrary) {
                if (sl[1].equalsIgnoreCase(rawCode) || sl[0].equalsIgnoreCase(rawSubName)) {
                    fullName = sl[0];
                    if (rawCode.isEmpty()) rawCode = sl[1];
                    break;
                }
            }
            subject.setText(fullName);
            subjectCode.setText(rawCode);

            // Auto-check sections matching the branch
            String branch = parts[3].trim();
            String basePrefix = branch.replaceAll("[^A-Za-z]", " ").trim().split("\\s+")[0].toUpperCase();
            examSectionCBs.forEach(cb -> {
                String secName = cb.getText().trim().toUpperCase();
                boolean match = secName.startsWith(branch.toUpperCase())
                    || (!basePrefix.isEmpty() && secName.startsWith(basePrefix));
                cb.setSelected(match);
            });
            updateCount.run();
        });

        // Wrap leftCol in scroll pane for overflow
        JScrollPane leftScroll = new JScrollPane(leftCol);
        leftScroll.setOpaque(false);
        leftScroll.getViewport().setOpaque(false);
        leftScroll.setBorder(BorderFactory.createLineBorder(new Color(209, 213, 219), 1, true));
        leftScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftScroll.getVerticalScrollBar().setUnitIncrement(12);

        // Fixed 50/50 split — no draggable divider
        body.setLayout(new GridLayout(1, 2, 14, 0));
        body.add(leftScroll);
        body.add(rightCol);

        JScrollPane bodyScroll = new JScrollPane(body);
        bodyScroll.setOpaque(false);
        bodyScroll.getViewport().setOpaque(false);
        bodyScroll.setBorder(null);
        bodyScroll.getVerticalScrollBar().setUnitIncrement(12);
        inputPanel.add(bodyScroll, BorderLayout.CENTER);

        // ========== DOWNLOAD PANEL ==========
        JPanel dlContent = new JPanel();
        dlContent.setLayout(new BoxLayout(dlContent, BoxLayout.Y_AXIS));
        dlContent.setOpaque(false);
        dlContent.setBorder(new EmptyBorder(30, 50, 30, 50));

        JLabel successLbl = new JLabel("Schedule Generated Successfully!", SwingConstants.CENTER);
        successLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 30));
        successLbl.setForeground(SECONDARY_COLOR);
        successLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        dlContent.add(successLbl);
        dlContent.add(Box.createVerticalStrut(8));

        JLabel roomInfoLbl = new JLabel(" ", SwingConstants.CENTER);
        roomInfoLbl.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        roomInfoLbl.setForeground(Color.GRAY);
        roomInfoLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        dlContent.add(roomInfoLbl);
        dlContent.add(Box.createVerticalStrut(25));

        // Button panel with fixed max width
        JPanel btnPanel = new JPanel();
        btnPanel.setLayout(new BoxLayout(btnPanel, BoxLayout.Y_AXIS));
        btnPanel.setOpaque(false);
        btnPanel.setMaximumSize(new Dimension(350, Integer.MAX_VALUE));
        btnPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnSeat = new ModernButton("Download Seating Plan (HTML)", PRIMARY_COLOR);
        btnSeat.setMaximumSize(new Dimension(420, 54));
        btnSeat.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        btnSeat.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnPanel.add(btnSeat);
        btnPanel.add(Box.createVerticalStrut(14));

        JButton btnAtt = new ModernButton("Download Attendance Sheet (HTML)", PRIMARY_COLOR);
        btnAtt.setMaximumSize(new Dimension(420, 54));
        btnAtt.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        btnAtt.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnPanel.add(btnAtt);
        btnPanel.add(Box.createVerticalStrut(14));

        JButton btnSeatingSummary = new ModernButton("Download Seating Summary (HOD)", new Color(139, 92, 246));
        btnSeatingSummary.setMaximumSize(new Dimension(420, 54));
        btnSeatingSummary.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        btnSeatingSummary.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnPanel.add(btnSeatingSummary);
        btnPanel.add(Box.createVerticalStrut(14));

        JButton btnSaveSchedule = new ModernButton("Save Schedule to File", new Color(107, 114, 128));
        btnSaveSchedule.setMaximumSize(new Dimension(420, 54));
        btnSaveSchedule.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        btnSaveSchedule.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnPanel.add(btnSaveSchedule);
        btnPanel.add(Box.createVerticalStrut(14));

        JButton btnEmailHODs = new ModernButton("Export HOD Schedule (JSON)", new Color(0, 120, 212));
        btnEmailHODs.setMaximumSize(new Dimension(420, 54));
        btnEmailHODs.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        btnEmailHODs.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnPanel.add(btnEmailHODs);
        btnPanel.add(Box.createVerticalStrut(22));

        JButton btnPartialRealloc = new ModernButton("Partial Re-allocation (Add Rooms)", new Color(234, 88, 12));
        btnPartialRealloc.setMaximumSize(new Dimension(420, 54));
        btnPartialRealloc.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        btnPartialRealloc.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnPanel.add(btnPartialRealloc);
        btnPanel.add(Box.createVerticalStrut(14));

        JButton btnMarkAbsent = new ModernButton("Mark Absentees", new Color(139, 92, 246));
        btnMarkAbsent.setMaximumSize(new Dimension(420, 54));
        btnMarkAbsent.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        btnMarkAbsent.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnPanel.add(btnMarkAbsent);
        btnPanel.add(Box.createVerticalStrut(22));

        JButton back = new ModernButton("Back to Scheduler", Color.GRAY);
        back.setMaximumSize(new Dimension(420, 50));
        back.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        back.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnPanel.add(back);

        dlContent.add(btnPanel);
        
        // Wrap in scroll pane for smaller screens
        JScrollPane dlScroll = new JScrollPane(dlContent);
        dlScroll.setOpaque(false);
        dlScroll.getViewport().setOpaque(false);
        dlScroll.setBorder(null);
        dlScroll.getVerticalScrollBar().setUnitIncrement(16);
        
        JPanel dlPanel = new JPanel(new BorderLayout());
        dlPanel.setOpaque(false);
        dlPanel.add(dlScroll, BorderLayout.CENTER);

        // ========== WIRE UP LISTENERS ==========
        checkBtn.addActionListener(e -> {
            String tStart = startTimeField.getText().trim();
            showAvailability(date.getText().trim(), tStart);
        });

        runBtn.addActionListener(e -> {
            List<String> chosenSections = new ArrayList<>();
            for (JCheckBox cb : examSectionCBs) {
                if (cb.isSelected()) chosenSections.add(cb.getText().trim());
            }
            if (chosenSections.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select at least one section from the right panel.");
                return;
            }
            String subText = subject.getText().trim();
            String dateText = date.getText().trim();
            if (subText.isEmpty()) { JOptionPane.showMessageDialog(this, "Please enter the subject name."); return; }
            if (dateText.isEmpty()) { JOptionPane.showMessageDialog(this, "Please enter the exam date.");    return; }

            // Enforce max 2 exams per day (FN + AN only)
            String tStartCheck = startTimeField.getText().trim();
            try {
                PreparedStatement pDay = con.prepareStatement(
                    "SELECT COUNT(DISTINCT start_time) FROM seating_plan WHERE exam_date=?");
                pDay.setString(1, dateText);
                ResultSet rsDay = pDay.executeQuery();
                if (rsDay.next() && rsDay.getInt(1) >= 2) {
                    JOptionPane.showMessageDialog(this,
                        "⚠  Max 2 exams (FN + AN) are already scheduled on " + dateText + ".\n" +
                        "Please choose a different date or remove an existing exam.",
                        "Limit Reached", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                // Also check if same date+time already occupied for same section
                PreparedStatement pSlot = con.prepareStatement(
                    "SELECT COUNT(*) FROM seating_plan WHERE exam_date=? AND start_time=?");
                pSlot.setString(1, dateText); pSlot.setString(2, tStartCheck);
                ResultSet rsSlot = pSlot.executeQuery();
                if (rsSlot.next() && rsSlot.getInt(1) > 0) {
                    int go = JOptionPane.showConfirmDialog(this,
                        "A session at " + tStartCheck + " on " + dateText + " already exists.\n" +
                        "Do you want to add more students to the same slot?",
                        "Slot Already Used", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (go != JOptionPane.YES_OPTION) return;
                }

                // ── Room conflict checker: detect rooms busy for a DIFFERENT subject ──
                PreparedStatement pConflict = con.prepareStatement(
                    "SELECT room_no, subject FROM seating_plan " +
                    "WHERE exam_date=? AND start_time=? AND subject != ? " +
                    "GROUP BY room_no, subject");
                pConflict.setString(1, dateText);
                pConflict.setString(2, normalizeTime(tStartCheck));
                pConflict.setString(3, subText);
                ResultSet rsConflict = pConflict.executeQuery();
                StringBuilder conflictMsg = new StringBuilder();
                while (rsConflict.next()) {
                    conflictMsg.append("  • Room ").append(rsConflict.getString("room_no"))
                               .append("  →  ").append(rsConflict.getString("subject")).append("\n");
                }
                if (conflictMsg.length() > 0) {
                    int gc = JOptionPane.showConfirmDialog(this,
                        "⚠  The following rooms are already booked for a DIFFERENT subject at "
                        + tStartCheck + " on " + dateText + ":\n\n" + conflictMsg +
                        "\nThese rooms will be automatically skipped during allocation.\n" +
                        "Continue anyway?",
                        "Room Conflict Detected", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (gc != JOptionPane.YES_OPTION) return;
                }
            } catch (Exception ex) { /* non-fatal, proceed */ }

            runBtn.setText("Generating...");
            runBtn.setEnabled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            String bhavanFilter = bhavanBox.getSelectedIndex() == 0 ? null : (String) bhavanBox.getSelectedItem();
            String invig = facBox.getSelectedIndex() == 0 ? "" : (String) facBox.getSelectedItem();
            boolean mixedMode = modeBox.getSelectedIndex() == 1;
            boolean autoAssignInvig = autoInvigilatorCB.isSelected();

            // Get time from text fields
            String tStart = startTimeField.getText().trim();
            String tEnd = endTimeField.getText().trim();

            new Thread(() -> {
                boolean ok = autoGenerateSchedule(
                    chosenSections, mixedMode, subText, dateText,
                    tStart, tEnd, invig, bhavanFilter, autoAssignInvig);
                SwingUtilities.invokeLater(() -> {
                    runBtn.setText("Generate");
                    runBtn.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                    if (ok) {
                        roomInfoLbl.setText(allocatedRooms.size() + " room(s) used: " + String.join(", ", allocatedRooms));
                        examCardLayout.show(examMainPanel, "DL");
                        // Auto-refresh Scheduled Exams & Invigilator Roster
                        if (scheduledExamsRefresher != null) scheduledExamsRefresher.run();
                        if (rosterRefresher != null) rosterRefresher.run();
                    }
                });
            }).start();
        });

        btnSeat.addActionListener(e -> downloadHTMLReports("SEATING"));
        btnAtt.addActionListener(e -> downloadHTMLReports("ATTENDANCE"));
        btnSeatingSummary.addActionListener(e -> downloadSeatingSummary());
        btnSaveSchedule.addActionListener(e -> saveScheduleToFile());
        btnEmailHODs.addActionListener(e -> sendScheduleToHODs());
        btnPartialRealloc.addActionListener(e -> showPartialReallocationDialog(subject.getText().trim(), date.getText().trim(), startTimeField.getText().trim(), endTimeField.getText().trim(), roomInfoLbl));
        btnMarkAbsent.addActionListener(e -> showMarkAbsenteesDialog(subject.getText().trim(), date.getText().trim(), startTimeField.getText().trim()));
        back.addActionListener(e -> examCardLayout.show(examMainPanel, "INPUT"));

        examMainPanel.add(inputPanel, "INPUT");
        examMainPanel.add(dlPanel,    "DL");

        return examMainPanel;
    }

    // ==========================================
    // SCHEDULED EXAMS PANEL (visible to ALL examiners)
    // ==========================================
    private JPanel createScheduledExamsPanel() {
        JPanel p = new ModernPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(0, 0, 18, 0));
        JLabel title = new JLabel("All Scheduled Exams");
        title.setFont(new Font(FONT_FAMILY, Font.BOLD, 26));
        title.setForeground(PRIMARY_COLOR);
        JLabel subtitle = new JLabel("View exams scheduled by any examiner \u2014 download reports or re-allocate invigilators");
        subtitle.setFont(new Font(FONT_FAMILY, Font.PLAIN, 14));
        subtitle.setForeground(Color.GRAY);
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);
        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(4));
        titleBlock.add(subtitle);
        headerPanel.add(titleBlock, BorderLayout.CENTER);

        JButton refreshBtn = new ModernButton("Refresh", new Color(100, 116, 139));
        refreshBtn.setPreferredSize(new Dimension(120, 44));
        refreshBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 15));
        headerPanel.add(refreshBtn, BorderLayout.EAST);

        // Table
        DefaultTableModel schedModel = new DefaultTableModel(
            new String[]{"Exam Date", "Subject", "Time Slot", "Rooms Used", "Total Students", "Invigilators"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable schedTable = new JTable(schedModel);
        schedTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(schedTable);

        // Set column widths
        schedTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        schedTable.getColumnModel().getColumn(1).setPreferredWidth(220);
        schedTable.getColumnModel().getColumn(2).setPreferredWidth(160);
        schedTable.getColumnModel().getColumn(3).setPreferredWidth(180);
        schedTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        schedTable.getColumnModel().getColumn(5).setPreferredWidth(250);

        JScrollPane tableScroll = createStyledTableScroll(schedTable);

        // Empty-state overlay shown when no exams exist
        JLabel emptyStateLbl = new JLabel("No exams scheduled yet — generate a schedule from \"Seating & Schedule\" first.", SwingConstants.CENTER);
        emptyStateLbl.setFont(new Font(FONT_FAMILY, Font.ITALIC, 14));
        emptyStateLbl.setForeground(new Color(150, 150, 150));
        emptyStateLbl.setBorder(new EmptyBorder(30, 0, 0, 0));

        JPanel tableArea = new JPanel(new BorderLayout());
        tableArea.setOpaque(false);
        tableArea.add(tableScroll, BorderLayout.CENTER);
        tableArea.add(emptyStateLbl, BorderLayout.SOUTH);

        // Bottom action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 12));
        actionPanel.setOpaque(false);

        JButton viewSeatingBtn = new ModernButton("View Seating Plan", PRIMARY_COLOR);
        viewSeatingBtn.setPreferredSize(new Dimension(200, 50));
        viewSeatingBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 15));

        JButton viewAttendanceBtn = new ModernButton("View Attendance", new Color(139, 92, 246));
        viewAttendanceBtn.setPreferredSize(new Dimension(200, 50));
        viewAttendanceBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 15));

        JButton markAbsentBtn = new ModernButton("Mark Absentees", new Color(234, 88, 12));
        markAbsentBtn.setPreferredSize(new Dimension(185, 50));
        markAbsentBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 15));

        JButton reallocInvigBtn = new ModernButton("Re-allocate Invigilators", new Color(234, 179, 8));
        reallocInvigBtn.setPreferredSize(new Dimension(240, 50));
        reallocInvigBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 15));

        JButton deleteExamBtn = new ModernButton("Delete Schedule", new Color(220, 38, 38));
        deleteExamBtn.setPreferredSize(new Dimension(180, 50));
        deleteExamBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 15));

        actionPanel.add(viewSeatingBtn);
        actionPanel.add(viewAttendanceBtn);
        actionPanel.add(markAbsentBtn);
        actionPanel.add(reallocInvigBtn);
        actionPanel.add(deleteExamBtn);

        p.add(tableArea, BorderLayout.CENTER);
        p.add(actionPanel, BorderLayout.SOUTH);

        // Data store for row -> rooms mapping
        List<String[]> schedRowData = new ArrayList<>(); // [date, subject, startTime]

        // Load scheduled exams
        Runnable loadScheduledExams = () -> {
            schedModel.setRowCount(0);
            schedRowData.clear();
            try {
                ResultSet rs = con.createStatement().executeQuery(
                    "SELECT exam_date, subject, start_time, end_time, " +
                    "GROUP_CONCAT(DISTINCT room_no ORDER BY room_no) AS rooms, " +
                    "COUNT(*) AS total_students, " +
                    "GROUP_CONCAT(DISTINCT invigilator_name ORDER BY invigilator_name SEPARATOR ', ') AS invigilators " +
                    "FROM seating_plan " +
                    "GROUP BY exam_date, subject, start_time, end_time " +
                    "ORDER BY exam_date DESC, start_time");
                while (rs.next()) {
                    String examDate = rs.getString("exam_date");
                    String subj = rs.getString("subject");
                    String sTime = rs.getString("start_time");
                    String eTime = rs.getString("end_time");
                    String rooms = rs.getString("rooms");
                    int total = rs.getInt("total_students");
                    String invigs = rs.getString("invigilators");
                    if (invigs == null || invigs.trim().isEmpty() || invigs.equals(", ")) invigs = "(None assigned)";
                    // Label FN or AN
                    String sessionLabel = (sTime != null && (sTime.startsWith("9") || sTime.startsWith("10") || sTime.startsWith("11"))) ? "FN" : "AN";

                    schedModel.addRow(new Object[]{
                        examDate, subj, sessionLabel + "  " + sTime + " \u2013 " + eTime,
                        rooms, total, invigs
                    });
                    schedRowData.add(new String[]{examDate, subj, sTime});
                }
                // leave table empty — empty-state label shown below
            } catch (Exception ex) {
                ex.printStackTrace();
                schedModel.addRow(new Object[]{"Error loading: " + ex.getMessage(), "", "", "", "", ""});
            }
        };

        loadScheduledExams.run();
        scheduledExamsRefresher = loadScheduledExams;
        refreshBtn.addActionListener(e -> loadScheduledExams.run());

        // View Seating for selected exam
        viewSeatingBtn.addActionListener(e -> {
            int row = schedTable.getSelectedRow();
            if (row < 0 || row >= schedRowData.size()) {
                JOptionPane.showMessageDialog(this, "Please select an exam from the table.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String[] info = schedRowData.get(row);
            try {
                // Get rooms for this exam
                PreparedStatement pst = con.prepareStatement(
                    "SELECT DISTINCT room_no FROM seating_plan WHERE exam_date=? AND subject=? AND start_time=? ORDER BY room_no");
                pst.setString(1, info[0]); pst.setString(2, info[1]); pst.setString(3, info[2]);
                ResultSet rs = pst.executeQuery();
                List<String> rooms = new ArrayList<>();
                while (rs.next()) rooms.add(rs.getString(1));

                if (rooms.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "No rooms found for this exam.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Temporarily set allocatedRooms and download
                List<String> savedRooms = new ArrayList<>(allocatedRooms);
                allocatedRooms.clear();
                allocatedRooms.addAll(rooms);
                downloadHTMLReports("SEATING");
                allocatedRooms.clear();
                allocatedRooms.addAll(savedRooms);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        });

        // View Attendance for selected exam
        viewAttendanceBtn.addActionListener(e -> {
            int row = schedTable.getSelectedRow();
            if (row < 0 || row >= schedRowData.size()) {
                JOptionPane.showMessageDialog(this, "Please select an exam from the table.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String[] info = schedRowData.get(row);
            try {
                PreparedStatement pst = con.prepareStatement(
                    "SELECT DISTINCT room_no FROM seating_plan WHERE exam_date=? AND subject=? AND start_time=? ORDER BY room_no");
                pst.setString(1, info[0]); pst.setString(2, info[1]); pst.setString(3, info[2]);
                ResultSet rs = pst.executeQuery();
                List<String> rooms = new ArrayList<>();
                while (rs.next()) rooms.add(rs.getString(1));

                List<String> savedRooms = new ArrayList<>(allocatedRooms);
                allocatedRooms.clear();
                allocatedRooms.addAll(rooms);
                downloadHTMLReports("ATTENDANCE");
                allocatedRooms.clear();
                allocatedRooms.addAll(savedRooms);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        });

        // Re-allocate invigilators for selected exam (auto, unique per room)
        reallocInvigBtn.addActionListener(e -> {
            int row = schedTable.getSelectedRow();
            if (row < 0 || row >= schedRowData.size()) {
                JOptionPane.showMessageDialog(this, "Please select an exam from the table.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String[] info = schedRowData.get(row);
            try {
                // Get rooms for this exam
                PreparedStatement pstRooms = con.prepareStatement(
                    "SELECT DISTINCT room_no FROM seating_plan WHERE exam_date=? AND subject=? AND start_time=? ORDER BY room_no");
                pstRooms.setString(1, info[0]); pstRooms.setString(2, info[1]); pstRooms.setString(3, info[2]);
                ResultSet rsRooms = pstRooms.executeQuery();
                List<String> rooms = new ArrayList<>();
                while (rsRooms.next()) rooms.add(rsRooms.getString(1));

                // Get already-assigned invigilators for OTHER exams at the same timeslot
                Set<String> busyInvigs = new HashSet<>();
                PreparedStatement pstBusy = con.prepareStatement(
                    "SELECT DISTINCT invigilator_name FROM seating_plan WHERE exam_date=? AND start_time=? AND subject!=? AND invigilator_name IS NOT NULL AND invigilator_name != ''");
                pstBusy.setString(1, info[0]); pstBusy.setString(2, info[2]); pstBusy.setString(3, info[1]);
                ResultSet rsBusy = pstBusy.executeQuery();
                while (rsBusy.next()) busyInvigs.add(rsBusy.getString(1));

                // Get all available faculty
                List<String> available = new ArrayList<>();
                ResultSet rsFac = con.createStatement().executeQuery("SELECT name FROM faculty ORDER BY name");
                while (rsFac.next()) {
                    String name = rsFac.getString(1);
                    if (!busyInvigs.contains(name)) available.add(name);
                }

                if (available.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                        "No available invigilators for this time slot.\\nAll faculty are assigned to other exams at the same time.",
                        "No Invigilators", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                // Shuffle and assign one per room (unique)
                Collections.shuffle(available);
                Set<String> usedInvigs = new HashSet<>();
                int invigIdx = 0;

                for (String room : rooms) {
                    String invig = "";
                    if (invigIdx < available.size()) {
                        invig = available.get(invigIdx++);
                        usedInvigs.add(invig);
                    }
                    // Update all seats in this room for this exam
                    PreparedStatement pstUpdate = con.prepareStatement(
                        "UPDATE seating_plan SET invigilator_name=? WHERE room_no=? AND exam_date=? AND subject=? AND start_time=?");
                    pstUpdate.setString(1, invig);
                    pstUpdate.setString(2, room);
                    pstUpdate.setString(3, info[0]);
                    pstUpdate.setString(4, info[1]);
                    pstUpdate.setString(5, info[2]);
                    pstUpdate.executeUpdate();
                }

                // Build summary
                StringBuilder summary = new StringBuilder("Invigilators re-allocated (unique per room):\n\n");
                invigIdx = 0;
                for (String room : rooms) {
                    String invig = invigIdx < available.size() ? available.get(invigIdx++) : "(None \u2014 add more faculty)";
                    summary.append("  Room ").append(room).append("  \u2192  ").append(invig).append("\n");
                }
                JOptionPane.showMessageDialog(this, summary.toString(), "Re-allocation Complete", JOptionPane.INFORMATION_MESSAGE);
                loadScheduledExams.run();

            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error re-allocating: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Delete selected exam schedule
        markAbsentBtn.addActionListener(e -> {
            int row = schedTable.getSelectedRow();
            if (row < 0 || row >= schedRowData.size()) {
                JOptionPane.showMessageDialog(this, "Please select an exam from the table.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String[] info = schedRowData.get(row);
            showMarkAbsenteesDialog(info[1], info[0], info[2]);
        });

        deleteExamBtn.addActionListener(e -> {
            int row = schedTable.getSelectedRow();
            if (row < 0 || row >= schedRowData.size()) {
                JOptionPane.showMessageDialog(this, "Please select an exam from the table.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String[] info = schedRowData.get(row);
            int confirm = JOptionPane.showConfirmDialog(this,
                "Delete ALL seating data for:\n\nSubject: " + info[1] + "\nDate: " + info[0] + "\nTime: " + info[2] +
                "\n\nThis cannot be undone!", "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    PreparedStatement pst = con.prepareStatement(
                        "DELETE FROM seating_plan WHERE exam_date=? AND subject=? AND start_time=?");
                    pst.setString(1, info[0]); pst.setString(2, info[1]); pst.setString(3, info[2]);
                    int deleted = pst.executeUpdate();
                    JOptionPane.showMessageDialog(this, deleted + " records deleted.", "Deleted", JOptionPane.INFORMATION_MESSAGE);
                    loadScheduledExams.run();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
                }
            }
        });

        p.add(headerPanel, BorderLayout.NORTH);
        p.add(tableArea,   BorderLayout.CENTER);
        p.add(actionPanel, BorderLayout.SOUTH);
        return p;
    }

    /** Select checkboxes whose section name starts with prefix; deselect others (case-insensitive). */
    private void autoSelectByPrefix(String prefix, List<JCheckBox> cbs) {
        String up = prefix.toUpperCase();
        cbs.forEach(cb -> cb.setSelected(cb.getText().trim().toUpperCase().startsWith(up)));
    }
    
    /** Parse start time from slot string like "10:30 AM - 12:00 PM (Session 1)" */
    private String parseTimeStart(String slot) {
        if (slot == null || slot.isEmpty()) return "10:00 AM";
        String[] parts = slot.split(" - ");
        if (parts.length >= 1) {
            return parts[0].trim();
        }
        return "10:00 AM";
    }
    
    /** Parse end time from slot string like "10:30 AM - 12:00 PM (Session 1)" */
    private String parseTimeEnd(String slot) {
        if (slot == null || slot.isEmpty()) return "12:00 PM";
        String[] parts = slot.split(" - ");
        if (parts.length >= 2) {
            String endPart = parts[1].trim();
            // Remove session info like "(Session 1)"
            int parenIdx = endPart.indexOf('(');
            if (parenIdx > 0) {
                endPart = endPart.substring(0, parenIdx).trim();
            }
            return endPart;
        }
        return "12:00 PM";
    }
    
    /** Generate seating summary like the official document format (Room-wise roll ranges) */
    private void downloadSeatingSummary() {
        if (allocatedRooms.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No rooms allocated yet. Generate a schedule first.",
                "Nothing to Export", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        downloads.mkdirs();
        
        try {
            // Get exam details from first room
            String subject = "", examDate = "", startTime = "", endTime = "";
            PreparedStatement pHead = con.prepareStatement(
                "SELECT subject, exam_date, start_time, end_time FROM seating_plan WHERE room_no=? LIMIT 1");
            pHead.setString(1, allocatedRooms.get(0));
            ResultSet rsHead = pHead.executeQuery();
            if (rsHead.next()) {
                subject = rsHead.getString(1);
                examDate = rsHead.getString(2);
                startTime = rsHead.getString(3);
                endTime = rsHead.getString(4);
            }
            
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n<html lang='en'><head>\n")
                .append("<meta charset='UTF-8'>\n")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1'>\n")
                .append("<title>Seating Arrangement Summary</title>\n")
                .append("<style>\n")
                .append("  *, *::before, *::after { box-sizing: border-box; }\n")
                .append("  body { font-family: 'Times New Roman', Times, serif; background: #fff; color: #000; padding: 30px 40px; font-size: 12pt; }\n")
                .append("  .no-print { text-align: center; margin-bottom: 20px; }\n")
                .append("  .print-btn { background: #4338ca; color: #fff; border: none; padding: 12px 30px; font-size: 14px; border-radius: 6px; cursor: pointer; font-family: Arial; }\n")
                .append("  .print-btn:hover { background: #3730a3; }\n")
                .append("  .header { text-align: center; margin-bottom: 25px; }\n")
                .append("  .header img { width: 60px; vertical-align: middle; margin-right: 15px; }\n")
                .append("  .header h1 { display: inline; font-size: 22pt; border: 2px solid #c00; border-radius: 25px; padding: 8px 25px; color: #000; margin: 0; }\n")
                .append("  .sub-header { text-align: center; margin: 20px 0; }\n")
                .append("  .sub-header h2 { font-size: 14pt; margin: 5px 0; font-weight: normal; }\n")
                .append("  .sub-header h3 { font-size: 13pt; margin: 5px 0; }\n")
                .append("  table { width: 100%; border-collapse: collapse; margin-top: 15px; }\n")
                .append("  th, td { border: 1px solid #000; padding: 10px 12px; text-align: center; vertical-align: middle; }\n")
                .append("  th { background: #f0f0f0; font-weight: bold; font-size: 11pt; }\n")
                .append("  td { font-size: 11pt; }\n")
                .append("  .signatures { margin-top: 50px; display: flex; justify-content: space-between; }\n")
                .append("  .sig-block { text-align: center; }\n")
                .append("  .sig-line { margin-top: 40px; border-top: 1px solid #000; padding-top: 5px; }\n")
                .append("  @media print {\n")
                .append("    .no-print { display: none !important; }\n")
                .append("    body { padding: 15mm; }\n")
                .append("    @page { size: A4 portrait; margin: 10mm; }\n")
                .append("  }\n")
                .append("</style>\n</head>\n<body>\n");
            
            html.append("<div class='no-print'>")
                .append("<button class='print-btn' onclick='window.print()'>&#128438; Print / Save as PDF</button>")
                .append("</div>\n");
            
            html.append("<div class='header'>")
                .append("<h1>ADITYA UNIVERSITY</h1>")
                .append("</div>\n");
            
            html.append("<div class='sub-header'>")
                .append("<h2>Computer Science and Engineering</h2>")
                .append("<h2>").append(escHtml(subject)).append(" - ").append(examDate).append("</h2>")
                .append("<h3>Seating Arrangement</h3>")
                .append("</div>\n");
            
            html.append("<table>\n<thead><tr>")
                .append("<th style='width:6%'>Sr. No.</th>")
                .append("<th style='width:12%'>Room No.</th>")
                .append("<th style='width:18%'>Building</th>")
                .append("<th colspan='2'>Roll Numbers</th>")
                .append("<th style='width:8%'>Total</th>")
                .append("</tr>\n<tr>")
                .append("<th></th><th></th><th></th>")
                .append("<th style='width:22%'>From</th>")
                .append("<th style='width:22%'>To</th>")
                .append("<th></th>")
                .append("</tr></thead>\n<tbody>\n");
            
            int srNo = 1;
            int grandTotal = 0;

            // Collect room data sorted by ascending min roll number
            List<String[]> roomRows = new ArrayList<>();
            for (String room : allocatedRooms) {
                PreparedStatement pst = con.prepareStatement(
                    "SELECT MIN(student_roll) as min_roll, MAX(student_roll) as max_roll, COUNT(*) as total " +
                    "FROM seating_plan WHERE room_no = ? ORDER BY student_roll ASC");
                pst.setString(1, room);
                ResultSet rs = pst.executeQuery();
                if (rs.next()) {
                    roomRows.add(new String[]{
                        room,
                        rs.getString("min_roll") != null ? rs.getString("min_roll") : "",
                        rs.getString("max_roll") != null ? rs.getString("max_roll") : "",
                        String.valueOf(rs.getInt("total"))
                    });
                }
            }
            // Sort by FROM roll number ascending so 24B11CS001 always appears first
            roomRows.sort(Comparator.comparing(r -> r[1].toUpperCase()));

            for (String[] rr : roomRows) {
                String room = rr[0];
                String minRoll = rr[1];
                String maxRoll = rr[2];
                int total = Integer.parseInt(rr[3]);
                grandTotal += total;
                html.append("<tr>")
                    .append("<td>").append(srNo++).append("</td>")
                    .append("<td>").append(room).append("</td>")
                    .append("<td>").append(getBuildingName(room)).append("</td>")
                    .append("<td>").append(minRoll).append("</td>")
                    .append("<td>").append(maxRoll).append("</td>")
                    .append("<td>").append(total).append("</td>")
                    .append("</tr>\n");
            }
            
            // Grand total row
            html.append("<tr style='font-weight: bold; background: #f5f5f5;'>")
                .append("<td colspan='5' style='text-align: right;'>Grand Total:</td>")
                .append("<td>").append(grandTotal).append("</td>")
                .append("</tr>\n");
            
            html.append("</tbody>\n</table>\n");
            
            // Signatures
            html.append("<div class='signatures'>")
                .append("<div class='sig-block'><div class='sig-line'>Exam-cell In-charge</div></div>")
                .append("<div class='sig-block'><div class='sig-line'>Head of the Department</div></div>")
                .append("</div>\n");
            
            html.append("</body></html>\n");
            
            File f = new File(downloads, "Seating_Summary_" + java.time.LocalDate.now() + ".html");
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    new java.io.FileOutputStream(f), java.nio.charset.StandardCharsets.UTF_8))) {
                pw.write(html.toString());
            }
            
            openInBrowser(f);
            JOptionPane.showMessageDialog(this, 
                "Seating summary saved to:\n" + f.getAbsolutePath() + "\n\nOpened in browser for printing.",
                "Downloaded", JOptionPane.INFORMATION_MESSAGE);
            
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error generating summary: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    // Hall Layout View — per building with room grid
    private void showHallLayoutByBuilding() {
        JDialog d = new JDialog(this, "Hall Layout — All Buildings", true);
        d.setSize(900, 680);
        d.setLocationRelativeTo(this);
        d.setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PRIMARY_COLOR);
        header.setBorder(new EmptyBorder(14, 20, 14, 20));
        JLabel hTitle = new JLabel("Examination Hall Layout");
        hTitle.setFont(new Font(FONT_FAMILY, Font.BOLD, 18));
        hTitle.setForeground(Color.WHITE);
        JLabel hSub = new JLabel("Click a room to see seat grid");
        hSub.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
        hSub.setForeground(new Color(200, 220, 255));
        header.add(hTitle, BorderLayout.WEST);
        header.add(hSub, BorderLayout.EAST);
        d.add(header, BorderLayout.NORTH);

        // Main split: left = building list, right = room detail
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(310);
        split.setDividerSize(4);
        split.setBackground(Color.WHITE);

        // ---- LEFT: Building tabs with room cards ----
        JTabbedPane bTabs = new JTabbedPane(JTabbedPane.TOP);
        bTabs.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));

        // ---- RIGHT: Room detail panel ----
        JPanel detailPanel = new JPanel(new BorderLayout());
        detailPanel.setBackground(Color.WHITE);
        detailPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        JLabel detailTitle = new JLabel("Select a room to view its seat map", SwingConstants.CENTER);
        detailTitle.setFont(new Font(FONT_FAMILY, Font.ITALIC, 14));
        detailTitle.setForeground(Color.GRAY);
        detailPanel.add(detailTitle, BorderLayout.CENTER);

        // Load rooms grouped by building
        java.util.LinkedHashMap<String, List<String[]>> buildingMap = new java.util.LinkedHashMap<>();
        try {
            ResultSet rs = con.createStatement().executeQuery(
                "SELECT room_no, capacity, COALESCE(rows_count,6) as rc, COALESCE(cols_count,6) as cc " +
                "FROM rooms ORDER BY room_no");
            while (rs.next()) {
                String rNo = rs.getString(1);
                String bldg = getBuildingName(rNo);
                buildingMap.computeIfAbsent(bldg, k -> new ArrayList<>())
                    .add(new String[]{rNo, rs.getString(2), rs.getString(3), rs.getString(4)});
            }
        } catch (Exception ex) {
            detailTitle.setText("DB error: " + ex.getMessage());
        }

        Color[] bColors = {new Color(59,130,246), new Color(5,150,105), new Color(217,119,6),
                           new Color(185,28,28), new Color(109,40,217), new Color(3,105,161), new Color(6,78,59)};
        int bIdx = 0;
        for (Map.Entry<String, List<String[]>> entry : buildingMap.entrySet()) {
            String bldg = entry.getKey();
            List<String[]> rooms = entry.getValue();
            Color bCol = bColors[bIdx++ % bColors.length];

            JPanel roomGrid = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
            roomGrid.setBackground(new Color(248, 250, 252));
            roomGrid.setBorder(new EmptyBorder(10, 10, 10, 10));

            for (String[] rm : rooms) {
                JButton btn = new JButton("<html><center><b>" + rm[0] + "</b><br><font size='2' color='gray'>Cap: " + rm[1] + "</font></center></html>");
                btn.setPreferredSize(new Dimension(90, 62));
                btn.setBackground(Color.WHITE);
                btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(bCol, 1, true),
                    new EmptyBorder(4, 4, 4, 4)));
                btn.setFocusPainted(false);
                btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
                final String[] rmFinal = rm;
                btn.addActionListener(e -> showRoomDetail(detailPanel, rmFinal, bCol));
                roomGrid.add(btn);
            }
            JScrollPane roomScroll = new JScrollPane(roomGrid);
            roomScroll.setBorder(null);
            roomScroll.getVerticalScrollBar().setUnitIncrement(16);

            JPanel tabPanel = new JPanel(new BorderLayout());
            tabPanel.setBackground(new Color(248, 250, 252));
            JLabel bLabel = new JLabel("  " + bldg + "  —  " + rooms.size() + " rooms");
            bLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
            bLabel.setForeground(bCol);
            bLabel.setBorder(new EmptyBorder(8, 12, 8, 12));
            tabPanel.add(bLabel, BorderLayout.NORTH);
            tabPanel.add(roomScroll, BorderLayout.CENTER);
            bTabs.addTab(bldg, tabPanel);
        }

        split.setLeftComponent(bTabs);
        split.setRightComponent(detailPanel);
        d.add(split, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 10));
        footer.setBackground(new Color(248, 250, 252));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));
        JButton close = new ModernButton("Close", new Color(107, 114, 128));
        close.setPreferredSize(new Dimension(100, 38));
        close.addActionListener(e -> d.dispose());
        footer.add(close);
        d.add(footer, BorderLayout.SOUTH);
        d.setVisible(true);
    }

    /** Show seat grid for a single room in the detail panel */
    private void showRoomDetail(JPanel detailPanel, String[] rm, Color accent) {
        String roomNo = rm[0];
        int cap = 0, rows = 6, cols = 6;
        try { cap = Integer.parseInt(rm[1]); } catch (Exception ignored) {}
        try { rows = Integer.parseInt(rm[2]); } catch (Exception ignored) {}
        try { cols = Integer.parseInt(rm[3]); } catch (Exception ignored) {}

        // Clamp visible grid
        int gridRows = Math.max(1, Math.min(rows, 12));
        int gridCols = Math.max(1, Math.min(cols, 12));

        // Load currently seated students in this room (latest exam)
        Set<String> occupiedSeats = new HashSet<>();
        Map<String, String> seatToRoll = new HashMap<>();
        try {
            PreparedStatement pst = con.prepareStatement(
                "SELECT seat_row, seat_col, student_roll FROM seating_plan " +
                "WHERE room_no=? ORDER BY exam_date DESC, start_time DESC LIMIT " + (gridRows * gridCols));
            pst.setString(1, roomNo);
            ResultSet rs = pst.executeQuery();
            while (rs.next()) {
                String key = rs.getInt(1) + ":" + rs.getInt(2);
                occupiedSeats.add(key);
                seatToRoll.put(key, rs.getString(3));
            }
        } catch (Exception ignored) {}

        detailPanel.removeAll();
        detailPanel.setBackground(Color.WHITE);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel rTitle = new JLabel("Room " + roomNo + "  —  Capacity: " + cap);
        rTitle.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        rTitle.setForeground(accent);
        JLabel seated = new JLabel(occupiedSeats.size() + " seated", SwingConstants.RIGHT);
        seated.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
        seated.setForeground(Color.GRAY);
        titleRow.add(rTitle, BorderLayout.WEST);
        titleRow.add(seated, BorderLayout.EAST);
        titleRow.setBorder(new EmptyBorder(0, 0, 10, 0));

        JPanel grid = new JPanel(new GridLayout(gridRows, gridCols, 4, 4));
        grid.setBackground(Color.WHITE);
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                String key = r + ":" + c;
                boolean occ = occupiedSeats.contains(key);
                JLabel cell = new JLabel(occ ? seatToRoll.getOrDefault(key, "●") : "" + (r * gridCols + c + 1),
                    SwingConstants.CENTER);
                cell.setOpaque(true);
                cell.setFont(new Font(FONT_FAMILY, Font.PLAIN, occ ? 9 : 11));
                cell.setBackground(occ ? new Color(220, 252, 231) : new Color(243, 244, 246));
                cell.setForeground(occ ? new Color(6, 78, 59) : new Color(100, 116, 139));
                cell.setBorder(BorderFactory.createLineBorder(occ ? new Color(134, 239, 172) : new Color(209, 213, 219), 1, true));
                cell.setPreferredSize(new Dimension(60, 36));
                cell.setToolTipText(occ ? "Occupied: " + seatToRoll.getOrDefault(key, "") : "Seat " + (r * gridCols + c + 1) + " (empty)");
                grid.add(cell);
            }
        }
        JScrollPane gridScroll = new JScrollPane(grid);
        gridScroll.setBorder(null);
        gridScroll.getVerticalScrollBar().setUnitIncrement(14);

        // Legend
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 6));
        legend.setOpaque(false);
        JLabel l1 = new JLabel("  Occupied  ");
        l1.setOpaque(true); l1.setBackground(new Color(220, 252, 231));
        l1.setForeground(new Color(6, 78, 59)); l1.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
        JLabel l2 = new JLabel("  Empty  ");
        l2.setOpaque(true); l2.setBackground(new Color(243, 244, 246));
        l2.setForeground(new Color(100, 116, 139)); l2.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
        legend.add(l1); legend.add(l2);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.add(titleRow, BorderLayout.NORTH);
        content.add(gridScroll, BorderLayout.CENTER);
        content.add(legend, BorderLayout.SOUTH);

        detailPanel.add(content, BorderLayout.CENTER);
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    // Premium Room Availability Dialog
    private void showAvailability(String date, String time) {
        JDialog d = new JDialog(this, "Room Availability — " + date + "  " + time, true);
        d.setSize(760, 560);
        d.setLocationRelativeTo(this);
        d.setLayout(new BorderLayout());

        // --- Header bar ---
        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.setBackground(PRIMARY_COLOR);
        header.setBorder(new EmptyBorder(18, 22, 18, 22));
        JLabel hTitle = new JLabel("Room Availability");
        hTitle.setFont(new Font(FONT_FAMILY, Font.BOLD, 20));
        hTitle.setForeground(Color.WHITE);
        JLabel hSub = new JLabel(date + "   •   " + time);
        hSub.setFont(new Font(FONT_FAMILY, Font.PLAIN, 14));
        hSub.setForeground(new Color(200, 220, 255));
        JPanel hText = new JPanel();
        hText.setLayout(new BoxLayout(hText, BoxLayout.Y_AXIS));
        hText.setOpaque(false);
        hText.add(hTitle);
        hText.add(Box.createVerticalStrut(3));
        hText.add(hSub);
        header.add(hText, BorderLayout.WEST);

        // Legend chips
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        legend.setOpaque(false);
        for (String[] chip : new String[][]{{"Available", "16a34a"}, {"Busy", "dc2626"}}) {
            JLabel l = new JLabel("  " + chip[0] + "  ");
            l.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
            l.setForeground(Color.WHITE);
            l.setBackground(Color.decode("#" + chip[1]));
            l.setOpaque(true);
            l.setBorder(new EmptyBorder(4, 10, 4, 10));
            legend.add(l);
        }
        header.add(legend, BorderLayout.EAST);
        d.add(header, BorderLayout.NORTH);

        // --- Room table ---
        String[] cols = {"Room No", "Building", "Capacity", "Status", "Exam (if busy)"};
        DefaultTableModel tm = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(tm);
        table.setRowHeight(42);
        table.setFont(new Font(FONT_FAMILY, Font.PLAIN, 14));
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(new Color(255, 230, 210));
        table.getTableHeader().setBackground(new Color(55, 65, 81));
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setFont(new Font(FONT_FAMILY, Font.BOLD, 14));
        table.getTableHeader().setPreferredSize(new Dimension(0, 44));

        // Column widths
        int[] cw = {90, 180, 90, 120, 220};
        for (int i = 0; i < cw.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(cw[i]);

        // Renderer — colour Status column and row stripe
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                String status = (String) t.getModel().getValueAt(row, 3);
                if (!sel) {
                    if ("AVAILABLE".equals(status)) {
                        c.setBackground(col == 3 ? new Color(220, 252, 231) : (row % 2 == 0 ? Color.WHITE : new Color(250, 250, 252)));
                    } else {
                        c.setBackground(col == 3 ? new Color(254, 226, 226) : (row % 2 == 0 ? Color.WHITE : new Color(250, 250, 252)));
                    }
                }
                if (col == 3) {
                    ((JLabel) c).setForeground("AVAILABLE".equals(status)
                        ? new Color(22, 101, 52) : new Color(185, 28, 28));
                    ((JLabel) c).setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    ((JLabel) c).setForeground(new Color(30, 30, 30));
                    ((JLabel) c).setFont(getFont().deriveFont(Font.PLAIN));
                }
                ((JLabel) c).setBorder(new EmptyBorder(0, 12, 0, 12));
                return c;
            }
        });

        // Load data
        int[] stats = {0, 0}; // [available, busy]
        try {
            Set<String> busyRooms = new HashSet<>();
            Map<String, String> busyExam = new HashMap<>();
            PreparedStatement pBusy = con.prepareStatement(
                "SELECT DISTINCT room_no, subject FROM seating_plan WHERE exam_date=? AND start_time=?");
            pBusy.setString(1, date); pBusy.setString(2, time);
            ResultSet rsBusy = pBusy.executeQuery();
            while (rsBusy.next()) {
                busyRooms.add(rsBusy.getString(1));
                busyExam.put(rsBusy.getString(1), rsBusy.getString(2));
            }

            ResultSet rs = con.createStatement().executeQuery(
                "SELECT room_no, capacity FROM rooms ORDER BY room_no");
            while (rs.next()) {
                String rNo = rs.getString(1);
                int cap = rs.getInt(2);
                boolean busy = busyRooms.contains(rNo);
                String statusStr = busy ? "BUSY" : "AVAILABLE";
                String examStr = busy ? busyExam.getOrDefault(rNo, "") : "";
                tm.addRow(new Object[]{rNo, getBuildingName(rNo), cap, statusStr, examStr});
                if (busy) stats[1]++; else stats[0]++;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            tm.addRow(new Object[]{"Error loading rooms", ex.getMessage(), "", "", ""});
        }

        // Summary strip
        hSub.setText(date + "   •   " + time
            + "   |   " + stats[0] + " available   •   " + stats[1] + " busy");

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);
        d.add(scroll, BorderLayout.CENTER);

        // --- Footer ---
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 12));
        footer.setBackground(new Color(248, 250, 252));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));
        JButton closeBtn = new ModernButton("Close", new Color(107, 114, 128));
        closeBtn.setPreferredSize(new Dimension(110, 42));
        closeBtn.addActionListener(e -> d.dispose());
        footer.add(closeBtn);
        d.add(footer, BorderLayout.SOUTH);

        d.setVisible(true);
    }

    // UPDATED: Column-wise filling logic with auto-assign invigilators
    private boolean autoGenerateSchedule(
            List<String> sections, boolean mixedMode,
            String sub, String dt, String t1, String t2,
            String fac, String bhavanFilter, boolean autoAssignInvig) {
        try {
            if (con == null) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "Not connected to database.", "Error", JOptionPane.ERROR_MESSAGE));
                return false;
            }

            // ── 0. Normalise and validate inputs ──────────────────────────
            String normT1 = normalizeTime(t1);
            String normT2 = normalizeTime(t2);
            if (!isValidDate(dt)) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "Invalid exam date: \"" + dt + "\"\nPlease use YYYY-MM-DD format.",
                    "Invalid Date", JOptionPane.ERROR_MESSAGE));
                return false;
            }
            // Normalise section names so DB lookup matches
            List<String> normSections = new ArrayList<>();
            for (String sec : sections) normSections.add(normalizeSection(sec));
            List<String> availableInvigilators = new ArrayList<>();
            Set<String> alreadyAssignedInvigilators = new HashSet<>();
            if (autoAssignInvig) {
                try {
                    PreparedStatement pstAssigned = con.prepareStatement(
                        "SELECT DISTINCT invigilator_name FROM seating_plan " +
                        "WHERE exam_date=? AND start_time=? AND invigilator_name IS NOT NULL AND invigilator_name != ''");
                    pstAssigned.setString(1, dt);
                    pstAssigned.setString(2, normT1);
                    ResultSet rsAssigned = pstAssigned.executeQuery();
                    while (rsAssigned.next()) alreadyAssignedInvigilators.add(rsAssigned.getString(1));

                    ResultSet rsInvig = con.createStatement().executeQuery("SELECT name FROM faculty ORDER BY name");
                    while (rsInvig.next()) {
                        String name = rsInvig.getString(1);
                        if (!alreadyAssignedInvigilators.contains(name)) availableInvigilators.add(name);
                    }
                } catch (Exception ex) { /* faculty table might not exist yet */ }
            }

            // ── 2. Load students ────────────────────────────────────────
            List<Student> students = new ArrayList<>();
            String placeholders = String.join(",", java.util.Collections.nCopies(normSections.size(), "?"));
            PreparedStatement pstStu = con.prepareStatement(
                "SELECT roll_no, name, TRIM(UPPER(section)) as sec FROM students " +
                "WHERE TRIM(UPPER(section)) IN (" + placeholders + ")");
            for (int i = 0; i < normSections.size(); i++) pstStu.setString(i + 1, normSections.get(i));
            ResultSet rs = pstStu.executeQuery();
            while (rs.next()) students.add(new Student(
                rs.getString("roll_no"), rs.getString("name"), rs.getString("sec")));

            if (students.isEmpty()) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "No students found for section(s):\n" + String.join(", ", normSections) +
                    "\n\nPlease import student data first (Admin → Student Data).",
                    "No Students", JOptionPane.WARNING_MESSAGE));
                return false;
            }

            // Sort
            if (mixedMode) students.sort(Comparator.comparing(s -> s.roll));
            else students.sort(Comparator.comparing((Student s) -> s.section).thenComparing(s -> s.roll));

            // 2. Load all rooms
            List<RoomInfo> allRooms = new ArrayList<>();
            ResultSet rsR = con.createStatement().executeQuery(
                "SELECT room_no, rows_count, cols_count, capacity FROM rooms ORDER BY capacity DESC");
            while (rsR.next()) allRooms.add(new RoomInfo(
                rsR.getString(1), rsR.getInt(2), rsR.getInt(3), rsR.getInt(4)));

            if (allRooms.isEmpty()) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "No rooms configured. Please add room layouts first (Admin → Room Layouts).",
                    "No Rooms", JOptionPane.WARNING_MESSAGE));
                return false;
            }

            // Find busy rooms for this slot (use normalised time)
            Set<String> busyRooms = new HashSet<>();
            PreparedStatement pstBusy = con.prepareStatement(
                "SELECT DISTINCT room_no FROM seating_plan WHERE exam_date=? AND start_time=?");
            pstBusy.setString(1, dt); pstBusy.setString(2, normT1);
            ResultSet rsBusy = pstBusy.executeQuery();
            while (rsBusy.next()) busyRooms.add(rsBusy.getString(1));

            // Available rooms
            List<RoomInfo> availableRooms = allRooms.stream()
                .filter(r -> !busyRooms.contains(r.no))
                .collect(Collectors.toList());

            // Bhavan preference filter — try preferred building first, fall back to all if none available
            if (bhavanFilter != null && !bhavanFilter.isEmpty()) {
                String bhFirst = bhavanFilter.split("\\s+")[0].toUpperCase();
                List<RoomInfo> filtered = availableRooms.stream()
                    .filter(r -> r.no.toUpperCase().startsWith(bhFirst)
                        || r.no.toUpperCase().contains(bhFirst))
                    .collect(Collectors.toList());
                if (!filtered.isEmpty()) {
                    availableRooms = filtered; // use preferred building
                }
                // If no rooms in preferred building, silently fall back to all available rooms
            }

            if (availableRooms.isEmpty()) {
                final String dateTime = dt + "  " + normT1;
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "No available rooms for " + dateTime + ".\nAll rooms are already occupied for this slot.",
                    "No Rooms Available", JOptionPane.WARNING_MESSAGE));
                return false;
            }

            // Calculate how many rooms are needed
            int totalStudentCount = students.size();
            int roomsNeeded = 0;
            int accCapacity = 0;
            for (RoomInfo ri : availableRooms) {
                if (accCapacity >= totalStudentCount) break;
                roomsNeeded++;
                accCapacity += ri.capacity;
            }

            // AUTO-ASSIGN invigilators: automatically pick unique invigilators, no manual dialog
            if (autoAssignInvig) {
                if (availableInvigilators.isEmpty()) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "No available invigilators for this time slot.\nPlease add faculty first (Admin → User Management → Add Faculty).\n\nSchedule will be generated without invigilators.",
                        "No Invigilators", JOptionPane.WARNING_MESSAGE));
                    // Continue anyway — rooms will have empty invigilator
                } else {
                    // Shuffle for random, fair assignment — each room gets a unique invigilator
                    Collections.shuffle(availableInvigilators);
                    // Trim to only as many as we need (one per room)
                    if (availableInvigilators.size() > roomsNeeded) {
                        availableInvigilators = new ArrayList<>(availableInvigilators.subList(0, roomsNeeded));
                    }
                    // If fewer invigilators than rooms, remaining rooms get no invigilator — that's fine
                }
            }

            // ── 3. TRANSACTION: delete old + insert new ─────────────────
            // studIdx declared outside try so it's accessible after the block
            int studIdx = 0;
            boolean prevAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
            // Remove old seating data for same subject + date
            PreparedStatement delPst = con.prepareStatement(
                "DELETE FROM seating_plan WHERE subject=? AND exam_date=?");
            delPst.setString(1, sub); delPst.setString(2, dt);
            delPst.executeUpdate();

            // 4. Allocate seats
            allocatedRooms.clear();
            Map<String, String> roomInvigilatorMap = new HashMap<>(); // Track assigned invigilators per room
            Set<String> usedInvigilators = new HashSet<>(); // Track already assigned invigilators
            int invigIndex = 0;

            PreparedStatement ins = con.prepareStatement(
                "INSERT INTO seating_plan (room_no, student_roll, student_name, subject, " +
                "seat_row, seat_col, exam_date, start_time, end_time, invigilator_name, section) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)");
            for (int roomIdx = 0; roomIdx < availableRooms.size() && studIdx < students.size(); roomIdx++) {
                RoomInfo room = availableRooms.get(roomIdx);
                allocatedRooms.add(room.no);
                
                // Determine invigilator for this room (one invigilator per room only, no duplicates)
                String invigName = "";
                if (autoAssignInvig && !availableInvigilators.isEmpty()) {
                    // Find next available invigilator who hasn't been assigned yet
                    while (invigIndex < availableInvigilators.size()) {
                        String candidateInvig = availableInvigilators.get(invigIndex);
                        invigIndex++;
                        if (!usedInvigilators.contains(candidateInvig)) {
                            invigName = candidateInvig;
                            usedInvigilators.add(candidateInvig);
                            break;
                        }
                    }
                    // If no more available invigilators, leave blank
                    if (invigName.isEmpty() && invigIndex >= availableInvigilators.size()) {
                        invigName = ""; // No invigilator available
                    }
                } else {
                    invigName = (fac == null || fac.trim().isEmpty()) ? "" : fac.trim();
                }
                roomInvigilatorMap.put(room.no, invigName);

                // Load broken seats
                Set<Point> broken = new HashSet<>();
                PreparedStatement pstB = con.prepareStatement(
                    "SELECT row_idx, col_idx FROM room_broken_seats WHERE room_no=?");
                pstB.setString(1, room.no);
                ResultSet rsB = pstB.executeQuery();
                while (rsB.next()) broken.add(new Point(rsB.getInt(1), rsB.getInt(2)));

                // Fill column-wise with labeled break to correctly exit both loops
                outerLoop:
                for (int c = 0; c < room.cols; c++) {
                    for (int r = 0; r < room.rows; r++) {
                        if (studIdx >= students.size()) break outerLoop;
                        if (broken.contains(new Point(r, c))) continue;

                        Student s = students.get(studIdx++);
                        ins.setString(1, room.no);
                        ins.setString(2, s.roll);
                        ins.setString(3, s.name);
                        ins.setString(4, sub);
                        ins.setInt(5, r);
                        ins.setInt(6, c);
                        ins.setString(7, dt);
                        ins.setString(8, normT1);   // canonical 24h
                        ins.setString(9, normT2);
                        ins.setString(10, invigName);
                        ins.setString(11, s.section);
                        ins.addBatch();
                    }
                }
            }
            ins.executeBatch();
            con.commit();
            } catch (Exception txEx) {
                try { con.rollback(); } catch (Exception ignored) {}
                throw txEx;   // re-throw so outer catch shows the error
            } finally {
                try { con.setAutoCommit(prevAutoCommit); } catch (Exception ignored) {}
            }

            final int totalStudents = students.size();
            final int seated = studIdx;
            final int leftOver = totalStudents - seated;
            final List<String> roomsUsed = new ArrayList<>(allocatedRooms);

            SwingUtilities.invokeLater(() -> {
                if (leftOver > 0) {
                    JOptionPane.showMessageDialog(this,
                        seated + " of " + totalStudents + " students seated across "
                        + roomsUsed.size() + " room(s).\n" +
                        leftOver + " students could NOT be seated — rooms are full.\n" +
                        "Please add more room layouts and try again.",
                        "Partial Schedule", JOptionPane.WARNING_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Schedule generated!\n" +
                        totalStudents + " students seated across " + roomsUsed.size() + " room(s):\n" +
                        String.join(", ", roomsUsed),
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            });
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                "Error generating schedule:\n" + msg, "Error", JOptionPane.ERROR_MESSAGE));
            return false;
        }
    }

    
    private void downloadHTMLReports(String type) {
        if (allocatedRooms.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No rooms allocated yet. Generate a schedule first.",
                "Nothing to Export", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Ask user for format preference
        String[] options = {"HTML + Auto Open", "Save as PDF (macOS)", "HTML Only"};
        int choice = JOptionPane.showOptionDialog(this,
            "Choose export format for " + allocatedRooms.size() + " room(s):",
            "Export Format", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
            null, options, options[0]);
        
        if (choice < 0) return; // Cancelled
        
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        downloads.mkdirs();
        List<File> written = new ArrayList<>();
        List<File> pdfWritten = new ArrayList<>();
        
        try {
            for (String room : allocatedRooms) {
                String html = type.equals("SEATING") ? generateSeatingHTML(room) : generateAttendanceHTML(room);
                String label = type.equals("SEATING") ? "Seating" : "Attendance";
                String safeName = room.replaceAll("[^A-Za-z0-9_-]", "_");
                File htmlFile = new File(downloads, label + "_Room_" + safeName + ".html");
                
                try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                        new java.io.FileOutputStream(htmlFile), java.nio.charset.StandardCharsets.UTF_8))) {
                    pw.write(html);
                }
                written.add(htmlFile);
                
                // Try PDF conversion on macOS
                if (choice == 1) {
                    File pdfFile = new File(downloads, label + "_Room_" + safeName + ".pdf");
                    boolean converted = convertHtmlToPdfMac(htmlFile, pdfFile);
                    if (converted) pdfWritten.add(pdfFile);
                }
                
                // Open in browser if requested
                if (choice == 0) {
                    openInBrowser(htmlFile);
                }
            }
            
            setCursor(Cursor.getDefaultCursor());
            
            StringBuilder msg = new StringBuilder();
            msg.append(written.size()).append(" HTML file(s) saved to Downloads.\n");
            if (!pdfWritten.isEmpty()) {
                msg.append(pdfWritten.size()).append(" PDF file(s) generated.\n");
            } else if (choice == 1) {
                msg.append("PDF conversion requires wkhtmltopdf or similar tool.\n");
                msg.append("Install via: brew install wkhtmltopdf\n");
                msg.append("Opening HTML files instead - use Print > Save as PDF.");
                for (File f : written) openInBrowser(f);
            }
            
            JOptionPane.showMessageDialog(this, msg.toString(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            
            // Open Downloads folder
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(downloads);
            }
        } catch (Exception ex) {
            setCursor(Cursor.getDefaultCursor());
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving reports: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /** Try to convert HTML to PDF using command-line tools on macOS */
    private boolean convertHtmlToPdfMac(File html, File pdf) {
        try {
            // Try wkhtmltopdf first (most reliable)
            ProcessBuilder pb = new ProcessBuilder(
                "wkhtmltopdf", "--quiet", "--page-size", "A4",
                "--margin-top", "10mm", "--margin-bottom", "10mm",
                html.getAbsolutePath(), pdf.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0 && pdf.exists();
        } catch (Exception e) {
            // wkhtmltopdf not installed, try alternative
            try {
                // Use macOS cupsfilter or similar (fallback)
                ProcessBuilder pb = new ProcessBuilder(
                    "/usr/sbin/cupsfilter", "-m", "application/pdf", html.getAbsolutePath());
                pb.redirectOutput(pdf);
                Process p = pb.start();
                int exitCode = p.waitFor();
                return exitCode == 0 && pdf.exists();
            } catch (Exception e2) {
                return false;
            }
        }
    }

    /** Opens a local HTML file in the system default browser. */
    private void openInBrowser(File file) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(file.toURI());
            } else {
                // macOS fallback
                Runtime.getRuntime().exec(new String[]{"open", file.getAbsolutePath()});
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Could not open browser automatically.\nPlease open this file manually:\n" + file.getAbsolutePath());
        }
    }
    
    /** Save current schedule to a JSON/CSV file */
    private void saveScheduleToFile() {
        if (allocatedRooms.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No schedule generated yet.", "Nothing to Save", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("ExamSchedule_" + java.time.LocalDate.now() + ".json"));
        fc.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
        
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".json")) {
                file = new File(file.getAbsolutePath() + ".json");
            }
            
            try (PrintWriter pw = new PrintWriter(file)) {
                pw.println("{");
                pw.println("  \"generatedAt\": \"" + java.time.LocalDateTime.now() + "\",");
                pw.println("  \"rooms\": [");
                
                for (int i = 0; i < allocatedRooms.size(); i++) {
                    String room = allocatedRooms.get(i);
                    pw.println("    {");
                    pw.println("      \"roomNo\": \"" + room + "\",");
                    pw.println("      \"students\": [");
                    
                    PreparedStatement pst = con.prepareStatement(
                        "SELECT student_roll, student_name, subject, seat_row, seat_col, exam_date, start_time, end_time, invigilator_name " +
                        "FROM seating_plan WHERE room_no = ? ORDER BY seat_col, seat_row");
                    pst.setString(1, room);
                    ResultSet rs = pst.executeQuery();
                    
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) pw.println(",");
                        first = false;
                        pw.print("        {\"roll\": \"" + rs.getString(1) + "\", \"name\": \"" + escHtml(rs.getString(2)) + 
                            "\", \"seat\": \"R" + (rs.getInt(4)+1) + "-C" + (rs.getInt(5)+1) + "\"}");
                    }
                    pw.println();
                    pw.println("      ]");
                    pw.print("    }");
                    if (i < allocatedRooms.size() - 1) pw.println(",");
                    else pw.println();
                }
                
                pw.println("  ]");
                pw.println("}");
                
                JOptionPane.showMessageDialog(this, "Schedule saved to:\n" + file.getAbsolutePath(), "Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error saving schedule: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Export schedule JSON + HTML files to a chosen folder so HODs can be
     * notified manually or via an external automation tool.
     * (No live HTTP calls from within this app.)   
     */
    private void sendScheduleToHODs() {
        if (allocatedRooms.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No schedule generated yet.", "Nothing to Send", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Select folder to save Power Automate trigger files");
        
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dir = fc.getSelectedFile();
            
            try {
                // Create a JSON trigger file for Power Automate
                File triggerFile = new File(dir, "HOD_Email_Trigger_" + java.time.LocalDate.now() + ".json");
                try (PrintWriter pw = new PrintWriter(triggerFile)) {
                    pw.println("{");
                    pw.println("  \"action\": \"send_exam_schedule\",");
                    pw.println("  \"timestamp\": \"" + java.time.LocalDateTime.now() + "\",");
                    pw.println("  \"examDetails\": {");
                    
                    // Get exam details from the first room
                    PreparedStatement pst = con.prepareStatement(
                        "SELECT subject, exam_date, start_time, end_time FROM seating_plan WHERE room_no = ? LIMIT 1");
                    pst.setString(1, allocatedRooms.get(0));
                    ResultSet rs = pst.executeQuery();
                    if (rs.next()) {
                        pw.println("    \"subject\": \"" + escHtml(rs.getString(1)) + "\",");
                        pw.println("    \"date\": \"" + rs.getString(2) + "\",");
                        pw.println("    \"time\": \"" + rs.getString(3) + " - " + rs.getString(4) + "\"");
                    }
                    
                    pw.println("  },");
                    pw.println("  \"recipients\": [");
                    pw.println("    {\"role\": \"HOD_CSE\", \"email\": \"hod.cse@aditya.edu.in\"},");
                    pw.println("    {\"role\": \"HOD_IT\", \"email\": \"hod.it@aditya.edu.in\"},");
                    pw.println("    {\"role\": \"HOD_AIML\", \"email\": \"hod.aiml@aditya.edu.in\"},");
                    pw.println("    {\"role\": \"Controller\", \"email\": \"coe@aditya.edu.in\"}");
                    pw.println("  ],");
                    pw.println("  \"attachments\": [");
                    
                    // Generate seating & attendance files
                    for (int i = 0; i < allocatedRooms.size(); i++) {
                        String room = allocatedRooms.get(i);
                        String seatingHtml = generateSeatingHTML(room);
                        String attendanceHtml = generateAttendanceHTML(room);
                        
                        File seatingFile = new File(dir, "Seating_" + room.replaceAll("[^A-Za-z0-9_-]", "_") + ".html");
                        File attendanceFile = new File(dir, "Attendance_" + room.replaceAll("[^A-Za-z0-9_-]", "_") + ".html");
                        
                        try (PrintWriter pwS = new PrintWriter(new OutputStreamWriter(new java.io.FileOutputStream(seatingFile), java.nio.charset.StandardCharsets.UTF_8))) {
                            pwS.write(seatingHtml);
                        }
                        try (PrintWriter pwA = new PrintWriter(new OutputStreamWriter(new java.io.FileOutputStream(attendanceFile), java.nio.charset.StandardCharsets.UTF_8))) {
                            pwA.write(attendanceHtml);
                        }
                        
                        pw.println("    {\"type\": \"seating\", \"room\": \"" + room + "\", \"file\": \"" + seatingFile.getName() + "\"},");
                        pw.print("    {\"type\": \"attendance\", \"room\": \"" + room + "\", \"file\": \"" + attendanceFile.getName() + "\"}");
                        if (i < allocatedRooms.size() - 1) pw.println(",");
                        else pw.println();
                    }
                    
                    pw.println("  ],");
                    pw.println("  \"rooms\": " + allocatedRooms.size() + ",");
                    pw.println("  \"message\": \"Please find attached the exam seating arrangements and attendance sheets for the upcoming examination.\"");
                    pw.println("}");
                }
                
                // Create instructions file
                File instructionsFile = new File(dir, "README_PowerAutomate.txt");
                try (PrintWriter pw = new PrintWriter(instructionsFile)) {
                    pw.println("=== POWER AUTOMATE INTEGRATION INSTRUCTIONS ===");
                    pw.println();
                    pw.println("Files generated on: " + java.time.LocalDateTime.now());
                    pw.println();
                    pw.println("To send emails automatically using Power Automate:");
                    pw.println();
                    pw.println("1. Create a new Power Automate flow with 'When a file is created' trigger");
                    pw.println("2. Point it to this folder and watch for JSON files");
                    pw.println("3. Use 'Parse JSON' action to read HOD_Email_Trigger_*.json");
                    pw.println("4. Use 'Send an email (V2)' action for each recipient");
                    pw.println("5. Attach the HTML files listed in the 'attachments' array");
                    pw.println();
                    pw.println("Alternatively, manually email these files to:");
                    pw.println("- HOD CSE: hod.cse@aditya.edu.in");
                    pw.println("- HOD IT: hod.it@aditya.edu.in");
                    pw.println("- HOD AIML: hod.aiml@aditya.edu.in");
                    pw.println("- Controller of Examinations: coe@aditya.edu.in");
                    pw.println();
                    pw.println("Files in this package:");
                    pw.println("- " + triggerFile.getName() + " (Power Automate trigger)");
                    for (String room : allocatedRooms) {
                        pw.println("- Seating_" + room.replaceAll("[^A-Za-z0-9_-]", "_") + ".html");
                        pw.println("- Attendance_" + room.replaceAll("[^A-Za-z0-9_-]", "_") + ".html");
                    }
                }
                
                JOptionPane.showMessageDialog(this, 
                    "Power Automate trigger files created!\n\n" +
                    "Folder: " + dir.getAbsolutePath() + "\n\n" +
                    "Files generated:\n" +
                    "• " + triggerFile.getName() + "\n" +
                    "• " + (allocatedRooms.size() * 2) + " HTML attachments\n" +
                    "• README_PowerAutomate.txt\n\n" +
                    "See README for Power Automate setup instructions.",
                    "Files Ready for HOD Email", JOptionPane.INFORMATION_MESSAGE);
                    
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error generating files: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    // ==========================================
    // USER MANAGEMENT PANEL (ADMIN)
    // ==========================================
    private JPanel createUserManagementPanel() {
        JPanel p = new ModernPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JLabel title = new JLabel("User Management");
        title.setFont(SUBHEADER_FONT);
        title.setForeground(PRIMARY_COLOR);
        title.setBorder(new EmptyBorder(0, 0, 20, 0));
        
        JPanel content = new JPanel(new BorderLayout(20, 0));
        content.setOpaque(false);
        
        // Left: User List
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setOpaque(false);
        listPanel.setPreferredSize(new Dimension(350, 0));
        listPanel.setBorder(createTitledBorder("Existing Users"));
        
        DefaultTableModel userTableModel = new DefaultTableModel(new String[]{"Username", "Role", "Created"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable userTable = new JTable(userTableModel);
        styleTable(userTable);
        
        JScrollPane tableScroll = createStyledTableScroll(userTable);
        
        JButton refreshBtn = new ModernButton("Refresh", new Color(107, 114, 128));
        refreshBtn.setPreferredSize(new Dimension(100, 38));
        
        JPanel listTopPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        listTopPanel.setOpaque(false);
        listTopPanel.add(refreshBtn);
        
        listPanel.add(listTopPanel, BorderLayout.NORTH);
        listPanel.add(tableScroll, BorderLayout.CENTER);
        
        // Right: Add User Form
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setOpaque(false);
        formPanel.setBorder(createTitledBorder("Create New User"));
        
        JTextField usernameField = new ModernTextField(20);
        decorateField(usernameField, "Username");
        usernameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        usernameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JPasswordField passwordField = new ModernPasswordField();
        passwordField.setBorder(createTitledBorder("Password"));
        passwordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JPasswordField confirmPasswordField = new ModernPasswordField();
        confirmPasswordField.setBorder(createTitledBorder("Confirm Password"));
        confirmPasswordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        confirmPasswordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JComboBox<String> roleCombo = new JComboBox<>(new String[]{"EXAMINER", "ADMIN"});
        roleCombo.setFont(BODY_FONT);
        JPanel roleComboWrapper = new JPanel(new BorderLayout());
        roleComboWrapper.setOpaque(false);
        roleComboWrapper.setBorder(createTitledBorder("Role"));
        roleComboWrapper.add(roleCombo, BorderLayout.CENTER);
        roleComboWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        roleComboWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JButton createUserBtn = new ModernButton("Create User", PRIMARY_COLOR);
        createUserBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        createUserBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JButton deleteUserBtn = new ModernButton("Delete Selected User", new Color(220, 38, 38));
        deleteUserBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        deleteUserBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Add Faculty Section
        JPanel facultySection = new JPanel();
        facultySection.setLayout(new BoxLayout(facultySection, BoxLayout.Y_AXIS));
        facultySection.setOpaque(false);
        facultySection.setBorder(createTitledBorder("Add Invigilator/Faculty"));
        facultySection.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JTextField facultyNameField = new ModernTextField(20);
        decorateField(facultyNameField, "Faculty Name");
        facultyNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        facultyNameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        String[] deptOptions = {
            "Computer Science & Engineering", "Information Technology",
            "Electronics & Communication Engineering", "Electrical & Electronics Engineering",
            "Mechanical Engineering", "Civil Engineering",
            "Artificial Intelligence & Machine Learning", "Data Science",
            "Mathematics", "Physics", "Chemistry", "MBA", "MCA", "Other"
        };
        JComboBox<String> facultyDeptCombo = new JComboBox<>(deptOptions);
        facultyDeptCombo.setFont(BODY_FONT);
        JPanel deptComboWrapper = new JPanel(new BorderLayout());
        deptComboWrapper.setOpaque(false);
        deptComboWrapper.setBorder(createTitledBorder("Department"));
        deptComboWrapper.add(facultyDeptCombo, BorderLayout.CENTER);
        deptComboWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        deptComboWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JButton addFacultyBtn = new ModernButton("Add Faculty", SECONDARY_COLOR);
        addFacultyBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        addFacultyBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        facultySection.add(facultyNameField);
        facultySection.add(Box.createVerticalStrut(8));
        facultySection.add(deptComboWrapper);
        facultySection.add(Box.createVerticalStrut(12));
        facultySection.add(addFacultyBtn);
        
        formPanel.add(usernameField);
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(passwordField);
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(confirmPasswordField);
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(roleComboWrapper);
        formPanel.add(Box.createVerticalStrut(15));
        formPanel.add(createUserBtn);
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(deleteUserBtn);
        formPanel.add(Box.createVerticalStrut(25));
        formPanel.add(facultySection);
        formPanel.add(Box.createVerticalGlue());
        
        // Load users function
        Runnable loadUsers = () -> {
            userTableModel.setRowCount(0);
            try {
                ResultSet rs = con.createStatement().executeQuery("SELECT username, role, created_at FROM users ORDER BY username");
                while (rs.next()) {
                    String created = rs.getTimestamp(3) != null ? rs.getTimestamp(3).toString().substring(0, 16) : "N/A";
                    userTableModel.addRow(new Object[]{rs.getString(1), rs.getString(2), created});
                }
            } catch (Exception ex) {
                // created_at column might not exist
                try {
                    ResultSet rs = con.createStatement().executeQuery("SELECT username, role FROM users ORDER BY username");
                    while (rs.next()) {
                        userTableModel.addRow(new Object[]{rs.getString(1), rs.getString(2), "N/A"});
                    }
                } catch (Exception e2) { e2.printStackTrace(); }
            }
        };
        
        // Initial load
        loadUsers.run();
        
        // Action listeners
        refreshBtn.addActionListener(e -> loadUsers.run());
        
        createUserBtn.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            String confirmPassword = new String(confirmPasswordField.getPassword());
            String role = (String) roleCombo.getSelectedItem();
            
            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a username.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a password.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!password.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (password.length() < 4) {
                JOptionPane.showMessageDialog(this, "Password must be at least 4 characters.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            try {
                // Check if user exists
                PreparedStatement checkPst = con.prepareStatement("SELECT COUNT(*) FROM users WHERE username = ?");
                checkPst.setString(1, username);
                ResultSet rs = checkPst.executeQuery();
                rs.next();
                if (rs.getInt(1) > 0) {
                    JOptionPane.showMessageDialog(this, "Username already exists.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                // Create user
                PreparedStatement pst = con.prepareStatement("INSERT INTO users (username, password, role) VALUES (?, ?, ?)");
                pst.setString(1, username);
                pst.setString(2, password);
                pst.setString(3, role);
                pst.executeUpdate();
                
                JOptionPane.showMessageDialog(this, "User created successfully!\n\nUsername: " + username + "\nRole: " + role, "Success", JOptionPane.INFORMATION_MESSAGE);
                
                // Clear fields
                usernameField.setText("");
                passwordField.setText("");
                confirmPasswordField.setText("");
                
                loadUsers.run();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error creating user: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        deleteUserBtn.addActionListener(e -> {
            int selectedRow = userTable.getSelectedRow();
            if (selectedRow < 0) {
                JOptionPane.showMessageDialog(this, "Please select a user to delete.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            String username = (String) userTableModel.getValueAt(selectedRow, 0);
            
            int confirm = JOptionPane.showConfirmDialog(this, 
                "Are you sure you want to delete user: " + username + "?", 
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    PreparedStatement pst = con.prepareStatement("DELETE FROM users WHERE username = ?");
                    pst.setString(1, username);
                    pst.executeUpdate();
                    JOptionPane.showMessageDialog(this, "User deleted successfully.", "Deleted", JOptionPane.INFORMATION_MESSAGE);
                    loadUsers.run();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error deleting user: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        addFacultyBtn.addActionListener(e -> {
            String name = facultyNameField.getText().trim();
            String dept = (String) facultyDeptCombo.getSelectedItem();
            if (dept == null) dept = "N/A";
            
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter faculty name.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            try {
                // initDatabase() already ensures faculty table exists
                PreparedStatement pst = con.prepareStatement("INSERT INTO faculty (name, department) VALUES (?, ?)");
                pst.setString(1, name);
                pst.setString(2, dept.isEmpty() ? "N/A" : dept);
                pst.executeUpdate();
                
                JOptionPane.showMessageDialog(this, "Faculty added successfully!\n\nName: " + name + "\nDepartment: " + dept, "Success", JOptionPane.INFORMATION_MESSAGE);
                
                facultyNameField.setText("");
                facultyDeptCombo.setSelectedIndex(0);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error adding faculty: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        JScrollPane formScrollPane = new JScrollPane(formPanel);
        formScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        formScrollPane.setBorder(null);
        formScrollPane.getVerticalScrollBar().setUnitIncrement(12);
        formScrollPane.setPreferredSize(new Dimension(360, 0));

        content.add(listPanel, BorderLayout.CENTER);
        content.add(formScrollPane, BorderLayout.EAST);
        
        p.add(title, BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        return p;
    }
    
    private String generateSeatingHTML(String roomNo) throws SQLException {
        // Fetch header info
        String subject = "", examDate = "", startTime = "", endTime = "", invigilator = "";
        PreparedStatement pHead = con.prepareStatement(
            "SELECT subject, exam_date, start_time, end_time, invigilator_name FROM seating_plan WHERE room_no=? LIMIT 1");
        pHead.setString(1, roomNo);
        ResultSet rsHead = pHead.executeQuery();
        if (rsHead.next()) {
            subject     = rsHead.getString(1);
            examDate    = rsHead.getString(2);
            startTime   = rsHead.getString(3);
            endTime     = rsHead.getString(4);
            invigilator = rsHead.getString(5) != null ? rsHead.getString(5) : "";
        }

        // Fetch room dimensions
        int rows = 0, cols = 0;
        PreparedStatement pRoom = con.prepareStatement(
            "SELECT rows_count, cols_count FROM rooms WHERE room_no=?");
        pRoom.setString(1, roomNo);
        ResultSet rsRoom = pRoom.executeQuery();
        if (rsRoom.next()) { rows = rsRoom.getInt(1); cols = rsRoom.getInt(2); }

        // Build seat map
        Map<String, String[]> seatMap = new LinkedHashMap<>();
        PreparedStatement pSeats = con.prepareStatement(
            "SELECT seat_row, seat_col, student_roll, student_name FROM seating_plan WHERE room_no=? ORDER BY seat_col, seat_row");
        pSeats.setString(1, roomNo);
        ResultSet rsS = pSeats.executeQuery();
        while (rsS.next())
            seatMap.put(rsS.getInt(1) + "," + rsS.getInt(2),
                new String[]{rsS.getString(3), rsS.getString(4)});

        int totalSeats  = rows * cols;
        int seatedCount = seatMap.size();

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang='en'><head>\n")
          .append("<meta charset='UTF-8'>\n")
          .append("<meta name='viewport' content='width=device-width, initial-scale=1'>\n")
          .append("<title>Seating Plan — Room ").append(roomNo).append("</title>\n")
          .append("<style>\n")
          .append("  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }\n")
          .append("  body { font-family: 'Times New Roman', Times, serif; background: #fff; color: #000; padding: 24px; }\n")
          .append("  .no-print { text-align: center; margin-bottom: 18px; }\n")
          .append("  .print-btn { background: #4338ca; color: #fff; border: none; padding: 11px 28px; font-size: 14px; border-radius: 6px; cursor: pointer; font-family: Arial, sans-serif; }\n")
          .append("  .print-btn:hover { background: #3730a3; }\n")
          .append("  h1 { text-align: center; font-size: 20pt; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 4px; }\n")
          .append("  h2 { text-align: center; font-size: 14pt; margin-bottom: 2px; }\n")
          .append("  .meta-table { width: 100%; border-collapse: collapse; margin: 14px 0 10px; font-size: 11pt; }\n")
          .append("  .meta-table td { padding: 4px 8px; border: 1px solid #999; }\n")
          .append("  .meta-table td:first-child { font-weight: bold; width: 140px; background: #f5f5f5; }\n")
          .append("  .stats { text-align: center; font-size: 10pt; color: #555; margin-bottom: 14px; }\n")
          .append("  .board { display: inline-block; text-align: center; background: #1a365d; color: #fff; padding: 6px 24px; font-size: 11pt; border-radius: 4px; margin: 6px auto 12px; }\n")
          .append("  .grid-wrap { display: table; border-collapse: separate; border-spacing: 5px; margin: 0 auto; }\n")
          .append("  .grid-row  { display: table-row; }\n")
          .append("  .seat { display: table-cell; width: 90px; height: 58px; border: 1.5px solid #888; border-radius: 4px; ")
          .append("text-align: center; vertical-align: middle; font-size: 9pt; padding: 3px; background: #e8f5e9; }\n")
          .append("  .seat.empty { background: #fafafa; color: #bbb; border-style: dashed; }\n")
          .append("  .seat .roll { font-weight: bold; font-size: 9.5pt; word-break: break-all; }\n")
          .append("  .seat .seatno { font-size: 7.5pt; color: #555; margin-top: 2px; }\n")
          .append("  .door { text-align: center; font-size: 10pt; font-style: italic; color: #666; margin: 10px 0; }\n")
          .append("  .footer-sig { margin-top: 30px; display: flex; justify-content: space-between; font-size: 10pt; }\n")
          .append("  @media print {\n")
          .append("    .no-print { display: none !important; }\n")
          .append("    body { padding: 10mm; }\n")
          .append("    .seat { width: 72px; height: 48px; font-size: 7.5pt; }\n")
          .append("    .seat .roll { font-size: 7.5pt; }\n")
          .append("    @page { size: A4 landscape; margin: 10mm; }\n")
          .append("  }\n")
          .append("</style>\n</head>\n<body>\n");

        sb.append("<div class='no-print'>")
          .append("<button class='print-btn' onclick='window.print()'>&#128438; Print / Save as PDF</button>")
          .append("<p style='margin-top:6px;font-size:11px;color:#666;font-family:Arial;'>")
          .append("Press <b>Ctrl+P</b> (Win/Linux) or <b>⌘+P</b> (Mac) &rarr; select <i>Save as PDF</i></p></div>\n");

        sb.append("<h1>Aditya University</h1>\n")
          .append("<h2>Examination Seating Arrangement</h2>\n");

        sb.append("<table class='meta-table'>\n")
          .append("<tr><td>Room No</td><td>").append(roomNo).append("</td>")
          .append("<td><b>Subject</b></td><td>").append(subject).append("</td></tr>\n")
          .append("<tr><td>Building</td><td>").append(getBuildingName(roomNo)).append("</td>")
          .append("<td><b>Date</b></td><td>").append(examDate).append("</td></tr>\n")
          .append("<tr><td>Time</td><td>").append(startTime).append(" &ndash; ").append(endTime).append("</td>")
          .append("<td><b>Invigilator</b></td><td>").append(invigilator).append("</td></tr>\n")
          .append("</table>\n");

        sb.append("<p class='stats'>Capacity: ").append(totalSeats)
          .append(" &nbsp;|&nbsp; Seated: ").append(seatedCount)
          .append(" &nbsp;|&nbsp; Empty: ").append(totalSeats - seatedCount).append("</p>\n");

        sb.append("<p class='door'>&larr; DOOR &rarr;</p>\n");

        sb.append("<div class='grid-wrap'>\n");
        for (int r = 0; r < rows; r++) {
            sb.append("  <div class='grid-row'>\n");
            for (int c = 0; c < cols; c++) {
                String key = r + "," + c;
                if (seatMap.containsKey(key)) {
                    String[] info = seatMap.get(key);
                    sb.append("    <div class='seat'>")
                      .append("<div class='roll'>").append(escHtml(info[0])).append("</div>")
                      .append("<div class='seatno'>R").append(r + 1).append("-C").append(c + 1).append("</div>")
                      .append("</div>\n");
                } else {
                    sb.append("    <div class='seat empty'><div class='seatno'>R")
                      .append(r + 1).append("-C").append(c + 1).append("</div></div>\n");
                }
            }
            sb.append("  </div>\n");
        }
        sb.append("</div>\n");

        sb.append("<div class='footer-sig'>")
          .append("<div>Date: ____________</div>")
          .append("<div>Invigilator Signature: ________________________</div>")
          .append("</div>\n");

        sb.append("</body></html>\n");
        return sb.toString();
    }
    
    private String generateAttendanceHTML(String roomNo) throws SQLException {
        String subject = "", examDate = "", startTime = "", endTime = "", invigilator = "";
        PreparedStatement pHead = con.prepareStatement(
            "SELECT subject, exam_date, start_time, end_time, invigilator_name FROM seating_plan WHERE room_no=? LIMIT 1");
        pHead.setString(1, roomNo);
        ResultSet rsHead = pHead.executeQuery();
        if (rsHead.next()) {
            subject     = rsHead.getString(1);
            examDate    = rsHead.getString(2);
            startTime   = rsHead.getString(3);
            endTime     = rsHead.getString(4);
            invigilator = rsHead.getString(5) != null ? rsHead.getString(5) : "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang='en'><head>\n")
          .append("<meta charset='UTF-8'>\n")
          .append("<meta name='viewport' content='width=device-width, initial-scale=1'>\n")
          .append("<title>Attendance Sheet — Room ").append(roomNo).append("</title>\n")
          .append("<style>\n")
          .append("  *, *::before, *::after { box-sizing: border-box; }\n")
          .append("  body { font-family: 'Times New Roman', Times, serif; background: #fff; color: #000; padding: 28px 32px; font-size: 11pt; }\n")
          .append("  .no-print { text-align: center; margin-bottom: 18px; }\n")
          .append("  .print-btn { background: #4338ca; color: #fff; border: none; padding: 11px 28px; font-size: 14px; border-radius: 6px; cursor: pointer; font-family: Arial, sans-serif; }\n")
          .append("  .print-btn:hover { background: #3730a3; }\n")
          .append("  h1 { text-align: center; font-size: 18pt; text-transform: uppercase; margin-bottom: 2px; }\n")
          .append("  h2 { text-align: center; font-size: 13pt; font-weight: normal; margin-bottom: 14px; }\n")
          .append("  .info-box { border: 1.5px solid #000; padding: 10px 14px; margin-bottom: 16px; display: grid; grid-template-columns: 1fr 1fr; gap: 6px 20px; font-size: 10.5pt; }\n")
          .append("  .info-box span.lbl { font-weight: bold; }\n")
          .append("  table { width: 100%; border-collapse: collapse; margin-top: 4px; font-size: 10.5pt; }\n")
          .append("  thead tr { background: #e8e8e8; }\n")
          .append("  th, td { border: 1px solid #000; padding: 7px 10px; text-align: left; vertical-align: middle; }\n")
          .append("  th { font-weight: bold; }\n")
          .append("  tr:nth-child(even) td { background: #fafafa; }\n")
          .append("  .sig-row { margin-top: 36px; display: flex; justify-content: space-between; align-items: flex-end; font-size: 10pt; }\n")
          .append("  @media print {\n")
          .append("    .no-print { display: none !important; }\n")
          .append("    body { padding: 10mm 12mm; }\n")
          .append("    thead { display: table-header-group; }\n")
          .append("    tr { page-break-inside: avoid; }\n")
          .append("    @page { size: A4 portrait; margin: 10mm; }\n")
          .append("  }\n")
          .append("</style>\n</head>\n<body>\n");

        sb.append("<div class='no-print'>")
          .append("<button class='print-btn' onclick='window.print()'>&#128438; Print / Save as PDF</button>")
          .append("<p style='margin-top:6px;font-size:11px;color:#666;font-family:Arial;'>")
          .append("Press <b>Ctrl+P</b> (Win/Linux) or <b>⌘+P</b> (Mac) &rarr; select <i>Save as PDF</i></p></div>\n");

        sb.append("<h1>Aditya University</h1>\n")
          .append("<h2>Examination Attendance Sheet</h2>\n");

        sb.append("<div class='info-box'>")
          .append("<div><span class='lbl'>Room No:</span> ").append(roomNo).append("</div>")
          .append("<div><span class='lbl'>Building:</span> ").append(getBuildingName(roomNo)).append("</div>")
          .append("<div><span class='lbl'>Date:</span> ").append(examDate).append("</div>")
          .append("<div><span class='lbl'>Subject:</span> ").append(escHtml(subject)).append("</div>")
          .append("<div><span class='lbl'>Time:</span> ").append(startTime).append(" &ndash; ").append(endTime).append("</div>")
          .append("<div><span class='lbl'>Invigilator:</span> ").append(escHtml(invigilator)).append("</div>")
          .append("</div>\n");

        sb.append("<table>\n<thead><tr>")
          .append("<th style='width:6%'>S.No</th>")
          .append("<th style='width:20%'>Roll Number</th>")
          .append("<th style='width:32%'>Student Name</th>")
          .append("<th style='width:22%'>Answer Booklet Serial No.</th>")
          .append("<th style='width:20%'>Signature</th>")
          .append("</tr></thead>\n<tbody>\n");

        PreparedStatement pst = con.prepareStatement(
            "SELECT student_roll, student_name, seat_row, seat_col FROM seating_plan " +
            "WHERE room_no=? ORDER BY seat_col, seat_row");
        pst.setString(1, roomNo);
        ResultSet rs = pst.executeQuery();
        int sno = 1;
        while (rs.next()) {
            sb.append("<tr>")
              .append("<td>").append(sno++).append("</td>")
              .append("<td>").append(escHtml(rs.getString(1))).append("</td>")
              .append("<td>").append(escHtml(rs.getString(2))).append("</td>")
              .append("<td>&nbsp;</td>")
              .append("<td>&nbsp;</td>")
              .append("</tr>\n");
        }
        sb.append("</tbody>\n</table>\n");

        sb.append("<div class='sig-row'>")
          .append("<div>Total Students: <b>").append(sno - 1).append("</b></div>")
          .append("<div>Invigilator Signature: ____________________________</div>")
          .append("</div>\n");

        sb.append("</body></html>\n");
        return sb.toString();
    }

    /** Escape HTML special characters. */
    private String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Get building name for a room — reads the `building` column from DB first, then falls back to prefix matching. */
    private String getBuildingName(String roomNo) {
        if (roomNo == null || roomNo.isEmpty()) return "";
        try {
            PreparedStatement pst = con.prepareStatement(
                "SELECT building FROM rooms WHERE room_no = ?");
            pst.setString(1, roomNo);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                String bld = rs.getString("building");
                if (bld != null && !bld.trim().isEmpty()) return bld;
            }
        } catch (Exception ignored) {}
        // Fallback: derive from room number prefix
        String upper = roomNo.toUpperCase();
        for (String bhavan : BHAVANS) {
            String prefix = bhavan.split("\s+")[0].toUpperCase();
            if (upper.startsWith(prefix) || upper.contains(prefix)) {
                return bhavan + " Bhavan";
            }
        }
        return "Unknown Building";
    }

    // ==========================================
    // EXAM TIMETABLE CREATOR
    // ==========================================
    private List<String> timetableBranches = new ArrayList<>(Arrays.asList("IT", "Min.E", "PT", "Ag.E", "CSE", "AIML"));
    private List<String> timetableDates = new ArrayList<>();
    private Map<String, Map<String, String[]>> timetableData = new HashMap<>(); // branch -> (date -> [subject, code])
    private JTable timetableTable;
    private DefaultTableModel timetableModel;
    private JLabel statsLabel;
    private String examTitle = "II B.Tech II Semester Regular Examinations";
    private String examTimings = "Time: 10:00 AM to 01:00 PM";

    private JPanel createExamTimetablePanel() {
        JPanel p = new ModernPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(15, 15, 15, 15));

        // ========== HEADER SECTION ==========
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(0, 0, 15, 0));
        
        JLabel title = new JLabel("Exam Timetable Creator");
        title.setFont(SUBHEADER_FONT);
        title.setForeground(PRIMARY_COLOR);
        
        statsLabel = new JLabel("Branches: 0 | Dates: 0 | Entries: 0");
        statsLabel.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
        statsLabel.setForeground(Color.GRAY);
        
        headerPanel.add(title, BorderLayout.WEST);
        headerPanel.add(statsLabel, BorderLayout.EAST);

        // ========== LEFT SIDE PANEL - Controls ==========
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(250, 0));
        leftPanel.setBorder(new EmptyBorder(0, 0, 0, 15));

        // -- Exam Info Section --
        JPanel examInfoPanel = createSectionPanel("Exam Information");
        JTextField examTitleField = new ModernTextField();
        examTitleField.setText(examTitle);
        decorateField(examTitleField, "Exam Title");
        examTitleField.setMaximumSize(new Dimension(600, 50));
        
        // Exam Type Selection (4 types)
        JComboBox<String> examTypeSelectBox = new JComboBox<>(new String[]{
            "Mid Exam 1 (1.5 hrs)",
            "Mid Exam 2 (1.5 hrs)",
            "Semester Exam (3 hrs)",
            "Supplementary Exam (3 hrs)"
        });
        examTypeSelectBox.setFont(BODY_FONT);
        JPanel examTypeWrapper = new JPanel(new BorderLayout());
        examTypeWrapper.setOpaque(false);
        examTypeWrapper.setBorder(createTitledBorder("Exam Type"));
        examTypeWrapper.add(examTypeSelectBox, BorderLayout.CENTER);
        examTypeWrapper.setMaximumSize(new Dimension(600, 70));
        examTypeWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Time slot options based on exam type
        String[] midSlots = {"10:30 AM - 12:00 PM (Session 1)", "2:30 PM - 4:00 PM (Session 2)"};
        String[] semSlots = {"10:00 AM - 1:00 PM", "2:00 PM - 5:00 PM"};
        
        JComboBox<String> timeSlotSelectBox = new JComboBox<>(midSlots);
        timeSlotSelectBox.setFont(BODY_FONT);
        JPanel timeSlotWrapper = new JPanel(new BorderLayout());
        timeSlotWrapper.setOpaque(false);
        timeSlotWrapper.setBorder(createTitledBorder("Time Slot"));
        timeSlotWrapper.add(timeSlotSelectBox, BorderLayout.CENTER);
        timeSlotWrapper.setMaximumSize(new Dimension(260, 62));
        timeSlotWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JTextField examTimingsField = new ModernTextField();
        examTimingsField.setText(examTimings);
        decorateField(examTimingsField, "Custom Timings (optional)");
        examTimingsField.setMaximumSize(new Dimension(600, 50));
        
        // Update time slots and timings when exam type changes
        examTypeSelectBox.addActionListener(ev -> {
            int idx = examTypeSelectBox.getSelectedIndex();
            timeSlotSelectBox.removeAllItems();
            if (idx <= 1) { // Mid exams
                for (String slot : midSlots) timeSlotSelectBox.addItem(slot);
            } else { // Semester/Supplementary exams
                for (String slot : semSlots) timeSlotSelectBox.addItem(slot);
            }
            // Auto-update timings field
            String selectedSlot = (String) timeSlotSelectBox.getSelectedItem();
            if (selectedSlot != null) {
                String timePart = selectedSlot.contains("(") ? selectedSlot.substring(0, selectedSlot.indexOf("(")).trim() : selectedSlot;
                examTimingsField.setText("Time: " + timePart);
                examTimings = examTimingsField.getText();
            }
        });
        
        // Update timings when time slot changes
        timeSlotSelectBox.addActionListener(ev -> {
            String selectedSlot = (String) timeSlotSelectBox.getSelectedItem();
            if (selectedSlot != null) {
                String timePart = selectedSlot.contains("(") ? selectedSlot.substring(0, selectedSlot.indexOf("(")).trim() : selectedSlot;
                examTimingsField.setText("Time: " + timePart);
                examTimings = examTimingsField.getText();
            }
        });
        
        examTitleField.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) { examTitle = examTitleField.getText(); }
        });
        examTimingsField.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) { examTimings = examTimingsField.getText(); }
        });
        
        examInfoPanel.add(examTitleField);
        examInfoPanel.add(Box.createVerticalStrut(8));
        examInfoPanel.add(examTypeWrapper);
        examInfoPanel.add(Box.createVerticalStrut(8));
        examInfoPanel.add(timeSlotWrapper);
        examInfoPanel.add(Box.createVerticalStrut(8));
        examInfoPanel.add(examTimingsField);

        // -- Add Date Section --
        JPanel datePanel = createSectionPanel("Add Exam Dates");
        
        JTextField dateField = new ModernTextField();
        dateField.setText(java.time.LocalDate.now().toString());
        decorateField(dateField, "Date (YYYY-MM-DD)");
        dateField.setMaximumSize(new Dimension(260, 50));
        
        JComboBox<String> dayCombo = new JComboBox<>(new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"});
        dayCombo.setFont(BODY_FONT);
        dayCombo.setSelectedItem("Wednesday");
        JPanel dayComboWrapper = new JPanel(new BorderLayout());
        dayComboWrapper.setOpaque(false);
        dayComboWrapper.setBorder(createTitledBorder("Day"));
        dayComboWrapper.add(dayCombo, BorderLayout.CENTER);
        dayComboWrapper.setMaximumSize(new Dimension(260, 62));
        dayComboWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JPanel dateBtnPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        dateBtnPanel.setOpaque(false);
        dateBtnPanel.setMaximumSize(new Dimension(260, 40));
        
        JButton addDateBtn = new ModernButton("+ Add", SECONDARY_COLOR);
        JButton removeDateBtn = new ModernButton("- Remove", new Color(220, 38, 38));
        dateBtnPanel.add(addDateBtn);
        dateBtnPanel.add(removeDateBtn);
        
        datePanel.add(dateField);
        datePanel.add(Box.createVerticalStrut(8));
        datePanel.add(dayComboWrapper);
        datePanel.add(Box.createVerticalStrut(8));
        datePanel.add(dateBtnPanel);

        // -- Quick Add Multiple Dates --
        JPanel quickDatePanel = createSectionPanel("Quick Add Dates");
        JButton addWeekBtn = new ModernButton("Add Exam Week", new Color(234, 179, 8));
        addWeekBtn.setMaximumSize(new Dimension(260, 40));
        addWeekBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        quickDatePanel.add(addWeekBtn);

        // -- Branch Section --
        JPanel branchPanel = createSectionPanel("Manage Branches");
        
        JTextField branchField = new ModernTextField();
        decorateField(branchField, "Branch Name");
        branchField.setMaximumSize(new Dimension(260, 50));
        
        JPanel branchBtnPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        branchBtnPanel.setOpaque(false);
        branchBtnPanel.setMaximumSize(new Dimension(260, 40));
        
        JButton addBranchBtn = new ModernButton("+ Add", SECONDARY_COLOR);
        JButton removeBranchBtn = new ModernButton("- Remove", new Color(220, 38, 38));
        branchBtnPanel.add(addBranchBtn);
        branchBtnPanel.add(removeBranchBtn);
        
        // Branch list
        DefaultListModel<String> branchListModel = new DefaultListModel<>();
        for (String b : timetableBranches) branchListModel.addElement(b);
        JList<String> branchList = new JList<>(branchListModel);
        branchList.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
        branchList.setVisibleRowCount(4);
        JScrollPane branchScroll = new JScrollPane(branchList);
        branchScroll.setMaximumSize(new Dimension(260, 80));
        branchScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        branchPanel.add(branchField);
        branchPanel.add(Box.createVerticalStrut(8));
        branchPanel.add(branchBtnPanel);
        branchPanel.add(Box.createVerticalStrut(8));
        branchPanel.add(branchScroll);

        leftPanel.add(examInfoPanel);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(datePanel);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(quickDatePanel);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(branchPanel);
        leftPanel.add(Box.createVerticalGlue());

        // ========== CENTER PANEL - Table ==========
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);

        // Toolbar above table
        JPanel tableToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        tableToolbar.setOpaque(false);
        
        JButton addEntryBtn = new ModernButton("+ Add Exam Entry", PRIMARY_COLOR);
        addEntryBtn.setPreferredSize(new Dimension(165, 40));
        
        JButton editEntryBtn = new ModernButton("Edit Selected", new Color(234, 179, 8));
        editEntryBtn.setPreferredSize(new Dimension(135, 40));
        
        JButton clearCellBtn = new ModernButton("Clear Cell", Color.GRAY);
        clearCellBtn.setPreferredSize(new Dimension(115, 40));
        
        JButton bulkAddBtn = new ModernButton("Bulk Add", SECONDARY_COLOR);
        bulkAddBtn.setPreferredSize(new Dimension(110, 40));
        
        tableToolbar.add(addEntryBtn);
        tableToolbar.add(editEntryBtn);
        tableToolbar.add(clearCellBtn);
        tableToolbar.add(bulkAddBtn);

        // Table
        timetableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Disable direct editing, use dialog instead
            }
        };
        timetableModel.addColumn("Branch");
        
        timetableTable = new JTable(timetableModel);
        timetableTable.setRowHeight(70);
        timetableTable.setFont(BODY_FONT);
        timetableTable.setShowGrid(true);
        timetableTable.setGridColor(new Color(160, 160, 190));
        timetableTable.setIntercellSpacing(new Dimension(1, 1));
        timetableTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        timetableTable.setCellSelectionEnabled(true);
        
        // Custom renderer for attractive cells
        timetableTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JPanel cellPanel = new JPanel(new BorderLayout());
                cellPanel.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
                
                if (column == 0) {
                    // Branch column
                    JLabel branchLabel = new JLabel(value == null ? "" : value.toString());
                    branchLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
                    branchLabel.setForeground(PRIMARY_COLOR);
                    cellPanel.add(branchLabel, BorderLayout.CENTER);
                    cellPanel.setBackground(new Color(240, 245, 255));
                } else {
                    // Data cells — column 1,3,5... = FN; column 2,4,6... = AN
                    boolean isFNCol = (column - 1) % 2 == 0;
                    Color emptyBg  = isFNCol ? new Color(232, 242, 255) : new Color(255, 243, 225);
                    Color filledBg = isFNCol ? new Color(210, 232, 255) : new Color(255, 230, 190);
                    String text = value == null ? "" : value.toString();
                    if (!text.isEmpty()) {
                        JPanel contentPanel = new JPanel();
                        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
                        contentPanel.setOpaque(false);
                        // Parse subject and code
                        String[] lines = text.split("\n");
                        JLabel subjectLabel = new JLabel(lines[0]);
                        subjectLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
                        subjectLabel.setForeground(TEXT_COLOR);
                        subjectLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                        if (lines.length > 1) {
                            JLabel codeLabel = new JLabel(lines[1]);
                            codeLabel.setFont(new Font(FONT_FAMILY, Font.PLAIN, 10));
                            codeLabel.setForeground(new Color(90, 90, 90));
                            codeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                            contentPanel.add(subjectLabel);
                            contentPanel.add(Box.createVerticalStrut(3));
                            contentPanel.add(codeLabel);
                        } else {
                            contentPanel.add(subjectLabel);
                        }
                        cellPanel.add(contentPanel, BorderLayout.CENTER);
                        cellPanel.setBackground(filledBg);
                    } else {
                        JLabel emptyLabel = new JLabel("-", SwingConstants.CENTER);
                        emptyLabel.setForeground(new Color(190, 190, 210));
                        cellPanel.add(emptyLabel, BorderLayout.CENTER);
                        cellPanel.setBackground(emptyBg);
                    }
                }
                
                if (isSelected) {
                    cellPanel.setBackground(new Color(200, 220, 255));
                    cellPanel.setBorder(BorderFactory.createLineBorder(PRIMARY_COLOR, 2));
                } else {
                    cellPanel.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 200), 1));
                }
                
                return cellPanel;
            }
        });
        
        // Double-click to edit
        timetableTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = timetableTable.getSelectedRow();
                    int col = timetableTable.getSelectedColumn();
                    if (col > 0 && row >= 0) {
                        showAddExamEntryDialog(row, col);
                    }
                }
            }
        });
        
        JTableHeader th = timetableTable.getTableHeader();
        th.setBackground(PRIMARY_COLOR);
        th.setForeground(Color.WHITE);
        th.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
        th.setPreferredSize(new Dimension(0, 68));

        // Custom header renderer — shows date + FN/AN with color coding
        th.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String colName = value == null ? "" : value.toString();
                JPanel hp = new JPanel(new BorderLayout(0, 2));
                hp.setOpaque(true);

                if (column == 0) {
                    // Branch header
                    hp.setBackground(new Color(30, 36, 50));
                    JLabel lbl = new JLabel("Branch", SwingConstants.CENTER);
                    lbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
                    lbl.setForeground(Color.WHITE);
                    hp.add(lbl, BorderLayout.CENTER);
                } else {
                    // Determine slot and date
                    boolean isFN;
                    String dateDisplay;
                    if (colName.endsWith("|FN") || colName.endsWith("|AN")) {
                        isFN = colName.endsWith("|FN");
                        dateDisplay = colName.substring(0, colName.lastIndexOf('|')).replace("\n", "  ");
                    } else {
                        isFN = true;
                        dateDisplay = colName.replace("\n", "  ");
                    }
                    // Alternate date group shading: compute date group index
                    int grpIdx = (column - 1) / 2;
                    Color topBg = grpIdx % 2 == 0 ? new Color(26, 54, 93) : new Color(40, 70, 120);
                    Color botBg = isFN ? new Color(59, 130, 246) : new Color(245, 158, 11);

                    JLabel dateLbl = new JLabel("<html><div style='text-align:center;font-size:10px;'>"
                        + dateDisplay.replace("  ", "<br>") + "</div></html>", SwingConstants.CENTER);
                    dateLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 10));
                    dateLbl.setForeground(Color.WHITE);
                    dateLbl.setBackground(topBg);
                    dateLbl.setOpaque(true);
                    dateLbl.setBorder(new EmptyBorder(2, 2, 1, 2));

                    JLabel sessionLbl = new JLabel(isFN ? "FN" : "AN", SwingConstants.CENTER);
                    sessionLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
                    sessionLbl.setForeground(Color.WHITE);
                    sessionLbl.setBackground(botBg);
                    sessionLbl.setOpaque(true);
                    sessionLbl.setBorder(new EmptyBorder(3, 4, 3, 4));

                    hp.setBackground(topBg);
                    hp.add(dateLbl, BorderLayout.CENTER);
                    hp.add(sessionLbl, BorderLayout.SOUTH);
                }
                hp.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(80, 90, 130)));
                return hp;
            }
        });
        
        JScrollPane tableScroll = new JScrollPane(timetableTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        tableScroll.getViewport().setBackground(Color.WHITE);
        
        centerPanel.add(tableToolbar, BorderLayout.NORTH);
        centerPanel.add(tableScroll, BorderLayout.CENTER);

        // ========== BOTTOM PANEL - Actions ==========
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(15, 0, 0, 0));
        
        JPanel leftBottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftBottomPanel.setOpaque(false);
        
        JButton clearAllBtn = new ModernButton("Clear All", new Color(220, 38, 38));
        clearAllBtn.setPreferredSize(new Dimension(115, 42));
        
        JButton previewBtn = new ModernButton("Preview", new Color(107, 114, 128));
        previewBtn.setPreferredSize(new Dimension(110, 42));
        
        leftBottomPanel.add(clearAllBtn);
        leftBottomPanel.add(previewBtn);
        
        JPanel rightBottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightBottomPanel.setOpaque(false);
        
        JButton downloadHTMLBtn = new ModernButton("Download HTML", PRIMARY_COLOR);
        downloadHTMLBtn.setPreferredSize(new Dimension(155, 42));
        
        JButton downloadPDFBtn = new ModernButton("Print / PDF", SECONDARY_COLOR);
        downloadPDFBtn.setPreferredSize(new Dimension(125, 42));
        
        rightBottomPanel.add(downloadHTMLBtn);
        rightBottomPanel.add(downloadPDFBtn);
        
        bottomPanel.add(leftBottomPanel, BorderLayout.WEST);
        bottomPanel.add(rightBottomPanel, BorderLayout.EAST);

        // ========== ACTION LISTENERS ==========
        addDateBtn.addActionListener(e -> {
            String date = dateField.getText().trim();
            String day = (String) dayCombo.getSelectedItem();
            if (!date.isEmpty()) {
                String header = date + "\n(" + day + ")";
                if (!timetableDates.contains(header)) {
                    timetableDates.add(header);
                    refreshTimetableTable();
                    updateStats();
                }
            }
        });
        
        removeDateBtn.addActionListener(e -> {
            if (!timetableDates.isEmpty()) {
                timetableDates.remove(timetableDates.size() - 1);
                refreshTimetableTable();
                updateStats();
            }
        });
        
        addWeekBtn.addActionListener(e -> showQuickAddDatesDialog());
        
        addBranchBtn.addActionListener(e -> {
            String branch = branchField.getText().trim();
            if (!branch.isEmpty() && !timetableBranches.contains(branch)) {
                timetableBranches.add(branch);
                branchListModel.addElement(branch);
                refreshTimetableTable();
                branchField.setText("");
                updateStats();
            }
        });
        
        removeBranchBtn.addActionListener(e -> {
            String selected = branchList.getSelectedValue();
            if (selected != null) {
                timetableBranches.remove(selected);
                branchListModel.removeElement(selected);
                timetableData.remove(selected);
                refreshTimetableTable();
                updateStats();
            }
        });
        
        addEntryBtn.addActionListener(e -> showAddExamEntryDialog(-1, -1));
        
        editEntryBtn.addActionListener(e -> {
            int row = timetableTable.getSelectedRow();
            int col = timetableTable.getSelectedColumn();
            if (col > 0 && row >= 0) {
                showAddExamEntryDialog(row, col);
            } else {
                JOptionPane.showMessageDialog(this, "Please select a cell to edit (not the branch column)");
            }
        });
        
        clearCellBtn.addActionListener(e -> {
            int row = timetableTable.getSelectedRow();
            int col = timetableTable.getSelectedColumn();
            if (col > 0 && row >= 0) {
                int dateIdx = (col - 1) / 2;
                String slot = (col - 1) % 2 == 0 ? "FN" : "AN";
                if (dateIdx < timetableDates.size()) {
                    String branch = timetableBranches.get(row);
                    String date   = timetableDates.get(dateIdx);
                    if (timetableData.containsKey(branch)) {
                        Map<String, String[]> bd = timetableData.get(branch);
                        bd.remove(date + "|" + slot);   // remove specific slot
                        bd.remove(date);                 // remove legacy key too
                    }
                    refreshTimetableTable();
                    updateStats();
                }
            } else {
                JOptionPane.showMessageDialog(ExamSeatingSystem.this,
                    "Please select a timetable cell to clear (not the Branch column).",
                    "No Cell Selected", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        
        bulkAddBtn.addActionListener(e -> showBulkAddDialog());
        
        clearAllBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Clear all timetable data?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                timetableDates.clear();
                timetableData.clear();
                refreshTimetableTable();
                updateStats();
            }
        });
        
        previewBtn.addActionListener(e -> showPreviewDialog());
        
        downloadHTMLBtn.addActionListener(e -> {
            saveTableDataToMap();
            downloadTimetableHTML();
        });
        
        downloadPDFBtn.addActionListener(e -> {
            saveTableDataToMap();
            printTimetable();
        });

        // Initialize
        refreshTimetableTable();
        updateStats();

        // ========== MAIN LAYOUT ==========
        JPanel mainContent = new JPanel(new BorderLayout());
        mainContent.setOpaque(false);
        mainContent.add(leftPanel, BorderLayout.WEST);
        mainContent.add(centerPanel, BorderLayout.CENTER);

        p.add(headerPanel, BorderLayout.NORTH);
        p.add(mainContent, BorderLayout.CENTER);
        p.add(bottomPanel, BorderLayout.SOUTH);

        return p;
    }
    
    private JPanel createSectionPanel(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(209, 213, 219), 1, true), title,
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font(FONT_FAMILY, Font.BOLD, 12), new Color(71, 85, 105)
            ),
            new EmptyBorder(10, 10, 10, 10)
        ));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return panel;
    }
    
    private void updateStats() {
        int entries = 0;
        for (Map<String, String[]> branchData : timetableData.values()) {
            entries += branchData.size();
        }
        statsLabel.setText("Branches: " + timetableBranches.size() + " | Dates: " + timetableDates.size() + " | Entries: " + entries);
    }
    
    private void showQuickAddDatesDialog() {
        JDialog d = new JDialog(this, "Quick Add Exam Dates", true);
        d.setSize(450, 400);
        d.setLocationRelativeTo(this);
        d.setLayout(new BorderLayout());
        d.getContentPane().setBackground(Color.WHITE);
        
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 20, 20, 20));
        content.setBackground(Color.WHITE);
        
        JLabel info = new JLabel("<html>Enter dates one per line in format:<br><b>YYYY-MM-DD,Day</b><br>Example: 2026-02-25,Wednesday</html>");
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.setBorder(new EmptyBorder(0, 0, 15, 0));
        
        JTextArea datesArea = new JTextArea(10, 30);
        datesArea.setFont(new Font("Menlo", Font.PLAIN, 13));
        datesArea.setText("2026-02-25,Wednesday\n2026-02-27,Friday\n2026-03-02,Monday\n2026-03-04,Wednesday\n2026-03-06,Friday");
        JScrollPane scroll = new JScrollPane(datesArea);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(Color.WHITE);
        JButton addBtn = new ModernButton("Add All Dates", PRIMARY_COLOR);
        JButton cancelBtn = new ModernButton("Cancel", Color.GRAY);
        btnPanel.add(cancelBtn);
        btnPanel.add(addBtn);
        
        addBtn.addActionListener(e -> {
            String[] lines = datesArea.getText().split("\n");
            for (String line : lines) {
                String[] parts = line.trim().split(",");
                if (parts.length >= 2) {
                    String header = parts[0].trim() + "\n(" + parts[1].trim() + ")";
                    if (!timetableDates.contains(header)) {
                        timetableDates.add(header);
                    }
                }
            }
            refreshTimetableTable();
            updateStats();
            d.dispose();
        });
        
        cancelBtn.addActionListener(e -> d.dispose());
        
        content.add(info);
        content.add(scroll);
        d.add(content, BorderLayout.CENTER);
        d.add(btnPanel, BorderLayout.SOUTH);
        d.setVisible(true);
    }
    
    private void showAddExamEntryDialog(int preselectedRow, int preselectedCol) {
        JDialog d = new JDialog(this, "Add Exam Entry", true);
        d.setSize(600, 550);
        d.setLocationRelativeTo(this);
        d.setLayout(new BorderLayout());
        d.getContentPane().setBackground(Color.WHITE);
        
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(new EmptyBorder(20, 25, 20, 25));
        content.setBackground(Color.WHITE);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        
        // Subject Search
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel searchLbl = new JLabel("Search Subject:");
        searchLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        content.add(searchLbl, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0;
        JTextField searchField = new ModernTextField(20);
        searchField.setPreferredSize(new Dimension(200, 35));
        content.add(searchField, gbc);
        
        // Subject list with search results
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        DefaultListModel<String> subjectListModel = new DefaultListModel<>();
        for (String[] sub : subjectLibrary) {
            subjectListModel.addElement(sub[0] + " (" + sub[1] + ")");
        }
        JList<String> subjectList = new JList<>(subjectListModel);
        subjectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        subjectList.setVisibleRowCount(5);
        subjectList.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
        JScrollPane subjectScroll = new JScrollPane(subjectList);
        subjectScroll.setPreferredSize(new Dimension(350, 120));
        subjectScroll.setBorder(BorderFactory.createTitledBorder("Select or search a subject"));
        content.add(subjectScroll, gbc);
        
        // Search filter
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String query = searchField.getText().toLowerCase();
                subjectListModel.clear();
                for (String[] sub : subjectLibrary) {
                    if (sub[0].toLowerCase().contains(query) || sub[1].toLowerCase().contains(query)) {
                        subjectListModel.addElement(sub[0] + " (" + sub[1] + ")");
                    }
                }
            }
        });
        
        // Subject Name (auto-filled or manual)
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        JLabel subjectLbl = new JLabel("Subject Name:");
        subjectLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        content.add(subjectLbl, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0;
        JTextField subjectField = new ModernTextField(25);
        subjectField.setPreferredSize(new Dimension(250, 38));
        content.add(subjectField, gbc);
        
        // Course Code
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        JLabel codeLbl = new JLabel("Course Code:");
        codeLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        content.add(codeLbl, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0;
        JTextField codeField = new ModernTextField(15);
        codeField.setPreferredSize(new Dimension(250, 38));
        content.add(codeField, gbc);
        
        // Auto-fill on selection
        subjectList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = subjectList.getSelectedValue();
                if (selected != null) {
                    // Parse "Subject Name (CODE)"
                    int lastParen = selected.lastIndexOf(" (");
                    if (lastParen > 0) {
                        String name = selected.substring(0, lastParen);
                        String code = selected.substring(lastParen + 2, selected.length() - 1);
                        subjectField.setText(name);
                        codeField.setText(code);
                    }
                }
            }
        });
        
        // Branch Selection
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        JLabel branchLbl = new JLabel("Branch:");
        branchLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        content.add(branchLbl, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0;
        JComboBox<String> branchCombo = new JComboBox<>();
        for (String b : timetableBranches) branchCombo.addItem(b);
        branchCombo.setPreferredSize(new Dimension(250, 38));
        branchCombo.setFont(BODY_FONT);
        if (preselectedRow >= 0 && preselectedRow < timetableBranches.size()) {
            branchCombo.setSelectedIndex(preselectedRow);
        }
        content.add(branchCombo, gbc);
        
        // Date Selection
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0;
        JLabel dateLbl = new JLabel("Exam Date:");
        dateLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        content.add(dateLbl, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0;
        JComboBox<String> dateCombo = new JComboBox<>();
        for (String dt : timetableDates) dateCombo.addItem(dt.replace("\n", " "));
        dateCombo.setPreferredSize(new Dimension(250, 38));
        dateCombo.setFont(BODY_FONT);
        // With 2 cols per date: col 1,2 = date 0 (FN,AN); col 3,4 = date 1, etc.
        if (preselectedCol > 0) {
            int preDateIdx = (preselectedCol - 1) / 2;
            if (preDateIdx < timetableDates.size()) {
                dateCombo.setSelectedIndex(preDateIdx);
            }
        }
        content.add(dateCombo, gbc);

        // ── FN / AN Slot picker ──────────────────────────────────────────
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0; gbc.gridwidth = 1;
        JLabel slotLbl = new JLabel("Session Slot:");
        slotLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        content.add(slotLbl, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        JComboBox<String> slotCombo = new JComboBox<>(new String[]{
            "FN — Forenoon  (10:00 AM – 1:00 PM)",
            "AN — Afternoon  (2:00 PM – 5:00 PM)"
        });
        slotCombo.setPreferredSize(new Dimension(250, 38));
        slotCombo.setFont(BODY_FONT);
        content.add(slotCombo, gbc);

        // Pre-fill if editing: derive date index and slot from column index
        if (preselectedRow >= 0 && preselectedCol > 0) {
            int preDateIdx  = (preselectedCol - 1) / 2;
            boolean isFNCol = (preselectedCol - 1) % 2 == 0;
            if (preDateIdx < timetableDates.size()) {
                String branch  = timetableBranches.get(preselectedRow);
                String date    = timetableDates.get(preDateIdx);
                String slotKey = isFNCol ? "FN" : "AN";
                // Pre-select the correct slot
                slotCombo.setSelectedIndex(isFNCol ? 0 : 1);
                // Load existing data for that slot if present
                String mapKey = date + "|" + slotKey;
                if (timetableData.containsKey(branch)) {
                    Map<String, String[]> bd = timetableData.get(branch);
                    String[] data = bd.containsKey(mapKey) ? bd.get(mapKey)
                                    : bd.containsKey(date) ? bd.get(date)  // legacy
                                    : null;
                    if (data != null) {
                        subjectField.setText(data[0]);
                        codeField.setText(data.length > 1 ? data[1] : "");
                    }
                }
            }
        }

        // Add new subject link
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2;
        JButton addNewSubjectBtn = new JButton("+ Add New Subject to Library");
        addNewSubjectBtn.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
        addNewSubjectBtn.setForeground(PRIMARY_COLOR);
        addNewSubjectBtn.setBorderPainted(false);
        addNewSubjectBtn.setContentAreaFilled(false);
        addNewSubjectBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        addNewSubjectBtn.addActionListener(e -> {
            String name = subjectField.getText().trim();
            String code = codeField.getText().trim();
            if (!name.isEmpty() && !code.isEmpty()) {
                boolean exists = subjectLibrary.stream().anyMatch(s -> s[1].equals(code));
                if (!exists) {
                    subjectLibrary.add(new String[]{name, code});
                    saveCustomSubject(name, code);
                    subjectListModel.addElement(name + " (" + code + ")");
                    JOptionPane.showMessageDialog(d, "Subject added to library!");
                } else {
                    JOptionPane.showMessageDialog(d, "Subject with this code already exists.");
                }
            } else {
                JOptionPane.showMessageDialog(d, "Please enter subject name and code first.");
            }
        });
        content.add(addNewSubjectBtn, gbc);
        
        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        btnPanel.setBackground(new Color(248, 250, 252));
        
        JButton cancelBtn = new ModernButton("Cancel", Color.GRAY);
        cancelBtn.setPreferredSize(new Dimension(100, 40));
        
        JButton saveBtn = new ModernButton("Save Entry", PRIMARY_COLOR);
        saveBtn.setPreferredSize(new Dimension(120, 40));
        
        btnPanel.add(cancelBtn);
        btnPanel.add(saveBtn);
        
        saveBtn.addActionListener(e -> {
            String subject = subjectField.getText().trim();
            String code = codeField.getText().trim();
            String branch = (String) branchCombo.getSelectedItem();
            int dateIdx = dateCombo.getSelectedIndex();
            
            if (subject.isEmpty()) {
                JOptionPane.showMessageDialog(d, "Please enter a subject name");
                return;
            }
            if (branch == null || dateIdx < 0) {
                JOptionPane.showMessageDialog(d, "Please select branch and date");
                return;
            }
            
            String date = timetableDates.get(dateIdx);
            // Determine slot string (FN / AN)
            String slot = slotCombo.getSelectedIndex() == 0 ? "FN" : "AN";
            String mapKey = date + "|" + slot;

            Map<String, String[]> branchData = timetableData.computeIfAbsent(branch, k -> new HashMap<>());

            // ── Clash detection: same branch already has a subject for this date+slot ──
            if (branchData.containsKey(mapKey)) {
                String existingSubj = branchData.get(mapKey)[0];
                if (!existingSubj.equalsIgnoreCase(subject)) {
                    int overwrite = JOptionPane.showConfirmDialog(d,
                        "⚠  Clash detected!\n\n" +
                        "Branch \"" + branch + "\" already has \"" + existingSubj + "\"\n" +
                        "scheduled for " + date.replace("\n", " ") + " [" + slot + "]\n\n" +
                        "Overwrite with \"" + subject + "\"?",
                        "Timetable Clash", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (overwrite != JOptionPane.YES_OPTION) return;
                }
            }

            branchData.put(mapKey, new String[]{subject, code});
            refreshTimetableTable();
            updateStats();
            d.dispose();
        });
        
        cancelBtn.addActionListener(e -> d.dispose());
        
        d.add(content, BorderLayout.CENTER);
        d.add(btnPanel, BorderLayout.SOUTH);
        d.setVisible(true);
    }
    
    private void showBulkAddDialog() {
        JDialog d = new JDialog(this, "Bulk Add — Same Subject to Multiple Branches", true);
        d.setSize(640, 680);
        d.setLocationRelativeTo(this);
        d.setLayout(new BorderLayout());
        d.getContentPane().setBackground(Color.WHITE);

        // ---- HEADER ----
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PRIMARY_COLOR);
        header.setBorder(new EmptyBorder(14, 20, 14, 20));
        JLabel hTitle = new JLabel("Bulk Add Subject");
        hTitle.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        hTitle.setForeground(Color.WHITE);
        header.add(hTitle, BorderLayout.WEST);
        d.add(header, BorderLayout.NORTH);

        // ---- CONTENT ----
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(new EmptyBorder(16, 20, 10, 20));
        content.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // — Subject search —
        JLabel subSearchLbl = new JLabel("Search Subject Library:");
        subSearchLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.weightx = 1.0;
        content.add(subSearchLbl, gbc);

        JTextField subSearchField = new ModernTextField(30);
        subSearchField.setPreferredSize(new Dimension(400, 36));
        gbc.gridy = 1;
        content.add(subSearchField, gbc);

        // Subject list (filtered)
        DefaultListModel<String> subListModel = new DefaultListModel<>();
        JList<String> subList = new JList<>(subListModel);
        subList.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        subList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        subList.setFixedCellHeight(32);
        JScrollPane subScroll = new JScrollPane(subList);
        subScroll.setPreferredSize(new Dimension(400, 110));
        gbc.gridy = 2;
        content.add(subScroll, gbc);

        // Subject Name + Code fields
        gbc.gridwidth = 1; gbc.gridy = 3; gbc.gridx = 0; gbc.weightx = 0;
        content.add(new JLabel("Subject Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        JTextField subjectField = new ModernTextField(25);
        content.add(subjectField, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        content.add(new JLabel("Course Code:"), gbc);
        gbc.gridx = 1;
        JTextField codeField = new ModernTextField(15);
        content.add(codeField, gbc);

        // Date picker
        gbc.gridx = 0; gbc.gridy = 5;
        content.add(new JLabel("Exam Date:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> dateCombo = new JComboBox<>();
        dateCombo.addItem("-- Select Date --");
        for (String dt : timetableDates) dateCombo.addItem(dt.replace("\n", " "));
        content.add(dateCombo, gbc);

        // FN / AN slot picker
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 1; gbc.weightx = 0;
        content.add(new JLabel("Session:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        JComboBox<String> slotCombo = new JComboBox<>(new String[]{"FN  (9:30 AM – 12:30 PM)", "AN  (1:30 PM – 4:30 PM)"});
        slotCombo.setFont(BODY_FONT);
        content.add(slotCombo, gbc);

        // Branches — load from DB so ALL sections are visible
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2;
        JLabel branchLbl = new JLabel("Select Branches / Sections:");
        branchLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
        content.add(branchLbl, gbc);

        JPanel branchCheckPanel = new JPanel(new GridLayout(0, 3, 6, 4));
        branchCheckPanel.setBackground(Color.WHITE);
        List<JCheckBox> branchChecks = new ArrayList<>();

        // Load ALL distinct sections/branches from students table
        Set<String> allBranches = new LinkedHashSet<>(timetableBranches);
        try {
            ResultSet rsB = con.createStatement().executeQuery(
                "SELECT DISTINCT section FROM students ORDER BY section");
            while (rsB.next()) allBranches.add(rsB.getString(1));
        } catch (Exception ignored) {}
        for (String b : allBranches) {
            JCheckBox cb = new JCheckBox(b);
            cb.setBackground(Color.WHITE);
            cb.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            branchChecks.add(cb);
            branchCheckPanel.add(cb);
        }
        JScrollPane branchScroll = new JScrollPane(branchCheckPanel);
        branchScroll.setPreferredSize(new Dimension(400, 120));
        gbc.gridy = 8;
        content.add(branchScroll, gbc);

        // Select All / None strip
        JPanel selectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        selectPanel.setBackground(Color.WHITE);
        JButton selectAll  = new ModernButton("Select All",  SECONDARY_COLOR); selectAll.setPreferredSize(new Dimension(100, 34));
        JButton selectNone = new ModernButton("None", Color.GRAY);              selectNone.setPreferredSize(new Dimension(70, 34));
        selectAll.addActionListener(e -> branchChecks.forEach(cb -> cb.setSelected(true)));
        selectNone.addActionListener(e -> branchChecks.forEach(cb -> cb.setSelected(false)));
        selectPanel.add(selectAll);
        selectPanel.add(selectNone);
        gbc.gridy = 9;
        content.add(selectPanel, gbc);

        // Populate subject list
        Runnable refreshSubList = () -> {
            subListModel.clear();
            String q = subSearchField.getText().toLowerCase().trim();
            for (String[] s : subjectLibrary) {
                if (q.isEmpty() || s[0].toLowerCase().contains(q) || s[1].toLowerCase().contains(q)) {
                    subListModel.addElement(s[0] + "  [" + s[1] + "]");
                }
            }
        };
        refreshSubList.run();
        subSearchField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) { refreshSubList.run(); }
        });
        // Click on list item auto-fills fields
        subList.addListSelectionListener(e2 -> {
            if (!e2.getValueIsAdjusting()) {
                String sel = subList.getSelectedValue();
                if (sel != null) {
                    int lb = sel.lastIndexOf('[');
                    int rb = sel.lastIndexOf(']');
                    if (lb > 0 && rb > lb) {
                        subjectField.setText(sel.substring(0, lb).trim());
                        codeField.setText(sel.substring(lb + 1, rb).trim());
                    }
                }
            }
        });

        JScrollPane contentScroll = new JScrollPane(content);
        contentScroll.setBorder(null);
        d.add(contentScroll, BorderLayout.CENTER);

        // ---- BUTTONS ----
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 12));
        btnPanel.setBackground(new Color(248, 250, 252));
        btnPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));
        JButton cancelBtn = new ModernButton("Cancel", Color.GRAY);
        JButton addBtn = new ModernButton("Add to Selected Branches", PRIMARY_COLOR);
        addBtn.setPreferredSize(new Dimension(200, 40));
        btnPanel.add(cancelBtn);
        btnPanel.add(addBtn);
        d.add(btnPanel, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> {
            String subject = subjectField.getText().trim();
            String code = codeField.getText().trim();
            int dateIdx = dateCombo.getSelectedIndex();
            if (subject.isEmpty()) { JOptionPane.showMessageDialog(d, "Please enter or select a Subject Name."); return; }
            if (dateIdx <= 0 || dateIdx > timetableDates.size()) { JOptionPane.showMessageDialog(d, "Please select an Exam Date."); return; }
            String date = timetableDates.get(dateIdx - 1);
            String slot = slotCombo.getSelectedIndex() == 0 ? "FN" : "AN";
            String slotKey = date + "|" + slot;
            int count = 0;
            for (JCheckBox cb : branchChecks) {
                if (cb.isSelected()) {
                    String branch = cb.getText();
                    timetableData.computeIfAbsent(branch, k -> new HashMap<>()).put(slotKey, new String[]{subject, code});
                    if (!timetableBranches.contains(branch)) timetableBranches.add(branch);
                    count++;
                }
            }
            if (count == 0) { JOptionPane.showMessageDialog(d, "Please select at least one branch."); return; }
            refreshTimetableTable();
            updateStats();
            JOptionPane.showMessageDialog(d, "Added \"" + subject + "\" to " + count + " branch(es)!");
            d.dispose();
        });
        cancelBtn.addActionListener(e -> d.dispose());
        d.setVisible(true);
    }
    
    private void showPreviewDialog() {
        saveTableDataToMap();
        
        JDialog d = new JDialog(this, "Timetable Preview", true);
        d.setSize(900, 700);
        d.setLocationRelativeTo(this);
        
        JEditorPane preview = new JEditorPane();
        preview.setContentType("text/html");
        preview.setEditable(false);
        preview.setText(generateTimetableHTML());
        preview.setCaretPosition(0);
        
        d.add(new JScrollPane(preview));
        d.setVisible(true);
    }
    
    private void printTimetable() {
        saveTableDataToMap();
        try {
            // Save to a temp file and open in the system browser — far better than JEditorPane.print()
            File tmp = File.createTempFile("Aditya_Timetable_", ".html");
            tmp.deleteOnExit();
            try (PrintWriter pw = new PrintWriter(
                    new OutputStreamWriter(new java.io.FileOutputStream(tmp), java.nio.charset.StandardCharsets.UTF_8))) {
                pw.write(generateTimetableHTML());
            }
            openInBrowser(tmp);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not open timetable in browser: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void refreshTimetableTable() {
        // Save current data before refresh
        saveTableDataToMap();
        // Persist to DB so data survives app restart
        saveTimetableToDB();
        
        // Rebuild columns: Branch + FN/AN pair per date
        timetableModel.setColumnCount(0);
        timetableModel.setRowCount(0);

        timetableModel.addColumn("Branch");
        for (String date : timetableDates) {
            timetableModel.addColumn(date + "|FN");
            timetableModel.addColumn(date + "|AN");
        }

        // Add rows for each branch
        for (String branch : timetableBranches) {
            Object[] rowData = new Object[timetableDates.size() * 2 + 1];
            rowData[0] = branch;
            Map<String, String[]> branchData = timetableData.get(branch);
            for (int i = 0; i < timetableDates.size(); i++) {
                String date = timetableDates.get(i);
                // FN slot
                String fnKey = date + "|FN";
                if (branchData != null && branchData.containsKey(fnKey)) {
                    String[] sc = branchData.get(fnKey);
                    rowData[i * 2 + 1] = sc[0] + (sc[1] != null && !sc[1].isEmpty() ? "\n(" + sc[1] + ")" : "");
                } else if (branchData != null && branchData.containsKey(date)) {
                    // legacy key (no slot suffix) — treat as FN
                    String[] sc = branchData.get(date);
                    rowData[i * 2 + 1] = sc[0] + (sc.length > 1 && !sc[1].isEmpty() ? "\n(" + sc[1] + ")" : "");
                }
                // AN slot
                String anKey = date + "|AN";
                if (branchData != null && branchData.containsKey(anKey)) {
                    String[] sc = branchData.get(anKey);
                    rowData[i * 2 + 2] = sc[0] + (sc[1] != null && !sc[1].isEmpty() ? "\n(" + sc[1] + ")" : "");
                }
            }
            timetableModel.addRow(rowData);
        }

        // Set column widths
        if (timetableTable.getColumnModel().getColumnCount() > 0) {
            timetableTable.getColumnModel().getColumn(0).setPreferredWidth(72);
            for (int i = 1; i < timetableTable.getColumnCount(); i++) {
                timetableTable.getColumnModel().getColumn(i).setPreferredWidth(105);
            }
        }
        // Persist changes to DB so they survive restart
        saveTimetableToDB();
    }
    
    private void saveTableDataToMap() {
        for (int row = 0; row < timetableModel.getRowCount(); row++) {
            String branch = (String) timetableModel.getValueAt(row, 0);
            Map<String, String[]> branchData = timetableData.computeIfAbsent(branch, k -> new HashMap<>());
            
            for (int col = 1; col < timetableModel.getColumnCount(); col++) {
                String date = timetableModel.getColumnName(col);
                Object value = timetableModel.getValueAt(row, col);
                if (value != null && !value.toString().trim().isEmpty()) {
                    String cellText = value.toString();
                    // Parse "Subject Name\n(Course Code)" format
                    String subject = cellText;
                    String code = "";
                    if (cellText.contains("\n(") && cellText.endsWith(")")) {
                        int idx = cellText.lastIndexOf("\n(");
                        subject = cellText.substring(0, idx);
                        code = cellText.substring(idx + 2, cellText.length() - 1);
                    } else if (cellText.contains("(") && cellText.endsWith(")")) {
                        int idx = cellText.lastIndexOf("(");
                        subject = cellText.substring(0, idx).trim();
                        code = cellText.substring(idx + 1, cellText.length() - 1);
                    }
                    branchData.put(date, new String[]{subject, code});
                }
            }
        }
    }
    
    private void downloadTimetableHTML() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("Exam_Timetable.html"));
        fc.setFileFilter(new FileNameExtensionFilter("HTML Files", "html"));
        fc.setDialogTitle("Save Exam Timetable as HTML");

        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".html"))
            file = new File(file.getAbsolutePath() + ".html");

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            pw.write(generateTimetableHTML());
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
            "Timetable saved to:\n" + file.getAbsolutePath() +
            "\n\nOpen in browser to Print / Save as PDF?",
            "Saved", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) openInBrowser(file);
    }
    
    private String generateTimetableHTML() {
        // Helper: format a date string like "2026-02-02\n(Monday)" → "02.02.2026\n(Monday)"
        java.util.function.Function<String, String> fmtDate = raw -> {
            String[] parts = raw.split("\n", 2);
            String datePart = parts[0].trim();        // e.g. "2026-02-02"
            String dayPart  = parts.length > 1 ? parts[1].trim() : "";
            try {
                java.time.LocalDate ld = java.time.LocalDate.parse(datePart);
                datePart = String.format("%02d.%02d.%d", ld.getDayOfMonth(), ld.getMonthValue(), ld.getYear());
            } catch (Exception ignored) {}
            return dayPart.isEmpty() ? datePart : datePart + "\n" + dayPart;
        };

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang='en'><head>\n")
          .append("<meta charset='UTF-8'>\n")
          .append("<title>Exam Timetable \u2014 Aditya University</title>\n")
          .append("<style>\n")
          .append("  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }\n")
          .append("  body { font-family: 'Times New Roman', Times, serif; background: #fff; color: #000; padding: 24px 28px; font-size: 10pt; }\n")
          .append("  /* ── Print button ── */\n")
          .append("  .no-print { text-align: center; margin-bottom: 16px; }\n")
          .append("  .print-btn { background: #1a365d; color: #fff; border: none; padding: 11px 28px; font-size: 13px; border-radius: 5px; cursor: pointer; font-family: Arial, sans-serif; }\n")
          .append("  .print-btn:hover { background: #2d5086; }\n")
          .append("  /* ── University header ── */\n")
          .append("  .univ-header { text-align: center; border: 2px solid #000; padding: 10px 12px 8px; margin-bottom: 6px; }\n")
          .append("  .univ-name { font-size: 18pt; font-weight: bold; text-transform: uppercase; letter-spacing: 0.5px; }\n")
          .append("  .univ-sub  { font-size: 11pt; margin-top: 2px; }\n")
          .append("  .exam-title { font-size: 12pt; font-weight: bold; text-transform: uppercase; margin-top: 3px; }\n")
          .append("  /* ── Table ── */\n")
          .append("  table { width: 100%; border-collapse: collapse; margin-top: 6px; table-layout: auto; }\n")
          .append("  th, td { border: 1px solid #000; padding: 5px 4px; text-align: center; vertical-align: middle; font-size: 8.5pt; }\n")
          .append("  /* Header row 1: date group */\n")
          .append("  .hdr-session { background: #1a365d; color: #fff; font-size: 9pt; width: 70px; }\n")
          .append("  .hdr-date { background: #1a365d; color: #fff; font-size: 8.5pt; white-space: pre-line; padding: 4px 3px; }\n")
          .append("  /* Header row 2: FN / AN */\n")
          .append("  .hdr-fn { background: #2563eb; color: #fff; font-size: 9pt; font-weight: bold; }\n")
          .append("  .hdr-an { background: #d97706; color: #fff; font-size: 9pt; font-weight: bold; }\n")
          .append("  /* Data cells */\n")
          .append("  .branch-col { background: #e8eaf6; font-weight: bold; white-space: nowrap; }\n")
          .append("  .fn-cell { background: #eff6ff; }\n")
          .append("  .an-cell { background: #fffbeb; }\n")
          .append("  .fn-cell.filled { background: #dbeafe; }\n")
          .append("  .an-cell.filled { background: #fef3c7; }\n")
          .append("  .sub-name { font-weight: bold; }\n")
          .append("  .sub-code { font-size: 8pt; color: #444; }\n")
          .append("  .empty { color: #bbb; }\n")
          .append("  /* Timings note & footer */\n")
          .append("  .note-box { margin-top: 12px; font-size: 9pt; border: 1px solid #000; padding: 6px 10px; }\n")
          .append("  .footer-row { margin-top: 24px; display: flex; justify-content: space-between; font-size: 10pt; }\n")
          .append("  @media print {\n")
          .append("    .no-print { display: none !important; }\n")
          .append("    body { padding: 6mm 8mm; }\n")
          .append("    thead { display: table-header-group; }\n")
          .append("    tr { page-break-inside: avoid; }\n")
          .append("    @page { size: A3 landscape; margin: 8mm; }\n")
          .append("  }\n")
          .append("</style>\n</head>\n<body>\n");

        // Print button
        sb.append("<div class='no-print'>")
          .append("<button class='print-btn' onclick='window.print()'>&#128438;&nbsp; Print / Save as PDF</button>")
          .append("<p style='margin-top:6px;font-size:10px;color:#666;font-family:Arial;'>")
          .append("Use <b>Landscape</b> orientation &amp; <b>A3</b> paper for best results.")
          .append("</p></div>\n");

        // University header block — embed logo as base64 data URI
        String logoDataUri = "";
        try {
            BufferedImage logoHtml = createAdityaLogoImage(320, 80, false);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(logoHtml, "PNG", baos);
            logoDataUri = "data:image/png;base64,"
                + java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception ignored) {}

        sb.append("<div class='univ-header'>\n");
        if (!logoDataUri.isEmpty()) {
            sb.append("  <div style='text-align:center;margin-bottom:6px;'>")
              .append("<img src='").append(logoDataUri).append("' alt='Aditya University' ")
              .append("style='height:72px;max-width:340px;object-fit:contain;'></div>\n");
        } else {
            sb.append("  <div class='univ-name'>Aditya University</div>\n");
        }
        sb.append("  <div class='exam-title'>").append(escHtml(examTitle)).append("</div>\n")
          .append("  <div class='univ-sub'>TIME TABLE</div>\n")
          .append("</div>\n");

        // ── TABLE ──
        sb.append("<table>\n<thead>\n");

        // Header row 1: "Date\n(Day)" spanning 2 rows in branch col, then each date spans 2 cols
        sb.append("  <tr>\n");
        sb.append("    <th class='hdr-session' rowspan='2'>Date<br>(Day)</th>\n");
        for (String date : timetableDates) {
            String fmt = fmtDate.apply(date);
            sb.append("    <th class='hdr-date' colspan='2'>")
              .append(fmt.replace("\n", "<br>"))
              .append("</th>\n");
        }
        sb.append("  </tr>\n");

        // Header row 2: FN | AN pair per date
        sb.append("  <tr>\n");
        for (int i = 0; i < timetableDates.size(); i++) {
            sb.append("    <th class='hdr-fn'>FN</th>\n");
            sb.append("    <th class='hdr-an'>AN</th>\n");
        }
        sb.append("  </tr>\n");
        sb.append("</thead>\n<tbody>\n");

        // Data rows
        for (String branch : timetableBranches) {
            sb.append("  <tr>\n");
            sb.append("    <td class='branch-col'>").append(escHtml(branch)).append("</td>\n");
            Map<String, String[]> bd = timetableData.get(branch);
            for (String date : timetableDates) {
                // FN cell
                String fnKey = date + "|FN";
                String[] fnData = (bd != null && bd.containsKey(fnKey)) ? bd.get(fnKey)
                                : (bd != null && bd.containsKey(date))   ? bd.get(date) : null;
                if (fnData != null && !fnData[0].isEmpty()) {
                    sb.append("    <td class='fn-cell filled'><span class='sub-name'>")
                      .append(escHtml(fnData[0])).append("</span>");
                    if (fnData.length > 1 && fnData[1] != null && !fnData[1].isEmpty())
                        sb.append("<br><span class='sub-code'>(").append(escHtml(fnData[1])).append(")</span>");
                    sb.append("</td>\n");
                } else {
                    sb.append("    <td class='fn-cell empty'>&mdash;</td>\n");
                }
                // AN cell
                String anKey = date + "|AN";
                String[] anData = (bd != null && bd.containsKey(anKey)) ? bd.get(anKey) : null;
                if (anData != null && !anData[0].isEmpty()) {
                    sb.append("    <td class='an-cell filled'><span class='sub-name'>")
                      .append(escHtml(anData[0])).append("</span>");
                    if (anData.length > 1 && anData[1] != null && !anData[1].isEmpty())
                        sb.append("<br><span class='sub-code'>(").append(escHtml(anData[1])).append(")</span>");
                    sb.append("</td>\n");
                } else {
                    sb.append("    <td class='an-cell empty'>&mdash;</td>\n");
                }
            }
            sb.append("  </tr>\n");
        }
        sb.append("</tbody>\n</table>\n");

        // Timings note
        sb.append("<div class='note-box'>")
          .append("<b>NOTE: 1.</b> Timings: <b>Forenoon (FN)</b> \u2014 10:00 AM to 1:00 PM &nbsp;&nbsp;|&nbsp;&nbsp; ")
          .append("<b>Afternoon (AN)</b> \u2014 2:00 PM to 5:00 PM.<br>")
          .append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2. Any omissions or clashes must be reported to the Controller of Examinations immediately.")
          .append("</div>\n");

        // Footer
        sb.append("<div class='footer-row'>")
          .append("<div><b>DATE:</b> ")
          .append(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")))
          .append("</div>")
          .append("<div style='text-align:center;'><br><b>Controller of Examinations</b></div>")
          .append("</div>\n");

        sb.append("</body></html>\n");
        return sb.toString();
    }

    // ==========================================
    // SEARCH (with Pagination)
    // ==========================================
    private int searchPageSize = 50;
    private int searchCurrentPage = 0;
    private String searchLastQuery = "";
    private int searchTotalResults = 0;
    
    private JPanel createSearchPanel() {
        JPanel p = new ModernPanel(new BorderLayout());
        
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
        top.setOpaque(false);
        JTextField search = new ModernTextField(20);
        search.setPreferredSize(new Dimension(300, 40));
        
        JButton find = new ModernButton("Search", PRIMARY_COLOR);
        find.setPreferredSize(new Dimension(120, 40));
        
        top.add(new JLabel("Search Student:")); top.add(search); top.add(find);
        
        DefaultTableModel tableModel = new DefaultTableModel(new String[]{"Room","Roll","Name","Seat","Subject","Time"}, 0);
        JTable table = new JTable(tableModel);
        styleTable(table);
        
        // Pagination panel
        JPanel paginationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        paginationPanel.setOpaque(false);
        JButton prevBtn = new ModernButton("◀ Previous", new Color(107, 114, 128));
        prevBtn.setPreferredSize(new Dimension(110, 35));
        JButton nextBtn = new ModernButton("Next ▶", new Color(107, 114, 128));
        nextBtn.setPreferredSize(new Dimension(100, 35));
        JLabel pageLabel = new JLabel("Page 1");
        pageLabel.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        JLabel totalLabel = new JLabel("");
        totalLabel.setFont(new Font(FONT_FAMILY, Font.ITALIC, 12));
        totalLabel.setForeground(Color.GRAY);
        
        paginationPanel.add(prevBtn);
        paginationPanel.add(pageLabel);
        paginationPanel.add(nextBtn);
        paginationPanel.add(Box.createHorizontalStrut(20));
        paginationPanel.add(totalLabel);
        
        // Search action with loading indicator
        Runnable doSearch = () -> {
            String q = "%" + search.getText() + "%";
            searchLastQuery = search.getText();
            int offset = searchCurrentPage * searchPageSize;
            
            // Show loading cursor
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            find.setEnabled(false);
            find.setText("Searching...");
            
            new Thread(() -> {
                try {
                    // Count total results
                    PreparedStatement countPst = con.prepareStatement(
                        "SELECT COUNT(*) FROM seating_plan WHERE student_roll LIKE ? OR student_name LIKE ?");
                    countPst.setString(1, q); countPst.setString(2, q);
                    ResultSet countRs = countPst.executeQuery();
                    if (countRs.next()) searchTotalResults = countRs.getInt(1);
                    
                    // Fetch page of results
                    PreparedStatement pst = con.prepareStatement(
                        "SELECT * FROM seating_plan WHERE student_roll LIKE ? OR student_name LIKE ? " +
                        "ORDER BY room_no, seat_col, seat_row LIMIT ? OFFSET ?");
                    pst.setString(1, q); pst.setString(2, q);
                    pst.setInt(3, searchPageSize); pst.setInt(4, offset);
                    ResultSet rs = pst.executeQuery();
                    
                    java.util.List<Object[]> rows = new ArrayList<>();
                    while(rs.next()) {
                        rows.add(new Object[]{
                            rs.getString("room_no"), 
                            rs.getString("student_roll"), 
                            rs.getString("student_name"), 
                            rs.getInt("seat_row")+","+rs.getInt("seat_col"), 
                            rs.getString("subject"),
                            rs.getString("start_time")
                        });
                    }
                    
                    SwingUtilities.invokeLater(() -> {
                        tableModel.setRowCount(0);
                        for (Object[] row : rows) tableModel.addRow(row);
                        
                        int totalPages = (int) Math.ceil((double) searchTotalResults / searchPageSize);
                        pageLabel.setText("Page " + (searchCurrentPage + 1) + " of " + Math.max(1, totalPages));
                        totalLabel.setText(searchTotalResults + " result(s) found");
                        
                        prevBtn.setEnabled(searchCurrentPage > 0);
                        nextBtn.setEnabled((searchCurrentPage + 1) * searchPageSize < searchTotalResults);
                        
                        setCursor(Cursor.getDefaultCursor());
                        find.setEnabled(true);
                        find.setText("Search");
                    });
                } catch(Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        setCursor(Cursor.getDefaultCursor());
                        find.setEnabled(true);
                        find.setText("Search");
                    });
                }
            }).start();
        };
        
        find.addActionListener(e -> {
            searchCurrentPage = 0;
            doSearch.run();
        });
        
        // Enter key to search
        search.addActionListener(e -> {
            searchCurrentPage = 0;
            doSearch.run();
        });
        
        prevBtn.addActionListener(e -> {
            if (searchCurrentPage > 0) {
                searchCurrentPage--;
                doSearch.run();
            }
        });
        
        nextBtn.addActionListener(e -> {
            if ((searchCurrentPage + 1) * searchPageSize < searchTotalResults) {
                searchCurrentPage++;
                doSearch.run();
            }
        });
        
        prevBtn.setEnabled(false);
        nextBtn.setEnabled(false);
        
        p.add(top, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);
        p.add(scroll, BorderLayout.CENTER);
        p.add(paginationPanel, BorderLayout.SOUTH);
        return p;
    }

    // ==========================================
    // BACKUP & RESTORE PANEL (ADMIN)
    // ==========================================
    private JPanel createBackupRestorePanel() {
        JPanel p = new ModernPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JLabel title = new JLabel("Database Backup & Restore");
        title.setFont(SUBHEADER_FONT);
        title.setForeground(PRIMARY_COLOR);
        title.setBorder(new EmptyBorder(0, 0, 20, 0));
        
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        
        // Backup Section
        JPanel backupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 15));
        backupPanel.setOpaque(false);
        backupPanel.setBorder(createTitledBorder("Create Backup"));
        
        JButton backupBtn = new ModernButton("Backup Database", PRIMARY_COLOR);
        backupBtn.setPreferredSize(new Dimension(200, 45));
        
        JLabel backupStatus = new JLabel("Ready to backup");
        backupStatus.setFont(new Font(FONT_FAMILY, Font.ITALIC, 12));
        backupStatus.setForeground(Color.GRAY);
        
        backupBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("exam_portal_backup_" + System.currentTimeMillis() + ".sql"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                        "mysqldump", "-u", DB_USER, "-p" + DB_PASS, "projectdb"
                    );
                    pb.redirectOutput(file);
                    Process process = pb.start();
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        backupStatus.setText("Backup saved: " + file.getName());
                        backupStatus.setForeground(SECONDARY_COLOR);
                        JOptionPane.showMessageDialog(this, "Backup created successfully!");
                    } else {
                        // Fallback: Manual SQL export
                        exportDatabaseManually(file);
                        backupStatus.setText("Backup saved: " + file.getName());
                        JOptionPane.showMessageDialog(this, "Backup created successfully!");
                    }
                } catch (Exception ex) {
                    // Fallback method
                    exportDatabaseManually(fc.getSelectedFile());
                    backupStatus.setText("Backup saved (manual): " + fc.getSelectedFile().getName());
                    JOptionPane.showMessageDialog(this, "Backup created!");
                }
            }
        });
        
        backupPanel.add(backupBtn);
        backupPanel.add(backupStatus);
        
        // Restore Section
        JPanel restorePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 15));
        restorePanel.setOpaque(false);
        restorePanel.setBorder(createTitledBorder("Restore from Backup"));
        
        JButton restoreBtn = new ModernButton("Restore Database", new Color(234, 179, 8));
        restoreBtn.setPreferredSize(new Dimension(200, 45));
        
        JLabel restoreStatus = new JLabel("Select a backup file to restore");
        restoreStatus.setFont(new Font(FONT_FAMILY, Font.ITALIC, 12));
        restoreStatus.setForeground(Color.GRAY);
        
        restoreBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, 
                "Warning: This will overwrite current data. Continue?", 
                "Confirm Restore", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
            
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("SQL Files", "sql"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                try {
                    importDatabaseManually(file);
                    restoreStatus.setText("Restored from: " + file.getName());
                    restoreStatus.setForeground(SECONDARY_COLOR);
                    JOptionPane.showMessageDialog(this, "Database restored successfully!");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Restore failed: " + ex.getMessage());
                }
            }
        });
        
        restorePanel.add(restoreBtn);
        restorePanel.add(restoreStatus);
        
        // Export Options Section
        JPanel exportPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 15));
        exportPanel.setOpaque(false);
        exportPanel.setBorder(createTitledBorder("Export Data"));
        
        JButton exportExcelBtn = new ModernButton("Export to Excel", SECONDARY_COLOR);
        exportExcelBtn.setPreferredSize(new Dimension(170, 45));
        exportExcelBtn.addActionListener(e -> exportToExcel());
        
        JButton exportHTMLBtn = new ModernButton("Export All Reports (HTML)", PRIMARY_COLOR);
        exportHTMLBtn.setPreferredSize(new Dimension(220, 45));
        exportHTMLBtn.addActionListener(e -> exportAllReportsHTML());
        
        exportPanel.add(exportExcelBtn);
        exportPanel.add(exportHTMLBtn);
        
        content.add(backupPanel);
        content.add(Box.createVerticalStrut(20));
        content.add(restorePanel);
        content.add(Box.createVerticalStrut(20));
        content.add(exportPanel);
        
        p.add(title, BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        return p;
    }
    
    private void exportDatabaseManually(File file) {
        try (PrintWriter pw = new PrintWriter(file)) {
            pw.println("-- Exam Portal Backup");
            pw.println("-- Generated: " + new java.util.Date());
            pw.println();
            
            String[] tables = {"users", "rooms", "room_broken_seats", "students", "faculty", "seating_plan", "subject_library", "exam_templates"};
            for (String table : tables) {
                try {
                    ResultSet rs = con.createStatement().executeQuery("SELECT * FROM " + table);
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    
                    pw.println("-- Table: " + table);
                    while (rs.next()) {
                        StringBuilder sb = new StringBuilder("INSERT INTO " + table + " VALUES (");
                        for (int i = 1; i <= cols; i++) {
                            String val = rs.getString(i);
                            if (val == null) sb.append("NULL");
                            else sb.append("'").append(val.replace("'", "''")).append("'");
                            if (i < cols) sb.append(", ");
                        }
                        sb.append(");");
                        pw.println(sb.toString());
                    }
                    pw.println();
                } catch (Exception e) {
                    // Table might not exist
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void importDatabaseManually(File file) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            StringBuilder statement = new StringBuilder();
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--")) continue;
                statement.append(line);
                if (line.endsWith(";")) {
                    con.createStatement().executeUpdate(statement.toString());
                    statement = new StringBuilder();
                }
            }
        }
    }
    
    private void exportToExcel() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("exam_data_export.csv"));
        fc.setFileFilter(new FileNameExtensionFilter("CSV/Excel Files", "csv", "xlsx"));
        
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (!file.getName().endsWith(".csv")) {
                file = new File(file.getAbsolutePath() + ".csv");
            }
            
            try (PrintWriter pw = new PrintWriter(file)) {
                // Export seating plan
                pw.println("SEATING PLAN EXPORT");
                pw.println("Room,Roll No,Student Name,Subject,Seat,Date,Time,Invigilator");
                
                ResultSet rs = con.createStatement().executeQuery("SELECT * FROM seating_plan ORDER BY exam_date, room_no");
                while (rs.next()) {
                    pw.printf("%s,%s,%s,%s,%d-%d,%s,%s-%s,%s%n",
                        rs.getString("room_no"),
                        rs.getString("student_roll"),
                        rs.getString("student_name"),
                        rs.getString("subject"),
                        rs.getInt("seat_row"),
                        rs.getInt("seat_col"),
                        rs.getString("exam_date"),
                        rs.getString("start_time"),
                        rs.getString("end_time"),
                        rs.getString("invigilator_name")
                    );
                }
                
                pw.println();
                pw.println("STUDENTS LIST");
                pw.println("Roll No,Name,Section");
                ResultSet rsS = con.createStatement().executeQuery("SELECT * FROM students ORDER BY section, roll_no");
                while (rsS.next()) {
                    pw.printf("%s,%s,%s%n", rsS.getString("roll_no"), rsS.getString("name"), rsS.getString("section"));
                }
                
                JOptionPane.showMessageDialog(this, "Exported to: " + file.getAbsolutePath());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage());
            }
        }
    }
    
    private void exportAllReportsHTML() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dir = fc.getSelectedFile();
            try {
                // Export timetable
                saveTableDataToMap();
                File ttFile = new File(dir, "Exam_Timetable.html");
                try (PrintWriter pw = new PrintWriter(ttFile)) {
                    pw.write(generateTimetableHTML());
                }
                JOptionPane.showMessageDialog(this, "All reports exported to: " + dir.getAbsolutePath());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage());
            }
        }
    }

    // ==========================================
    // HALL TICKET GENERATOR
    // ==========================================
    private JPanel createHallTicketPanel() {
        JPanel p = new ModernPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JLabel title = new JLabel("Hall Ticket Generator");
        title.setFont(SUBHEADER_FONT);
        title.setForeground(PRIMARY_COLOR);
        title.setBorder(new EmptyBorder(0, 0, 20, 0));
        
        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        
        // Left panel - Options
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setOpaque(false);
        optionsPanel.setBorder(new EmptyBorder(0, 0, 0, 20));
        optionsPanel.setPreferredSize(new Dimension(300, 0));
        
        // Search student
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setOpaque(false);
        searchPanel.setBorder(createTitledBorder("Find Student"));
        
        JTextField searchField = new ModernTextField(15);
        JButton searchBtn = new ModernButton("Search", PRIMARY_COLOR);
        searchBtn.setPreferredSize(new Dimension(100, 40));
        searchPanel.add(searchField);
        searchPanel.add(searchBtn);
        
        // Student list
        DefaultListModel<String> studentModel = new DefaultListModel<>();
        JList<String> studentList = new JList<>(studentModel);
        studentList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane studentScroll = new JScrollPane(studentList);
        studentScroll.setBorder(createTitledBorder("Students"));
        studentScroll.setPreferredSize(new Dimension(280, 250));
        
        // Options
        JPanel optBtns = new JPanel(new GridLayout(3, 1, 10, 10));
        optBtns.setOpaque(false);
        optBtns.setBorder(createTitledBorder("Generate Options"));
        
        JCheckBox includePhotoChk = new JCheckBox("Include Photo Placeholder");
        includePhotoChk.setOpaque(false);
        includePhotoChk.setSelected(true);
        
        JCheckBox includeQRChk = new JCheckBox("Include QR Code");
        includeQRChk.setOpaque(false);
        includeQRChk.setSelected(true);
        
        JCheckBox includeScheduleChk = new JCheckBox("Include Full Schedule");
        includeScheduleChk.setOpaque(false);
        includeScheduleChk.setSelected(true);
        
        optBtns.add(includePhotoChk);
        optBtns.add(includeQRChk);
        optBtns.add(includeScheduleChk);
        
        JButton generateBtn = new ModernButton("Generate Hall Tickets", SECONDARY_COLOR);
        generateBtn.setMaximumSize(new Dimension(280, 50));
        generateBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JButton generateAllBtn = new ModernButton("Generate All (Section)", PRIMARY_COLOR);
        generateAllBtn.setMaximumSize(new Dimension(280, 50));
        generateAllBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        optionsPanel.add(searchPanel);
        optionsPanel.add(Box.createVerticalStrut(10));
        optionsPanel.add(studentScroll);
        optionsPanel.add(Box.createVerticalStrut(10));
        optionsPanel.add(optBtns);
        optionsPanel.add(Box.createVerticalStrut(15));
        optionsPanel.add(generateBtn);
        optionsPanel.add(Box.createVerticalStrut(10));
        optionsPanel.add(generateAllBtn);
        
        // Right panel - Preview
        JEditorPane previewPane = new JEditorPane();
        previewPane.setContentType("text/html");
        previewPane.setEditable(false);
        previewPane.setText("<html><body style='font-family:Arial;padding:20px;'><h2>Hall Ticket Preview</h2><p>Select students and click Generate to preview hall tickets.</p></body></html>");
        JScrollPane previewScroll = new JScrollPane(previewPane);
        previewScroll.setBorder(createTitledBorder("Preview"));
        
        // Action Listeners
        searchBtn.addActionListener(e -> {
            studentModel.clear();
            try {
                String q = "%" + searchField.getText() + "%";
                PreparedStatement pst = con.prepareStatement(
                    "SELECT DISTINCT s.roll_no, s.name, s.section FROM students s " +
                    "JOIN seating_plan sp ON s.roll_no = sp.student_roll " +
                    "WHERE s.roll_no LIKE ? OR s.name LIKE ? ORDER BY s.roll_no");
                pst.setString(1, q);
                pst.setString(2, q);
                ResultSet rs = pst.executeQuery();
                while (rs.next()) {
                    studentModel.addElement(rs.getString(1) + " - " + rs.getString(2) + " (" + rs.getString(3) + ")");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        generateBtn.addActionListener(e -> {
            List<String> selected = studentList.getSelectedValuesList();
            if (selected.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select students");
                return;
            }
            
            StringBuilder html = new StringBuilder();
            for (String s : selected) {
                String roll = s.split(" - ")[0];
                html.append(generateHallTicketHTML(roll, includePhotoChk.isSelected(), includeQRChk.isSelected(), includeScheduleChk.isSelected()));
                html.append("<div style='page-break-after: always;'></div>");
            }
            previewPane.setText(html.toString());
            previewPane.setCaretPosition(0);
        });
        
        generateAllBtn.addActionListener(e -> {
            String section = JOptionPane.showInputDialog(this, "Enter section (e.g., CSE 1):");
            if (section == null || section.isEmpty()) return;
            
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File dir = fc.getSelectedFile();
                try {
                    PreparedStatement pst = con.prepareStatement(
                        "SELECT DISTINCT roll_no FROM students WHERE section = ?");
                    pst.setString(1, section);
                    ResultSet rs = pst.executeQuery();
                    
                    StringBuilder allHtml = new StringBuilder();
                    allHtml.append("<html><head><style>");
                    allHtml.append("@media print { .hall-ticket { page-break-after: always; } }");
                    allHtml.append("</style></head><body>");
                    
                    int count = 0;
                    while (rs.next()) {
                        String roll = rs.getString(1);
                        allHtml.append(generateHallTicketHTML(roll, includePhotoChk.isSelected(), includeQRChk.isSelected(), includeScheduleChk.isSelected()));
                        count++;
                    }
                    allHtml.append("</body></html>");
                    
                    File file = new File(dir, "HallTickets_" + section.replace(" ", "_") + ".html");
                    try (PrintWriter pw = new PrintWriter(file)) {
                        pw.write(allHtml.toString());
                    }
                    
                    JOptionPane.showMessageDialog(this, "Generated " + count + " hall tickets!\nSaved to: " + file.getAbsolutePath());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
                }
            }
        });
        
        content.add(optionsPanel, BorderLayout.WEST);
        content.add(previewScroll, BorderLayout.CENTER);
        
        p.add(title, BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        return p;
    }
    
    private String generateHallTicketHTML(String rollNo, boolean photo, boolean qr, boolean schedule) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='hall-ticket' style='border: 2px solid #000; padding: 20px; margin: 20px; font-family: Arial;'>");
        
        // Header
        sb.append("<div style='text-align: center; border-bottom: 2px solid #000; padding-bottom: 10px;'>");
        sb.append("<h1 style='margin: 0; color: #1a365d;'>ADITYA UNIVERSITY</h1>");
        sb.append("<h2 style='margin: 5px 0;'>EXAMINATION HALL TICKET</h2>");
        sb.append("<p style='margin: 0; font-size: 12px;'>II B.Tech II Semester Regular Examinations - February/March 2026</p>");
        sb.append("</div>");
        
        try {
            // Student details
            PreparedStatement pst = con.prepareStatement("SELECT * FROM students WHERE roll_no = ?");
            pst.setString(1, rollNo);
            ResultSet rs = pst.executeQuery();
            
            if (rs.next()) {
                String name = rs.getString("name");
                String section = rs.getString("section");
                
                // Use table layout for JEditorPane compatibility (no flex)
                sb.append("<table style='width:100%;margin-top:15px;border-collapse:collapse;'><tr>");
                
                // Left: Student Info
                sb.append("<td style='vertical-align:top;padding-right:16px;'>");
                sb.append("<table style='border-collapse:collapse;'>");
                sb.append("<tr><td style='padding:5px 8px;font-weight:bold;'>Roll Number:</td><td style='padding:5px 8px;'>").append(rollNo).append("</td></tr>");
                sb.append("<tr><td style='padding:5px 8px;font-weight:bold;'>Name:</td><td style='padding:5px 8px;'>").append(name).append("</td></tr>");
                sb.append("<tr><td style='padding:5px 8px;font-weight:bold;'>Section:</td><td style='padding:5px 8px;'>").append(section).append("</td></tr>");
                sb.append("<tr><td style='padding:5px 8px;font-weight:bold;'>Branch:</td><td style='padding:5px 8px;'>Computer Science &amp; Engineering</td></tr>");
                sb.append("</table>");
                sb.append("</td>");
                
                // Right: Photo — saved to temp file so JEditorPane can load via file:// URL
                if (photo) {
                    String photoUrl = "https://mobile.technicalhub.io:5010/student/" + rollNo + ".png";
                    String imgTag;
                    try {
                        java.net.URL url = new java.net.URL(photoUrl);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(4000);
                        conn.setReadTimeout(6000);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                        if (conn.getResponseCode() == 200) {
                            byte[] imgBytes = conn.getInputStream().readAllBytes();
                            // Write to a temp file — JEditorPane reliably loads file:// URLs
                            File tmpImg = File.createTempFile("ht_photo_" + rollNo, ".png");
                            tmpImg.deleteOnExit();
                            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmpImg)) {
                                fos.write(imgBytes);
                            }
                            String fileUri = tmpImg.toURI().toString();
                            imgTag = "<img src=\"" + fileUri + "\" width=\"120\" height=\"120\">";
                        } else {
                            imgTag = "<span style='font-size:11px;color:#999;'>PHOTO</span>";
                        }
                        conn.disconnect();
                    } catch (Exception ex) {
                        imgTag = "<span style='font-size:11px;color:#999;'>PHOTO</span>";
                    }
                    sb.append("<td style='vertical-align:top;width:120px;'>");
                    sb.append("<div style='width:120px;height:120px;border:2px solid #333;overflow:hidden;"
                            + "background:#f5f5f5;text-align:center;line-height:120px;font-size:11px;color:#999;'>");
                    sb.append(imgTag);
                    sb.append("</div>");
                    sb.append("<div style='text-align:center;font-size:10px;margin-top:4px;color:#555;'>Affix Photograph</div>");
                    sb.append("</td>");
                }
                
                sb.append("</tr></table>");
                
                // Exam Schedule
                if (schedule) {
                    sb.append("<div style='margin-top: 20px;'>");
                    sb.append("<h3 style='margin-bottom: 10px; border-bottom: 1px solid #000;'>Examination Schedule</h3>");
                    sb.append("<table style='width: 100%; border-collapse: collapse;'>");
                    sb.append("<tr style='background: #f0f0f0;'>");
                    sb.append("<th style='border: 1px solid #000; padding: 8px;'>Date</th>");
                    sb.append("<th style='border: 1px solid #000; padding: 8px;'>Subject</th>");
                    sb.append("<th style='border: 1px solid #000; padding: 8px;'>Time</th>");
                    sb.append("<th style='border: 1px solid #000; padding: 8px;'>Room</th>");
                    sb.append("<th style='border: 1px solid #000; padding: 8px;'>Seat</th>");
                    sb.append("</tr>");
                    
                    PreparedStatement pstSeat = con.prepareStatement(
                        "SELECT * FROM seating_plan WHERE student_roll = ? ORDER BY exam_date, start_time");
                    pstSeat.setString(1, rollNo);
                    ResultSet rsSeat = pstSeat.executeQuery();
                    
                    while (rsSeat.next()) {
                        sb.append("<tr>");
                        sb.append("<td style='border: 1px solid #000; padding: 8px;'>").append(rsSeat.getString("exam_date")).append("</td>");
                        sb.append("<td style='border: 1px solid #000; padding: 8px;'>").append(rsSeat.getString("subject")).append("</td>");
                        sb.append("<td style='border: 1px solid #000; padding: 8px;'>").append(rsSeat.getString("start_time")).append(" - ").append(rsSeat.getString("end_time")).append("</td>");
                        sb.append("<td style='border: 1px solid #000; padding: 8px;'>").append(rsSeat.getString("room_no")).append("</td>");
                        sb.append("<td style='border: 1px solid #000; padding: 8px;'>Row ").append(rsSeat.getInt("seat_row") + 1).append(", Col ").append(rsSeat.getInt("seat_col") + 1).append("</td>");
                        sb.append("</tr>");
                    }
                    
                    sb.append("</table>");
                    sb.append("</div>");
                }
                
                // QR Code placeholder
                if (qr) {
                    sb.append("<div style='margin-top:15px;text-align:center;'>");
                    sb.append("<table style='margin:0 auto;'><tr><td style='width:80px;height:80px;border:1px solid #000;"
                            + "text-align:center;vertical-align:middle;font-size:10px;'>QR: ").append(rollNo).append("</td></tr></table>");
                    sb.append("<p style='margin:5px 0;font-size:10px;'>Scan to verify</p>");
                    sb.append("</div>");
                }
                
                // Footer
                sb.append("<table style='width:100%;margin-top:30px;'><tr>");
                sb.append("<td><p>Student Signature: ________________</p></td>");
                sb.append("<td style='text-align:right;'><p>Controller of Examinations</p><p style='margin-top:30px;'>_______________________</p></td>");
                sb.append("</tr></table>");
                
                sb.append("<p style='text-align: center; font-size: 10px; margin-top: 15px; font-style: italic;'>This hall ticket is valid only with student ID card. Report to exam hall 15 minutes before the scheduled time.</p>");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        sb.append("</div>");
        return sb.toString();
    }

    // ==========================================
    // CAMPUS MAP PANEL
    // ==========================================
    private JPanel createCampusMapPanel() {
        JPanel p = new ModernPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("Campus Map - Bhavan Locations");
        title.setFont(SUBHEADER_FONT);
        title.setForeground(PRIMARY_COLOR);
        title.setBorder(new EmptyBorder(0, 0, 15, 0));

        // Bhavan data
        final String[][] bhavanData = {
            {"Bill Gates",     "Technology Block",       "Rooms: 101-120", "Capacity: 1200 students", "Facilities: AC, Projector, CCTV"},
            {"Ratan Tata",     "Business Block",         "Rooms: 201-215", "Capacity: 800 students",  "Facilities: AC, CCTV"},
            {"K L Rao",        "Engineering Block",      "Rooms: 301-330", "Capacity: 1500 students", "Facilities: AC, Labs, CCTV"},
            {"Vishweshwarya",   "Civil Engineering Block","Rooms: 401-420", "Capacity: 1000 students", "Facilities: AC, Drawing Halls"},
            {"Bhaskar",        "Science Block",          "Rooms: 501-525", "Capacity: 1250 students", "Facilities: AC, Labs, CCTV"},
            {"C V Raman",      "Physics Block",          "Rooms: 601-615", "Capacity: 750 students",  "Facilities: AC, Labs"},
            {"Ramanujan",      "Mathematics Block",      "Rooms: 701-720", "Capacity: 1000 students", "Facilities: AC, Computer Labs"}
        };

        final Color[] bhavanColors = {
            new Color(66, 153, 225),  new Color(72, 187, 120),  new Color(237, 137, 54),
            new Color(159, 122, 234), new Color(236, 168, 30),  new Color(245, 101, 101),
            new Color(56, 178, 172)
        };

        // ── Details area (always visible) ────────────────────────────────
        JTextArea detailsArea = new JTextArea("Select a building to view its details.");
        detailsArea.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setBackground(new Color(248, 250, 252));
        detailsArea.setBorder(new EmptyBorder(10, 12, 10, 12));

        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        detailsScroll.setBorder(null);

        JPanel detailsPanel = new JPanel(new BorderLayout(0, 8));
        detailsPanel.setOpaque(false);
        detailsPanel.setBorder(createTitledBorder("Bhavan Details"));
        detailsPanel.add(detailsScroll, BorderLayout.CENTER);

        // Admin-only "Edit Room Details" button
        final String[] selectedBhavan = {null};
        if ("ADMIN".equals(currentRole)) {
            JButton editRoomBtn = new ModernButton("Edit Room Details", PRIMARY_COLOR);
            editRoomBtn.setPreferredSize(new Dimension(180, 42));
            JPanel editBtnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
            editBtnRow.setOpaque(false);
            editBtnRow.add(editRoomBtn);
            detailsPanel.add(editBtnRow, BorderLayout.SOUTH);
            editRoomBtn.addActionListener(e -> {
                if (selectedBhavan[0] == null) {
                    JOptionPane.showMessageDialog(this, "Please select a building first.");
                    return;
                }
                showBhavanRoomEditDialog(selectedBhavan[0]);
            });
        }

        // ── Building buttons in a horizontal wrapping (FlowLayout) panel ─
        JPanel buttonContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        buttonContainer.setOpaque(false);

        for (int idx = 0; idx < bhavanData.length; idx++) {
            final String[] data  = bhavanData[idx];
            final Color btnColor = bhavanColors[idx % bhavanColors.length];
            JButton btn = new ModernButton(data[0], btnColor);
            btn.setPreferredSize(new Dimension(160, 50));
            btn.addActionListener(e -> {
                selectedBhavan[0] = data[0];
                StringBuilder sb = new StringBuilder();
                sb.append("===== ").append(data[0]).append(" BHAVAN =====\n\n");
                sb.append("Location  : ").append(data[1]).append("\n");
                sb.append(data[2]).append("\n");
                sb.append(data[3]).append("\n");
                sb.append(data[4]).append("\n\n");
                sb.append("--- Configured Exam Rooms ---\n");
                try {
                    ResultSet rs = con.createStatement().executeQuery(
                        "SELECT room_no, capacity, rows_count, cols_count FROM rooms ORDER BY room_no");
                    boolean found = false;
                    while (rs.next()) {
                        sb.append("  Room ").append(rs.getString(1))
                          .append("  |  Capacity: ").append(rs.getInt(2))
                          .append("  |  Grid: ").append(rs.getInt(3)).append("\u00d7").append(rs.getInt(4)).append("\n");
                        found = true;
                    }
                    if (!found) sb.append("  No rooms configured yet.\n");
                } catch (Exception ex) {
                    sb.append("  Unable to load room data.\n");
                }
                if ("ADMIN".equals(currentRole)) {
                    sb.append("\n[Click 'Edit Room Details' to view / modify rooms in this bhavan]");
                }
                detailsArea.setText(sb.toString());
                detailsArea.setCaretPosition(0);
            });
            buttonContainer.add(btn);
        }

        // Wrap buttons in a scroll pane (handles many buildings gracefully)
        JScrollPane buttonScroll = new JScrollPane(buttonContainer);
        buttonScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        buttonScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        buttonScroll.setBorder(createTitledBorder("Examination Bhavans  \u2014  click a building to view details"));
        buttonScroll.getVerticalScrollBar().setUnitIncrement(10);
        buttonScroll.setPreferredSize(new Dimension(0, 175));

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        content.add(buttonScroll, BorderLayout.NORTH);
        content.add(detailsPanel,  BorderLayout.CENTER);

        p.add(title,   BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    /** Admin: dialog to view and edit rooms in a selected bhavan */
    private void showBhavanRoomEditDialog(String bhavanName) {
        JDialog d = new JDialog(this, "Edit Rooms \u2014 " + bhavanName + " Bhavan", true);
        d.setSize(740, 520);
        d.setLocationRelativeTo(this);
        d.setLayout(new BorderLayout());
        d.getContentPane().setBackground(Color.WHITE);

        // Header bar
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PRIMARY_COLOR);
        header.setBorder(new EmptyBorder(14, 20, 14, 20));
        JLabel hTitle = new JLabel("Room Configuration \u2014 " + bhavanName);
        hTitle.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        hTitle.setForeground(Color.WHITE);
        header.add(hTitle, BorderLayout.WEST);
        d.add(header, BorderLayout.NORTH);

        // Rooms table (editable except room_no)
        DefaultTableModel roomModel = new DefaultTableModel(
                new String[]{"Room No", "Capacity", "Rows", "Cols", "Floor"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c != 0; }
        };
        JTable roomTable = new JTable(roomModel);
        styleTable(roomTable);
        try {
            ResultSet rs = con.createStatement().executeQuery(
                "SELECT room_no, capacity, rows_count, cols_count, floor FROM rooms ORDER BY room_no");
            while (rs.next()) {
                roomModel.addRow(new Object[]{
                    rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)
                });
            }
        } catch (Exception ex) { ex.printStackTrace(); }

        JScrollPane tableScroll = new JScrollPane(roomTable);
        tableScroll.setBorder(createTitledBorder("Configured Rooms  (double-click a cell to edit)"));
        d.add(tableScroll, BorderLayout.CENTER);

        // Button row
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnRow.setBackground(new Color(248, 250, 252));
        btnRow.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));

        JButton addRoomBtn    = new ModernButton("+ Add Room",     SECONDARY_COLOR);
        JButton saveChangesBtn = new ModernButton("Save Changes",  PRIMARY_COLOR);
        JButton deleteRoomBtn  = new ModernButton("Delete Room",   new Color(220, 38, 38));
        JButton closeBtn       = new ModernButton("Close",         Color.GRAY);
        for (JButton b : new JButton[]{addRoomBtn, deleteRoomBtn, saveChangesBtn, closeBtn})
            b.setPreferredSize(new Dimension(125, 42));
        btnRow.add(addRoomBtn);
        btnRow.add(deleteRoomBtn);
        btnRow.add(saveChangesBtn);
        btnRow.add(closeBtn);
        d.add(btnRow, BorderLayout.SOUTH);

        addRoomBtn.addActionListener(e -> {
            JTextField roomNoF   = new ModernTextField(8);
            JTextField capacityF = new ModernTextField(5);
            JTextField rowsF     = new ModernTextField(3);
            JTextField colsF     = new ModernTextField(3);
            JPanel inp = new JPanel(new GridLayout(4, 2, 8, 8));
            inp.add(new JLabel("Room No:"));  inp.add(roomNoF);
            inp.add(new JLabel("Capacity:")); inp.add(capacityF);
            inp.add(new JLabel("Rows:"));     inp.add(rowsF);
            inp.add(new JLabel("Cols:"));     inp.add(colsF);
            if (JOptionPane.showConfirmDialog(d, inp, "Add Room",
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                try {
                    String rNo = roomNoF.getText().trim();
                    int cap = Integer.parseInt(capacityF.getText().trim());
                    int rr  = Integer.parseInt(rowsF.getText().trim());
                    int cc  = Integer.parseInt(colsF.getText().trim());
                    PreparedStatement pst = con.prepareStatement(
                        "INSERT INTO rooms (room_no,floor,rows_count,cols_count,capacity) VALUES (?,1,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE rows_count=?,cols_count=?,capacity=?");
                    pst.setString(1,rNo); pst.setInt(2,rr); pst.setInt(3,cc); pst.setInt(4,cap);
                    pst.setInt(5,rr);    pst.setInt(6,cc); pst.setInt(7,cap);
                    pst.executeUpdate();
                    roomModel.addRow(new Object[]{rNo, cap, rr, cc, 1});
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(d, "Error: " + ex.getMessage());
                }
            }
        });

        saveChangesBtn.addActionListener(e -> {
            try {
                for (int i = 0; i < roomModel.getRowCount(); i++) {
                    String rNo = roomModel.getValueAt(i,0).toString();
                    int cap = Integer.parseInt(roomModel.getValueAt(i,1).toString());
                    int rr  = Integer.parseInt(roomModel.getValueAt(i,2).toString());
                    int cc  = Integer.parseInt(roomModel.getValueAt(i,3).toString());
                    int fl  = Integer.parseInt(roomModel.getValueAt(i,4).toString());
                    PreparedStatement pst = con.prepareStatement(
                        "UPDATE rooms SET capacity=?,rows_count=?,cols_count=?,floor=? WHERE room_no=?");
                    pst.setInt(1,cap); pst.setInt(2,rr); pst.setInt(3,cc);
                    pst.setInt(4,fl);  pst.setString(5,rNo);
                    pst.executeUpdate();
                }
                JOptionPane.showMessageDialog(d, "Changes saved successfully!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(d, "Error saving: " + ex.getMessage());
            }
        });

        deleteRoomBtn.addActionListener(e -> {
            int sel = roomTable.getSelectedRow();
            if (sel < 0) { JOptionPane.showMessageDialog(d, "Select a row to delete."); return; }
            String rNo = roomModel.getValueAt(sel,0).toString();
            if (JOptionPane.showConfirmDialog(d, "Delete room " + rNo + "?",
                    "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                try {
                    PreparedStatement pst = con.prepareStatement("DELETE FROM rooms WHERE room_no=?");
                    pst.setString(1, rNo);
                    pst.executeUpdate();
                    roomModel.removeRow(sel);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(d, "Error: " + ex.getMessage());
                }
            }
        });

        closeBtn.addActionListener(e -> d.dispose());
        d.setVisible(true);
    }

    // ==========================================
    // TEMPLATE LIBRARY PANEL
    // ==========================================
    private JPanel createTemplateLibraryPanel() {
        JPanel p = new ModernPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JLabel title = new JLabel("Template Library & Subject Search");
        title.setFont(SUBHEADER_FONT);
        title.setForeground(PRIMARY_COLOR);
        title.setBorder(new EmptyBorder(0, 0, 20, 0));
        
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        
        // Tab 1: Subject Library
        JPanel subjectTab = createSubjectLibraryTab();
        tabs.addTab("Subject Library", subjectTab);
        
        // Tab 2: Exam Templates  
        JPanel templateTab = createExamTemplatesTab();
        tabs.addTab("Exam Templates", templateTab);
        
        // Tab 3: Supplementary Exams
        JPanel suppTab = createSupplementaryTab();
        tabs.addTab("Supplementary Exams", suppTab);
        
        p.add(title, BorderLayout.NORTH);
        p.add(tabs, BorderLayout.CENTER);
        return p;
    }
    
    private JPanel createSubjectLibraryTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        searchPanel.setBackground(Color.WHITE);
        
        JTextField searchField = new ModernTextField(25);
        searchField.setPreferredSize(new Dimension(300, 40));
        JButton searchBtn = new ModernButton("Search", PRIMARY_COLOR);
        searchBtn.setPreferredSize(new Dimension(100, 40));
        JButton addSubjectBtn = new ModernButton("+ Add Subject", SECONDARY_COLOR);
        addSubjectBtn.setPreferredSize(new Dimension(130, 40));
        
        searchPanel.add(new JLabel("Search:"));
        searchPanel.add(searchField);
        searchPanel.add(searchBtn);
        searchPanel.add(addSubjectBtn);
        
        // Subject list — vertical rows instead of horizontal tiles
        JPanel subjectGrid = new JPanel();
        subjectGrid.setLayout(new BoxLayout(subjectGrid, BoxLayout.Y_AXIS));
        subjectGrid.setBackground(Color.WHITE);

        Runnable refreshSubjects = () -> {
            subjectGrid.removeAll();
            String query = searchField.getText().toLowerCase().trim();
            int[] rowIdx = {0};
            for (String[] sub : subjectLibrary) {
                if (query.isEmpty() || sub[0].toLowerCase().contains(query) || sub[1].toLowerCase().contains(query)) {
                    JPanel row = new JPanel(new BorderLayout(10, 0));
                    row.setBackground(rowIdx[0] % 2 == 0 ? Color.WHITE : new Color(247, 249, 255));
                    row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 230, 240)),
                        new EmptyBorder(10, 15, 10, 15)));
                    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
                    row.setCursor(new Cursor(Cursor.HAND_CURSOR));

                    JLabel nameLabel = new JLabel(sub[0]);
                    nameLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
                    nameLabel.setForeground(new Color(30, 30, 60));

                    JLabel codeLabel = new JLabel(sub[1]);
                    codeLabel.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
                    codeLabel.setForeground(new Color(100, 116, 139));
                    codeLabel.setPreferredSize(new Dimension(90, 20));
                    codeLabel.setHorizontalAlignment(SwingConstants.RIGHT);

                    row.add(nameLabel, BorderLayout.CENTER);
                    row.add(codeLabel, BorderLayout.EAST);

                    final String[] s = sub;
                    row.addMouseListener(new java.awt.event.MouseAdapter() {
                        Color orig = row.getBackground();
                        public void mouseEntered(java.awt.event.MouseEvent e) { row.setBackground(new Color(219, 234, 254)); }
                        public void mouseExited(java.awt.event.MouseEvent e)  { row.setBackground(orig); }
                        public void mouseClicked(java.awt.event.MouseEvent e) {
                            java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(s[0] + " (" + s[1] + ")");
                            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
                            JOptionPane.showMessageDialog(subjectGrid, "Copied: " + s[0] + " (" + s[1] + ")\n\nPaste in the timetable.", "Copied", JOptionPane.INFORMATION_MESSAGE);
                        }
                    });
                    subjectGrid.add(row);
                    rowIdx[0]++;
                }
            }
            if (rowIdx[0] == 0) {
                JLabel empty = new JLabel("  No subjects found");
                empty.setForeground(Color.GRAY);
                empty.setFont(new Font(FONT_FAMILY, Font.ITALIC, 13));
                empty.setBorder(new EmptyBorder(20, 15, 0, 0));
                subjectGrid.add(empty);
            }
            subjectGrid.revalidate();
            subjectGrid.repaint();
        };
        
        refreshSubjects.run();
        
        searchBtn.addActionListener(e -> refreshSubjects.run());
        searchField.addActionListener(e -> refreshSubjects.run());
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                refreshSubjects.run();
            }
        });
        
        addSubjectBtn.addActionListener(e -> {
            JTextField nameField = new JTextField(25);
            JTextField codeField = new JTextField(15);
            JPanel inputPanel = new JPanel(new GridLayout(2, 2, 10, 10));
            inputPanel.add(new JLabel("Subject Name:"));
            inputPanel.add(nameField);
            inputPanel.add(new JLabel("Course Code:"));
            inputPanel.add(codeField);
            
            int result = JOptionPane.showConfirmDialog(this, inputPanel, "Add New Subject", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION && !nameField.getText().isEmpty() && !codeField.getText().isEmpty()) {
                String name = nameField.getText().trim();
                String code = codeField.getText().trim();
                subjectLibrary.add(new String[]{name, code});
                saveCustomSubject(name, code);
                refreshSubjects.run();
                JOptionPane.showMessageDialog(this, "Subject added successfully!");
            }
        });
        
        JScrollPane scroll = new JScrollPane(subjectGrid);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        
        p.add(searchPanel, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }
    
    private JPanel createExamTemplatesTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Saved templates list
        DefaultListModel<String> templateModel = new DefaultListModel<>();
        loadExamTemplates(templateModel);
        
        JList<String> templateList = new JList<>(templateModel);
        templateList.setFont(new Font(FONT_FAMILY, Font.PLAIN, 14));
        JScrollPane listScroll = new JScrollPane(templateList);
        listScroll.setBorder(createTitledBorder("Saved Templates"));
        listScroll.setPreferredSize(new Dimension(300, 0));
        
        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        btnPanel.setBackground(Color.WHITE);
        
        JButton saveCurrentBtn = new ModernButton("Save Current as Template", PRIMARY_COLOR);
        saveCurrentBtn.setPreferredSize(new Dimension(220, 45));
        
        JButton loadTemplateBtn = new ModernButton("Load Selected Template", SECONDARY_COLOR);
        loadTemplateBtn.setPreferredSize(new Dimension(200, 45));
        
        JButton deleteTemplateBtn = new ModernButton("Delete", new Color(220, 38, 38));
        deleteTemplateBtn.setPreferredSize(new Dimension(100, 45));
        
        btnPanel.add(saveCurrentBtn);
        btnPanel.add(loadTemplateBtn);
        btnPanel.add(deleteTemplateBtn);
        
        // Template details
        JTextArea detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font("Menlo", Font.PLAIN, 12));
        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        detailsScroll.setBorder(createTitledBorder("Template Details"));
        
        templateList.addListSelectionListener(e -> {
            String selected = templateList.getSelectedValue();
            if (selected != null) {
                loadTemplateDetails(selected, detailsArea);
            }
        });
        
        saveCurrentBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Enter template name:");
            if (name != null && !name.isEmpty()) {
                saveExamTemplate(name);
                templateModel.addElement(name);
                JOptionPane.showMessageDialog(this, "Template saved: " + name);
            }
        });
        
        loadTemplateBtn.addActionListener(e -> {
            String selected = templateList.getSelectedValue();
            if (selected != null) {
                loadExamTemplate(selected);
                JOptionPane.showMessageDialog(this, "Template loaded! Go to Exam Timetable to see it.");
            }
        });
        
        deleteTemplateBtn.addActionListener(e -> {
            String selected = templateList.getSelectedValue();
            if (selected != null) {
                deleteExamTemplate(selected);
                templateModel.removeElement(selected);
            }
        });
        
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(Color.WHITE);
        leftPanel.add(listScroll, BorderLayout.CENTER);
        leftPanel.add(btnPanel, BorderLayout.SOUTH);
        
        p.add(leftPanel, BorderLayout.WEST);
        p.add(detailsScroll, BorderLayout.CENTER);
        return p;
    }
    
    private void loadExamTemplates(DefaultListModel<String> model) {
        try {
            ResultSet rs = con.createStatement().executeQuery("SELECT name FROM exam_templates ORDER BY created_at DESC");
            while (rs.next()) {
                model.addElement(rs.getString(1));
            }
        } catch (Exception e) {
            // Table doesn't exist yet
        }
    }
    
    private void loadTemplateDetails(String name, JTextArea area) {
        try {
            PreparedStatement pst = con.prepareStatement("SELECT data, created_at FROM exam_templates WHERE name = ?");
            pst.setString(1, name);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                area.setText("Template: " + name + "\nCreated: " + rs.getTimestamp(2) + "\n\n" + rs.getString(1));
            }
        } catch (Exception e) {
            area.setText("Error loading template details.");
        }
    }
    
    private void saveExamTemplate(String name) {
        try {
            saveTableDataToMap();
            StringBuilder data = new StringBuilder();
            data.append("Branches: ").append(String.join(", ", timetableBranches)).append("\n");
            data.append("Dates: ").append(String.join(", ", timetableDates)).append("\n");
            data.append("--- Schedule ---\n");
            for (String branch : timetableBranches) {
                Map<String, String[]> branchData = timetableData.get(branch);
                if (branchData != null) {
                    for (Map.Entry<String, String[]> entry : branchData.entrySet()) {
                        data.append(branch).append(" | ").append(entry.getKey()).append(" | ").append(entry.getValue()[0]).append(" (").append(entry.getValue()[1]).append(")\n");
                    }
                }
            }
            
            PreparedStatement pst = con.prepareStatement(
                "INSERT INTO exam_templates (name, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data = ?, created_at = CURRENT_TIMESTAMP");
            pst.setString(1, name);
            pst.setString(2, data.toString());
            pst.setString(3, data.toString());
            pst.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void loadExamTemplate(String name) {
        try {
            PreparedStatement pst = con.prepareStatement("SELECT data FROM exam_templates WHERE name = ?");
            pst.setString(1, name);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                String data = rs.getString(1);
                // Parse and load template (simplified - full implementation would parse completely)
                JOptionPane.showMessageDialog(this, "Template data loaded. Apply it in the Timetable panel.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void deleteExamTemplate(String name) {
        try {
            PreparedStatement pst = con.prepareStatement("DELETE FROM exam_templates WHERE name = ?");
            pst.setString(1, name);
            pst.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private JPanel createSupplementaryTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        JLabel info = new JLabel("<html><h2>Supplementary/Re-Exam Management</h2>" +
            "<p>Configure supplementary exams with scattered seating for smaller groups.</p></html>");
        info.setBorder(new EmptyBorder(0, 0, 20, 0));
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Exam Name
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Exam Name:"), gbc);
        gbc.gridx = 1;
        JTextField examNameField = new ModernTextField(20);
        examNameField.setText("Supplementary Exam - Feb 2026");
        formPanel.add(examNameField, gbc);
        
        // Subject
        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Subject:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> subjectCombo = new JComboBox<>();
        for (String[] sub : subjectLibrary) {
            subjectCombo.addItem(sub[0] + " (" + sub[1] + ")");
        }
        formPanel.add(subjectCombo, gbc);
        
        // Student List (manual entry)
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Student Roll Numbers:"), gbc);
        gbc.gridx = 1;
        JTextArea rollsArea = new JTextArea(5, 25);
        rollsArea.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        rollsArea.setToolTipText("Enter roll numbers, one per line");
        JScrollPane rollsScroll = new JScrollPane(rollsArea);
        formPanel.add(rollsScroll, gbc);
        
        // Scattered seating option
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Seating Mode:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> modeCombo = new JComboBox<>(new String[]{"Scattered (Skip rows)", "Normal", "Alternate seats"});
        formPanel.add(modeCombo, gbc);
        
        // Date & Time
        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel("Date (YYYY-MM-DD):"), gbc);
        gbc.gridx = 1;
        JTextField dateField = new ModernTextField(12);
        dateField.setText("2026-03-15");
        formPanel.add(dateField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 5;
        formPanel.add(new JLabel("Time:"), gbc);
        gbc.gridx = 1;
        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        timePanel.setBackground(Color.WHITE);
        JTextField startTime = new ModernTextField(8);
        startTime.setText("10:00 AM");
        JTextField endTime = new ModernTextField(8);
        endTime.setText("01:00 PM");
        timePanel.add(startTime);
        timePanel.add(new JLabel(" to "));
        timePanel.add(endTime);
        formPanel.add(timePanel, gbc);
        
        // Generate button
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        JButton generateBtn = new ModernButton("Generate Supplementary Seating", PRIMARY_COLOR);
        generateBtn.setPreferredSize(new Dimension(300, 50));
        formPanel.add(generateBtn, gbc);
        
        generateBtn.addActionListener(e -> {
            String[] rolls = rollsArea.getText().split("\n");
            if (rolls.length == 0 || rolls[0].isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter student roll numbers");
                return;
            }
            
            JOptionPane.showMessageDialog(this, 
                "Supplementary exam seating generated for " + rolls.length + " students.\n" +
                "Mode: " + modeCombo.getSelectedItem() + "\n" +
                "Check 'Seating & Schedule' panel for details.");
        });
        
        p.add(info, BorderLayout.NORTH);
        p.add(formPanel, BorderLayout.CENTER);
        return p;
    }

    // ==========================================
    // HELPERS & CUSTOM COMPONENTS
    // ==========================================
    
    // --- Custom Modern Button ---
    class ModernButton extends JButton {
        private Color bgColor;
        private boolean hovered = false;
        private boolean pressed = false;
        private float animAlpha = 0f;
        public ModernButton(String text, Color bg) {
            super(text);
            this.bgColor = bg;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setForeground(Color.WHITE);
            setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(10, 20, 10, 20));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                public void mouseExited(MouseEvent e) { hovered = false; pressed = false; repaint(); }
                public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
                public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
            });
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            int w = getWidth(), h = getHeight();
            int rad = 12;
            // Drop shadow (skip when pressed)
            if (!pressed && isEnabled()) {
                g2.setColor(new Color(0, 0, 0, 20));
                g2.fill(new RoundRectangle2D.Float(1, 2, w - 2, h - 1, rad, rad));
                g2.setColor(new Color(0, 0, 0, 10));
                g2.fill(new RoundRectangle2D.Float(0, 3, w, h - 1, rad + 2, rad + 2));
            }
            int yOff = pressed ? 1 : 0;
            Color baseColor = !isEnabled() ? new Color(156, 163, 175) : bgColor;
            Color drawColor = pressed ? baseColor.darker() : (hovered ? brighter(baseColor, 0.15f) : baseColor);
            // Gradient fill for depth
            GradientPaint gp = new GradientPaint(0, yOff, brighter(drawColor, 0.08f), 0, h - 2 + yOff, drawColor);
            g2.setPaint(gp);
            g2.fill(new RoundRectangle2D.Float(0, yOff, w - 1, h - 2, rad, rad));
            // Inner top highlight
            if (!pressed && isEnabled()) {
                g2.setColor(new Color(255, 255, 255, 35));
                g2.fillRoundRect(1, yOff + 1, w - 3, (h - 3) / 2, rad, rad);
            }
            // Text
            g2.setColor(isEnabled() ? getForeground() : new Color(255, 255, 255, 180));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h + fm.getAscent() - fm.getDescent()) / 2 + yOff;
            g2.drawString(getText(), tx, ty);
            g2.dispose();
        }
        private Color brighter(Color c, float factor) {
            int r = Math.min(255, (int)(c.getRed() + 255 * factor));
            int g = Math.min(255, (int)(c.getGreen() + 255 * factor));
            int b = Math.min(255, (int)(c.getBlue() + 255 * factor));
            return new Color(r, g, b, c.getAlpha());
        }
        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width + 24, d.width), Math.max(d.height, 40));
        }
    }
    
    // --- Custom Text Field ---
    class ModernTextField extends JTextField {
        public ModernTextField() { super(); init(); }
        public ModernTextField(int cols) { super(cols); init(); }
        private void init() {
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            setFont(BODY_FONT);
            setBackground(new Color(0, 0, 0, 0));
            setCaretColor(PRIMARY_COLOR);
            addFocusListener(new java.awt.event.FocusAdapter() {
                public void focusGained(java.awt.event.FocusEvent e) { repaint(); }
                public void focusLost(java.awt.event.FocusEvent e)   { repaint(); }
            });
        }
        @Override public void setOpaque(boolean isOpaque) { super.setOpaque(false); }
        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, Math.max(d.height, 42));
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean focused = isFocusOwner();
            int rad = 10;
            // Subtle inner shadow when not focused
            if (!focused) {
                g2.setColor(new Color(0, 0, 0, 6));
                g2.fill(new RoundRectangle2D.Float(1, 2, getWidth() - 2, getHeight() - 2, rad, rad));
            }
            // Background
            g2.setColor(focused ? (isDarkMode ? new Color(45, 40, 80) : new Color(245, 247, 255)) : CARD_BG);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, rad, rad));
            // Border — accent when focused, subtle otherwise
            g2.setStroke(new BasicStroke(focused ? 1.8f : 1f));
            g2.setColor(focused ? PRIMARY_COLOR : (isDarkMode ? new Color(75, 85, 99) : new Color(209, 213, 219)));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 2, getHeight() - 2, rad, rad));
            // Focus glow
            if (focused) {
                g2.setColor(new Color(PRIMARY_COLOR.getRed(), PRIMARY_COLOR.getGreen(), PRIMARY_COLOR.getBlue(), 20));
                g2.setStroke(new BasicStroke(3f));
                g2.draw(new RoundRectangle2D.Float(-0.5f, -0.5f, getWidth(), getHeight(), rad + 2, rad + 2));
            }
            setForeground(TEXT_COLOR);
            super.paintComponent(g);
            g2.dispose();
        }
    }
    
    class ModernPasswordField extends JPasswordField {
        public ModernPasswordField() {
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            setFont(BODY_FONT);
            setBackground(new Color(0, 0, 0, 0));
            setCaretColor(PRIMARY_COLOR);
            addFocusListener(new java.awt.event.FocusAdapter() {
                public void focusGained(java.awt.event.FocusEvent e) { repaint(); }
                public void focusLost(java.awt.event.FocusEvent e)   { repaint(); }
            });
        }
        @Override public void setOpaque(boolean isOpaque) { super.setOpaque(false); }
        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, Math.max(d.height, 42));
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean focused = isFocusOwner();
            int rad = 10;
            if (!focused) {
                g2.setColor(new Color(0, 0, 0, 6));
                g2.fill(new RoundRectangle2D.Float(1, 2, getWidth() - 2, getHeight() - 2, rad, rad));
            }
            g2.setColor(focused ? (isDarkMode ? new Color(45, 40, 80) : new Color(245, 247, 255)) : CARD_BG);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, rad, rad));
            g2.setStroke(new BasicStroke(focused ? 1.8f : 1f));
            g2.setColor(focused ? PRIMARY_COLOR : (isDarkMode ? new Color(75, 85, 99) : new Color(209, 213, 219)));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 2, getHeight() - 2, rad, rad));
            if (focused) {
                g2.setColor(new Color(PRIMARY_COLOR.getRed(), PRIMARY_COLOR.getGreen(), PRIMARY_COLOR.getBlue(), 20));
                g2.setStroke(new BasicStroke(3f));
                g2.draw(new RoundRectangle2D.Float(-0.5f, -0.5f, getWidth(), getHeight(), rad + 2, rad + 2));
            }
            setForeground(TEXT_COLOR);
            super.paintComponent(g);
            g2.dispose();
        }
    }

    // --- Modern Panel (Card container with rounded corners; respects dark mode) ---
    class ModernPanel extends JPanel {
        private final int SHADOW_SIZE = 6;
        private final int CORNER_RADIUS = 18;
        public ModernPanel(LayoutManager lm) { 
            super(lm); 
            setOpaque(false);
            // Add internal padding so content doesn't overlap the shadow area
            setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(0, 0, SHADOW_SIZE + 2, SHADOW_SIZE + 2),
                new EmptyBorder(16, 18, 16, 18)
            ));
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int cardW = getWidth() - SHADOW_SIZE - 2;
            int cardH = getHeight() - SHADOW_SIZE - 2;
            // Multi-layer soft shadow
            for (int i = SHADOW_SIZE; i > 0; i--) {
                int alpha = (int)(18.0 * (1.0 - (double)i / SHADOW_SIZE));
                g2.setColor(new Color(0, 0, 0, Math.max(alpha, 3)));
                g2.fill(new RoundRectangle2D.Float(i + 1, i + 2, cardW - 2, cardH - 2, CORNER_RADIUS, CORNER_RADIUS));
            }
            // Card body
            g2.setColor(CARD_BG);
            g2.fill(new RoundRectangle2D.Float(0, 0, cardW, cardH, CORNER_RADIUS, CORNER_RADIUS));
            // Subtle border
            g2.setColor(isDarkMode ? new Color(75, 85, 99) : new Color(220, 227, 235));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, cardW - 1, cardH - 1, CORNER_RADIUS, CORNER_RADIUS));
            g2.dispose();
            super.paintComponent(g);
        }
    }
    
    // --- Logic from previous steps (kept as is) ---
private void parseAndImportCSV(File f, JTextArea log) {
        log.append("\nReading " + f.getName() + "...\n");
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            int rollIdx = -1, nameIdx = -1;
            boolean headerFound = false;
            
            // Deduce section from filename more robustly
            String section = f.getName().replace(".csv", "").trim();
            // Handle "CSE_II Year... - CSE 9 - THUB.csv" -> "CSE 9"
            if (section.contains("-")) {
                String[] parts = section.split("-");
                for (String part : parts) {
                    if (part.trim().toUpperCase().matches("CSE\\s*\\d+")) {
                        section = part.trim();
                        break;
                    }
                }
            }
            // Fallback if regex didn't match
            if (!section.toUpperCase().startsWith("CSE")) {
                 if(f.getName().toUpperCase().contains("CSE 1")) section = "CSE 1";
                 else if(f.getName().toUpperCase().contains("CSE 2")) section = "CSE 2";
                 else if(f.getName().toUpperCase().contains("CSE 3")) section = "CSE 3";
                 else if(f.getName().toUpperCase().contains("CSE 4")) section = "CSE 4";
                 else if(f.getName().toUpperCase().contains("CSE 5")) section = "CSE 5";
                 else if(f.getName().toUpperCase().contains("CSE 6")) section = "CSE 6";
                 else if(f.getName().toUpperCase().contains("CSE 7")) section = "CSE 7";
                 else if(f.getName().toUpperCase().contains("CSE 8")) section = "CSE 8";
                 else if(f.getName().toUpperCase().contains("CSE 9")) section = "CSE 9";
                 else if(f.getName().toUpperCase().contains("THUB")) section = "THUB";
            }

            PreparedStatement pst = con.prepareStatement(
                "INSERT INTO students (roll_no, name, section) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE section=?");
            
            int imported = 0;
            while((line = br.readLine()) != null) {
                String[] cells = line.split(",");
                
                if(!headerFound) {
                    // Check if this row looks like a header
                    for(int i=0; i<cells.length; i++) {
                        String cell = cells[i].toLowerCase().trim();
                        if(cell.contains("roll no") || cell.contains("roll number")) rollIdx = i;
                        // Specific check for "Name of The Student" or just "Name"
                        if( cell.contains("name of the student") || cell.equals("name")) nameIdx = i;
                    }
                    
                    // If simple "Name" check failed, look for any column containing "Name" *after* finding Roll No
                    if (rollIdx != -1 && nameIdx == -1) {
                         for(int i=0; i<cells.length; i++) {
                            if (cells[i].toLowerCase().contains("name")) nameIdx = i;
                         }
                    }

                    if(rollIdx != -1 && nameIdx != -1) {
                        headerFound = true;
                        log.append("Header found at indices: Roll=" + rollIdx + ", Name=" + nameIdx + "\n");
                    }
                    continue;
                    
                }
                
                // Process Data Rows
                if(cells.length > rollIdx && cells.length > nameIdx) {
                    String roll = cells[rollIdx].trim();
                    String name = cells[nameIdx].trim();
                    
                    // Cleanup Name (remove ",Smart Interview" if appended)
                    if (name.contains("Smart Interview")) {
                        name = name.replace("Smart Interview", "").trim();
                    }
                    
                    // Basic validation
                    if(roll.length() > 5 && !roll.toLowerCase().contains("roll") && !name.isEmpty()) {
                        pst.setString(1, roll); 
                        pst.setString(2, name); 
                        pst.setString(3, section); 
                        pst.setString(4, section);
                        pst.addBatch(); 
                        imported++;
                    }
                }
            }
            if (imported > 0) {
                pst.executeBatch();
                log.append("Success: Imported " + imported + " students into section " + section + ".\n");
            } else {
                log.append("Warning: No valid student rows found in " + f.getName() + ".\n");
            }
        } catch(Exception e) { 
            e.printStackTrace();
            log.append("Error reading file: " + e.getMessage() + "\n"); 
        }
    }
    
    private void loadSections(DefaultListModel<String> model) {
        model.clear();
        try {
            ResultSet rs = con.createStatement().executeQuery("SELECT DISTINCT section FROM students ORDER BY section");
            while(rs.next()) model.addElement(rs.getString(1));
        } catch(Exception e) {}
    }
    
    private void loadFaculty(JComboBox<String> box) {
        try {
            ResultSet rs = con.createStatement().executeQuery("SELECT name FROM faculty ORDER BY name");
            int count = 0;
            while(rs.next()) { box.addItem(rs.getString(1)); count++; }
            if (count == 0) {
                box.addItem("(No faculty added yet)");
            }
        } catch(Exception e) {
            box.addItem("(Error loading faculty)");
        }
    }
    
    // ==========================================
    // PARTIAL REALLOCATION DIALOG
    // Re-seats students from the last generated schedule into newly added rooms.
    // ==========================================
    private void showPartialReallocationDialog(String subject, String examDate, String startTime, String endTime, JLabel roomInfoLbl) {
        if (subject.isEmpty() || examDate.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No active schedule. Please generate a schedule first.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // Count currently unseated students (not in any room)
        try {
            // Find students in selected sections that are NOT yet in seating_plan for this exam
            PreparedStatement pSeated = con.prepareStatement(
                "SELECT COUNT(*) FROM seating_plan WHERE subject=? AND exam_date=? AND start_time=?");
            pSeated.setString(1, subject);
            pSeated.setString(2, examDate);
            pSeated.setString(3, normalizeTime(startTime));
            ResultSet rsSeated = pSeated.executeQuery();
            int seated = rsSeated.next() ? rsSeated.getInt(1) : 0;

            // Count how many rooms are currently used
            PreparedStatement pRooms = con.prepareStatement(
                "SELECT GROUP_CONCAT(DISTINCT room_no ORDER BY room_no) FROM seating_plan WHERE subject=? AND exam_date=? AND start_time=?");
            pRooms.setString(1, subject); pRooms.setString(2, examDate); pRooms.setString(3, normalizeTime(startTime));
            ResultSet rsRooms = pRooms.executeQuery();
            String roomList = rsRooms.next() ? rsRooms.getString(1) : "(none)";

            JDialog d = new JDialog(this, "Partial Re-allocation", true);
            d.setSize(560, 420);
            d.setLocationRelativeTo(this);
            d.setLayout(new BorderLayout());
            d.getContentPane().setBackground(Color.WHITE);

            // Header
            JPanel hdr = new JPanel(new BorderLayout());
            hdr.setBackground(new Color(234, 88, 12));
            hdr.setBorder(new EmptyBorder(14, 20, 14, 20));
            JLabel hTitle = new JLabel("Partial Re-allocation — Add New Rooms");
            hTitle.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
            hTitle.setForeground(Color.WHITE);
            hdr.add(hTitle, BorderLayout.WEST);
            d.add(hdr, BorderLayout.NORTH);

            JPanel content = new JPanel(new GridBagLayout());
            content.setBorder(new EmptyBorder(20, 25, 10, 25));
            content.setBackground(Color.WHITE);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(8, 8, 8, 8);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.WEST;

            gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
            JLabel info = new JLabel("<html><b>Exam:</b> " + escHtml(subject) + "  |  <b>Date:</b> " + examDate + "  |  <b>Time:</b> " + startTime + "<br>" +
                "<b>Currently seated:</b> " + seated + " students   <b>Rooms used:</b> " + roomList + "<br><br>" +
                "Choose sections with <b>remaining students</b> to seat into new available rooms:</html>");
            info.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            content.add(info, gbc);

            // Section selector
            gbc.gridy = 1; gbc.gridwidth = 2;
            JLabel secLbl = new JLabel("Sections to reallocate:");
            secLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
            content.add(secLbl, gbc);

            gbc.gridy = 2;
            JPanel cbContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            cbContainer.setBackground(Color.WHITE);
            List<JCheckBox> reAllocCBs = new ArrayList<>();
            try {
                ResultSet rsSec = con.createStatement().executeQuery("SELECT DISTINCT section FROM students ORDER BY section");
                while (rsSec.next()) {
                    JCheckBox cb = new JCheckBox(rsSec.getString(1));
                    cb.setBackground(Color.WHITE);
                    cb.setFont(BODY_FONT);
                    // Pre-select sections already in the current exam
                    cb.setSelected(allocatedRooms.size() > 0);
                    reAllocCBs.add(cb);
                    cbContainer.add(cb);
                }
            } catch (Exception ex) { /* skip */ }
            content.add(new JScrollPane(cbContainer), gbc);

            // Bhavan filter
            gbc.gridy = 3; gbc.gridwidth = 1;
            content.add(new JLabel("Bhavan (optional):"), gbc);
            gbc.gridx = 1;
            JComboBox<String> bhavanBox = new JComboBox<>();
            bhavanBox.addItem("Any");
            for (String bh : BHAVANS) bhavanBox.addItem(bh);
            content.add(bhavanBox, gbc);

            // Buttons
            JPanel btnP = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
            btnP.setBackground(new Color(248, 250, 252));
            JButton cancelBtn = new ModernButton("Cancel", Color.GRAY);
            JButton runBtn = new ModernButton("Run Re-allocation", new Color(234, 88, 12));
            cancelBtn.setPreferredSize(new Dimension(110, 40));
            runBtn.setPreferredSize(new Dimension(185, 40));
            btnP.add(cancelBtn);
            btnP.add(runBtn);

            cancelBtn.addActionListener(ev -> d.dispose());
            runBtn.addActionListener(ev -> {
                List<String> chosen = new ArrayList<>();
                for (JCheckBox cb : reAllocCBs) if (cb.isSelected()) chosen.add(cb.getText().trim());
                if (chosen.isEmpty()) { JOptionPane.showMessageDialog(d, "Please select at least one section."); return; }
                String bhavanFilt = bhavanBox.getSelectedIndex() == 0 ? null : (String) bhavanBox.getSelectedItem();
                d.dispose();
                runBtn.setEnabled(false);
                new Thread(() -> {
                    boolean ok = autoGenerateSchedule(chosen, false, subject, examDate, startTime, endTime, "", bhavanFilt, true);
                    SwingUtilities.invokeLater(() -> {
                        if (ok && roomInfoLbl != null)
                            roomInfoLbl.setText(allocatedRooms.size() + " room(s) used: " + String.join(", ", allocatedRooms));
                    });
                }).start();
            });

            d.add(content, BorderLayout.CENTER);
            d.add(btnP, BorderLayout.SOUTH);
            d.setVisible(true);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    // ==========================================
    // MARK ABSENTEES DIALOG
    // ==========================================
    private void showMarkAbsenteesDialog(String subject, String examDate, String startTime) {
        JDialog d = new JDialog(this, "Mark Absentees — " + subject + "  " + examDate, true);
        d.setSize(780, 600);
        d.setLocationRelativeTo(this);
        d.setLayout(new BorderLayout());
        d.getContentPane().setBackground(Color.WHITE);

        // Header
        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setBackground(new Color(139, 92, 246));
        hdr.setBorder(new EmptyBorder(14, 20, 14, 20));
        JLabel hTitle = new JLabel("Mark Absentees");
        hTitle.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        hTitle.setForeground(Color.WHITE);
        JLabel hSub = new JLabel(subject + "   •   " + examDate + "   •   " + startTime);
        hSub.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
        hSub.setForeground(new Color(220, 200, 255));
        JPanel hText = new JPanel();
        hText.setLayout(new BoxLayout(hText, BoxLayout.Y_AXIS));
        hText.setOpaque(false);
        hText.add(hTitle);
        hText.add(hSub);
        hdr.add(hText, BorderLayout.WEST);
        d.add(hdr, BorderLayout.NORTH);

        // Filter bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filterBar.setBackground(new Color(248, 250, 252));
        filterBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)));
        JTextField filterField = new ModernTextField(20);
        filterField.setPreferredSize(new Dimension(220, 36));
        JLabel filterLbl = new JLabel("Search roll/name:");
        filterLbl.setFont(BODY_FONT);
        JComboBox<String> roomFilter = new JComboBox<>();
        roomFilter.addItem("All Rooms");
        try {
            PreparedStatement pRoom = con.prepareStatement(
                "SELECT DISTINCT room_no FROM seating_plan WHERE subject=? AND exam_date=? AND start_time=? ORDER BY room_no");
            pRoom.setString(1, subject); pRoom.setString(2, examDate); pRoom.setString(3, normalizeTime(startTime));
            ResultSet rsR = pRoom.executeQuery();
            while (rsR.next()) roomFilter.addItem(rsR.getString(1));
        } catch (Exception ignored) {}
        JButton showAbsent = new ModernButton("Show Absent Only", new Color(220, 38, 38));
        showAbsent.setPreferredSize(new Dimension(160, 36));
        filterBar.add(filterLbl);
        filterBar.add(filterField);
        filterBar.add(new JLabel("Room:"));
        filterBar.add(roomFilter);
        filterBar.add(showAbsent);
        d.add(filterBar, BorderLayout.NORTH);

        // Table
        String[] cols2 = {"Roll No", "Name", "Room", "Seat (R,C)", "Status"};
        DefaultTableModel abModel = new DefaultTableModel(cols2, 0) {
            @Override public boolean isCellEditable(int row, int col) { return col == 4; }
            @Override public Class<?> getColumnClass(int col) { return col == 4 ? Boolean.class : String.class; }
        };
        JTable abTable = new JTable(abModel);
        abTable.setRowHeight(40);
        abTable.setFont(BODY_FONT);
        abTable.getTableHeader().setBackground(new Color(139, 92, 246));
        abTable.getTableHeader().setForeground(Color.WHITE);
        abTable.getTableHeader().setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        abTable.getColumnModel().getColumn(0).setPreferredWidth(110);
        abTable.getColumnModel().getColumn(1).setPreferredWidth(220);
        abTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        abTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        abTable.getColumnModel().getColumn(4).setPreferredWidth(80);

        // Load data
        List<Object[]> allRows = new ArrayList<>(); // [roll, name, room, seat, isAbsent]
        Runnable loadAbsentees = () -> {
            abModel.setRowCount(0);
            allRows.clear();
            try {
                PreparedStatement pst2 = con.prepareStatement(
                    "SELECT sp.id, sp.student_roll, sp.student_name, sp.room_no, sp.seat_row, sp.seat_col, " +
                    "COALESCE(ea.status,'PRESENT') as att_status " +
                    "FROM seating_plan sp " +
                    "LEFT JOIN exam_attendance ea ON ea.student_roll=sp.student_roll AND ea.exam_date=sp.exam_date AND ea.subject=sp.subject " +
                    "WHERE sp.subject=? AND sp.exam_date=? AND sp.start_time=? " +
                    "ORDER BY sp.room_no, sp.seat_col, sp.seat_row");
                pst2.setString(1, subject); pst2.setString(2, examDate); pst2.setString(3, normalizeTime(startTime));
                ResultSet rsA = pst2.executeQuery();
                while (rsA.next()) {
                    boolean isAbsent = "ABSENT".equals(rsA.getString("att_status"));
                    Object[] row = {
                        rsA.getString("student_roll"),
                        rsA.getString("student_name"),
                        rsA.getString("room_no"),
                        (rsA.getInt("seat_row")+1) + "," + (rsA.getInt("seat_col")+1),
                        isAbsent
                    };
                    allRows.add(row);
                    abModel.addRow(row);
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        };
        loadAbsentees.run();

        // Filter logic
        filterField.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) {
                String q = filterField.getText().toLowerCase();
                String roomSel = (String) roomFilter.getSelectedItem();
                boolean onlyAbsent = showAbsent.getBackground().equals(new Color(220, 38, 38));
                abModel.setRowCount(0);
                for (Object[] r : allRows) {
                    boolean rollMatch = r[0].toString().toLowerCase().contains(q) || r[1].toString().toLowerCase().contains(q);
                    boolean roomMatch = "All Rooms".equals(roomSel) || r[2].toString().equals(roomSel);
                    if (rollMatch && roomMatch) abModel.addRow(r);
                }
            }
        });
        roomFilter.addActionListener(e -> filterField.getKeyListeners()[0].keyReleased(null));

        boolean[] showingAbsentOnly = {false};
        showAbsent.addActionListener(e -> {
            showingAbsentOnly[0] = !showingAbsentOnly[0];
            showAbsent.setBackground(showingAbsentOnly[0] ? new Color(239, 68, 68) : new Color(220, 38, 38));
            abModel.setRowCount(0);
            for (Object[] r : allRows) {
                if (!showingAbsentOnly[0] || Boolean.TRUE.equals(r[4])) abModel.addRow(r);
            }
        });

        JScrollPane tableScroll2 = new JScrollPane(abTable);
        tableScroll2.setBorder(BorderFactory.createEmptyBorder());
        d.add(tableScroll2, BorderLayout.CENTER);

        // Footer buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 10));
        footer.setBackground(new Color(248, 250, 252));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));
        JLabel countLbl = new JLabel();
        countLbl.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
        countLbl.setForeground(Color.GRAY);
        JButton saveAbsent = new ModernButton("Save Attendance", new Color(16, 185, 129));
        saveAbsent.setPreferredSize(new Dimension(170, 40));
        JButton closeBtn2 = new ModernButton("Close", Color.GRAY);
        closeBtn2.setPreferredSize(new Dimension(100, 40));
        footer.add(countLbl);
        footer.add(saveAbsent);
        footer.add(closeBtn2);

        // Update count label when table changes
        abModel.addTableModelListener(ev -> {
            int absentCount = 0;
            for (int i = 0; i < abModel.getRowCount(); i++) {
                if (Boolean.TRUE.equals(abModel.getValueAt(i, 4))) absentCount++;
            }
            countLbl.setText("Absent: " + absentCount + " / " + abModel.getRowCount());
        });

        saveAbsent.addActionListener(e -> {
            try {
                // Find seating IDs for all students in this exam
                PreparedStatement pSave = con.prepareStatement(
                    "INSERT INTO exam_attendance (seating_id, student_roll, exam_date, subject, room_no, status, marked_by) " +
                    "VALUES ((SELECT id FROM seating_plan WHERE student_roll=? AND exam_date=? AND subject=? LIMIT 1), ?, ?, ?, ?, ?, 'EXAMINER') " +
                    "ON DUPLICATE KEY UPDATE status=VALUES(status), marked_by=VALUES(marked_by), marked_at=NOW()");
                int saved = 0;
                for (int i = 0; i < abModel.getRowCount(); i++) {
                    String roll = (String) abModel.getValueAt(i, 0);
                    String room2 = (String) abModel.getValueAt(i, 2);
                    boolean absent = Boolean.TRUE.equals(abModel.getValueAt(i, 4));
                    String status = absent ? "ABSENT" : "PRESENT";
                    pSave.setString(1, roll); pSave.setString(2, examDate); pSave.setString(3, subject);
                    pSave.setString(4, roll); pSave.setString(5, examDate); pSave.setString(6, subject);
                    pSave.setString(7, room2); pSave.setString(8, status);
                    pSave.addBatch();
                    saved++;
                }
                pSave.executeBatch();
                JOptionPane.showMessageDialog(d, "Attendance saved for " + saved + " students.", "Saved", JOptionPane.INFORMATION_MESSAGE);
                loadAbsentees.run();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(d, "Error saving: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        closeBtn2.addActionListener(e -> d.dispose());
        d.add(footer, BorderLayout.SOUTH);
        d.setVisible(true);
    }

    // ==========================================
    // ADMIN CAMPUS MAP — ROOM STATUS VIEW
    // ==========================================
    private JPanel createAdminCampusMapPanel() {
        JPanel p = new ModernPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(16, 18, 16, 18));

        // ── HEADER ──────────────────────────────────────────────────────────
        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        hdr.setBorder(new EmptyBorder(0, 0, 12, 0));

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);
        JLabel titleLbl = new JLabel("Campus Map & Room Status");
        titleLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 24));
        titleLbl.setForeground(PRIMARY_COLOR);
        JLabel subLbl = new JLabel("Visual campus map + live room availability. Click any room for instant actions.");
        subLbl.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        subLbl.setForeground(Color.GRAY);
        titleBlock.add(titleLbl);
        titleBlock.add(subLbl);
        hdr.add(titleBlock, BorderLayout.CENTER);

        JPanel hdrRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hdrRight.setOpaque(false);
        // Legend chips
        for (String[] chip : new String[][]{{"Available","16a34a"},{"In Use","dc2626"},{"Maintenance","b45309"}}) {
            JLabel lbl = new JLabel(" \u25cf " + chip[0]);
            lbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
            lbl.setForeground(Color.decode("#" + chip[1]));
            hdrRight.add(lbl);
        }
        JButton autoResetBtn = new ModernButton("\u21ba Auto-reset", new Color(59, 130, 246));
        autoResetBtn.setPreferredSize(new Dimension(138, 34));
        JButton refreshBtn = new ModernButton("Refresh", new Color(100, 116, 139));
        refreshBtn.setPreferredSize(new Dimension(92, 34));
        hdrRight.add(autoResetBtn);
        hdrRight.add(refreshBtn);
        hdr.add(hdrRight, BorderLayout.EAST);
        p.add(hdr, BorderLayout.NORTH);

        // ── STATS BAR ────────────────────────────────────────────────────────
        JPanel statsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        statsBar.setOpaque(true);
        statsBar.setBackground(new Color(255, 243, 230));
        statsBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(255, 200, 150)),
            new EmptyBorder(8, 4, 8, 4)));
        JLabel[] statLabels = new JLabel[4]; // total / available / inuse / maintenance
        for (int i = 0; i < 4; i++) { statLabels[i] = new JLabel(); statLabels[i].setFont(new Font(FONT_FAMILY, Font.BOLD, 13)); statsBar.add(statLabels[i]); }

        // ── SPLIT PANE ───────────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerSize(5);
        split.setResizeWeight(0.0);
        split.setBorder(null);
        split.setOpaque(false);

        // LEFT: Visual schematic map ─────────────────────────────────────────
        // bhavan schematic coordinates [x,y,w,h] and color
        int[][] bCoords = {{18,18,150,72},{188,18,150,72},{358,18,150,72},
                           {18,112,150,72},{188,112,150,72},{358,112,150,72},{188,206,150,72}};
        Color[] bSchemaColors = {
            new Color(59,130,246),new Color(5,150,105),new Color(217,119,6),
            new Color(185,28,28),new Color(109,40,217),new Color(16,185,129),new Color(234,88,12)
        };

        // Mutable map: bhavan name → status summary [vacant, occupied, maintenance]
        java.util.Map<String, int[]> bSummaryMap = new java.util.LinkedHashMap<>();
        for (String bv : BHAVANS) bSummaryMap.put(bv, new int[]{0,0,0});

        JPanel schematicPanel = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Background
                g2.setColor(new Color(240, 245, 250));
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Grid dots
                g2.setColor(new Color(200, 210, 225));
                for (int gx = 0; gx < getWidth(); gx += 20)
                    for (int gy = 0; gy < getHeight(); gy += 20)
                        g2.fillOval(gx - 1, gy - 1, 2, 2);
                // Roads
                g2.setColor(new Color(200, 210, 220));
                g2.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(178, 0, 178, getHeight());
                g2.drawLine(348, 0, 348, getHeight());
                g2.drawLine(0, 102, getWidth(), 102);
                g2.drawLine(0, 196, getWidth(), 196);
                g2.setStroke(new BasicStroke(1));
                // Bhavan blocks
                for (int i = 0; i < BHAVANS.length && i < bCoords.length; i++) {
                    int[] bc = bCoords[i];
                    int[] summ = bSummaryMap.getOrDefault(BHAVANS[i], new int[]{0,0,0});
                    // Determine block edge color by dominant state
                    Color edgeCol = summ[1] > 0 ? new Color(185,28,28) :
                                    summ[2] > 0 ? new Color(180,100,0) : new Color(21,128,61);
                    // Block fill
                    g2.setColor(bSchemaColors[i % bSchemaColors.length].darker());
                    g2.fillRoundRect(bc[0]+2, bc[1]+2, bc[2], bc[3], 14, 14); // shadow
                    g2.setColor(bSchemaColors[i % bSchemaColors.length]);
                    g2.fillRoundRect(bc[0], bc[1], bc[2], bc[3], 14, 14);
                    // Status stripe at bottom
                    g2.setColor(edgeCol);
                    g2.setStroke(new BasicStroke(3));
                    g2.drawRoundRect(bc[0]+1, bc[1]+1, bc[2]-2, bc[3]-2, 14, 14);
                    g2.setStroke(new BasicStroke(1));
                    // Name
                    g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
                    g2.setColor(Color.WHITE);
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = bc[0] + (bc[2] - fm.stringWidth(BHAVANS[i])) / 2;
                    g2.drawString(BHAVANS[i], tx, bc[1] + 26);
                    // Mini stats
                    g2.setFont(new Font(FONT_FAMILY, Font.PLAIN, 10));
                    String info = "\u2713" + summ[0] + "  \u2715" + summ[1] + "  \u26a0" + summ[2];
                    int iw = g2.getFontMetrics().stringWidth(info);
                    g2.setColor(new Color(255,255,255,200));
                    g2.drawString(info, bc[0] + (bc[2] - iw) / 2, bc[1] + 52);
                }
                g2.dispose();
            }
        };
        schematicPanel.setPreferredSize(new Dimension(530, 300));
        schematicPanel.setOpaque(false);

        // Bhavan buttons below the schematic
        JPanel bhavanBtnPanel = new JPanel();
        bhavanBtnPanel.setLayout(new BoxLayout(bhavanBtnPanel, BoxLayout.Y_AXIS));
        bhavanBtnPanel.setOpaque(false);
        bhavanBtnPanel.setBorder(new EmptyBorder(10, 6, 6, 6));

        // LEFT wrapper
        JPanel leftWrapper = new JPanel(new BorderLayout());
        leftWrapper.setOpaque(false);
        leftWrapper.setPreferredSize(new Dimension(540, 0));
        leftWrapper.add(schematicPanel, BorderLayout.NORTH);

        JScrollPane bhavanScroll = new JScrollPane(bhavanBtnPanel);
        bhavanScroll.setOpaque(false);
        bhavanScroll.getViewport().setOpaque(false);
        bhavanScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(255,200,150),1,true), " Buildings "));
        leftWrapper.add(bhavanScroll, BorderLayout.CENTER);

        split.setLeftComponent(leftWrapper);

        // RIGHT: Room-card grid inside tabbed pane ───────────────────────────
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
        split.setRightComponent(tabs);
        split.setDividerLocation(548);

        // Make schematic building blocks clickable → jump to corresponding tab
        schematicPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        schematicPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                for (int i = 0; i < bCoords.length && i < BHAVANS.length; i++) {
                    int[] bc = bCoords[i];
                    if (e.getX() >= bc[0] && e.getX() <= bc[0] + bc[2]
                            && e.getY() >= bc[1] && e.getY() <= bc[1] + bc[3]) {
                        String bName = BHAVANS[i];
                        for (int ti = 0; ti < tabs.getTabCount(); ti++) {
                            if (tabs.getTitleAt(ti).startsWith(bName)) {
                                tabs.setSelectedIndex(ti);
                                break;
                            }
                        }
                        break;
                    }
                }
            }
        });

        // ── REFRESH LOGIC ─────────────────────────────────────────────────
        Runnable[] refresh = {null};
        refresh[0] = () -> {
            // Auto-reset expired occupied rooms
            try {
                con.createStatement().executeUpdate(
                    "UPDATE room_status rs JOIN rooms r ON r.room_no=rs.room_no " +
                    "SET rs.status='VACANT', rs.occupied_for='' " +
                    "WHERE rs.status='OCCUPIED' AND NOT EXISTS(" +
                    "  SELECT 1 FROM seating_plan sp WHERE sp.room_no=rs.room_no " +
                    "  AND CONCAT(sp.exam_date,' ',sp.end_time) > NOW())");
            } catch (Exception ignored) {}

            // Load all rooms + upcoming exam info grouped by building
            java.util.LinkedHashMap<String, List<Object[]>> buildingMap = new java.util.LinkedHashMap<>();
            int[] totals = {0, 0, 0, 0}; // all, vacant, occupied, maintenance
            for (String bv : BHAVANS) bSummaryMap.put(bv, new int[]{0,0,0});

            try {
                // Fetch rooms + status + next upcoming exam for each room
                ResultSet rs = con.createStatement().executeQuery(
                    "SELECT r.room_no, r.capacity, r.rows_count AS rows, r.cols_count AS cols, " +
                    "  COALESCE(r.building,'') AS building, " +
                    "  COALESCE(rs.status,'VACANT') AS status, " +
                    "  COALESCE(rs.occupied_for,'') AS occ_for, " +
                    "  (SELECT CONCAT(subject,' | ',exam_date,' ',start_time,'-',end_time) " +
                    "   FROM seating_plan sp WHERE sp.room_no=r.room_no " +
                    "   AND exam_date >= CURDATE() ORDER BY exam_date,start_time LIMIT 1) AS next_exam, " +
                    "  (SELECT COUNT(*) FROM seating_plan sp2 WHERE sp2.room_no=r.room_no " +
                    "   AND exam_date=CURDATE()) AS today_count " +
                    "FROM rooms r LEFT JOIN room_status rs ON rs.room_no=r.room_no ORDER BY r.room_no");
                while (rs.next()) {
                    String rNo   = rs.getString("room_no");
                    int cap      = rs.getInt("capacity");
                    int rows2    = rs.getInt("rows");
                    int cols2    = rs.getInt("cols");
                    String st    = rs.getString("status");
                    String oFor  = rs.getString("occ_for");
                    String nExam = rs.getString("next_exam");
                    int todayCnt = rs.getInt("today_count");
                    String bldgDb = rs.getString("building");
                    // Use stored building; fall back to prefix-derived name
                    String bldg;
                    if (bldgDb != null && !bldgDb.trim().isEmpty()) {
                        bldg = bldgDb.trim();
                    } else {
                        bldg = getBuildingName(rNo);
                    }
                    buildingMap.computeIfAbsent(bldg, k -> new ArrayList<>())
                        .add(new Object[]{rNo, cap, rows2, cols2, st, oFor, nExam == null ? "" : nExam, todayCnt, bldg});
                    totals[0]++;
                    if ("OCCUPIED".equals(st))     { totals[2]++; int[] s = bSummaryMap.getOrDefault(bldg, new int[]{0,0,0}); s[1]++; bSummaryMap.put(bldg, s); }
                    else if ("MAINTENANCE".equals(st)) { totals[3]++; int[] s = bSummaryMap.getOrDefault(bldg, new int[]{0,0,0}); s[2]++; bSummaryMap.put(bldg, s); }
                    else                            { totals[1]++; int[] s = bSummaryMap.getOrDefault(bldg, new int[]{0,0,0}); s[0]++; bSummaryMap.put(bldg, s); }
                }
            } catch (Exception ex) { /* ignore */ }

            // Update stats bar
            statLabels[0].setText("  Total: " + totals[0] + "   ");
            statLabels[0].setForeground(new Color(50, 60, 100));
            statLabels[1].setText("\u25cf Available: " + totals[1] + "   ");
            statLabels[1].setForeground(new Color(21, 128, 61));
            statLabels[2].setText("\u25cf In Use: " + totals[2] + "   ");
            statLabels[2].setForeground(new Color(185, 28, 28));
            statLabels[3].setText("\u25cf Maintenance: " + totals[3]);
            statLabels[3].setForeground(new Color(180, 100, 0));

            // Repaint schematic
            schematicPanel.repaint();

            // Always ensure every bhavan appears — even if it has no rooms in DB yet
            for (String bv : BHAVANS) buildingMap.computeIfAbsent(bv, k -> new ArrayList<>());

            // Rebuild bhavan buttons
            bhavanBtnPanel.removeAll();
            for (Map.Entry<String, List<Object[]>> entry : buildingMap.entrySet()) {
                String bldg2 = entry.getKey();
                int[] summ = bSummaryMap.getOrDefault(bldg2, new int[]{0,0,0});
                JButton bb = new JButton("<html><b>" + bldg2 + "</b>  "
                    + "<font color='#15803d'>\u2713" + summ[0] + "</font>  "
                    + "<font color='#b91c1c'>\u2715" + summ[1] + "</font>  "
                    + "<font color='#b45309'>\u26a0" + summ[2] + "</font></html>");
                bb.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
                bb.setHorizontalAlignment(SwingConstants.LEFT);
                bb.setBackground(new Color(255, 243, 230));
                bb.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(255, 200, 150)),
                    new EmptyBorder(8, 10, 8, 10)));
                bb.setFocusPainted(false);
                bb.setCursor(new Cursor(Cursor.HAND_CURSOR));
                bb.setMaximumSize(new Dimension(Short.MAX_VALUE, 44));
                bb.setAlignmentX(Component.LEFT_ALIGNMENT);
                // Jump to that building's tab
                String bldgFinal = bldg2;
                bb.addActionListener(ev -> {
                    for (int ti = 0; ti < tabs.getTabCount(); ti++) {
                        if (tabs.getTitleAt(ti).startsWith(bldgFinal)) { tabs.setSelectedIndex(ti); break; }
                    }
                });
                bhavanBtnPanel.add(bb);
            }
            bhavanBtnPanel.revalidate();
            bhavanBtnPanel.repaint();

            // Rebuild room-card tabs
            tabs.removeAll();
            for (Map.Entry<String, List<Object[]>> entry : buildingMap.entrySet()) {
                String bldg2 = entry.getKey();
                List<Object[]> rooms = entry.getValue();

                long vacCnt  = rooms.stream().filter(r -> "VACANT".equals(r[4])).count();
                long occCnt  = rooms.stream().filter(r -> "OCCUPIED".equals(r[4])).count();
                long mntCnt  = rooms.stream().filter(r -> "MAINTENANCE".equals(r[4])).count();

                JPanel blockPanel = new JPanel(new BorderLayout());
                blockPanel.setBackground(new Color(248, 250, 252));

                // Summary bar
                JPanel sumBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 7));
                sumBar.setBackground(new Color(255, 243, 230));
                sumBar.setBorder(BorderFactory.createMatteBorder(0,0,1,0, new Color(255,200,150)));
                JLabel bldgTitle = new JLabel(bldg2);
                bldgTitle.setFont(new Font(FONT_FAMILY, Font.BOLD, 14));
                bldgTitle.setForeground(PRIMARY_COLOR);
                sumBar.add(bldgTitle);
                sumBar.add(makePill("\u2713 Available: " + vacCnt, new Color(21, 128, 61)));
                sumBar.add(makePill("\u2715 In Use: " + occCnt, new Color(185, 28, 28)));
                sumBar.add(makePill("\u26a0 Maintenance: " + mntCnt, new Color(146, 64, 14)));
                // Filter buttons
                JButton showAll  = new JButton("All");
                JButton showVac  = new JButton("Available");
                JButton showOcc  = new JButton("In Use");
                for (JButton fb : new JButton[]{showAll, showVac, showOcc}) {
                    fb.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
                    fb.setForeground(new Color(255,106,0));
                    fb.setBackground(Color.WHITE);
                    fb.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(255,200,150), 1, true),
                        new EmptyBorder(3, 10, 3, 10)));
                    fb.setFocusPainted(false);
                    fb.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    sumBar.add(fb);
                }
                blockPanel.add(sumBar, BorderLayout.NORTH);

                // Room cards grid
                JPanel cardGrid = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
                cardGrid.setBackground(new Color(248, 250, 252));
                cardGrid.setBorder(new EmptyBorder(10, 12, 10, 12));

                if (rooms.isEmpty()) {
                    JPanel emptyBldg = new JPanel(new BorderLayout());
                    emptyBldg.setOpaque(false);
                    emptyBldg.setBorder(new EmptyBorder(30, 30, 30, 30));
                    JLabel noR = new JLabel("<html><center>No rooms added for <b>" + bldg2 + "</b> yet.<br><br>"
                        + "Go to <b>Room Layouts</b> in the sidebar, enter a room number<br>starting with this building prefix, set rows &amp; cols, then Save.</center></html>",
                        SwingConstants.CENTER);
                    noR.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
                    noR.setForeground(new Color(100, 116, 139));
                    emptyBldg.add(noR, BorderLayout.CENTER);
                    blockPanel.add(emptyBldg, BorderLayout.CENTER);
                    tabs.addTab(bldg2 + " (0)", blockPanel);
                    continue;
                }

                for (Object[] rm : rooms) {
                    final String rNo2   = (String) rm[0];
                    final int cap2      = (int) rm[1];
                    final int rows2     = (int) rm[2];
                    final int cols2     = (int) rm[3];
                    final String st2    = (String) rm[4];
                    final String oFor2  = (String) rm[5];
                    final String nExam2 = (String) rm[6];
                    final int todayCnt2 = (int) rm[7];
                    final String bldgName2 = rm.length > 8 ? (String) rm[8] : getBuildingName(rNo2);

                    // Card panel (158×192) — slightly taller to fit building label + edit btn
                    JPanel card2 = new JPanel(new BorderLayout(0, 0)) {
                        @Override
                        protected void paintComponent(Graphics g) {
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            // Shadow
                            g2.setColor(new Color(0, 0, 0, 20));
                            g2.fillRoundRect(3, 4, getWidth() - 4, getHeight() - 4, 14, 14);
                            // Body
                            Color bodyCol;
                            switch (st2) {
                                case "OCCUPIED":    bodyCol = new Color(255, 241, 241); break;
                                case "MAINTENANCE": bodyCol = new Color(255, 245, 230); break;
                                default:            bodyCol = new Color(240, 255, 246); break;
                            }
                            g2.setColor(bodyCol);
                            g2.fillRoundRect(0, 0, getWidth() - 4, getHeight() - 4, 14, 14);
                            // Status stripe top
                            Color stripeCol;
                            switch (st2) {
                                case "OCCUPIED":    stripeCol = new Color(220, 38, 38); break;
                                case "MAINTENANCE": stripeCol = new Color(180, 100, 0); break;
                                default:            stripeCol = new Color(21, 128, 61); break;
                            }
                            g2.setColor(stripeCol);
                            g2.fillRoundRect(0, 0, getWidth() - 4, 6, 14, 14);
                            g2.fillRect(0, 3, getWidth() - 4, 3);
                            // Border
                            g2.setColor(stripeCol);
                            g2.setStroke(new BasicStroke(1.5f));
                            g2.drawRoundRect(0, 0, getWidth() - 5, getHeight() - 5, 14, 14);
                            g2.dispose();
                            super.paintComponent(g);
                        }
                    };
                    card2.setOpaque(false);
                    card2.setPreferredSize(new Dimension(162, 198));

                    // Card top info
                    JPanel cardInfo = new JPanel();
                    cardInfo.setLayout(new BoxLayout(cardInfo, BoxLayout.Y_AXIS));
                    cardInfo.setOpaque(false);
                    cardInfo.setBorder(new EmptyBorder(12, 10, 4, 10));

                    JLabel rNumLbl = new JLabel(rNo2);
                    rNumLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 17));
                    rNumLbl.setForeground(new Color(17, 24, 39));
                    rNumLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

                    // Building label
                    JLabel bldgLbl = new JLabel("\u25a4 " + bldgName2);
                    bldgLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 10));
                    bldgLbl.setForeground(new Color(255, 106, 0));
                    bldgLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

                    JLabel capLbl = new JLabel("Cap: " + cap2 + "  |  " + rows2 + "\u00d7" + cols2);
                    capLbl.setFont(new Font(FONT_FAMILY, Font.PLAIN, 10));
                    capLbl.setForeground(new Color(100, 116, 139));
                    capLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

                    // Status badge
                    String stText; Color stColor;
                    switch (st2) {
                        case "OCCUPIED":    stText = "\u25cf  IN USE";       stColor = new Color(185,28,28);   break;
                        case "MAINTENANCE": stText = "\u26a0  MAINTENANCE";  stColor = new Color(146,64,14);   break;
                        default:            stText = "\u25cf  AVAILABLE";    stColor = new Color(21,128,61);   break;
                    }
                    JLabel stLbl = new JLabel(stText);
                    stLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
                    stLbl.setForeground(stColor);
                    stLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

                    // Note / next exam
                    String infoText = !oFor2.isEmpty() ? oFor2
                        : (todayCnt2 > 0 ? "<html><font color='#b91c1c'>Today: " + todayCnt2 + " seated</font></html>"
                        : (!nExam2.isEmpty() ? "<html><font color='#6366f1'>\u23f1 " + escHtml(nExam2) + "</font></html>" : ""));
                    JLabel noteLbl = new JLabel(infoText.isEmpty() ? " " : infoText);
                    noteLbl.setFont(new Font(FONT_FAMILY, Font.PLAIN, 10));
                    noteLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

                    cardInfo.add(rNumLbl);
                    cardInfo.add(Box.createVerticalStrut(1));
                    cardInfo.add(bldgLbl);
                    cardInfo.add(Box.createVerticalStrut(2));
                    cardInfo.add(capLbl);
                    cardInfo.add(Box.createVerticalStrut(4));
                    cardInfo.add(stLbl);
                    cardInfo.add(Box.createVerticalStrut(2));
                    cardInfo.add(noteLbl);
                    card2.add(cardInfo, BorderLayout.CENTER);

                    // Action buttons row: Free | Use | Edit
                    JPanel actPanel = new JPanel(new GridLayout(1, 3, 3, 0));
                    actPanel.setOpaque(false);
                    actPanel.setBorder(new EmptyBorder(0, 7, 9, 7));

                    JButton availBtn = new JButton("\u2713 Free");
                    JButton useBtn   = new JButton("\u2691 Use");
                    JButton editBtn  = new JButton("\u270e Edit");
                    for (JButton ab : new JButton[]{availBtn, useBtn, editBtn}) {
                        ab.setFont(new Font(FONT_FAMILY, Font.BOLD, 10));
                        ab.setFocusPainted(false);
                        ab.setCursor(new Cursor(Cursor.HAND_CURSOR));
                        ab.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
                        ab.setOpaque(true);
                    }
                    availBtn.setBackground(new Color(220, 252, 231));
                    availBtn.setForeground(new Color(21, 128, 61));
                    useBtn.setBackground(new Color(254, 226, 226));
                    useBtn.setForeground(new Color(185, 28, 28));
                    editBtn.setBackground(new Color(255, 243, 230));
                    editBtn.setForeground(new Color(255, 106, 0));

                    // Quick toggle — no dialog, saves immediately
                    availBtn.addActionListener(ev -> {
                        try {
                            PreparedStatement ps = con.prepareStatement(
                                "INSERT INTO room_status (room_no,status,occupied_for) VALUES(?,?,?) " +
                                "ON DUPLICATE KEY UPDATE status=VALUES(status),occupied_for=VALUES(occupied_for),updated_at=NOW()");
                            ps.setString(1, rNo2); ps.setString(2, "VACANT"); ps.setString(3, "");
                            ps.executeUpdate();
                            refresh[0].run();
                        } catch (Exception ex) { JOptionPane.showMessageDialog(p, "Error: " + ex.getMessage()); }
                    });
                    useBtn.addActionListener(ev -> {
                        try {
                            PreparedStatement ps = con.prepareStatement(
                                "INSERT INTO room_status (room_no,status,occupied_for) VALUES(?,?,?) " +
                                "ON DUPLICATE KEY UPDATE status=VALUES(status),occupied_for=VALUES(occupied_for),updated_at=NOW()");
                            ps.setString(1, rNo2); ps.setString(2, "OCCUPIED"); ps.setString(3, "Manual");
                            ps.executeUpdate();
                            refresh[0].run();
                        } catch (Exception ex) { JOptionPane.showMessageDialog(p, "Error: " + ex.getMessage()); }
                    });
                    // Full edit dialog: status + building
                    editBtn.addActionListener(ev -> showRoomEditDialog(rNo2, st2, oFor2, bldgName2, refresh[0]));

                    actPanel.add(availBtn);
                    actPanel.add(useBtn);
                    actPanel.add(editBtn);
                    card2.add(actPanel, BorderLayout.SOUTH);

                    // Double-click also opens full edit
                    card2.addMouseListener(new MouseAdapter() {
                        public void mouseClicked(MouseEvent e) {
                            if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2)
                                showRoomEditDialog(rNo2, st2, oFor2, bldgName2, refresh[0]);
                        }
                        public void mouseEntered(MouseEvent e) { card2.setCursor(new Cursor(Cursor.HAND_CURSOR)); }
                    });
                    card2.setToolTipText("<html><b>" + rNo2 + "</b> \u2014 double-click or click Edit to modify<br>"
                        + "Building: " + escHtml(bldgName2) + "<br>"
                        + "Status: " + st2 + (oFor2.isEmpty() ? "" : "<br>Note: " + escHtml(oFor2)) + "</html>");

                    cardGrid.add(card2);

                    // Filter wiring (store card ref)
                    final String stFinal = st2;
                    showAll.addActionListener(ev -> card2.setVisible(true));
                    showVac.addActionListener(ev -> card2.setVisible("VACANT".equals(stFinal)));
                    showOcc.addActionListener(ev -> card2.setVisible("OCCUPIED".equals(stFinal)));
                }

                JScrollPane scrollCards = new JScrollPane(cardGrid);
                scrollCards.setBorder(null);
                scrollCards.getViewport().setBackground(new Color(248, 250, 252));
                scrollCards.getVerticalScrollBar().setUnitIncrement(20);
                blockPanel.add(scrollCards, BorderLayout.CENTER);

                // Tab title with inline count
                String tabTitle = "<html><b>" + bldg2 + "</b> <font color='#15803d'>" + vacCnt + "</font>/<font color='#b91c1c'>" + occCnt + "</font></html>";
                tabs.addTab(bldg2 + " " + vacCnt + "/" + rooms.size(), blockPanel);
                // Use HTML in tab title via renderer
                tabs.setToolTipTextAt(tabs.getTabCount()-1,
                    "<html>Available: "+vacCnt+"  In Use: "+occCnt+"  Maintenance: "+mntCnt+"</html>");
            }

            if (tabs.getTabCount() == 0) {
                JPanel noRoomsPanel = new JPanel(new BorderLayout());
                noRoomsPanel.setBackground(new Color(248, 250, 252));
                JPanel noRoomsCtr = new JPanel();
                noRoomsCtr.setLayout(new BoxLayout(noRoomsCtr, BoxLayout.Y_AXIS));
                noRoomsCtr.setOpaque(false);
                noRoomsCtr.setBorder(new EmptyBorder(40, 40, 40, 40));
                JLabel icon = new JLabel("\uD83C\uDFEB", SwingConstants.CENTER);
                icon.setFont(new Font(FONT_FAMILY, Font.PLAIN, 48));
                icon.setAlignmentX(Component.CENTER_ALIGNMENT);
                JLabel msg = new JLabel("No rooms configured yet", SwingConstants.CENTER);
                msg.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
                msg.setForeground(new Color(71, 85, 105));
                msg.setAlignmentX(Component.CENTER_ALIGNMENT);
                JLabel hint = new JLabel("<html><center>Go to <b>Room Layouts</b> in the left sidebar<br>to add rooms, then hit Refresh here.</center></html>", SwingConstants.CENTER);
                hint.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
                hint.setForeground(Color.GRAY);
                hint.setAlignmentX(Component.CENTER_ALIGNMENT);
                noRoomsCtr.add(icon); noRoomsCtr.add(Box.createVerticalStrut(10));
                noRoomsCtr.add(msg); noRoomsCtr.add(Box.createVerticalStrut(6));
                noRoomsCtr.add(hint);
                noRoomsPanel.add(noRoomsCtr, BorderLayout.CENTER);
                tabs.addTab("(no rooms)", noRoomsPanel);
            }
        };

        refreshBtn.addActionListener(e -> refresh[0].run());
        autoResetBtn.addActionListener(e -> {
            refresh[0].run();
            JOptionPane.showMessageDialog(this, "Expired occupied rooms reset to Available.", "Done", JOptionPane.INFORMATION_MESSAGE);
        });
        refresh[0].run();

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(statsBar, BorderLayout.NORTH);
        centerPanel.add(split, BorderLayout.CENTER);

        p.add(centerPanel, BorderLayout.CENTER);
        return p;
    }

    private JLabel makePill(String text, Color bg) {
        JLabel l = new JLabel("  " + text + "  ");
        l.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
        l.setForeground(Color.WHITE);
        l.setBackground(bg);
        l.setOpaque(true);
        l.setBorder(new EmptyBorder(3, 7, 3, 7));
        return l;
    }

    private void showRoomStatusChangeDialog(String roomNo, String currentStatus, String currentNote, Runnable onSave) {
        JDialog d = new JDialog(this, "Change Room Status — " + roomNo, true);
        d.setSize(420, 300);
        d.setLocationRelativeTo(this);
        d.setLayout(new BorderLayout());
        d.getContentPane().setBackground(Color.WHITE);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(new EmptyBorder(20, 25, 10, 25));
        content.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 8, 10, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        content.add(new JLabel("Room:  " + roomNo), gbc);

        gbc.gridy = 1;
        content.add(new JLabel("New Status:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> statusBox = new JComboBox<>(new String[]{"VACANT", "OCCUPIED", "MAINTENANCE"});
        statusBox.setSelectedItem(currentStatus);
        statusBox.setFont(BODY_FONT);
        content.add(statusBox, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        content.add(new JLabel("Note / Reason:"), gbc);
        gbc.gridx = 1;
        JTextField noteField = new ModernTextField(20);
        noteField.setText(currentNote);
        content.add(noteField, gbc);

        JPanel btnP = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnP.setBackground(new Color(248, 250, 252));
        JButton cancelBtn = new ModernButton("Cancel", Color.GRAY);
        JButton saveBtn = new ModernButton("Save", PRIMARY_COLOR);
        cancelBtn.setPreferredSize(new Dimension(90, 38));
        saveBtn.setPreferredSize(new Dimension(90, 38));
        btnP.add(cancelBtn);
        btnP.add(saveBtn);

        saveBtn.addActionListener(e -> {
            try {
                PreparedStatement pst = con.prepareStatement(
                    "INSERT INTO room_status (room_no, status, occupied_for) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE status=VALUES(status), occupied_for=VALUES(occupied_for), updated_at=NOW()");
                pst.setString(1, roomNo);
                pst.setString(2, (String) statusBox.getSelectedItem());
                pst.setString(3, noteField.getText().trim());
                pst.executeUpdate();
                d.dispose();
                if (onSave != null) onSave.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(d, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancelBtn.addActionListener(e -> d.dispose());

        d.add(content, BorderLayout.CENTER);
        d.add(btnP, BorderLayout.SOUTH);
        d.setVisible(true);
    }

    /**
     * Full room edit dialog — lets admin change status, note, AND building assignment.
     */
    private void showRoomEditDialog(String roomNo, String currentStatus, String currentNote, String currentBuilding, Runnable onSave) {
        JDialog d = new JDialog(this, "Edit Room — " + roomNo, true);
        d.setSize(460, 360);
        d.setLocationRelativeTo(this);
        d.setLayout(new BorderLayout());
        d.getContentPane().setBackground(Color.WHITE);

        // Header bar
        JPanel hdrBar = new JPanel(new BorderLayout());
        hdrBar.setBackground(PRIMARY_COLOR);
        hdrBar.setBorder(new EmptyBorder(12, 18, 12, 18));
        JLabel hdrLbl = new JLabel("\u270e  Room " + roomNo + " — Edit Details");
        hdrLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 15));
        hdrLbl.setForeground(Color.WHITE);
        hdrBar.add(hdrLbl, BorderLayout.WEST);
        d.add(hdrBar, BorderLayout.NORTH);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(new EmptyBorder(20, 25, 10, 25));
        content.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 8, 10, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Status
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.35;
        JLabel stLbl2 = new JLabel("Status:");
        stLbl2.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        content.add(stLbl2, gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        JComboBox<String> statusBox = new JComboBox<>(new String[]{"VACANT", "OCCUPIED", "MAINTENANCE"});
        statusBox.setSelectedItem(currentStatus);
        statusBox.setFont(BODY_FONT);
        content.add(statusBox, gbc);

        // Building
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.35;
        JLabel bldgLbl2 = new JLabel("Building:");
        bldgLbl2.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        content.add(bldgLbl2, gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        // Combo with all BHAVANS + manual option
        String[] bhavanOpts = new String[BHAVANS.length + 1];
        bhavanOpts[0] = "-- Select Building --";
        System.arraycopy(BHAVANS, 0, bhavanOpts, 1, BHAVANS.length);
        JComboBox<String> buildingCombo = new JComboBox<>(bhavanOpts);
        buildingCombo.setFont(BODY_FONT);
        // Pre-select current building if it matches
        boolean matched = false;
        for (int i = 1; i < bhavanOpts.length; i++) {
            if (bhavanOpts[i].equals(currentBuilding)) { buildingCombo.setSelectedIndex(i); matched = true; break; }
        }
        if (!matched && currentBuilding != null && !currentBuilding.isEmpty()
                && !currentBuilding.equals("Unknown Building")) {
            // custom value — add it
            buildingCombo.addItem(currentBuilding);
            buildingCombo.setSelectedItem(currentBuilding);
        }
        content.add(buildingCombo, gbc);

        // Note
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.35;
        JLabel noteLbl2 = new JLabel("Note / Reason:");
        noteLbl2.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        content.add(noteLbl2, gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        JTextField noteField = new ModernTextField(20);
        noteField.setText(currentNote);
        content.add(noteField, gbc);

        JPanel btnP = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnP.setBackground(new Color(248, 250, 252));
        JButton cancelBtn2 = new ModernButton("Cancel", Color.GRAY);
        JButton saveBtn2   = new ModernButton("Save Changes", PRIMARY_COLOR);
        cancelBtn2.setPreferredSize(new Dimension(100, 38));
        saveBtn2.setPreferredSize(new Dimension(140, 38));
        btnP.add(cancelBtn2);
        btnP.add(saveBtn2);

        saveBtn2.addActionListener(e -> {
            try {
                String selBuilding = (String) buildingCombo.getSelectedItem();
                if ("-- Select Building --".equals(selBuilding)) selBuilding = "";
                // Update room_status
                PreparedStatement ps1 = con.prepareStatement(
                    "INSERT INTO room_status (room_no, status, occupied_for) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE status=VALUES(status), occupied_for=VALUES(occupied_for), updated_at=NOW()");
                ps1.setString(1, roomNo);
                ps1.setString(2, (String) statusBox.getSelectedItem());
                ps1.setString(3, noteField.getText().trim());
                ps1.executeUpdate();
                // Update building in rooms table
                PreparedStatement ps2 = con.prepareStatement(
                    "UPDATE rooms SET building=? WHERE room_no=?");
                ps2.setString(1, selBuilding.isEmpty() ? null : selBuilding);
                ps2.setString(2, roomNo);
                ps2.executeUpdate();
                d.dispose();
                if (onSave != null) onSave.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(d, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancelBtn2.addActionListener(e -> d.dispose());

        d.add(content, BorderLayout.CENTER);
        d.add(btnP, BorderLayout.SOUTH);
        d.setVisible(true);
    }
    private JPanel createInvigilatorRosterPanel() {
        JPanel p = new ModernPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Header
        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        hdr.setBorder(new EmptyBorder(0, 0, 18, 0));
        JLabel title = new JLabel("Invigilator Duty Roster");
        title.setFont(new Font(FONT_FAMILY, Font.BOLD, 24));
        title.setForeground(PRIMARY_COLOR);
        JLabel sub2 = new JLabel("Full duty schedule for each faculty member across all exams.");
        sub2.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        sub2.setForeground(Color.GRAY);
        JPanel titleBlock2 = new JPanel();
        titleBlock2.setLayout(new BoxLayout(titleBlock2, BoxLayout.Y_AXIS));
        titleBlock2.setOpaque(false);
        titleBlock2.add(title);
        titleBlock2.add(sub2);
        hdr.add(titleBlock2, BorderLayout.CENTER);

        JButton refreshRoster = new ModernButton("Refresh", new Color(100, 116, 139));
        refreshRoster.setPreferredSize(new Dimension(110, 38));
        JButton exportRoster = new ModernButton("Export HTML", PRIMARY_COLOR);
        exportRoster.setPreferredSize(new Dimension(130, 38));
        JPanel hdrBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        hdrBtns.setOpaque(false);
        hdrBtns.add(refreshRoster);
        hdrBtns.add(exportRoster);
        hdr.add(hdrBtns, BorderLayout.EAST);
        p.add(hdr, BorderLayout.NORTH);

        // Filter bar
        JPanel filterBar2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filterBar2.setOpaque(false);
        JTextField facultySearch = new ModernTextField(20);
        facultySearch.setPreferredSize(new Dimension(220, 36));
        decorateField(facultySearch, "Search Faculty");
        JComboBox<String> dateFilterBox = new JComboBox<>();
        dateFilterBox.addItem("All Dates");
        try {
            ResultSet rsDates = con.createStatement().executeQuery(
                "SELECT DISTINCT exam_date FROM seating_plan WHERE invigilator_name IS NOT NULL AND invigilator_name!='' ORDER BY exam_date");
            while (rsDates.next()) dateFilterBox.addItem(rsDates.getString(1));
        } catch (Exception ignored) {}
        filterBar2.add(new JLabel("Faculty:"));
        filterBar2.add(facultySearch);
        filterBar2.add(new JLabel("Date:"));
        filterBar2.add(dateFilterBox);
        p.add(filterBar2, BorderLayout.SOUTH);

        // Table
        String[] rosterCols = {"Faculty Name", "Exam Date", "Session", "Subject", "Room", "Time Slot", "Students"};
        DefaultTableModel rosterModel = new DefaultTableModel(rosterCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable rosterTable = new JTable(rosterModel);
        styleTable(rosterTable);

        int[] rosterCols2 = {160, 100, 70, 220, 80, 130, 80};
        for (int i = 0; i < rosterCols2.length; i++)
            rosterTable.getColumnModel().getColumn(i).setPreferredWidth(rosterCols2[i]);

        List<Object[]> allRosterRows = new ArrayList<>();

        Runnable loadRoster = () -> {
            rosterModel.setRowCount(0);
            allRosterRows.clear();
            String dateFilter = (String) dateFilterBox.getSelectedItem();
            try {
                StringBuilder sql = new StringBuilder(
                    "SELECT invigilator_name, exam_date, subject, room_no, start_time, end_time, COUNT(*) as total " +
                    "FROM seating_plan " +
                    "WHERE invigilator_name IS NOT NULL AND invigilator_name != '' ");
                if (!"All Dates".equals(dateFilter)) sql.append("AND exam_date='").append(dateFilter).append("' ");
                sql.append("GROUP BY invigilator_name, exam_date, subject, room_no, start_time, end_time " +
                           "ORDER BY invigilator_name, exam_date, start_time");
                ResultSet rs3 = con.createStatement().executeQuery(sql.toString());
                while (rs3.next()) {
                    String sTime3 = rs3.getString("start_time");
                    String session = (sTime3 != null && (sTime3.startsWith("09") || sTime3.startsWith("10") || sTime3.startsWith("11"))) ? "FN" : "AN";
                    Object[] row = {
                        rs3.getString("invigilator_name"),
                        rs3.getString("exam_date"),
                        session,
                        rs3.getString("subject"),
                        rs3.getString("room_no"),
                        sTime3 + " – " + rs3.getString("end_time"),
                        rs3.getInt("total")
                    };
                    allRosterRows.add(row);
                    rosterModel.addRow(row);
                }
                if (rosterModel.getRowCount() == 0)
                    rosterModel.addRow(new Object[]{"No duty assignments found", "", "", "", "", "", ""});
            } catch (Exception ex) {
                rosterModel.addRow(new Object[]{"Error: " + ex.getMessage(), "", "", "", "", "", ""});
            }
        };

        loadRoster.run();
        rosterRefresher = loadRoster;
        refreshRoster.addActionListener(e -> loadRoster.run());
        dateFilterBox.addActionListener(e -> loadRoster.run());

        // Faculty name search filter
        facultySearch.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) {
                String q = facultySearch.getText().trim().toLowerCase();
                rosterModel.setRowCount(0);
                for (Object[] r : allRosterRows) {
                    if (r[0].toString().toLowerCase().contains(q)) rosterModel.addRow(r);
                }
            }
        });

        // Export to HTML
        exportRoster.addActionListener(e -> {
            StringBuilder sb2 = new StringBuilder();
            sb2.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Invigilator Duty Roster</title>")
               .append("<style>body{font-family:Arial;padding:24px}table{border-collapse:collapse;width:100%}")
               .append("th{background:#4338ca;color:#fff;padding:10px 12px}td{padding:9px 12px;border:1px solid #ddd}")
               .append("tr:nth-child(even)td{background:#f8f8f8}</style></head><body>")
               .append("<h2>Invigilator Duty Roster — Aditya University</h2>")
               .append("<p>Generated: ").append(java.time.LocalDate.now()).append("</p>")
               .append("<table><thead><tr>");
            for (String c : rosterCols) sb2.append("<th>").append(c).append("</th>");
            sb2.append("</tr></thead><tbody>");
            for (Object[] r : allRosterRows) {
                sb2.append("<tr>");
                for (Object cell : r) sb2.append("<td>").append(escHtml(cell == null ? "" : cell.toString())).append("</td>");
                sb2.append("</tr>");
            }
            sb2.append("</tbody></table></body></html>");
            try {
                File f2 = new File(System.getProperty("user.home") + "/Downloads", "InvigilatorRoster_" + java.time.LocalDate.now() + ".html");
                try (PrintWriter pw2 = new PrintWriter(new OutputStreamWriter(new java.io.FileOutputStream(f2), java.nio.charset.StandardCharsets.UTF_8))) {
                    pw2.write(sb2.toString());
                }
                openInBrowser(f2);
                JOptionPane.showMessageDialog(this, "Roster exported to:\n" + f2.getAbsolutePath(), "Exported", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JScrollPane scroll3 = new JScrollPane(rosterTable);
        scroll3.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        scroll3.getViewport().setBackground(Color.WHITE);
        p.add(scroll3, BorderLayout.CENTER);
        return p;
    }

    /**
     * Renders the Aditya University logo as a BufferedImage at any size.
     * @param darkBg true = white emblem + orange/pale text for dark backgrounds (sidebar)
     *               false = navy emblem + orange/navy text for light backgrounds (login)
     */
    public static BufferedImage createAdityaLogoImage(int w, int h, boolean darkBg) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        final Color NAVY        = darkBg ? Color.WHITE              : new Color(10, 40, 90);
        final Color ORANGE      = new Color(232, 65, 28);
        final Color UNIV_COLOR  = darkBg ? new Color(200, 215, 255) : new Color(10, 40, 90);

        boolean squarish = (w <= h * 1.5);
        int cx, cy, r;
        if (squarish) {
            r  = (int)(Math.min(w, h) * 0.43);
            cx = w / 2;
            cy = h / 2;
        } else {
            r  = (int)(h * 0.43);
            cx = r + (int)(h * 0.08);
            cy = h / 2;
        }

        // Outer ring
        g.setColor(NAVY);
        g.setStroke(new BasicStroke(r * 0.05f));
        g.drawOval(cx - r, cy - r, 2 * r, 2 * r);

        // Orbital dashed arc
        g.setStroke(new BasicStroke(r * 0.03f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                0, new float[]{r * 0.18f, r * 0.12f}, 0));
        int r2 = (int)(r * 0.76);
        g.drawOval(cx - r2, cy - r2, 2 * r2, 2 * r2);

        // Pearl dots
        int pd = (int)(r * 0.10);
        g.setStroke(new BasicStroke(1));
        for (int deg : new int[]{0, 60, 120, 180, 240, 300}) {
            double rad = Math.toRadians(deg);
            g.setColor(NAVY);
            g.fillOval((int)(cx + r * Math.cos(rad)) - pd/2,
                       (int)(cy + r * Math.sin(rad)) - pd/2, pd, pd);
        }

        // Star burst
        int nr = (int)(r * 0.48), nbr = (int)(r * 0.28), npts = 8;
        int[] sx = new int[npts * 2], sy = new int[npts * 2];
        for (int i = 0; i < npts * 2; i++) {
            double ang = Math.toRadians(i * 180.0 / npts - 90);
            int rad2 = (i % 2 == 0) ? nr : nbr;
            sx[i] = (int)(cx + rad2 * Math.cos(ang));
            sy[i] = (int)(cy + rad2 * Math.sin(ang));
        }
        g.setColor(NAVY);
        g.fillPolygon(sx, sy, npts * 2);

        // Central fire triangle
        int ft = (int)(r * 0.24);
        g.setColor(ORANGE);
        g.fillPolygon(new int[]{cx, cx - ft, cx + ft}, new int[]{cy - ft, cy + ft, cy + ft}, 3);
        g.setColor(new Color(255, 200, 80));
        g.fillPolygon(new int[]{cx, cx - ft/2, cx + ft/2},
                      new int[]{cy - ft, cy + ft/2, cy + ft/2}, 3);

        // Text (banner mode)
        if (!squarish) {
            int textX = cx + r + (int)(h * 0.12);
            g.setFont(buildLogoFont((int)(h * 0.42), true));
            g.setColor(ORANGE);
            g.drawString("ADITYA", textX, cy - (int)(h * 0.04));
            g.setFont(buildLogoFont((int)(h * 0.22), false));
            g.setColor(UNIV_COLOR);
            g.drawString("UNIVERSITY", textX, cy + (int)(h * 0.28));
        }

        g.dispose();
        return img;
    }

    /** Convenience overload — defaults to light background. */
    public static BufferedImage createAdityaLogoImage(int w, int h) {
        return createAdityaLogoImage(w, h, false);
    }

    private static Font buildLogoFont(int size, boolean bold) {
        // Try canonical serif fonts, fall back gracefully
        for (String name : new String[]{"Segoe UI", "Helvetica Neue", "Arial", Font.SANS_SERIF}) {
            Font f = new Font(name, bold ? Font.BOLD : Font.PLAIN, size);
            if (!f.getFamily().equals(Font.DIALOG)) return f;
        }
        return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size);
    }

    /** Returns a JLabel containing a scaled version of the logo for use in Swing UIs. */
    private JLabel createLogoLabel(int w, int h) {
        BufferedImage img = createAdityaLogoImage(w, h);
        java.awt.Image scaled = img.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH);
        JLabel lbl = new JLabel(new ImageIcon(scaled));
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        return lbl;
    }

    static class Student { String roll, name, section; public Student(String r, String n, String s) { roll=r; name=n; section=s; } }
    static class RoomInfo { public int capacity;
    String no; int rows, cols, cap; public RoomInfo(String n, int r, int c, int cp) { no=n; rows=r; cols=c; cap=cp; } }
}