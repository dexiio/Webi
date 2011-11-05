package com.cphse.webi.mapping;

import com.thoughtworks.xstream.XStream;

public class XMLMapping implements Mapping {
    private final XStream xstream = new XStream();

    public <T> T deserialize(byte[] in,Class<T> clz) {
        return (T) xstream.fromXML(new String(in));
    }

    public byte[] serialize(Object object) {
        return xstream.toXML(object).getBytes();
    }
    
    public boolean supportsType(String mimeType) {
        return mimeType.contains("xml");
    }

    public String getMimeType() {
        return "text/xml";
    }
    
}
