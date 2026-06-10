import jakarta.servlet.http.*;
import org.json.*;

import java.io.IOException;
import java.sql.*;

public class RouteServlet extends HttpServlet {

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

        String date = req.getParameter("date");
        if (date == null || date.isBlank()) {
            date = java.time.LocalDate.now().toString();
        }

        String sql = "SELECT r.id, r.route_name, r.origin, r.destination, " +
                     "r.departure, r.total_seats, r.fare, COUNT(b.id) AS booked_seats " +
                     "FROM routes r " +
                     "LEFT JOIN bookings b ON b.route_id = r.id " +
                     "AND b.travel_date = TO_DATE(?, 'YYYY-MM-DD') " +
                     "AND b.status = 'CONFIRMED' " +
                     "GROUP BY r.id, r.route_name, r.origin, r.destination, " +
                     "r.departure, r.total_seats, r.fare " +
                     "ORDER BY r.departure";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(true);
            ps.setString(1, date);
            JSONArray routes = new JSONArray();

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int totalSeats  = rs.getInt("total_seats");
                    int bookedSeats = rs.getInt("booked_seats");

                    JSONObject route = new JSONObject();
                    route.put("id",             rs.getInt("id"));
                    route.put("routeName",      rs.getString("route_name"));
                    route.put("origin",         rs.getString("origin"));
                    route.put("destination",    rs.getString("destination"));
                    route.put("departure",      rs.getString("departure"));
                    route.put("totalSeats",     totalSeats);
                    route.put("bookedSeats",    bookedSeats);
                    route.put("availableSeats", totalSeats - bookedSeats);
                    route.put("fare",           rs.getBigDecimal("fare"));
                    routes.put(route);
                }
            }

            resp.getWriter().write(routes.toString());

        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }
}