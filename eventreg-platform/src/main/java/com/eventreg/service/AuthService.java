package com.eventreg.service;

import com.eventreg.dto.request.*;
import com.eventreg.dto.response.AuthResponse;
import com.eventreg.dto.response.UserResponse;
import com.eventreg.entity.User;
import com.eventreg.entity.enums.Role;
import com.eventreg.exception.InvalidTokenException;
import com.eventreg.exception.ResourceNotFoundException;
import com.eventreg.repository.UserRepository;
import com.eventreg.security.AppUserPrincipal;
import com.eventreg.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RedisTokenService redisTokenService;
    private final EmailService emailService;

    @Value("${app.mail.verification-token-expiry-minutes}")
    private long verificationExpiryMinutes;

    @Value("${app.mail.password-reset-token-expiry-minutes}")
    private long resetExpiryMinutes;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("An account with this email already exists");
        }

        // Never trust client-supplied ADMIN role.
        Role role = (request.getRole() == null || request.getRole() == Role.ADMIN)
                ? Role.ATTENDEE : request.getRole();

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .verified(false)
                .build();
        user = userRepository.save(user);

        String token = redisTokenService.createVerificationToken(user.getId(), verificationExpiryMinutes);
        String link = frontendBaseUrl + "/verify-email?token=" + token;
        emailService.sendVerificationEmail(user.getEmail(), user.getName(), link);

        return UserResponse.from(user);
    }

    @Transactional
    public void verifyEmail(String token) {
        Long userId = redisTokenService.consumeVerificationToken(token)
                .orElseThrow(() -> new InvalidTokenException("Verification link is invalid or has expired"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setVerified(true);
        userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        AppUserPrincipal principal = new AppUserPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(principal);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(UserResponse.from(user))
                .build();
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        String token = request.getRefreshToken();
        if (!"refresh".equals(jwtService.extractTokenType(token)) || jwtService.isTokenExpired(token)) {
            throw new InvalidTokenException("Refresh token is invalid or has expired");
        }
        String email = jwtService.extractUsername(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        AppUserPrincipal principal = new AppUserPrincipal(user);
        String newAccessToken = jwtService.generateAccessToken(principal);
        String newRefreshToken = jwtService.generateRefreshToken(principal);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .user(UserResponse.from(user))
                .build();
    }

    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String token = redisTokenService.createPasswordResetToken(user.getId(), resetExpiryMinutes);
            String link = frontendBaseUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), link);
        });
        // Intentionally no error if email not found — avoids leaking which emails are registered.
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        Long userId = redisTokenService.consumePasswordResetToken(request.getToken())
                .orElseThrow(() -> new InvalidTokenException("Reset link is invalid or has expired"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
