package com.vonhof.webi.websockets;

import com.vonhof.webi.Filter;
import com.vonhof.webi.PathPatternMap;
import com.vonhof.webi.Webi;
import com.vonhof.webi.WebiContext;
import com.vonhof.webi.bean.AfterInject;
import com.vonhof.webi.websockets.SocketService.Client;
import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class WebSocketFilter extends WebSocketServerFactory implements Filter {
    @Inject
    private Webi webi;
    
    /**
     * Request handler map
     */
    private final PathPatternMap<SocketService> webSockets = new PathPatternMap<SocketService>();

    public WebSocketFilter() {
        super();
        getPolicy().setIdleTimeout(Long.MAX_VALUE);
        getPolicy().setMaxTextMessageSize(Integer.MAX_VALUE);

    }

    /**
     * Add websocket handler at path
     * @param path
     */
    public <T extends SocketService> T  add(String path,T service) {
        webSockets.put(path, service);
        webi.addBean(service);
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
            Logger.getLogger(WebSocketFilter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return true;
    }

    @Override
    public Client createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
        SocketService service = webSockets.get(req.getRequestURI().getPath());
        if (service != null) {
            try {
                Client client = service.newClient();
                webi.getBeanContext().injectOnly(client);
                return client;
            } catch (Exception ex) {
                Logger.getLogger(Webi.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }
}
