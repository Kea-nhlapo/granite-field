package za.co.trademesh.modules.delivery.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.trademesh.modules.delivery.application.NearbySupplierService;

@RestController
@RequestMapping("/api/suppliers")
public class NearbySupplierController {

    private final NearbySupplierService suppliers;

    public NearbySupplierController(NearbySupplierService suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping("/nearby")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    NearbySupplierContracts.NearbySuppliersResponse nearby(
            @RequestParam("lat") double latitude,
            @RequestParam("lng") double longitude,
            @RequestParam(defaultValue = "10") int limit) {
        return NearbySupplierContracts.NearbySuppliersResponse.from(suppliers.find(latitude, longitude, limit));
    }
}
