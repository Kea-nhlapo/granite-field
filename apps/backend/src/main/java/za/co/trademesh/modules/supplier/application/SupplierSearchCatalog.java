package za.co.trademesh.modules.supplier.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SupplierSearchCatalog {

    List<Candidate> search(String query, int limit);

    List<Candidate> listRegistered(int limit);

    record Candidate(
            UUID supplierProfileId,
            UUID businessId,
            String displayName,
            String registeredAddress,
            BigDecimal averageRating,
            BigDecimal successfulDeliveryRate) {}
}
