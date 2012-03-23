package com.vonhof.webi.rest;

import com.thoughtworks.paranamer.AdaptiveParanamer;
import com.thoughtworks.paranamer.Paranamer;
import com.vonhof.babelshark.*;
import com.vonhof.babelshark.annotation.Ignore;
import com.vonhof.babelshark.exception.MappingException;
import com.vonhof.webi.HttpException;
import com.vonhof.webi.RequestHandler;
import com.vonhof.webi.Webi;
import com.vonhof.webi.WebiContext;
import com.vonhof.webi.annotation.Body;
import com.vonhof.webi.annotation.Parm;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.websocket.WebSocket;

/**
 * REST web service request handling
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class RESTRequestHandler implements RequestHandler {
    
    @Inject
    private Webi webi;
    
    @Inject
    private BabelShark bs;
    
    private final Paranamer paranamer = new AdaptiveParanamer();
    private final UrlMapper urlMapper;
    

    public RESTRequestHandler(UrlMapper urlMapper) {
        this.urlMapper = urlMapper;
    }
    
    public RESTRequestHandler() {
        this(new DefaultUrlMapper());
    }
    
    public void expose(Object obj) {
        urlMapper.expose(obj);
        webi.addBean(obj);
    };
    
    public void expose(Object obj, String baseUrl) {
        urlMapper.expose(obj, baseUrl);
        webi.addBean(obj);
    }
    
    public void handle(WebiContext ctxt) throws IOException, ServletException {
        try {
            
            setResponseType(ctxt);
            
            //Invoke REST method
            Object output = invokeAction(ctxt);
            
            ctxt.setHeader("Content-type", ctxt.getResponseType());
            final Output out = new Output(ctxt.getOutputStream(),ctxt.getResponseType());
            try {
                bs.write(out,output);
            } catch (MappingException ex) {
                throw new IOException(ex);
            }
            ctxt.flushBuffer();
        } catch (Throwable ex) {
            ctxt.sendError(ex);
        }
    }
    
    public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
        WebiContext ctxt = new WebiContext(request.getPathInfo(),request, null);
        ctxt.setResponseType(protocol);
        try {
            Object out = invokeAction(ctxt);
            if (out instanceof WebSocket)
                return (WebSocket) out;
            else
                throw new HttpException(HttpException.INTERNAL_ERROR,
                        "Wrong return type for websocket method");
            
        } catch (HttpException ex) {
            Logger.getLogger(RESTRequestHandler.class.getName()).
                    log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     * Invoke action based on path and http method
     * @param req
     * @return
     * @throws HttpException 
     */
    private Object invokeAction(WebiContext req) throws HttpException {
        String path = req.getPath();
        if (!path.isEmpty())
            path = path.substring(1);
        try {
            Object obj = urlMapper.getObjectByURL(path);
            if (obj == null) {
                throw new HttpException(HttpException.NOT_FOUND, "Not found");
            }
            Method method = urlMapper.getMethodByURL(path, req.getMethod());
            if (method == null) {
                throw new HttpException(HttpException.NOT_FOUND, "Not found");
            }

            final List<Parameter> methodParms = getMethodParameters(method);
            final Object[] callParms = getMethodArguments(req,methodParms);
            
            Object output = method.invoke(obj, callParms);
            return refineValue(output,method.getReturnType());
        } catch (HttpException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new HttpException(HttpException.INTERNAL_ERROR, ex);
        }
    }
    /**
     * Set response type from webi context
     * @param ctxt 
     */
    private void setResponseType(WebiContext ctxt) {
        String format = ctxt.GET().get("format");
        if (format == null) {
            format = bs.getDefaultType();
        }
        //Set response type
        ctxt.setResponseType(bs.getMimeType(format));
    }

    /**
     * Converts HTTP request into a suitable argument list for the specified list of parameters 
     * @param req
     * @param methodParms
     * @return
     * @throws Exception 
     */
    private Object[] getMethodArguments(WebiContext req,final List<Parameter> methodParms) throws Exception {
        final Object[] out = new Object[methodParms.size()];
        if (!methodParms.isEmpty()) {

            parmLoop:
            for (int i = 0; i < methodParms.size(); i++) {
                Parameter p = methodParms.get(i);
                if (p.ignore()) {
                    continue;
                }
                
                Object value = getMethodArgument(req, p);
                value = refineValue(value,p.getType());
                
                if (p.isRequired() && isMissing(value))
                    throw new HttpException(HttpException.CLIENT,"Bad request - missing required parameter: "+p.getName());
                
                out[i] = value;
            }
        }
        return out;
    }
    
    /**
     * Converts HTTP request into the specified method argument (Parameter) (From headers, GET parms etc.)
     * @param req
     * @param p
     * @return
     * @throws Exception 
     */
    private Object getMethodArgument(WebiContext req,Parameter p) throws Exception {
        Object value = null;
        String name = p.getName();

        switch (p.getParmType()) {
            case PATH:
                break;
            case HEADER:
                String headerValue = req.getHeader(name);
                if (ReflectUtils.isPrimitive(p.getType())) {
                    value = ConvertUtils.convert(headerValue, p.getType());
                } else {
                    value = headerValue;
                }
                break;
            default:
                if (p.hasAnnotation(Body.class)) {
                    value = readBODYParm(req,p);
                    break;
                }
                if (InputStream.class.isAssignableFrom(p.getType())) {
                    value = req.getInputStream();
                    break;
                }
                if (OutputStream.class.isAssignableFrom(p.getType())) {
                    value = req.getOutputStream();
                    break;
                }
                if (WebiContext.class.isAssignableFrom(p.getType())) {
                    value = req;
                    break;
                }

                String[] values = req.GET().getAll(name);
                if (values == null) {
                    values = p.getDefaultValue();
                }

                value = readGETParm(p, values);
                break;
        }
        return value;
    }
    /**
     * Refine output value
     * @param value
     * @param type
     * @return 
     */
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
    
    /**
     * Determine if parameter value is missing
     * @param value
     * @return 
     */
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
    
    /**
     * Read body from request. Converts the raw string to a value instance 
     * using content type
     * @param req
     * @param p
     * @return
     * @throws Exception 
     */
    private Object readBODYParm(WebiContext req,Parameter p) throws Exception {
        return bs.read(new Input(req.getInputStream(), req.getRequestType()), p.getType());
    }

    /**
     * Read GET parameter into method parameter
     * @param p
     * @param values
     * @return
     * @throws Exception 
     */
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
            return values[0];
        }
        return null;
    }

    /**
     * Get method parameters
     * @param m
     * @return 
     */
    private List<Parameter> getMethodParameters(Method m) {
        String[] parmNames = paranamer.lookupParameterNames(m, false);

        Class<?>[] parmTypes = m.getParameterTypes();
        Annotation[][] parmAnnotations = m.getParameterAnnotations();

        List<Parameter> out = new LinkedList<Parameter>();
        for (int i = 0; i < parmTypes.length; i++) {
            out.add(new Parameter(parmNames[i], parmTypes[i], parmAnnotations[i]));
        }

        return out;
    }

    public boolean checkOrigin(HttpServletRequest request, String origin) {
        return true;
    }

    /**
     * Internal representaion of a method parameter
     * Used in the mapping from HTTP to Method
     */
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
