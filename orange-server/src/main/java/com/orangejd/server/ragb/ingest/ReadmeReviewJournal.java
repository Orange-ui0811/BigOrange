package com.orangejd.server.ragb.ingest;

import com.fasterxml.jackson.databind.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Durable, cross-process attempt reservation. Crashed/failed attempts still consume the run limit. */
public final class ReadmeReviewJournal {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path file;
    private final String runId;
    private final boolean simulated;
    public ReadmeReviewJournal(Path file, String runId, boolean simulated) {
        this.file = file.toAbsolutePath().normalize(); this.runId = Objects.requireNonNull(runId); this.simulated = simulated;
        if (runId.isBlank() || runId.length()>128) throw new IllegalArgumentException("Invalid run ID");
    }
    public String runId() { return runId; }
    public boolean reserve(String key, String model, int input, int output, int maxRequests, long maxTokens) throws Exception {
        synchronized (ReadmeReviewJournal.class) {
            Files.createDirectories(file.getParent());
            try (var channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                if (channel.size()>8*1024*1024) throw new IllegalStateException("Journal too large");
                ByteBuffer buffer = ByteBuffer.allocate((int)channel.size());
                while (buffer.hasRemaining() && channel.read(buffer)>=0) { }
                buffer.flip(); int attempts=0; long tokens=0;
                for (String row : StandardCharsets.UTF_8.decode(buffer).toString().split("\n")) if (!row.isBlank()) {
                    JsonNode node = JSON.readTree(row);
                    if (node==null || !node.isObject() || !node.hasNonNull("event") || !node.hasNonNull("runId"))
                        throw new IllegalStateException("Malformed journal");
                    if (runId.equals(node.path("runId").asText()) && "START".equals(node.path("event").asText())) {
                        attempts++; tokens += node.path("inputUpperBound").asLong() + node.path("outputLimit").asLong();
                    }
                }
                if (attempts>=maxRequests || tokens+input+output>maxTokens) return false;
                Map<String,Object> event = event("START", key);
                event.put("model",model);event.put("inputUpperBound",input);event.put("outputLimit",output);
                append(channel,event); return true;
            }
        }
    }
    public void outcome(String key, String status, Map<String, Integer> usage) throws Exception {
        synchronized (ReadmeReviewJournal.class) {
            Files.createDirectories(file.getParent());
            try (var channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);
                 var lock=channel.lock()) {
                Map<String,Object> event=event(status,key);event.put("usage",usage);append(channel,event);
            }
        }
    }
    private Map<String,Object> event(String kind,String key) {
        Map<String,Object> row=new LinkedHashMap<>();row.put("event",kind);row.put("runId",runId);
        row.put("cacheKey",key);row.put("simulated",simulated);row.put("at",Instant.now().toString());return row;
    }
    private void append(FileChannel channel,Map<String,Object> event) throws Exception {
        channel.position(channel.size());ByteBuffer row=StandardCharsets.UTF_8.encode(JSON.writeValueAsString(event)+"\n");
        while(row.hasRemaining())channel.write(row);channel.force(true);
    }
}
