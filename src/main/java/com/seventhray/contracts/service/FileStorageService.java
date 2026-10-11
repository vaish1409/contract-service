package com.seventhray.contracts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(@Value("${app.storage.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage directory " + root, e);
        }
    }

    public String save(byte[] data, String extension) {
        String stored = UUID.randomUUID() + extension;
        try {
            Files.write(root.resolve(stored), data);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file", e);
        }
        return stored;
    }

    public Resource load(String storedFilename) {
        Path p = root.resolve(storedFilename).normalize();
        if (!p.startsWith(root) || !Files.exists(p)) {
            throw new IllegalStateException("Stored file missing: " + storedFilename);
        }
        return new FileSystemResource(p);
    }

    public void deleteQuietly(String storedFilename) {
        try {
            Files.deleteIfExists(root.resolve(storedFilename).normalize());
        } catch (IOException ignored) {
        }
    }
}
