package za.co.trademesh.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import za.co.trademesh.shared.events.CorrelationContext;

public final class CorrelationContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Object value = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        UUID correlationId = value instanceof UUID requestId ? requestId : UUID.randomUUID();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication != null && authentication.isAuthenticated() ? authentication.getName() : null;

        try {
            CorrelationContext.runCheckedWithin(correlationId, actor, () -> filterChain.doFilter(request, response));
        } catch (ServletException | IOException expected) {
            throw expected;
        } catch (Exception unexpected) {
            throw new ServletException(unexpected);
        }
    }
}
