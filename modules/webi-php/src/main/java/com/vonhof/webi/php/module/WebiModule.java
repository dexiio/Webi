package com.vonhof.webi.php.module;

import com.caucho.quercus.QuercusException;
import com.caucho.quercus.env.*;
import com.caucho.quercus.module.AbstractQuercusModule;
import com.vonhof.babelshark.BabelShark;
import com.vonhof.babelshark.BabelSharkInstance;
import com.vonhof.babelshark.ConvertUtils;
import com.vonhof.babelshark.ReflectUtils;
import com.vonhof.babelshark.annotation.Name;
import com.vonhof.babelshark.node.SharkType;
import com.vonhof.babelshark.reflect.MethodInfo;
import com.vonhof.babelshark.reflect.MethodInfo.Parameter;
import com.vonhof.webi.HttpMethod;
import com.vonhof.webi.Webi;
import com.vonhof.webi.WebiContext;
import com.vonhof.webi.annotation.Body;
import com.vonhof.webi.annotation.Parm;
import com.vonhof.webi.rest.RESTServiceHandler;
import com.vonhof.webi.rest.TextFile;
import com.vonhof.webi.rest.UrlMapper;
import com.vonhof.webi.session.WebiSession;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.fileupload.FileItem;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class WebiModule extends AbstractQuercusModule {

    private final RESTServiceHandler service;
    private final Webi webi;

    public WebiModule(Webi webi,RESTServiceHandler service) {
        this.webi = webi;
        this.service = service;
    }

    public WebiWrapper webi(Env env) {
        return new WebiWrapper(service, env);
    }

    public class WebiWrapper {

        private final RESTServiceHandler service;
        private final Env env;
        private final Map<String, ControllerWrapper> controllers = new HashMap<String, ControllerWrapper>();

        public WebiWrapper(RESTServiceHandler webi, Env env) {
            this.service = webi;
            this.env = env;
            

            UrlMapper urlMapper = service.getUrlMapper();


            final Map<String, Map<String, EnumMap<HttpMethod, MethodInfo>>> methods = urlMapper.getMethods();
            for (Map.Entry<String, Map<String, EnumMap<HttpMethod, MethodInfo>>> baseEntry : methods.entrySet()) {
                final String baseUrl = baseEntry.getKey();
                Object ctrl = urlMapper.getObjectByURL(baseUrl);
                String typeName = getTypeName(SharkType.get(ctrl.getClass()));
                controllers.put(typeName, new ControllerWrapper(ctrl,baseEntry.getValue()));
            }
        }

        public ControllerWrapper __call(Env env,String name, ArrayValue arguments) {
            return controllers.get(name);
        }

        private String getTypeName(SharkType<?, ?> type) {
            if (type.isArray()) {
                return getTypeName(type.getValueType()) + "[]";
            }
            if (type.isCollection()) {
                return getTypeName(type.getValueType()) + "[]";

            }
            if (type.isMap()) {

                return "Map<String," + getTypeName(type.getValueType()) + ">";
            }

            Name nameAnno = type.getType().getAnnotation(Name.class);
            if (nameAnno != null) {
                return nameAnno.value();
            }
            return type.getType().getSimpleName();
        }
    }

    public class ControllerWrapper {

        private final Object instance;
        private final Map<String, List<MethodInfo>> methods = new HashMap<String, List<MethodInfo>>();
        
        public ControllerWrapper(Object instance,Map<String, EnumMap<HttpMethod, MethodInfo>> ctrlMethods) {
            this.instance = instance;
            
            for(Entry<String,EnumMap<HttpMethod,MethodInfo>> entry:ctrlMethods.entrySet()) {
                for(MethodInfo method:entry.getValue().values()) {
                    String name = method.getName();
                    if (!methods.containsKey(name))
                        methods.put(name,new ArrayList<MethodInfo>());
                    methods.get(name).add(method);                            
                }
            }
        }
        
        
        public Object __call(Env env,String name, ArrayValue arguments) {
            BabelSharkInstance bs = BabelShark.getDefaultInstance();
            
            JavaValue map = (JavaValue) env.getGlobalValue("CONTEXT");
            WebiContext ctxt = (WebiContext) map.toJavaObject();
            Object[] args = new Object[0];
            
            List<MethodInfo> overloads = methods.get(name);
            if (overloads == null || overloads.isEmpty())
                throw new QuercusException(String.format("Method not found %s",name));
            
            for(MethodInfo method:overloads) {
                try {
                    
                    Map<String, Parameter> parms = method.getParameters();
                    args = new Object[parms.size()];
                    
                    int i = 0;
                    int argI = 0;
                    for(Parameter p:parms.values()) {
                        Parm parmAnno = p.getAnnotation(Parm.class);
                        final Parm.Type parmType = parmAnno != null ? parmAnno.type() : Parm.Type.AUTO;
                        final String[] defaultValue = parmAnno != null ? parmAnno.defaultValue() : new String[0];
                        Object value = null;
                        
                        switch (parmType) {
                            case PATH:
                                break;
                            case HEADER:
                                String headerValue = ctxt.getHeader(name);
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
                                value = ctxt.getSession().get(name);
                                if (!p.getType().isAssignableFrom(value.getClass())) {
                                    value = ctxt.getSession().get(p.getType().getName());
                                }

                                break;
                            default:
                                if (p.getType().inherits(InputStream.class)) {
                                    value = ctxt.getInputStream();
                                    break;
                                }
                                if (p.getType().inherits(OutputStream.class)) {
                                    value = ctxt.getOutputStream();
                                    break;
                                }
                                if (p.getType().inherits(WebiContext.class)) {
                                    value = ctxt;
                                    break;
                                }

                                if (p.getType().isA(FileItem.class)) {
                                    throw new UnsupportedOperationException();
                                }

                                if (p.getType().isA(TextFile.class)) {
                                    throw new UnsupportedOperationException();
                                }

                                if (p.getType().inherits(WebiSession.class)) {
                                    value = ctxt.getSession();
                                    break;
                                }

                                if (p.getType().inherits(WebiContext.ParmMap.class)) {
                                    value = ctxt.getParameterMap();
                                    break;
                                }
                                if (defaultValue != null && defaultValue.length > 0)
                                    value = bs.convert(defaultValue[0],p.getType());
                                Value phpValue = arguments.get(new LongValue(argI));
                                argI++;
                                
                                if (!phpValue.isNull()) {
                                    if (ReflectUtils.isMappable(p.getType().getType())) {
                                        Map mapValue = phpValue.toJavaMap(env, Map.class);
                                        value = bs.convert(mapValue,p.getType().getType());
                                    } else if (ReflectUtils.isCollection(p.getType().getType())) {
                                        Collection collValue = phpValue.toJavaCollection(env, p.getType().getType());
                                        value = bs.convert(collValue,p.getType());
                                    } else {
                                        value = bs.convert(phpValue.toJavaString(),p.getType());
                                    }
                                }
                                
                                break;
                        }
                        
                        args[i] = value;
                        
                        i++;
                    }
                    return method.invoke(instance, args);
                } catch (Exception ex) {
                    Logger.getLogger(WebiModule.class.getName()).log(Level.SEVERE, null, ex);
                    throw new QuercusException(String.format("Exception was thrown when calling %s(%s);",name,args),ex);
                }
            }
            return null;
        }
    }
}
