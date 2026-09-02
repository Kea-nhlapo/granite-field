package za.co.trademesh.shared.web;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public final class ApiRequestGuardFilter extends OncePerRequestFilter {

    private static final String TELEMETRY_CREDENTIAL = "X-Telemetry-Credential";
    private static final Pattern INVITATION_CREATE = Pattern.compile("^/api/businesses/[^/]+/supplier-invitations$");
    private static final Pattern INVITATION_GUEST =
            Pattern.compile("^/api/supplier-invitations/guest/[^/]+(?:/responses)?$");
    private static final Pattern UPLOAD = Pattern.compile("^/api/businesses/[^/]+/files$");

    private final ApiRateLimitProperties limits;
    private final ApiWebProperties web;
    private final ApiProblemWriter problems;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final Map<ClientBucket, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public ApiRequestGuardFilter(
            ApiRateLimitProperties limits,
            ApiWebProperties web,
            ApiProblemWriter problems,
            MeterRegistry metrics,
            Clock clock) {
        this.limits = limits;
        this.web = web;
        this.problems = problems;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > web.maximumContentLength().toBytes()) {
            reject(
                    request,
                    response,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Request is too large",
                    "The request exceeds the configured size limit.",
                    "REQUEST_TOO_LARGE",
                    null);
            return;
        }

        Limit limit = limitFor(request);
        if (limit != null && !allow(request, limit)) {
            response.setHeader(
                    HttpHeaders.RETRY_AFTER,
                    Long.toString(Math.max(limits.window().toSeconds(), 1)));
            reject(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests",
                    "Please wait before trying this operation again.",
                    "RATE_LIMIT_EXCEEDED",
                    limit.bucket());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean allow(HttpServletRequest request, Limit limit) {
        Instant now = Instant.now(clock);
        attempts.entrySet().removeIf(entry -> !entry.getValue().endsAt().isAfter(now));

        ClientBucket key = new ClientBucket(limit.bucket(), fingerprint(request, limit.bucket()));
        if (!attempts.containsKey(key) && attempts.size() >= limits.maximumTrackedClients()) {
            return false;
        }
        AttemptWindow window = attempts.compute(key, (ignored, current) -> {
            if (current == null || !current.endsAt().isAfter(now)) {
                return new AttemptWindow(1, now.plus(limits.window()));
            }
            return new AttemptWindow(current.count() + 1, current.endsAt());
        });
        return window.count() <= limit.maximumAttempts();
    }

    private Limit limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (HttpMethod.POST.matches(method) && "/api/auth/login".equals(path)) {
            return new Limit("login", limits.login());
        }
        if (HttpMethod.POST.matches(method) && "/api/auth/otp/send".equals(path)) {
            return new Limit("otp-send", limits.otpSend());
        }
        if (HttpMethod.POST.matches(method) && "/api/auth/otp/verify".equals(path)) {
            return new Limit("otp-verify", limits.otpVerify());
        }
        if (HttpMethod.POST.matches(method) && "/api/auth/momo/initiate".equals(path)) {
            return new Limit("momo-initiate", limits.momoInitiate());
        }
        if ((HttpMethod.POST.matches(method) && INVITATION_CREATE.matcher(path).matches())
                || INVITATION_GUEST.matcher(path).matches()) {
            return new Limit("invitations", limits.invitations());
        }
        if (HttpMethod.POST.matches(method) && UPLOAD.matcher(path).matches()) {
            return new Limit("uploads", limits.uploads());
        }
        if (HttpMethod.POST.matches(method) && "/api/telemetry/readings".equals(path)) {
            return new Limit("telemetry", limits.telemetry());
        }
        if (HttpMethod.POST.matches(method) && "/api/handovers/confirmations".equals(path)) {
            return new Limit("qr-validation", limits.qrValidation());
        }
        return null;
    }

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail,
            String code,
            String bucket)
            throws IOException {
        if (bucket != null) {
            metrics.counter("trademesh.http.rate_limit.rejections", "bucket", bucket)
                    .increment();
        }
        problems.write(request, response, status, title, detail, code);
    }

    private static String fingerprint(HttpServletRequest request, String bucket) {
        String credential = "telemetry".equals(bucket) ? request.getHeader(TELEMETRY_CREDENTIAL) : "";
        String input = String.valueOf(request.getRemoteAddr()) + "\u0000" + String.valueOf(credential);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record Limit(String bucket, int maximumAttempts) {}

    private record ClientBucket(String bucket, String fingerprint) {}

    private record AttemptWindow(int count, Instant endsAt) {}
}
