package com.vonhof.webi.websockets;

import com.vonhof.babelshark.BabelSharkInstance;
import com.vonhof.babelshark.node.ArrayNode;
import com.vonhof.babelshark.node.ObjectNode;
import com.vonhof.babelshark.node.SharkNode;
import com.vonhof.babelshark.node.ValueNode;
import com.vonhof.babelshark.reflect.ClassInfo;
import com.vonhof.babelshark.reflect.MethodInfo;
import com.vonhof.babelshark.reflect.MethodInfo.Parameter;
import com.vonhof.webi.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SocketService<T extends SocketService.Client>  {
    private static final Logger log = LogManager.getLogger(SocketService.class);

    @Inject
    private BabelSharkInstance bs;
    
    private final ConcurrentLinkedQueue<Client> clients = new ConcurrentLinkedQueue<Client>();
    private final Map<String, MethodInfo> eventHandlers = new HashMap<String, MethodInfo>();
    private final ClassInfo<T> clientClass;
    private String contentType = "json";
    
    public SocketService(Class<T> clientClass) {
        this.clientClass = ClassInfo.from(clientClass);
        readEventHandlers();
    }

    public ClassInfo<T> getClientClass() {
        return clientClass;
    }


    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    private void readEventHandlers() {
        List<MethodInfo> methods = clientClass.getMethods();
        for (MethodInfo m : methods) {
            EventHandler eventHandlerAnno = m.getAnnotation(EventHandler.class);
            if (eventHandlerAnno == null) {
                continue;
            }
            String event = eventHandlerAnno.value().toLowerCase();
            if (event == null || event.isEmpty())
                event = m.getName().toLowerCase();
            eventHandlers.put(event, m);
        }
    }
    
    public final void broadcast(String event, Object... args) {
        broadcast(null, event, args);    
    }
    
    public final boolean send(Client client,String event, Object... args) {
        event = event.toLowerCase();
        Event evt = new Event(event, args);
        return send(client, evt);
    }

    public List<T> getClients() {
        ArrayList<T> out = new ArrayList<T>();
        for(Client c:clients) {
            out.add((T)c);
        }
        return out;
    }

    public final void broadcast(Client from,String event, Object... args) {
        event = event.toLowerCase();
        Event evt = new Event(event, args);
        for (Client client : clients) {
            if (from == client) continue;
            send(client, evt);
        }
    }
    
    
    private boolean send(Client client, Event evt) {
        try {
            if (!client.session.isOpen()) {
                return false;
            }
            final String output = bs.writeToString(evt, contentType);
            client.session.getRemote().sendString(output);
            
            return true;
        } catch (EofException ex) {
            //Ignore error
            return false;
        } catch (ClosedChannelException ex) {
            //Ignore error
            return false;
        } catch (Exception ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Broken pipe")) {
                //Ignore error - user disconnected quickly.
                return false;
            }
            log.warn("Failed while attempting to send event to client socket", ex);
            return false;
        }
    }
    
    private boolean send(Client client, byte[] data) {
        try {
            if (!client.session.isOpen()) {
                return false;
            }
            
            client.session.getRemote().sendString(new String(data,"UTF-8"));
            return true;
        } catch (Exception ex) {
            log.warn("Failed while attempting to send message to client socket", ex);
            return false;
        }
    }
    
    public T newClient() throws Exception {
        Client client = clientClass.newInstance();
        client.service = this;
        return (T) client;
    }

    public static class Client<T extends Client> extends WebSocketAdapter {
        private Session session;
        
        @Inject
        private SocketService<T> service;
        
        @Inject
        private BabelSharkInstance bs;


        public SocketService<T> getService() {
            return service;
        }
        
        @EventHandler
        public void ping() {
            
        }
        
        
        public final void broadcast(String evt,Object ... args) {
            service.broadcast(this,evt, args);
        }
        public final void send(T c,String evt,Object ... args) {
            service.send(c,new Event(evt, args));
        }
        
        public final void send(String evt,Object ... args) {
            service.send(this,new Event(evt, args));
        }
        
        public final void send(byte[] data) {
            service.send(this, data);
        }

        @Override
        public void onWebSocketBinary(byte[] bytes, int i, int i2) {

        }

        @Override
        public void onWebSocketClose(int closeCode, String msg) {
            service.clients.remove(this);
        }

        @Override
        public void onWebSocketConnect(Session session) {
            service.clients.add(this);
            this.session = session;
        }

        @Override
        public void onWebSocketError(Throwable ex) {
            if (ex instanceof InvocationTargetException
                    && ex.getCause() instanceof HttpException)
                return;
            log.warn("Web socket failed", ex);
        }

        @Override
        public void onWebSocketText(String data) {
            if (data.isEmpty() || data.equals("{}") || data.equals("[]"))
                return;
            try {
                final ObjectNode evtNode = bs.read(data, bs.getDefaultType(), ObjectNode.class);

                final ValueNode<String> typeNode = (ValueNode<String>) evtNode.get("type");
                final String evtType = typeNode.getValue().toLowerCase();

                final MethodInfo evtHandler = service.eventHandlers.get(evtType);
                if (evtHandler == null) {
                    throw new IllegalArgumentException("Event handler for event '" + evtType + "' not found");
                }

                final ArrayNode argsNode = (ArrayNode) evtNode.get("args");
                final Parameter[] parmTypes = evtHandler.getParameters().values().toArray(new Parameter[0]);

                int argI = 0;

                Object[] args = new Object[parmTypes.length];
                for (int i = 0; i < args.length; i++) {
                    Parameter p = parmTypes[i];
                    if (p.getType().inherits(Client.class)) {
                        args[i] = this;
                        continue;
                    }
                    SharkNode arg = argsNode.get(argI);
                    if (arg != null) {
                        args[i] = bs.read(arg, p.getType());
                    }
                    argI++;
                }

                evtHandler.invoke(this, args);
            } catch (Exception ex) {
                onWebSocketError(ex);
            }
        }
    }

    public static final class Event<T> {

        private String type;
        private T[] args;

        public Event() {
        }

        public Event(String eventType, T[] args) {
            this.type = eventType;
            this.args = args;
        }

        public T[] getArgs() {
            return args;
        }

        public void setArgs(T[] args) {
            this.args = args;
        }

        public String getType() {
            return type;
        }

        public void setType(String eventType) {
            this.type = eventType;
        }
    }
}
