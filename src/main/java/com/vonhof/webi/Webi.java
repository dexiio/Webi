package com.vonhof.webi;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jetty9.InstrumentedConnectionFactory;
import com.codahale.metrics.jetty9.InstrumentedHandler;
import com.codahale.metrics.jetty9.InstrumentedQueuedThreadPool;
import com.vonhof.babelshark.BabelShark;
import com.vonhof.webi.bean.BeanContext;
import com.vonhof.webi.session.SessionHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlets.gzip.GzipHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/**
 * Main Webi method
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public final class Webi {
    private final String ATTR_SUSPENDED = "QoSFilter@" + Integer.toHexString(hashCode()) + ".SUSPENDED";
    private final String ATTR_RESUMED = "QoSFilter@" + Integer.toHexString(hashCode()) + ".RESUMED";


    private static final Logger log = LogManager.getLogger(Webi.class);

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
    private final Map<String, String> users = new HashMap<String, String>();

    /**
     * A list of registered shutdown handlers that gets called when the Webi server is stopped.
     */
    private final List<ShutdownHandler> shutdownHandlers = new ArrayList<ShutdownHandler>();

    /**
     * Dev mode disables various caching to allow for easier development.
     */
    private boolean devMode = false;
    ;

    /**
     * Shutdown gracefully
     */
    private boolean shutdownGracefully = false;

    private Semaphore requestSemaphore;

    private final Queue<AsyncContext> requestQueue = new ConcurrentLinkedQueue<>();

    private final AsyncListener requestListeners = new QoSAsyncListener();

    private long waitMs = 100;

    private long suspendMs = -1;

    private int maxRequests = 50;

    private MetricRegistry registry;

    private RequestLogHandler requestLogHandler = new RequestLogHandler();

    public Webi(int port, int maxThreads, int acceptQueueSize, int maxConcurrentRequests) {
        this(null, port, maxThreads, acceptQueueSize, maxConcurrentRequests,30000);
    }

    public Webi(int port, int maxThreads, int acceptQueueSize, int maxConcurrentRequests, int idleTimeout) {
        this(null, port, maxThreads, acceptQueueSize, maxConcurrentRequests,idleTimeout);
    }

    /**
     * Setup webi server on specified port
     *
     * @param port
     */
    public Webi(MetricRegistry registry, int port, int maxThreads, int acceptQueueSize, int maxConcurrentRequests, int idleTimeout) {
        this.maxRequests = maxConcurrentRequests;
        this.registry = registry;

        log.info("Configured with maxThread: {}, acceptQueueSize: {}, maxConcurrentRequests: {}, metrics: {}",
                    maxThreads, acceptQueueSize, maxConcurrentRequests, registry != null);

        if (registry != null) {
            server = new Server(new InstrumentedQueuedThreadPool(registry, maxThreads));
        } else {
            server = new Server(new QueuedThreadPool(maxThreads));
        }

        int numProcessors = Runtime.getRuntime().availableProcessors();
        final ServerConnector connector = new ServerConnector(server, numProcessors, numProcessors * 2);
        connector.setAcceptQueueSize(acceptQueueSize);
        connector.setPort(port);
        connector.setReuseAddress(true);
        connector.setIdleTimeout(idleTimeout);

        if (registry != null) {
            Collection<ConnectionFactory> connectionFactories = new ArrayList<>();

            for (ConnectionFactory factory : connector.getConnectionFactories()) {
                connectionFactories.add(new InstrumentedConnectionFactory(factory, registry.timer(factory.getProtocol())));
            }
            connector.setConnectionFactories(connectionFactories);
        }


        server.setConnectors(new Connector[]{connector});


        init();
    }

    public void setRequestLog(RequestLog requestLog) {
        requestLogHandler.setRequestLog(requestLog);
        if (requestLog instanceof NCSARequestLog) {
            log.info("Configured request log: {}, filename: {}", requestLog.getClass(), ((NCSARequestLog)requestLog).getFilename());
        } else {
            log.info("Configured request log: {}", requestLog);
        }

    }
    /**
     * Setup webi server using specified jetty server
     *
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

        if (maxRequests > 0) {
            requestSemaphore = new Semaphore(maxRequests, true);
        }

    }

    /**
     * disable dev mode
     */
    public boolean isDevMode() {
        return devMode;
    }

    /**
     * Enable dev mode
     */
    public void setDevMode(boolean debugMode) {
        this.devMode = debugMode;
    }


    /**
     * Start webi server - blocks until server is stopped
     *
     * @throws Exception
     */
    public void start() throws Exception {
        if (!beanContext.isInitialized()) {
            beanContext.init();
        }

        GzipHandler gzipHandler = new GzipHandler();
        gzipHandler.setHandler(new Handler());
        gzipHandler.setMimeTypes("text/html,text/css,text/javascript,application/json,image/gif,image/jpeg,image/png");

        org.eclipse.jetty.server.Handler handler = gzipHandler;

        if (requestLogHandler.getRequestLog() != null) {
            requestLogHandler.setHandler(gzipHandler);
            handler = requestLogHandler;
        }

        if (registry != null) {
            InstrumentedHandler instrumentedHandler = new InstrumentedHandler(registry, "webi");
            instrumentedHandler.setHandler(handler);

            handler = instrumentedHandler;
            log.info("Added instrumented handler for webi metrics");
        }

        server.setHandler(handler);
        server.start();
        try {
            server.join();
        } catch (Throwable ex) {

        }

        //Not getting here before its shut down
        for (ShutdownHandler shutdownHandler : shutdownHandlers) {
            try {
                shutdownHandler.onShutdown(shutdownGracefully);
            } catch (Exception ex) {
                log.warn("Webi got an exception while trying to shutdown jetty", ex);
            }
        }
    }

    /**
     * Shutdown graceful
     *
     * @throws Exception
     */
    public void stop() throws Exception {
        stop(true);
    }

    /**
     * Stop webi server
     *
     * @throws Exception
     */
    public void stop(final boolean graceful) throws Exception {
        shutdownGracefully = graceful;
        Thread shutdownThread = new Thread("Shutdown") {
            @Override
            public void run() {
                try {
                    if (graceful)
                        server.setStopTimeout(5000);
                    server.stop();
                } catch (Throwable ex) {

                }
            }
        };
        shutdownThread.start();
        try {
            shutdownThread.join();
        } catch (Throwable ex) {

        }
    }

    /**
     * Add request handler at path
     *
     * @param path
     * @param handler
     */
    public <T extends RequestHandler> T add(String path, T handler) {
        requestHandlers.put(path, handler);
        beanContext.add(handler);
        return handler;
    }


    /**
     * Add filter at path
     *
     * @param path
     * @param filter
     */
    public <T extends Filter> T add(String path, T filter) {
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
     *
     * @param handler
     */
    public <T extends SessionHandler> T add(T handler) {
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
        beanContext.add(id, obj);
    }

    public <T> void addBean(Class<T> clz, T obj) {
        beanContext.add(clz, obj);
    }

    public BeanContext getBeanContext() {
        return beanContext;
    }

    /**
     * Internal webi jetty handler
     */
    private class Handler extends AbstractHandler {

        @Override
        public void handle(final String path,
                           final Request baseRequest,
                           final HttpServletRequest request,
                           final HttpServletResponse response)
                throws IOException, ServletException {

            if (maxRequests < 1) {
                doHandle(path, baseRequest, request, response);
                return;
            }

            throttleRequest(request, response, new HandlerCallback() {
                @Override
                public void handle() throws IOException, ServletException {
                    doHandle(path, baseRequest, request, response);
                }
            });
        }

        /**
         * Handle many concurrent requests
         * @param request
         * @param response
         * @return
         * @throws IOException
         */
        public boolean throttleRequest(ServletRequest request, ServletResponse response, HandlerCallback callable) throws IOException, ServletException {
            boolean accepted = false;
            try {
                Boolean suspended = (Boolean) request.getAttribute(ATTR_SUSPENDED);
                if (suspended == null) {
                    accepted = requestSemaphore.tryAcquire(waitMs, TimeUnit.MILLISECONDS);
                    if (accepted) {
                        request.setAttribute(ATTR_SUSPENDED, Boolean.FALSE);

                    } else {
                        request.setAttribute(ATTR_SUSPENDED, Boolean.TRUE);

                        AsyncContext asyncContext = request.startAsync();
                        if (suspendMs > 0) {
                            asyncContext.setTimeout(suspendMs);
                        }
                        asyncContext.addListener(requestListeners);
                        requestQueue.add(asyncContext);
                        return false;
                    }
                } else {
                    if (suspended) {
                        request.setAttribute(ATTR_SUSPENDED, Boolean.FALSE);
                        Boolean resumed = (Boolean) request.getAttribute(ATTR_RESUMED);
                        if (resumed == Boolean.TRUE) {
                            requestSemaphore.acquire();
                            accepted = true;
                        } else {
                            // Timeout! try 1 more time.
                            accepted = requestSemaphore.tryAcquire(waitMs, TimeUnit.MILLISECONDS);
                            log.warn("Request timed out to: {}", ((HttpServletRequest) request).getRequestURI());
                        }
                    } else {
                        // Pass through resume of previously accepted request.
                        requestSemaphore.acquire();
                        accepted = true;
                    }
                }

                if (accepted) {
                    callable.handle();
                } else {
                    ((HttpServletResponse) response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                }
            } catch (InterruptedException e) {
                ((HttpServletResponse) response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            } finally {
                if (accepted) {
                    AsyncContext asyncContext = requestQueue.poll();
                    if (asyncContext != null) {
                        ServletRequest candidate = asyncContext.getRequest();
                        Boolean suspended = (Boolean) candidate.getAttribute(ATTR_SUSPENDED);
                        if (suspended == Boolean.TRUE) {
                            candidate.setAttribute(ATTR_RESUMED, Boolean.TRUE);
                            asyncContext.dispatch();
                        }
                    }

                    requestSemaphore.release();
                }
            }

            return accepted;
        }

        private void doHandle(String path, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            PathPattern basePattern = requestHandlers.getPattern(path);
            String basePath = basePattern != null ? basePattern.toString() : "/";

            RequestHandler handler = requestHandlers.get(path);

            final SessionHandler sessionResolver = sessionHandlers.get(path);

            //Hack for path - make a proper normalization process for paths
            if (handler != null) {
                path = requestHandlers.trimContext(path);
            }

            WebiContext wr = null;
            try {
                beanContext.clearThreadLocals();

                wr = new WebiContext(basePath, path,
                        baseRequest,
                        request, response);

                beanContext.add(wr);
                beanContext.add(wr.resolve(sessionResolver));

                for (Filter filter : filters.getAll(path)) {
                    if (!filter.apply(wr)) {
                        wr.setRequestHandled(true);
                        return;
                    }

                }

                if (handler != null) {
                    handler.handle(wr);
                } else {
                    response.sendError(404, "Not found");
                }
            } catch (HttpException ex) {
                response.sendError(ex.getCode(), ex.getMessage());
            }
        }
    }

    public interface ShutdownHandler {

        void onShutdown(boolean graceful) throws Exception;
    }

    private interface HandlerCallback {
        void handle() throws IOException, ServletException;
    }

    private class QoSAsyncListener implements AsyncListener {

        public QoSAsyncListener() {

        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {}

        @Override
        public void onComplete(AsyncEvent event) throws IOException {}

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            // Remove before it's redispatched, so it won't be
            // redispatched again at the end of the filtering.
            AsyncContext asyncContext = event.getAsyncContext();
            requestQueue.remove(asyncContext);
            asyncContext.dispatch();
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {}
    }
}
