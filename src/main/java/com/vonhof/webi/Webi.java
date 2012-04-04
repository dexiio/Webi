package com.vonhof.webi;

import com.vonhof.babelshark.BabelShark;
import com.vonhof.webi.bean.BeanContext;
import com.vonhof.webi.session.SessionHandler;
import com.vonhof.webi.session.WebiSession;
import com.vonhof.webi.websocket.SocketService;
import com.vonhof.webi.websocket.SocketService.Client;
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
     * Filter map
     */
    private final PathPatternMap<SessionHandler> sessionHandlers = new PathPatternMap<SessionHandler>();
    
    
    /**
     * Bean context - handles dependency injection
     */
    private final BeanContext beanContext = new BeanContext();
    
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
        init();
    }
    
    /**
     * Setup webi server using specified jetty server
     * @param server 
     */
    public Webi(Server server) {
        this.server = server;
        init();
    }
    
    private void init() {
        beanContext.add(this);
        beanContext.add(server);
        beanContext.add(BabelShark.getDefaultInstance());
    }
    


    /**
     * Start webi server - blocks until server is stopped
     * @throws Exception 
     */
    public void start() throws Exception {
        beanContext.inject(true);
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
    public <T extends RequestHandler> T add(String path,T handler) {
        requestHandlers.put(path, handler);
        beanContext.add(handler);
        return handler;
    }
    
    /**
     * Add filter at path
     * @param path
     * @param filter 
     */
    public <T extends Filter> T add(String path,T filter) {
        filters.put(path, filter);
        beanContext.add(filter);
        return filter;
    }
    
    /**
     * Add request handler at path
     * @param path
     * @param handler 
     */
    public <T extends SocketService> T  add(String path,T service) {
        webSockets.put(path, service);
        beanContext.add(service);
        return service;
    }
    
    /**
     * Add session resolver at path
     * @param path
     * @param handler 
     */
    public <T extends SessionHandler> T  add(String path,T handler) {
        sessionHandlers.put(path, handler);
        beanContext.add(handler);
        return handler;
    }

    public void addBean(Object bean) {
        beanContext.add(bean);
    }
    
    public <T> T getBean(Class<T> clz) {
        return beanContext.get(clz);
    }

    public void addBean(String id, Object obj) {
        beanContext.add(id,obj);
    }
    public <T> void addBean(Class<T> clz, T obj) {
        beanContext.add(clz,obj);
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
            
            RequestHandler handler = requestHandlers.get(path);
            
            final SessionHandler sessionResolver = sessionHandlers.get(path);
            
            //Hack for path - make a proper normalization process for paths
            if (handler != null) {
                path = requestHandlers.trimContext(path);
            }
            
            
            
            final WebiContext wr = new WebiContext(path,request,response,
                                                   sessionResolver);
            
            for(Filter filter:filters.getAll(path)) {
                if (!filter.apply(wr))
                    return;
            }
            
            if (webSocketFactory.acceptWebSocket(request,response)) {
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
                    Client client = service.newClient();
                    beanContext.inject(client);
                    return client;
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
