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
public class CookieSessionHandler implements SessionHandler {
    private final Map<String,WebiSession> sessions = new HashMap<String, WebiSession>();
    
    private final String cookieName;
    private int maxAge = 3600;

    public CookieSessionHandler(String cookieName) {
        this.cookieName = cookieName;
    }

    public WebiSession handle(WebiContext ctxt) {
        Cookie[] cookies = ctxt.getRequest().getCookies();
        WebiSession out = null;
        String cookieValue = null;;
        for(Cookie c:cookies) {
            String name = c.getName();
            if (name.equalsIgnoreCase(cookieName)) {
                cookieValue = c.getValue();
                WebiSession session = sessions.get(cookieValue);
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
            out = new WebiSession();
            sessions.put(cookieName, out);
        }
        Cookie cookie = new Cookie(cookieName, cookieValue);
        cookie.setMaxAge(maxAge);
        
        if (ctxt.getResponse() != null)
            ctxt.getResponse().addCookie(cookie);
        return out;
    }

}
