package com.geosun.tms.auth.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.domain.token.RefreshToken;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.repository.RefreshTokenRepository;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.auth.security.SecurityErrorWriter;
import com.geosun.tms.auth.security.SecurityPathRules;
import com.geosun.tms.auth.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Витягує Bearer JWT, перевіряє користувача, refresh-сесію та статус облікового запису.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final ObjectMapper objectMapper;

  public JwtAuthenticationFilter(
      JwtService jwtService,
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      ObjectMapper objectMapper) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityPathRules.isPublic(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      writeUnauthorized(response, request, "Missing or invalid Authorization header");
      return;
    }

    String token = header.substring(7).trim();
    if (token.isEmpty()) {
      writeUnauthorized(response, request, "Missing or invalid Authorization header");
      return;
    }

    Optional<JwtAccessClaims> parsed = jwtService.parseAccessToken(token);
    if (parsed.isEmpty()) {
      writeUnauthorized(response, request, "Invalid or expired access token");
      return;
    }

    JwtAccessClaims claims = parsed.get();
    String subjectUserId = Objects.requireNonNull(claims.subjectUserId());
    Optional<User> userOpt = userRepository.findById(subjectUserId);
    if (userOpt.isEmpty()) {
      writeUnauthorized(response, request, "Invalid or expired access token");
      return;
    }

    User user = userOpt.get();
    if (user.isDeleted()) {
      SecurityErrorWriter.writeJson(
          response,
          objectMapper,
          HttpServletResponse.SC_FORBIDDEN,
          "Forbidden",
          "USER_DELETED",
          "User account has been deleted",
          request.getRequestURI());
      return;
    }
    if (!user.isActive()) {
      SecurityErrorWriter.writeJson(
          response,
          objectMapper,
          HttpServletResponse.SC_FORBIDDEN,
          "Forbidden",
          "ACCOUNT_DISABLED",
          "Account is disabled",
          request.getRequestURI());
      return;
    }

    Optional<RefreshToken> sessionOpt = refreshTokenRepository.findByIdWithUser(claims.sessionId());
    if (sessionOpt.isEmpty()) {
      writeInvalidSession(response, request);
      return;
    }
    RefreshToken session = sessionOpt.get();
    if (!session.getUser().getId().equals(user.getId())) {
      writeInvalidSession(response, request);
      return;
    }
    if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(Instant.now())) {
      writeInvalidSession(response, request);
      return;
    }

    UserPrincipal principal =
        new UserPrincipal(user.getId(), user.getEmail(), user.getRole(), claims.sessionId());
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);

    filterChain.doFilter(request, response);
  }

  private void writeUnauthorized(
      HttpServletResponse response, HttpServletRequest request, String message) throws IOException {
    SecurityErrorWriter.writeJson(
        response,
        objectMapper,
        HttpServletResponse.SC_UNAUTHORIZED,
        "Unauthorized",
        "UNAUTHORIZED",
        message,
        request.getRequestURI());
  }

  private void writeInvalidSession(HttpServletResponse response, HttpServletRequest request)
      throws IOException {
    SecurityErrorWriter.writeJson(
        response,
        objectMapper,
        HttpServletResponse.SC_UNAUTHORIZED,
        "Unauthorized",
        "INVALID_SESSION",
        "Refresh session is invalid or expired",
        request.getRequestURI());
  }
}
