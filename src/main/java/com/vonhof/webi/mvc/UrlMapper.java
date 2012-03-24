package com.vonhof.webi.mvc;

import com.vonhof.babelshark.reflect.MethodInfo;
import com.vonhof.webi.HttpMethod;

/**
 * Url mappers are used to control the REST web service handler
 * @author Henrik Hofmeister <@vonhofdk>
 */
public interface UrlMapper {
    /**
     * Expose object as controller in rest handler
     * @param obj 
     */
    public void expose(Object obj);
    /**
     * Expose object as controller in rest handler - using specified base url
     * @param obj
     * @param baseUrl 
     */
    public void expose(Object obj, String baseUrl);
    /**
     * Get previously exposed method from path
     * @param path
     * @param method
     * @return 
     */
    public MethodInfo getMethodByURL(String path,HttpMethod method);
    /**
     * Get previously exposed object instance from path
     * @param path
     * @return 
     */
    public Object getObjectByURL(String path);
}
