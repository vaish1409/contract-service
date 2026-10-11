package com.seventhray.contracts.controller;

import com.seventhray.contracts.dto.*;
import com.seventhray.contracts.model.Contract;
import com.seventhray.contracts.model.ContractStatus;
import com.seventhray.contracts.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContractResponse> create(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {
        Contract c = service.create(file, title);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(c.getId()).toUri();
        return ResponseEntity.created(location).body(ContractResponse.from(c, true));
    }

    @GetMapping("/{id}")
    public ContractResponse get(@PathVariable Long id) {
        return ContractResponse.from(service.get(id), true);
    }

    @GetMapping
    public List<ContractResponse> list(@RequestParam(value = "status", required = false) ContractStatus status) {
        return service.list(status).stream().map(c -> ContractResponse.from(c, false)).toList();
    }

    @GetMapping(value = "/{id}/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> text(@PathVariable Long id) {
        String text = service.get(id).getExtractedText();
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(text);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Contract c = service.get(id);
        Resource r = service.file(id);
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (c.getContentType() != null) {
            try { type = MediaType.parseMediaType(c.getContentType()); } catch (Exception ignored) { }
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(c.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(r);
    }

    @PatchMapping("/{id}/status")
    public ContractResponse updateStatus(@PathVariable Long id,
                                         @Valid @RequestBody StatusUpdateRequest req) {
        return ContractResponse.from(service.updateStatus(id, req.status()), true);
    }

    @PostMapping("/{id}/ask")
    public AnswerResponse ask(@PathVariable Long id, @Valid @RequestBody QuestionRequest req) {
        return service.ask(id, req.question());
    }
}
