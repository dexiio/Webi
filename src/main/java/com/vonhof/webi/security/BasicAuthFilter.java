package com.vonhof.webi.security;

import com.vonhof.webi.Filter;
import com.vonhof.webi.WebiContext;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class BasicAuthFilter implements Filter {
    
    private Map<String,String> users = new HashMap<String, String>();
    private String realm = "Protected area";

    public BasicAuthFilter(String realm) {
        this.realm = realm;
    }
    
    public BasicAuthFilter(String realm,String username,String password) {
        this.realm = realm;
        this.addUser(username, password);
    }

    public BasicAuthFilter() {
        
    }
    

    
    
    
    @Override
    public boolean apply(WebiContext ctxt) {
        String authHeader = ctxt.getHeader("Authorization");
        if (authHeader != null 
                && !authHeader.isEmpty()
                && authHeader.trim().startsWith("Basic ")) {
            String authBase64 = authHeader.substring(6);
            String[] userPw = new String(Base64.decodeBase64(authBase64)).split(":",2);
            if (userPw[1].equals(users.get(userPw[0])))
                return true;
        }
        try {
            ctxt.setHeader("WWW-Authenticate", String.format("Basic realm=\"%s\"",realm));
            ctxt.sendError(401,"Authorization Required");
        } catch (IOException ex) {
            LogManager.getLogger(BasicAuthFilter.class).fatal("Failed to send auth request", ex);
        }
        
        return false;
    }
    
    public void addUser(String username,String password) {
        users.put(username, password);
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }
}
