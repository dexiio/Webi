package com.vonhof.webi.mongo;

import com.mongodb.DBCursor;

import java.util.Iterator;


public abstract class DBIterator<T> implements Iterator<T> {

    private final DBCursor cursor;

    public DBIterator(DBCursor cursor) {
        this.cursor = cursor;
    }

    @Override
    public boolean hasNext() {
        return cursor.hasNext();
    }

    @Override
    public void remove() {
        cursor.remove();
    }

    public void close() {
        cursor.close();
    }

    public boolean isEmpty() {
        return !hasNext();
    }

    public int count() {
        return cursor.count();
    }
}
