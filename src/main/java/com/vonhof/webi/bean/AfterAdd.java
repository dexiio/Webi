package com.vonhof.webi.bean;

/**
 *
 * After add is called after a bean as been added to the bean context - webi and the bean context is available and injected.
 * Anything else is not guaranteed.
 */
public interface AfterAdd {
    void afterAdd(BeanContext context);
}
