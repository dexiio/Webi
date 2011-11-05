package com.cphse.webi.mapping;

import com.cphse.webi.mapping.annotation.Ignore;
import com.cphse.webi.mapping.annotation.Name;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ObjectInspector {
    private final Map<Class,Map<String,Field>> fieldMapCache 
                            = new HashMap<Class, Map<String, Field>>();

    public Object get(Object obj,String name) {
        try {
            Field field = getFieldMap(obj).get(name);
            if (field.isAccessible())
                return field.get(obj);
            return getGetter(obj, field).invoke(obj);
        } catch (Exception ex) {
            Logger.getLogger(ObjectInspector.class.getName()).
                    log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public void set(Object obj,String name,Object value) {
        try {
            Field field = getFieldMap(obj).get(name);
            if (field.isAccessible())
                field.set(obj,value);
            else
                getSetter(obj, field).invoke(obj,value);
        } catch (Exception ex) {
            Logger.getLogger(ObjectInspector.class.getName()).
                    log(Level.SEVERE, null, ex);
        }
    }
    public List<Object> getValues(Object obj) {
        Map<String, Field> fieldMap = getFieldMap(obj);
        List<Object> out = new ArrayList<Object>();
        
        for(String fieldName:fieldMap.keySet()) {
            out.add(get(obj,fieldName));
        }
        
        return out;
    }
    
    public Map<String,Field> getFieldMap(Object obj) {
        if (!fieldMapCache.containsKey(obj.getClass())) {
            Map<String,Field> out = new HashMap<String,Field>();
            Field[] fields = obj.getClass().getFields();
            for(Field f:fields) {
                if (f.isAnnotationPresent(Ignore.class))
                    continue;
                String name = f.getName();
                Name nameAnno = f.getAnnotation(Name.class);
                if (nameAnno != null) 
                    name = nameAnno.value();
                out.put(name,f);
            }

            fieldMapCache.put(obj.getClass(), out);
        }
        return fieldMapCache.get(obj.getClass());
    }
    
    
    private Method getSetter(Object obj,Field field) {
        try {
            return obj.getClass().getMethod("set"+field.getName(),field.getType());
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(ObjectInspector.class.getName()).
                    log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(ObjectInspector.class.getName()).
                    log(Level.SEVERE, null, ex);
        }
        return null;
    }
    private Method getGetter(Object obj,Field field) {
        try {
            return obj.getClass().getMethod("get"+field.getName());
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(ObjectInspector.class.getName()).
                    log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(ObjectInspector.class.getName()).
                    log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    
}
