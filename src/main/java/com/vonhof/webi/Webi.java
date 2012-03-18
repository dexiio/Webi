package com.vonhof.webi;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;

/**
 * Main Webi method
 * @author Henrik Hofmeister <@vonhofdk>
 */
public final class Webi {

    private final PathPatternMap<RequestHandler> requestHandlers = new PathPatternMap<RequestHandler>();
    private final PathPatternMap<Filter> filters = new PathPatternMap<Filter>();
    
    private final Server server;

    public Webi(int port) {
        server = new Server();
        final SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(port);
        connector.setAcceptors(Runtime.getRuntime().availableProcessors());
        server.setConnectors(new Connector[]{connector});
    }


    public void start() throws Exception {
        server.setHandler(new Handler());
        server.start();
        server.join();
    }
    
    public void add(PathPattern path,RequestHandler handler) {
        requestHandlers.put(path, handler);
    }
    
    public void add(PathPattern path,Filter filter) {
        filters.put(path, filter);
    }
    
    
    public void add(String path,RequestHandler handler) {
        requestHandlers.put(path, handler);
    }
    
    public void add(String path,Filter filter) {
        filters.put(path, filter);
    }
    
    
    private class Handler extends AbstractHandler {

        public void handle(String path,
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response)
                throws IOException, ServletException {
            
            RequestHandler handler = requestHandlers.get(path);
            if (handler != null) {
                path = requestHandlers.trimContext(path);
            }
                
            
            final WebiContext wr = new WebiContext(path, request, response);
            
            for(Filter filter:filters.getAll(path)) {
                if (!filter.apply(wr))
                    return;
            }
            
            if (handler != null) {
                handler.handle(wr);
            } else {
                response.sendError(404,"Not found");
            }
        }
    }
}
