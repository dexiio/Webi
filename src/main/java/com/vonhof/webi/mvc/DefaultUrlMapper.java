package com.vonhof.webi.mvc;

import com.vonhof.webi.HttpMethod;
import com.vonhof.webi.annotation.Path;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of the url mapper
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class DefaultUrlMapper implements UrlMapper {

    private final Map<String, Object> controllers = new HashMap<String, Object>();
    private final Map<String, EnumMap<HttpMethod,Method>> actions = new HashMap<String, EnumMap<HttpMethod,Method>>();

    @Override
    public void expose(Object obj) {
        expose(obj, getObjectURL(obj));
    }

    @Override
    public void expose(Object obj, String baseUrl) {
        controllers.put(baseUrl, obj);
        for (Method m : obj.getClass().getMethods()) {
            Path path = m.getAnnotation(Path.class);
            HttpMethod httpMethod = path != null ? path.method() : HttpMethod.GET;
            
            String url = getMethodURL(m);
            if (!actions.containsKey(url))
                actions.put(url,new EnumMap<HttpMethod, Method>(HttpMethod.class));
            actions.get(url).put(httpMethod,m);
        }
    }

    protected String getMethodURL(Method m) {
        Path path = m.getAnnotation(Path.class);
        if (path != null) {
            return path.value();
        }
        return m.getName().toLowerCase();
    }

    protected String getObjectURL(Object obj) {
        Class<? extends Object> clz = obj.getClass();
        Path path = clz.getAnnotation(Path.class);
        if (path != null) {
            return path.value();
        }
        return obj.getClass().getSimpleName().toLowerCase();
    }

    @Override
    public Object getObjectByURL(String url) {
        String[] parts = url.split("/");
        if (parts.length > 0) {
            return controllers.get(parts[0].toLowerCase());
        }
        return null;
    }

    @Override
    public Method getMethodByURL(String url,HttpMethod method) {
        int firstSep = url.indexOf("/");
        if (firstSep > 1) {
            String name = url.substring(firstSep + 1);
            if (name.endsWith("/"))
                name = name.substring(0,name.length()-1);
            EnumMap<HttpMethod, Method> methods = actions.get(name.toLowerCase());
            if (methods != null)
                return methods.get(method);
        }
        return null;
    }
}
