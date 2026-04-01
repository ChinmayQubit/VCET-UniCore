package com.college.studentportal.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
            .requestMatchers("/assets/**", "/*.html", "/*.css", "/*.js", "/*.png", "/*.jpg", "/*.svg", "/*.ico");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // --- Public: authentication & static assets ---
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/", "/index.html", "/*.html").permitAll()
                .requestMatchers("/*.css", "/*.js", "/*.png", "/*.jpg", "/*.ico", "/assets/**").permitAll()

                // --- Student endpoints (STUDENT or ADMIN can access) ---
                .requestMatchers("/results/student/**").hasAnyRole("STUDENT", "ADMIN")
                .requestMatchers("/results/cgpa/**").hasAnyRole("STUDENT", "ADMIN")
                .requestMatchers("/results/sgpa/**").hasAnyRole("STUDENT", "ADMIN")
                .requestMatchers("/analysis/**").hasAnyRole("STUDENT", "ADMIN")
                .requestMatchers("/api/attendance/summary/**").hasAnyRole("STUDENT", "ADMIN", "FACULTY")
                .requestMatchers("/announcements/student/**").hasAnyRole("STUDENT", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/students/*/change-password").hasAnyRole("STUDENT", "ADMIN")

                // --- Faculty self-service endpoints ---
                .requestMatchers(HttpMethod.POST, "/faculty/change-password").hasRole("FACULTY")

                // --- Attendance endpoints (FACULTY only for marking) ---
                .requestMatchers(HttpMethod.POST, "/api/attendance/mark").hasRole("FACULTY")
                .requestMatchers("/api/attendance/my-subjects").hasRole("FACULTY")
                .requestMatchers("/api/attendance/students-by-semester/*/my-department").hasRole("FACULTY")

                // --- Admin can still view students by semester (for admin dashboard) ---
                .requestMatchers("/api/attendance/students-by-semester/**").hasAnyRole("ADMIN", "FACULTY")

                // --- Admin-only: Student management ---
                .requestMatchers(HttpMethod.GET, "/students").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/students").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/students/upload").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/students/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/students/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/students/*/reset-password").hasRole("ADMIN")

                // --- Subject management (read for all authenticated, write for admin) ---
                .requestMatchers(HttpMethod.GET, "/subjects/**").hasAnyRole("STUDENT", "ADMIN", "FACULTY")
                .requestMatchers(HttpMethod.POST, "/subjects").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/subjects/*/assign-faculty").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/subjects/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/subjects/**").hasRole("ADMIN")

                // --- Announcements ---
                .requestMatchers(HttpMethod.GET, "/announcements").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/announcements").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/announcements/**").hasRole("ADMIN")

                // --- Results ---
                .requestMatchers(HttpMethod.GET, "/results").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/results").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/results/upload").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/results/**").hasRole("ADMIN")

                // --- Faculty management (admin only) ---
                .requestMatchers(HttpMethod.GET, "/faculty").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/faculty").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/faculty/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/faculty/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/faculty/*/reset-password").hasRole("ADMIN")

                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
            "http://localhost:5173", 
            "http://localhost:8080",
            "https://vcet-unicore-production.up.railway.app"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
