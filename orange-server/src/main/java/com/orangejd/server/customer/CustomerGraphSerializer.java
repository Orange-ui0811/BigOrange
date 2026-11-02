package com.orangejd.server.customer;

import org.bsc.langgraph4j.serializer.StateSerializer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** JSON values only, with a byte limit; never deserialize arbitrary Java objects. */
final class CustomerGraphSerializer extends StateSerializer<CustomerGraphState> {
    static final int MAX_BYTES=256*1024;
    private final CustomerJson json;
    CustomerGraphSerializer(CustomerJson json) {super(CustomerGraphState::new);this.json=json;}
    @Override public void writeData(Map<String,Object> data,ObjectOutput out) throws IOException {
        byte[] bytes=json.write(data).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new IOException("CHECKPOINT_TOO_LARGE");
        out.writeInt(bytes.length);out.write(bytes);
    }
    @Override public Map<String,Object> readData(ObjectInput in) throws IOException {
        int size=in.readInt();if(size<0 || size>MAX_BYTES)throw new IOException("INVALID_CHECKPOINT_SIZE");
        byte[] bytes=new byte[size];in.readFully(bytes);
        return json.read(new String(bytes,StandardCharsets.UTF_8));
    }
}
