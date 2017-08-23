package com.vonhof.webi.bean;


import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;
import java.util.Map;

public interface BeanInvocationInterceptor {
    boolean shouldApply(Class obj);
    void before(Object obj, Method method, Object[] args, MethodProxy methodProxy, Map<String, Object> scope);
    void after(Object obj, Method method, Object[] args, MethodProxy methodProxy, Object result, Throwable thrownException, long timeTaken, Map<String, Object> scope);
}
