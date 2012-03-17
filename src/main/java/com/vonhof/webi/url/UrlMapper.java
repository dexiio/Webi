package com.vonhof.webi.url;

import com.vonhof.webi.HttpMethod;
import java.lang.reflect.Method;

/**
 *
 * @author Henrik Hofmeister <hh@cphse.com>
 */
public interface UrlMapper {
    public void expose(Object obj);
    public void expose(Object obj, String baseUrl);
    public Method getMethodByURL(String path,HttpMethod method);
    public Object getObjectByURL(String path);
}
