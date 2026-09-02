package za.co.trademesh.modules.procurement.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProcurementRepository {

    boolean saveRequest(ProductRequest request, UUID clientRequestId);

    Optional<ProductRequest> findRequest(UUID buyerBusinessId, UUID requestId);

    Optional<ProductRequest> findRequestForUpdate(UUID buyerBusinessId, UUID requestId);

    Optional<ProductRequest> findRequestByClientRequestId(UUID buyerBusinessId, UUID clientRequestId);

    boolean markRequestQuoted(UUID requestId, Instant now);

    boolean cancelRequest(UUID requestId, Instant now);

    boolean markRequestOrdered(UUID requestId, Instant now);

    boolean saveQuote(SupplierQuote quote, UUID clientRequestId);

    Optional<SupplierQuote> findQuote(UUID buyerBusinessId, UUID quoteId);

    Optional<SupplierQuote> findQuoteByClientRequestId(UUID buyerBusinessId, UUID requestId, UUID clientRequestId);

    Optional<SupplierQuote> findQuoteBySourceDocument(UUID sourceDocumentId);

    boolean acceptQuote(UUID quoteId);

    boolean saveOrder(ConfirmedOrder order, UUID confirmationRequestId);

    Optional<ConfirmedOrder> findOrder(UUID buyerBusinessId, UUID orderId);

    Optional<ConfirmedOrder> findOrderByConfirmationRequestId(UUID buyerBusinessId, UUID confirmationRequestId);

    Optional<ConfirmedOrder> findOrderByQuoteId(UUID quoteId);
}
