package com.vonhof.webi.queue;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class RateLimitedQueue<T extends Task,U extends Serializable>  {

    private final Map<String, Integer> taskTypeRateLimits = new ConcurrentHashMap<String, Integer>();

    /**
     * Limit the throughput of a specific task type ( e.g. how many tasks of the given type that may be processed
     * concurrently )
     * @param taskType
     * @param limit
     */
    public final void setTaskTypeRateLimit(String taskType, int limit) {
        taskTypeRateLimits.put(taskType, limit);
    }

    /**
     * Gets the max allowed concurrent tasks for a given task type. Returns -1 if no limit is specified.
     * @param taskType
     * @return
     */
    public final int getTaskTypeRateLimit(String taskType) {
        if (taskTypeRateLimits.containsKey(taskType)) {
            return taskTypeRateLimits.get(taskType);
        }
        return -1;
    }

    /**
     * Get estimated time untill queue no longer is blocked for the given task type.
     * @param taskType
     * @return
     */
    public abstract long getEstimatedTimeLeft(String taskType);

    /**
     * Get estimated time until queue no longer is blocked
     * @return
     */
    public abstract long getEstimatedTimeLeft();

    /**
     * Get all pending tasks
     * @return
     */
    public abstract Collection<T> getPending();

    /**
     * Returns the amount of tasks in this queue.
     * @return
     */
    public abstract int size();

    /**
     * Get as specific task in the queue.
     * @param id
     * @return
     */
    public abstract T get(UUID id);

    /**
     * Submits a task for processing
     * @param task
     * @return
     */
    public abstract boolean submit(T task);

    /**
     * Removes a task, will return false if task is being processed.
     * @param task
     * @return
     */
    public abstract boolean remove(T task);

    /**
     * Acknowledge a task. Removes it from queue.
     * @param id
     */
    public abstract void acknowledge(UUID id,U response);

    /**
     * Reject a task
     * @param id
     * @param resubmit
     */
    public abstract void reject(UUID id, boolean resubmit);

    /**
     * Acquire next task, blocks if nothing is available, or if rate limiter determines no tasks are eligible for
     * execution.
     * @return
     */
    public abstract T acquire();
}
