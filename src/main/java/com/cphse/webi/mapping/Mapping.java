package com.cphse.webi.mapping;

/**
 *
 * @author Henrik Hofmeister <henrik@newdawn.dk>
 */
public interface Mapping {
    public <T> T deserialize(byte[] in,Class<T> clz);
    public byte[] serialize(Object object);
    
    public boolean supportsType(String mimeType);
    
}
