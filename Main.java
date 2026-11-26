import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

public class Main {
    private static final Path DATA_FILE = Path.of("attendance-data.txt");
    private static final Path ACCOUNT_FILE = Path.of("attendance-accounts.txt");
    private static final int LOW_ATTENDANCE_THRESHOLD = 75;
    private static final int HASH_ITERATIONS = 120_000;
    private static final Color INK = new Color(34, 52, 47);
    private static final Color MUTED = new Color(119, 135, 128);
    private static final Color GREEN = new Color(47, 119, 98);
    private static final Color GREEN_DARK = new Color(34, 91, 75);
    private static final Color GREEN_SOFT = new Color(232, 244, 237);
    private static final Color CANVAS = new Color(246, 248, 246);
    private static final Color LINE = new Color(229, 236, 231);
    private static final String[] ATTENDANCE_OPTIONS = {
        "Not marked", "Present", "Absent", "Late"
    };

    private final Map<String, Student> students = new LinkedHashMap<>();
    private final Map<LocalDate, Map<String, AttendanceStatus>> attendance =
            new TreeMap<>();
    private final Map<String, PasswordCredential> studentCredentials = new LinkedHashMap<>();
    private final Map<String, JButton> navigationButtons = new LinkedHashMap<>();
    private final JFrame frame = new JFrame("Classroom | Attendance");
    private final CardLayout rootLayout = new CardLayout();
    private final JPanel rootCards = new JPanel(rootLayout);
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pageCards = new JPanel(pageLayout);
    private final JLabel breadcrumb = new JLabel("Overview");
    private final JLabel totalValue = new JLabel("0");
    private final JLabel presentValue = new JLabel("0");
    private final JLabel absentValue = new JLabel("0");
    private final JLabel rateValue = new JLabel("-");
    private final JLabel reportAttendedValue = new JLabel("0");
    private final JLabel reportAbsentValue = new JLabel("0");
    private final JLabel reportRateValue = new JLabel("-");
    private final JLabel studentRateValue = new JLabel("-");
    private final JLabel studentAttendedValue = new JLabel("0");
    private final JLabel studentMarkedValue = new JLabel("0");
    private final JLabel studentNameHeading = new JLabel("Your attendance");
    private final JLabel warningList = new JLabel("All students are above the attendance threshold.");
    private final JLabel studentWarning = new JLabel(" ");
    private JButton createTeacherButton;
    private final JLabel attendanceHint = new JLabel(" ");
    private final JLabel rosterCount = new JLabel("0 students");
    private final JTextField attendanceDate = new JTextField(LocalDate.now().toString(), 10);
    private final JTextField reportDate = new JTextField(LocalDate.now().toString(), 10);
    private final JTextField dashboardSearch = new JTextField(18);
    private final JTextField rosterSearch = new JTextField(18);
    private final AttendanceTableModel attendanceModel = new AttendanceTableModel();
    private final StudentTableModel studentModel = new StudentTableModel();
    private final ReportTableModel reportModel = new ReportTableModel();
    private final StudentHistoryTableModel studentHistoryModel = new StudentHistoryTableModel();
    private final JTable attendanceTable = new JTable(attendanceModel);
    private final JTable studentTable = new JTable(studentModel);
    private final JTable reportTable = new JTable(reportModel);
    private final JTable studentHistoryTable = new JTable(studentHistoryModel);
    private String currentPage = "Overview";
    private PasswordCredential teacherCredential;
    private Student signedInStudent;
    private boolean accountFileInvalid;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Use Swing's default look and feel when the platform theme is unavailable.
            }

            new Main().start();
        });
    }

    private void start() {
        try {
            loadData();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Saved attendance could not be loaded:\n" + e.getMessage(),
                    "Data file error", JOptionPane.ERROR_MESSAGE);
        }
        try {
            loadAccounts();
        } catch (IOException e) {
            accountFileInvalid = true;
            JOptionPane.showMessageDialog(null,
                    "Login data could not be loaded. Sign-in is disabled to protect existing accounts.\n"
                            + e.getMessage(),
                    "Account file error", JOptionPane.ERROR_MESSAGE);
        }

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 620));
        frame.setSize(1180, 760);
        frame.setLocationRelativeTo(null);
        rootCards.add(buildLoginPage(), "Login");
        rootCards.add(buildTeacherWorkspace(), "Teacher");
        rootCards.add(buildStudentWorkspace(), "Student");
        frame.setContentPane(rootCards);
        refreshAll();
        rootLayout.show(rootCards, "Login");
        frame.setVisible(true);
    }

    private JPanel buildLoginPage() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(CANVAS);
        JPanel shell = new JPanel(new GridLayout(1, 2, 0, 0));
        shell.setPreferredSize(new Dimension(780, 470));
        shell.setBorder(BorderFactory.createLineBorder(LINE));

        JPanel intro = new JPanel(new GridBagLayout());
        intro.setBackground(GREEN_DARK);
        JPanel introText = new JPanel(new GridLayout(0, 1, 0, 12));
        introText.setOpaque(false);
        JLabel brand = new JLabel("CLASSROOM");
        brand.setForeground(new Color(194, 224, 207));
        brand.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        JLabel title = new JLabel("<html>Attendance,<br>made thoughtful.</html>");
        title.setForeground(Color.WHITE);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 31));
        JLabel description = new JLabel("<html><div style='width:250px'>A calm place to manage your class and keep track of every check-in.</div></html>");
        description.setForeground(new Color(220, 236, 226));
        description.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        introText.add(brand);
        introText.add(title);
        introText.add(description);
        intro.add(introText);

        JPanel formSide = new JPanel(new BorderLayout(0, 16));
        formSide.setBackground(Color.WHITE);
        formSide.setBorder(BorderFactory.createEmptyBorder(31, 32, 30, 32));
        JLabel heading = new JLabel("Welcome back");
        heading.setForeground(INK);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        JLabel subtitle = new JLabel("Choose how you would like to sign in.");
        subtitle.setForeground(MUTED);
        JPanel titleStack = new JPanel(new GridLayout(0, 1, 0, 5));
        titleStack.setOpaque(false);
        titleStack.add(heading);
        titleStack.add(subtitle);

        JPanel roleButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        roleButtons.setOpaque(false);
        JButton teacherRole = secondaryButton("Teacher");
        JButton studentRole = secondaryButton("Student");
        roleButtons.add(teacherRole);
        roleButtons.add(studentRole);

        CardLayout formsLayout = new CardLayout();
        JPanel forms = new JPanel(formsLayout);
        forms.setOpaque(false);
        JPanel teacherForm = buildTeacherLoginForm();
        JPanel studentForm = buildStudentLoginForm();
        forms.add(teacherForm, "Teacher");
        forms.add(studentForm, "Student");
        teacherRole.addActionListener(event -> formsLayout.show(forms, "Teacher"));
        studentRole.addActionListener(event -> formsLayout.show(forms, "Student"));

        JPanel stack = new JPanel(new BorderLayout(0, 20));
        stack.setOpaque(false);
        stack.add(titleStack, BorderLayout.NORTH);
        stack.add(roleButtons, BorderLayout.CENTER);
        stack.add(forms, BorderLayout.SOUTH);
        formSide.add(stack, BorderLayout.NORTH);
        shell.add(intro);
        shell.add(formSide);
        page.add(shell);
        return page;
    }

    private JPanel buildTeacherLoginForm() {
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 9));
        form.setOpaque(false);
        JPasswordField password = new JPasswordField();
        JButton signIn = primaryButton("Sign in as teacher");
        JButton setup = secondaryButton("Create teacher account");
        createTeacherButton = setup;
        JLabel status = new JLabel(" ");
        status.setForeground(MUTED);
        form.add(fieldLabel("Teacher password"));
        form.add(password);
        form.add(signIn);
        form.add(setup);
        form.add(status);
        signIn.addActionListener(event -> {
            if (accountFileInvalid) {
                status.setText("Login data is unavailable.");
                return;
            }
            char[] entered = password.getPassword();
            boolean valid = teacherCredential != null && verifyPassword(entered, teacherCredential);
            java.util.Arrays.fill(entered, '\0');
            password.setText("");
            if (!valid) {
                status.setText("Password doesn't match. Try again.");
                return;
            }
            showTeacherWorkspace();
        });
        setup.addActionListener(event -> setupTeacherAccount());
        setup.setVisible(teacherCredential == null && !accountFileInvalid);
        return form;
    }

    private JPanel buildStudentLoginForm() {
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 8));
        form.setOpaque(false);
        JTextField id = new JTextField();
        JPasswordField pin = new JPasswordField();
        JButton signIn = primaryButton("Sign in as student");
        JLabel status = new JLabel("Use the ID and PIN provided by your teacher.");
        status.setForeground(MUTED);
        form.add(fieldLabel("Student ID"));
        form.add(id);
        form.add(fieldLabel("Student PIN"));
        form.add(pin);
        form.add(signIn);
        form.add(status);
        signIn.addActionListener(event -> {
            if (accountFileInvalid) {
                status.setText("Login data is unavailable.");
                return;
            }
            Student student = findStudent(id.getText().trim());
            PasswordCredential credential = student == null
                    ? null : studentCredentials.get(student.id);
            char[] entered = pin.getPassword();
            boolean valid = credential != null && verifyPassword(entered, credential);
            java.util.Arrays.fill(entered, '\0');
            pin.setText("");
            if (!valid) {
                status.setText("Student ID or PIN doesn't match.");
                return;
            }
            signedInStudent = student;
            refreshStudentPage();
            rootLayout.show(rootCards, "Student");
        });
        return form;
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(INK);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        return label;
    }

    private JPanel buildTeacherWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.add(buildSidebar(), BorderLayout.WEST);
        workspace.add(buildMainArea(), BorderLayout.CENTER);
        return workspace;
    }

    private JPanel buildStudentWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(0, 18));
        workspace.setBackground(CANVAS);
        JPanel topbar = new JPanel(new BorderLayout());
        topbar.setBackground(Color.WHITE);
        topbar.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
        JLabel title = new JLabel("Student attendance");
        title.setForeground(INK);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        JButton signOut = secondaryButton("Sign out");
        signOut.addActionListener(event -> signOut());
        topbar.add(title, BorderLayout.WEST);
        topbar.add(signOut, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(CANVAS);
        content.setBorder(BorderFactory.createEmptyBorder(25, 30, 30, 30));
        studentNameHeading.setForeground(INK);
        studentNameHeading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 27));
        studentNameHeading.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
        JPanel intro = new JPanel(new BorderLayout());
        intro.setOpaque(false);
        intro.add(studentNameHeading, BorderLayout.NORTH);
        content.add(intro, BorderLayout.NORTH);

        JPanel details = new JPanel(new GridLayout(1, 3, 12, 0));
        details.setOpaque(false);
        details.add(statCard("Attendance rate", studentRateValue, "Present and late count as attended"));
        details.add(statCard("Days attended", studentAttendedValue, "Across recorded days"));
        details.add(statCard("Days recorded", studentMarkedValue, "Your attendance history"));
        JPanel history = new JPanel(new BorderLayout());
        history.setBackground(Color.WHITE);
        history.setBorder(panelBorder());
        history.add(panelHeader("Your check-in history", "Only your own records are shown here."),
                BorderLayout.NORTH);
        styleTable(studentHistoryTable);
        history.add(new JScrollPane(studentHistoryTable), BorderLayout.CENTER);
        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setOpaque(false);
        center.add(details, BorderLayout.NORTH);
        center.add(history, BorderLayout.CENTER);
        JPanel warning = new JPanel(new BorderLayout());
        warning.setBackground(new Color(255, 249, 238));
        warning.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(242, 226, 190)),
                BorderFactory.createEmptyBorder(13, 15, 13, 15)));
        warning.add(studentWarning, BorderLayout.CENTER);
        center.add(warning, BorderLayout.SOUTH);
        content.add(center, BorderLayout.CENTER);
        workspace.add(topbar, BorderLayout.NORTH);
        workspace.add(content, BorderLayout.CENTER);
        return workspace;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(new Color(251, 252, 251));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, LINE));
        sidebar.setPreferredSize(new Dimension(220, 0));

        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        brand.setBorder(BorderFactory.createEmptyBorder(25, 18, 22, 14));
        JLabel mark = new JLabel("✓", SwingConstants.CENTER);
        mark.setOpaque(true);
        mark.setBackground(GREEN);
        mark.setForeground(Color.WHITE);
        mark.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        mark.setPreferredSize(new Dimension(38, 38));
        JLabel brandText = new JLabel("<html><b>Classroom</b><br><span style='color:#788783;font-size:10px'>Attendance workspace</span></html>");
        brand.add(mark, BorderLayout.WEST);
        brand.add(brandText, BorderLayout.CENTER);

        JPanel nav = new JPanel(new GridLayout(0, 1, 0, 7));
        nav.setOpaque(false);
        nav.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
        nav.add(navButton("▦   Overview", "Overview"));
        nav.add(navButton("♙   Students", "Students"));
        nav.add(navButton("▤   Reports", "Reports"));

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        JLabel section = new JLabel("WORKSPACE");
        section.setForeground(new Color(153, 165, 158));
        section.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        section.setBorder(BorderFactory.createEmptyBorder(12, 21, 0, 12));
        center.add(section, BorderLayout.NORTH);
        center.add(nav, BorderLayout.CENTER);

        JLabel note = new JLabel("<html><b>● Saved on this device</b><br><span style='color:#788783;font-size:10px'>Your class data stays local.</span></html>");
        note.setOpaque(true);
        note.setBackground(Color.WHITE);
        note.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        JPanel bottom = new JPanel(new BorderLayout(0, 14));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(12, 14, 18, 14));
        bottom.add(note, BorderLayout.NORTH);
        JLabel footer = new JLabel("A little more present, every day.");
        footer.setForeground(MUTED);
        footer.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        bottom.add(footer, BorderLayout.SOUTH);

        sidebar.add(brand, BorderLayout.NORTH);
        sidebar.add(center, BorderLayout.CENTER);
        sidebar.add(bottom, BorderLayout.SOUTH);
        return sidebar;
    }

    private JButton navButton(String text, String page) {
        JButton button = new JButton(text);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setBackground(page.equals(currentPage) ? GREEN_SOFT : new Color(251, 252, 251));
        button.setForeground(page.equals(currentPage) ? GREEN_DARK : MUTED);
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        button.setBorder(BorderFactory.createEmptyBorder(11, 12, 11, 10));
        button.addActionListener(event -> showPage(page));
        navigationButtons.put(page, button);
        return button;
    }

    private JPanel buildMainArea() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(CANVAS);
        JPanel topbar = new JPanel(new BorderLayout());
        topbar.setBackground(Color.WHITE);
        topbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                BorderFactory.createEmptyBorder(17, 28, 17, 28)));
        JLabel path = new JLabel("Workspace   /   ");
        path.setForeground(MUTED);
        JPanel bread = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bread.setOpaque(false);
        bread.add(path);
        breadcrumb.setForeground(INK);
        breadcrumb.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        bread.add(breadcrumb);
        JLabel date = new JLabel(LocalDate.now().toString());
        date.setForeground(MUTED);
        JButton signOut = secondaryButton("Sign out");
        signOut.addActionListener(event -> signOut());
        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        topActions.setOpaque(false);
        topActions.add(date);
        topActions.add(signOut);
        topbar.add(bread, BorderLayout.WEST);
        topbar.add(topActions, BorderLayout.EAST);

        pageCards.setBackground(CANVAS);
        pageCards.setBorder(BorderFactory.createEmptyBorder(26, 30, 30, 30));
        pageCards.add(buildDashboardPage(), "Overview");
        pageCards.add(buildStudentsPage(), "Students");
        pageCards.add(buildReportsPage(), "Reports");
        main.add(topbar, BorderLayout.NORTH);
        main.add(pageCards, BorderLayout.CENTER);
        return main;
    }

    private JPanel buildDashboardPage() {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout(12, 12));
        header.setOpaque(false);
        header.add(titleBlock("YOUR CLASSROOM AT A GLANCE", "Good morning",
                "A clear view of who's here and how your class is doing."), BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        actions.setOpaque(false);
        dateFieldStyle(attendanceDate);
        JButton dateButton = secondaryButton("Use today");
        dateButton.addActionListener(event -> {
            attendanceDate.setText(LocalDate.now().toString());
            refreshDashboard();
        });
        JButton fillButton = primaryButton("Fill unmarked");
        fillButton.addActionListener(event -> fillUnmarked());
        actions.add(new JLabel("Date"));
        actions.add(attendanceDate);
        actions.add(dateButton);
        actions.add(fillButton);
        header.add(actions, BorderLayout.EAST);

        JPanel stats = new JPanel(new GridLayout(1, 4, 12, 0));
        stats.setOpaque(false);
        stats.add(statCard("Total students", totalValue, "In your class list"));
        stats.add(statCard("Present", presentValue, "Including late arrivals"));
        stats.add(statCard("Absent", absentValue, "For the selected date"));
        stats.add(statCard("Attendance rate", rateValue, "Of marked students"));

        JPanel attendancePanel = new JPanel(new BorderLayout());
        attendancePanel.setBackground(Color.WHITE);
        attendancePanel.setBorder(panelBorder());
        JPanel panelHeader = panelHeader("Daily attendance", "Update each student's status; changes save automatically.");
        JPanel searchWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        searchWrap.setOpaque(false);
        searchWrap.add(new JLabel("Search  "));
        searchWrap.add(dashboardSearch);
        panelHeader.add(searchWrap, BorderLayout.EAST);
        attendancePanel.add(panelHeader, BorderLayout.NORTH);
        styleTable(attendanceTable);
        attendanceTable.setRowHeight(43);
        attendanceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        attendanceTable.getColumnModel().getColumn(2).setCellEditor(
                new DefaultCellEditor(new JComboBox<>(ATTENDANCE_OPTIONS)));
        attendancePanel.add(new JScrollPane(attendanceTable), BorderLayout.CENTER);
        attendanceHint.setForeground(MUTED);
        attendanceHint.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        attendancePanel.add(attendanceHint, BorderLayout.SOUTH);

        JPanel warningPanel = new JPanel(new BorderLayout(0, 6));
        warningPanel.setBackground(new Color(255, 249, 238));
        warningPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(242, 226, 190)),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JLabel warningTitle = new JLabel("LOW ATTENDANCE · BELOW " + LOW_ATTENDANCE_THRESHOLD + "%");
        warningTitle.setForeground(new Color(151, 105, 47));
        warningTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        warningList.setForeground(new Color(106, 88, 63));
        warningList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        warningPanel.add(warningTitle, BorderLayout.NORTH);
        warningPanel.add(warningList, BorderLayout.CENTER);

        JPanel upper = new JPanel(new BorderLayout(0, 14));
        upper.setOpaque(false);
        upper.add(header, BorderLayout.NORTH);
        upper.add(stats, BorderLayout.CENTER);
        upper.add(warningPanel, BorderLayout.SOUTH);
        page.add(upper, BorderLayout.NORTH);
        page.add(attendancePanel, BorderLayout.CENTER);
        attachSearch(dashboardSearch, attendanceTable);
        attendanceDate.addActionListener(event -> refreshDashboard());
        return page;
    }

    private JPanel buildStudentsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 17));
        page.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(titleBlock("YOUR CLASS ROSTER", "Students",
                "Manage names and IDs, and review each student's attendance."), BorderLayout.WEST);
        JButton export = secondaryButton("Export CSV");
        export.addActionListener(event -> exportCsv());
        header.add(export, BorderLayout.EAST);

        JPanel rosterPanel = new JPanel(new BorderLayout());
        rosterPanel.setBackground(Color.WHITE);
        rosterPanel.setBorder(panelBorder());
        JPanel rosterHeader = panelHeader("Class roster", "Select a student to edit or remove them.");
        JPanel searchWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        searchWrap.setOpaque(false);
        searchWrap.add(new JLabel("Search  "));
        searchWrap.add(rosterSearch);
        rosterHeader.add(searchWrap, BorderLayout.EAST);
        rosterPanel.add(rosterHeader, BorderLayout.NORTH);
        styleTable(studentTable);
        rosterPanel.add(new JScrollPane(studentTable), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(11, 14, 11, 14));
        rosterCount.setForeground(MUTED);
        footer.add(rosterCount, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton add = primaryButton("Add student");
        add.addActionListener(event -> addStudentDialog());
        JButton edit = secondaryButton("Edit selected");
        edit.addActionListener(event -> editSelectedStudent());
        JButton delete = dangerButton("Remove");
        delete.addActionListener(event -> deleteSelectedStudent());
        buttons.add(add);
        buttons.add(edit);
        buttons.add(delete);
        footer.add(buttons, BorderLayout.EAST);
        rosterPanel.add(footer, BorderLayout.SOUTH);

        page.add(header, BorderLayout.NORTH);
        page.add(rosterPanel, BorderLayout.CENTER);
        attachSearch(rosterSearch, studentTable);
        return page;
    }

    private JPanel buildReportsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 17));
        page.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(titleBlock("PATTERNS OVER TIME", "Attendance reports",
                "A thoughtful look at each student's check-in history."), BorderLayout.WEST);
        JPanel dateTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        dateTools.setOpaque(false);
        dateFieldStyle(reportDate);
        JButton print = secondaryButton("Print report");
        print.addActionListener(event -> printReport());
        dateTools.add(new JLabel("Date"));
        dateTools.add(reportDate);
        dateTools.add(print);
        header.add(dateTools, BorderLayout.EAST);

        JPanel summaries = new JPanel(new GridLayout(1, 3, 12, 0));
        summaries.setOpaque(false);
        summaries.add(statCard("Attended", reportAttendedValue, "Present and late"));
        summaries.add(statCard("Absent", reportAbsentValue, "For selected date"));
        summaries.add(statCard("Class rate", reportRateValue, "Of marked students"));
        JPanel reportPanel = new JPanel(new BorderLayout());
        reportPanel.setBackground(Color.WHITE);
        reportPanel.setBorder(panelBorder());
        reportPanel.add(panelHeader("Student attendance history",
                "Attendance rate includes present and late days."), BorderLayout.NORTH);
        styleTable(reportTable);
        reportPanel.add(new JScrollPane(reportTable), BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout(0, 16));
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        top.add(summaries, BorderLayout.CENTER);
        page.add(top, BorderLayout.NORTH);
        page.add(reportPanel, BorderLayout.CENTER);
        reportDate.addActionListener(event -> refreshReports());
        return page;
    }

    private JPanel titleBlock(String eyebrow, String title, String subtitle) {
        JPanel block = new JPanel(new BorderLayout(0, 4));
        block.setOpaque(false);
        JLabel overline = new JLabel(eyebrow);
        overline.setForeground(GREEN);
        overline.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        JLabel heading = new JLabel(title);
        heading.setForeground(INK);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 27));
        JLabel sub = new JLabel(subtitle);
        sub.setForeground(MUTED);
        sub.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        JPanel stack = new JPanel(new GridLayout(0, 1, 0, 4));
        stack.setOpaque(false);
        stack.add(overline);
        stack.add(heading);
        stack.add(sub);
        block.add(stack, BorderLayout.CENTER);
        return block;
    }

    private JPanel statCard(String title, JLabel value, String foot) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(15, 16, 14, 16)));
        JLabel label = new JLabel(title);
        label.setForeground(MUTED);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        value.setForeground(INK);
        value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 27));
        JLabel detail = new JLabel(foot);
        detail.setForeground(MUTED);
        detail.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        JPanel content = new JPanel(new GridLayout(0, 1, 0, 5));
        content.setOpaque(false);
        content.add(label);
        content.add(value);
        content.add(detail);
        card.add(content, BorderLayout.CENTER);
        card.setPreferredSize(new Dimension(150, 100));
        return card;
    }

    private JPanel panelHeader(String title, String subtitle) {
        JPanel header = new JPanel(new BorderLayout(10, 4));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                BorderFactory.createEmptyBorder(13, 15, 13, 15)));
        JPanel texts = new JPanel(new GridLayout(0, 1, 0, 3));
        texts.setOpaque(false);
        JLabel heading = new JLabel(title);
        heading.setForeground(INK);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel sub = new JLabel(subtitle);
        sub.setForeground(MUTED);
        sub.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        texts.add(heading);
        texts.add(sub);
        header.add(texts, BorderLayout.CENTER);
        return header;
    }

    private javax.swing.border.Border panelBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(0, 0, 0, 0));
    }

    private JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(GREEN);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        return button;
    }

    private JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(Color.WHITE);
        button.setForeground(INK);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return button;
    }

    private JButton dangerButton(String text) {
        JButton button = secondaryButton(text);
        button.setForeground(new Color(166, 82, 82));
        return button;
    }

    private void dateFieldStyle(JTextField field) {
        field.setText(LocalDate.now().toString());
        field.setToolTipText("Enter a date as YYYY-MM-DD");
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(7, 9, 7, 9)));
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    }

    private void styleTable(JTable table) {
        table.setFillsViewportHeight(true);
        table.setRowHeight(42);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(239, 243, 240));
        table.setSelectionBackground(GREEN_SOFT);
        table.setSelectionForeground(INK);
        table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        table.getTableHeader().setBackground(new Color(250, 252, 250));
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        table.getTableHeader().setPreferredSize(new Dimension(0, 37));
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable source, Object value,
                    boolean selected, boolean focused, int row, int column) {
                Component component = super.getTableCellRendererComponent(
                        source, value, selected, focused, row, column);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                if (!selected) {
                    component.setBackground(Color.WHITE);
                    component.setForeground(column == 0 ? INK : MUTED);
                }
                return component;
            }
        });
    }

    private void attachSearch(JTextField field, JTable table) {
        TableRowSorter<?> sorter = new TableRowSorter<>(table.getModel());
        table.setRowSorter(sorter);
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void filter() {
                String query = field.getText().trim();
                sorter.setRowFilter(query.isEmpty() ? null : RowFilter.regexFilter(
                        "(?i)" + java.util.regex.Pattern.quote(query)));
            }
            @Override public void insertUpdate(DocumentEvent event) { filter(); }
            @Override public void removeUpdate(DocumentEvent event) { filter(); }
            @Override public void changedUpdate(DocumentEvent event) { filter(); }
        });
    }

    private void showPage(String page) {
        currentPage = page;
        breadcrumb.setText(page);
        for (Map.Entry<String, JButton> entry : navigationButtons.entrySet()) {
            boolean selected = entry.getKey().equals(page);
            entry.getValue().setBackground(selected ? GREEN_SOFT : new Color(251, 252, 251));
            entry.getValue().setForeground(selected ? GREEN_DARK : MUTED);
        }
        pageLayout.show(pageCards, page);
        if ("Overview".equals(page)) {
            refreshDashboard();
        } else if ("Students".equals(page)) {
            studentModel.fireTableDataChanged();
        } else {
            refreshReports();
        }
    }

    private void showDashboard(ActionEvent event) {
        showPage("Overview");
    }

    private void refreshAll() {
        refreshDashboard();
        studentModel.fireTableDataChanged();
        refreshReports();
        rosterCount.setText(students.size() + " student" + (students.size() == 1 ? "" : "s"));
        if (signedInStudent != null) refreshStudentPage();
    }

    private void refreshDashboard() {
        LocalDate date = selectedDate(attendanceDate);
        if (date == null) {
            return;
        }
        AttendanceCounts counts = countsFor(date);
        totalValue.setText(String.valueOf(students.size()));
        presentValue.setText(String.valueOf(counts.present + counts.late));
        absentValue.setText(String.valueOf(counts.absent));
        int marked = counts.present + counts.absent + counts.late;
        rateValue.setText(marked == 0 ? "-" : Math.round((counts.present + counts.late) * 100.0 / marked) + "%");
        attendanceHint.setText(students.size() + " students  ·  " + marked
                + " marked  ·  Changes save automatically");
        StringBuilder alerts = new StringBuilder("<html>");
        int alertCount = 0;
        for (Student student : students.values()) {
            int studentMarked = markedDays(student.id);
            int percentage = attendanceRate(student.id);
            if (studentMarked > 0 && percentage < LOW_ATTENDANCE_THRESHOLD) {
                if (alertCount > 0) alerts.append(" &nbsp; · &nbsp; ");
                alerts.append(escapeHtml(student.name)).append(" (").append(percentage).append("%)");
                alertCount++;
            }
        }
        warningList.setText(alertCount == 0
                ? "No students are currently below the " + LOW_ATTENDANCE_THRESHOLD + "% target."
                : alerts.append("</html>").toString());
        attendanceModel.setDate(date);
    }

    private void refreshReports() {
        LocalDate date = selectedDate(reportDate);
        if (date == null) {
            return;
        }
        AttendanceCounts counts = countsFor(date);
        int marked = counts.present + counts.absent + counts.late;
        reportAttendedValue.setText(String.valueOf(counts.present + counts.late));
        reportAbsentValue.setText(String.valueOf(counts.absent));
        reportRateValue.setText(marked == 0 ? "-" :
                Math.round((counts.present + counts.late) * 100.0 / marked) + "%");
        reportModel.fireTableDataChanged();
    }

    private LocalDate selectedDate(JTextField field) {
        try {
            return LocalDate.parse(field.getText().trim());
        } catch (DateTimeParseException e) {
            JOptionPane.showMessageDialog(frame, "Enter a valid date in YYYY-MM-DD format.",
                    "Invalid date", JOptionPane.WARNING_MESSAGE);
            field.requestFocusInWindow();
            return null;
        }
    }

    private AttendanceCounts countsFor(LocalDate date) {
        AttendanceCounts counts = new AttendanceCounts();
        Map<String, AttendanceStatus> daily = attendance.get(date);
        for (Student student : students.values()) {
            AttendanceStatus status = daily == null ? null : daily.get(student.id);
            if (status == AttendanceStatus.PRESENT) counts.present++;
            else if (status == AttendanceStatus.ABSENT) counts.absent++;
            else if (status == AttendanceStatus.LATE) counts.late++;
            else counts.unmarked++;
        }
        return counts;
    }

    private int markedDays(String studentId) {
        int marked = 0;
        for (Map<String, AttendanceStatus> daily : attendance.values()) {
            if (daily.containsKey(studentId)) marked++;
        }
        return marked;
    }

    private int attendanceRate(String studentId) {
        int attended = 0;
        int marked = 0;
        for (Map<String, AttendanceStatus> daily : attendance.values()) {
            AttendanceStatus status = daily.get(studentId);
            if (status != null) {
                marked++;
                if (status != AttendanceStatus.ABSENT) attended++;
            }
        }
        return marked == 0 ? 0 : (int) Math.round(attended * 100.0 / marked);
    }

    private void fillUnmarked() {
        LocalDate date = selectedDate(attendanceDate);
        if (date == null) return;
        Map<String, AttendanceStatus> daily =
                attendance.computeIfAbsent(date, ignored -> new LinkedHashMap<>());
        int changed = 0;
        for (Student student : students.values()) {
            if (!daily.containsKey(student.id)) {
                daily.put(student.id, AttendanceStatus.PRESENT);
                changed++;
            }
        }
        if (changed == 0) {
            showMessage("Everyone already has a status for this date.", "Attendance");
            return;
        }
        if (saveData()) {
            refreshAll();
            showMessage(changed + " unmarked student(s) marked present.", "Attendance saved");
        }
    }

    private void addStudentDialog() {
        JTextField id = new JTextField(18);
        JTextField name = new JTextField(18);
        JPasswordField pin = new JPasswordField(18);
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 7));
        fields.add(new JLabel("Student ID"));
        fields.add(id);
        fields.add(new JLabel("Full name"));
        fields.add(name);
        fields.add(new JLabel("Student PIN (4-8 digits)"));
        fields.add(pin);
        if (JOptionPane.showConfirmDialog(frame, fields, "Add student",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        String studentId = id.getText().trim();
        String studentName = name.getText().trim();
        char[] studentPin = pin.getPassword();
        if (studentId.isEmpty() || studentName.isEmpty() || !validPin(studentPin)) {
            java.util.Arrays.fill(studentPin, '\0');
            showMessage("Student ID, name, and a 4-8 digit PIN are required.", "Check the details");
            return;
        }
        if (findStudent(studentId) != null) {
            java.util.Arrays.fill(studentPin, '\0');
            showMessage("That student ID is already in your class.", "Duplicate ID");
            return;
        }
        students.put(studentId, new Student(studentId, studentName));
        studentCredentials.put(studentId, createCredential(studentPin));
        java.util.Arrays.fill(studentPin, '\0');
        if (!saveData()) {
            students.remove(studentId);
            studentCredentials.remove(studentId);
            refreshAll();
            return;
        }
        if (!saveAccounts()) {
            students.remove(studentId);
            studentCredentials.remove(studentId);
            saveData();
        }
        refreshAll();
    }

    private void editSelectedStudent() {
        Student student = selectedStudent();
        if (student == null) return;
        JTextField id = new JTextField(student.id, 18);
        JTextField name = new JTextField(student.name, 18);
        JPasswordField pin = new JPasswordField(18);
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 7));
        fields.add(new JLabel("Student ID"));
        fields.add(id);
        fields.add(new JLabel("Full name"));
        fields.add(name);
        fields.add(new JLabel("New student PIN (leave blank to keep current)"));
        fields.add(pin);
        if (JOptionPane.showConfirmDialog(frame, fields, "Edit student",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        String newId = id.getText().trim();
        String newName = name.getText().trim();
        char[] newPin = pin.getPassword();
        Student conflict = findStudent(newId);
        if (newId.isEmpty() || newName.isEmpty()) {
            java.util.Arrays.fill(newPin, '\0');
            showMessage("Student ID and name are required.", "Check the details");
            return;
        }
        if (conflict != null && conflict != student) {
            java.util.Arrays.fill(newPin, '\0');
            showMessage("That student ID is already in your class.", "Duplicate ID");
            return;
        }
        String oldId = student.id;
        String oldName = student.name;
        PasswordCredential oldCredential = studentCredentials.get(oldId);
        if (newPin.length > 0 && !validPin(newPin)) {
            java.util.Arrays.fill(newPin, '\0');
            showMessage("Student PIN must contain 4-8 digits.", "Invalid PIN");
            return;
        }
        if (!oldId.equals(newId)) {
            students.remove(oldId);
            for (Map<String, AttendanceStatus> day : attendance.values()) {
                AttendanceStatus status = day.remove(oldId);
                if (status != null) day.put(newId, status);
            }
            PasswordCredential credential = studentCredentials.remove(oldId);
            if (credential != null) studentCredentials.put(newId, credential);
            student.id = newId;
            students.put(newId, student);
        }
        student.name = newName;
        if (newPin.length > 0) studentCredentials.put(newId, createCredential(newPin));
        java.util.Arrays.fill(newPin, '\0');
        if (!saveData() || !saveAccounts()) {
            if (!oldId.equals(newId)) {
                students.remove(newId);
                studentCredentials.remove(newId);
                for (Map<String, AttendanceStatus> day : attendance.values()) {
                    AttendanceStatus status = day.remove(newId);
                    if (status != null) day.put(oldId, status);
                }
                student.id = oldId;
                students.put(oldId, student);
                if (oldCredential != null) studentCredentials.put(oldId, oldCredential);
            } else if (oldCredential != null) {
                studentCredentials.put(oldId, oldCredential);
            } else {
                studentCredentials.remove(oldId);
            }
            student.name = oldName;
            saveData();
            saveAccounts();
        }
        refreshAll();
    }

    private void deleteSelectedStudent() {
        Student student = selectedStudent();
        if (student == null) return;
        int choice = JOptionPane.showConfirmDialog(frame,
                "Remove " + student.name + " and their attendance records?",
                "Remove student", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        Map<LocalDate, AttendanceStatus> oldRecords = new TreeMap<>();
        for (Map.Entry<LocalDate, Map<String, AttendanceStatus>> entry : attendance.entrySet()) {
            AttendanceStatus status = entry.getValue().get(student.id);
            if (status != null) oldRecords.put(entry.getKey(), status);
        }
        students.remove(student.id);
        PasswordCredential credential = studentCredentials.remove(student.id);
        for (Map<String, AttendanceStatus> day : attendance.values()) {
            day.remove(student.id);
        }
        if (!saveData() || !saveAccounts()) {
            students.put(student.id, student);
            if (credential != null) studentCredentials.put(student.id, credential);
            for (Map.Entry<LocalDate, AttendanceStatus> entry : oldRecords.entrySet()) {
                attendance.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashMap<>())
                        .put(student.id, entry.getValue());
            }
            saveData();
            saveAccounts();
            refreshAll();
            return;
        }
        refreshAll();
    }

    private boolean validPin(char[] pin) {
        if (pin.length < 4 || pin.length > 8) return false;
        for (char digit : pin) {
            if (digit < '0' || digit > '9') return false;
        }
        return true;
    }

    private Student selectedStudent() {
        int viewRow = studentTable.getSelectedRow();
        if (viewRow < 0) {
            showMessage("Select a student in the roster first.", "No student selected");
            return null;
        }
        int modelRow = studentTable.convertRowIndexToModel(viewRow);
        return studentModel.studentAt(modelRow);
    }

    private Student findStudent(String id) {
        for (Student student : students.values()) {
            if (student.id.equalsIgnoreCase(id)) return student;
        }
        return null;
    }

    private void setupTeacherAccount() {
        if (accountFileInvalid || teacherCredential != null) return;
        JPasswordField password = new JPasswordField(18);
        JPasswordField confirm = new JPasswordField(18);
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 7));
        fields.add(new JLabel("Create a teacher password (at least 8 characters)"));
        fields.add(password);
        fields.add(new JLabel("Confirm password"));
        fields.add(confirm);
        if (JOptionPane.showConfirmDialog(frame, fields, "Create teacher account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        char[] first = password.getPassword();
        char[] second = confirm.getPassword();
        password.setText("");
        confirm.setText("");
        if (first.length < 8 || !sameCharacters(first, second)) {
            java.util.Arrays.fill(first, '\0');
            java.util.Arrays.fill(second, '\0');
            showMessage("Use at least 8 characters and enter the same password twice.",
                    "Password not saved");
            return;
        }
        PasswordCredential previous = teacherCredential;
        teacherCredential = createCredential(first);
        java.util.Arrays.fill(first, '\0');
        java.util.Arrays.fill(second, '\0');
        if (saveAccounts()) {
            createTeacherButton.setVisible(false);
            showMessage("Teacher account created. You can now sign in.", "Account ready");
            rootLayout.show(rootCards, "Login");
        } else {
            teacherCredential = previous;
        }
    }

    private void showTeacherWorkspace() {
        currentPage = "Overview";
        rootLayout.show(rootCards, "Teacher");
        pageLayout.show(pageCards, currentPage);
        refreshAll();
    }

    private void signOut() {
        signedInStudent = null;
        rootLayout.show(rootCards, "Login");
    }

    private void refreshStudentPage() {
        if (signedInStudent == null) return;
        Student student = signedInStudent;
        int attended = 0;
        int marked = 0;
        for (Map<String, AttendanceStatus> daily : attendance.values()) {
            AttendanceStatus status = daily.get(student.id);
            if (status != null) {
                marked++;
                if (status != AttendanceStatus.ABSENT) attended++;
            }
        }
        int percentage = marked == 0 ? 0 : (int) Math.round(attended * 100.0 / marked);
        studentNameHeading.setText("Hello, " + student.name);
        studentRateValue.setText(marked == 0 ? "-" : percentage + "%");
        studentAttendedValue.setText(String.valueOf(attended));
        studentMarkedValue.setText(String.valueOf(marked));
        if (marked > 0 && percentage < LOW_ATTENDANCE_THRESHOLD) {
            studentWarning.setText("<html><b>Attendance needs attention</b> — your rate is "
                    + percentage + "%, below the " + LOW_ATTENDANCE_THRESHOLD + "% target.</html>");
            studentWarning.setForeground(new Color(145, 96, 37));
        } else {
            studentWarning.setText(marked == 0
                    ? "No attendance has been recorded for you yet."
                    : "You're on track. Keep up your attendance.");
            studentWarning.setForeground(GREEN_DARK);
        }
        studentHistoryModel.fireTableDataChanged();
    }

    private PasswordCredential createCredential(char[] password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return new PasswordCredential(salt, hashPassword(password, salt));
    }

    private boolean sameCharacters(char[] first, char[] second) {
        int difference = first.length ^ second.length;
        int length = Math.max(first.length, second.length);
        for (int i = 0; i < length; i++) {
            char left = i < first.length ? first[i] : 0;
            char right = i < second.length ? second[i] : 0;
            difference |= left ^ right;
        }
        return difference == 0;
    }

    private boolean verifyPassword(char[] password, PasswordCredential credential) {
        return MessageDigest.isEqual(hashPassword(password, credential.salt), credential.hash);
    }

    private byte[] hashPassword(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, HASH_ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is unavailable.", e);
        } finally {
            spec.clearPassword();
        }
    }

    private void loadAccounts() throws IOException {
        if (!Files.exists(ACCOUNT_FILE)) return;
        try (BufferedReader reader =
                     Files.newBufferedReader(ACCOUNT_FILE, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (!"CLASSROOM-ACCOUNTS\t1".equals(header)) {
                throw new IOException("Unrecognized account file format.");
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String[] fields = line.split("\t", -1);
                try {
                    if (fields.length == 3 && fields[0].equals("T")) {
                        if (teacherCredential != null) throw new IllegalArgumentException("Duplicate teacher account");
                        teacherCredential = new PasswordCredential(
                                Base64.getDecoder().decode(fields[1]),
                                Base64.getDecoder().decode(fields[2]));
                    } else if (fields.length == 4 && fields[0].equals("S")) {
                        String id = decode(fields[1]);
                        PasswordCredential credential = new PasswordCredential(
                                Base64.getDecoder().decode(fields[2]),
                                Base64.getDecoder().decode(fields[3]));
                        if (!students.containsKey(id) || studentCredentials.put(id, credential) != null) {
                            throw new IllegalArgumentException("Unknown or duplicate student account");
                        }
                    } else {
                        throw new IllegalArgumentException("Unrecognized account record");
                    }
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid account data at line " + lineNumber, e);
                }
            }
        }
    }

    private boolean saveAccounts() {
        Path temporaryFile = ACCOUNT_FILE.resolveSibling(ACCOUNT_FILE.getFileName() + ".tmp");
        try (BufferedWriter writer =
                     Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
            writer.write("CLASSROOM-ACCOUNTS\t1");
            writer.newLine();
            if (teacherCredential != null) {
                writer.write("T\t" + Base64.getEncoder().encodeToString(teacherCredential.salt)
                        + "\t" + Base64.getEncoder().encodeToString(teacherCredential.hash));
                writer.newLine();
            }
            for (Map.Entry<String, PasswordCredential> entry : studentCredentials.entrySet()) {
                writer.write("S\t" + encode(entry.getKey()) + "\t"
                        + Base64.getEncoder().encodeToString(entry.getValue().salt) + "\t"
                        + Base64.getEncoder().encodeToString(entry.getValue().hash));
                writer.newLine();
            }
        } catch (IOException e) {
            showMessage("Could not save login credentials:\n" + e.getMessage(), "Save failed");
            return false;
        }
        try {
            Files.move(temporaryFile, ACCOUNT_FILE, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(temporaryFile, ACCOUNT_FILE, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException moveError) {
                showMessage("Could not save login credentials:\n" + moveError.getMessage(),
                        "Save failed");
                return false;
            }
        } catch (IOException e) {
            showMessage("Could not save login credentials:\n" + e.getMessage(), "Save failed");
            return false;
        }
    }

    private void exportCsv() {
        Path csvFile = Path.of("attendance-report.csv");
        try (BufferedWriter writer =
                     Files.newBufferedWriter(csvFile, StandardCharsets.UTF_8)) {
            writer.write("Date,Student ID,Student Name,Status");
            writer.newLine();
            for (Map.Entry<LocalDate, Map<String, AttendanceStatus>> day
                    : attendance.entrySet()) {
                for (Map.Entry<String, AttendanceStatus> record : day.getValue().entrySet()) {
                    Student student = students.get(record.getKey());
                    if (student == null) continue;
                    writer.write(csv(day.getKey().toString()) + ","
                            + csv(student.id) + "," + csv(student.name) + ","
                            + csv(record.getValue().toString()));
                    writer.newLine();
                }
            }
            showMessage("Attendance exported to:\n" + csvFile.toAbsolutePath(), "Export complete");
        } catch (IOException e) {
            showMessage("Could not export CSV:\n" + e.getMessage(), "Export failed");
        }
    }

    private void printReport() {
        try {
            reportTable.print(JTable.PrintMode.FIT_WIDTH,
                    new java.text.MessageFormat("Attendance report"),
                    new java.text.MessageFormat("Page {0}"));
        } catch (java.awt.print.PrinterException e) {
            showMessage("Could not print the report:\n" + e.getMessage(), "Print failed");
        }
    }

    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private void showMessage(String message, String title) {
        JOptionPane.showMessageDialog(frame, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void loadData() throws IOException {
        if (!Files.exists(DATA_FILE)) return;
        try (BufferedReader reader =
                     Files.newBufferedReader(DATA_FILE, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String[] fields = line.split("\t", -1);
                try {
                    if (fields.length == 3 && fields[0].equals("S")) {
                        String id = decode(fields[1]);
                        String name = decode(fields[2]);
                        if (id.isEmpty() || name.isEmpty() || students.containsKey(id)) {
                            throw new IllegalArgumentException("Invalid or duplicate student");
                        }
                        students.put(id, new Student(id, name));
                    } else if (fields.length == 4 && fields[0].equals("A")) {
                        LocalDate date = LocalDate.parse(fields[1]);
                        String id = decode(fields[2]);
                        AttendanceStatus status = AttendanceStatus.valueOf(fields[3]);
                        attendance.computeIfAbsent(date, ignored -> new LinkedHashMap<>())
                                .put(id, status);
                    } else {
                        throw new IllegalArgumentException("Unrecognized record");
                    }
                } catch (IllegalArgumentException | DateTimeParseException e) {
                    throw new IOException("Invalid data at line " + lineNumber, e);
                }
            }
        }
    }

    private boolean saveData() {
        Path temporaryFile = DATA_FILE.resolveSibling(DATA_FILE.getFileName() + ".tmp");
        try (BufferedWriter writer =
                     Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
            for (Student student : students.values()) {
                writer.write("S\t" + encode(student.id) + "\t" + encode(student.name));
                writer.newLine();
            }
            for (Map.Entry<LocalDate, Map<String, AttendanceStatus>> day
                    : attendance.entrySet()) {
                for (Map.Entry<String, AttendanceStatus> record : day.getValue().entrySet()) {
                    writer.write("A\t" + day.getKey() + "\t" + encode(record.getKey())
                            + "\t" + record.getValue());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            showMessage("Could not save attendance:\n" + e.getMessage(), "Save failed");
            return false;
        }
        try {
            Files.move(temporaryFile, DATA_FILE, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(temporaryFile, DATA_FILE, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException moveError) {
                showMessage("Could not save attendance:\n" + moveError.getMessage(), "Save failed");
                return false;
            }
        } catch (IOException e) {
            showMessage("Could not save attendance:\n" + e.getMessage(), "Save failed");
            return false;
        }
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private final class AttendanceTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] columns = {"Student", "Student ID", "Attendance status"};
        private LocalDate date = LocalDate.now();

        private void setDate(LocalDate selected) {
            date = selected;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return students.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public boolean isCellEditable(int row, int column) { return column == 2; }

        @Override
        public Object getValueAt(int row, int column) {
            Student student = studentAt(row);
            if (column == 0) return escapeHtml(student.name);
            if (column == 1) return escapeHtml(student.id);
            Map<String, AttendanceStatus> daily = attendance.get(date);
            AttendanceStatus status = daily == null ? null : daily.get(student.id);
            return status == null ? ATTENDANCE_OPTIONS[0] : titleCase(status);
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column != 2 || row < 0 || row >= students.size()) return;
            Student student = studentAt(row);
            String selected = String.valueOf(value);
            Map<String, AttendanceStatus> daily =
                    attendance.computeIfAbsent(date, ignored -> new LinkedHashMap<>());
            if ("Not marked".equals(selected)) daily.remove(student.id);
            else daily.put(student.id, AttendanceStatus.valueOf(selected.toUpperCase()));
            if (daily.isEmpty()) attendance.remove(date);
            if (saveData()) refreshAll();
        }

        private Student studentAt(int row) {
            return students.values().stream().skip(row).findFirst().orElseThrow();
        }
    }

    private final class StudentTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] columns = {"Student ID", "Student name", "Attendance rate", "Alert"};
        @Override public int getRowCount() { return students.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override
        public Object getValueAt(int row, int column) {
            Student student = studentAt(row);
            if (column == 0) return escapeHtml(student.id);
            if (column == 1) return escapeHtml(student.name);
            int marked = markedDays(student.id);
            if (column == 2) {
                return marked == 0 ? "No records" :
                        attendanceRate(student.id) + "%  (" + marked + " days)";
            }
            return marked > 0 && attendanceRate(student.id) < LOW_ATTENDANCE_THRESHOLD
                    ? "Below " + LOW_ATTENDANCE_THRESHOLD + "%" : "On track";
        }

        private Student studentAt(int row) {
            return students.values().stream().skip(row).findFirst().orElseThrow();
        }
    }

    private final class ReportTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] columns = {"Student", "Present", "Late", "Absent", "Attendance rate"};
        @Override public int getRowCount() { return students.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override
        public Object getValueAt(int row, int column) {
            Student student = students.values().stream().skip(row).findFirst().orElseThrow();
            int present = 0;
            int late = 0;
            int absent = 0;
            for (Map<String, AttendanceStatus> daily : attendance.values()) {
                AttendanceStatus status = daily.get(student.id);
                if (status == AttendanceStatus.PRESENT) present++;
                else if (status == AttendanceStatus.LATE) late++;
                else if (status == AttendanceStatus.ABSENT) absent++;
            }

            int marked = present + late + absent;
            if (column == 0) return escapeHtml(student.name) + "  (" + escapeHtml(student.id) + ")";
            if (column == 1) return present;
            if (column == 2) return late;
            if (column == 3) return absent;
            return marked == 0 ? "-" :
                    Math.round((present + late) * 100.0 / marked) + "%";
        }
    }

    private final class StudentHistoryTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] columns = {"Date", "Status"};

        private java.util.List<Map.Entry<LocalDate, AttendanceStatus>> records() {
            java.util.List<Map.Entry<LocalDate, AttendanceStatus>> result =
                    new java.util.ArrayList<>();
            if (signedInStudent == null) return result;
            for (Map.Entry<LocalDate, Map<String, AttendanceStatus>> entry
                    : new TreeMap<>(attendance).descendingMap().entrySet()) {
                AttendanceStatus status = entry.getValue().get(signedInStudent.id);
                if (status != null) {
                    result.add(new java.util.AbstractMap.SimpleImmutableEntry<>(
                            entry.getKey(), status));
                }
            }
            return result;
        }

        @Override public int getRowCount() { return records().size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Object getValueAt(int row, int column) {
            Map.Entry<LocalDate, AttendanceStatus> record = records().get(row);
            return column == 0 ? record.getKey().toString() : titleCase(record.getValue());
        }
    }

    private static String titleCase(AttendanceStatus status) {
        String value = status.name().toLowerCase();
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static final class AttendanceCounts {
        private int present;
        private int absent;
        private int late;
        private int unmarked;
    }

    private static final class PasswordCredential {
        private final byte[] salt;
        private final byte[] hash;

        private PasswordCredential(byte[] salt, byte[] hash) {
            if (salt.length != 16 || hash.length != 32) {
                throw new IllegalArgumentException("Invalid password credential.");
            }
            this.salt = salt.clone();
            this.hash = hash.clone();
        }
    }

    private static final class Student {
        private String id;
        private String name;

        private Student(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private enum AttendanceStatus {
        PRESENT,
        ABSENT,
        LATE
    }
}
