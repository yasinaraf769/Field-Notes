package com.fieldnotes.auth.service;

import com.fieldnotes.auth.dto.AuthResponse;
import com.fieldnotes.auth.dto.LoginRequest;
import com.fieldnotes.auth.dto.RefreshTokenRequest;
import com.fieldnotes.auth.dto.RegisterRequest;
import com.fieldnotes.auth.dto.UserSummaryResponse;
import com.fieldnotes.auth.entity.RefreshToken;
import com.fieldnotes.auth.repository.RefreshTokenRepository;
import com.fieldnotes.auth.security.JwtService;
import com.fieldnotes.user.User;
import com.fieldnotes.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName() == null ? null : request.firstName().trim());
        user.setLastName(request.lastName() == null ? null : request.lastName().trim());
        user.setPhone(request.phone() == null ? null : request.phone().trim());
        user.setActive(true);

        User savedUser = userRepository.save(user);
        return buildAuthResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        if (!authentication.isAuthenticated()) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        Claims claims;
        try {
            claims = jwtService.parseToken(request.refreshToken());
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new BadCredentialsException("Invalid token type");
        }

        String tokenHash = hashToken(request.refreshToken());
        RefreshToken storedToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token is revoked or invalid"));

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new BadCredentialsException("Refresh token is expired");
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();
        if (!user.isActive()) {
            throw new BadCredentialsException("User account is deactivated");
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(User user) {
        if (user != null) {
            refreshTokenRepository.revokeAllByUser(user);
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail());
        String refreshToken = createAndStoreRefreshToken(user);
        return AuthResponse.of(UserSummaryResponse.from(user), accessToken, refreshToken);
    }

    private String createAndStoreRefreshToken(User user) {
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());
        Claims claims = jwtService.parseToken(refreshToken);
        Instant expiresAt = claims.getExpiration().toInstant();

        RefreshToken tokenEntity = new RefreshToken();
        tokenEntity.setUser(user);
        tokenEntity.setTokenHash(hashToken(refreshToken));
        tokenEntity.setExpiresAt(expiresAt);
        tokenEntity.setRevoked(false);
        refreshTokenRepository.save(tokenEntity);

        return refreshToken;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
