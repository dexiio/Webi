package com.vonhof.webi.websocket;

import com.vonhof.babelshark.BabelShark;
import com.vonhof.babelshark.ReflectUtils;
import com.vonhof.babelshark.node.*;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.websocket.WebSocket;

public final class SocketService {

    private final ConcurrentLinkedQueue<Client> clients = new ConcurrentLinkedQueue<Client>();
    private final BabelShark bs = BabelShark.getInstance();
    private final Map<String, Method> eventHandlers = new HashMap<String, Method>();
    private final Class<? extends Client> clientClass;

    public SocketService(Class<? extends Client> clientClass) {
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
            String event = eventHandlerAnno.value().
                    toLowerCase();
            eventHandlers.put(event, m);
        }
    }

    protected void broadcast(Client from,String event, Object... args) throws Exception {
        event = event.toLowerCase();
        Event evt = new Event(event, args);
        for (Client client : clients) {
            if (from == client) continue;
            sendTo(client, evt);
        }
    }
    
    
    private void sendTo(Client client, Event evt) throws Exception {
        if (!client.conn.isOpen()) {
            return;
        }
        String contentType = bs.getMimeType(client.conn.getProtocol());
        if (contentType == null || contentType.isEmpty())
            contentType = bs.getDefaultType();
        String output = bs.writeToString(evt, contentType);
        client.conn.sendMessage(output);
    }
    
    public Client newClient() throws Exception {
        Client client = clientClass.newInstance();
        client.service = this;
        return client;
    }

    public static class Client implements WebSocket.OnTextMessage {
        private Connection conn;
        private SocketService service;

        public void onOpen(Connection connection) {
            service.clients.add(this);
            this.conn = connection;
        }

        public final void onMessage(String data) {
            try {
                ObjectNode evtNode = service.bs.read(data, service.bs.getDefaultType(), ObjectNode.class);
                ValueNode<String> typeNode = (ValueNode<String>) evtNode.get("type");
                String evtType = typeNode.getValue().
                        toLowerCase();
                Method evtHandler = service.eventHandlers.get(evtType);
                ArrayNode argsNode = (ArrayNode) evtNode.get("args");

                Class<?>[] parms = evtHandler.getParameterTypes();

                int argI = 0;
                Object[] args = new Object[parms.length];
                for (int i = 0; i < args.length; i++) {
                    if (Client.class.isAssignableFrom(parms[i])) {
                        args[i] = this;
                        continue;
                    }
                    SharkNode arg = argsNode.get(argI);
                    if (arg != null) {
                        Type[] genParmTypes = evtHandler.getGenericParameterTypes();
                        SharkType type = SharkType.get(parms[i]);
                        if (genParmTypes.length > 0) {
                            if (ReflectUtils.isMap(parms[i])) {
                                if (genParmTypes.length > 1)
                                    type = SharkType.get(parms[i],genParmTypes[1]);
                                else
                                    type = SharkType.get(parms[i],genParmTypes[0]);
                            } else if (ReflectUtils.isCollection(parms[i])) {
                                type = SharkType.get(parms[i],genParmTypes[0]);
                            }
                        }
                        args[i] = service.bs.readAsValue(arg, type);
                    }
                    argI++;
                }

                evtHandler.invoke(this, args);

            } catch (Exception ex) {
                handleException(ex);
            }
        }
        
        public final void broadcast(String evt,Object ... args) throws Exception {
            service.broadcast(this,evt, args);
        }
        
        public final void reply(String evt,Object ... args) throws Exception {
            service.sendTo(this,new Event(evt, args));
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
