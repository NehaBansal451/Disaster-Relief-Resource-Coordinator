package com.disasterrelief.backend.map;

import com.disasterrelief.backend.security.StaffOnly;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/map: everything the map screen draws, from real records. Optional disasterId limits disasters, areas,
 * shelters and routes to one disaster. Warehouses are always shown.
 */
@RestController
@RequestMapping("/api/map")
public class MapController {

    public record Point(long id, String name, double latitude, double longitude, String severity, String detail) {
    }

    public record Route(long dispatchId, String status, double fromLatitude, double fromLongitude, double toLatitude,
                        double toLongitude, String label) {
    }

    public record MapResponse(List<Point> disasters, List<Point> areas, List<Point> warehouses, List<Point> shelters,
                              List<Route> routes) {
    }

    private final JdbcClient jdbc;

    public MapController(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @GetMapping
    @StaffOnly
    public MapResponse get(@RequestParam(required = false) Long disasterId) {
        List<Point> disasters = jdbc.sql("""
                SELECT id, name, ST_Y(epicentre::geometry) AS lat, ST_X(epicentre::geometry) AS lon, severity, status
                FROM disasters WHERE epicentre IS NOT NULL AND (CAST(:d AS bigint) IS NULL OR id = :d) ORDER BY id
                """).param("d", disasterId)
                .query((rs, n) -> new Point(rs.getLong("id"), rs.getString("name"), rs.getDouble("lat"),
                        rs.getDouble("lon"), rs.getString("severity"), rs.getString("status")))
                .list();
        List<Point> areas = jdbc.sql("""
                SELECT id, name, ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lon, impact_level, population_estimate
                FROM affected_areas WHERE (CAST(:d AS bigint) IS NULL OR disaster_id = :d) ORDER BY id
                """).param("d", disasterId)
                .query((rs, n) -> new Point(rs.getLong("id"), rs.getString("name"), rs.getDouble("lat"),
                        rs.getDouble("lon"), rs.getString("impact_level"), rs.getInt("population_estimate") + " people"))
                .list();
        List<Point> warehouses = jdbc.sql("""
                SELECT id, name, ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lon, status
                FROM warehouses ORDER BY id
                """)
                .query((rs, n) -> new Point(rs.getLong("id"), rs.getString("name"), rs.getDouble("lat"),
                        rs.getDouble("lon"), null, rs.getString("status")))
                .list();
        List<Point> shelters = jdbc.sql("""
                SELECT id, name, ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lon, current_occupancy AS occupancy, capacity_people AS capacity
                FROM shelters WHERE status <> 'INACTIVE' ORDER BY id
                """)
                .query((rs, n) -> new Point(rs.getLong("id"), rs.getString("name"), rs.getDouble("lat"),
                        rs.getDouble("lon"), rs.getInt("occupancy") > rs.getInt("capacity") ? "CRITICAL" : "LOW",
                        rs.getInt("occupancy") + " of " + rs.getInt("capacity") + " places used"))
                .list();
        List<Route> routes = jdbc.sql("""
                SELECT d.id, d.status, ST_Y(w.location::geometry) AS flat, ST_X(w.location::geometry) AS flon,
                       ST_Y(a.location::geometry) AS tlat, ST_X(a.location::geometry) AS tlon,
                       w.name || ' to ' || a.name AS label
                FROM dispatches d
                JOIN warehouses w ON w.id = d.warehouse_id
                JOIN need_requests n ON n.id = d.request_id
                JOIN affected_areas a ON a.id = n.affected_area_id
                WHERE d.status <> 'DELIVERED' AND (CAST(:d AS bigint) IS NULL OR n.disaster_id = :d) ORDER BY d.id
                """).param("d", disasterId)
                .query((rs, n) -> new Route(rs.getLong("id"), rs.getString("status"), rs.getDouble("flat"),
                        rs.getDouble("flon"), rs.getDouble("tlat"), rs.getDouble("tlon"), rs.getString("label")))
                .list();
        return new MapResponse(disasters, areas, warehouses, shelters, routes);
    }
}
