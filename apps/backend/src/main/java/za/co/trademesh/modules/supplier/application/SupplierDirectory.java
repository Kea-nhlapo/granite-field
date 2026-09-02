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

    public SupplierDirectory(SupplierInvitationRepository suppliers) {
        this.suppliers = suppliers;
    }

    @Transactional(readOnly = true)
    public Optional<SupplierReference> find(UUID supplierProfileId) {
        return suppliers
                .findProfileById(supplierProfileId)
                .map(profile -> new SupplierReference(
                        profile.id(), profile.status() == SupplierProfileStatus.REGISTERED, profile.businessId()));
    }

    public record SupplierReference(UUID supplierProfileId, boolean registered, UUID businessId) {}
}
