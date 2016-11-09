package com.vonhof.webi.bean;


import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface BeanScope {
    Type value() default Type.GLOBAL;

    enum Type {
        GLOBAL,
        LOCAL
    }
}
