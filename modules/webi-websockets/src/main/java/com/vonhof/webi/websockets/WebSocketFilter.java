package com.vonhof.webi.websockets;

import com.vonhof.webi.Filter;
import com.vonhof.webi.PathPatternMap;
import com.vonhof.webi.Webi;
import com.vonhof.webi.WebiContext;
import com.vonhof.webi.bean.AfterAdd;
import com.vonhof.webi.bean.AfterInit;
import com.vonhof.webi.bean.AfterInject;
import com.vonhof.webi.bean.BeanContext;
import com.vonhof.webi.websockets.SocketService.Client;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class WebSocketFilter extends WebSocketServerFactory implements Filter, AfterAdd {
    private static final Logger log = LogManager.getLogger(WebSocketFilter.class);

    @Inject
    private Webi webi;
    
    /**
     * Request handler map
     */
    private final PathPatternMap<SocketService> webSockets = new PathPatternMap<SocketService>();

    public WebSocketFilter() {
        super();
    }

    @Override
    public void afterAdd(BeanContext context) {
        getPolicy().setIdleTimeout(Long.MAX_VALUE);
        getPolicy().setMaxTextMessageSize(Integer.MAX_VALUE);
    }

    /**
     * Add websocket handler at path
     * @param path
     */
    public <T extends SocketService> T  add(String path,T service) {
        webSockets.put(path, service);
        return service;
    }

    /**
     * Get websocket services
     * @return 
     */
    public PathPatternMap<SocketService> getWebSockets() {
        return webSockets;
    }

    public boolean apply(WebiContext ctxt) {

        try {

            if (isUpgradeRequest(ctxt.getRequest(), ctxt.getResponse()) &&
                    webSockets.get(ctxt.getPath()) != null) {

                if (acceptWebSocket(this, ctxt.getRequest(),ctxt.getResponse())) {
                    return false;
                }

            }
        } catch (IOException ex) {
            log.error("Failed while upgrading websocket request", ex);
        }
        return true;
    }

    @Override
    public Client createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
        SocketService service = webSockets.get(req.getRequestURI().getPath());
        if (service != null) {
            try {
                Client client = service.newClient();
                BeanContext beanContextCopy = new BeanContext(webi.getBeanContext());
                beanContextCopy.injectOnly(client);
                log.debug("Created web socket client {} for service: {}", client.getClass(), service.getClass());
                return client;
            } catch (Exception ex) {
                log.error("Failed while creating websocket", ex);
            }
        }
        return null;
    }
}
