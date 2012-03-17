package com.vonhof.webi;

import com.thoughtworks.paranamer.AdaptiveParanamer;
import com.thoughtworks.paranamer.Paranamer;
import com.vonhof.babelshark.BabelShark;
import com.vonhof.babelshark.ConvertUtils;
import com.vonhof.babelshark.Input;
import com.vonhof.babelshark.ReflectUtils;
import com.vonhof.babelshark.annotation.Ignore;
import com.vonhof.webi.annotation.Body;
import com.vonhof.webi.annotation.Parm;
import com.vonhof.webi.url.UrlMapper;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class WebiRequestHandler extends AbstractHandler {

    private final Paranamer paranamer = new AdaptiveParanamer();
    private final UrlMapper urlMapper;
    private final BabelShark bs = BabelShark.getInstance();

    public WebiRequestHandler(UrlMapper urlMapper) {
        this.urlMapper = urlMapper;
    }
    
    public void handle(String target,
                    Request baseRequest,
                    HttpServletRequest request,
                    HttpServletResponse response)
                    throws IOException, ServletException {
        handle(new WebRequest(target, request, response));
    }

    public void handle(WebRequest req) throws IOException, ServletException {
        try {
            Object output = invokeAction(req);
            req.respond(output);
        } catch (Throwable ex) {
            req.sendError(ex);
        }
    }

    private Object invokeAction(WebRequest req) throws HttpException {
        final String path = req.getPath();
        try {
            Object obj = urlMapper.getObjectByURL(path);
            if (obj == null) {
                throw new HttpException(HttpException.NOT_FOUND, "Not found");
            }
            Method method = urlMapper.getMethodByURL(path, req.getMethod());
            if (method == null) {
                throw new HttpException(HttpException.NOT_FOUND, "Not found");
            }

            final List<Parameter> methodParms = fromMethod(method);
            final Object[] callParms = mapRequestToMethod(req,methodParms);
            
            Object output = method.invoke(obj, callParms);
            return refineValue(output,method.getReturnType());
        } catch (HttpException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new HttpException(HttpException.INTERNAL_ERROR, "Server error");
        }
    }

    

    private Object[] mapRequestToMethod(WebRequest req,final List<Parameter> methodParms) throws Exception {
        final Map<String, String[]> GET = req.getGETParms();
        final Object[] callParms = new Object[methodParms.size()];
        
        if (!methodParms.isEmpty()) {

            parmLoop:
            for (int i = 0; i < methodParms.size(); i++) {
                callParms[i] = null;
                Parameter p = methodParms.get(i);
                if (p.ignore()) {
                    continue;
                }
                
                String name = p.getName();

                switch (p.getParmType()) {
                    case PATH:
                        break;
                    default:
                        if (p.hasAnnotation(Body.class)) {
                            callParms[i] = readBODYParm(req,p);
                            break;
                        }
                        
                        String[] values = GET.get(name);
                        if (values == null) {
                            values = p.getDefaultValue();
                        }

                        callParms[i] = readGETParm(p, values);
                        break;
                }
                
                callParms[i] = refineValue(callParms[i],p.getType());
                
                if (p.isRequired() && isMissing(callParms[i]))
                    throw new HttpException(HttpException.CLIENT,"Bad request - missing required parameter: "+name);
            }
        }
        return callParms;
    }
    
    private Object refineValue(Object value,Class type) {
        if (value != null) 
            return value;
        //Make sure certain values never is null
        if (type.equals(String.class))
            return "";
        if (type.isArray())
            return new Object[0];
        if (Set.class.isAssignableFrom(type))
            return Collections.EMPTY_SET;
        if (Map.class.isAssignableFrom(type))
            return Collections.EMPTY_MAP;
        if (List.class.isAssignableFrom(type))
            return Collections.EMPTY_LIST;
        return value;
    }
    
    private boolean isMissing(Object value) {
        boolean missing = value == null;
        if (!missing && value instanceof String) {
            missing = ((String)value).isEmpty();
        }

        if (!missing && value instanceof Date) {
            missing = ((Date)value).getTime() == 0;
        }

        if (!missing && value instanceof Collection) {
            missing = ((Collection)value).isEmpty();
        }

        if (!missing && value instanceof Map) {
            missing = ((Map)value).isEmpty();
        }
        return missing;
    }
    
    private Object readBODYParm(WebRequest req,Parameter p) throws Exception {
        return bs.read(new Input(req.getBodyStream(), req.getContentType()), p.getType());
    }

    private Object readGETParm(Parameter p, String[] values) throws Exception {
        if (values != null && values.length > 0) {
            if (ReflectUtils.isCollection(p.getType())) {
                if (p.getType().
                        isArray()) {
                    Object[] realValues = new Object[values.length];
                    for (int x = 0; x < values.length; x++) {
                        realValues[x] = ConvertUtils.convert(values[x], p.getType());
                    }
                    return realValues;
                } else {
                    Collection list = (Collection) p.getType().newInstance();
                    list.addAll(Arrays.asList(values));
                    return list;
                }

            } else if (ReflectUtils.isPrimitive(p.getType())) {
                return ConvertUtils.convert(values[0], p.getType());
            }
        }
        return null;
    }

    private List<Parameter> fromMethod(Method m) {
        String[] parmNames = paranamer.lookupParameterNames(m, false);

        Class<?>[] parmTypes = m.getParameterTypes();
        Annotation[][] parmAnnotations = m.getParameterAnnotations();

        List<Parameter> out = new LinkedList<Parameter>();
        for (int i = 0; i < parmTypes.length; i++) {
            out.add(new Parameter(parmNames[i], parmTypes[i], parmAnnotations[i]));
        }

        return out;
    }

    private static final class Parameter {

        private final String name;
        private final Class type;
        private final Map<Class<? extends Annotation>, Annotation> annotations =
                new HashMap<Class<? extends Annotation>, Annotation>();
        private final Parm parmAnno;

        public Parameter(String name, Class type, Annotation[] annotations) {
            this.type = type;
            for (Annotation a : annotations) {
                this.annotations.put(a.annotationType(), a);
            }

            parmAnno = getAnnotation(Parm.class);
            if (parmAnno != null
                    && !parmAnno.value().
                    isEmpty()) {
                name = parmAnno.value();
            }
            this.name = name;

        }

        public boolean ignore() {
            return hasAnnotation(Ignore.class);
        }

        public Parm.Type getParmType() {
            return parmAnno != null ? parmAnno.type() : Parm.Type.AUTO;
        }

        public String[] getDefaultValue() {
            return parmAnno != null ? parmAnno.defaultValue() : new String[0];
        }

        public <T extends Annotation> T getAnnotation(Class<T> type) {
            return (T) this.annotations.get(type);
        }

        public <T extends Annotation> boolean hasAnnotation(Class<T> type) {
            return this.annotations.containsKey(type);
        }

        public String getName() {
            return name;
        }

        public Class getType() {
            return type;
        }

        private boolean isRequired() {
            return parmAnno != null ? parmAnno.required() : false;
        }
    }
}
