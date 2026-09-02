package za.co.trademesh.modules.supplier.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.supplier.domain.SupplierInvitationRepository;
import za.co.trademesh.modules.supplier.domain.SupplierProfileStatus;

@Service
public class SupplierDirectory {

    private final SupplierInvitationRepository suppliers;
    private final SupplierSearchCatalog searchCatalog;

    public SupplierDirectory(SupplierInvitationRepository suppliers, SupplierSearchCatalog searchCatalog) {
        this.suppliers = suppliers;
        this.searchCatalog = searchCatalog;
    }

    @Transactional(readOnly = true)
    public Optional<SupplierReference> find(UUID supplierProfileId) {
        return suppliers
                .findProfileById(supplierProfileId)
                .map(profile -> new SupplierReference(
                        profile.id(), profile.status() == SupplierProfileStatus.REGISTERED, profile.businessId()));
    }

    @Transactional(readOnly = true)
    public java.util.List<SearchResult> search(String rawQuery, int limit) {
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.isBlank() || query.length() > 200 || limit < 1 || limit > 50) {
            throw new IllegalArgumentException("Invalid supplier search");
        }
        return searchCatalog.search(query, limit).stream()
                .map(candidate -> new SearchResult(
                        candidate.supplierProfileId(),
                        candidate.businessId(),
                        candidate.displayName(),
                        candidate.registeredAddress(),
                        candidate.averageRating(),
                        candidate.successfulDeliveryRate()))
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<SearchResult> listRegistered(int limit) {
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("Invalid supplier limit");
        }
        return searchCatalog.listRegistered(limit).stream()
                .map(candidate -> new SearchResult(
                        candidate.supplierProfileId(),
                        candidate.businessId(),
                        candidate.displayName(),
                        candidate.registeredAddress(),
                        candidate.averageRating(),
                        candidate.successfulDeliveryRate()))
                .toList();
    }

    public record SupplierReference(UUID supplierProfileId, boolean registered, UUID businessId) {}

    public record SearchResult(
            UUID supplierProfileId,
            UUID businessId,
            String displayName,
            String registeredAddress,
            java.math.BigDecimal averageRating,
            java.math.BigDecimal successfulDeliveryRate) {}
}
