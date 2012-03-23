package com.vonhof.webi.bean;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Handles dependency injection
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class BeanContext {
    private final static Logger LOG = Logger.getLogger(BeanContext.class.getName());
    private Map<Class,Object> beans = new HashMap<Class, Object>();
    
    public <T> void add(Class<T> clz,T bean) {
        beans.put(clz, bean);
        inject(bean);
    }
    
    public <T> void add(T bean) {
        beans.put(bean.getClass(), bean);
        inject(bean);
    }
    
    public <T> T get(Class<T> beanClz) {
        return (T) beans.get(beanClz);
    }
    
    public void inject() {
        for(Entry<Class,Object> entry:beans.entrySet()) {
            inject(entry.getValue());
        }
    }
    
    public void inject(Object obj) {
        Class clz = obj.getClass();
        Field[] fields = clz.getDeclaredFields();
        for(Field f:fields) {
            Inject annotation = f.getAnnotation(Inject.class);
            if (annotation == null) 
                continue;
            f.setAccessible(true);
            try {
                Object value = f.get(obj);
                if (value == null) {
                    Object bean = get(f.getType());
                    if (bean != null) {
                        f.set(obj, bean);
                    } else {
                        LOG.log(Level.WARNING,"No bean registered for class: {0}",f.getType().getName());
                    }
                }
            } catch (Throwable ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        if (obj instanceof AfterInject) {
            ((AfterInject)obj).afterInject();
        }
    }
}
