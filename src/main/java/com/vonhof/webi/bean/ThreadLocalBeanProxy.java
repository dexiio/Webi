package com.vonhof.webi.bean;

import java.util.Collection;


final class ThreadLocalBeanProxy<T> extends AbstractBeanProxy<T> {

    private final ThreadLocal<T> threadLocal = new ThreadLocal<T>();

    public ThreadLocalBeanProxy(Collection<BeanInvocationInterceptor> interceptors) {
        super(interceptors);
    }


    public T getNullableBean() {
        return threadLocal.get();
    }

    @Override
    public T getBean() {
        T instance = threadLocal.get();
        if (instance == null) {
            throw new IllegalStateException("Method was called on thread local instance before the instance had been set");
        }

        return instance;
    }


    @Override
    public final void setBean(T bean) {
        threadLocal.set(bean);
    }
}
