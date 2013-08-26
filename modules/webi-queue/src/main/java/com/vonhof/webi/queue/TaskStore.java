package com.vonhof.webi.queue;


import java.util.UUID;

public interface TaskStore<T extends Task> {

    public T get(UUID id);

    public void put(T task);

    public void remove(T task);

    public void remove(UUID id);
}
