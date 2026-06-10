import jakarta.servlet.http.*;
import org.json.*;

import java.io.IOException;
import java.sql.*;

/**
 * GET /api/my-bookings
 * Returns all bookings (confirmed + cancelled) for the logged-in student,
 * newest first, enriched with route details.
 */
public class MyBookingsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("application/json;charset=UTF-8");

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return;
        }

        int userId = (int) session.getAttribute("userId");

        String sql = """
                SELECT
                    b.id           AS booking_id,
                    b.seat_no,
                    b.travel_date,
                    b.status,
                    b.booked_at,
                    r.route_name,
                    r.origin,
                    r.destination,
                    r.departure,
                    r.fare
                FROM bookings b
                JOIN routes r ON r.id = b.route_id
                WHERE b.user_id = ?
                ORDER BY b.travel_date DESC, b.booked_at DESC
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            JSONArray bookings = new JSONArray();

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject booking = new JSONObject();
                    booking.put("bookingId",   rs.getInt("booking_id"));
                    booking.put("seatNo",      rs.getInt("seat_no"));
                    booking.put("travelDate",  rs.getString("travel_date"));
                    booking.put("status",      rs.getString("status"));
                    booking.put("bookedAt",    rs.getString("booked_at"));
                    booking.put("routeName",   rs.getString("route_name"));
                    booking.put("origin",      rs.getString("origin"));
                    booking.put("destination", rs.getString("destination"));
                    booking.put("departure",   rs.getString("departure"));
                    booking.put("fare",        rs.getBigDecimal("fare"));
                    bookings.put(booking);
                }
            }

            resp.getWriter().write(bookings.toString());

        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }
}