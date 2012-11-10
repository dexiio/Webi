package com.vonhof.webi.rest;

import com.vonhof.babelshark.annotation.Ignore;
import com.vonhof.babelshark.reflect.ClassInfo;
import com.vonhof.babelshark.reflect.MethodInfo;
import com.vonhof.webi.HttpMethod;
import com.vonhof.webi.annotation.Handler;
import com.vonhof.webi.annotation.Path;
import com.vonhof.webi.bean.BeanContext;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of the url mapper
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class DefaultUrlMapper implements UrlMapper {
    
    private final static Logger LOG = Logger.getLogger(BeanContext.class.getName());

    private final Map<String, Object> controllers = new HashMap<String, Object>();
    private final Map<String,Map<String, EnumMap<HttpMethod,MethodInfo>>> actions = new HashMap<String, Map<String, EnumMap<HttpMethod, MethodInfo>>>();

    @Override
    public void expose(Object obj) {
        expose(obj, getObjectURL(obj));
    }

    @Override
    public void expose(Object obj, String baseUrl) {
        controllers.put(baseUrl, obj);
        if (!actions.containsKey(baseUrl))
            actions.put(baseUrl, new HashMap<String, EnumMap<HttpMethod, MethodInfo>>());
        Map<String, EnumMap<HttpMethod, MethodInfo>> ctrlActions = actions.get(baseUrl);
        
        ClassInfo<?> classInfo = ClassInfo.from(obj.getClass());
        for (MethodInfo m : classInfo.getMethods()) {
            if (!m.isPublic() || m.hasAnnotation(Ignore.class) || m.hasAnnotation(Handler.class))
                continue;
            
            Path path = m.getAnnotation(Path.class);
            HttpMethod httpMethod = path != null ? path.method() : HttpMethod.GET;
            String url = getMethodURL(m);
            if (!ctrlActions.containsKey(url))
                ctrlActions.put(url,new EnumMap<HttpMethod, MethodInfo>(HttpMethod.class));
            ctrlActions.get(url).put(httpMethod,m);
            LOG.log(Level.INFO,String.format("Mapped %s/%s (%s) to %s:%s",
                                                            baseUrl,url,httpMethod,
                                                            obj.getClass().getSimpleName(),m.getName()));
        }
    }

    public Map<String, Map<String, EnumMap<HttpMethod, MethodInfo>>> getMethods() {
        return actions;
    }
    

    protected String getMethodURL(MethodInfo m) {
        Path path = m.getAnnotation(Path.class);
        if (path != null && !path.value().isEmpty()) {
            return path.value().toLowerCase();
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
    public MethodInfo getMethodByURL(String url,HttpMethod method) {
        int firstSep = url.indexOf("/");
        if (firstSep > 1) {
            String ctrlUrl = url.substring(0,firstSep);
            if (ctrlUrl.endsWith("/"))
                ctrlUrl = ctrlUrl.substring(0,ctrlUrl.length()-1);
            
            final Map<String, EnumMap<HttpMethod, MethodInfo>> ctrlActions = actions.get(ctrlUrl);
            if (ctrlActions == null)
                return null;
            
            String name = url.substring(firstSep + 1);
            if (name.endsWith("/"))
                name = name.substring(0,name.length()-1);
            
            final EnumMap<HttpMethod, MethodInfo> methods = ctrlActions.get(name.toLowerCase());
            if (methods != null)
                return methods.get(method);
        }
        return null;
    }
}
