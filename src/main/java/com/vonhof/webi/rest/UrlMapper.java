package com.vonhof.webi.rest;

import com.vonhof.webi.HttpMethod;
import java.lang.reflect.Method;

/**
 * Url mappers are used to control the REST web service handler
 * @author Henrik Hofmeister <hh@cphse.com>
 */
public interface UrlMapper {
    public void expose(Object obj);
    public void expose(Object obj, String baseUrl);
    public Method getMethodByURL(String path,HttpMethod method);
    public Object getObjectByURL(String path);
}
