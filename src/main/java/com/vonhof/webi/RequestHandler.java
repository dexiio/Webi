package com.vonhof.webi;

import java.io.IOException;
import javax.servlet.ServletException;

/**
 * Request handlers are the main handlers in webi.
 * @author Henrik Hofmeister <@vonhofdk>
 */
public interface RequestHandler {
    /**
     * Handle webicontext
     * @param req
     * @throws IOException
     * @throws ServletException 
     */
    public void handle(WebiContext req) throws IOException, ServletException;
}
