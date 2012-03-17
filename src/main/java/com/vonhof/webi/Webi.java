package com.vonhof.webi;

import com.vonhof.webi.url.DefaultUrlMapper;
import com.vonhof.webi.url.UrlMapper;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;


/**
 * 
 * @author Henrik Hofmeister <@vonhofdk>
 */
public final class Webi {
    private final UrlMapper urlMapper = new DefaultUrlMapper();
    private final Server server;
    
    public Webi(int port) {
        server = new Server(port);
        final SelectChannelConnector connector = new SelectChannelConnector();
        connector.setAcceptors(Runtime.getRuntime().availableProcessors());
        server.setConnectors(new Connector[]{connector});
    }
    
    public void expose(Object obj) {
        urlMapper.expose(obj);
    }
    
    public void expose(Object obj,String baseUrl) {
        urlMapper.expose(obj,baseUrl);
    }
    
    public void start() throws Exception {
        server.setHandler(new WebiRequestHandler(urlMapper));
        server.start();
        server.join();
    }
}
