package com.cphse.webi.mapping;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jackson.map.ObjectMapper;

public class JSONMapper implements Mapping {
    
    private ObjectMapper om = new ObjectMapper();

    public byte[] serialize(Object object) {
        try {
            return om.writeValueAsBytes(object);
        } catch (IOException ex) {
            Logger.getLogger(JSONMapper.class.getName()).
                    log(Level.SEVERE, null, ex);
        }
        return new byte[0];
    }

    public <T> T deserialize(byte[] in,Class<T> clz) {
        try {
            return om.readValue(in, clz);
        } catch (IOException ex) {
            Logger.getLogger(JSONMapper.class.getName()).
                    log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public boolean supportsType(String mimeType) {
        return mimeType.contains("json");
    }

    public String getMimeType() {
        return "application/json";
    }
    
}
