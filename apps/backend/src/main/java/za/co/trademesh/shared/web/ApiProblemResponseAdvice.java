package za.co.trademesh.shared.web;

import java.net.URI;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiProblemResponseAdvice implements ResponseBodyAdvice<ProblemDetail> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ProblemDetail.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public ProblemDetail beforeBodyWrite(
            ProblemDetail problem,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        problem.setInstance(URI.create("/api"));
        if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
            problem.setProperty("code", "REQUEST_FAILED");
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            problem.setProperty("requestId", RequestContext.requestId(servletRequest.getServletRequest()));
        }
        return problem;
    }
}
