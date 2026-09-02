package za.co.trademesh.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.trademesh.shared.events.CorrelationContext;

class CorrelationContextFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sharesTheHttpRequestIdAndAuthenticatedActorWithDomainEvents() throws Exception {
        UUID requestId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestContext.REQUEST_ID_ATTRIBUTE, requestId);
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("user-17", null, "ROLE_BUSINESS_OWNER"));

        new CorrelationContextFilter()
                .doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
                    assertThat(CorrelationContext.correlationId()).isEqualTo(requestId);
                    assertThat(CorrelationContext.actor()).contains("user-17");
                });

        assertThat(CorrelationContext.actor()).isEmpty();
    }
}
