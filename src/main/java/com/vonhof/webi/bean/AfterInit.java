package com.vonhof.webi.bean;

/**
 *
 * After init is on all beans implementing it when the bean context is initied. All injections has happened
 * prior to this being called and all injected beans are guaranteed to be available.
 */
public interface AfterInit {
    void afterInit();
}
