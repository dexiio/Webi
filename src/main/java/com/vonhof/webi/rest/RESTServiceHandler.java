package com.vonhof.webi.rest;

import com.vonhof.babelshark.*;
import com.vonhof.babelshark.annotation.Ignore;
import com.vonhof.babelshark.exception.MappingException;
import com.vonhof.babelshark.node.ObjectNode;
import com.vonhof.babelshark.node.SharkNode;
import com.vonhof.babelshark.node.SharkNode.NodeType;
import com.vonhof.babelshark.reflect.ClassInfo;
import com.vonhof.babelshark.reflect.MethodInfo;
import com.vonhof.babelshark.reflect.MethodInfo.Parameter;
import com.vonhof.webi.*;
import com.vonhof.webi.WebiContext.ParmMap;
import com.vonhof.webi.annotation.Body;
import com.vonhof.webi.annotation.Handler;
import com.vonhof.webi.annotation.Parm;
import com.vonhof.webi.bean.AfterInject;
import com.vonhof.webi.session.WebiSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.Map.Entry;
import javax.inject.Inject;
import javax.servlet.ServletException;
import org.apache.commons.fileupload.FileItem;

/**
 * MVC request handling.
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class RESTServiceHandler implements RequestHandler,AfterInject {
    
    @Inject
    private Webi webi;
    
    @Inject
    private BabelSharkInstance bs;

    private final UrlMapper urlMapper;
    private RESTListener listener;
    private ExceptionHandler exceptionHandler = new DefaultExceptionHandler();
    private List<String> okOrigins = new ArrayList<String>();


    public RESTServiceHandler(UrlMapper urlMapper) {
        this.urlMapper = urlMapper;
    }
    
    public RESTServiceHandler() {
        this(new DefaultUrlMapper());
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public UrlMapper getUrlMapper() {
        return urlMapper;
    }
    
    public void expose(Object obj) {
        urlMapper.expose(obj);
        webi.addBean(obj);
    }
    
    public void expose(Object obj, String baseUrl) {
        urlMapper.expose(obj, baseUrl);
        webi.addBean(obj);
    }
    
    public void expose(String id,Object obj) {
        urlMapper.expose(obj);
        webi.addBean(id,obj);
    }

    public void addAllowedOrigin(String host) {
        okOrigins.add(host.toLowerCase());
    }

    public RESTListener getListener() {
        return listener;
    }

    public void setListener(RESTListener listener) {
        this.listener = listener;
    }

    @Override
    public void handle(WebiContext ctxt) throws IOException, ServletException {
        
        setResponseType(ctxt);

        String origin = ctxt.getRequest().getHeader("Origin");

        if (origin != null && !origin.isEmpty()) {
            if (okOrigins.contains(origin)) {
                ctxt.setHeader("Access-Control-Allow-Origin", origin);
                ctxt.setHeader("Access-Control-Allow-Headers", "origin, content-type, accept, api-key");
            }
        }
        
        Object output = null;

        if (ctxt.getMethod().equals(HttpMethod.OPTIONS)) {
            ctxt.flushBuffer();
            return;
        }

        try {
            //Invoke REST method
            output = invokeAction(ctxt);
        } catch (Throwable ex) {
            output = exceptionHandler.handle(ctxt,ex);
        } finally {
            if (ctxt.getResponse().isCommitted() || ctxt.isHandled()) {
                //Response is already send - exit
                return;
            }
            
            ctxt.setHeader("Content-type", ctxt.getResponseType());
            
            final Output out = new Output(ctxt.getOutputStream(),ctxt.getOutputType());
            
            try {
                bs.write(out,output);
            } catch (MappingException ex) {
                throw new IOException(ex);
            }
            
            ctxt.flushBuffer();
        }
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
        long startTime = System.currentTimeMillis();
        boolean success = false;
        ClassInfo targetClass = null;
        MethodInfo targetMethod = null;
        try {
            
            //Get controller instance
            Object obj = urlMapper.getObjectByURL(path);
            if (obj == null) {
                throw new HttpException(HttpException.NOT_FOUND, "Not found");
            }

            final ClassInfo<?> info = ClassInfo.from(obj.getClass());
            targetClass = info;

                    //Get handlers
            List<MethodInfo> handlers = info.getMethodsByAnnotation(Handler.class);
            
            //Invoke before request handlers
            for(MethodInfo handler:handlers) {
                Handler annotation = handler.getAnnotation(Handler.class);
                if (annotation.value().equals(Handler.Type.BEFORE_REQUEST)) {
                    invoke(obj, handler, req);
                }
            }
            
            //Get method
            MethodInfo method = urlMapper.getMethodByURL(path, req.getMethod());
            if (method == null) {
                throw new HttpException(HttpException.NOT_FOUND, "Not found");
            }

            targetMethod = method;

            //Invoke controller method
            Object output =  invoke(obj, method, req);
            
            //Invoke after request handlers
            for(MethodInfo handler:handlers) {
                Handler annotation = handler.getAnnotation(Handler.class);
                if (annotation.value().equals(Handler.Type.AFTER_REQUEST)) {
                    invoke(obj, handler, req);
                }
            }

            success = true;
            return output;
            
        } catch (InvocationTargetException ex) {
            if (ex.getTargetException() instanceof HttpException)
                throw (HttpException)ex.getTargetException();
            throw new HttpException(HttpException.INTERNAL_ERROR, ex.getTargetException());
        } catch (HttpException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new HttpException(HttpException.INTERNAL_ERROR, ex);
        } finally {
            emitExecutionResult(
                    System.currentTimeMillis() - startTime,
                    targetClass,
                    targetMethod,
                    success
            );
        }
    }

    private void emitExecutionResult(long executionTime, ClassInfo targetClass, MethodInfo targetMethod, boolean success) {
        if (listener == null || targetClass == null || targetMethod == null) {
            return;
        }

        listener.invokeResult(executionTime, targetClass, targetMethod, success);
    }

    /**
     * Set response type from webi context
     * @param ctxt 
     */
    private void setResponseType(WebiContext ctxt) {
        String format = ctxt.getParameterMap().get("format");
        if (format == null) {
            format = bs.getDefaultType();
        }
        //Set response type
        ctxt.setResponseType(bs.getMimeType(format,true));
    }
    
    private Object invoke(Object obj,MethodInfo method,WebiContext req) throws Exception {
        //Resolve method argumetns from request
        final Object[] callParms = getMethodArguments(req,method);

        //Invoke method
        Object output = method.invoke(obj, callParms);

        //Refine value before outputting
        return refineValue(output,method.getReturnType());
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
        
        Map<Integer,Parameter> bodyParms = new HashMap<Integer,Parameter>();
        for (int i = 0; i < parms.size(); i++) {
            if (parms.get(i).hasAnnotation(Body.class)) {
                bodyParms.put(i,parms.get(i));
            } else {
                out[i] = getMethodArgument(req, parms.get(i));
            }
        }
        if (!bodyParms.isEmpty()) {
            SharkNode body = readBody(req);
            if (bodyParms.size() == 1) {
                Entry<Integer, Parameter> entry = bodyParms.entrySet().iterator().next();
                out[entry.getKey()] = bs.read(body, entry.getValue().getType());
            } else if (body.is(NodeType.MAP)) {
                ObjectNode obj = (ObjectNode) body;
                for(Entry<Integer, Parameter> entry:bodyParms.entrySet()) {
                    final String name = entry.getValue().getName();
                    SharkNode val = obj.get(name);
                    if (val == null)
                        out[entry.getKey()] = null;
                    else
                        out[entry.getKey()] = bs.read(val, entry.getValue().getType());
                }
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
                if (ReflectUtils.isSimple(p.getType().getType())) {
                    value = ConvertUtils.convert(headerValue, p.getType().getType());
                } else {
                    value = headerValue;
                }
                break;
            case INJECT:
                value = webi.getBean(p.getType().getType());
                break;
            case SESSION:
                value = req.getSession().get(name);
                if (!p.getType().isAssignableFrom(value.getClass())) {
                    value = req.getSession().get(p.getType().getName());
                }
                
                break;
            default:
                if (p.hasAnnotation(Body.class)) {
                    //Is handled outside this method while there could be more than 1
                    return null;
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
                
                if (p.getType().isA(FileItem.class)) {
                    value = req.getUpload(name);
                    break;
                }
                
                if (p.getType().isA(TextFile.class)) {
                    value = new TextFile(req.getUpload(name));
                    break;
                }
                
                if (p.getType().inherits(WebiSession.class)) {
                    value = req.getSession();
                    break;
                }
                
                if (p.getType().inherits(ParmMap.class)) {
                    value = req.getParameterMap();
                    break;
                }

                String[] values = req.getParameterMap().getAll(name);
                if (values == null) {
                    values = defaultValue;
                }

                value = readGETParm(p, values);
                break;
        }
        
        value = refineValue(value,p.getType());
        
        if (required && isMissing(value))
            throw new HttpException(HttpException.BAD_REQUEST,"Bad request - missing required parameter: "+p.getName());
        
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
    private SharkNode readBody(WebiContext req) throws Exception {
        return bs.read(new Input(req.getInputStream(), req.getRequestType()), SharkNode.class);
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

    public void afterInject() {
        webi.addBean(UrlMapper.class,urlMapper);
    }

}
