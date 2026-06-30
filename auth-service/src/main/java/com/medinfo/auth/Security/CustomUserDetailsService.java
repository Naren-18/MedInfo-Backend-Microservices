package com.medinfo.auth.Security;

import com.medinfo.auth.Entity.User;
import com.medinfo.auth.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User dbUser=userRepository.findByEmail(username)
                .orElseThrow(()-> new UsernameNotFoundException("User Not Found"));
        return org.springframework.security.core.userdetails.User.builder()
                .username(dbUser.getEmail())
                .password(dbUser.getPassword())
                .authorities(new ArrayList<>())
                .build();
    }
}
