
package com.vonhof.webi.annotation;

import java.lang.annotation.*;

/**
 * Marks the method argument that takes the body input
 * @author Henrik Hofmeister <@vonhofdk>
 */

@Documented
@Target(value={ElementType.PARAMETER})
@Retention(value=RetentionPolicy.RUNTIME)
@Inherited
public @interface Body {
    boolean value() default true;
}
