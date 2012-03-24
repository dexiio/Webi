package com.vonhof.webi.websocket;

import com.vonhof.babelshark.BabelSharkInstance;
import com.vonhof.babelshark.node.ArrayNode;
import com.vonhof.babelshark.node.ObjectNode;
import com.vonhof.babelshark.node.SharkNode;
import com.vonhof.babelshark.node.ValueNode;
import com.vonhof.babelshark.reflect.ClassInfo;
import com.vonhof.babelshark.reflect.MethodInfo;
import com.vonhof.babelshark.reflect.MethodInfo.Parameter;
import com.vonhof.webi.websocket.SocketService.Client;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.eclipse.jetty.websocket.WebSocket;

public final class SocketService<T extends SocketService.Client> {

    @Inject
    private BabelSharkInstance bs;
    
    private final ConcurrentLinkedQueue<Client> clients = new ConcurrentLinkedQueue<Client>();
    private final Map<String, MethodInfo> eventHandlers = new HashMap<String, MethodInfo>();
    private final ClassInfo<T> clientClass;

    public SocketService(Class<T> clientClass) {
        this.clientClass = ClassInfo.from(clientClass);
        readEventHandlers();
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
            if (!client.connection.isOpen()) {
                return false;
            }
            final String contentType = bs.getMimeType(client.connection.getProtocol(),true);
            final String output = bs.writeToString(evt, contentType);
            
            client.connection.sendMessage(output);
            return true;
        } catch (Exception ex) {
            Logger.getLogger(SocketService.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }
    
    public T newClient() throws Exception {
        Client client = clientClass.newInstance();
        return (T) client;
    }

    public static class Client<T extends Client> implements WebSocket.OnTextMessage {
        private Connection connection;
        
        @Inject
        private SocketService<T> service;
        
        @Inject
        private BabelSharkInstance bs;

        public void onOpen(Connection connection) {
            service.clients.add(this);
            this.connection = connection;
        }

        public final void onMessage(String data) {
            try {
                final ObjectNode evtNode = bs.read(data, bs.getDefaultType(), ObjectNode.class);
                final ValueNode<String> typeNode = (ValueNode<String>) evtNode.get("type");
                final String evtType = typeNode.getValue().toLowerCase();
                
                final MethodInfo evtHandler = service.eventHandlers.get(evtType);
                
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
                        args[i] = bs.readAsValue(arg, p.getType());
                    }
                    argI++;
                }

                evtHandler.invoke(this, args);

            } catch (Exception ex) {
                handleException(ex);
            }
        }

        public SocketService<T> getService() {
            return service;
        }
        
        
        public final void broadcast(String evt,Object ... args) {
            service.broadcast(this,evt, args);
        }
        public final void send(T c,String evt,Object ... args) {
            service.send(c,new Event(evt, args));
        }
        
        public final void reply(String evt,Object ... args) {
            service.send(this,new Event(evt, args));
        }

        public void handleException(Throwable ex) {
            Logger.getLogger(SocketService.class.getName()).
                    log(Level.SEVERE, null, ex);
        }

        public void onClose(int closeCode, String message) {
            service.clients.remove(this);
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
