package za.co.trademesh.modules.delivery.application;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import za.co.trademesh.modules.supplier.application.SupplierDirectory;

@Service
public class NearbySupplierService {

    private final SupplierDirectory suppliers;
    private final SupplierDistanceClient distances;

    public NearbySupplierService(SupplierDirectory suppliers, SupplierDistanceClient distances) {
        this.suppliers = suppliers;
        this.distances = distances;
    }

    public List<NearbySupplier> find(double latitude, double longitude, int limit) {
        if (!Double.isFinite(latitude)
                || latitude < -90
                || latitude > 90
                || !Double.isFinite(longitude)
                || longitude < -180
                || longitude > 180
                || limit < 1
                || limit > 50) {
            throw DeliveryException.invalidNearbySearch();
        }
        List<SupplierDirectory.SearchResult> candidates = suppliers.listRegistered(limit);
        Map<UUID, SupplierDistanceClient.Distance> measured;
        try {
            measured = distances.distances(
                    latitude,
                    longitude,
                    candidates.stream()
                            .map(candidate -> new SupplierDistanceClient.Destination(
                                    candidate.supplierProfileId(), candidate.registeredAddress()))
                            .toList());
        } catch (RuntimeException providerFailure) {
            throw DeliveryException.distanceProviderUnavailable();
        }
        return candidates.stream()
                .map(candidate -> nearby(candidate, measured.get(candidate.supplierProfileId())))
                .filter(candidate -> candidate.distanceMetres() != null)
                .sorted(Comparator.comparing(NearbySupplier::distanceMetres)
                        .thenComparing(NearbySupplier::averageRating, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(NearbySupplier::displayName)
                        .thenComparing(NearbySupplier::supplierProfileId))
                .limit(limit)
                .toList();
    }

    private static NearbySupplier nearby(
            SupplierDirectory.SearchResult supplier, SupplierDistanceClient.Distance distance) {
        return new NearbySupplier(
                supplier.supplierProfileId(),
                supplier.businessId(),
                supplier.displayName(),
                supplier.averageRating(),
                supplier.successfulDeliveryRate(),
                distance == null ? null : distance.metres(),
                distance == null ? null : distance.durationSeconds());
    }

    public record NearbySupplier(
            UUID supplierProfileId,
            UUID businessId,
            String displayName,
            BigDecimal averageRating,
            BigDecimal successfulDeliveryRate,
            Long distanceMetres,
            Long durationSeconds) {}
}
