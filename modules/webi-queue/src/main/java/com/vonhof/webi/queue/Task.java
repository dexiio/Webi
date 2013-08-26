package com.vonhof.webi.queue;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.UUID;

public class Task<T extends Serializable> implements Serializable {

    private final UUID id;
    private final String type;
    private long estimatedDuration;
    private long actualDuration;

    private transient WeakReference<T> data;

    public Task(String type) {
        this.id = UUID.randomUUID();
        this.type = type;
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public long getEstimatedDuration() {
        return estimatedDuration;
    }

    public void setEstimatedDuration(long estimatedDuration) {
        this.estimatedDuration = estimatedDuration;
    }

    public long getActualDuration() {
        return actualDuration;
    }

    public void setActualDuration(long actualDuration) {
        this.actualDuration = actualDuration;
    }

    public T getData() {
        return data.get();
    }

    public void setData(T data) {
        this.data = new WeakReference<T>(data);
    }
}
