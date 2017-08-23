package com.vonhof.webi.bean;


import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface BeanScope {
    Type value() default Type.GLOBAL;

    boolean ignored() default false;

    enum Type {
        GLOBAL,
        LOCAL
    }
}
