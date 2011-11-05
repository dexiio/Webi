package com.cphse.webi.mapping;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

abstract public class TableMapping implements Mapping {
    ObjectInspector objectInspector = new ObjectInspector();
    

    public <T> T deserialize(byte[] in,
            Class<T> clz) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public final byte[] serialize(Object obj) {
        if (!(obj instanceof Collection)) //Need a collection to make a list
            return new byte[0];
        Collection list = (Collection) obj;
        
        if (list.isEmpty()) //Empty list gives no result
            return new byte[0];
        
        Object first = list.iterator().next();
        
        Map<String, Field> fieldMap = objectInspector.getFieldMap(first);
        StringBuilder sb = new StringBuilder();
        sb.append(writeHeader(fieldMap.keySet()));
        for(Object elm:list) {
            sb.append(writeRow(objectInspector.getValues(elm)));
        }
        
        sb.append(writeFooter());
        
        return sb.toString().getBytes();
        
    }
    abstract public String writeHeader(Collection<String> fieldNames);
    abstract public String writeFooter();
    abstract public String writeRow(Collection<Object> values);

}
