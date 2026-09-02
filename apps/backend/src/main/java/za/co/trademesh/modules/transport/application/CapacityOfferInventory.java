package za.co.trademesh.modules.transport.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Atomic capacity operations exposed to the matching module without exposing transport tables. */
public interface CapacityOfferInventory {

    boolean tryReserve(UUID offerId, BigDecimal weightKg, BigDecimal volumeCubicMetres);

    boolean release(UUID offerId, BigDecimal weightKg, BigDecimal volumeCubicMetres);
}
