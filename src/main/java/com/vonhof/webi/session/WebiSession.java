package com.vonhof.webi.session;

import java.util.HashMap;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class WebiSession extends HashMap<String, Object> {
    private int maxAge = -1;
    private String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(int maxAge) {
        this.maxAge = maxAge;
    }
}
