package com.vonhof.webi.mongo.dao;

import com.mongodb.*;
import com.vonhof.babelshark.BabelSharkInstance;
import com.vonhof.babelshark.exception.MappingException;
import com.vonhof.webi.bean.AfterInject;
import com.vonhof.webi.db.dto.ResultSetDTO;
import com.vonhof.webi.mongo.DBIterator;
import com.vonhof.webi.mongo.dto.BasicDTO;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class AbstractDAO<T extends BasicDTO> implements AfterInject {

    protected @Inject Mongo mongo;

    protected @Inject DB db;

    protected @Inject BabelSharkInstance bs;

    private final String collectionName;
    private final Class<T> entryClass;
    private DBCollection collection;
    private List<DBObject> sortKeys = new ArrayList<DBObject>();

    public AbstractDAO(String collectionName, Class<T> entryClass) {
        this.collectionName = collectionName;
        this.entryClass = entryClass;
    }

    public Class<T> getEntryClass() {
        return entryClass;
    }

    @Override
    public void afterInject() {
        collection = db.getCollection(collectionName);
    }


    protected void addShardKey(String... shardKeys) {

        ensureIndex(shardKeys);

        DB adminDb = mongo.getDB("admin");

        final BasicDBObject shardKeyObject = new BasicDBObject();
        for (String key : shardKeys) {
            shardKeyObject.put(key, 1);
        }
        final BasicDBObject shardCollectionCmd = new BasicDBObject("shardcollection", String.format("%s.%s", db.getName(), collectionName));
        shardCollectionCmd.put("key", shardKeyObject);
        adminDb.command(shardCollectionCmd);
    }

    protected void addSortKey(String field, int dir) {
        sortKeys.add(new BasicDBObject(field, dir));
    }

    protected void ensureIndex(String... fields) {
        if (fields.length == 0) {
            return;
        }

        BasicDBObject keys = new BasicDBObject();
        for (String field : fields) {
            keys.put(field, 1);
        }

        coll().createIndex(keys);
    }

    protected void ensureSortedIndex(String... fields) {
        if (fields.length == 0) {
            return;
        }
        for (DBObject sortKey : sortKeys) {

            BasicDBObject keys = new BasicDBObject();
            for (String field : fields) {
                keys.put(field, 1);
            }
            keys.putAll(sortKey);
            coll().createIndex(keys);
        }
    }


    public DBCollection coll() {
        return collection;
    }

    protected BasicDBObject toDb(Object doc) {
        try {
            return bs.convert(doc, BasicDBObject.class);
        } catch (MappingException ex) {
            Logger.getLogger(AbstractDAO.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    protected List<T> toList(final DBCursor c) {
        c.addOption(Bytes.QUERYOPTION_NOTIMEOUT)
                .addOption(Bytes.QUERYOPTION_SLAVEOK);

        try {
            List<T> out = new ArrayList<T>();
            while (c.hasNext()) {
                out.add(fromDb(c.next()));
            }
            return out;
        } finally {
            c.close();
        }
    }

    protected DBIterator<T> toIterator(final DBCursor c) {

        c.addOption(Bytes.QUERYOPTION_NOTIMEOUT)
            .addOption(Bytes.QUERYOPTION_SLAVEOK);

        return new DBIterator<T>(c) {
            @Override
            public T next() {
                return fromDb(c.next());
            }
        };
    }

    protected DBObject getListFields() {
        return null;
    }


    protected ResultSetDTO<T> toResultSet(final DBCursor c) {
        c.addOption(Bytes.QUERYOPTION_NOTIMEOUT)
                .addOption(Bytes.QUERYOPTION_SLAVEOK);

        ResultSetDTO<T> out = new ResultSetDTO<T>();
        while (c.hasNext()) {
            out.getRows().add(fromDb(c.next()));
        }
        long count = count(c.getQuery());
        out.setTotalRows(count);
        return out;
    }

    public T fromDb(DBObject dbdoc) {
        return fromDb(dbdoc, entryClass);
    }

    public <U> U fromDb(DBObject dbdoc, Class<U> clz) {
        if (dbdoc == null)
            return null;
        try {
            return bs.convert(dbdoc, clz);
        } catch (MappingException ex) {
            Logger.getLogger(AbstractDAO.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public T get(String id, String... fields) {
        BasicDBObject doc = new BasicDBObject();
        doc.put("_id", id);
        DBObject out = null;
        if (fields.length > 0) {
            BasicDBObject fieldsObj = new BasicDBObject();
            for (String field : fields) {
                fieldsObj.put(field, true);
            }
            out = coll().findOne(doc, fieldsObj);
        } else {
            out = coll().findOne(doc);
        }

        return fromDb(out);
    }

    public ResultSetDTO<T> getList(String... ids) {
        DBObject q = QueryBuilder.start("_id").in(ids).get();

        return toResultSet(queryForList(q));
    }

    public ResultSetDTO<T> getList(Collection<String> ids) {
        DBObject q = QueryBuilder.start("_id").in(ids).get();
        return toResultSet(queryForList(q));
    }

    public ResultSetDTO<T> getAll(int offset, int limit) {
        return toResultSet(queryForList().skip(offset).limit(limit));
    }

    public ResultSetDTO<T> getAll() {
        return toResultSet(queryForList());
    }

    public DBIterator<T> iterateAll() {
        return toIterator(queryForList());
    }

    protected DBCursor queryForList() {
        return queryForList(new BasicDBObject());
    }

    protected DBCursor queryForList(DBObject query) {
        return coll().find(query, getListFields());
    }

    public T create(T doc) {
        BasicDBObject dbDoc = toDb(doc);
        WriteResult out = coll().insert(dbDoc);
        return doc;
    }

    public boolean createBulk(T... docs) {
        BulkWriteOperation bulk = coll().initializeUnorderedBulkOperation();
        for(int i = 0 ; i < docs.length; i++) {
            bulk.insert(toDb(docs[i]));
        }
        return bulk.execute(WriteConcern.ACKNOWLEDGED).getInsertedCount() == docs.length;
    }

    public long count(DBObject q) {
        AggregationOutput aggregate = coll().aggregate(Arrays.asList(
                new BasicDBObject("$match", q),
                new BasicDBObject("$group",
                        new BasicDBObject("_id", null)
                            .append("count", new BasicDBObject("$sum", 1)))
        ));

        Iterator<DBObject> iterator = aggregate.results().iterator();
        if (iterator.hasNext()) {
            Object count = iterator.next().get("count");
            return Long.valueOf(String.valueOf(count));
        }

        return 0;
    }

    public T update(T doc) {
        BasicDBObject qDoc = new BasicDBObject("_id", doc.getId());
        BasicDBObject dbDoc = toDb(doc);
        coll().update(qDoc, dbDoc);
        return doc;
    }

    public boolean delete(String id) {
        BasicDBObject doc = new BasicDBObject();
        doc.put("_id", id);
        WriteResult out = coll().remove(doc);
        return true;
    }

}
