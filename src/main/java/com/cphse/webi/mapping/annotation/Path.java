package com.cphse.webi.mapping.annotation;

import com.cphse.webi.HttpMethod;
import java.lang.annotation.*;

/**
 * Use to force a specific url for a given method or type
 * @author Henrik Hofmeister <@vonhofdk>
 */

@Documented
@Target(value={ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Path {
    String value();
    HttpMethod[] methods() default {HttpMethod.GET};
}
