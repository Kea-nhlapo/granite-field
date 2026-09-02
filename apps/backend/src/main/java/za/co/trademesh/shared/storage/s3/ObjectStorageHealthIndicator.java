package za.co.trademesh.shared.storage.s3;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 *
 * <p>Switched off in the test suite, which has no object store and should not be made to start one:
 * a check that reports the truth about the environment it runs in would report DOWN there, and every
 * test that asserts the application is healthy would fail for a reason that has nothing to do with
 * the application. Cloud Readiness covers this against real S3 instead.
 */
@ConditionalOnProperty(name = "management.health.object-storage.enabled", matchIfMissing = true)
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
