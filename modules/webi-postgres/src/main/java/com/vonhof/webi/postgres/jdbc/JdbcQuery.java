package com.vonhof.webi.postgres.jdbc;


import com.vonhof.webi.db.Query;
import com.vonhof.webi.db.Query.Select;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class JdbcQuery {
    
    public static JdbcQuery from(Select s, String ... tableNames) {
        return new JdbcQuery(s,tableNames);
    } 
    
    private final Select select;
    private final String tableNames;

    public JdbcQuery(Select select, String[] tableNames) {
        this.select = select;
        this.tableNames = StringUtils.join(tableNames,",");
    }
    
    public String toSQL() {
        return selectToSQL(select);
    }
    
    public String toCountSQL() {
        return selectToCountSQL(select);
    }
    
    public Object[] toArgs() {
        return queryToParms(select).toArray();
    }

    @Override
    public String toString() {
        return toSQL();
    }
    
    
    
    
    private List<Object> queryToParms(Query<?> query) {
        List<Object> out = new ArrayList<Object>();
        for(Query.Criteria c:query.getCriteria()) {
            
            switch(c.getComparison()) {
                case IN:
                case BETWEEN:
                    Object[] vals = (Object[]) c.getValue();
                    out.addAll(Arrays.asList(vals));
                    break;
                case EQ:
                case EQGT:
                case EQLT:
                case GT:
                case LT:
                case NEQ:
                case LIKE:
                    Object value = c.getValue();
                    if (value == null) 
                        break;
                    if (value instanceof Enum) {
                        value = ((Enum)value).name();
                    }
                    out.add(value);
                    break;
            }
            
        }
        for(Query q:query.getSubQueries()) {
            out.addAll(queryToParms(q));
        }
        return out;
    }
    
    private String selectToCountSQL(Select query) {
        Set<String> fields = query.fields();
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT COUNT(*) as \"count\"");
        
        sb.append(" FROM \"").append(tableNames)
                .append("\" WHERE ")
                .append(queryToSQL(query));
        
        return sb.toString();
    }
    
    private String selectToSQL(Select query) {
        Set<String> fields = query.fields();
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        if (fields.isEmpty())
            sb.append("*");
        else {
            boolean first = true;
            for(String field:fields) {
                if (first) first = false;
                else sb.append(", ");
                sb.append("\"").append(field).append("\"");
            }
        }
        sb.append(" FROM \"").append(tableNames)
                .append("\" WHERE ")
                .append(queryToSQL(query));
        
        if (query.getGroupBy() != null) {
            sb.append(" GROUP BY ");
            boolean first = true;
            for(String group:query.getGroupBy()) {
                if (!first)
                    sb.append(",");
                first = false;
                sb.append("\"").append(group).append("\"");
            }
        }
            
        
        
        if (query.getOrderBy() != null && !query.getOrderBy().isEmpty())
            sb.append(" ORDER BY \"").append(query.getOrderBy()).append("\" ").append(query.getDirection());
        
        if (query.getOffset() > 0)
            sb.append(" OFFSET ").append(query.getOffset());
        if (query.getLimit() > 0)
            sb.append(" LIMIT ").append(query.getLimit());
        
        
        return sb.toString();
    }
    
    private String queryToSQL(Query<?> query) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for(Query.Criteria c:query.getCriteria()) {
            if (first) first = false;
            else sb.append(" AND ");
            sb.append("\"").append(c.getField()).append("\"");
            switch(c.getComparison()) {
                case BETWEEN:
                    sb.append(" (BETWEEN ? AND ?) ");
                    break;
                case EQ:
                    if (c.getValue() == null)
                        sb.append(" IS NULL ");
                    else
                        sb.append(" = ? ");
                    break;
                case EQGT:
                    sb.append(" >= ? ");
                    break;
                case EQLT:
                    sb.append(" <= ? ");
                    break;
                case GT:
                    sb.append(" > ? ");
                    break;
                case LT:
                    sb.append(" < ? ");
                    break;
                case IN:
                    sb.append(" IN (?)");
                    break;
                case NEQ:
                    if (c.getValue() == null)
                        sb.append(" IS NOT NULL ");
                    else
                        sb.append(" != ?");
                    
                    break;
                case LIKE:
                    sb.append(" LIKE ?");
                    break;
            }
        }
        first = true;
        for(Query q:query.getSubQueries()) {
            q.getType();
            if (first)  {
                first = false;
            } else {
                sb.append(" ) ");
            }
            
            sb.append(q.getType().name());
            if (q.isNot())
                sb.append(" NOT ");
            sb.append(" ( ");
            
            sb.append(queryToSQL(q));
        }
        
        if (!query.getSubQueries().isEmpty())
            sb.append(" ) ");
        return sb.toString();
    }
    
}
