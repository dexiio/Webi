package com.cphse.webi.mapping.annotation;

import com.cphse.webi.mapping.HttpMethod;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Use to force a specific url for a given method or type
 * @author Henrik Hofmeister <henrik@newdawn.dk>
 */

@Documented
@Target(value={ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Path {
    String value();
    HttpMethod[] methods() default {HttpMethod.GET};
}
