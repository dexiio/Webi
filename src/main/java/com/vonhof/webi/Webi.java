package com.vonhof.webi;

import com.vonhof.webi.websocket.SocketService;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketFactory;
import org.eclipse.jetty.websocket.WebSocketHandler;

/**
 * Main Webi method
 * @author Henrik Hofmeister <@vonhofdk>
 */
public final class Webi {
    /**
     * Request handler map
     */
    private final PathPatternMap<SocketService> webSockets = new PathPatternMap<SocketService>();
    /**
     * Request handler map
     */
    private final PathPatternMap<RequestHandler> requestHandlers = new PathPatternMap<RequestHandler>();
    /**
     * Filter map
     */
    private final PathPatternMap<Filter> filters = new PathPatternMap<Filter>();
    
    /**
     * Jetty server instance
     */
    private final Server server;

    /**
     * Setup webi server on specified port
     * @param port 
     */
    public Webi(int port) {
        server = new Server();
        final SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(port);
        connector.setAcceptors(Runtime.getRuntime().availableProcessors());
        server.setConnectors(new Connector[]{connector});
    }
    
    /**
     * Setup webi server using specified jetty server
     * @param server 
     */
    public Webi(Server server) {
        this.server = server;
    }
    


    /**
     * Start webi server - blocks until server is stopped
     * @throws Exception 
     */
    public void start() throws Exception {
        
        WebSocketHandler handler;
        server.setHandler(new Handler());
        server.start();
        server.join();
    }
    
    /**
     * Stop webi server
     * @throws Exception 
     */
    public void stop() throws Exception {
        server.stop();
    }
    
    /**
     * Add request handler at path
     * @param path
     * @param handler 
     */
    public void add(String path,RequestHandler handler) {
        requestHandlers.put(path, handler);
    }
    
    /**
     * Add filter at path
     * @param path
     * @param filter 
     */
    public void add(String path,Filter filter) {
        filters.put(path, filter);
    }
    
    /**
     * Add request handler at path
     * @param path
     * @param handler 
     */
    public void add(String path,SocketService server) {
        webSockets.put(path, server);
    }
    
    /**
     * Internal webi jetty handler
     */
    private class Handler extends AbstractHandler implements WebSocketFactory.Acceptor {
        
        private final WebSocketFactory webSocketFactory = new WebSocketFactory(this, 3*1024);

        public void handle(String path,
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response)
                throws IOException, ServletException {
            
            if (webSocketFactory.acceptWebSocket(request,response)) {
                return;
            }
            
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

        public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
            SocketService service = webSockets.get(request.getPathInfo());
            if (service != null) {
                try {
                    return service.newClient();
                } catch (Exception ex) {
                    Logger.getLogger(Webi.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            return null;
        }

        public boolean checkOrigin(HttpServletRequest request, String origin) {
            return true;
        }
    }
}
