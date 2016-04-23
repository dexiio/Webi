package com.vonhof.webi.bean;

import com.vonhof.babelshark.ReflectUtils;
import com.vonhof.babelshark.reflect.ClassInfo;
import com.vonhof.babelshark.reflect.FieldInfo;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;
import javax.inject.Inject;

/**
 * Handles dependency injection
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class BeanContext {
    private final static Logger log = LogManager.getLogger(BeanContext.class.getName());
    private Map<Class, Object> beansByClass = new HashMap<Class, Object>();
    private Map<String, Object> beansById = new HashMap<String, Object>();

    private Map<Class, ThreadLocalWrapper> wrappersByClass = new HashMap<Class, ThreadLocalWrapper>();

    private Map<Class, Object> proxiesByClass = new HashMap<Class, Object>();
    private Map<String, Object> proxiesById = new HashMap<String, Object>();

    private Set<Object> injecting = new HashSet<Object>();
    private Set<Object> injected = new HashSet<Object>();
    private List<AfterInject> afterInjectionCalled = new LinkedList<AfterInject>();
    private List<BeanInjectInterceptor> interceptors = new LinkedList<>();

    public BeanContext() {
        //Add myself
        add(this);
    }

    public BeanContext(BeanContext other) {


        proxiesByClass.putAll(other.proxiesByClass);
        proxiesById.putAll(other.proxiesById);

        beansByClass.putAll(other.beansByClass);
        beansById.putAll(other.beansById);

        add(this);

        afterInjectionCalled.addAll(other.afterInjectionCalled);
        interceptors.addAll(other.interceptors);

        //Add thread locals as "normal" beans - this is a temp copy.
        for(ThreadLocalWrapper wrapper : other.wrappersByClass.values()) {
            if (wrapper.threadLocal.get() == null) {
                continue;
            }
            add(wrapper.threadLocal.get());
        }


    }

    public Map<String, Object> getBeans() {
        return beansById;
    }

    public <T> void add(Class<T> clz, T bean) {
        beansByClass.put(clz, bean);
        proxiesByClass.put(clz, intercept(bean));
        if (bean instanceof AfterAdd) {
            ((AfterAdd) bean).afterAdd(this);
        }

        inject(bean, false);
    }

    public <T> void add(T bean) {
        add((Class<T>)bean.getClass(), bean);
    }

    public <T> void add(String id, T bean) {
        beansById.put(id, bean);
        proxiesById.put(id, intercept(bean));
        if (bean instanceof AfterAdd) {
            ((AfterAdd) bean).afterAdd(this);
        }
        add(bean);
    }

    public <T> T get(Class<T> beanClz) {
        Object obj = proxiesByClass.get(beanClz);
        if (obj instanceof ThreadLocal) {
            obj = ((ThreadLocal) obj).get();
        }
        return (T) obj;
    }

    public <T> T get(String id) {
        Object obj = proxiesById.get(id);
        if (obj instanceof ThreadLocal) {
            obj = ((ThreadLocal) obj).get();
        }
        return (T) obj;
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

    public void injectAll() {
        for (Entry<Class, Object> entry : new HashSet<>(beansByClass.entrySet())) {
            inject(entry.getValue(), true);
        }

        for (Entry<String, Object> entry : new HashSet<>(beansById.entrySet())) {
            inject(entry.getValue(), true);
        }
    }


    private Class realClass(Class clz) {
        while (Enhancer.isEnhanced(clz)) {
            clz = clz.getSuperclass();
        }

        return clz;
    }

    private <T> T inject(T obj, boolean required) {
        if (injected.contains(obj)) {
            return obj;
        }

        Class<?> realClass = realClass(obj.getClass());

        ClassInfo<?> clz = ClassInfo.from(realClass);
        Map<String, FieldInfo> fields = clz.getFields();
        boolean injectedAll = true;
        for (FieldInfo f : fields.values()) {
            Inject annotation = f.getAnnotation(Inject.class);
            f.forceAccessible();
            injecting.add(obj);
            try {
                Object value = f.get(obj);
                if (annotation != null
                        && value == null) {
                    Object bean = getBeanForField(f);
                    if (bean != null) {
                        f.set(obj, bean);
                    } else {
                        injectedAll = false;
                        if (required) {
                            throw new RuntimeException(
                                    String.format("No bean registered for class: %s in %s",
                                            f.getType().getName(),
                                            realClass.getName()));
                        }
                    }
                }

                if (value != null
                        && ReflectUtils.isBean(value.getClass())
                        && !f.getType().getFieldsByAnnotation(Inject.class).isEmpty()) {
                    //Recurse
                    if (Enhancer.isEnhanced(value.getClass())) {
                        value = getRealBeanForField(f);
                    }
                    if (!injecting.contains(value)) {
                        inject(value, required);
                    }
                }
            } catch (Throwable ex) {
                log.fatal("Failed while injecting beans", ex);
            } finally {
                injecting.remove(obj);
            }
        }

        if (injectedAll) {
            injected.add(obj);
        }
        //Only call if all fields were injected (it may be too soon)
        if (injectedAll &&
                obj instanceof AfterInject &&
                !afterInjectionCalled.contains(obj)) {
            afterInjectionCalled.add(((AfterInject) obj));
            ((AfterInject) obj).afterInject();
        }

        return obj;
    }

    public <T> T injectOnly(T obj) {
        T out = inject(obj, true);
        injected.remove(out);
        return out;
    }

    private Object getBeanForField(FieldInfo f) {
        Object bean = get(f.getName());
        if (bean != null && !f.getType().getType().isAssignableFrom(bean.getClass())) {
            bean = null;
        }
        if (bean == null)
            bean = get(f.getType().getType());
        return bean;
    }

    private Object getRealBeanForField(FieldInfo f) {
        Object bean = getOriginal(f.getName());
        if (bean != null && !f.getType().getType().isAssignableFrom(bean.getClass())) {
            bean = null;
        }
        if (bean == null)
            bean = getOriginal(f.getType().getType());
        return bean;
    }

    public void addInjectInterceptor(BeanInjectInterceptor interceptor) {
        this.interceptors.add(interceptor);
    }

    private <T> T intercept(T obj) {
        for(BeanInjectInterceptor interceptor : interceptors) {
            obj = interceptor.intercept(obj);
        }

        return obj;
    }

    public <T> void addThreadLocal(T bean) {
        if (bean == null) {
            return;
        }

        final Class beanClass = bean.getClass();
        addThreadLocal(beanClass, bean);
    }

    public <T> void addThreadLocal(Class<T> beanClass, T bean) {

        ThreadLocalWrapper<T> wrapper = wrappersByClass.get(beanClass);
        if (wrapper == null) {
            wrapper = new ThreadLocalWrapper<T>();
            wrapper.setBean(bean);
            wrappersByClass.put(beanClass, wrapper);
            T proxyBean = (T) Enhancer.create(beanClass, beanClass.getInterfaces(), wrapper);
            add(beanClass, proxyBean);
        } else {
            wrapper.setBean(bean);
        }
    }

    public <T> void clearThreadLocal(T bean) {
        clearThreadLocal(bean.getClass());
    }

    public <T> T getThreadLocal(Class<T> beanClass) {
        ThreadLocalWrapper<T> wrapper = wrappersByClass.get(beanClass);
        return wrapper.threadLocal.get();
    }

    public <T> void clearThreadLocal(Class<T> beanClass) {
        ThreadLocalWrapper<T> wrapper = wrappersByClass.get(beanClass);
        if (wrapper != null) {
            wrapper.setBean(null);
        }
    }

    public void clearThreadLocals() {
        for(Entry<Class,ThreadLocalWrapper> entry : wrappersByClass.entrySet()) {
            entry.getValue().setBean(null);
        }
    }

    private final class ThreadLocalWrapper<T> implements MethodInterceptor {

        private final ThreadLocal<T> threadLocal = new ThreadLocal<T>();

        public final void setBean(T bean) {
            threadLocal.set(bean);
        }

        @Override
        public Object intercept(Object proxy, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
            T instance = threadLocal.get();
            if (instance == null) {
                throw new IllegalStateException("Method was called on thread local instance before the instance had been set");
            }
            return method.invoke(instance, objects);
        }
    }
}
