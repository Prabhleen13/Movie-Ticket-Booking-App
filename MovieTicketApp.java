import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.io.*;

// JavaMail imports
import javax.mail.*;
import javax.mail.internet.*;

// iText PDF imports (only the classes we need)
import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;    // iText Font – used only inside PDF generation
import com.itextpdf.text.PageSize;

public class MovieTicketApp extends JFrame {

    private CardLayout cardLayout;
    private JPanel mainPanel;

    private String selectedMovie = "", selectedTheatre = "", selectedShow = "";
    private int selectedShowId = -1;
    private Set<String> selectedSeats = new HashSet<>();
    private String currentUser = "";
    private int totalSpent = 0;
    private int loyaltyPoints = 0;
    private static final int SEAT_PRICE = 200;
    private String generatedOtp = "";

    // Food order
    private java.util.List<String> foodOrder = new ArrayList<>();
    private int foodTotal = 0;

    // Theme support
    static boolean isDarkTheme = true;
    private String currentScreen = "login";

    // Admin panel components
    private JTable movieTable;
    private DefaultTableModel movieTableModel;
    private JTextField adminTitleField, adminIconField, adminRatingField, adminPosterField, adminTrailerField;
    private JButton adminAddBtn, adminUpdateBtn, adminDeleteBtn, adminRefreshBtn;

    // Search & filter
    private JTextField searchField;
    private JComboBox<String> ratingFilterCombo;
    private JPanel movieContentPanel;
    private java.util.List<Movie> currentMovies = new ArrayList<>();

    // Profile fields
    private JTextField profileNameField, profilePhoneField, profileEmailField;
    private JPasswordField profilePasswordField, profileConfirmField;
    private JLabel profilePointsLabel, profileSpentLabel;

    // Points redemption in payment
    private JSpinner pointsSpinner;
    private int pointsToUse = 0;
    private int discountedTotal = 0;

    // Food screen components
    private java.util.List<JCheckBox> foodCheckboxes = new ArrayList<>();
    private Map<String, Integer> foodItemsMap = new LinkedHashMap<>();

    public MovieTicketApp() {
        setTitle("🎬 MovieTicket");
        setSize(1200, 800);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.setBackground(Theme.getBackgroundColor());

        // Initialize food items
        foodItemsMap.put("Popcorn (Regular)", 150);
        foodItemsMap.put("Popcorn (Large)", 250);
        foodItemsMap.put("Soda", 100);
        foodItemsMap.put("Nachos", 120);
        foodItemsMap.put("Candy", 80);

        initializeDatabase();
        showLogin();

        add(mainPanel);
        setVisible(true);
    }

    // ===================== THEME COLORS =====================
    static class Theme {
        static Color getBackgroundColor() { return isDarkTheme ? new Color(18, 18, 18) : new Color(245, 245, 245); }
        static Color getCardColor() { return isDarkTheme ? new Color(44, 44, 44) : new Color(255, 255, 255); }
        static Color getTextColor() { return isDarkTheme ? Color.WHITE : Color.BLACK; }
        static Color getSecondaryTextColor() { return isDarkTheme ? new Color(136, 136, 136) : new Color(100, 100, 100); }
        static Color getButtonColor() { return new Color(229, 9, 20); }
        static Color getBorderColor() { return isDarkTheme ? new Color(100, 100, 100) : new Color(200, 200, 200); }
        static Color getPanelColor() { return isDarkTheme ? new Color(30, 30, 30) : new Color(240, 240, 240); }
        static GradientPaint getLeftLoginGradient() {
            if (isDarkTheme) return new GradientPaint(0, 0, new Color(229, 9, 20), 400, 700, new Color(80, 0, 10));
            else return new GradientPaint(0, 0, new Color(255, 100, 100), 400, 700, new Color(200, 50, 50));
        }
        static GradientPaint getLeftRegGradient() {
            if (isDarkTheme) return new GradientPaint(0, 0, new Color(106, 13, 173), 300, 500, new Color(0, 50, 100));
            else return new GradientPaint(0, 0, new Color(150, 50, 200), 300, 500, new Color(80, 80, 200));
        }
        static GradientPaint getRightRegGradient() {
            return new GradientPaint(0, 0, new Color(30, 30, 30), 500, 500, new Color(80, 80, 80));
        }
        static GradientPaint getBackgroundGradient() {
            if (isDarkTheme) return new GradientPaint(0, 0, new Color(18, 18, 18), 1200, 800, new Color(40, 40, 40));
            else return new GradientPaint(0, 0, new Color(245, 245, 245), 1200, 800, new Color(220, 220, 220));
        }
        static GradientPaint getFoodGradient() {
            return new GradientPaint(0, 0, new Color(10, 10, 10), 1200, 800, new Color(50, 50, 50));
        }
    }

    // ===================== DATABASE CONNECTION =====================
    private static class DBConnection {
        private static String URL;
private static String USER;
private static String PASSWORD;

static {
    try (InputStream input = new FileInputStream("config.properties")) {
        Properties prop = new Properties();
        prop.load(input);
        URL = prop.getProperty("db.url");
        USER = prop.getProperty("db.user");
        PASSWORD = prop.getProperty("db.password");
    } catch (IOException e) {
        e.printStackTrace();
        throw new RuntimeException("Could not load config.properties");
    }
}

        public static Connection getConnection() throws SQLException {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        }
    }

    // ===================== CREATE TABLES =====================
    private void createTablesIfNotExist(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();

        stmt.executeUpdate(
            "CREATE TABLE IF NOT EXISTS users (" +
            "id INT PRIMARY KEY AUTO_INCREMENT," +
            "username VARCHAR(50) UNIQUE NOT NULL," +
            "password VARCHAR(100) NOT NULL," +
            "full_name VARCHAR(100)," +
            "phone VARCHAR(20)," +
            "email VARCHAR(100)," +
            "is_admin INT DEFAULT 0," +
            "loyalty_points INT DEFAULT 0," +
            "total_spent INT DEFAULT 0" +
            ")"
        );

        stmt.executeUpdate(
            "CREATE TABLE IF NOT EXISTS movies (" +
            "id INT PRIMARY KEY AUTO_INCREMENT," +
            "title VARCHAR(200) NOT NULL," +
            "icon VARCHAR(10)," +
            "rating DOUBLE," +
            "poster_path VARCHAR(500)," +
            "teaser_url VARCHAR(500)" +
            ")"
        );

        stmt.executeUpdate(
            "CREATE TABLE IF NOT EXISTS theatres (" +
            "id INT PRIMARY KEY AUTO_INCREMENT," +
            "name VARCHAR(100) NOT NULL," +
            "rating DOUBLE," +
            "distance_km DOUBLE" +
            ")"
        );

        stmt.executeUpdate(
            "CREATE TABLE IF NOT EXISTS shows (" +
            "id INT PRIMARY KEY AUTO_INCREMENT," +
            "movie_id INT NOT NULL," +
            "theatre_id INT NOT NULL," +
            "show_time VARCHAR(20)," +
            "show_type VARCHAR(50)," +
            "price INT," +
            "FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE," +
            "FOREIGN KEY (theatre_id) REFERENCES theatres(id) ON DELETE CASCADE" +
            ")"
        );

        stmt.executeUpdate(
            "CREATE TABLE IF NOT EXISTS seats (" +
            "id INT PRIMARY KEY AUTO_INCREMENT," +
            "show_id INT NOT NULL," +
            "seat_number VARCHAR(10) NOT NULL," +
            "is_booked BOOLEAN DEFAULT FALSE," +
            "FOREIGN KEY (show_id) REFERENCES shows(id) ON DELETE CASCADE" +
            ")"
        );

        stmt.executeUpdate(
            "CREATE TABLE IF NOT EXISTS bookings (" +
            "id INT PRIMARY KEY AUTO_INCREMENT," +
            "booking_id VARCHAR(50) UNIQUE NOT NULL," +
            "user_id INT," +
            "show_id INT NOT NULL," +
            "seats TEXT," +
            "total_amount INT," +
            "booking_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "payment_method VARCHAR(50)," +
            "status VARCHAR(20) DEFAULT 'confirmed'," +
            "food_items TEXT," +
            "points_used INT DEFAULT 0," +
            "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL," +
            "FOREIGN KEY (show_id) REFERENCES shows(id) ON DELETE CASCADE" +
            ")"
        );

        addColumnIfNotExists(conn, "bookings", "food_items", "TEXT");
        addColumnIfNotExists(conn, "bookings", "points_used", "INT DEFAULT 0");

        System.out.println("Tables created/verified.");
    }

    private void addColumnIfNotExists(Connection conn, String tableName, String columnName, String columnDef) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        ResultSet rs = meta.getColumns(null, null, tableName, columnName);
        if (!rs.next()) {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDef);
            System.out.println("Added column " + columnName + " to " + tableName);
        }
    }

    private void initializeDatabase() {
        try (Connection conn = DBConnection.getConnection()) {
            createTablesIfNotExist(conn);
            addTeaserColumnIfNotExists(conn);
            createAdminUser(conn);

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM movies");
            rs.next();
            if (rs.getInt(1) == 0) {
                insertMoviesAndTheatres(conn);
                insertShows(conn);
            }
            populateSeatsIfNeeded(conn);
            System.out.println("Database initialization complete.");
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database initialization failed: " + e.getMessage());
        }
    }

    private void addTeaserColumnIfNotExists(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        ResultSet rs = meta.getColumns(null, null, "movies", "teaser_url");
        if (!rs.next()) {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("ALTER TABLE movies ADD COLUMN teaser_url VARCHAR(500)");
            System.out.println("Added teaser_url column.");
        }
    }

    private void createAdminUser(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        ResultSet rs = meta.getColumns(null, null, "users", "is_admin");
        if (!rs.next()) {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN is_admin INT DEFAULT 0");
        }

        PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE username = 'admin'");
        rs = pstmt.executeQuery();
        rs.next();
        if (rs.getInt(1) == 0) {
            String sql = "INSERT INTO users (username, password, full_name, phone, email, is_admin, loyalty_points) VALUES (?, ?, ?, ?, ?, ?, ?)";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, "admin");
            pstmt.setString(2, "admin");
            pstmt.setString(3, "Administrator");
            pstmt.setString(4, "0000000000");
            pstmt.setString(5, "admin@movieticket.com");
            pstmt.setInt(6, 1);
            pstmt.setInt(7, 0);
            pstmt.executeUpdate();
            System.out.println("Admin user created.");
        }
    }

    private void insertMoviesAndTheatres(Connection conn) throws SQLException {
        String[] titles = {
            "Dhurandhar 2", "Pathaan", "The Kerala Story", "Kalki", "Border",
            "Gadar", "RRR", "KGF Chapter 2", "Pushpa: The Rule"
        };
        String[] icons = {"🎬", "💥", "📖", "⚡", "🇮🇳", "💪", "🐅", "⛏️", "🌿"};
        double[] ratings = {4.5, 4.5, 4.5, 4.5, 4.5, 4.5, 4.8, 4.7, 4.6};
        String[] posters = {
            "posters/dhurandhar 2.jpeg",
            "posters/pathaan.jpg",
            "posters/The kerala Story.jpg",
            "posters/Kalki.jpeg",
            "posters/border.jpg",
            "posters/gadar2.jpeg",
            "posters/RRR.jpeg",
            "posters/KGF2.jpeg",
            "posters/pushpa.jpg"
        };
        String[] teasers = {
            "https://youtu.be/Pb2KJlBGids",
            "https://youtu.be/vqu4z34wENw",
            "https://youtu.be/j0_038C5RRk",
            "https://youtu.be/aninoDcPWo4",
            "https://youtu.be/AZGfCK1yTTI",
            "https://youtu.be/mljj92tRwlk",
            "https://youtu.be/GY4BgdUSpbE",
            "https://youtu.be/JKa05nyUmuQ",
            "https://youtu.be/1kVK0MZlbI4"
        };

        String insertMovie = "INSERT INTO movies (title, icon, rating, poster_path, teaser_url) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement pstmt = conn.prepareStatement(insertMovie);
        for (int i = 0; i < titles.length; i++) {
            pstmt.setString(1, titles[i]);
            pstmt.setString(2, icons[i]);
            pstmt.setDouble(3, ratings[i]);
            pstmt.setString(4, posters[i]);
            pstmt.setString(5, teasers[i]);
            pstmt.addBatch();
        }
        pstmt.executeBatch();

        String[] theatreNames = {"PVR", "INOX", "Cinepolis", "Miraj"};
        double[] theatreRatings = {4.2, 4.5, 4.0, 3.8};
        double[] distances = {2.5, 3.2, 4.1, 1.8};

        String insertTheatre = "INSERT INTO theatres (name, rating, distance_km) VALUES (?, ?, ?)";
        pstmt = conn.prepareStatement(insertTheatre);
        for (int i = 0; i < theatreNames.length; i++) {
            pstmt.setString(1, theatreNames[i]);
            pstmt.setDouble(2, theatreRatings[i]);
            pstmt.setDouble(3, distances[i]);
            pstmt.addBatch();
        }
        pstmt.executeBatch();
    }

    private void insertShows(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        java.util.List<Integer> movieIds = new ArrayList<>();
        ResultSet rs = stmt.executeQuery("SELECT id FROM movies ORDER BY id");
        while (rs.next()) movieIds.add(rs.getInt("id"));
        java.util.List<Integer> theatreIds = new ArrayList<>();
        rs = stmt.executeQuery("SELECT id FROM theatres ORDER BY id");
        while (rs.next()) theatreIds.add(rs.getInt("id"));

        String[] showTimes = {"10:00 AM", "2:00 PM", "6:00 PM", "9:00 PM"};
        String[] showTypes = {"Morning Show", "Matinee", "Evening Show", "Night Show"};
        int[] prices = {200, 250, 300, 350};

        String insertShow = "INSERT INTO shows (movie_id, theatre_id, show_time, show_type, price) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement pstmt = conn.prepareStatement(insertShow);
        for (int movieId : movieIds) {
            for (int theatreId : theatreIds) {
                for (int i = 0; i < showTimes.length; i++) {
                    pstmt.setInt(1, movieId);
                    pstmt.setInt(2, theatreId);
                    pstmt.setString(3, showTimes[i]);
                    pstmt.setString(4, showTypes[i]);
                    pstmt.setInt(5, prices[i]);
                    pstmt.addBatch();
                }
            }
        }
        pstmt.executeBatch();
    }

    private void populateSeatsIfNeeded(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM shows");
        rs.next();
        int showCount = rs.getInt(1);
        if (showCount == 0) return;

        rs = stmt.executeQuery("SELECT COUNT(*) FROM seats");
        rs.next();
        int seatCount = rs.getInt(1);
        int expectedSeats = showCount * 25;

        if (seatCount == expectedSeats) return;

        stmt.executeUpdate("DELETE FROM seats");
        rs = stmt.executeQuery("SELECT id FROM shows");
        java.util.List<Integer> showIds = new ArrayList<>();
        while (rs.next()) showIds.add(rs.getInt("id"));

        String insertSeat = "INSERT INTO seats (show_id, seat_number) VALUES (?, ?)";
        PreparedStatement pstmt = conn.prepareStatement(insertSeat);
        char[] rows = {'A','B','C','D','E'};
        for (int showId : showIds) {
            for (char row : rows) {
                for (int col = 1; col <= 5; col++) {
                    String seat = "" + row + col;
                    pstmt.setInt(1, showId);
                    pstmt.setString(2, seat);
                    pstmt.addBatch();
                }
            }
            pstmt.executeBatch();
        }
    }

    // ===================== HELPER METHODS =====================
    private int getUserId(String username) {
        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement pstmt = conn.prepareStatement("SELECT id FROM users WHERE username = ?");
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    private boolean isAdmin(String username) {
        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement pstmt = conn.prepareStatement("SELECT is_admin FROM users WHERE username = ?");
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("is_admin") == 1;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    private void loadUserDetails() {
        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement pstmt = conn.prepareStatement("SELECT total_spent, loyalty_points FROM users WHERE username = ?");
            pstmt.setString(1, currentUser);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                totalSpent = rs.getInt("total_spent");
                loyaltyPoints = rs.getInt("loyalty_points");
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void updateUserLoyalty(int spentAmount) {
        int earnedPoints = spentAmount / 10;
        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement pstmt = conn.prepareStatement("UPDATE users SET total_spent = total_spent + ?, loyalty_points = loyalty_points + ? WHERE username = ?");
            pstmt.setInt(1, spentAmount);
            pstmt.setInt(2, earnedPoints);
            pstmt.setString(3, currentUser);
            pstmt.executeUpdate();
            loadUserDetails();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void deductLoyaltyPoints(int points) {
        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement pstmt = conn.prepareStatement("UPDATE users SET loyalty_points = loyalty_points - ? WHERE username = ?");
            pstmt.setInt(1, points);
            pstmt.setString(2, currentUser);
            pstmt.executeUpdate();
            loadUserDetails();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private String[] getShowDetails(int showId) {
        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement pstmt = conn.prepareStatement("SELECT m.title, t.name, s.show_time FROM shows s JOIN movies m ON s.movie_id = m.id JOIN theatres t ON s.theatre_id = t.id WHERE s.id = ?");
            pstmt.setInt(1, showId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return new String[]{rs.getString("title"), rs.getString("name"), rs.getString("show_time")};
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    private void playClickSound() { Toolkit.getDefaultToolkit().beep(); }

    private void toggleTheme() {
        isDarkTheme = !isDarkTheme;
        switch (currentScreen) {
            case "login": showLogin(); break;
            case "movies": showMovies(); break;
            case "theatres": showTheatres(); break;
            case "shows": showShows(); break;
            case "seats": showSeats(); break;
            case "food": showFoodScreen(); break;
            case "payment": showPayment(0); break;
            case "confirmation": showConfirmation("", 0); break;
            case "history": showBookingHistory(); break;
            case "admin": showAdminPanel(); break;
            case "profile": showProfile(); break;
            default: showLogin();
        }
    }

    // ===================== REAL EMAIL SENDING METHODS =====================
    private void sendEmail(String to, String subject, String body) {
        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");

                // REPLACE WITH YOUR EMAIL AND PASSWORD/APP PASSWORD
                final String username;
final String password;
try (InputStream input = new FileInputStream("config.properties")) {
    Properties prop = new Properties();
    prop.load(input);
    username = prop.getProperty("email.user");
    password = prop.getProperty("email.password");
} catch (IOException e) {
    e.printStackTrace();
    throw new RuntimeException("Email config missing");
}

                Session session = Session.getInstance(props, new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(username));
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
                message.setSubject(subject);
                message.setText(body);

                Transport.send(message);
                System.out.println("✅ Email sent to " + to);
            } catch (MessagingException e) {
                e.printStackTrace();
                System.err.println("❌ Failed to send email: " + e.getMessage());
            }
        }).start();
    }

    // ===================== LOGIN SCREEN =====================
    void showLogin() {
        currentScreen = "login";
        JPanel main = new JPanel(new GridBagLayout());
        main.setBackground(Theme.getBackgroundColor());

        JPanel leftPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = Theme.getLeftLoginGradient();
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setPreferredSize(new Dimension(400, 700));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(100, 20, 20, 20));

        JLabel welcome = new JLabel("Welcome to");
        welcome.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 32));
        welcome.setForeground(Color.WHITE);
        welcome.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel appName = new JLabel("MOVIETICKET");
        appName.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 48));
        appName.setForeground(Color.WHITE);
        appName.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel tagline = new JLabel("Book Your Dream Show");
        tagline.setFont(new java.awt.Font("Segoe UI", java.awt.Font.ITALIC, 18));
        tagline.setForeground(Color.WHITE);
        tagline.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel icons = new JLabel("🎬 🍿 🎭 🎫");
        icons.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 60));
        icons.setForeground(Color.WHITE);
        icons.setAlignmentX(Component.CENTER_ALIGNMENT);

        leftPanel.add(Box.createVerticalGlue());
        leftPanel.add(welcome);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(appName);
        leftPanel.add(Box.createVerticalStrut(20));
        leftPanel.add(tagline);
        leftPanel.add(Box.createVerticalStrut(40));
        leftPanel.add(icons);
        leftPanel.add(Box.createVerticalGlue());

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBackground(Theme.getPanelColor());
        rightPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.getBorderColor(), 1), BorderFactory.createEmptyBorder(40,40,40,40)));
        rightPanel.setPreferredSize(new Dimension(400, 700));

        JLabel title = new JLabel("MOVIETICKET");
        title.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 36));
        title.setForeground(Theme.getButtonColor());
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel loginLabel = new JLabel("Welcome");
        loginLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 24));
        loginLabel.setForeground(Theme.getTextColor());
        loginLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField userField = new JTextField(20);
        styleTextField(userField);
        JPasswordField passField = new JPasswordField(20);
        styleTextField(passField);
        JLabel errorMsg = new JLabel();
        errorMsg.setForeground(new Color(255,107,107));
        errorMsg.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton loginBtn = createStyledButton("Login");
        loginBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginBtn.addActionListener(e -> {
            playClickSound();
            String username = userField.getText().trim();
            String password = new String(passField.getPassword()).trim();
            if (username.isEmpty() || password.isEmpty()) {
                errorMsg.setText("❌ Please enter username and password");
                return;
            }
            try (Connection conn = DBConnection.getConnection()) {
                PreparedStatement pstmt = conn.prepareStatement("SELECT password FROM users WHERE username = ?");
                pstmt.setString(1, username);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next() && rs.getString("password").equals(password)) {
                    currentUser = username;
                    loadUserDetails();
                    if (isAdmin(username)) showAdminPanel();
                    else showMovies();
                } else errorMsg.setText("❌ Invalid username or password");
            } catch (SQLException ex) { errorMsg.setText("Database error: " + ex.getMessage()); }
        });

        JButton guestBtn = createStyledButton("Continue as Guest");
        guestBtn.setBackground(new Color(76,175,80));
        guestBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        guestBtn.addActionListener(e -> {
            playClickSound();
            currentUser = "Guest";
            showMovies();
        });

        JLabel registerLink = new JLabel("<HTML><U>Don't have an account? Register</U></HTML>");
        registerLink.setForeground(new Color(100,149,237));
        registerLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        registerLink.setAlignmentX(Component.CENTER_ALIGNMENT);
        registerLink.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { playClickSound(); showRegistrationDialog(); }
        });

        JLabel forgotLink = new JLabel("<HTML><U>Forgot Password ?</U></HTML>");
        forgotLink.setForeground(new Color(100,149,237));
        forgotLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        forgotLink.setAlignmentX(Component.CENTER_ALIGNMENT);
        forgotLink.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { playClickSound(); showForgotPasswordDialog(); }
        });

        JButton adminSecretBtn = new JButton("🔒");
        adminSecretBtn.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        adminSecretBtn.setBackground(new Color(0,0,0,0));
        adminSecretBtn.setBorderPainted(false);
        adminSecretBtn.setFocusPainted(false);
        adminSecretBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        adminSecretBtn.addActionListener(e -> { playClickSound(); showAdminLoginDialog(); });

        rightPanel.add(Box.createVerticalStrut(60));
        rightPanel.add(title);
        rightPanel.add(Box.createVerticalStrut(20));
        rightPanel.add(loginLabel);
        rightPanel.add(Box.createVerticalStrut(30));
        JLabel userLbl = new JLabel("Username");
        userLbl.setForeground(Theme.getTextColor());
        userLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(userLbl);
        rightPanel.add(Box.createVerticalStrut(5));
        rightPanel.add(userField);
        rightPanel.add(Box.createVerticalStrut(15));
        JLabel passLbl = new JLabel("Password");
        passLbl.setForeground(Theme.getTextColor());
        passLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(passLbl);
        rightPanel.add(Box.createVerticalStrut(5));
        rightPanel.add(passField);
        rightPanel.add(Box.createVerticalStrut(15));
        rightPanel.add(loginBtn);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(guestBtn);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(registerLink);
        rightPanel.add(Box.createVerticalStrut(5));
        rightPanel.add(forgotLink);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(errorMsg);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.5; gbc.fill = GridBagConstraints.BOTH;
        main.add(leftPanel, gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        main.add(rightPanel, gbc);
        main.add(adminSecretBtn, new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE, new Insets(10,10,10,10), 0, 0));

        mainPanel.add(main, "login");
        cardLayout.show(mainPanel, "login");
    }

    private void styleTextField(JTextField field) {
        field.setBackground(isDarkTheme ? new Color(44,44,44) : new Color(220,220,220));
        field.setForeground(Theme.getTextColor());
        field.setCaretColor(Theme.getTextColor());
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.getBorderColor(),1), BorderFactory.createEmptyBorder(8,8,8,8)));
        field.setMaximumSize(new Dimension(300,40));
        field.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    private void showAdminLoginDialog() {
        JDialog dialog = new JDialog(this, "Admin Login", true);
        dialog.setLayout(new GridBagLayout());
        dialog.setBackground(Theme.getBackgroundColor());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10,10,10,10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField userField = new JTextField(15);
        styleTextField(userField);
        JPasswordField passField = new JPasswordField(15);
        styleTextField(passField);
        JLabel status = new JLabel();
        status.setForeground(Color.RED);
        gbc.gridx=0; gbc.gridy=0; dialog.add(new JLabel("Admin Username:"), gbc);
        gbc.gridx=1; dialog.add(userField, gbc);
        gbc.gridy=1; gbc.gridx=0; dialog.add(new JLabel("Password:"), gbc);
        gbc.gridx=1; dialog.add(passField, gbc);
        gbc.gridy=2; gbc.gridx=0; gbc.gridwidth=2; dialog.add(status, gbc);
        JButton loginBtn = new JButton("Login");
        loginBtn.setBackground(Theme.getButtonColor());
        loginBtn.setForeground(Color.WHITE);
        loginBtn.addActionListener(e -> {
            playClickSound();
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword()).trim();
            if ("admin".equals(user) && "admin".equals(pass)) {
                dialog.dispose();
                currentUser = "admin";
                showAdminPanel();
            } else status.setText("Invalid admin credentials");
        });
        gbc.gridy=3; dialog.add(loginBtn, gbc);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ===================== ADMIN PANEL =====================
    private void showAdminPanel() {
        currentScreen = "admin";
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.getBackgroundColor());
        JPanel topBar = createTopBar();
        panel.add(topBar, BorderLayout.NORTH);
        JLabel title = new JLabel("Admin Panel - Movie Management", SwingConstants.CENTER);
        title.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 28));
        title.setForeground(Theme.getTextColor());
        title.setBorder(BorderFactory.createEmptyBorder(20,0,20,0));
        panel.add(title, BorderLayout.NORTH);

        movieTableModel = new DefaultTableModel(new String[]{"ID","Title","Icon","Rating","Poster Path","Trailer URL"},0);
        movieTable = new JTable(movieTableModel);
        movieTable.setBackground(Theme.getCardColor());
        movieTable.setForeground(Theme.getTextColor());
        movieTable.getTableHeader().setBackground(Theme.getPanelColor());
        movieTable.getTableHeader().setForeground(Theme.getTextColor());
        movieTable.setSelectionBackground(Theme.getButtonColor());
        movieTable.setSelectionForeground(Color.WHITE);
        JScrollPane scrollPane = new JScrollPane(movieTable);
        scrollPane.setBackground(Theme.getBackgroundColor());

        JPanel formPanel = new JPanel(new GridLayout(0,2,10,10));
        formPanel.setBackground(Theme.getPanelColor());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        formPanel.add(new JLabel("Title:"));
        adminTitleField = new JTextField(); styleTextField(adminTitleField); formPanel.add(adminTitleField);
        formPanel.add(new JLabel("Icon (emoji):"));
        adminIconField = new JTextField(); styleTextField(adminIconField); formPanel.add(adminIconField);
        formPanel.add(new JLabel("Rating:"));
        adminRatingField = new JTextField(); styleTextField(adminRatingField); formPanel.add(adminRatingField);
        formPanel.add(new JLabel("Poster Path:"));
        adminPosterField = new JTextField(); styleTextField(adminPosterField); formPanel.add(adminPosterField);
        formPanel.add(new JLabel("Trailer URL:"));
        adminTrailerField = new JTextField(); styleTextField(adminTrailerField); formPanel.add(adminTrailerField);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER,20,10));
        adminAddBtn = createStyledButton("Add Movie");
        adminUpdateBtn = createStyledButton("Update Selected");
        adminDeleteBtn = createStyledButton("Delete Selected");
        adminRefreshBtn = createStyledButton("Refresh");
        buttonPanel.add(adminAddBtn); buttonPanel.add(adminUpdateBtn); buttonPanel.add(adminDeleteBtn); buttonPanel.add(adminRefreshBtn);

        adminAddBtn.addActionListener(e -> addMovie());
        adminUpdateBtn.addActionListener(e -> updateMovie());
        adminDeleteBtn.addActionListener(e -> deleteMovie());
        adminRefreshBtn.addActionListener(e -> refreshMovieTable());

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(formPanel, BorderLayout.CENTER);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);
        southPanel.setBackground(Theme.getBackgroundColor());

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(southPanel, BorderLayout.SOUTH);
        refreshMovieTable();
        mainPanel.add(panel, "admin");
        cardLayout.show(mainPanel, "admin");
    }

    private void refreshMovieTable() {
        movieTableModel.setRowCount(0);
        try (Connection conn = DBConnection.getConnection()) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, title, icon, rating, poster_path, teaser_url FROM movies ORDER BY id");
            while (rs.next()) {
                movieTableModel.addRow(new Object[]{rs.getInt("id"), rs.getString("title"), rs.getString("icon"), rs.getDouble("rating"), rs.getString("poster_path"), rs.getString("teaser_url")});
            }
        } catch (SQLException e) { e.printStackTrace(); JOptionPane.showMessageDialog(this, "Error loading movies: "+e.getMessage()); }
    }

    private void addMovie() {
        String title = adminTitleField.getText().trim();
        String icon = adminIconField.getText().trim();
        String ratingStr = adminRatingField.getText().trim();
        String poster = adminPosterField.getText().trim();
        String trailer = adminTrailerField.getText().trim();
        if (title.isEmpty() || icon.isEmpty() || ratingStr.isEmpty()) { JOptionPane.showMessageDialog(this, "Title, icon and rating are required."); return; }
        double rating; try { rating = Double.parseDouble(ratingStr); } catch (NumberFormatException e) { JOptionPane.showMessageDialog(this, "Rating must be a number."); return; }
        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement pstmt = conn.prepareStatement("INSERT INTO movies (title, icon, rating, poster_path, teaser_url) VALUES (?,?,?,?,?)");
            pstmt.setString(1, title); pstmt.setString(2, icon); pstmt.setDouble(3, rating);
            pstmt.setString(4, poster.isEmpty()?null:poster); pstmt.setString(5, trailer.isEmpty()?null:trailer);
            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(this, "Movie added!");
            refreshMovieTable(); clearAdminForm();
        } catch (SQLException e) { e.printStackTrace(); JOptionPane.showMessageDialog(this, "Database error: "+e.getMessage()); }
    }

    private void updateMovie() {
        int selectedRow = movieTable.getSelectedRow();
        if (selectedRow == -1) { JOptionPane.showMessageDialog(this, "Select a movie to update."); return; }
        int id = (int) movieTableModel.getValueAt(selectedRow, 0);
        String title = adminTitleField.getText().trim();
        String icon = adminIconField.getText().trim();
        String ratingStr = adminRatingField.getText().trim();
        String poster = adminPosterField.getText().trim();
        String trailer = adminTrailerField.getText().trim();
        if (title.isEmpty() || icon.isEmpty() || ratingStr.isEmpty()) { JOptionPane.showMessageDialog(this, "Title, icon and rating are required."); return; }
        double rating; try { rating = Double.parseDouble(ratingStr); } catch (NumberFormatException e) { JOptionPane.showMessageDialog(this, "Rating must be a number."); return; }
        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement pstmt = conn.prepareStatement("UPDATE movies SET title=?, icon=?, rating=?, poster_path=?, teaser_url=? WHERE id=?");
            pstmt.setString(1, title); pstmt.setString(2, icon); pstmt.setDouble(3, rating);
            pstmt.setString(4, poster.isEmpty()?null:poster); pstmt.setString(5, trailer.isEmpty()?null:trailer); pstmt.setInt(6, id);
            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(this, "Movie updated!");
            refreshMovieTable(); clearAdminForm();
        } catch (SQLException e) { e.printStackTrace(); JOptionPane.showMessageDialog(this, "Database error: "+e.getMessage()); }
    }

    private void deleteMovie() {
        int selectedRow = movieTable.getSelectedRow();
        if (selectedRow == -1) { JOptionPane.showMessageDialog(this, "Select a movie to delete."); return; }
        int id = (int) movieTableModel.getValueAt(selectedRow, 0);
        if (JOptionPane.showConfirmDialog(this, "Delete this movie?", "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement pstmt = conn.prepareStatement("DELETE FROM movies WHERE id=?");
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(this, "Movie deleted!");
            refreshMovieTable(); clearAdminForm();
        } catch (SQLException e) { e.printStackTrace(); JOptionPane.showMessageDialog(this, "Database error: "+e.getMessage()); }
    }

    private void clearAdminForm() {
        adminTitleField.setText(""); adminIconField.setText(""); adminRatingField.setText(""); adminPosterField.setText(""); adminTrailerField.setText("");
        movieTable.clearSelection();
    }

    // ===================== REGISTRATION DIALOG =====================
    private void showRegistrationDialog() {
        JDialog dialog = new JDialog(this, "Register", true);
        dialog.setLayout(new GridBagLayout());
        dialog.setBackground(Theme.getBackgroundColor());
        dialog.setSize(900, 550);
        dialog.setLocationRelativeTo(this);

        JPanel leftPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = Theme.getLeftRegGradient();
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setPreferredSize(new Dimension(350, 550));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(50, 20, 20, 20));

        JLabel regIcon = new JLabel("🎭 Join the Show");
        regIcon.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 32));
        regIcon.setForeground(Color.WHITE);
        regIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel regTagline = new JLabel("Create your account");
        regTagline.setFont(new java.awt.Font("Segoe UI", java.awt.Font.ITALIC, 18));
        regTagline.setForeground(Color.WHITE);
        regTagline.setAlignmentX(Component.CENTER_ALIGNMENT);

        leftPanel.add(Box.createVerticalGlue());
        leftPanel.add(regIcon);
        leftPanel.add(Box.createVerticalStrut(15));
        leftPanel.add(regTagline);
        leftPanel.add(Box.createVerticalGlue());

        JPanel rightPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = Theme.getRightRegGradient();
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        rightPanel.setPreferredSize(new Dimension(550, 550));

        JTextField nameField = new JTextField(15);
        JTextField phoneField = new JTextField(15);
        JTextField emailField = new JTextField(15);
        JTextField usernameField = new JTextField(15);
        JPasswordField passField = new JPasswordField(15);
        JPasswordField confirmField = new JPasswordField(15);
        JTextField otpField = new JTextField(15);
        JButton sendOtpBtn = new JButton("Send OTP");
        sendOtpBtn.setBackground(new Color(0, 120, 215));
        sendOtpBtn.setForeground(Color.WHITE);
        sendOtpBtn.setFocusPainted(false);

        JLabel status = new JLabel();
        status.setForeground(new Color(255, 200, 0));
        status.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 12));

        JComponent[] fields = {nameField, phoneField, emailField, usernameField, passField, confirmField, otpField};
        for (JComponent f : fields) {
            f.setBackground(isDarkTheme ? new Color(44,44,44) : new Color(220,220,220));
            f.setForeground(Theme.getTextColor());
            ((JTextField)f).setCaretColor(Theme.getTextColor());
            f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.getBorderColor(),1), BorderFactory.createEmptyBorder(8,8,8,8)));
            ((JTextField)f).setMaximumSize(new Dimension(300,35));
            ((JTextField)f).setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        otpField.setMaximumSize(new Dimension(200,35));
        sendOtpBtn.setMaximumSize(new Dimension(100,35));
        sendOtpBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel otpPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        otpPanel.setBackground(new Color(0,0,0,0));
        otpPanel.add(otpField);
        otpPanel.add(sendOtpBtn);

        JButton registerBtn = createStyledButton("Register");
        registerBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        registerBtn.setPreferredSize(new Dimension(200,40));

        rightPanel.add(Box.createVerticalStrut(10));
        addFormRow(rightPanel, "Full Name", nameField);
        addFormRow(rightPanel, "Phone Number", phoneField);
        addFormRow(rightPanel, "Email", emailField);
        addFormRow(rightPanel, "Username", usernameField);
        addFormRow(rightPanel, "Password", passField);
        addFormRow(rightPanel, "Confirm Password", confirmField);
        addFormRow(rightPanel, "OTP", otpPanel);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(registerBtn);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(status);

        sendOtpBtn.addActionListener(e -> {
            playClickSound();
            String email = emailField.getText().trim();
            if (email.isEmpty()) {
                status.setText("Please enter email address");
                return;
            }
            generatedOtp = String.format("%06d", new Random().nextInt(1000000));
            String subject = "MovieTicket Registration OTP";
            String body = "Your OTP for registration is: " + generatedOtp + "\n\nValid for 10 minutes.\n\nThank you!";
            sendEmail(email, subject, body);
            JOptionPane.showMessageDialog(dialog, "OTP sent to " + email + "\nPlease check your inbox.", "OTP Sent", JOptionPane.INFORMATION_MESSAGE);
            status.setText("OTP sent! Enter the code.");
        });

        registerBtn.addActionListener(e -> {
            playClickSound();
            String name = nameField.getText().trim();
            String phone = phoneField.getText().trim();
            String email = emailField.getText().trim();
            String username = usernameField.getText().trim();
            String password = new String(passField.getPassword());
            String confirm = new String(confirmField.getPassword());
            String otp = otpField.getText().trim();
            if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || username.isEmpty() || password.isEmpty() || confirm.isEmpty() || otp.isEmpty()) {
                status.setText("All fields are required");
                return;
            }
            if (!password.equals(confirm)) { status.setText("Passwords do not match"); return; }
            if (!otp.equals(generatedOtp)) { status.setText("Invalid OTP"); return; }
            try (Connection conn = DBConnection.getConnection()) {
                PreparedStatement pstmt = conn.prepareStatement("INSERT INTO users (username, password, full_name, phone, email) VALUES (?,?,?,?,?)");
                pstmt.setString(1, username); pstmt.setString(2, password); pstmt.setString(3, name); pstmt.setString(4, phone); pstmt.setString(5, email);
                pstmt.executeUpdate();
                JOptionPane.showMessageDialog(dialog, "Registration successful! You can now login.");
                dialog.dispose();
            } catch (SQLException ex) {
                if (ex.getMessage().contains("Duplicate entry")) status.setText("Username already exists!");
                else status.setText("Database error: "+ex.getMessage());
            }
        });

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.4; gbc.fill = GridBagConstraints.BOTH;
        dialog.add(leftPanel, gbc);
        gbc.gridx = 1; gbc.weightx = 0.6;
        dialog.add(rightPanel, gbc);
        dialog.pack();
        dialog.setVisible(true);
    }

    private void addFormRow(JPanel container, String labelText, JComponent field) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(new Color(0,0,0,0));
        JLabel label = new JLabel(labelText);
        label.setForeground(Color.WHITE);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        row.add(label);
        row.add(Box.createVerticalStrut(5));
        if (field instanceof JPanel) ((JPanel)field).setAlignmentX(Component.CENTER_ALIGNMENT);
        else field.setAlignmentX(Component.CENTER_ALIGNMENT);
        row.add(field);
        row.setMaximumSize(new Dimension(350,70));
        container.add(row);
        container.add(Box.createVerticalStrut(10));
    }

    private void showForgotPasswordDialog() {
        JDialog dialog = new JDialog(this, "Forgot Password", true);
        dialog.setLayout(new GridBagLayout());
        dialog.setBackground(Theme.getBackgroundColor());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10,10,10,10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField userField = new JTextField(15);
        styleTextField(userField);
        gbc.gridx=0; gbc.gridy=0; dialog.add(new JLabel("Enter your username:"), gbc);
        gbc.gridx=1; dialog.add(userField, gbc);
        JLabel result = new JLabel();
        result.setForeground(Color.BLUE);
        gbc.gridx=0; gbc.gridy=1; gbc.gridwidth=2; dialog.add(result, gbc);
        JButton submitBtn = new JButton("Submit");
        submitBtn.setBackground(Theme.getButtonColor());
        submitBtn.setForeground(Color.WHITE);
        submitBtn.setFocusPainted(false);
        submitBtn.addActionListener(e -> {
            playClickSound();
            String user = userField.getText().trim();
            try (Connection conn = DBConnection.getConnection()) {
                PreparedStatement pstmt = conn.prepareStatement("SELECT password FROM users WHERE username = ?");
                pstmt.setString(1, user);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) result.setText("Your password is: "+rs.getString("password"));
                else { result.setText("Username not found!"); result.setForeground(Color.RED); }
            } catch (SQLException ex) { result.setText("Database error"); }
        });
        gbc.gridy=2; dialog.add(submitBtn, gbc);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ===================== MOVIE SELECTION =====================
    void showMovies() {
        currentScreen = "movies";
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.getBackgroundColor());

        JPanel topBar = createTopBar();
        panel.add(topBar, BorderLayout.NORTH);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        searchPanel.setBackground(Theme.getBackgroundColor());
        searchPanel.add(new JLabel("Search:"));
        searchField = new JTextField(20);
        styleTextField(searchField);
        searchField.setMaximumSize(new Dimension(200,35));
        searchPanel.add(searchField);
        searchPanel.add(new JLabel("Min Rating:"));
        ratingFilterCombo = new JComboBox<>(new String[]{"All", "4.0+", "4.5+", "4.8+"});
        ratingFilterCombo.setBackground(Theme.getCardColor());
        ratingFilterCombo.setForeground(Theme.getTextColor());
        searchPanel.add(ratingFilterCombo);
        JButton searchBtn = createStyledButton("Apply Filters");
        searchBtn.setPreferredSize(new Dimension(120,35));
        searchBtn.addActionListener(e -> filterMovies());
        searchPanel.add(searchBtn);

        panel.add(searchPanel, BorderLayout.NORTH);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(Theme.getBackgroundColor());
        tabbedPane.setForeground(Theme.getTextColor());

        movieContentPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        movieContentPanel.setBackground(Theme.getBackgroundColor());
        JScrollPane scrollPane = new JScrollPane(movieContentPanel);
        scrollPane.setBackground(Theme.getBackgroundColor());
        scrollPane.getViewport().setBackground(Theme.getBackgroundColor());
        scrollPane.setBorder(null);
        tabbedPane.addTab("Now Showing", scrollPane);

        JPanel comingSoonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        comingSoonPanel.setBackground(Theme.getBackgroundColor());
        addComingSoonMovies(comingSoonPanel);
        JScrollPane comingScroll = new JScrollPane(comingSoonPanel);
        comingScroll.setBackground(Theme.getBackgroundColor());
        comingScroll.getViewport().setBackground(Theme.getBackgroundColor());
        tabbedPane.addTab("Coming Soon", comingScroll);

        panel.add(tabbedPane, BorderLayout.CENTER);

        loadAllMovies();
        filterMovies();

        mainPanel.add(panel, "movies");
        cardLayout.show(mainPanel, "movies");
    }

    private void loadAllMovies() {
        currentMovies.clear();
        try (Connection conn = DBConnection.getConnection()) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT title, icon, rating, poster_path, teaser_url FROM movies ORDER BY id");
            while (rs.next()) {
                currentMovies.add(new Movie(rs.getString("title"), rs.getString("icon"), rs.getDouble("rating"), rs.getString("poster_path"), rs.getString("teaser_url")));
            }
        } catch (SQLException ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Error loading movies: "+ex.getMessage()); }
    }

    private void filterMovies() {
        String search = searchField.getText().trim().toLowerCase();
        String ratingFilter = (String) ratingFilterCombo.getSelectedItem();
        double minRating = 0;
        if ("4.0+".equals(ratingFilter)) minRating = 4.0;
        else if ("4.5+".equals(ratingFilter)) minRating = 4.5;
        else if ("4.8+".equals(ratingFilter)) minRating = 4.8;

        java.util.List<Movie> filtered = new ArrayList<>();
        for (Movie m : currentMovies) {
            if (!search.isEmpty() && !m.title.toLowerCase().contains(search)) continue;
            if (minRating > 0 && m.rating < minRating) continue;
            filtered.add(m);
        }
        displayMovies(filtered);
    }

    private void displayMovies(java.util.List<Movie> movies) {
        movieContentPanel.removeAll();
        for (Movie m : movies) {
            JPanel movieCard = new JPanel(new BorderLayout());
            movieCard.setBackground(Theme.getCardColor());
            movieCard.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.getBorderColor(),1), BorderFactory.createEmptyBorder(10,10,10,10)));
            movieCard.setPreferredSize(new Dimension(300,420));
            movieCard.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { movieCard.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.getButtonColor(),2), BorderFactory.createEmptyBorder(10,10,10,10))); }
                public void mouseExited(MouseEvent e) { movieCard.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.getBorderColor(),1), BorderFactory.createEmptyBorder(10,10,10,10))); }
            });

            JPanel centerPanel = new JPanel();
            centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
            centerPanel.setBackground(Theme.getCardColor());

            JLabel posterLabel;
            if (m.posterPath != null && !m.posterPath.isEmpty()) {
                try {
                    ImageIcon icon = new ImageIcon(m.posterPath);
                    java.awt.Image scaled = icon.getImage().getScaledInstance(150,200, java.awt.Image.SCALE_SMOOTH);
                    posterLabel = new JLabel(new ImageIcon(scaled));
                } catch (Exception e) { posterLabel = new JLabel(m.icon); posterLabel.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN,48)); }
            } else { posterLabel = new JLabel(m.icon); posterLabel.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN,48)); }
            posterLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel name = new JLabel(m.title);
            name.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,14));
            name.setForeground(Theme.getTextColor());
            name.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel rating = new JLabel("⭐ "+m.rating);
            rating.setForeground(new Color(255,215,0));
            rating.setAlignmentX(Component.CENTER_ALIGNMENT);

            centerPanel.add(posterLabel);
            centerPanel.add(Box.createVerticalStrut(8));
            centerPanel.add(name);
            centerPanel.add(Box.createVerticalStrut(5));
            centerPanel.add(rating);
            centerPanel.add(Box.createVerticalStrut(10));

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER,10,0));
            buttonPanel.setBackground(Theme.getCardColor());
            JButton bookBtn = createStyledButton("Book Now");
            bookBtn.setPreferredSize(new Dimension(110,35));
            bookBtn.addActionListener(e -> { playClickSound(); selectedMovie = m.title; showTheatres(); });
            JButton trailerBtn = new JButton("🎬 Trailer");
            trailerBtn.setBackground(new Color(0,120,215));
            trailerBtn.setForeground(Color.WHITE);
            trailerBtn.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,12));
            trailerBtn.setBorderPainted(false);
            trailerBtn.setFocusPainted(false);
            trailerBtn.setPreferredSize(new Dimension(100,35));
            trailerBtn.addActionListener(e -> {
                playClickSound();
                if (m.teaserUrl != null && !m.teaserUrl.isEmpty()) {
                    try { Desktop.getDesktop().browse(new URI(m.teaserUrl)); }
                    catch (Exception ex) { JOptionPane.showMessageDialog(this, "Cannot open browser: "+ex.getMessage()); }
                } else JOptionPane.showMessageDialog(this, "No trailer available.");
            });
            buttonPanel.add(bookBtn);
            buttonPanel.add(trailerBtn);
            movieCard.add(centerPanel, BorderLayout.CENTER);
            movieCard.add(buttonPanel, BorderLayout.SOUTH);
            movieContentPanel.add(movieCard);
        }
        movieContentPanel.revalidate();
        movieContentPanel.repaint();
    }

    private void addComingSoonMovies(JPanel container) {
        String[][] coming = {
            {"Avatar 3", "🌊", "4.2", "posters/avatar3.jpg", "https://youtu.be/..."},
            {"Jawan 2", "🔥", "4.0", "posters/jawan2.jpg", "https://youtu.be/..."},
            {"Salaar 2", "⚔️", "4.3", "posters/salaar2.jpg", "https://youtu.be/..."}
        };
        for (String[] data : coming) {
            JPanel card = new JPanel(new BorderLayout());
            card.setBackground(Theme.getCardColor());
            card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.getBorderColor(),1), BorderFactory.createEmptyBorder(10,10,10,10)));
            card.setPreferredSize(new Dimension(300,350));
            JLabel icon = new JLabel(data[1]);
            icon.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 80));
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            JLabel title = new JLabel(data[0]);
            title.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 16));
            title.setForeground(Theme.getTextColor());
            title.setHorizontalAlignment(SwingConstants.CENTER);
            JLabel rating = new JLabel("⭐ "+data[2]);
            rating.setForeground(new Color(255,215,0));
            rating.setHorizontalAlignment(SwingConstants.CENTER);
            JLabel comingLabel = new JLabel("Coming Soon");
            comingLabel.setForeground(Theme.getButtonColor());
            comingLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
            comingLabel.setHorizontalAlignment(SwingConstants.CENTER);

            JPanel center = new JPanel();
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
            center.setBackground(Theme.getCardColor());
            center.add(icon);
            center.add(Box.createVerticalStrut(10));
            center.add(title);
            center.add(Box.createVerticalStrut(5));
            center.add(rating);
            center.add(Box.createVerticalStrut(5));
            center.add(comingLabel);
            card.add(center, BorderLayout.CENTER);
            container.add(card);
        }
    }

    // ===================== THEATRE SELECTION =====================
    void showTheatres() {
        currentScreen = "theatres";
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = Theme.getBackgroundGradient();
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setOpaque(false);

        JPanel topBar = createTopBar();
        panel.add(topBar, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);

        JLabel title = new JLabel("🏢 Select Theatre", SwingConstants.CENTER);
        title.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 28));
        title.setForeground(Theme.getTextColor());
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        centerPanel.add(title, BorderLayout.NORTH);

        JPanel theatreList = new JPanel();
        theatreList.setLayout(new BoxLayout(theatreList, BoxLayout.Y_AXIS));
        theatreList.setBackground(Theme.getBackgroundColor());
        theatreList.setBorder(BorderFactory.createEmptyBorder(10, 40, 10, 40));

        try (Connection conn = DBConnection.getConnection()) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT name, rating, distance_km FROM theatres");
            while (rs.next()) {
                String name = rs.getString("name");
                double rating = rs.getDouble("rating");
                double distance = rs.getDouble("distance_km");
                createTheatreCard(theatreList, name, rating, distance);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading theatres: " + ex.getMessage());
            return;
        }

        JScrollPane scrollPane = new JScrollPane(theatreList);
        scrollPane.setBackground(Theme.getBackgroundColor());
        scrollPane.getViewport().setBackground(Theme.getBackgroundColor());
        scrollPane.setBorder(null);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);

        JButton backBtn = createBackButton(() -> showMovies());
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setOpaque(false);
        bottomPanel.add(backBtn);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        mainPanel.add(panel, "theatres");
        cardLayout.show(mainPanel, "theatres");
    }

    private void createTheatreCard(JPanel container, String name, double rating, double distance) {
        JPanel theatreCard = new JPanel(new BorderLayout());
        theatreCard.setBackground(Theme.getCardColor());
        theatreCard.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.getBorderColor(),1), BorderFactory.createEmptyBorder(15,20,15,20)));
        theatreCard.setMaximumSize(new Dimension(500,80));

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(Theme.getCardColor());

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 18));
        nameLabel.setForeground(Theme.getTextColor());

        JLabel ratingLabel = new JLabel(rating + " ★");
        ratingLabel.setForeground(new Color(255,215,0));

        JLabel distanceLabel = new JLabel("📍 " + distance + " km away");
        distanceLabel.setForeground(Theme.getSecondaryTextColor());
        distanceLabel.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 11));

        infoPanel.add(nameLabel);
        infoPanel.add(ratingLabel);
        infoPanel.add(distanceLabel);

        JButton selectBtn = createStyledButton("Select");
        selectBtn.setPreferredSize(new Dimension(100,35));
        selectBtn.addActionListener(e -> {
            playClickSound();
            selectedTheatre = name;
            showShows();
        });

        theatreCard.add(infoPanel, BorderLayout.WEST);
        theatreCard.add(selectBtn, BorderLayout.EAST);

        container.add(theatreCard);
        container.add(Box.createVerticalStrut(15));
    }

    // ===================== SHOW TIMES =====================
    void showShows() {
        currentScreen = "shows";
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = Theme.getBackgroundGradient();
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setOpaque(false);

        JPanel topBar = createTopBar();
        panel.add(topBar, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);

        JLabel title = new JLabel("⏰ Select Show Time", SwingConstants.CENTER);
        title.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 28));
        title.setForeground(Theme.getTextColor());
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        centerPanel.add(title, BorderLayout.NORTH);

        JPanel showsGrid = new JPanel(new GridLayout(2,2,20,20));
        showsGrid.setBackground(Theme.getBackgroundColor());
        showsGrid.setBorder(BorderFactory.createEmptyBorder(20,40,20,40));

        try (Connection conn = DBConnection.getConnection()) {
            String sql = "SELECT s.id, s.show_time, s.show_type, s.price FROM shows s " +
                         "JOIN movies m ON s.movie_id = m.id " +
                         "JOIN theatres t ON s.theatre_id = t.id " +
                         "WHERE m.title = ? AND t.name = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, selectedMovie);
            pstmt.setString(2, selectedTheatre);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                int showId = rs.getInt("id");
                String time = rs.getString("show_time");
                String type = rs.getString("show_type");
                int price = rs.getInt("price");

                JPanel showCard = new JPanel();
                showCard.setLayout(new BoxLayout(showCard, BoxLayout.Y_AXIS));
                showCard.setBackground(Theme.getCardColor());
                showCard.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.getBorderColor(),1), BorderFactory.createEmptyBorder(15,15,15,15)));

                JLabel timeLabel = new JLabel(time);
                timeLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 20));
                timeLabel.setForeground(Theme.getTextColor());
                timeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

                JLabel typeLabel = new JLabel(type);
                typeLabel.setForeground(Theme.getSecondaryTextColor());
                typeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

                JLabel priceLabel = new JLabel("₹" + price);
                priceLabel.setForeground(new Color(76,175,80));
                priceLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
                priceLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

                JButton bookBtn = createStyledButton("Book");
                bookBtn.setMaximumSize(new Dimension(100,35));
                bookBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
                bookBtn.addActionListener(e -> {
                    playClickSound();
                    selectedShow = time;
                    selectedShowId = showId;
                    showSeats();
                });

                showCard.add(timeLabel);
                showCard.add(Box.createVerticalStrut(5));
                showCard.add(typeLabel);
                showCard.add(Box.createVerticalStrut(5));
                showCard.add(priceLabel);
                showCard.add(Box.createVerticalStrut(10));
                showCard.add(bookBtn);

                showsGrid.add(showCard);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading shows: " + ex.getMessage());
            return;
        }

        centerPanel.add(showsGrid, BorderLayout.CENTER);

        JButton backBtn = createBackButton(() -> showTheatres());
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setOpaque(false);
        bottomPanel.add(backBtn);

        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        mainPanel.add(panel, "shows");
        cardLayout.show(mainPanel, "shows");
    }

    // ===================== SEAT SELECTION =====================
    void showSeats() {
        currentScreen = "seats";
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = Theme.getBackgroundGradient();
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setOpaque(false);

        JPanel topBar = createTopBar();
        panel.add(topBar, BorderLayout.NORTH);

        JPanel centerContainer = new JPanel();
        centerContainer.setLayout(new BoxLayout(centerContainer, BoxLayout.Y_AXIS));
        centerContainer.setBackground(Theme.getBackgroundColor());
        centerContainer.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        JLabel title = new JLabel("🎭 Select Your Seats", SwingConstants.CENTER);
        title.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 28));
        title.setForeground(Theme.getTextColor());
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerContainer.add(title);
        centerContainer.add(Box.createVerticalStrut(20));

        JPanel screenPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0,0,new Color(60,60,60),getWidth(),0,new Color(100,100,100));
                g2d.setPaint(gp);
                g2d.fillRoundRect(0,0,getWidth(),getHeight(),20,20);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 16));
                String text = "🎬 SCREEN 🎬";
                FontMetrics fm = g2d.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(text))/2;
                int y = (getHeight() - fm.getHeight())/2 + fm.getAscent();
                g2d.drawString(text, x, y);
            }
        };
        screenPanel.setPreferredSize(new Dimension(600,60));
        screenPanel.setMaximumSize(new Dimension(800,60));
        screenPanel.setOpaque(false);
        screenPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerContainer.add(screenPanel);
        centerContainer.add(Box.createVerticalStrut(30));

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER,20,0));
        legend.setBackground(Theme.getBackgroundColor());
        legend.add(createLegendItem("💰 Premium (A)", new Color(255,215,0), 350));
        legend.add(createLegendItem("⭐ Prime (B)", new Color(255,140,0), 300));
        legend.add(createLegendItem("🎫 Classic (C-D)", new Color(70,130,200), 250));
        legend.add(createLegendItem("💺 Economy (E)", new Color(60,179,113), 200));
        legend.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerContainer.add(legend);
        centerContainer.add(Box.createVerticalStrut(20));

        JPanel seatPanel = new JPanel(new GridBagLayout());
        seatPanel.setBackground(Theme.getBackgroundColor());
        seatPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);

        selectedSeats.clear();

        Set<String> bookedSeats = new HashSet<>();
        java.util.List<String> allSeats = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            String sql = "SELECT seat_number, is_booked FROM seats WHERE show_id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, selectedShowId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String seatNum = rs.getString("seat_number");
                allSeats.add(seatNum);
                if (rs.getBoolean("is_booked")) bookedSeats.add(seatNum);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading seats: "+ex.getMessage());
            return;
        }

        Collections.sort(allSeats, new Comparator<String>() {
            public int compare(String a, String b) {
                int rowCompare = a.charAt(0)-b.charAt(0);
                if (rowCompare!=0) return rowCompare;
                int numA = Integer.parseInt(a.substring(1));
                int numB = Integer.parseInt(b.substring(1));
                return Integer.compare(numA, numB);
            }
        });

        Map<Character,Color> rowColors = new HashMap<>();
        rowColors.put('A', new Color(255,215,0));
        rowColors.put('B', new Color(255,140,0));
        rowColors.put('C', new Color(70,130,200));
        rowColors.put('D', new Color(70,130,200));
        rowColors.put('E', new Color(60,179,113));

        Map<Character,Integer> rowPrices = new HashMap<>();
        rowPrices.put('A', 350);
        rowPrices.put('B', 300);
        rowPrices.put('C', 250);
        rowPrices.put('D', 250);
        rowPrices.put('E', 200);

        char currentRow = ' ';
        JPanel rowPanel = null;
        int rowIndex = 0;
        for (String seat : allSeats) {
            char rowChar = seat.charAt(0);
            if (rowChar != currentRow) {
                if (rowPanel != null) {
                    gbc.gridx = 0; gbc.gridy = rowIndex++;
                    seatPanel.add(rowPanel, gbc);
                }
                currentRow = rowChar;
                rowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER,5,0));
                rowPanel.setBackground(Theme.getBackgroundColor());
                JLabel rowLabel = new JLabel(String.valueOf(currentRow));
                rowLabel.setForeground(Theme.getTextColor());
                rowLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
                rowPanel.add(rowLabel);
                rowPanel.add(Box.createHorizontalStrut(5));
            }
            JButton seatBtn = new JButton(seat);
            seatBtn.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,12));
            seatBtn.setPreferredSize(new Dimension(60,50));
            seatBtn.setFocusPainted(false);
            seatBtn.setBorder(BorderFactory.createLineBorder(Theme.getBorderColor(),1));
            seatBtn.setOpaque(true);

            Color defaultColor = rowColors.get(rowChar);
            if (bookedSeats.contains(seat)) {
                seatBtn.setEnabled(false);
                seatBtn.setBackground(Color.GRAY);
                seatBtn.setText("❌");
            } else if (selectedSeats.contains(seat)) {
                seatBtn.setBackground(Theme.getButtonColor());
                seatBtn.setText("✓");
            } else {
                seatBtn.setBackground(defaultColor);
                seatBtn.setText("💺");
            }

            seatBtn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    if (seatBtn.isEnabled() && !selectedSeats.contains(seat)) seatBtn.setBackground(defaultColor.brighter());
                }
                public void mouseExited(MouseEvent e) {
                    if (seatBtn.isEnabled() && !selectedSeats.contains(seat)) seatBtn.setBackground(defaultColor);
                }
            });

            seatBtn.addActionListener(e -> {
                playClickSound();
                if (selectedSeats.contains(seat)) {
                    selectedSeats.remove(seat);
                    seatBtn.setBackground(defaultColor);
                    seatBtn.setText("💺");
                } else {
                    selectedSeats.add(seat);
                    seatBtn.setBackground(Theme.getButtonColor());
                    seatBtn.setText("✓");
                }
                updateSeatSummary(centerContainer);
            });
            rowPanel.add(seatBtn);
        }
        if (rowPanel != null) {
            gbc.gridx = 0; gbc.gridy = rowIndex;
            seatPanel.add(rowPanel, gbc);
        }

        centerContainer.add(seatPanel);
        centerContainer.add(Box.createVerticalStrut(30));

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBackground(Theme.getBackgroundColor());
        bottomPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel selectedInfo = new JLabel("Selected Seats: None");
        selectedInfo.setForeground(Theme.getTextColor());
        selectedInfo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel totalLabel = new JLabel("Total: ₹0");
        totalLabel.setForeground(new Color(76,175,80));
        totalLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 18));
        totalLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton proceedBtn = createStyledButton("Continue to Food & Beverages");
        proceedBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        proceedBtn.addActionListener(e -> {
            playClickSound();
            if (selectedSeats.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select at least one seat!", "No Seats Selected", JOptionPane.WARNING_MESSAGE);
            } else {
                showFoodScreen();
            }
        });

        JButton backBtn = createBackButton(() -> showShows());
        backBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        bottomPanel.add(selectedInfo);
        bottomPanel.add(Box.createVerticalStrut(5));
        bottomPanel.add(totalLabel);
        bottomPanel.add(Box.createVerticalStrut(10));
        bottomPanel.add(proceedBtn);
        bottomPanel.add(Box.createVerticalStrut(10));
        bottomPanel.add(backBtn);

        centerContainer.add(bottomPanel);
        panel.add(centerContainer, BorderLayout.CENTER);

        mainPanel.add(panel, "seats");
        cardLayout.show(mainPanel, "seats");
    }

    private JPanel createLegendItem(String label, Color color, int price) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));
        panel.setBackground(Theme.getBackgroundColor());
        JLabel colorBox = new JLabel(" ");
        colorBox.setBackground(color);
        colorBox.setOpaque(true);
        colorBox.setBorder(BorderFactory.createLineBorder(Theme.getBorderColor(),1));
        colorBox.setPreferredSize(new Dimension(20,20));
        JLabel text = new JLabel(label + " ₹" + price);
        text.setForeground(Theme.getTextColor());
        panel.add(colorBox);
        panel.add(text);
        return panel;
    }

    private void updateSeatSummary(JPanel centerContainer) {
        Component[] comps = centerContainer.getComponents();
        for (Component c : comps) {
            if (c instanceof JPanel) {
                JPanel bottom = (JPanel) c;
                Component[] bottomComps = bottom.getComponents();
                if (bottomComps.length >= 2 && bottomComps[0] instanceof JLabel && bottomComps[1] instanceof JLabel) {
                    ((JLabel)bottomComps[0]).setText("Selected Seats: " + (selectedSeats.isEmpty() ? "None" : String.join(", ", selectedSeats)));
                    int seatTotal = calculateTotalPrice();
                    ((JLabel)bottomComps[1]).setText("Total: ₹" + seatTotal);
                    break;
                }
            }
        }
    }

    private int calculateTotalPrice() {
        int total = 0;
        Map<Character,Integer> rowPrices = new HashMap<>();
        rowPrices.put('A',350);
        rowPrices.put('B',300);
        rowPrices.put('C',250);
        rowPrices.put('D',250);
        rowPrices.put('E',200);
        for (String seat : selectedSeats) {
            char row = seat.charAt(0);
            total += rowPrices.getOrDefault(row,200);
        }
        return total;
    }

    // ===================== FOOD SCREEN (Fixed Layout) =====================
    private void showFoodScreen() {
        currentScreen = "food";
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = Theme.getFoodGradient();
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setOpaque(false);

        JPanel topBar = createTopBar();
        panel.add(topBar, BorderLayout.NORTH);

        JLabel title = new JLabel("🍿 Food & Beverages", SwingConstants.CENTER);
        title.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 32));
        title.setForeground(Color.WHITE);
        title.setBorder(BorderFactory.createEmptyBorder(20,0,20,0));
        panel.add(title, BorderLayout.NORTH);

        // Use a BoxLayout vertical list with consistent spacing
        JPanel itemsPanel = new JPanel();
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        itemsPanel.setBackground(new Color(0,0,0,0));
        itemsPanel.setBorder(BorderFactory.createEmptyBorder(20, 150, 20, 150));

        foodCheckboxes.clear();
        for (Map.Entry<String,Integer> entry : foodItemsMap.entrySet()) {
            JCheckBox cb = new JCheckBox(entry.getKey() + " - ₹" + entry.getValue());
            cb.setForeground(Color.WHITE);
            cb.setBackground(new Color(0,0,0,0));
            cb.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 20));
            cb.setIconTextGap(15);
            cb.setAlignmentX(Component.CENTER_ALIGNMENT);
            // Give it a fixed size to prevent shifting
            cb.setMaximumSize(new Dimension(500, 45));
            cb.setPreferredSize(new Dimension(500, 45));
            itemsPanel.add(cb);
            itemsPanel.add(Box.createVerticalStrut(15));
            foodCheckboxes.add(cb);
        }

        JScrollPane scrollPane = new JScrollPane(itemsPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBackground(new Color(0,0,0,0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        JLabel foodTotalLabel = new JLabel("Food Total: ₹0");
        foodTotalLabel.setForeground(new Color(255,215,0));
        foodTotalLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 24));
        foodTotalLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER,20,10));
        buttonPanel.setBackground(new Color(0,0,0,0));
        JButton orderBtn = createStyledButton("Add to Order & Proceed to Payment");
        orderBtn.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 16));
        orderBtn.setPreferredSize(new Dimension(350,50));
        JButton skipBtn = createStyledButton("Skip & Proceed to Payment");
        skipBtn.setBackground(new Color(85,85,85));
        skipBtn.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 16));
        skipBtn.setPreferredSize(new Dimension(350,50));

        orderBtn.addActionListener(e -> {
            playClickSound();
            foodOrder.clear();
            foodTotal = 0;
            for (int i=0; i<foodCheckboxes.size(); i++) {
                if (foodCheckboxes.get(i).isSelected()) {
                    String item = (String) foodItemsMap.keySet().toArray()[i];
                    int price = foodItemsMap.get(item);
                    foodOrder.add(item);
                    foodTotal += price;
                }
            }
            int seatTotal = calculateTotalPrice();
            int grandTotal = seatTotal + foodTotal;
            showPayment(grandTotal);
        });

        skipBtn.addActionListener(e -> {
            playClickSound();
            foodOrder.clear();
            foodTotal = 0;
            int seatTotal = calculateTotalPrice();
            showPayment(seatTotal);
        });

        buttonPanel.add(orderBtn);
        buttonPanel.add(skipBtn);

        bottomPanel.add(foodTotalLabel);
        bottomPanel.add(Box.createVerticalStrut(15));
        bottomPanel.add(buttonPanel);

        // Update total when checkboxes change
        for (JCheckBox cb : foodCheckboxes) {
            cb.addActionListener(e -> {
                int total = 0;
                for (int i=0; i<foodCheckboxes.size(); i++) {
                    if (foodCheckboxes.get(i).isSelected()) {
                        total += (int) foodItemsMap.values().toArray()[i];
                    }
                }
                foodTotalLabel.setText("Food Total: ₹" + total);
            });
        }

        panel.add(bottomPanel, BorderLayout.SOUTH);

        mainPanel.add(panel, "food");
        cardLayout.show(mainPanel, "food");
    }

    // ===================== PAYMENT =====================
    void showPayment(int total) {
        currentScreen = "payment";
        int actualTotal = total;
        discountedTotal = actualTotal;
        pointsToUse = 0;

        JPanel panel = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = Theme.getBackgroundGradient();
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setOpaque(false);

        JPanel topBar = createTopBar();
        panel.add(topBar, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Theme.getBackgroundColor());
        content.setBorder(BorderFactory.createEmptyBorder(20,40,20,40));

        // Order summary
        JPanel summaryBox = new JPanel();
        summaryBox.setLayout(new BoxLayout(summaryBox, BoxLayout.Y_AXIS));
        summaryBox.setBackground(Theme.getCardColor());
        summaryBox.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.getBorderColor(),1), BorderFactory.createEmptyBorder(20,20,20,20)));
        summaryBox.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel movieInfo = new JLabel("🎬 " + selectedMovie);
        movieInfo.setForeground(Theme.getTextColor());
        movieInfo.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,16));
        JLabel theatreInfo = new JLabel("🏢 " + selectedTheatre);
        theatreInfo.setForeground(Theme.getSecondaryTextColor());
        JLabel showInfo = new JLabel("⏰ " + selectedShow);
        showInfo.setForeground(Theme.getSecondaryTextColor());
        JLabel seatsInfo = new JLabel("💺 Seats: " + String.join(", ", selectedSeats));
        seatsInfo.setForeground(new Color(255,215,0));
        JLabel foodInfo = new JLabel("🍿 Food: " + (foodOrder.isEmpty() ? "None" : String.join(", ", foodOrder)));
        foodInfo.setForeground(Theme.getSecondaryTextColor());
        JLabel totalInfo = new JLabel("💰 Total: ₹" + actualTotal);
        totalInfo.setForeground(new Color(76,175,80));
        totalInfo.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,20));

        summaryBox.add(movieInfo);
        summaryBox.add(Box.createVerticalStrut(5));
        summaryBox.add(theatreInfo);
        summaryBox.add(Box.createVerticalStrut(5));
        summaryBox.add(showInfo);
        summaryBox.add(Box.createVerticalStrut(5));
        summaryBox.add(seatsInfo);
        summaryBox.add(Box.createVerticalStrut(5));
        summaryBox.add(foodInfo);
        summaryBox.add(Box.createVerticalStrut(10));
        summaryBox.add(totalInfo);

        // Points redemption
        if (!currentUser.equals("Guest") && loyaltyPoints > 0) {
            JPanel pointsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER,10,10));
            pointsPanel.setBackground(Theme.getBackgroundColor());
            pointsPanel.add(new JLabel("Available points: " + loyaltyPoints));
            pointsPanel.add(new JLabel("Use points (1 point = ₹1, max 50% of total):"));
            SpinnerNumberModel spinnerModel = new SpinnerNumberModel(0, 0, Math.min(loyaltyPoints, actualTotal/2), 50);
            pointsSpinner = new JSpinner(spinnerModel);
            pointsSpinner.setPreferredSize(new Dimension(100,30));
            pointsPanel.add(pointsSpinner);
            JButton applyPointsBtn = new JButton("Apply");
            applyPointsBtn.addActionListener(e -> {
                int points = (Integer) pointsSpinner.getValue();
                if (points > actualTotal/2) {
                    JOptionPane.showMessageDialog(this, "You can only redeem up to 50% of the total.");
                    return;
                }
                pointsToUse = points;
                discountedTotal = actualTotal - points;
                totalInfo.setText("💰 Total: ₹" + discountedTotal);
            });
            pointsPanel.add(applyPointsBtn);
            content.add(pointsPanel);
            content.add(Box.createVerticalStrut(15));
        }

        // Payment method selection
        JPanel paymentMethodPanel = new JPanel();
        paymentMethodPanel.setLayout(new BoxLayout(paymentMethodPanel, BoxLayout.Y_AXIS));
        paymentMethodPanel.setBackground(Theme.getBackgroundColor());
        paymentMethodPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel methodLabel = new JLabel("Select Payment Method:");
        methodLabel.setForeground(Theme.getTextColor());
        methodLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,16));
        methodLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        ButtonGroup group = new ButtonGroup();
        JRadioButton upiRadio = new JRadioButton("UPI");
        JRadioButton creditRadio = new JRadioButton("Credit Card");
        JRadioButton debitRadio = new JRadioButton("Debit Card");
        JRadioButton netRadio = new JRadioButton("Net Banking");
        upiRadio.setForeground(Theme.getTextColor());
        creditRadio.setForeground(Theme.getTextColor());
        debitRadio.setForeground(Theme.getTextColor());
        netRadio.setForeground(Theme.getTextColor());
        upiRadio.setBackground(Theme.getBackgroundColor());
        creditRadio.setBackground(Theme.getBackgroundColor());
        debitRadio.setBackground(Theme.getBackgroundColor());
        netRadio.setBackground(Theme.getBackgroundColor());
        group.add(upiRadio);
        group.add(creditRadio);
        group.add(debitRadio);
        group.add(netRadio);
        upiRadio.setSelected(true);

        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.CENTER,15,0));
        radioPanel.setBackground(Theme.getBackgroundColor());
        radioPanel.add(upiRadio);
        radioPanel.add(creditRadio);
        radioPanel.add(debitRadio);
        radioPanel.add(netRadio);

        paymentMethodPanel.add(methodLabel);
        paymentMethodPanel.add(Box.createVerticalStrut(10));
        paymentMethodPanel.add(radioPanel);
        paymentMethodPanel.add(Box.createVerticalStrut(15));

        // Dynamic input panel
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));
        inputPanel.setBackground(Theme.getBackgroundColor());
        inputPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JTextField upiField = new JTextField("example@okhdfcbank");
        styleTextField(upiField);
        upiField.setMaximumSize(new Dimension(300,40));
        inputPanel.add(upiField);
        JPanel dummyPanel = new JPanel();
        dummyPanel.setBackground(Theme.getBackgroundColor());

        ActionListener radioListener = e -> {
            inputPanel.removeAll();
            if (upiRadio.isSelected()) inputPanel.add(upiField);
            else if (creditRadio.isSelected() || debitRadio.isSelected()) {
                JTextField cardNum = new JTextField("1234 5678 9012 3456");
                styleTextField(cardNum);
                cardNum.setMaximumSize(new Dimension(300,40));
                inputPanel.add(cardNum);
                inputPanel.add(Box.createVerticalStrut(10));
                JPanel expiryCvv = new JPanel(new FlowLayout(FlowLayout.CENTER,10,0));
                JTextField expiry = new JTextField("MM/YY");
                JTextField cvv = new JTextField("123");
                styleTextField(expiry); expiry.setMaximumSize(new Dimension(100,40));
                styleTextField(cvv); cvv.setMaximumSize(new Dimension(80,40));
                expiryCvv.add(expiry);
                expiryCvv.add(cvv);
                inputPanel.add(expiryCvv);
            } else if (netRadio.isSelected()) {
                JComboBox<String> bankCombo = new JComboBox<>(new String[]{"Select Bank","SBI","HDFC","ICICI"});
                bankCombo.setBackground(Theme.getCardColor());
                bankCombo.setForeground(Theme.getTextColor());
                bankCombo.setMaximumSize(new Dimension(300,40));
                inputPanel.add(bankCombo);
            }
            inputPanel.revalidate();
            inputPanel.repaint();
        };
        upiRadio.addActionListener(radioListener);
        creditRadio.addActionListener(radioListener);
        debitRadio.addActionListener(radioListener);
        netRadio.addActionListener(radioListener);
        radioListener.actionPerformed(null); // initial

        JLabel paymentStatus = new JLabel();
        paymentStatus.setForeground(new Color(255,215,0));
        paymentStatus.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton payBtn = createStyledButton("Pay ₹" + discountedTotal);
        payBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        payBtn.addActionListener(e -> {
            playClickSound();
            boolean valid = false;
            String method = "";
            if (upiRadio.isSelected()) {
                method = "UPI";
                String upiId = upiField.getText().trim();
                if (upiId.isEmpty() || !upiId.contains("@")) {
                    paymentStatus.setText("❌ Please enter a valid UPI ID");
                    return;
                }
                valid = true;
            } else if (creditRadio.isSelected() || debitRadio.isSelected()) {
                method = creditRadio.isSelected() ? "Credit Card" : "Debit Card";
                valid = true;
            } else if (netRadio.isSelected()) {
                method = "Net Banking";
                valid = true;
            }
            if (valid) {
                JOptionPane.showMessageDialog(this, "💳 Payment of ₹" + discountedTotal + " via " + method + " initiated.\nProcessing... (Demo)", "Payment", JOptionPane.INFORMATION_MESSAGE);
                // Save booking
                try (Connection conn = DBConnection.getConnection()) {
                    conn.setAutoCommit(false);
                    String bookingId = "BK" + System.currentTimeMillis();
                    String sqlBooking = "INSERT INTO bookings (booking_id, user_id, show_id, seats, total_amount, payment_method, food_items, points_used) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                    PreparedStatement pstmtBooking = conn.prepareStatement(sqlBooking);
                    pstmtBooking.setString(1, bookingId);
                    int userId = currentUser.equals("Guest") ? -1 : getUserId(currentUser);
                    if (userId == -1) pstmtBooking.setNull(2, Types.INTEGER);
                    else pstmtBooking.setInt(2, userId);
                    pstmtBooking.setInt(3, selectedShowId);
                    pstmtBooking.setString(4, String.join(", ", selectedSeats));
                    pstmtBooking.setInt(5, discountedTotal);
                    pstmtBooking.setString(6, method);
                    pstmtBooking.setString(7, foodOrder.isEmpty() ? null : String.join(", ", foodOrder));
                    pstmtBooking.setInt(8, pointsToUse);
                    pstmtBooking.executeUpdate();

                    String sqlUpdate = "UPDATE seats SET is_booked = true WHERE show_id = ? AND seat_number = ?";
                    PreparedStatement pstmtSeat = conn.prepareStatement(sqlUpdate);
                    for (String seat : selectedSeats) {
                        pstmtSeat.setInt(1, selectedShowId);
                        pstmtSeat.setString(2, seat);
                        pstmtSeat.executeUpdate();
                    }
                    conn.commit();

                    // Update loyalty points (earned on original total)
                    if (!currentUser.equals("Guest")) {
                        updateUserLoyalty(actualTotal);
                        if (pointsToUse > 0) deductLoyaltyPoints(pointsToUse);
                    }
                    paymentStatus.setText("✅ Payment Successful! Booking Confirmed!");
                    paymentStatus.setForeground(new Color(76,175,80));
                    payBtn.setEnabled(false);

                    sendBookingConfirmationEmail(bookingId, discountedTotal);
                    showConfirmation(bookingId, discountedTotal);
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Booking failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton backBtn = createBackButton(() -> showFoodScreen());
        backBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(summaryBox);
        content.add(Box.createVerticalStrut(25));
        content.add(paymentMethodPanel);
        content.add(Box.createVerticalStrut(10));
        content.add(inputPanel);
        content.add(Box.createVerticalStrut(15));
        content.add(paymentStatus);
        content.add(Box.createVerticalStrut(10));
        content.add(payBtn);
        content.add(Box.createVerticalStrut(10));
        content.add(backBtn);

        JLabel title = new JLabel("💳 Payment Details", SwingConstants.CENTER);
        title.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,28));
        title.setForeground(Theme.getTextColor());

        panel.add(title, BorderLayout.CENTER);
        panel.add(content, BorderLayout.SOUTH);

        mainPanel.add(panel, "payment");
        cardLayout.show(mainPanel, "payment");
    }

    // REAL EMAIL SENDING FOR BOOKING CONFIRMATION
    private void sendBookingConfirmationEmail(String bookingId, int total) {
        String userEmail = "customer@example.com";
        if (!currentUser.equals("Guest")) {
            try (Connection conn = DBConnection.getConnection()) {
                int userId = getUserId(currentUser);
                PreparedStatement pstmt = conn.prepareStatement("SELECT email FROM users WHERE id = ?");
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) userEmail = rs.getString("email");
            } catch (SQLException e) { e.printStackTrace(); }
        }

        String subject = "🎬 MovieTicket Booking Confirmation";
        String body = "Dear " + currentUser + ",\n\n" +
                      "Your booking has been confirmed!\n" +
                      "Booking ID: " + bookingId + "\n" +
                      "Movie: " + selectedMovie + "\n" +
                      "Theatre: " + selectedTheatre + "\n" +
                      "Show: " + selectedShow + "\n" +
                      "Seats: " + String.join(", ", selectedSeats) + "\n" +
                      "Food: " + (foodOrder.isEmpty() ? "None" : String.join(", ", foodOrder)) + "\n" +
                      "Total Amount: ₹" + total + "\n\n" +
                      "Thank you for booking with MovieTicket!\n" +
                      "Enjoy your show! 🎬🍿";

        sendEmail(userEmail, subject, body);
        JOptionPane.showMessageDialog(this, "📧 Booking confirmation email sent to " + userEmail, "Email Sent", JOptionPane.INFORMATION_MESSAGE);
    }

    // ===================== CONFIRMATION with PDF Generation =====================
    void showConfirmation(String bookingId, int total) {
        currentScreen = "confirmation";
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.getBackgroundColor());

        JPanel topBar = createTopBar();
        panel.add(topBar, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Theme.getCardColor());
        content.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.getBorderColor(),1), BorderFactory.createEmptyBorder(30,30,30,30)));

        JLabel successIcon = new JLabel("🎉");
        successIcon.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 64));
        successIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel confirmedLabel = new JLabel("BOOKING CONFIRMED!");
        confirmedLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,24));
        confirmedLabel.setForeground(new Color(76,175,80));
        confirmedLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel bookingIdLabel = new JLabel("Booking ID: " + bookingId);
        bookingIdLabel.setForeground(new Color(255,215,0));
        bookingIdLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel seatsLabel = new JLabel("Seats: " + String.join(", ", selectedSeats));
        seatsLabel.setForeground(Theme.getTextColor());
        seatsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel foodLabel = new JLabel("Food: " + (foodOrder.isEmpty() ? "None" : String.join(", ", foodOrder)));
        foodLabel.setForeground(Theme.getSecondaryTextColor());
        foodLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel totalLabel = new JLabel("Total Paid: ₹" + total);
        totalLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,18));
        totalLabel.setForeground(new Color(76,175,80));
        totalLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // QR code simulation
        JPanel qrPanel = new JPanel(new BorderLayout());
        qrPanel.setBackground(Theme.getCardColor());
        qrPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Theme.getBorderColor()), "QR Code Ticket"));
        JTextArea qrText = new JTextArea(3,20);
        qrText.setText("📱 Scan this code at the theatre:\n" + bookingId);
        qrText.setEditable(false);
        qrText.setBackground(Theme.getCardColor());
        qrText.setForeground(Theme.getTextColor());
        qrText.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 14));
        qrPanel.add(qrText, BorderLayout.CENTER);
        qrPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        qrPanel.setMaximumSize(new Dimension(300,80));

        // PDF generation (using file chooser)
        JButton pdfBtn = createStyledButton("📄 Download PDF Ticket");
        pdfBtn.setPreferredSize(new Dimension(200,40));
        pdfBtn.addActionListener(e -> {
            try {
                generatePDFReceipt(bookingId, total);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error generating PDF: " + ex.getMessage(), "PDF Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        pdfBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton homeBtn = createStyledButton("🏠 Home");
        homeBtn.addActionListener(e -> { playClickSound(); showMovies(); });
        JButton historyBtn = createStyledButton("📜 Booking History");
        historyBtn.addActionListener(e -> { playClickSound(); showBookingHistory(); });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER,20,0));
        buttonPanel.setBackground(Theme.getCardColor());
        buttonPanel.add(homeBtn);
        buttonPanel.add(historyBtn);

        content.add(successIcon);
        content.add(Box.createVerticalStrut(20));
        content.add(confirmedLabel);
        content.add(Box.createVerticalStrut(15));
        content.add(bookingIdLabel);
        content.add(Box.createVerticalStrut(10));
        content.add(seatsLabel);
        content.add(Box.createVerticalStrut(5));
        content.add(foodLabel);
        content.add(Box.createVerticalStrut(10));
        content.add(totalLabel);
        content.add(Box.createVerticalStrut(20));
        content.add(qrPanel);
        content.add(Box.createVerticalStrut(10));
        content.add(pdfBtn);
        content.add(Box.createVerticalStrut(20));
        content.add(buttonPanel);

        panel.add(content, BorderLayout.CENTER);

        mainPanel.add(panel, "confirmation");
        cardLayout.show(mainPanel, "confirmation");
    }

    // PDF Generation using iText (with file chooser)
    private void generatePDFReceipt(String bookingId, int total) throws Exception {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save PDF Ticket");
        fileChooser.setSelectedFile(new File("MovieTicket_" + bookingId + ".pdf"));
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            String fileName = fileToSave.getAbsolutePath();
            if (!fileName.toLowerCase().endsWith(".pdf")) {
                fileName += ".pdf";
            }

            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(fileName));
            document.open();

            // Use fully qualified iText Font to avoid ambiguity
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 24, Font.BOLD, BaseColor.BLACK);
            Font normalFont = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL, BaseColor.BLACK);
            Font boldFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, BaseColor.BLACK);

            Paragraph title = new Paragraph("MOVIETICKET - Booking Confirmation", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Booking ID: " + bookingId, boldFont));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("User: " + currentUser, normalFont));
            document.add(new Paragraph("Movie: " + selectedMovie, normalFont));
            document.add(new Paragraph("Theatre: " + selectedTheatre, normalFont));
            document.add(new Paragraph("Show: " + selectedShow, normalFont));
            document.add(new Paragraph("Seats: " + String.join(", ", selectedSeats), normalFont));
            document.add(new Paragraph("Food: " + (foodOrder.isEmpty() ? "None" : String.join(", ", foodOrder)), normalFont));
            document.add(new Paragraph("Total Paid: ₹" + total, boldFont));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Date: " + new java.util.Date(), normalFont));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Thank you for booking with MovieTicket! 🎬", normalFont));

            document.close();
            JOptionPane.showMessageDialog(this, "PDF ticket saved to:\n" + fileName, "PDF Generated", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ===================== BOOKING HISTORY =====================
    void showBookingHistory() {
        currentScreen = "history";
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.getBackgroundColor());

        JPanel topBar = createTopBar();
        panel.add(topBar, BorderLayout.NORTH);

        JPanel historyPanel = new JPanel();
        historyPanel.setLayout(new BoxLayout(historyPanel, BoxLayout.Y_AXIS));
        historyPanel.setBackground(Theme.getBackgroundColor());
        historyPanel.setBorder(BorderFactory.createEmptyBorder(20,40,20,40));

        java.util.List<Booking> bookings = new ArrayList<>();
        if (!currentUser.equals("Guest")) {
            try (Connection conn = DBConnection.getConnection()) {
                int userId = getUserId(currentUser);
                String sql = "SELECT booking_id, seats, total_amount, booking_date, payment_method, status, show_id, food_items, points_used FROM bookings WHERE user_id = ? ORDER BY booking_date DESC";
                PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    int showId = rs.getInt("show_id");
                    String[] showDetails = getShowDetails(showId);
                    if (showDetails != null) {
                        bookings.add(new Booking(
                            rs.getString("booking_id"),
                            showDetails[0],
                            showDetails[1],
                            showDetails[2],
                            rs.getString("seats"),
                            rs.getInt("total_amount"),
                            rs.getTimestamp("booking_date"),
                            rs.getString("payment_method"),
                            rs.getString("status"),
                            rs.getString("food_items"),
                            rs.getInt("points_used")
                        ));
                    }
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error loading history: " + ex.getMessage());
            }
        } else {
            JLabel guestMsg = new JLabel("Guest users cannot view booking history.");
            guestMsg.setForeground(Theme.getTextColor());
            guestMsg.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN,16));
            guestMsg.setAlignmentX(Component.CENTER_ALIGNMENT);
            historyPanel.add(guestMsg);
        }

        if (bookings.isEmpty() && !currentUser.equals("Guest")) {
            JLabel noBookings = new JLabel("No bookings found. Book your first show! 🎬");
            noBookings.setForeground(Theme.getTextColor());
            noBookings.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN,16));
            noBookings.setAlignmentX(Component.CENTER_ALIGNMENT);
            historyPanel.add(noBookings);
        } else {
            for (Booking b : bookings) {
                JPanel bookingCard = new JPanel();
                bookingCard.setLayout(new BoxLayout(bookingCard, BoxLayout.Y_AXIS));
                bookingCard.setBackground(Theme.getCardColor());
                bookingCard.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.getBorderColor(),1), BorderFactory.createEmptyBorder(15,15,15,15)));
                bookingCard.setMaximumSize(new Dimension(600,300));

                JLabel id = new JLabel("ID: " + b.bookingId);
                id.setForeground(new Color(255,215,0));
                id.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,12));

                JLabel movie = new JLabel("🎬 " + b.movie);
                movie.setForeground(Theme.getTextColor());
                movie.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,14));

                JLabel theatre = new JLabel("🏢 " + b.theatre);
                theatre.setForeground(Theme.getSecondaryTextColor());

                JLabel show = new JLabel("⏰ " + b.showTime);
                show.setForeground(Theme.getSecondaryTextColor());

                JLabel seats = new JLabel("💺 " + b.seats);
                seats.setForeground(new Color(255,215,0));

                JLabel food = new JLabel("🍿 " + (b.foodItems == null ? "None" : b.foodItems));
                food.setForeground(Theme.getSecondaryTextColor());

                JLabel points = new JLabel("🏷️ Points used: " + b.pointsUsed);
                points.setForeground(Theme.getSecondaryTextColor());

                JLabel total = new JLabel("💰 ₹" + b.amount);
                total.setForeground(new Color(76,175,80));

                bookingCard.add(id);
                bookingCard.add(Box.createVerticalStrut(5));
                bookingCard.add(movie);
                bookingCard.add(theatre);
                bookingCard.add(show);
                bookingCard.add(seats);
                bookingCard.add(food);
                bookingCard.add(points);
                bookingCard.add(total);

                historyPanel.add(bookingCard);
                historyPanel.add(Box.createVerticalStrut(15));
            }
        }

        JScrollPane scrollPane = new JScrollPane(historyPanel);
        scrollPane.setBackground(Theme.getBackgroundColor());
        scrollPane.getViewport().setBackground(Theme.getBackgroundColor());

        JLabel title = new JLabel("📜 Booking History", SwingConstants.CENTER);
        title.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,28));
        title.setForeground(Theme.getTextColor());

        JButton backBtn = createBackButton(() -> showMovies());

        panel.add(title, BorderLayout.CENTER);
        panel.add(scrollPane, BorderLayout.SOUTH);
        panel.add(backBtn, BorderLayout.PAGE_END);

        mainPanel.add(panel, "history");
        cardLayout.show(mainPanel, "history");
    }

    // ===================== USER PROFILE =====================
    private void showProfile() {
        currentScreen = "profile";
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.getBackgroundColor());

        JPanel topBar = createTopBar();
        panel.add(topBar, BorderLayout.NORTH);

        JLabel title = new JLabel("User Profile", SwingConstants.CENTER);
        title.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,28));
        title.setForeground(Theme.getTextColor());
        title.setBorder(BorderFactory.createEmptyBorder(20,0,20,0));
        panel.add(title, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Theme.getCardColor());
        formPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.getBorderColor(),1), BorderFactory.createEmptyBorder(20,20,20,20)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("Full Name:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        profileNameField = new JTextField(20); styleTextField(profileNameField); formPanel.add(profileNameField, gbc);
        gbc.gridy = 1; gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("Phone:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        profilePhoneField = new JTextField(20); styleTextField(profilePhoneField); formPanel.add(profilePhoneField, gbc);
        gbc.gridy = 2; gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        profileEmailField = new JTextField(20); styleTextField(profileEmailField); formPanel.add(profileEmailField, gbc);
        gbc.gridy = 3; gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("New Password:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        profilePasswordField = new JPasswordField(20); styleTextField(profilePasswordField); formPanel.add(profilePasswordField, gbc);
        gbc.gridy = 4; gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("Confirm Password:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        profileConfirmField = new JPasswordField(20); styleTextField(profileConfirmField); formPanel.add(profileConfirmField, gbc);
        gbc.gridy = 5; gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("Total Spent:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        profileSpentLabel = new JLabel("₹"+totalSpent); profileSpentLabel.setForeground(Theme.getTextColor()); formPanel.add(profileSpentLabel, gbc);
        gbc.gridy = 6; gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("Loyalty Points:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        profilePointsLabel = new JLabel(String.valueOf(loyaltyPoints)); profilePointsLabel.setForeground(Theme.getTextColor()); formPanel.add(profilePointsLabel, gbc);
        gbc.gridy = 7; gbc.gridx = 0; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;
        JButton updateBtn = createStyledButton("Update Profile");
        updateBtn.addActionListener(e -> updateProfile());
        formPanel.add(updateBtn, gbc);
        gbc.gridy = 8;
        JButton backBtn = createBackButton(() -> showMovies());
        formPanel.add(backBtn, gbc);

        panel.add(formPanel, BorderLayout.CENTER);
        loadProfileData();
        mainPanel.add(panel, "profile");
        cardLayout.show(mainPanel, "profile");
    }

    private void loadProfileData() {
        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement pstmt = conn.prepareStatement("SELECT full_name, phone, email FROM users WHERE username = ?");
            pstmt.setString(1, currentUser);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                profileNameField.setText(rs.getString("full_name"));
                profilePhoneField.setText(rs.getString("phone"));
                profileEmailField.setText(rs.getString("email"));
            }
            profileSpentLabel.setText("₹"+totalSpent);
            profilePointsLabel.setText(String.valueOf(loyaltyPoints));
        } catch (SQLException e) { e.printStackTrace(); JOptionPane.showMessageDialog(this, "Error loading profile: "+e.getMessage()); }
    }

    private void updateProfile() {
        String name = profileNameField.getText().trim();
        String phone = profilePhoneField.getText().trim();
        String email = profileEmailField.getText().trim();
        String pass = new String(profilePasswordField.getPassword());
        String confirm = new String(profileConfirmField.getPassword());
        if (name.isEmpty() || phone.isEmpty() || email.isEmpty()) { JOptionPane.showMessageDialog(this, "Name, phone and email are required."); return; }
        if (!pass.isEmpty() && !pass.equals(confirm)) { JOptionPane.showMessageDialog(this, "Passwords do not match."); return; }
        try (Connection conn = DBConnection.getConnection()) {
            if (!pass.isEmpty()) {
                PreparedStatement pstmt = conn.prepareStatement("UPDATE users SET full_name=?, phone=?, email=?, password=? WHERE username=?");
                pstmt.setString(1, name); pstmt.setString(2, phone); pstmt.setString(3, email); pstmt.setString(4, pass); pstmt.setString(5, currentUser);
                pstmt.executeUpdate();
            } else {
                PreparedStatement pstmt = conn.prepareStatement("UPDATE users SET full_name=?, phone=?, email=? WHERE username=?");
                pstmt.setString(1, name); pstmt.setString(2, phone); pstmt.setString(3, email); pstmt.setString(4, currentUser);
                pstmt.executeUpdate();
            }
            JOptionPane.showMessageDialog(this, "Profile updated.");
            loadProfileData();
        } catch (SQLException e) { e.printStackTrace(); JOptionPane.showMessageDialog(this, "Error updating profile: "+e.getMessage()); }
    }

    // ===================== UI COMPONENTS =====================
    JPanel createTopBar() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(30,30,30));
        topBar.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT,15,0));
        rightPanel.setBackground(new Color(30,30,30));

        JLabel userLabel = new JLabel("👤 " + currentUser);
        userLabel.setForeground(Color.WHITE);
        JLabel spendLabel = new JLabel("💰 ₹" + totalSpent);
        spendLabel.setForeground(new Color(76,175,80));
        spendLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,12));

        JButton historyBtn = new JButton("📜 History");
        historyBtn.setBackground(new Color(229,9,20));
        historyBtn.setForeground(Color.WHITE);
        historyBtn.setBorderPainted(false);
        historyBtn.addActionListener(e -> { playClickSound(); showBookingHistory(); });

        JButton profileBtn = new JButton("👤 Profile");
        profileBtn.setBackground(new Color(85,85,85));
        profileBtn.setForeground(Color.WHITE);
        profileBtn.setBorderPainted(false);
        profileBtn.addActionListener(e -> { playClickSound(); showProfile(); });
        if (currentUser.equals("Guest")) profileBtn.setEnabled(false);

        JButton themeBtn = new JButton(isDarkTheme ? "☀️ Light" : "🌙 Dark");
        themeBtn.setBackground(new Color(85,85,85));
        themeBtn.setForeground(Color.WHITE);
        themeBtn.setBorderPainted(false);
        themeBtn.addActionListener(e -> { playClickSound(); toggleTheme(); themeBtn.setText(isDarkTheme ? "☀️ Light" : "🌙 Dark"); });

        JButton logoutBtn = new JButton("🚪 Logout");
        logoutBtn.setBackground(new Color(85,85,85));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setBorderPainted(false);
        logoutBtn.addActionListener(e -> {
            playClickSound();
            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to logout?", "Logout", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) showLogin();
        });

        rightPanel.add(userLabel);
        rightPanel.add(spendLabel);
        rightPanel.add(historyBtn);
        rightPanel.add(profileBtn);
        rightPanel.add(themeBtn);
        rightPanel.add(logoutBtn);

        topBar.add(rightPanel, BorderLayout.EAST);
        return topBar;
    }

    JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(Theme.getButtonColor());
        button.setForeground(Color.WHITE);
        button.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD,14));
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(200,40));
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { button.setBackground(Theme.getButtonColor().brighter()); }
            public void mouseExited(MouseEvent e) { button.setBackground(Theme.getButtonColor()); }
        });
        return button;
    }

    JButton createBackButton(Runnable action) {
        JButton backBtn = new JButton("← Back");
        backBtn.setBackground(new Color(85,85,85));
        backBtn.setForeground(Color.WHITE);
        backBtn.setBorderPainted(false);
        backBtn.setPreferredSize(new Dimension(100,35));
        backBtn.addActionListener(e -> { playClickSound(); action.run(); });
        return backBtn;
    }

    private static class Movie {
        String title, icon, posterPath, teaserUrl;
        double rating;
        Movie(String title, String icon, double rating, String posterPath, String teaserUrl) {
            this.title = title; this.icon = icon; this.rating = rating; this.posterPath = posterPath; this.teaserUrl = teaserUrl;
        }
    }

    private static class Booking {
        String bookingId, movie, theatre, showTime, seats, paymentMethod, status, foodItems;
        int amount, pointsUsed;
        java.util.Date date;
        Booking(String id, String m, String t, String st, String seats, int amt, java.util.Date d, String pm, String stat, String food, int points) {
            this.bookingId = id; this.movie = m; this.theatre = t; this.showTime = st; this.seats = seats; this.amount = amt;
            this.date = d; this.paymentMethod = pm; this.status = stat; this.foodItems = food; this.pointsUsed = points;
        }
    }

    // ===================== MAIN =====================
    public static void main(String[] args) {
        try {
            System.out.println("Working Directory = " + System.getProperty("user.dir"));
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DBConnection.getConnection();
            System.out.println("Database connection OK! Database: " + conn.getCatalog());
            conn.close();
        } catch (Exception e) {
            System.err.println("Database connection failed: " + e.getMessage());
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> new MovieTicketApp());
    }
}