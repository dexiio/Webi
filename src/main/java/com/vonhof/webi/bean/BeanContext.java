package com.vonhof.webi.bean;

import com.vonhof.babelshark.reflect.ClassInfo;
import com.vonhof.babelshark.reflect.FieldInfo;
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
    private Map<Class,Object> beansByClass = new HashMap<Class, Object>();
    private Map<String,Object> beansById = new HashMap<String, Object>();
    
    public <T> void add(Class<T> clz,T bean) {
        beansByClass.put(clz, bean);
        inject(bean);
    }
    
    public <T> void add(T bean) {
        beansByClass.put(bean.getClass(), bean);
        inject(bean);
    }
    
    public <T> void add(String id,T bean) {
        beansById.put(id,bean);
        inject(bean);
    }
    
    public <T> T get(Class<T> beanClz) {
        return (T) beansByClass.get(beanClz);
    }
    
    public <T> T get(String id) {
        return (T) beansById.get(id);
    }
    
    public void inject() {
        for(Entry<Class,Object> entry:beansByClass.entrySet()) {
            inject(entry.getValue());
        }
        
        for(Entry<String,Object> entry:beansById.entrySet()) {
            inject(entry.getValue());
        }
    }
    
    public void inject(Object obj) {
        ClassInfo<?> clz = ClassInfo.from(obj.getClass());
        Map<String,FieldInfo> fields = clz.getFields();
        boolean injectedAll = true;
        for(FieldInfo f:fields.values()) {
            Inject annotation = f.getAnnotation(Inject.class);
            if (annotation == null) 
                continue;
            
            f.forceAccessible();
            try {
                Object value = f.get(obj);
                if (value == null) {
                    Object bean = get(f.getName());
                    if (bean != null && !f.getType().getType().isAssignableFrom(bean.getClass())) {
                        bean = null;
                    }
                    if (bean == null)
                        bean = get(f.getType().getType());
                    if (bean != null) {
                        f.set(obj, bean);
                    } else {
                        injectedAll = false;
                        LOG.log(Level.WARNING,"No bean registered for class: {0}",f.getType().getName());
                    }
                }
            } catch (Throwable ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        //Only call if all fields were injected (it may be too soon)
        if (injectedAll && obj instanceof AfterInject) {
            ((AfterInject)obj).afterInject();
        }
    }
}
