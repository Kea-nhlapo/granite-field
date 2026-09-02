package za.co.trademesh.modules.telemetry.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import za.co.trademesh.modules.telemetry.application.BackhaulMatchingService;
import za.co.trademesh.modules.telemetry.application.TelemetryService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api")
public class TelemetryController {

    static final String DEVICE_CREDENTIAL_HEADER = "X-Telemetry-Credential";

    private final TelemetryService telemetry;
    private final AuthorizationService authorization;
    private final TrackingPositionSseBroker positionStream;
    private final BackhaulMatchingService backhaul;

    public TelemetryController(
            TelemetryService telemetry,
            AuthorizationService authorization,
            TrackingPositionSseBroker positionStream,
            BackhaulMatchingService backhaul) {
        this.telemetry = telemetry;
        this.authorization = authorization;
        this.positionStream = positionStream;
        this.backhaul = backhaul;
    }

    @PostMapping("/businesses/{businessId}/shipments/{shipmentId}/telemetry-devices")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ResponseEntity<TelemetryContracts.IssuedDeviceResponse> provision(
            @PathVariable UUID businessId,
            @PathVariable UUID shipmentId,
            @Valid @RequestBody TelemetryContracts.ProvisionDeviceRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        var issued = telemetry.provision(
                businessId, shipmentId, request.displayName(), authorization.authenticatedUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(TelemetryContracts.IssuedDeviceResponse.from(issued));
    }

    @DeleteMapping("/businesses/{businessId}/telemetry-devices/{deviceId}")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ResponseEntity<Void> revoke(
            @PathVariable UUID businessId, @PathVariable UUID deviceId, Authentication authentication) {
        authorize(authentication, businessId);
        telemetry.revoke(businessId, deviceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/telemetry/readings")
    ResponseEntity<TelemetryContracts.IngestionResponse> ingest(
            @RequestHeader(DEVICE_CREDENTIAL_HEADER) String credential,
            @Valid @RequestBody TelemetryContracts.IngestReadingsRequest request) {
        var result = telemetry.ingest(
                credential,
                request.readings().stream()
                        .map(TelemetryContracts.ReadingRequest::toInput)
                        .toList());
        return ResponseEntity.accepted().body(TelemetryContracts.IngestionResponse.from(result));
    }

    @PostMapping("/tracking/{shipmentId}/position")
    ResponseEntity<TelemetryContracts.PositionIngestionResponse> position(
            @PathVariable UUID shipmentId,
            @RequestHeader(DEVICE_CREDENTIAL_HEADER) String credential,
            @Valid @RequestBody TelemetryContracts.PositionRequest request) {
        var result = telemetry.ingestPosition(credential, shipmentId, request.toInput());
        return ResponseEntity.accepted().body(TelemetryContracts.PositionIngestionResponse.from(result));
    }

    @GetMapping(value = "/tracking/{shipmentId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize(
            "hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'SUPPLIER', 'TRANSPORTER', 'DRIVER', 'ADMINISTRATOR')")
    SseEmitter positionEvents(
            @PathVariable UUID shipmentId, @RequestParam UUID businessId, Authentication authentication) {
        authorize(authentication, businessId);
        return positionStream.subscribe(shipmentId, telemetry.findLivePosition(businessId, shipmentId));
    }

    @GetMapping("/tracking/{shipmentId}/backhaul-matches")
    @PreAuthorize(
            "hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'SUPPLIER', 'TRANSPORTER', 'DRIVER', 'ADMINISTRATOR')")
    BackhaulContracts.BackhaulMatchesResponse backhaulMatches(
            @PathVariable UUID shipmentId, @RequestParam UUID businessId, Authentication authentication) {
        authorize(authentication, businessId);
        return BackhaulContracts.BackhaulMatchesResponse.from(backhaul.find(businessId, shipmentId));
    }

    @GetMapping("/businesses/{businessId}/shipments/{shipmentId}/telemetry/live")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    TelemetryContracts.LivePositionResponse live(
            @PathVariable UUID businessId, @PathVariable UUID shipmentId, Authentication authentication) {
        authorize(authentication, businessId);
        return TelemetryContracts.LivePositionResponse.from(telemetry.getLivePosition(businessId, shipmentId));
    }

    @GetMapping("/businesses/{businessId}/shipments/{shipmentId}/telemetry")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    TelemetryContracts.ReadingHistoryResponse history(
            @PathVariable UUID businessId,
            @PathVariable UUID shipmentId,
            @RequestParam(defaultValue = "100") int limit,
            Authentication authentication) {
        authorize(authentication, businessId);
        return TelemetryContracts.ReadingHistoryResponse.from(
                telemetry.getRecentReadings(businessId, shipmentId, limit));
    }

    private void authorize(Authentication authentication, UUID businessId) {
        authorization.requireBusinessAccess(authentication, businessId);
    }
}
