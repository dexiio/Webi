package com.vonhof.webi.postgres.jdbc;

import com.vonhof.babelshark.annotation.Ignore;
import com.vonhof.babelshark.annotation.Name;
import com.vonhof.babelshark.reflect.ClassInfo;
import com.vonhof.babelshark.reflect.FieldInfo;
import java.io.Externalizable;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.StatementCreatorUtils;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class DTOWrapper<T> {
    
    public static <T> DTOWrapper<T> from(Class<T> clz) {
        return new DTOWrapper<T>(clz);
    }
    
    private final ClassInfo<T> info;
    private final Map<String,FieldInfo> fields = new HashMap<String, FieldInfo>();
    private final Map<String,Integer> parmTypes = new HashMap<String, Integer>();

    private DTOWrapper(Class<T> clz) {
        this.info = ClassInfo.from(clz);
        readFields();
    }
    
    private void readFields() {
        for(Map.Entry<String,FieldInfo> entry:info.getFields().entrySet()) {
            FieldInfo field = entry.getValue();
            
            //if (field.hasAnnotation(Ignore.class)) 
            //    continue;
            
            String name = field.hasAnnotation(Name.class) ? field.getAnnotation(Name.class).value() : field.getName();
            if (name.isEmpty())
                name = field.getName();
            fields.put(name,field);
            
            Class raw = entry.getValue().getType().getType();
            int sqlParmType = StatementCreatorUtils.javaTypeToSqlParameterType(raw);
            
            if (sqlParmType == SqlTypeValue.TYPE_UNKNOWN 
                    && !entry.getValue().getType().isPrimitive()) {
                if (!entry.getValue().getType().inherits(Externalizable.class)) {
                    sqlParmType = Types.VARCHAR;
                }
            }
            parmTypes.put(name, sqlParmType);
        }
    }

    public Map<String, FieldInfo> getFields() {
        return fields;
    }

    public int getSqlType(String paramName) {
        return parmTypes.get(paramName);
    }

    public ClassInfo<T> getInfo() {
        return info;
    }

    public FieldInfo getField(String fieldName) {
        return fields.get(fieldName);
    }
    
}
