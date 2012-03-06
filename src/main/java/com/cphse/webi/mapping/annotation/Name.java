
package com.cphse.webi.mapping.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Use this name in mapping instead of the actual name
 * @author Henrik Hofmeister <henrik@newdawn.dk>
 */

@Documented
@Target(value={ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD})
@Retention(value=RetentionPolicy.RUNTIME)
public @interface Name {
    String value() default "";
    String description() default "";
    boolean required() default false;
    Type type() default Type.AUTO;
    
    public static enum Type {
        AUTO,PATH,PARAMETER,BODY
    }
}
