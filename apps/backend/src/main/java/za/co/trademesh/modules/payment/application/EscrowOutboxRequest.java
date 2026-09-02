package za.co.trademesh.modules.payment.application;

import java.util.UUID;

record EscrowOutboxRequest(UUID transactionId, int pollSequence) {

    static final String SUBMIT_TYPE = "ESCROW_MOMO_SUBMIT";
    static final String POLL_TYPE = "ESCROW_MOMO_POLL";
    static final int SCHEMA_VERSION = 1;
}
