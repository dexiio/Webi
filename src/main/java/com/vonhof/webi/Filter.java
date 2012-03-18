package com.vonhof.webi;

/**
 * Filter interface - used to manipulate the WebiContext
 * @author Henrik Hofmeister <@vonhofdk>
 */
public interface Filter {
    /**
     * Apply filter - if return false - stops request
     */
    public boolean apply(WebiContext input);
}
