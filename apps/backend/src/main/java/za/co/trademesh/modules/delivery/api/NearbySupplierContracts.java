package za.co.trademesh.modules.delivery.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.delivery.application.NearbySupplierService;

final class NearbySupplierContracts {

    private NearbySupplierContracts() {}

    record NearbySuppliersResponse(List<SupplierResponse> suppliers) {
        static NearbySuppliersResponse from(List<NearbySupplierService.NearbySupplier> suppliers) {
            return new NearbySuppliersResponse(
                    suppliers.stream().map(SupplierResponse::from).toList());
        }
    }

    record SupplierResponse(
            UUID supplierProfileId,
            UUID businessId,
            String displayName,
            BigDecimal averageRating,
            BigDecimal successfulDeliveryRate,
            long distanceMetres,
            long durationSeconds) {

        static SupplierResponse from(NearbySupplierService.NearbySupplier supplier) {
            return new SupplierResponse(
                    supplier.supplierProfileId(),
                    supplier.businessId(),
                    supplier.displayName(),
                    supplier.averageRating(),
                    supplier.successfulDeliveryRate(),
                    supplier.distanceMetres(),
                    supplier.durationSeconds());
        }
    }
}
