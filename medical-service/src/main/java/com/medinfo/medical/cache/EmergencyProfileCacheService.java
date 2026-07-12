package com.medinfo.medical.cache;

import com.medinfo.medical.DTO.EmergencyProfileResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmergencyProfileCacheService {
    private final RedisTemplate<String,Object> redisTemplate;
    private String getCacheKey(String publicProfileId) {
        return "emergency-profile::" + publicProfileId;
    }

    public EmergencyProfileResponseDTO getEmergencyProfile(String publicProfileId){
        String cacheKey=getCacheKey(publicProfileId);
        return (EmergencyProfileResponseDTO) redisTemplate.opsForValue().get(cacheKey);
    }

    public void cacheEmergencyProfile(String publicProfileId,
                    EmergencyProfileResponseDTO response){
        String cacheKey=getCacheKey(publicProfileId);
        log.info("Cached emergency profile {}", publicProfileId);
        redisTemplate.opsForValue().set(cacheKey,response, Duration.ofMinutes(10));
    }

    public void evictEmergencyProfile(String publicProfileId){
        log.info("Evicted cache for {}", publicProfileId);
        redisTemplate.delete(getCacheKey(publicProfileId));
    }
}
