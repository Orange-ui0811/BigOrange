package com.orangejd.server.service;
import com.orangejd.server.mapper.NotificationMapper;
import org.springframework.stereotype.Service;
@Service
public class NotificationMaintenanceService {
    private final NotificationMapper mapper;
    public NotificationMaintenanceService(NotificationMapper mapper) { this.mapper = mapper; }
    public void cleanupReadOlderThan(int days) { mapper.deleteOldRead(days); }
}
