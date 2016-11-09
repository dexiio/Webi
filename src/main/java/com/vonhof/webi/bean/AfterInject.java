package com.vonhof.webi.bean;

/**
 * Is called after the injection occurs - all beans are injected and available prior to this being called.
 */
public interface AfterInject {
    void afterInject();
}
