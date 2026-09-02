package za.co.trademesh.shared.storage.api;

import java.io.IOException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import za.co.trademesh.shared.security.AuthorizationService;
import za.co.trademesh.shared.storage.FileCategory;
import za.co.trademesh.shared.storage.FileStorageService;
import za.co.trademesh.shared.storage.StorageException;

@RestController
@RequestMapping("/api/businesses/{businessId}/files")
public class FileStorageController {

    private final FileStorageService storage;
    private final AuthorizationService authorizationService;

    public FileStorageController(FileStorageService storage, AuthorizationService authorizationService) {
        this.storage = storage;
        this.authorizationService = authorizationService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'SUPPLIER', 'ADMINISTRATOR')")
    ResponseEntity<FileStorageContracts.FileMetadataResponse> upload(
            @PathVariable UUID businessId,
            @RequestParam FileCategory category,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        authorizationService.requireBusinessAccess(authentication, businessId);
        UUID userId = authorizationService.authenticatedUserId(authentication);
        try {
            var stored = storage.upload(
                    businessId, userId, category, file.getOriginalFilename(), file.getContentType(), file.getBytes());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(FileStorageContracts.FileMetadataResponse.from(stored));
        } catch (IOException unreadable) {
            throw StorageException.unreadableUpload(unreadable);
        }
    }

    @GetMapping("/{fileId}")
    FileStorageContracts.FileMetadataResponse metadata(
            @PathVariable UUID businessId, @PathVariable UUID fileId, Authentication authentication) {
        authorizationService.requireBusinessAccess(authentication, businessId);
        return FileStorageContracts.FileMetadataResponse.from(storage.getMetadata(businessId, fileId));
    }

    @GetMapping("/{fileId}/download")
    ResponseEntity<FileStorageContracts.DownloadAccessResponse> download(
            @PathVariable UUID businessId, @PathVariable UUID fileId, Authentication authentication) {
        authorizationService.requireBusinessAccess(authentication, businessId);
        var access = storage.createDownloadAccess(businessId, fileId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(FileStorageContracts.DownloadAccessResponse.from(access));
    }
}
