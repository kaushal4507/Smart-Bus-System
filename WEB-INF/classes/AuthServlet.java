import jakarta.servlet.http.*;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;

import java.io.*;
import java.sql.*;

public class AuthServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("application/json;charset=UTF-8");
        String action = req.getPathInfo();

        if (action == null) {
            sendError(resp, 400, "Missing action path");
            return;
        }

        switch (action) {
            case "/login"  -> handleLogin(req, resp);
            case "/signup" -> handleSignup(req, resp);
            case "/logout" -> handleLogout(req, resp);
            default        -> sendError(resp, 404, "Unknown action: " + action);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("application/json;charset=UTF-8");
        String action = req.getPathInfo();

        if ("/me".equals(action)) {
            HttpSession session = req.getSession(false);
            if (session == null || session.getAttribute("userId") == null) {
                sendError(resp, 401, "Not logged in");
                return;
            }
            JSONObject out = new JSONObject();
            out.put("id",     session.getAttribute("userId"));
            out.put("name",   session.getAttribute("userName"));
            out.put("email",  session.getAttribute("userEmail"));
            out.put("rollNo", session.getAttribute("userRollNo"));
            resp.getWriter().write(out.toString());
        } else {
            sendError(resp, 404, "Unknown action");
        }
    }

    private void handleLogin(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        JSONObject body = parseBody(req);
        String email    = body.optString("email", "").trim().toLowerCase();
        String password = body.optString("password", "");

        if (email.isEmpty() || password.isEmpty()) {
            sendError(resp, 400, "Email and password are required");
            return;
        }

        String sql = "SELECT id, name, email, password, roll_no FROM users WHERE email = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(true);
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    sendError(resp, 401, "Invalid email or password");
                    return;
                }

                String hashed = rs.getString("password");
                if (!BCrypt.checkpw(password, hashed)) {
                    sendError(resp, 401, "Invalid email or password");
                    return;
                }

                HttpSession session = req.getSession(true);
                session.setAttribute("userId",     rs.getInt("id"));
                session.setAttribute("userName",   rs.getString("name"));
                session.setAttribute("userEmail",  rs.getString("email"));
                session.setAttribute("userRollNo", rs.getString("roll_no"));

                JSONObject out = new JSONObject();
                out.put("message", "Login successful");
                out.put("name",    rs.getString("name"));
                resp.getWriter().write(out.toString());
            }

        } catch (SQLException e) {
            sendError(resp, 500, "Database error: " + e.getMessage());
        }
    }

    private void handleSignup(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        JSONObject body = parseBody(req);
        String name     = body.optString("name", "").trim();
        String email    = body.optString("email", "").trim().toLowerCase();
        String password = body.optString("password", "");
        String rollNo   = body.optString("rollNo", "").trim().toUpperCase();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || rollNo.isEmpty()) {
            sendError(resp, 400, "All fields are required");
            return;
        }
        if (password.length() < 6) {
            sendError(resp, 400, "Password must be at least 6 characters");
            return;
        }

        String hashed = BCrypt.hashpw(password, BCrypt.gensalt(12));
        String sql = "INSERT INTO users (name, email, password, roll_no) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(true);
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, hashed);
            ps.setString(4, rollNo);
            ps.executeUpdate();

            // Get the newly inserted user's ID
            String idSql = "SELECT id FROM users WHERE email = ?";
            try (PreparedStatement idPs = conn.prepareStatement(idSql)) {
                idPs.setString(1, email);
                try (ResultSet rs = idPs.executeQuery()) {
                    rs.next();
                    HttpSession session = req.getSession(true);
                    session.setAttribute("userId",     rs.getInt("id"));
                    session.setAttribute("userName",   name);
                    session.setAttribute("userEmail",  email);
                    session.setAttribute("userRollNo", rollNo);
                }
            }

            resp.setStatus(201);
            JSONObject out = new JSONObject();
            out.put("message", "Account created successfully");
            resp.getWriter().write(out.toString());

        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("ORA-00001") || msg.contains("unique constraint"))) {
                sendError(resp, 409, "Email or Roll No already registered");
            } else {
                sendError(resp, 500, "Database error: " + msg);
            }
        }
    }

    private void handleLogout(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();

        JSONObject out = new JSONObject();
        out.put("message", "Logged out");
        resp.getWriter().write(out.toString());
    }

    private JSONObject parseBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        String body = sb.toString().trim();
        return body.isEmpty() ? new JSONObject() : new JSONObject(body);
    }

    private void sendError(HttpServletResponse resp, int status, String message)
            throws IOException {
        resp.setStatus(status);
        JSONObject err = new JSONObject();
        err.put("error", message);
        resp.getWriter().write(err.toString());
    }
}