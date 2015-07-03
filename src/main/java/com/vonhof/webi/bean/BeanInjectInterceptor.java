package com.vonhof.webi.bean;


public interface BeanInjectInterceptor {
    public <T> T intercept(T obj);
}
