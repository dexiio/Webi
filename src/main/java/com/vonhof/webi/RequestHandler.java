package com.vonhof.webi;

import java.io.IOException;
import javax.servlet.ServletException;

/**
 *
 * @author Henrik Hofmeister <hh@cphse.com>
 */
public interface RequestHandler {
    public void handle(WebiContext req) throws IOException, ServletException;
}
