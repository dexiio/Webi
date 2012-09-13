package com.vonhof.webi.postgres.jdbc;

import com.vonhof.babelshark.BabelShark;
import com.vonhof.babelshark.reflect.FieldInfo;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.ObjectOutputStream;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class DTOSqlParameterSource implements SqlParameterSource {
    
    private final DTOWrapper<?> wrapper;
    private final Object instance;
    private final Map<String,Object> values = new HashMap<String,Object>();
    
    public DTOSqlParameterSource(Object instance) {
        this.wrapper = DTOWrapper.from(instance.getClass());
        this.instance = instance;
        readValues();
    }
    
    private void readValues() {
        for(Entry<String,FieldInfo> entry:wrapper.getFields().entrySet()) {
            FieldInfo field = entry.getValue();
            field.forceAccessible();
            try {
                Object value = field.get(instance);
                if (value instanceof Enum) {
                    value = ((Enum)value).name();
                }
                int sqlType = wrapper.getSqlType(entry.getKey());
                    
                if (sqlType == Types.VARCHAR 
                        && value != null 
                        && !field.getType().isPrimitive()) {
                    value = BabelShark.writeToString(value,"json");
                }
                if (sqlType == SqlTypeValue.TYPE_UNKNOWN 
                        && value instanceof Externalizable) {
                    final ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    final ObjectOutputStream oout = new ObjectOutputStream(bout);
                    oout.writeObject(value);
                    oout.close();
                    bout.close();
                    value = bout.toByteArray();
                }
                values.put(entry.getKey(),value);
            } catch (Exception ex) {
                Logger.getLogger(DTOSqlParameterSource.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    

    public boolean hasValue(String name) {
        return values.containsKey(name);
    }

    public Object getValue(String name) throws IllegalArgumentException {
        return values.get(name);
    }

    public int getSqlType(String paramName) {
        return wrapper.getSqlType(paramName);
    }

    public String getTypeName(String paramName) {
        return null;
    }

}
