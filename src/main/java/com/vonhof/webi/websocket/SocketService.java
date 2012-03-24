package com.vonhof.webi.websocket;

import com.vonhof.babelshark.BabelSharkInstance;
import com.vonhof.babelshark.ReflectUtils;
import com.vonhof.babelshark.node.*;
import com.vonhof.webi.websocket.SocketService.Client;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
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
    private final Map<String, Method> eventHandlers = new HashMap<String, Method>();
    private final Class<T> clientClass;

    public SocketService(Class<T> clientClass) {
        this.clientClass = clientClass;
        readEventHandlers();
    }
    
    

    private void readEventHandlers() {
        Method[] methods = clientClass.getMethods();
        for (Method m : methods) {
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
            if (!client._connection.isOpen()) {
                return false;
            }
            String contentType = bs.getMimeType(client._connection.getProtocol());
            if (contentType == null || contentType.isEmpty())
                contentType = bs.getDefaultType();
            String output = bs.writeToString(evt, contentType);
            client._connection.sendMessage(output);
            return true;
        } catch (Exception ex) {
            Logger.getLogger(SocketService.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }
    
    public T newClient() throws Exception {
        Client client = clientClass.newInstance();
        client._service = this;
        return (T) client;
    }

    public static class Client<T extends Client> implements WebSocket.OnTextMessage {
        private Connection _connection;
        private SocketService<T> _service;

        public void onOpen(Connection connection) {
            _service.clients.add(this);
            this._connection = connection;
        }

        public final void onMessage(String data) {
            try {
                ObjectNode evtNode = _service.bs.read(data, _service.bs.getDefaultType(), ObjectNode.class);
                ValueNode<String> typeNode = (ValueNode<String>) evtNode.get("type");
                String evtType = typeNode.getValue().
                        toLowerCase();
                Method evtHandler = _service.eventHandlers.get(evtType);
                ArrayNode argsNode = (ArrayNode) evtNode.get("args");

                Class<?>[] parmTypes = evtHandler.getParameterTypes();
                Type[] genParmTypes = evtHandler.getGenericParameterTypes();

                int argI = 0;
                Object[] args = new Object[parmTypes.length];
                for (int i = 0; i < args.length; i++) {
                    if (Client.class.isAssignableFrom(parmTypes[i])) {
                        args[i] = this;
                        continue;
                    }
                    SharkNode arg = argsNode.get(argI);
                    if (arg != null) {
                        
                        SharkType type = SharkType.get(parmTypes[i]);
                        if (genParmTypes[i] != null) {
                            type = SharkType.get(parmTypes[i],genParmTypes[i]);
                        }
                        args[i] = _service.bs.readAsValue(arg, type);
                    }
                    argI++;
                }

                evtHandler.invoke(this, args);

            } catch (Exception ex) {
                handleException(ex);
            }
        }

        public SocketService<T> getService() {
            return _service;
        }
        
        
        public final void broadcast(String evt,Object ... args) {
            _service.broadcast(this,evt, args);
        }
        public final void send(T c,String evt,Object ... args) {
            _service.send(c,new Event(evt, args));
        }
        
        public final void reply(String evt,Object ... args) {
            _service.send(this,new Event(evt, args));
        }

        public void handleException(Throwable ex) {
            Logger.getLogger(SocketService.class.getName()).
                    log(Level.SEVERE, null, ex);
        }

        public void onClose(int closeCode, String message) {
            _service.clients.remove(this);
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
