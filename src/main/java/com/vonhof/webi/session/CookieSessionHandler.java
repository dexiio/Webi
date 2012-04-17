package com.vonhof.webi.session;

import com.vonhof.webi.WebiContext;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.Cookie;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * Default implementation of a session handler. Uses cookies
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class CookieSessionHandler<T extends WebiSession> implements SessionHandler {
    private final Map<String,T> sessions = new HashMap<String, T>();
    
    private final String cookieName;
    private final String basePath;
    private int maxAge = 3600;
    
    
    
    public CookieSessionHandler(String basePath,String cookieName) {
        this.basePath = basePath;
        this.cookieName = cookieName;
    }

    public String getBasePath() {
        return basePath;
    }

    public String getCookieName() {
        return cookieName;
    }

    public T handle(WebiContext ctxt) {
        Cookie[] cookies = ctxt.getRequest().getCookies();
        T out = null;
        String cookieValue = null;
        for(Cookie c:cookies) {
            String name = c.getName();
            if (name.equalsIgnoreCase(cookieName)) {
                cookieValue = c.getValue();
                T session = sessions.get(cookieValue);
                if (session != null) {
                    out = session;
                }
                
                break;
            }
        }
        if (cookieValue == null) {
            cookieValue = DigestUtils.md5Hex(new Date().toString());
        }
        if (out == null) {
            out = newSession(cookieValue);
            add(cookieValue, out);
        }
        Cookie cookie = new Cookie(cookieName, cookieValue);
        cookie.setMaxAge(maxAge);
        cookie.setPath(getBasePath());
        
        if (ctxt.getResponse() != null)
            ctxt.getResponse().addCookie(cookie);
        return out;
    }
    
    public T newSession(String sessionKey) {
        return (T) new WebiSession();
    }
    
    public T get(String sessionKey) {
        return sessions.get(sessionKey);
    }
    
    public void add(String sessionKey,T session) {
        sessions.put(sessionKey, session);
    }
    
    public void remove(String sessionKey) {
        sessions.remove(sessionKey);
    }

}
