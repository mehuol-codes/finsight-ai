package dev.mehuol.finsight.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import dev.mehuol.finsight.repository.UserRepository;

/** Loads accounts by email for Spring Security's login. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        return users.findByEmail(normalized)
                .map(u -> new AppUserDetails(u.id(), u.email(), u.passwordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + normalized));
    }
}
