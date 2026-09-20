package com.simfat.backend.config;

import com.simfat.backend.security.AuthProperties;
import jakarta.servlet.DispatcherType;
import com.simfat.backend.security.JwtAuthenticationFilter;
import com.simfat.backend.security.PrivilegedActionAuditFilter;
import com.simfat.backend.security.PublicEndpointPaths;
import com.simfat.backend.security.RestAccessDeniedHandler;
import com.simfat.backend.security.RestAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({ AuthProperties.class, SupabaseStorageProperties.class, LocalStorageProperties.class })
public class SecurityIntegrationConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtAuthenticationFilter,
        PrivilegedActionAuditFilter privilegedActionAuditFilter,
        RestAuthenticationEntryPoint authenticationEntryPoint,
        RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> {
                // Default deny: only PublicEndpointPaths is reachable anonymously.
                for (PublicEndpointPaths.Rule rule : PublicEndpointPaths.RULES) {
                    if (rule.method() == null) {
                        auth.requestMatchers(rule.pattern()).permitAll();
                    } else {
                        auth.requestMatchers(rule.method(), rule.pattern()).permitAll();
                    }
                }
                // The container ERROR dispatch runs anonymously (stateless, JWT filter skipped on error
                // dispatches); without this, a failure on a protected path would be masked as 401.
                auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(privilegedActionAuditFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
