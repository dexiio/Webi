package com.vonhof.webi.websockets;

import com.vonhof.babelshark.annotation.Name;
import com.vonhof.babelshark.node.ObjectNode;
import com.vonhof.babelshark.reflect.ClassInfo;
import com.vonhof.webi.WebiContext;
import com.vonhof.webi.annotation.Path;
import com.vonhof.webi.mvc.WebiController;
import java.util.Map;
import javax.inject.Inject;

/**
 * Extends WebiController and adds Websocket service information to service output
 * @author Henrik Hofmeister <@vonhofdk>
 */
@Path("webi")
@Name("webi")
public class WebiSocketController extends WebiController {
    
    @Inject
    WebSocketFilter webSocketFilter;

    @Override
    public ObjectNode service(WebiContext ctxt) {
        ObjectNode out = super.service(ctxt);
        
        
        //Generate web socket service information
        ObjectNode socketsNode = out.putObject("sockets");
        for(Map.Entry<String,SocketService> service:webSocketFilter.getWebSockets().entrySet()) {
            ClassInfo<?> classInfo = service.getValue().getClientClass();
            String name = "";
            if (classInfo.hasAnnotation(Name.class))
                name = classInfo.getAnnotation(Name.class).value();
            if (name.isEmpty())
                name = classInfo.getType().getSimpleName().toLowerCase();
            ObjectNode socketNode = socketsNode.putObject(name);
            socketNode.put("url", service.getKey());
            socketNode.put("type", classInfo.getName());
        }
        
        return out;
    }

    
}
