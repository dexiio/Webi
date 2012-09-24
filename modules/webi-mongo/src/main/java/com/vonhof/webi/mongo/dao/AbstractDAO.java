package com.vonhof.webi.mongo.dao;

import com.mongodb.*;
import com.vonhof.babelshark.BabelSharkInstance;
import com.vonhof.babelshark.exception.MappingException;
import com.vonhof.webi.bean.AfterInject;
import com.vonhof.webi.db.dto.ResultSetDTO;
import com.vonhof.webi.mongo.dto.BasicDTO;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class AbstractDAO<T extends BasicDTO> implements AfterInject {
    protected
    @Inject DB db;
    
    protected
    @Inject BabelSharkInstance bs;
    
    private final String collectionName;
    private final Class<T> entryClass;
    private DBCollection collection;

    public AbstractDAO(String collectionName,Class<T> entryClass) {
        this.collectionName = collectionName;
        this.entryClass = entryClass;
    }
    
    @Override
    public void afterInject() {
        collection = db.getCollection(collectionName);
    }
    
    protected DBCollection coll() {
        return collection;
    }
        
    protected BasicDBObject toDb(T doc) {
        try {
            return bs.convert(doc, BasicDBObject.class);
        } catch (MappingException ex) {
            Logger.getLogger(AbstractDAO.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    protected List<T> toList(final DBCursor c) {
        List<T> out = new ArrayList<T>();
        while(c.hasNext()) {
            out.add(fromDb(c.next()));
        }
        return out;
    }
    
    protected DBObject getListFields() {
        return null;
    }

    
    protected ResultSetDTO<T> toResultSet(final DBCursor c) {
        ResultSetDTO<T> out = new ResultSetDTO<T>();
        while(c.hasNext()) {
            out.getRows().add(fromDb(c.next()));
        }
        out.setTotalRows(c.count());
        return out;
    }
    
    protected T fromDb(DBObject dbdoc) {
        return fromDb(dbdoc, entryClass);
    }
    
    protected <U> U fromDb(DBObject dbdoc,Class<U> clz) {
        if (dbdoc == null) 
            return null;
        try {
            return bs.convert(dbdoc, clz);
        } catch (MappingException ex) {
            Logger.getLogger(AbstractDAO.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public T get(String id,String ... fields) {
        BasicDBObject doc = new BasicDBObject();
        doc.put("_id", id);
        DBObject out = null;
        if (fields.length > 0) {
            BasicDBObject fieldsObj = new BasicDBObject();
            for(String field:fields) {
                fieldsObj.put(field,true);
            }
            out = coll().findOne(doc,fieldsObj);
        } else {
            out = coll().findOne(doc);
        }
        
        return fromDb(out);
    }
    
    public ResultSetDTO<T> getList(String ... ids) {
        DBObject q = QueryBuilder.start("_id").in(ids).get();
        
        return toResultSet(queryForList(q));
    }
    
    public ResultSetDTO<T> getList(Collection<String> ids) {
        DBObject q = QueryBuilder.start("_id").in(ids).get();
        return toResultSet(queryForList(q));
    }
    
    public ResultSetDTO<T> getAll(int offset,int limit) {
        return toResultSet(queryForList().skip(offset).limit(limit));
    }
    
    public ResultSetDTO<T> getAll() {
        return toResultSet(queryForList());
    }
    
    protected DBCursor queryForList() {
        return queryForList(new BasicDBObject());
    }
    protected DBCursor queryForList(DBObject query) {
        return coll().find(query,getListFields());
    }
    
    public T create(T doc) {
        BasicDBObject dbDoc = toDb(doc);
        WriteResult out = coll().insert(dbDoc);
        if (out.getError() == null || out.getError().isEmpty())
            return doc;
        return null;
    }
    
    public T update(T doc) {
        BasicDBObject qDoc = new BasicDBObject("_id", doc.getId());
        BasicDBObject dbDoc = toDb(doc);
        WriteResult out = coll().update(qDoc,dbDoc);
        return doc;
    }
    
    public boolean delete(String id) {
        BasicDBObject doc = new BasicDBObject();
        doc.put("_id", id);
        WriteResult out = coll().remove(doc);
        return (out.getError() == null || out.getError().isEmpty());
    }
    
}
