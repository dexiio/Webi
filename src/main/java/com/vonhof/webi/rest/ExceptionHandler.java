package com.vonhof.webi.rest;

import com.vonhof.webi.WebiContext;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public interface ExceptionHandler {
    public Object handle(WebiContext ctxt,Throwable ex);
}
