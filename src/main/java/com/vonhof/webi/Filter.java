package com.vonhof.webi;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public interface Filter {
    /**
     * Apply filter - if return false - stops request
     */
    public boolean apply(WebiContext input);
}
