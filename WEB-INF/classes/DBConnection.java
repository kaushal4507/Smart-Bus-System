import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Singleton-style DB connection helper.
 * Configure DB_URL, DB_USER, DB_PASS for your environment.
 *
 * Schema (run once):
 *
 * CREATE DATABASE college_bus;
 * USE college_bus;
 *
 * CREATE TABLE users (
 *   id          INT AUTO_INCREMENT PRIMARY KEY,
 *   name        VARCHAR(100) NOT NULL,
 *   email       VARCHAR(100) NOT NULL UNIQUE,
 *   password    VARCHAR(255) NOT NULL,       -- bcrypt hash
 *   roll_no     VARCHAR(30)  NOT NULL UNIQUE,
 *   created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 * );
 *
 * CREATE TABLE routes (
 *   id           INT AUTO_INCREMENT PRIMARY KEY,
 *   route_name   VARCHAR(100) NOT NULL,
 *   origin       VARCHAR(100) NOT NULL,
 *   destination  VARCHAR(100) NOT NULL,
 *   departure    TIME NOT NULL,
 *   total_seats  INT NOT NULL DEFAULT 40,
 *   fare         DECIMAL(8,2) NOT NULL
 * );
 *
 * CREATE TABLE bookings (
 *   id          INT AUTO_INCREMENT PRIMARY KEY,
 *   user_id     INT NOT NULL,
 *   route_id    INT NOT NULL,
 *   seat_no     INT NOT NULL,
 *   travel_date DATE NOT NULL,
 *   status      ENUM('CONFIRMED','CANCELLED') DEFAULT 'CONFIRMED',
 *   booked_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *   UNIQUE KEY uq_seat (route_id, seat_no, travel_date),
 *   FOREIGN KEY (user_id)  REFERENCES users(id),
 *   FOREIGN KEY (route_id) REFERENCES routes(id)
 * );
 */
public class DBConnection {

    // ── Change these three values to match your Oracle setup ──────────────────
    private static final String DB_URL  = "jdbc:oracle:thin:@localhost:1521:orcl";
    private static final String DB_USER = "system";
    private static final String DB_PASS = "1234";
    // ─────────────────────────────────────────────────────────────────────────

    static {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("MySQL driver not found: " + e.getMessage());
        }
    }

    /**
     * Returns a new JDBC connection. Caller is responsible for closing it.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }
}
