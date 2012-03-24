package com.vonhof.webi.mvc;

import com.vonhof.babelshark.*;
import com.vonhof.babelshark.annotation.Ignore;
import com.vonhof.babelshark.exception.MappingException;
import com.vonhof.babelshark.reflect.ClassInfo;
import com.vonhof.babelshark.reflect.MethodInfo;
import com.vonhof.babelshark.reflect.MethodInfo.Parameter;
import com.vonhof.webi.HttpException;
import com.vonhof.webi.RequestHandler;
import com.vonhof.webi.Webi;
import com.vonhof.webi.WebiContext;
import com.vonhof.webi.annotation.Body;
import com.vonhof.webi.annotation.Parm;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.websocket.WebSocket;

/**
 * MVC request handling.
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class MVCRequestHandler implements RequestHandler {
    
    @Inject
    private Webi webi;
    
    @Inject
    private BabelSharkInstance bs;
    
    private final UrlMapper urlMapper;
    

    public MVCRequestHandler(UrlMapper urlMapper) {
        this.urlMapper = urlMapper;
    }
    
    public MVCRequestHandler() {
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
            Logger.getLogger(MVCRequestHandler.class.getName()).
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
            
            //Get controller instance
            Object obj = urlMapper.getObjectByURL(path);
            if (obj == null) {
                throw new HttpException(HttpException.NOT_FOUND, "Not found");
            }
            
            //Get method
            MethodInfo method = urlMapper.getMethodByURL(path, req.getMethod());
            if (method == null) {
                throw new HttpException(HttpException.NOT_FOUND, "Not found");
            }

            //Resolve method argumetns from request
            final Object[] callParms = getMethodArguments(req,method);
            
            //Invoke method
            Object output = method.invoke(obj, callParms);
            
            //Refine value before outputting
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
        ctxt.setResponseType(bs.getMimeType(format,true));
    }

    /**
     * Converts HTTP request into a suitable argument list for the specified list of parameters 
     * @param req
     * @param methodParms
     * @return
     * @throws Exception 
     */
    private Object[] getMethodArguments(WebiContext req,MethodInfo method) throws Exception {
        List<Parameter> parms = new ArrayList<Parameter>(method.getParameters().values());
        final Object[] out = new Object[parms.size()];
        for (int i = 0; i < parms.size(); i++) {
            out[i] = getMethodArgument(req, parms.get(i));
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
        if (p.hasAnnotation(Ignore.class)) {
            return null;
        }
        final Parm parmAnno = p.getAnnotation(Parm.class);
        final Parm.Type parmType = parmAnno != null ? parmAnno.type() : Parm.Type.AUTO;
        final String[] defaultValue = parmAnno != null ? parmAnno.defaultValue() : new String[0];
        final boolean required  = parmAnno != null ? parmAnno.required() : false; 

        Object value = null;
        String name = p.getName();

        switch (parmType) {
            case PATH:
                break;
            case HEADER:
                String headerValue = req.getHeader(name);
                if (ReflectUtils.isPrimitive(p.getType().getType())) {
                    value = ConvertUtils.convert(headerValue, p.getType().getType());
                } else {
                    value = headerValue;
                }
                break;
            case INJECT:
                value = webi.getBean(p.getType().getType());
                break;
            default:
                if (p.hasAnnotation(Body.class)) {
                    value = readBODYParm(req,p);
                    break;
                }
                if (p.getType().inherits(InputStream.class)) {
                    value = req.getInputStream();
                    break;
                }
                if (p.getType().inherits(OutputStream.class)) {
                    value = req.getOutputStream();
                    break;
                }
                if (p.getType().inherits(WebiContext.class)) {
                    value = req;
                    break;
                }

                String[] values = req.GET().getAll(name);
                if (values == null) {
                    values = defaultValue;
                }

                value = readGETParm(p, values);
                break;
        }
        
        value = refineValue(value,p.getType());
        
        if (required && isMissing(value))
            throw new HttpException(HttpException.CLIENT,"Bad request - missing required parameter: "+p.getName());
        
        return value;
    }
    /**
     * Refine output value
     * @param value
     * @param type
     * @return 
     */
    private Object refineValue(Object value,ClassInfo type) {
        if (value != null) 
            return value;
        //Make sure certain values never is null
        if (type.isA(String.class))
            return "";
        if (type.isArray())
            return new Object[0];
        if (type.inherits(Set.class))
            return Collections.EMPTY_SET;
        if (type.inherits(Map.class))
            return Collections.EMPTY_MAP;
        if (type.inherits(Collection.class))
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
        return ConvertUtils.convertCollection(p.getType(),values);
    }

}
