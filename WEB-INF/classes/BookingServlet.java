import jakarta.servlet.http.*;
import org.json.*;

import java.io.*;
import java.sql.*;

public class BookingServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("application/json;charset=UTF-8");
        if (!isLoggedIn(req)) { sendUnauth(resp); return; }

        String routeIdStr = req.getParameter("routeId");
        String date       = req.getParameter("date");

        if (routeIdStr == null || date == null) {
            sendError(resp, 400, "routeId and date are required");
            return;
        }

        int routeId;
        try { routeId = Integer.parseInt(routeIdStr); }
        catch (NumberFormatException e) { sendError(resp, 400, "Invalid routeId"); return; }

        String sql = "SELECT seat_no, u.name AS booked_by " +
                     "FROM bookings b " +
                     "JOIN users u ON u.id = b.user_id " +
                     "WHERE b.route_id = ? AND b.travel_date = TO_DATE(?, 'YYYY-MM-DD') AND b.status = 'CONFIRMED' " +
                     "ORDER BY seat_no";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(true);
            ps.setInt(1, routeId);
            ps.setString(2, date);

            JSONArray seats = new JSONArray();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject seat = new JSONObject();
                    seat.put("seatNo",   rs.getInt("seat_no"));
                    seat.put("bookedBy", rs.getString("booked_by"));
                    seats.put(seat);
                }
            }

            int totalSeats = getTotalSeats(conn, routeId);

            JSONObject out = new JSONObject();
            out.put("totalSeats",  totalSeats);
            out.put("bookedSeats", seats);
            resp.getWriter().write(out.toString());

        } catch (SQLException e) {
            sendError(resp, 500, "Database error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("application/json;charset=UTF-8");
        if (!isLoggedIn(req)) { sendUnauth(resp); return; }

        JSONObject body = parseBody(req);
        int    routeId  = body.optInt("routeId", -1);
        int    seatNo   = body.optInt("seatNo",  -1);
        String date     = body.optString("date",  "").trim();

        if (routeId < 1 || seatNo < 1 || date.isEmpty()) {
            sendError(resp, 400, "routeId, seatNo, and date are required");
            return;
        }

        int userId = (int) req.getSession().getAttribute("userId");

        String insertSql = "INSERT INTO bookings (user_id, route_id, seat_no, travel_date, status) " +
                           "VALUES (?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), 'CONFIRMED')";

        try (Connection conn = DBConnection.getConnection()) {

            conn.setAutoCommit(true);

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, routeId);
                ps.setInt(3, seatNo);
                ps.setString(4, date);
                ps.executeUpdate();

                String idSql = "SELECT id FROM bookings WHERE user_id = ? AND route_id = ? " +
                               "AND seat_no = ? AND travel_date = TO_DATE(?, 'YYYY-MM-DD')";
                try (PreparedStatement idPs = conn.prepareStatement(idSql)) {
                    idPs.setInt(1, userId);
                    idPs.setInt(2, routeId);
                    idPs.setInt(3, seatNo);
                    idPs.setString(4, date);
                    try (ResultSet rs = idPs.executeQuery()) {
                        rs.next();
                        resp.setStatus(201);
                        JSONObject out = new JSONObject();
                        out.put("message",   "Booking confirmed");
                        out.put("bookingId", rs.getInt("id"));
                        resp.getWriter().write(out.toString());
                    }
                }
            }

        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("ORA-00001") || msg.contains("unique constraint"))) {
                sendError(resp, 409, "Seat " + seatNo + " is already taken. Please choose another.");
            } else {
                sendError(resp, 500, "Database error: " + msg);
            }
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("application/json;charset=UTF-8");
        if (!isLoggedIn(req)) { sendUnauth(resp); return; }

        String bookingIdStr = req.getParameter("bookingId");
        if (bookingIdStr == null) { sendError(resp, 400, "bookingId is required"); return; }

        int bookingId;
        try { bookingId = Integer.parseInt(bookingIdStr); }
        catch (NumberFormatException e) { sendError(resp, 400, "Invalid bookingId"); return; }

        int userId = (int) req.getSession().getAttribute("userId");

        String sql = "UPDATE bookings SET status = 'CANCELLED' " +
                     "WHERE id = ? AND user_id = ? AND status = 'CONFIRMED'";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(true);
            ps.setInt(1, bookingId);
            ps.setInt(2, userId);
            int rows = ps.executeUpdate();

            if (rows == 0) {
                sendError(resp, 404, "Booking not found or already cancelled");
            } else {
                JSONObject out = new JSONObject();
                out.put("message", "Booking cancelled");
                resp.getWriter().write(out.toString());
            }

        } catch (SQLException e) {
            sendError(resp, 500, "Database error: " + e.getMessage());
        }
    }

    private int getTotalSeats(Connection conn, int routeId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT total_seats FROM routes WHERE id = ?")) {
            ps.setInt(1, routeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("total_seats") : 40;
            }
        }
    }

    private boolean isLoggedIn(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null && s.getAttribute("userId") != null;
    }

    private JSONObject parseBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = req.getReader()) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        String body = sb.toString().trim();
        return body.isEmpty() ? new JSONObject() : new JSONObject(body);
    }

    private void sendError(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(new JSONObject().put("error", msg).toString());
    }

    private void sendUnauth(HttpServletResponse resp) throws IOException {
        sendError(resp, 401, "Not authenticated");
    }
}