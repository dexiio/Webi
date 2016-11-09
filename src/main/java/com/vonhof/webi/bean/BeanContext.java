package com.vonhof.webi.bean;

import com.vonhof.babelshark.reflect.ClassInfo;
import com.vonhof.babelshark.reflect.FieldInfo;
import net.sf.cglib.proxy.Enhancer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.util.*;
import java.util.Map.Entry;

/**
 * Handles dependency injection
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class BeanContext {
    private final static Logger log = LogManager.getLogger(BeanContext.class.getName());
    private Map<Class, Object> beansByClass = new HashMap<Class, Object>();
    private Map<String, Object> beansById = new HashMap<String, Object>();
    private Map<Class, BeanWrapper> proxiesByClass = new HashMap<>();
    private Map<String, BeanWrapper> proxiesById = new HashMap<>();

    private List<ThreadLocalBeanProxy> threadLocalBeanProxies = new ArrayList<>();

    private List<AfterInject> afterInjectionCalled = new LinkedList<>();
    private List<AfterInit> afterInitCalled = new LinkedList<>();
    private List<BeanInvocationInterceptor> interceptors = new LinkedList<>();
    private boolean disableThreadLocals = false;
    private boolean initialized = false;

    public BeanContext() {
        add(this);
    }

    public BeanContext(BeanContext other) {
        //We disable thread locals in copies to make them easy to provide the threaded workers
        disableThreadLocals = true;

        beansByClass.putAll(other.beansByClass);
        beansById.putAll(other.beansById);

        proxiesByClass.putAll(other.proxiesByClass);
        proxiesById.putAll(other.proxiesById);

        replace(this);

        afterInjectionCalled.addAll(other.afterInjectionCalled);
        interceptors.addAll(other.interceptors);

        for (ThreadLocalBeanProxy threadLocalBeanProxy : other.threadLocalBeanProxies) {
            if (threadLocalBeanProxy.getNullableBean() == null) {
                continue;
            }

            add(threadLocalBeanProxy.getNullableBean());
        }

        initialized = true;
    }

    public Map<String, Object> getBeans() {
        return beansById;
    }

    public <T> void replace(T bean) {
        proxiesByClass.remove(bean.getClass());
        add(bean);
    }

    public <T> void add(Class<T> clz, T bean) {
        beansByClass.put(clz, bean);
        BeanWrapper beanWrapper = proxiesByClass.get(clz);

        if (disableThreadLocals &&
                beanWrapper != null &&
                beanWrapper.getProxyHandler() instanceof ThreadLocalBeanProxy) {
            beanWrapper = null;
        }
        if (beanWrapper != null) {
            beanWrapper.setBean(bean);
        } else {
            proxiesByClass.put(clz, makeBeanProxy(bean));
        }

        //This injects just the things available at this point in time - but is guaranteed to inject the bean context itself.
        injectFields(bean);

        if (bean instanceof AfterAdd) {
            ((AfterAdd) bean).afterAdd(this);
        }
    }

    public <T> void add(T bean) {
        add((Class<T>)bean.getClass(), bean);
    }

    public <T> void add(String id, T bean) {
        beansById.put(id, bean);
        BeanWrapper beanWrapper = proxiesById.get(id);
        if (disableThreadLocals &&
                beanWrapper.getProxyHandler() instanceof ThreadLocalBeanProxy) {
            beanWrapper = null;
        }
        if (beanWrapper != null) {
            beanWrapper.setBean(bean);
        } else {
            proxiesById.put(id, makeBeanProxy(bean));
        }

        add(bean);
    }

    public <T> T get(Class<T> beanClz) {
        BeanWrapper wrapper = proxiesByClass.get(beanClz);
        if (wrapper == null) {
            return null;
        }

        if (wrapper.getProxy() instanceof ThreadLocal) {
            return (T) ((ThreadLocal) wrapper.getProxy()).get();
        }
        return (T) wrapper.getProxy();
    }

    public <T> T get(String id) {
        BeanWrapper wrapper = proxiesById.get(id);
        if (wrapper == null) {
            return null;
        }

        if (wrapper.getProxy() instanceof ThreadLocal) {
            return (T) ((ThreadLocal) wrapper.getProxy()).get();
        }
        return (T) wrapper.getProxy();
    }

    protected <T> T getOriginal(Class<T> beanClz) {
        Object obj = beansByClass.get(beanClz);
        if (obj instanceof ThreadLocal) {
            obj = ((ThreadLocal) obj).get();
        }
        return (T) obj;
    }

    protected <T> T getOriginal(String id) {
        Object obj = beansById.get(id);
        if (obj instanceof ThreadLocal) {
            obj = ((ThreadLocal) obj).get();
        }
        return (T) obj;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void init() {
        if (initialized) {
            throw new IllegalStateException("Bean Context was already initialized");
        }

        initialized = true;

        for (Entry<Class, BeanWrapper> entry : new HashSet<>(proxiesByClass.entrySet())) {
            if (entry.getValue().getBean() == null) {
                throw new IllegalStateException("Missing bean for type: " + entry.getKey());
            }

            inject(entry.getValue().getBean());
        }

        for (Entry<String, BeanWrapper> entry : new HashSet<>(proxiesById.entrySet())) {

            if (entry.getValue().getBean() == null) {
                throw new IllegalStateException("Missing bean for ID: " + entry.getKey());
            }

            inject(entry.getValue().getBean());
        }

        for (Entry<Class, BeanWrapper> entry : new HashSet<>(proxiesByClass.entrySet())) {
            Object bean = entry.getValue().getBean();
            if (bean instanceof AfterInit &&
                    !afterInitCalled.contains(bean)) {
                ((AfterInit) bean).afterInit();
                afterInitCalled.add(((AfterInit) bean));
            }


        }

        for (Entry<String, BeanWrapper> entry : new HashSet<>(proxiesById.entrySet())) {
            Object bean = entry.getValue().getBean();
            if (bean instanceof AfterInit &&
                    !afterInitCalled.contains(bean)) {
                ((AfterInit) bean).afterInit();
                afterInitCalled.add(((AfterInit) bean));
            }
        }
    }


    private Class realClass(Class clz) {
        while (Enhancer.isEnhanced(clz)) {
            clz = clz.getSuperclass();
        }

        return clz;
    }

    private <T> T injectFields(T obj) {
        if (Enhancer.isEnhanced(obj.getClass())) {
            throw new IllegalArgumentException("Should only inject on actual beans - not proxies: " + obj.getClass());
        }

        ClassInfo<?> classInfo = ClassInfo.from(obj.getClass());

        for (FieldInfo f : classInfo.getFields().values()) {
            Inject annotation = f.getAnnotation(Inject.class);

            if (annotation == null) {
                continue;
            }

            f.forceAccessible();

            try {
                Object value = f.get(obj);
                if (value != null) {
                    continue;
                }
            } catch (IllegalAccessException e) {
                log.error("Failed to read bean", e);
            }

            BeanWrapper newWrapper = getOrMakeWrapper(f.getType());

            if (newWrapper.getProxy() == null) {
                throw new IllegalStateException("Bean not available for field: " + f);
            }

            try {
                f.set(obj, newWrapper.getProxy());
            } catch (IllegalAccessException e) {
                log.error("Failed to set bean", e);
            }
        }

        return obj;
    }

    private <T> T inject(T obj) {
        injectFields(obj);

        if (obj instanceof AfterInject &&
                !afterInjectionCalled.contains(obj)) {
            afterInjectionCalled.add(((AfterInject) obj));
            ((AfterInject) obj).afterInject();
        }

        return obj;
    }

    private BeanWrapper getOrMakeWrapper(Class type) {
        BeanWrapper wrapper = proxiesByClass.get(type);
        if (wrapper == null) {
            wrapper = makeBeanProxy(type);
            proxiesByClass.put(type, wrapper);
        }

        return wrapper;
    }

    public <T> T injectOnly(T obj) {
        T out = inject(obj);

        if (out instanceof AfterInit) {
            ((AfterInit)out).afterInit();
        }

        return out;
    }

    private Object getBeanForField(FieldInfo f) {
        Object bean = get(f.getName());
        if (bean != null && !f.getType().isAssignableFrom(bean.getClass())) {
            bean = null;
        }
        if (bean == null)
            bean = get(f.getType());
        return bean;
    }

    private Object getRealBeanForField(FieldInfo f) {
        Object bean = getOriginal(f.getName());
        if (bean != null && !f.getType().isAssignableFrom(bean.getClass())) {
            bean = null;
        }
        if (bean == null)
            bean = getOriginal(f.getType());
        return bean;
    }

    private <T> BeanWrapper makeBeanProxy(T obj) {
        BeanWrapper wrapper = makeBeanProxy(obj.getClass());
        wrapper.setBean(obj);
        return wrapper;
    }

    private <T> BeanWrapper makeBeanProxy(Class<T> clz) {
        AbstractBeanProxy<T> beanProxyHandler = makeBeanProxyHandler(clz);
        try {

            return new BeanWrapper(beanProxyHandler, Enhancer.create(
                    clz,
                    clz.getInterfaces(),
                    beanProxyHandler
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Can not make proxy for class: {} - Error: {}", clz, e.getMessage());

            return new BeanWrapper(beanProxyHandler, null);
        }
    }

    private <T> AbstractBeanProxy<T> makeBeanProxyHandler(Class<T> clz) {
        BeanScope annotation = clz.getAnnotation(BeanScope.class);
        if (annotation == null ||
                annotation.value() == BeanScope.Type.GLOBAL ||
                disableThreadLocals) {
            return new BeanProxy<>(getInterceptorsFor(clz));
        }

        ThreadLocalBeanProxy out = new ThreadLocalBeanProxy<>(getInterceptorsFor(clz));
        threadLocalBeanProxies.add(out);
        return out;
    }


    private <T> Collection<BeanInvocationInterceptor> getInterceptorsFor(Class<T> clz) {
        Collection<BeanInvocationInterceptor> out = new ArrayList<>();

        for (BeanInvocationInterceptor interceptor : interceptors) {
            if (interceptor.shouldApply(clz)) {
                out.add(interceptor);
            }
        }

        return out;
    }

    public void addInjectInterceptor(BeanInvocationInterceptor interceptor) {
        this.interceptors.add(interceptor);
    }

    public <T> void clearThreadLocal(Class<T> beanClass) {
        for(ThreadLocalBeanProxy entry : threadLocalBeanProxies) {
            if (entry.getNullableBean() != null &&
                    entry.getNullableBean().getClass().equals(beanClass)) {
                entry.setBean(null);
            }
        }
    }

    public void clearThreadLocals() {
        for(ThreadLocalBeanProxy entry : threadLocalBeanProxies) {
            entry.setBean(null);
        }
    }
}
