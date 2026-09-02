package za.co.trademesh.shared.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class OpenApiConfiguration {

    private static final String BEARER_AUTH = "bearerAuth";
    private static final Map<String, String> NON_DEFAULT_SUCCESS_STATUSES = Map.ofEntries(
            Map.entry("aggregationSuggest", "201"),
            Map.entry("authLogout", "204"),
            Map.entry("authRegister", "201"),
            Map.entry("businessStartRegisteredOnboarding", "201"),
            Map.entry("capacityMatchingReserve", "201"),
            Map.entry("capacityMatchingSearch", "201"),
            Map.entry("documentComparisonCompare", "201"),
            Map.entry("documentRegister", "202"),
            Map.entry("fileStorageUpload", "201"),
            Map.entry("handoverIssue", "201"),
            Map.entry("procurementConfirmQuote", "201"),
            Map.entry("procurementCreateQuote", "201"),
            Map.entry("procurementCreateRequest", "201"),
            Map.entry("routeScoringScore", "201"),
            Map.entry("routingCalculate", "201"),
            Map.entry("shipmentCreate", "201"),
            Map.entry("supplierInvite", "201"),
            Map.entry("telemetryIngest", "202"),
            Map.entry("telemetryProvision", "201"),
            Map.entry("telemetryRevoke", "204"),
            Map.entry("transportAssignDriver", "201"),
            Map.entry("transportCreateDriver", "201"),
            Map.entry("transportCreateVehicle", "201"),
            Map.entry("transportPublishOffer", "201"),
            Map.entry("transportRegister", "201"));

    @Bean
    OpenAPI tradeMeshOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TradeMesh API")
                        .version("1.0.0")
                        .description("Reviewed contract for the TradeMesh web client and partner integrations."))
                .servers(List.of(new Server().url("/").description("Current TradeMesh API host")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        .addSchemas("ApiProblem", apiProblemSchema()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    GroupedOpenApi tradeMeshApi() {
        return GroupedOpenApi.builder()
                .group("trademesh-v1")
                .pathsToMatch("/api/**")
                .addOperationCustomizer(stableOperationIds())
                .addOpenApiCustomizer(commonErrors())
                .build();
    }

    private static OperationCustomizer stableOperationIds() {
        return (operation, handlerMethod) -> {
            String controller = handlerMethod.getBeanType().getSimpleName().replaceFirst("Controller$", "");
            String operationId = lowerCamel(controller)
                    + upperCamel(handlerMethod.getMethod().getName());
            operation.setOperationId(operationId);
            normalizeSuccessResponse(operation.getResponses(), NON_DEFAULT_SUCCESS_STATUSES.get(operationId));
            if (isPublic(handlerMethod)) {
                operation.setSecurity(List.of());
            }
            return operation;
        };
    }

    private static OpenApiCustomizer commonErrors() {
        return openApi -> {
            Components components = openApi.getComponents().addSchemas("ApiProblem", apiProblemSchema());
            addProblemComponent(components, "BadRequest", "400", "The request is invalid", "INVALID_REQUEST");
            addProblemComponent(
                    components, "Unauthorized", "401", "Authentication is required", "AUTHENTICATION_REQUIRED");
            addProblemComponent(
                    components,
                    "Forbidden",
                    "403",
                    "The caller is not allowed to perform this action",
                    "ACCESS_DENIED");
            addProblemComponent(components, "NotFound", "404", "The requested resource was not found", "NOT_FOUND");
            addProblemComponent(
                    components, "Conflict", "409", "The request conflicts with current state", "STATE_CONFLICT");
            addProblemComponent(
                    components, "RateLimited", "429", "The request limit was exceeded", "RATE_LIMIT_EXCEEDED");
            addProblemComponent(
                    components, "ServerError", "500", "The server could not complete the request", "INTERNAL_ERROR");
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                addProblem(operation.getResponses(), "400", "BadRequest");
                addProblem(operation.getResponses(), "401", "Unauthorized");
                addProblem(operation.getResponses(), "403", "Forbidden");
                addProblem(operation.getResponses(), "404", "NotFound");
                addProblem(operation.getResponses(), "409", "Conflict");
                addProblem(operation.getResponses(), "429", "RateLimited");
                addProblem(operation.getResponses(), "500", "ServerError");
            }));
        };
    }

    private static void addProblemComponent(
            Components components, String name, String status, String description, String errorCode) {
        MediaType mediaType = new MediaType()
                .schema(new ObjectSchema().$ref("#/components/schemas/ApiProblem"))
                .example(java.util.Map.of(
                        "type",
                        "about:blank",
                        "title",
                        description,
                        "status",
                        Integer.parseInt(status),
                        "detail",
                        description + ".",
                        "instance",
                        "/api/example",
                        "code",
                        errorCode,
                        "requestId",
                        "01JEXAMPLE00000000000000000"));
        components.addResponses(
                name,
                new ApiResponse()
                        .description(description)
                        .content(new Content().addMediaType("application/problem+json", mediaType)));
    }

    private static void addProblem(
            io.swagger.v3.oas.models.responses.ApiResponses responses, String status, String component) {
        responses.putIfAbsent(status, new ApiResponse().$ref("#/components/responses/" + component));
    }

    private static ObjectSchema apiProblemSchema() {
        return (ObjectSchema) new ObjectSchema()
                .required(List.of("type", "title", "status", "detail", "instance", "code", "requestId"))
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("The request is invalid"))
                .addProperty("status", new IntegerSchema().format("int32").example(400))
                .addProperty("detail", new StringSchema().example("One or more fields are invalid."))
                .addProperty("instance", new StringSchema().example("/api/auth/login"))
                .addProperty("code", new StringSchema().example("INVALID_REQUEST"))
                .addProperty("requestId", new StringSchema().example("01JEXAMPLE00000000000000000"));
    }

    private static boolean isPublic(HandlerMethod handlerMethod) {
        String controller = handlerMethod.getBeanType().getSimpleName();
        String method = handlerMethod.getMethod().getName();
        return controller.equals("AuthController")
                || (controller.equals("SupplierController")
                        && (method.equals("viewGuest") || method.equals("submitResponse")))
                || (controller.equals("TelemetryController") && method.equals("ingest"))
                || (controller.equals("TrustController") && method.equals("publicSummary"));
    }

    private static void normalizeSuccessResponse(
            io.swagger.v3.oas.models.responses.ApiResponses responses, String status) {
        if (status == null) {
            return;
        }
        ApiResponse response = responses.remove("200");
        if (response == null) {
            return;
        }
        response.setDescription(
                switch (status) {
                    case "201" -> "Created";
                    case "202" -> "Accepted";
                    case "204" -> "No Content";
                    default -> response.getDescription();
                });
        if (status.equals("204")) {
            response.setContent(null);
        }
        responses.addApiResponse(status, response);
    }

    private static String lowerCamel(String value) {
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

    private static String upperCamel(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
