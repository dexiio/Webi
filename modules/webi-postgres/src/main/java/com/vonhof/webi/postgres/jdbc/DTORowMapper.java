package com.vonhof.webi.postgres.jdbc;

import com.vonhof.babelshark.BabelShark;
import com.vonhof.babelshark.annotation.TypeResolver;
import com.vonhof.babelshark.exception.MappingException;
import com.vonhof.babelshark.node.SharkType;
import com.vonhof.babelshark.reflect.ClassInfo;
import com.vonhof.babelshark.reflect.FieldInfo;
import com.vonhof.babelshark.reflect.MethodInfo;
import java.io.ByteArrayInputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class DTORowMapper<T> implements RowMapper<T> {
    
    private final DTOWrapper<T> wrapper;

    public DTORowMapper(Class<T> clz) {
        wrapper = DTOWrapper.from(clz);
    }
    

    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        try {
            final ResultSetMetaData md = rs.getMetaData();
            T instance = null;
            
            DTOWrapper<T> dtoWrapper = this.wrapper;
            
            //Use type resolver if available
            if (dtoWrapper.getInfo().hasAnnotation(TypeResolver.class)) {
                TypeResolver type = dtoWrapper.getInfo().getAnnotation(TypeResolver.class);
                rs.getString(type.field());
                FieldInfo field = dtoWrapper.getField(type.field());
                int ix = getIndexOfColumn(md, type.field());
                Object value = JdbcUtils.getResultSetValue(rs, ix, field.getType().getType());
                value = readValue(field, value);
                MethodInfo resolverMethod = dtoWrapper.getInfo().getMethod(type.resolverMethod(), field.getType());
                
                Class<T> resolvedType = (Class<T>) resolverMethod.invoke(null,value);
                dtoWrapper = DTOWrapper.from(resolvedType);
                instance = resolvedType.newInstance();
            }
            
            if (instance == null)
                instance = dtoWrapper.getInfo().newInstance();
            
            for(int i = 1;i <= md.getColumnCount();i++) {
                String columnName = md.getColumnName(i);
                FieldInfo field = dtoWrapper.getField(columnName);
                if (field == null) {
                   continue; 
                }
                field.forceAccessible();
                Object value = JdbcUtils.getResultSetValue(rs, i, field.getType().getType());
                
                value = readValue(field, value);
                
                field.set(instance,value);
            }
            
            return instance;
        } catch (Exception ex) {
            throw new SQLException(ex);
        }
    }
    
    private int getIndexOfColumn(ResultSetMetaData md,String columnName) throws SQLException {
        for(int i = 1;i <= md.getColumnCount();i++) {
            String name = md.getColumnName(i);
            if (name.equals(columnName))
                return i;
        }
        return -1;
    }
    
    private Object readValue(FieldInfo field, Object value) throws IOException, ClassNotFoundException, MappingException {
        if (field.getType().isEnum()) {
            if (value != null) 
                return Enum.valueOf(field.getType().getType(),(String)value);
        } else {
            if (!field.getType().isPrimitive() 
                    && value instanceof String) {
                value = BabelShark.read((String)value,field.getType());
            } 
            if (value instanceof byte[] && field.getType().inherits(Externalizable.class)) {
                ByteArrayInputStream bin = new ByteArrayInputStream(((byte[])value));
                ObjectInputStream oin = new ObjectInputStream(bin);
                value = oin.readObject();
                oin.close();
                bin.close();
            }
        }
        return value;
    }

}
