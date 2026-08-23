package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.ScheduleService;
import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ServiceRepository {
    private static final String SERVICE_COLUMNS =
            "id, personId, date, time, carModel, description, serviceStatus, technician, "
                    + "estimatedDurationMinutes, completedAt, canceledAt";

    private final DataSource dataSource;

    public ServiceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Service> findByPersonId(int personId) {
        List<Service> services = new ArrayList<>();
        String sqlQuery = "SELECT " + SERVICE_COLUMNS + " FROM services "
                + "WHERE personId = ? ORDER BY id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
            statement.setInt(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    services.add(mapService(rs));
                }
            }
            return services;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load customer services", e);
        }
    }

    public List<Service> findActiveByPersonId(int personId) {
        List<Service> services = new ArrayList<>();
        String sqlQuery = "SELECT " + SERVICE_COLUMNS + " FROM services "
                + "WHERE personId = ? AND serviceStatus IN ('SCHEDULED', 'ASSIGNED', 'IN_PROGRESS') "
                + "ORDER BY id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
            statement.setInt(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    services.add(mapService(rs));
                }
            }
            return services;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load active customer services", e);
        }
    }

    public int countCompletedByPersonId(int personId) {
        String sqlQuery = "SELECT COUNT(*) FROM services WHERE personId = ? AND serviceStatus = 'COMPLETED'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
            statement.setInt(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not count completed customer services", e);
        }
    }

    public void insertScheduledService(int userId, ScheduleService scheduleService, String tableName) throws SQLException {
        String sqlQuery = "insert into " + tableName + " (personId, carModel, description) values (?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
            statement.setInt(1, userId);
            statement.setString(2, scheduleService.getCarModel());
            statement.setString(3, scheduleService.getDescription());
            statement.executeUpdate();
        }
    }

    public void insertScheduledService(int userId, ScheduleService scheduleService) throws SQLException {
        insertScheduledService(userId, scheduleService, "services");
    }

    public Optional<Service> findById(int id) {
        String sqlQuery = "SELECT " + SERVICE_COLUMNS + " FROM services WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapService(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load service " + id, e);
        }
    }

    public List<Service> findActiveAssignments(String technician, int excludedServiceId) {
        String sqlQuery = "SELECT " + SERVICE_COLUMNS + " FROM services "
                + "WHERE technician = ? AND id <> ? "
                + "AND serviceStatus IN ('ASSIGNED', 'IN_PROGRESS')";
        List<Service> assignments = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
            statement.setString(1, technician);
            statement.setInt(2, excludedServiceId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    assignments.add(mapService(rs));
                }
            }
            return assignments;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load technician assignments", e);
        }
    }

    public boolean assignTechnician(int serviceId, String technician, LocalDate date,
                                    LocalTime time, int estimatedDurationMinutes) {
        String sqlQuery = "UPDATE services SET date = ?, time = ?, technician = ?, "
                + "estimatedDurationMinutes = ?, serviceStatus = 'ASSIGNED' WHERE id = ? "
                + "AND serviceStatus IN ('SCHEDULED', 'ASSIGNED')";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
            statement.setDate(1, Date.valueOf(date));
            statement.setTime(2, Time.valueOf(time));
            statement.setString(3, technician);
            statement.setInt(4, estimatedDurationMinutes);
            statement.setInt(5, serviceId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not assign technician", e);
        }
    }

    public boolean cancel(int serviceId) {
        String sqlQuery = "UPDATE services SET serviceStatus = 'CANCELED', canceledAt = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND serviceStatus IN ('SCHEDULED', 'ASSIGNED')";
        return executeTransition(sqlQuery, serviceId, "cancel");
    }

    public boolean start(int serviceId) {
        String sqlQuery = "UPDATE services SET serviceStatus = 'IN_PROGRESS' "
                + "WHERE id = ? AND serviceStatus = 'ASSIGNED'";
        return executeTransition(sqlQuery, serviceId, "start");
    }

    public boolean complete(int serviceId) {
        String sqlQuery = "UPDATE services SET serviceStatus = 'COMPLETED', completedAt = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND serviceStatus = 'IN_PROGRESS'";
        return executeTransition(sqlQuery, serviceId, "complete");
    }

    private boolean executeTransition(String sqlQuery, int serviceId, String action) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
            statement.setInt(1, serviceId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not " + action + " service", e);
        }
    }

    private Service mapService(ResultSet rs) throws SQLException {
        Date date = rs.getDate("date");
        Time time = rs.getTime("time");
        Timestamp completedAt = rs.getTimestamp("completedAt");
        Timestamp canceledAt = rs.getTimestamp("canceledAt");
        int duration = rs.getInt("estimatedDurationMinutes");
        Integer estimatedDurationMinutes = rs.wasNull() ? null : duration;

        return new Service(
                rs.getInt("id"),
                rs.getInt("personId"),
                date == null ? null : date.toLocalDate(),
                time == null ? null : time.toLocalTime(),
                rs.getString("carModel"),
                rs.getString("description"),
                ServiceStatus.valueOf(rs.getString("serviceStatus")),
                rs.getString("technician"),
                estimatedDurationMinutes,
                completedAt == null ? null : completedAt.toLocalDateTime(),
                canceledAt == null ? null : canceledAt.toLocalDateTime()
        );
    }
}
