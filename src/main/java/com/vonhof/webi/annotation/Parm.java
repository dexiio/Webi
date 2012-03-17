
package com.vonhof.webi.annotation;

import java.lang.annotation.*;

/**
 * Use this name in mapping instead of the actual name
 * @author Henrik Hofmeister <@vonhofdk>
 */

@Documented
@Target(value={ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD})
@Retention(value=RetentionPolicy.RUNTIME)
public @interface Parm {
    String value() default "";
    String[] defaultValue() default {""};
    String description() default "";
    boolean required() default false;
    Type type() default Type.AUTO;
    
    public static enum Type {
        AUTO,PATH,PARAMETER,BODY
    }
}
