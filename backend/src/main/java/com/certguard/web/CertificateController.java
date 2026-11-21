package com.certguard.web;

import com.certguard.dto.*;
import com.certguard.entity.CertStatus;
import com.certguard.entity.CertType;
import com.certguard.entity.CertificateFile;
import com.certguard.extraction.ExtractionService;
import com.certguard.service.ApiException;
import com.certguard.service.CertificateService;
import com.certguard.service.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService service;
    private final ExtractionService extraction;
    private final CurrentUser me;

    public CertificateController(CertificateService service, ExtractionService extraction, CurrentUser me) {
        this.service = service;
        this.extraction = extraction;
        this.me = me;
    }

    @GetMapping
    public PageResponse<CertificateDto> list(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) CertType type,
                                             @RequestParam(required = false) CertStatus status,
                                             @RequestParam(defaultValue = "false") boolean archived,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size,
                                             @RequestParam(defaultValue = "expiryDate") String sort,
                                             @RequestParam(defaultValue = "asc") String dir) {
        return service.list(me.id(), q, type, status, archived, page, size, sort, dir);
    }

    /** Reads dates etc. from the file without saving anything. */
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExtractionResult extract(@RequestPart("file") MultipartFile file,
                                    @RequestParam(value = "password", required = false) String password)
            throws IOException {
        if (file.isEmpty()) throw ApiException.badRequest("File is empty");
        return extraction.extract(file.getBytes(), file.getOriginalFilename(), password);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CertificateDto create(@RequestPart("data") @Valid CertificateRequest data,
                                 @RequestPart(value = "file", required = false) MultipartFile file) {
        return service.create(me.id(), data, file);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CertificateDto createJson(@Valid @RequestBody CertificateRequest data) {
        return service.create(me.id(), data, null);
    }

    @GetMapping("/{id}")
    public CertificateDetailDto get(@PathVariable Long id) {
        return service.detail(me.id(), id);
    }

    @PutMapping("/{id}")
    public CertificateDto update(@PathVariable Long id, @Valid @RequestBody CertificateRequest data) {
        return service.update(me.id(), id, data);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(me.id(), id);
    }

    @PostMapping(value = "/{id}/renew", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CertificateDto renew(@PathVariable Long id,
                                @RequestPart("data") @Valid CertificateRequest data,
                                @RequestPart("file") MultipartFile file) {
        return service.renew(me.id(), id, data, file);
    }

    @PostMapping("/{id}/archive")
    public CertificateDto archive(@PathVariable Long id) {
        return service.setArchived(me.id(), id, true);
    }

    @PostMapping("/{id}/unarchive")
    public CertificateDto unarchive(@PathVariable Long id) {
        return service.setArchived(me.id(), id, false);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> download(@PathVariable Long id,
                                           @RequestParam(required = false) Long fileId,
                                           @RequestParam(defaultValue = "false") boolean inline) {
        CertificateFile f = service.file(me.id(), id, fileId);
        // plain ASCII filename for old clients + RFC 5987 filename* for the real (possibly non-ASCII) name
        String ascii = f.getFileName().replaceAll("[^A-Za-z0-9._ -]", "_");
        String encoded = URLEncoder.encode(f.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = (inline ? "inline" : "attachment") + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(f.getMimeType()))
                .contentLength(f.getSizeBytes())
                .body(f.getData());
    }
}
