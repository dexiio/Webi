package com.vonhof.webi.postgres.jdbc;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class PSQLArray<T> implements Array {

    private final T[] inner;
    private final int type;
    private final String typeName;

    public PSQLArray(T[] inner, String typeName, int type) {
        this.inner = inner;
        this.type = type;
        this.typeName = typeName;
    }

    public PSQLArray(Set<T> elements, String typeName, int type) {
        this((T[]) elements.toArray(), typeName, type);
    }

    public String getBaseTypeName() throws SQLException {
        return typeName;
    }

    public int getBaseType() throws SQLException {
        return type;
    }

    public Object getArray() throws SQLException {
        return inner;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < inner.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(inner[i]).append("\"");
        }

        sb.append("}");
        return sb.toString();
    }

    public Object getArray(Map<String, Class<?>> map) throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Object getArray(long index, int count) throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Object getArray(long index, int count, Map<String, Class<?>> map) throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public java.sql.ResultSet getResultSet() throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public java.sql.ResultSet getResultSet(Map<String, Class<?>> map) throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public java.sql.ResultSet getResultSet(long index, int count) throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public java.sql.ResultSet getResultSet(long index, int count, Map<String, Class<?>> map) throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void free() throws SQLException {
    }
}
