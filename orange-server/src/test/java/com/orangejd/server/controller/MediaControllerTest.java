package com.orangejd.server.controller;

import com.orangejd.server.service.impl.OssObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaControllerTest {
    @Test
    void redirectsManagedObjectToShortLivedOssUrl() throws Exception {
        OssObjectStorage storage = mock(OssObjectStorage.class);
        String key = "cover/123e4567-e89b-12d3-a456-426614174000.png";
        URL signed = new URL("https://bucket.oss-cn-shanghai.aliyuncs.com/" + key + "?signed=true");
        when(storage.signedGetUrl(key)).thenReturn(signed);

        var response = new MediaController(storage).get("cover", "123e4567-e89b-12d3-a456-426614174000.png");

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(signed.toString(), response.getHeaders().getLocation().toString());
    }

    @Test
    void rejectsUnmanagedObjectKey() {
        MediaController controller = new MediaController(mock(OssObjectStorage.class));
        assertThrows(ResponseStatusException.class, () -> controller.get("private", "secret.txt"));
    }

    @Test
    void redirectsOwnedManagedObject() throws Exception {
        OssObjectStorage storage = mock(OssObjectStorage.class);
        String file = "123e4567-e89b-12d3-a456-426614174000.mp4";
        String key = "video/42/" + file;
        URL signed = new URL("https://bucket.oss-cn-shanghai.aliyuncs.com/" + key + "?signed=true");
        when(storage.signedGetUrl(key)).thenReturn(signed);

        var response = new MediaController(storage).getOwned("video", "42", file);

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(signed.toString(), response.getHeaders().getLocation().toString());
    }
}
