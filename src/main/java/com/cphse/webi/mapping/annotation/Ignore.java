
package com.cphse.webi.mapping.annotation;

import java.lang.annotation.*;

/**
 * Ignore field / method entirely
 * @author Henrik Hofmeister <henrik@newdawn.dk>
 */

@Documented
@Target(value={ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
@Retention(value=RetentionPolicy.RUNTIME)
public @interface Ignore {
    String value();
}
