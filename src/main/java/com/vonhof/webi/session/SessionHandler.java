package com.vonhof.webi.session;

import com.vonhof.webi.WebiContext;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public interface SessionHandler {
    public WebiSession handle(WebiContext ctxt);
    public String getBasePath();
}
