package com.vonhof.webi.bean;

public class BeanWrapper<T, U extends AbstractBeanProxy<T>> {
    private final U proxyHandler;
    private T proxy;

    public BeanWrapper(U proxyHandler, T proxy) {
        this.proxyHandler = proxyHandler;
        this.proxy = proxy;
    }

    public U getProxyHandler() {
        return proxyHandler;
    }

    public T getProxy() {
        return proxy;
    }

    public T getBean() {
        return proxyHandler.getBean();
    }

    public void setBean(T bean) {
        if (proxy == null) {
            proxy = bean; //If proxy wasn't set use the bean directly
        }
        proxyHandler.setBean(bean);
    }
}
