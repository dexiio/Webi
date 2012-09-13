package com.vonhof.webi.db;

import java.util.*;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class Query<T extends Query> {
    private final List<Query> subQueries = new LinkedList<Query>();
    private final List<Criteria> criteria = new LinkedList<Criteria>();
    private final QueryType type;
    private final boolean not;
    protected Select select;

    private Query(Select select,QueryType type) {
        this(select,type, false);
        
    }
    private Query(Select select,QueryType type, boolean not) {
        this.select = select;
        this.type = type;
        this.not = not;
    }
    
    public Query and() {
        Query and = new Query(select,QueryType.AND);
        subQueries.add(and);
        return and;
    }
    
    public Query or() {
        Query and = new Query(select,QueryType.OR);
        subQueries.add(and);
        return and;
    }
    
    public Query andNot() {
        Query and = new Query(select,QueryType.AND,true);
        subQueries.add(and);
        return and;
    }
    
    public Query orNot() {
        Query and = new Query(select,QueryType.OR,true);
        subQueries.add(and);
        return and;
    }
    
    public T eq(String field,Object value) {
        criteria.add(new Criteria(field, CompareType.EQ, value));
        return (T) this;
    }
    
    public T neq(String field,Object value) {
        criteria.add(new Criteria(field, CompareType.NEQ, value));
        return (T) this;
    }
    
    public T eqgt(String field,Object value) {
        criteria.add(new Criteria(field, CompareType.EQGT, value));
        return (T) this;
    }
    
    public T eqlt(String field,Object value) {
        criteria.add(new Criteria(field, CompareType.EQLT, value));
        return (T) this;
    }
    
    public T gt(String field,Object value) {
        criteria.add(new Criteria(field, CompareType.GT, value));
        return (T) this;
    }
    
    public T lt(String field,Object value) {
        criteria.add(new Criteria(field, CompareType.LT, value));
        return (T) this;
    }
    
    public T in(String field,Object ... value) {
        criteria.add(new Criteria(field, CompareType.IN, value));
        return (T) this;
    }
    
    public T between(String field,Object value1, Object value2) {
        criteria.add(new Criteria(field, CompareType.IN, new Object[]{value1,value2}));
        return (T) this;
    }
    
    public T like(String field, String value) {
        criteria.add(new Criteria(field, CompareType.LIKE, value));
        return (T) this;
    }
    
    

    public List<Criteria> getCriteria() {
        return criteria;
    }

    public boolean isNot() {
        return not;
    }

    public List<Query> getSubQueries() {
        return subQueries;
    }

    public QueryType getType() {
        return type;
    }

    public Select statement() {
        return select;
    }
    
    
    
    public static Select select(String ... fields) {
        return new Select(fields);
    }

    
    public static class Select extends Query<Select> {
        private int offset;
        private int limit;
        private String orderBy;
        private String[] groupBy;
        private final Set<String> fields = new HashSet<String>();
        private String dir;
        
        private Select(String[] fields) {
            super(null,QueryType.AND);
            this.select = this;
            this.fields.addAll(Arrays.asList(fields));
        }
        
        public Set<String> fields() {
            return fields;
        }

        public int getLimit() {
            return limit;
        }

        public Select limit(int limit) {
            return limit(0,limit);
        }
        public Select limit(int offset,int limit) {
            this.offset = offset;
            this.limit = limit;
            return this;
        }

        public int getOffset() {
            return offset;
        }

        public String getOrderBy() {
            return orderBy;
        }
        
        public String getDirection() {
            return dir;
        }

        public String[] getGroupBy() {
            return groupBy;
        }


        public Select orderBy(String field) {
            return orderBy(field, "DESC");
        }

        public Select orderBy(String field, String dir) {
            this.orderBy = field;
            this.dir = dir;
            return this;
        }

        public Select offset(int offset) {
            this.offset = offset;
            return this;
        }

        public Select groupBy(String ... groups) {
            this.groupBy = groups;
            return this;
        }
    }
    
    
    public static class Criteria {
        private final String field;
        private final CompareType comparison;
        private final Object value;

        private Criteria(String field, CompareType comparison, Object value) {
            this.field = field;
            this.comparison = comparison;
            this.value = value;
        }
        
        public CompareType getComparison() {
            return comparison;
        }

        public String getField() {
            return field;
        }

        public Object getValue() {
            return value;
        }
    }
    
    public static enum QueryType {
        AND,OR
    }
    
    public static enum CompareType {
        EQ,EQGT,EQLT,GT,LT,BETWEEN,IN,NEQ,LIKE}
    
}
