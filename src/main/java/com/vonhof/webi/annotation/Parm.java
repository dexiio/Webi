
package com.vonhof.webi.annotation;

import java.lang.annotation.*;

/**
 * Extended information regarding method parameters.
 * @author Henrik Hofmeister <@vonhofdk>
 */

@Documented
@Target(value={ElementType.PARAMETER})
@Retention(value=RetentionPolicy.RUNTIME)
public @interface Parm {
    String value() default "";
    String[] defaultValue() default {""};
    String description() default "";
    boolean required() default false;
    Type type() default Type.AUTO;
    
    public static enum Type {
        AUTO,
        PATH,
        PARAMETER,
        HEADER,
        BODY,
        INJECT,
        SESSION
    }
}
