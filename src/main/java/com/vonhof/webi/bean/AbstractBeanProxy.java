package com.vonhof.webi.bean;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;


abstract class AbstractBeanProxy<T> implements MethodInterceptor, Callback {

    private final Collection<BeanInvocationInterceptor> interceptors;

    protected AbstractBeanProxy(Collection<BeanInvocationInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    abstract public T getBean();
    abstract public void setBean(T bean);

    @Override
    public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
        T thisBean = getBean();
        if (thisBean == null) {
            return null;
        }

        for (BeanInvocationInterceptor interceptor : interceptors) {
            interceptor.before(thisBean, method, args, methodProxy);
        }

        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable thrownException = null;

        try {
            result = method.invoke(thisBean, args);
            return result;

        } catch (InvocationTargetException ex) {
            thrownException = ex.getTargetException();
            throw ex.getTargetException();
        } catch (Throwable ex) {
            thrownException = ex;
            throw ex;
        } finally {
            long timeTaken = System.currentTimeMillis() - startTime;
            for (BeanInvocationInterceptor interceptor : interceptors) {
                interceptor.after(thisBean, method, args, methodProxy, result, thrownException, timeTaken);
            }
        }
    }


}
