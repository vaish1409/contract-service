package com.seventhray.contracts.service;

import com.seventhray.contracts.dto.AnswerResponse;
import com.seventhray.contracts.exception.InvalidRequestException;
import com.seventhray.contracts.exception.NotFoundException;
import com.seventhray.contracts.model.Contract;
import com.seventhray.contracts.model.ContractStatus;
import com.seventhray.contracts.parser.DocumentParser;
import com.seventhray.contracts.repository.ContractRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class ContractService {

    private final ContractRepository repo;
    private final FileStorageService storage;
    private final DocumentParser parser;
    private final QaService qa;

    public ContractService(ContractRepository repo, FileStorageService storage,
                           DocumentParser parser, QaService qa) {
        this.repo = repo;
        this.storage = storage;
        this.parser = parser;
        this.qa = qa;
    }

    @Transactional
    public Contract create(MultipartFile file, String title) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("A non-empty file is required (form field name: file)");
        }
        String original = sanitizeFilename(file.getOriginalFilename());
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new InvalidRequestException("Could not read the uploaded file");
        }

        String text = parser.extractText(original, data);
        if (text.isBlank()) {
            throw new InvalidRequestException(
                    "No readable text found. Scanned/image-only documents are not supported.");
        }

        String ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        String stored = storage.save(data, ext);
        try {
            Contract c = new Contract();
            c.setTitle(resolveTitle(title, original));
            c.setOriginalFilename(original);
            c.setContentType(file.getContentType());
            c.setStoredFilename(stored);
            c.setSizeBytes(data.length);
            c.setExtractedText(text);
            c.setStatus(ContractStatus.DRAFT);
            c.addHistory(null, ContractStatus.DRAFT);
            return repo.save(c);
        } catch (RuntimeException e) {
            storage.deleteQuietly(stored);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Contract get(Long id) {
        Contract c = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Contract " + id + " not found"));
        c.getHistory().size();
        return c;
    }

    @Transactional(readOnly = true)
    public List<Contract> list(ContractStatus status) {
        return status == null
                ? repo.findAllByOrderByCreatedAtDesc()
                : repo.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional
    public Contract updateStatus(Long id, ContractStatus target) {
        Contract c = get(id);
        ContractStatus current = c.getStatus();
        if (current == target) {
            throw new InvalidRequestException("Contract is already " + current);
        }
        if (!current.canMoveTo(target)) {
            String hint = current.next() == null
                    ? "it is final and cannot change"
                    : "the only allowed next status is " + current.next();
            throw new InvalidRequestException(
                    "Cannot move from " + current + " to " + target + "; " + hint);
        }
        c.setStatus(target);
        c.addHistory(current, target);
        return repo.save(c);
    }

    @Transactional(readOnly = true)
    public AnswerResponse ask(Long id, String question) {
        Contract c = get(id);
        return qa.answer(question.trim(), c.getExtractedText());
    }

    @Transactional(readOnly = true)
    public Resource file(Long id) {
        return storage.load(get(id).getStoredFilename());
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) throw new InvalidRequestException("File has no name");
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).trim();
        if (!base.contains(".")) throw new InvalidRequestException("File must have an extension");
        return base;
    }

    private static String resolveTitle(String title, String filename) {
        String t = (title == null || title.isBlank())
                ? filename.substring(0, filename.lastIndexOf('.'))
                : title.trim();
        if (t.length() > 200) t = t.substring(0, 200);
        return t;
    }
}
