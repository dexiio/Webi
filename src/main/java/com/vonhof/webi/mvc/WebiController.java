package com.vonhof.webi.mvc;

import com.vonhof.babelshark.annotation.Ignore;
import com.vonhof.babelshark.annotation.Name;
import com.vonhof.babelshark.node.ArrayNode;
import com.vonhof.babelshark.node.ObjectNode;
import com.vonhof.babelshark.node.SharkNode;
import com.vonhof.babelshark.reflect.ClassInfo;
import com.vonhof.babelshark.reflect.FieldInfo;
import com.vonhof.babelshark.reflect.MethodInfo;
import com.vonhof.babelshark.reflect.MethodInfo.Parameter;
import com.vonhof.webi.HttpMethod;
import com.vonhof.webi.PathPatternMap;
import com.vonhof.webi.Webi;
import com.vonhof.webi.WebiContext;
import com.vonhof.webi.annotation.Body;
import com.vonhof.webi.annotation.Parm;
import com.vonhof.webi.annotation.Path;
import com.vonhof.webi.session.WebiSession;
import com.vonhof.webi.websocket.SocketService;
import java.lang.reflect.Type;
import java.util.*;
import java.util.Map.Entry;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

/**
 * Various Webi specific methods
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
@Path("webi")
@Name("webi")
public class WebiController {

    @Inject
    private UrlMapper urlMapper;
    
    @Inject 
    private Webi webi;
    
    public ObjectNode service(WebiContext ctxt) {
        ObjectNode out = new ObjectNode();
        out.put("url",ctxt.getBase());
        
        //Generate web socket service information
        ObjectNode socketsNode = out.putObject("sockets");
        for(Entry<String,SocketService> service:webi.getWebSockets().entrySet()) {
            ClassInfo<?> classInfo = service.getValue().getClientClass();
            String name = "";
            if (classInfo.hasAnnotation(Name.class))
                name = classInfo.getAnnotation(Name.class).value();
            if (name.isEmpty())
                name = classInfo.getType().getSimpleName().toLowerCase();
            ObjectNode socketNode = socketsNode.putObject(name);
            socketNode.put("url", service.getKey());
            socketNode.put("type", classInfo.getName());
        }

        //Generate output for methods/actions
        List<ClassInfo> models = new ArrayList<ClassInfo>();

        ObjectNode methodsNode = out.putObject("methods");
        final Map<String, Map<String, EnumMap<HttpMethod, MethodInfo>>> methods = urlMapper.getMethods();
        for (Entry<String, Map<String, EnumMap<HttpMethod, MethodInfo>>> baseEntry : methods.entrySet()) {
            final String baseUrl = baseEntry.getKey();
            Object ctrl = urlMapper.getObjectByURL(baseUrl);
            ObjectNode ctrlNode = methodsNode.putObject(getTypeName(ClassInfo.from(ctrl.getClass())));
            ctrlNode.put("url", baseUrl);
            ObjectNode ctrlMethodsNode = ctrlNode.putObject("methods");

            writeMethods(ctrlMethodsNode, baseEntry, baseUrl, models);
        }

        ObjectNode modelsNode = out.putObject("models");

        for (ClassInfo<?> model : models) {
            if (model.inherits(SharkNode.class) 
                    || model.inherits(Collection.class)
                    || model.inherits(Map.class)) 
                continue;
        
            ObjectNode modelNode = modelsNode.putObject(getTypeName(model));
            writeModel(modelsNode,modelNode, model);
        }

        return out;
    }

    private void writeModel(ObjectNode modelsNode, ObjectNode modelNode, ClassInfo<?> model) {
        if (model.inherits(SharkNode.class) 
                    || model.inherits(Collection.class)
                    || model.inherits(Map.class)) 
            return;
        
        for (FieldInfo field : model.getFields().values()) {
            if (field.hasAnnotation(Ignore.class)) {
                continue;
            }

            Name nameAnno = field.getAnnotation(Name.class);

            ObjectNode fieldNode = modelNode.putObject(field.getName());
            ClassInfo type = field.getType();
            if (nameAnno != null) {
                fieldNode.put("name", nameAnno.value());
                fieldNode.put("description", nameAnno.description());
                fieldNode.put("required", nameAnno.required());
            } else {
                fieldNode.put("name", field.getName());
                fieldNode.put("description", "");
                fieldNode.put("required", false);
            }

            if (type.isPrimitive() || type.isCollection() || type.isMap()) {
                fieldNode.put("type", getTypeName(type));
                
                if (type.isEnum()) {
                    fieldNode.put("type", "enum");
                    ArrayNode enumNode = fieldNode.putArray("enum");
                    for(Object val:type.getEnumConstants()) {
                        enumNode.add((Enum)val);
                    }
                } else if (type.isCollection()) {
                    ClassInfo<?> genType = ClassInfo.from(Object.class);
                    if (type.getGenericTypes().length > 0) {
                        genType = getGenType(type.getGenericTypes()[0]);
                    }
                    
                    String typeName = getTypeName(genType);
                    if (modelsNode.get(typeName) != null) return;
                    ObjectNode subModelNode = modelsNode.putObject(typeName);
                    writeModel(modelsNode,subModelNode, genType);
                    return;
                } else if (type.isMap()) {
                    ClassInfo<?> genType = getMapParm(type);
                    String typeName = getTypeName(genType);
                    if (modelsNode.get(typeName) != null) return;
                    ObjectNode subModelNode = modelsNode.putObject(typeName);
                    writeModel(modelsNode,subModelNode, genType);
                    return;
                }
            } else {
                ObjectNode subModelNode = modelsNode.putObject(getTypeName(type));
                writeModel(modelsNode,subModelNode, type);
            }
        }
    }
    
    private ClassInfo<?> getMapParm(ClassInfo<?> type) {
        if (type.isMap()) {
            Type parmType = type.getGenericTypes()[1];
            return getGenType(parmType);
        }
        return ClassInfo.from(Object.class);
    }
    
    private ClassInfo<?> getGenType(Type parmType) {
        
        if (parmType instanceof Class) {
            Class genType = (Class)parmType;
            return ClassInfo.from(genType);
        } else if (parmType instanceof ParameterizedTypeImpl) {
            ParameterizedTypeImpl genType = (ParameterizedTypeImpl)parmType;
            Class<?> rawType = genType.getRawType();
            return ClassInfo.from(rawType);
        }
        return ClassInfo.from(Object.class);
    }
    private String getTypeName(ClassInfo<?> type) {
        if (type.isArray()) {
            return getTypeName(ClassInfo.from(type.getComponentType()))+"[]";
        }
        if (type.isCollection()) {
            if (type.getGenericTypes().length > 0) {
                ClassInfo<?> genType = getGenType(type.getGenericTypes()[0]);
                return getTypeName(genType)+"[]";
            } else {
                return "Object[]";
            }
            
        }
        if (type.isMap()) {
            ClassInfo<?> mapParm = getMapParm(type);
            return "Map<String,"+getTypeName(mapParm)+">";
        }
        
        Name nameAnno = type.getAnnotation(Name.class);
        if (nameAnno != null)
            return nameAnno.value();
        return type.getType().getSimpleName();
    }

    private void writeMethods(ObjectNode ctrlObject,
            Entry<String, Map<String, EnumMap<HttpMethod, MethodInfo>>> baseEntry,
            String baseUrl, List<ClassInfo> models) {

        for (Entry<String, EnumMap<HttpMethod, MethodInfo>> methodPathEntry : baseEntry.getValue().entrySet()) {
            final String methodUrl = methodPathEntry.getKey();

            for (Entry<HttpMethod, MethodInfo> methodEntry : methodPathEntry.getValue().entrySet()) {
                final HttpMethod httpMethod = methodEntry.getKey();
                final MethodInfo method = methodEntry.getValue();
                writeMethod(ctrlObject, methodUrl, baseUrl, httpMethod, method, models);
            }

        }
    }

    private void writeMethod(ObjectNode ctrlObject, String methodUrl, String baseUrl,
            HttpMethod httpMethod, MethodInfo method, List<ClassInfo> models) {
        String methodName = method.getName();

        ArrayNode methodsObject = (ArrayNode) ctrlObject.get(methodName);
        if (methodsObject == null) {
            methodsObject = ctrlObject.putArray(methodName);
        }
        ObjectNode methodObject = methodsObject.addObject();

        methodObject.put("name", methodName);
        methodObject.put("method", httpMethod);
        methodObject.put("url", String.format("%s/%s", baseUrl, methodUrl));

        ClassInfo returnType = method.getReturnType();
        if (!returnType.isA(Void.TYPE)) {
            methodObject.put("returns", getTypeName(returnType));
            if (!returnType.isPrimitive()) {
                models.add(returnType);
            }
        }

        Map<String, Parameter> parms = method.getParameters();
        if (!parms.isEmpty()) {
            writeParms(methodObject.putArray("args"), parms, models);
        }
    }

    private void writeParms(ArrayNode argsObject, Map<String, Parameter> parms, List<ClassInfo> models) {
        for (Entry<String, Parameter> entry : parms.entrySet()) {
            Parameter parm = entry.getValue();

            ClassInfo type = parm.getType();
            if (type.hasAnnotation(Ignore.class)) {
                continue;
            }

            if (type.inherits(WebiSession.class)
                    || type.inherits(WebiContext.class)
                    || type.inherits(HttpServletRequest.class)
                    || type.inherits(HttpServletResponse.class)) {
                continue;
            }

            String transport = "GET";
            Parm parmAnno = null;
            if (parm.hasAnnotation(Parm.class)) {
                parmAnno = parm.getAnnotation(Parm.class);
                switch (parmAnno.type()) {
                    case INJECT:
                    case SESSION:
                        continue;
                    default:
                        transport = parmAnno.type().name();

                }

            }

            ObjectNode arg = argsObject.addObject();

            if (parmAnno != null
                    && parmAnno.defaultValue() != null
                    && parmAnno.defaultValue().length > 0) {
                if (parmAnno.defaultValue().length == 1) {
                    if (!parmAnno.defaultValue()[0].isEmpty()) {
                        arg.put("defaultValue", parmAnno.defaultValue()[0]);
                    }
                } else {
                    ArrayNode defValueArray = arg.putArray("defaultValue");
                    for (String val : parmAnno.defaultValue()) {
                        defValueArray.add(val);
                    }
                }
            }

            arg.put("required", (parmAnno != null && parmAnno.required()));

            if (parm.hasAnnotation(Body.class)) {
                transport = "BODY";
                arg.put("required", true);

                if (!type.isPrimitive()) {
                    models.add(type);
                }
            }

            if (transport.equals("AUTO")) {
                transport = "GET";
            }


            arg.put("name", entry.getKey());
            arg.put("type", getTypeName(type));
            if (type.isEnum()) {
                arg.put("type", "enum");
                ArrayNode enumNode = arg.putArray("enum");
                for(Object val:type.getEnumConstants()) {
                    enumNode.add((Enum)val);
                }
            }

            arg.put("transport", transport);
        }
    }
}
