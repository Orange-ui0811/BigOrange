package com.orangejd.server.controller;

import com.orangejd.server.service.impl.OssObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

@Controller
@ConditionalOnProperty(name = "orange.oss.enabled", havingValue = "true")
public class MediaController {
    private static final Set<String> DIRECTORIES = Set.of("general", "cover", "detail", "avatar", "video");
    private static final Pattern FILE_NAME = Pattern.compile(
            "^[0-9a-fA-F-]{36}\\.(png|jpg|gif|webp|mp4|mov|m4v|webm)$");
    private final OssObjectStorage storage;

    public MediaController(OssObjectStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/media/{directory}/{filename:.+}")
    public ResponseEntity<Void> get(@PathVariable String directory, @PathVariable String filename) {
        if (!DIRECTORIES.contains(directory) || !FILE_NAME.matcher(filename).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        URI location = URI.create(storage.signedGetUrl(directory + "/" + filename).toString());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
