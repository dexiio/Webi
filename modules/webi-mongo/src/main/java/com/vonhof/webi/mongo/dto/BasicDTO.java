package com.vonhof.webi.mongo.dto;

import com.vonhof.babelshark.annotation.Name;
import java.util.UUID;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class BasicDTO {
    
    @Name(required=true,value="_id")
    private String id;

    public String getId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return String.format("%s[%s]",this.getClass().getSimpleName(),this.getId());
    }
    
    
}
