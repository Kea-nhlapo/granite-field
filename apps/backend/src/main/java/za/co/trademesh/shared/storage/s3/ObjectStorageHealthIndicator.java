package za.co.trademesh.shared.storage.s3;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports whether the object store is actually reachable with the configured credentials.
 *
 * <p>The storage client is lazy, so an endpoint, region, bucket or key that is wrong is invisible
 * until the first upload - which, before this existed, meant a user found it. This belongs to the
 * readiness group rather than liveness: a storage outage means the deployment should not be
 * released, but it is not a reason to restart a healthy process.
 */
@Component("objectStorage")
class ObjectStorageHealthIndicator implements HealthIndicator {

    private final S3CompatibleObjectStorage storage;

    ObjectStorageHealthIndicator(S3CompatibleObjectStorage storage) {
        this.storage = storage;
    }

    @Override
    public Health health() {
        try {
            if (storage.bucketReachable()) {
                return Health.up().build();
            }
            return Health.down().withDetail("reason", "bucket-not-found").build();
        } catch (Exception unreachable) {
            // The failure carries the endpoint and can carry credentials; never publish it.
            return Health.down().withDetail("reason", "unreachable").build();
        }
    }
}
