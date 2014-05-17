package com.vonhof.webi;

import com.vonhof.babelshark.BabelShark;
import com.vonhof.webi.bean.BeanContext;
import com.vonhof.webi.session.SessionHandler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.GzipHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;

/**
 * Main Webi method
 * @author Henrik Hofmeister <@vonhofdk>
 */
public final class Webi {
    private static final Logger LOG  = Logger.getLogger(Webi.class.getName());
    
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
     * Users used for basic authentication
     */
    private final Map<String,String> users = new HashMap<String, String>();
    
    /**
     * A list of registered shutdown handlers that gets called when the Webi server is stopped.
     */
    private final List<ShutdownHandler> shutdownHandlers = new ArrayList<ShutdownHandler>();
    
    /**
     * Dev mode disables various caching to allow for easier development.
     */
    private boolean devMode = false;;
    
    /**
     * Shutdown gracefully
     */
    private boolean shutdownGracefully = false;;
    
    /**
     * Setup webi server on specified port
     * @param port 
     */
    public Webi(int port) {
        server = new Server();
        final SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(port);
        //connector.setAcceptors((Runtime.getRuntime().availableProcessors()*2)-2);
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
     *  disable dev mode
     */
    public boolean isDevMode() {
        return devMode;
    }

    /**
     *  Enable dev mode
     */
    public void setDevMode(boolean debugMode) {
        this.devMode = debugMode;
    }
    


    /**
     * Start webi server - blocks until server is stopped
     * @throws Exception 
     */
    public void start() throws Exception {
        beanContext.inject(true);

        GzipHandler gzipHandler = new GzipHandler();
        gzipHandler.setHandler(new Handler());
        gzipHandler.setMimeTypes("text/html,text/css,text/javascript,application/json,image/gif,image/jpeg,image/png");

        server.setHandler(gzipHandler);
        server.start();
        try {
            server.join();
        } catch (Throwable ex) {
            
        }
        
        //Not getting here before its shut down
        for(ShutdownHandler handler:shutdownHandlers) {
            try {
                handler.onShutdown(shutdownGracefully);
            } catch(Exception ex) {
                LOG.log(Level.WARNING, "Webi got an exception while trying to shutdown jetty", ex);
            }
        }
    }
    
    /**
     * Shutdown graceful
     * @throws Exception 
     */
    public void stop() throws Exception {
        stop(true);
    }
    
    /**
     * Stop webi server
     * @throws Exception 
     */
    public void stop(final boolean graceful) throws Exception {
        shutdownGracefully = graceful;
        Thread shutdownThread = new Thread("Shutdown") {
            @Override
            public void run() {
                try {
                    if (graceful)
                        server.setGracefulShutdown(5000);
                    server.stop();
                } catch(Throwable ex) {

                }
            }
        };
        shutdownThread.start();
        try {
            shutdownThread.join();
        } catch(Throwable ex) {
            
        }
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
    
  
    
    public void add(ShutdownHandler shutdownHandler) {
        beanContext.add(shutdownHandler);
        shutdownHandlers.add(shutdownHandler);
    }
    
    /**
     * Add session resolver at path
     * @param handler
     */
    public <T extends SessionHandler> T  add(T handler) {
        sessionHandlers.put(handler.getBasePath(), handler);
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

    public BeanContext getBeanContext() {
        return beanContext;
    }

    /**
     * Internal webi jetty handler
     */
    private class Handler extends AbstractHandler {

        private final ThreadLocal<WebiContext> context = new ThreadLocal<WebiContext>();
        
        @Override
        public void handle(String path,
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response)
                throws IOException, ServletException {


            
            PathPattern basePattern = requestHandlers.getPattern(path);
            String basePath = basePattern != null ? basePattern.toString() : "/";
            
            RequestHandler handler = requestHandlers.get(path);
            
            final SessionHandler sessionResolver = sessionHandlers.get(path);

            //Hack for path - make a proper normalization process for paths
            if (handler != null) {
                path = requestHandlers.trimContext(path);
            }
            
            final WebiContext wr = new WebiContext(basePath,path,
                                                    request,response,
                                                   sessionResolver);

            context.set(wr);
            beanContext.addThreadLocal(wr.getSession());

            try {

                for(Filter filter:filters.getAll(path)) {
                    if (!filter.apply(wr))
                        return;
                }

                if (handler != null) {
                    handler.handle(wr);
                } else {
                    response.sendError(404,"Not found");
                }
            } finally {
                //Reset webi context for this thread , just in case
                beanContext.clearThreadLocal(wr.getSession());
            }
        }
    }
    
    public static interface ShutdownHandler {
        
        public void onShutdown(boolean graceful) throws Exception;
    }
}
