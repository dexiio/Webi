package com.vonhof.webi.websockets;

import java.lang.annotation.*;

/**
 * Marks a method as an event handler on a websocket service
 * @author Henrik Hofmeister <@vonhofdk>
 */
@Documented
@Target(value={ElementType.METHOD})
@Retention(value=RetentionPolicy.RUNTIME)
public @interface EventHandler {
    String value() default "";
}
