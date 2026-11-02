package com.orangejd.server.foundation.external;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

/** Opt-in local acceptance guard at the HTTP boundary; failed attempts count too. No prompt/key logging. */
public final class AiAcceptanceBudget {
    private AiAcceptanceBudget() {}
    /** Read-only scheduling hint for optional work. beforeRequest remains the authoritative guard. */
    public static boolean hasCapacity() {
        String file=System.getProperty("orange.acceptance.ledger",System.getenv("CUSTOMER_ACCEPTANCE_LEDGER"));
        if(file==null||file.isBlank())return true;
        try {
            int limit=Integer.parseInt(System.getProperty("orange.acceptance.limit",System.getenv().getOrDefault("CUSTOMER_ACCEPTANCE_LIMIT","50")));
            Path path=Path.of(file).toAbsolutePath().normalize();
            return limit>=1 && limit<=50 && (!Files.exists(path) || Files.size(path)<=65536 && Files.readString(path).lines().filter(s->!s.isBlank()).count()<limit);
        } catch(Exception ex) {return false;}
    }
    public static synchronized void beforeRequest(String kind) {
        String file=System.getProperty("orange.acceptance.ledger",System.getenv("CUSTOMER_ACCEPTANCE_LEDGER"));
        if(file==null||file.isBlank())return;
        Path path=Path.of(file).toAbsolutePath().normalize();
        int limit=Integer.parseInt(System.getProperty("orange.acceptance.limit",System.getenv().getOrDefault("CUSTOMER_ACCEPTANCE_LIMIT","50")));
        if(limit<1||limit>50)throw new IllegalStateException("INVALID_ACCEPTANCE_BUDGET");
        try {
            Files.createDirectories(path.getParent());
            try(var channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);var lock=channel.lock()) {
                if(channel.size()>65536)throw new IllegalStateException("ACCEPTANCE_LEDGER_INVALID");
                ByteBuffer buffer=ByteBuffer.allocate((int)channel.size());while(buffer.hasRemaining()&&channel.read(buffer)>=0){}buffer.flip();
                long count=StandardCharsets.UTF_8.decode(buffer).toString().lines().filter(s->!s.isBlank()).count();
                if(count>=limit)throw new IllegalStateException("ACCEPTANCE_BUDGET_EXHAUSTED");
                String row="{\"attempt\":"+(count+1)+",\"kind\":\""+kind+"\",\"at\":\""+Instant.now()+"\"}\n";
                channel.position(channel.size());var bytes=StandardCharsets.UTF_8.encode(row);while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
            }
        } catch(java.io.IOException ex) {throw new IllegalStateException("ACCEPTANCE_LEDGER_UNAVAILABLE",ex);}
    }
}
