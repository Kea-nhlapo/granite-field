package za.co.trademesh.modules.procurement.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.procurement.application.ProcurementService;
import za.co.trademesh.modules.procurement.domain.ConfirmedOrder;
import za.co.trademesh.modules.procurement.domain.OrderLineItem;
import za.co.trademesh.modules.procurement.domain.OrderStatus;
import za.co.trademesh.modules.procurement.domain.ProcurementRequestStatus;
import za.co.trademesh.modules.procurement.domain.ProductRequest;
import za.co.trademesh.modules.procurement.domain.ProductRequestItem;
import za.co.trademesh.modules.procurement.domain.QuoteLineItem;
import za.co.trademesh.modules.procurement.domain.QuoteStatus;
import za.co.trademesh.modules.procurement.domain.SupplierQuote;
import za.co.trademesh.modules.procurement.domain.UnitOfMeasure;

public final class ProcurementContracts {

    private ProcurementContracts() {}

    public record CreateProductRequest(
            @NotNull UUID requestId,
            @NotBlank @Size(max = 500) String destinationLabel,
            double destinationLatitude,
            double destinationLongitude,
            @NotNull Instant deliveryWindowStart,
            @NotNull Instant deliveryWindowEnd,
            @NotEmpty @Size(max = 100) List<@Valid ProductRequestItemRequest> items) {

        ProcurementService.CreateRequest toCommand() {
            return new ProcurementService.CreateRequest(
                    requestId,
                    destinationLabel,
                    destinationLatitude,
                    destinationLongitude,
                    deliveryWindowStart,
                    deliveryWindowEnd,
                    items.stream().map(ProductRequestItemRequest::toCommand).toList());
        }
    }

    public record ProductRequestItemRequest(
            @NotNull UUID itemId,
            @Size(max = 100) String productCode,
            @NotBlank @Size(max = 500) String description,

            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal quantity,

            @NotNull UnitOfMeasure unitOfMeasure) {

        ProcurementService.RequestItem toCommand() {
            return new ProcurementService.RequestItem(itemId, productCode, description, quantity, unitOfMeasure);
        }
    }

    public record CreateQuoteRequest(
            @NotNull UUID requestId,
            @NotNull UUID supplierProfileId,
            @NotNull UUID sourceDocumentId,
            @NotBlank @Size(min = 3, max = 3) String currency,

            @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4)
            BigDecimal taxAmount,

            @NotNull @Future Instant validUntil,
            @NotEmpty @Size(max = 100) List<@Valid QuoteItemRequest> items) {

        ProcurementService.CreateQuote toCommand() {
            return new ProcurementService.CreateQuote(
                    requestId,
                    supplierProfileId,
                    sourceDocumentId,
                    currency,
                    taxAmount,
                    validUntil,
                    items.stream().map(QuoteItemRequest::toCommand).toList());
        }
    }

    public record QuoteItemRequest(
            @NotNull UUID requestItemId,

            @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4)
            BigDecimal unitPrice) {

        ProcurementService.QuoteItem toCommand() {
            return new ProcurementService.QuoteItem(requestItemId, unitPrice);
        }
    }

    public record ConfirmQuoteRequest(@NotNull UUID requestId) {}

    public record ProductRequestResponse(
            UUID id,
            UUID buyerBusinessId,
            ProcurementRequestStatus status,
            DestinationResponse destination,
            DeliveryWindowResponse deliveryWindow,
            List<ProductRequestItemResponse> items,
            UUID createdByUserId,
            Instant createdAt,
            Instant updatedAt) {

        static ProductRequestResponse from(ProductRequest request) {
            return new ProductRequestResponse(
                    request.id(),
                    request.buyerBusinessId(),
                    request.status(),
                    new DestinationResponse(
                            request.destinationLabel(), request.destinationLatitude(), request.destinationLongitude()),
                    new DeliveryWindowResponse(request.deliveryWindowStart(), request.deliveryWindowEnd()),
                    request.items().stream()
                            .map(ProductRequestItemResponse::from)
                            .toList(),
                    request.createdByUserId(),
                    request.createdAt(),
                    request.updatedAt());
        }
    }

    public record ProductRequestItemResponse(
            UUID id, String productCode, String description, BigDecimal quantity, UnitOfMeasure unitOfMeasure) {

        static ProductRequestItemResponse from(ProductRequestItem item) {
            return new ProductRequestItemResponse(
                    item.id(), item.productCode(), item.description(), item.quantity(), item.unitOfMeasure());
        }
    }

    public record QuoteResponse(
            UUID id,
            UUID requestId,
            UUID buyerBusinessId,
            UUID supplierProfileId,
            UUID sourceDocumentId,
            QuoteStatus status,
            MoneySummary money,
            Instant validUntil,
            List<QuoteItemResponse> items,
            UUID createdByUserId,
            Instant createdAt) {

        static QuoteResponse from(SupplierQuote quote) {
            return new QuoteResponse(
                    quote.id(),
                    quote.requestId(),
                    quote.buyerBusinessId(),
                    quote.supplierProfileId(),
                    quote.sourceDocumentId(),
                    quote.status(),
                    new MoneySummary(quote.currency(), quote.subtotal(), quote.taxAmount(), quote.total()),
                    quote.validUntil(),
                    quote.items().stream().map(QuoteItemResponse::from).toList(),
                    quote.createdByUserId(),
                    quote.createdAt());
        }
    }

    public record QuoteItemResponse(
            UUID id,
            UUID requestItemId,
            String description,
            BigDecimal quantity,
            UnitOfMeasure unitOfMeasure,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {

        static QuoteItemResponse from(QuoteLineItem item) {
            return new QuoteItemResponse(
                    item.id(),
                    item.requestItemId(),
                    item.description(),
                    item.quantity(),
                    item.unitOfMeasure(),
                    item.unitPrice(),
                    item.lineTotal());
        }
    }

    public record OrderResponse(
            UUID id,
            UUID requestId,
            UUID sourceQuoteId,
            UUID buyerBusinessId,
            UUID supplierProfileId,
            UUID sourceDocumentId,
            OrderStatus status,
            MoneySummary money,
            DestinationResponse destination,
            DeliveryWindowResponse deliveryWindow,
            List<OrderItemResponse> items,
            UUID confirmedByUserId,
            Instant confirmedAt) {

        static OrderResponse from(ConfirmedOrder order) {
            return new OrderResponse(
                    order.id(),
                    order.requestId(),
                    order.sourceQuoteId(),
                    order.buyerBusinessId(),
                    order.supplierProfileId(),
                    order.sourceDocumentId(),
                    order.status(),
                    new MoneySummary(order.currency(), order.subtotal(), order.taxAmount(), order.total()),
                    new DestinationResponse(
                            order.destinationLabel(), order.destinationLatitude(), order.destinationLongitude()),
                    new DeliveryWindowResponse(order.deliveryWindowStart(), order.deliveryWindowEnd()),
                    order.items().stream().map(OrderItemResponse::from).toList(),
                    order.confirmedByUserId(),
                    order.confirmedAt());
        }
    }

    public record OrderItemResponse(
            UUID id,
            UUID sourceRequestItemId,
            String productCode,
            String description,
            BigDecimal quantity,
            UnitOfMeasure unitOfMeasure,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {

        static OrderItemResponse from(OrderLineItem item) {
            return new OrderItemResponse(
                    item.id(),
                    item.sourceRequestItemId(),
                    item.productCode(),
                    item.description(),
                    item.quantity(),
                    item.unitOfMeasure(),
                    item.unitPrice(),
                    item.lineTotal());
        }
    }

    public record DestinationResponse(String label, double latitude, double longitude) {}

    public record DeliveryWindowResponse(Instant start, Instant end) {}

    public record MoneySummary(String currency, BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total) {}
}
