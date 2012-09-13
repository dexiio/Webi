package com.vonhof.webi.postgres.dao;

import com.vonhof.webi.postgres.jdbc.DTORowMapper;
import com.vonhof.webi.postgres.jdbc.DTOSqlParameterSource;
import com.vonhof.webi.postgres.jdbc.DTOWrapper;
import com.vonhof.webi.postgres.jdbc.JdbcQuery;
import com.vonhof.babelshark.annotation.Ignore;
import com.vonhof.babelshark.reflect.FieldInfo;
import com.vonhof.webi.db.Query.Select;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import javax.inject.Inject;
import javax.sql.DataSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class BaseDAO<T> {
    
    @Inject
    protected DataSource ds;
    
    @Inject
    protected JdbcTemplate jdbcTemplate;
    
    @Inject
    protected SimpleJdbcTemplate simpleJdbcTemplate;
    
    private final String defaultTableName;
    private final Class<T> defaultRowClass;

    public BaseDAO(String defaultTableName, Class<T> defaultRowClass) {
        this.defaultTableName = defaultTableName;
        this.defaultRowClass = defaultRowClass;
    }
    
    public boolean insert(T obj) {
        return insert(obj, defaultTableName);
    }
    
    public boolean update(T obj) {
        return update(obj, defaultTableName);
    }
    
    public boolean delete(UUID id) {
        return delete(id, defaultTableName);
    }
    
    public T get(UUID id) {
        return get(id, defaultTableName, defaultRowClass);
    }
    
    public ResultSet<T> search(Select q) {
        return search(q, defaultRowClass);
    }
    
    public boolean hasAny(Select q) {
        q.limit(0,1);
        return !search(q, defaultRowClass).isEmpty();
    }
    
    protected boolean insert(Object obj,String tableName) {
        
        final DTOWrapper<T> wrapper = (DTOWrapper<T>) DTOWrapper.from(obj.getClass());
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO \"").append(tableName).append("\" ");
        
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        
        boolean first = true;
        for(Entry<String,FieldInfo> entry:wrapper.getFields().entrySet()) {
            if (first) first = false;
            else {
                cols.append(" , ");
                vals.append(" , ");
            }
            cols.append("\"").append(entry.getKey()).append("\"");
            vals.append(":").append(entry.getKey());
        }
        
        
        sb.append(" ( ").append(cols).append(" )  VALUES ( ").append(vals).append(" )");
        
        return simpleJdbcTemplate.update(sb.toString(),new DTOSqlParameterSource(obj)) > 0;
    }
    
    protected <T> boolean update(T obj,String tableName) {
        final DTOWrapper<T> wrapper = (DTOWrapper<T>) DTOWrapper.from(obj.getClass());
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE \"").append(tableName).append("\" SET ");
        
        boolean first = true;
        for(Entry<String,FieldInfo> entry:wrapper.getFields().entrySet()) {
            FieldInfo field = entry.getValue();
            
            if (field.hasAnnotation(Ignore.class))
                continue;
            
            if (entry.getKey().equals("id")) 
                continue;
            if (first) first = false;
            else sb.append(" , ");
            sb.append("\"").append(entry.getKey()).append("\"")
                        .append(" = :").append(entry.getKey());
        }
        sb.append(" WHERE id = :id");
        
        return simpleJdbcTemplate.update(sb.toString(),new DTOSqlParameterSource(obj)) > 0;
    }
    
    protected <T> T get(UUID id,String tableName,Class<T> type) {
        try {
            return simpleJdbcTemplate.queryForObject(
                                    String.format("SELECT * FROM %s WHERE id = ?",tableName)
                                    ,new DTORowMapper<T>(type)
                                    ,id);
        } catch(EmptyResultDataAccessException ex) {
            return null;
        }
    }
    
    protected boolean delete(UUID id,String tableName) {
        return simpleJdbcTemplate.update(String.format("DELETE FROM %s WHERE id = ?",tableName),id) > 0;
    }
    
    protected boolean execute(String query,Object ... parms) {
        return simpleJdbcTemplate.update(query,parms) > 0;
    }
    
    protected <T> ResultSet<T> search(Select query,Class<T> clz) {
        return search(query, defaultTableName, clz);
    }
    protected <T> ResultSet<T> query(String sql,Class<T> clz,Object ... args) {
        final List<T> rows = simpleJdbcTemplate.query(sql, new DTORowMapper<T>(clz),args);
        long total = rows.size();
        return new ResultSet<T>(rows, total,-1);
    }
    
    protected <T> ResultSet<T> search(Select query,String tableName,Class<T> clz) {
        final JdbcQuery sql = JdbcQuery.from(query,tableName);
        final List<T> rows = simpleJdbcTemplate.query(sql.toSQL(), new DTORowMapper<T>(clz),sql.toArgs());
        long total = rows.size();
        if (query.getLimit() > 0)
            total = count(query, tableName);
        return new ResultSet<T>(rows, total,query.getOffset()+query.getLimit());
    }
    
    protected long count(Select query,String tableName) {
        final JdbcQuery sql = JdbcQuery.from(query,tableName);
        
        return simpleJdbcTemplate.queryForLong(sql.toCountSQL(),sql.toArgs());
    }
    
    public static class ResultSet<T> implements Iterable<T> {
        private final List<T> rows;
        private final long totalRows;
        private final int nextOffset;

        public ResultSet(List<T> rows, long totalRows,int nextOffset) {
            this.rows = rows;
            this.totalRows = totalRows;
            this.nextOffset = nextOffset;
        }

        public int getNextOffset() {
            return nextOffset;
        }

        public List<T> getRows() {
            return rows;
        }

        public long getTotalRows() {
            return totalRows;
        }

        public boolean isEmpty() {
            return rows.isEmpty();
        }

        public Iterator<T> iterator() {
            return rows.iterator();
        }

        public T get(int i) {
            return rows.get(i);
        }
    }

}
