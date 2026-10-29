package com.eparly.api.auth.config;

import com.eparly.api.auth.dto.ApiErrorResponse;
import com.eparly.api.auth.service.UserSecurityValidator;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.time.Instant;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            Environment environment,
            UserSecurityValidator userSecurityValidator,
            @Value("${eparly.openapi.public:false}") boolean openApiPublic
    ) throws Exception {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.POST,
                                    "/v1/auth/register",
                                    "/v1/auth/login",
                                    "/v1/auth/refresh",
                                    "/v1/auth/logout",
                                    "/v1/auth/verify-email",
                                    "/v1/auth/resend-verification",
                                    "/v1/auth/forgot-password",
                                    "/v1/auth/verify-reset-code",
                                    "/v1/auth/reset-password")
                            .permitAll();
                    authorize.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                            .permitAll();
                    if (openApiPublic && environment.acceptsProfiles(Profiles.of("local", "test"))
                            && !environment.acceptsProfiles(Profiles.of("prod"))) {
                        authorize.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                .permitAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                            userSecurityValidator.validate(token);
                            return jwtAuthenticationConverter.convert(token);
                        }))
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(objectMapper, response, 401, "Unauthorized",
                                        "Authentication is required or the access token is invalid"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(objectMapper, response, 403, "Forbidden",
                                        "You are not allowed to access this resource"))
                );
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }
        return http.build();
    }

    private static void writeSecurityError(
            ObjectMapper objectMapper,
            HttpServletResponse response,
            int status,
            String error,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (status == 401) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        objectMapper.writeValue(response.getOutputStream(),
                new ApiErrorResponse(Instant.now(), status, error, message, null));
    }
}
