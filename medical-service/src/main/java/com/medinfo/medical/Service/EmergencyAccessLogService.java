package com.medinfo.medical.Service;

import com.medinfo.medical.Entity.EmergencyAccessLog;
import com.medinfo.medical.Enum.AccessMethod;
import com.medinfo.medical.Repository.EmergencyAccessLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class EmergencyAccessLogService {

    private final EmergencyAccessLogRepository emergencyAccessLogRepository;
    public void logAccess(
            Long userId,
            HttpServletRequest request,
            AccessMethod accessMethod
    ){
        String ipAddress = request.getRemoteAddr();

        String userAgent = request.getHeader("User-Agent");

        EmergencyAccessLog emergencyAccessLog=EmergencyAccessLog.builder()
                .userId(userId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .accessMethod(accessMethod)
                .build();
        emergencyAccessLogRepository.save(emergencyAccessLog);


    }

}
