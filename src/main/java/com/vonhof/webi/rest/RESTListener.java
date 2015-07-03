package com.vonhof.webi.rest;


import com.vonhof.babelshark.reflect.ClassInfo;
import com.vonhof.babelshark.reflect.MethodInfo;

public interface RESTListener {

    void invokeResult(long executionTime, ClassInfo targetClass, MethodInfo methodInfo, boolean success);
}
