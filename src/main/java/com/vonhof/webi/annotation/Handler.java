package com.vonhof.webi.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Handler {
    public Type value();
    
    public static enum Type {
        BEFORE_REQUEST,
        AFTER_REQUEST
    }
}
