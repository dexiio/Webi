package com.vonhof.webi.websockets;

import com.vonhof.webi.Filter;
import com.vonhof.webi.PathPatternMap;
import com.vonhof.webi.Webi;
import com.vonhof.webi.WebiContext;
import com.vonhof.webi.bean.AfterInject;
import com.vonhof.webi.websockets.SocketService.Client;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketFactory;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class WebSocketFilter implements Filter, WebSocketFactory.Acceptor,AfterInject {
    @Inject
    private Webi webi;
    
    /**
     * Request handler map
     */
    private final PathPatternMap<SocketService> webSockets = new PathPatternMap<SocketService>();
    
    private final WebSocketFactory webSocketFactory = new WebSocketFactory(this, 3*1024);

    public WebSocketFilter() {
        webSocketFactory.setMaxIdleTime(-1);
    }
    
    
      /**
     * Add websocket handler at path
     * @param path
     * @param handler 
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
            if (webSocketFactory.acceptWebSocket(ctxt.getRequest(),ctxt.getResponse())) {
                return false;
            }
        } catch (IOException ex) {
            Logger.getLogger(WebSocketFilter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return true;
    }

    @Override
    public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
        SocketService service = webSockets.get(request.getPathInfo());
        if (service != null) {
            try {
                Client client = service.newClient();
                webi.getBeanContext().inject(client);
                return client;
            } catch (Exception ex) {
                Logger.getLogger(Webi.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    @Override
    public boolean checkOrigin(HttpServletRequest request, String origin) {
        return true;
    }

    public void afterInject() {
        
    }

}
