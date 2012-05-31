package com.vonhof.webi.mvc;

import com.vonhof.babelshark.ReflectUtils;
import com.vonhof.babelshark.annotation.Ignore;
import com.vonhof.babelshark.annotation.Name;
import com.vonhof.babelshark.node.ArrayNode;
import com.vonhof.babelshark.node.ObjectNode;
import com.vonhof.babelshark.node.SharkNode;
import com.vonhof.babelshark.node.SharkType;
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
        List<SharkType> types = new ArrayList<SharkType>();

        ObjectNode methodsNode = out.putObject("methods");
        final Map<String, Map<String, EnumMap<HttpMethod, MethodInfo>>> methods = urlMapper.getMethods();
        for (Entry<String, Map<String, EnumMap<HttpMethod, MethodInfo>>> baseEntry : methods.entrySet()) {
            final String baseUrl = baseEntry.getKey();
            Object ctrl = urlMapper.getObjectByURL(baseUrl);
            ObjectNode ctrlNode = methodsNode.putObject(getTypeName(SharkType.get(ctrl.getClass())));
            ctrlNode.put("url", baseUrl);
            ObjectNode ctrlMethodsNode = ctrlNode.putObject("methods");

            writeMethods(ctrlMethodsNode, baseEntry, baseUrl, types);
        }

        ObjectNode modelsNode = out.putObject("models");

        for (SharkType type : types) {
            if (type.inherits(SharkNode.class) 
                    || type.isMap()
                    || type.isCollection()) 
                continue;
        
            ObjectNode modelNode = modelsNode.putObject(getTypeName(type));
            writeModel(modelsNode,modelNode, type);
        }

        return out;
    }

    private void writeModel(ObjectNode modelsNode, ObjectNode modelNode, final SharkType<?,?> type) {
        if (type.inherits(SharkNode.class) 
                    || type.inherits(Collection.class)
                    || type.inherits(Map.class)) 
            return;
        final ClassInfo<?> typeInfo = ClassInfo.from(type.getType());
        for (final FieldInfo field : typeInfo.getFields().values()) {
            if (field.hasAnnotation(Ignore.class)) {
                continue;
            }
            
            final ClassInfo fieldInfo = field.getType();
            final SharkType fieldType = SharkType.get(fieldInfo);
            
            Name nameAnno = field.getAnnotation(Name.class);

            ObjectNode fieldNode = modelNode.putObject(field.getName());
            
            if (nameAnno != null) {
                fieldNode.put("name", nameAnno.value());
                fieldNode.put("description", nameAnno.description());
                fieldNode.put("required", nameAnno.required());
            } else {
                fieldNode.put("name", field.getName());
                fieldNode.put("description", "");
                fieldNode.put("required", false);
            }

            if (fieldType.isPrimitive() || fieldType.isCollection() || fieldType.isMap()) {
                fieldNode.put("type", getTypeName(fieldType));
                SharkType sharkType = fieldType;
                
                if (fieldInfo.isEnum()) {
                    fieldNode.put("type", "enum");
                    ArrayNode enumNode = fieldNode.putArray("enum");
                    for(Object val:fieldInfo.getEnumConstants()) {
                        enumNode.add((Enum)val);
                    }
                } else if (fieldType.isCollection()) {
                    SharkType genType = fieldType.getValueType();
                    
                    String typeName = getTypeName(genType);
                    if (modelsNode.get(typeName) != null) return;
                    ObjectNode subModelNode = modelsNode.putObject(typeName);
                    writeModel(modelsNode,subModelNode, genType);
                    return;
                } else if (fieldType.isMap()) {
                    SharkType genType = fieldType.getValueType();
                    String typeName = getTypeName(genType);
                    if (modelsNode.get(typeName) != null) return;
                    ObjectNode subModelNode = modelsNode.putObject(typeName);
                    writeModel(modelsNode,subModelNode, genType);
                    return;
                }
            } else {
                ObjectNode subModelNode = modelsNode.putObject(getTypeName(fieldType));
                writeModel(modelsNode,subModelNode, fieldType);
            }
        }
    }
    
    private String getTypeName(SharkType<?,?> type) {
        if (type.isArray()) {
            return getTypeName(type.getValueType())+"[]";
        }
        if (type.isCollection()) {
            return getTypeName(type.getValueType())+"[]";
            
        }
        if (type.isMap()) {
            
            return "Map<String,"+getTypeName(type.getValueType())+">";
        }
        
        Name nameAnno = type.getType().getAnnotation(Name.class);
        if (nameAnno != null)
            return nameAnno.value();
        return type.getType().getSimpleName();
    }

    private void writeMethods(ObjectNode ctrlObject,
            Entry<String, Map<String, EnumMap<HttpMethod, MethodInfo>>> baseEntry,
            String baseUrl, List<SharkType> types) {

        for (Entry<String, EnumMap<HttpMethod, MethodInfo>> methodPathEntry : baseEntry.getValue().entrySet()) {
            final String methodUrl = methodPathEntry.getKey();

            for (Entry<HttpMethod, MethodInfo> methodEntry : methodPathEntry.getValue().entrySet()) {
                final HttpMethod httpMethod = methodEntry.getKey();
                final MethodInfo method = methodEntry.getValue();
                writeMethod(ctrlObject, methodUrl, baseUrl, httpMethod, method, types);
            }

        }
    }

    private void writeMethod(ObjectNode ctrlObject, String methodUrl, String baseUrl,
            HttpMethod httpMethod, MethodInfo method, List<SharkType> types) {
        String methodName = method.getName();

        ArrayNode methodsObject = (ArrayNode) ctrlObject.get(methodName);
        if (methodsObject == null) {
            methodsObject = ctrlObject.putArray(methodName);
        }
        ObjectNode methodObject = methodsObject.addObject();

        methodObject.put("name", methodName);
        methodObject.put("method", httpMethod);
        methodObject.put("url", String.format("%s/%s", baseUrl, methodUrl));

        SharkType returnType = SharkType.get(method.getReturnType());
        if (!returnType.isA(Void.TYPE)) {
            methodObject.put("returns", getTypeName(returnType));
            if (!returnType.isPrimitive()) {
                types.add(returnType);
            }
        }

        Map<String, Parameter> parms = method.getParameters();
        if (!parms.isEmpty()) {
            writeParms(methodObject.putArray("args"), parms, types);
        }
    }

    private void writeParms(ArrayNode argsObject, Map<String, Parameter> parms, List<SharkType> models) {
        for (Entry<String, Parameter> entry : parms.entrySet()) {
            Parameter parm = entry.getValue();

            if (parm.getType().hasAnnotation(Ignore.class)) {
                continue;
            }
            
            SharkType type = SharkType.get(parm.getType());
            
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
            if (parm.getType().isEnum()) {
                arg.put("type", "enum");
                ArrayNode enumNode = arg.putArray("enum");
                for(Object val:parm.getType().getEnumConstants()) {
                    enumNode.add((Enum)val);
                }
            }

            arg.put("transport", transport);
        }
    }
}
