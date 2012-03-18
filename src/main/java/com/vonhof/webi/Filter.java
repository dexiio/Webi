package com.vonhof.webi;

/**
 * Filter interface - used to manipulate the WebiContext
 * @author Henrik Hofmeister <@vonhofdk>
 */
public interface Filter {
    
    /**
     * Apply filter
     * @param input
     * @return if false then webi will stop processing request
     */
    public boolean apply(WebiContext input);
}
