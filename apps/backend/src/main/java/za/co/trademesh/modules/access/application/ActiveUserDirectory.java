package za.co.trademesh.modules.access.application;

import java.util.UUID;

/** Minimal account boundary for modules that must validate an expected participant. */
public interface ActiveUserDirectory {
    boolean isActive(UUID userId);
}
