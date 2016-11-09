package com.vonhof.webi.bean;

import java.util.Collection;


class BeanProxy<T> extends AbstractBeanProxy<T> {
    private T bean;

    public BeanProxy(Collection<BeanInvocationInterceptor> interceptors) {
        super(interceptors);
    }

    @Override
    public void setBean(T bean) {
        this.bean = bean;
    }

    @Override
    public T getBean() {
        return bean;
    }

}
