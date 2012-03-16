package com.cphse.webi;

import com.cphse.webi.mapping.annotation.Path;
import com.vonhof.babelshark.BabelShark;
import com.vonhof.babelshark.Output;
import com.vonhof.babelshark.exception.MappingException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
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
import org.eclipse.jetty.server.nio.SelectChannelConnector;

public final class Webi {
    private final Server server;
    private final Map<String,Object> controllers = new HashMap<String,Object>();
    private final Map<String,Method> actions = new HashMap<String,Method>();
    
    public Webi(int port) {
        server = new Server(port);
        final SelectChannelConnector connector = new SelectChannelConnector();
        connector.setAcceptors(100);
        connector.setResponseBufferSize(0);
        connector.setAcceptQueueSize(5000);
        server.setConnectors(new Connector[]{connector});
    }
    
    public void expose(Object obj) {
        expose(obj, getObjectURI(obj));
    }
    public void expose(Object obj,String baseUrl) {
        controllers.put(baseUrl, obj);
        for(Method m:obj.getClass().getMethods()) {
            actions.put(getMethodURI(m), m);
        }
    }
    public void start() throws Exception {
        server.setHandler(new RequestHandler());
        server.start();
        server.join();
    }
    
    
    private String getMethodURI(Method m) {
        Path path = m.getAnnotation(Path.class);
        if (path != null)
            return path.value();
        return m.getName().toLowerCase();
    }
    
    private String getObjectURI(Object obj) {
        Class<? extends Object> clz = obj.getClass();
        Path path = clz.getAnnotation(Path.class);
        if (path != null) 
            return path.value();
        return obj.getClass().getSimpleName().toLowerCase();
    }
    
    private Object getObjectByURI(String uri) {
        String[] parts = uri.split("/");
        if (parts.length > 0) {
            return controllers.get(parts[0].toLowerCase());
        }
        return null;
    }
    private Method getMethodByURI(String uri) {
        int firstSep = uri.indexOf("/");
        if (firstSep > 1) {
            String name = uri.substring(firstSep+1);
            return actions.get(name.toLowerCase());
        }
        return null;
    }
    
    private class RequestHandler extends AbstractHandler {
        public void handle(String target, 
                            Request baseRequest, 
                            HttpServletRequest request, 
                            HttpServletResponse response)
                                throws IOException, ServletException {
            
            String path = target.isEmpty() ? "" : target.substring(1);
            Map<String, String[]> parms = request.getParameterMap();
            Object result = null;
            if (path != null)
                result = invokeAction(path, parms);
            
            if (result == null) {
                response.setStatus(404);
                response.setHeader("Content-type","text/plain");
                response.getWriter().println("No method was registered with the given url");
                System.out.println("No method was registered with the given url:"+path);
            } else {
                String type = getResponseType(request);
                response.setHeader("Content-type", BabelShark.getInstance().getMimeType(type));
                try {
                    BabelShark.getInstance().write(new Output(response.getOutputStream(),type), result);
                } catch (MappingException ex) {
                    throw new IOException(ex);
                }
            }
            response.flushBuffer();
        }
        
        private Object invokeAction(final String path,final Map<String, String[]> parms) {
            try {
                Object obj = getObjectByURI(path);
                if (obj == null)
                    return null;
                Method method = getMethodByURI(path);
                if (method == null)
                    return null;
                return method.invoke(obj);
            } catch (Throwable ex) {
                Logger.getLogger(Webi.class.getName()).
                        log(Level.SEVERE, null, ex);
            }
            return null;
        }
        
        private String getResponseType(HttpServletRequest request) {
            String format = request.getParameter("format");
            if (format == null)
                BabelShark.getInstance().getDefaultType();
            return format;
        }
        
    }
}
