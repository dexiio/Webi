package com.vonhof.webi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class WebiContext {
    private final String path;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final HttpMethod httpMethod;
    private final GETMap GETMap;
    private String responseType = "text/plain";
    
    protected WebiContext(String path, HttpServletRequest request, HttpServletResponse response) {
        this.path = path;
        this.request = request;
        this.response = response;
        httpMethod = HttpMethod.valueOf(request.getMethod());
        GETMap = new GETMap(request.getParameterMap());
    }

    /**
     * Get requested path
     * @return 
     */
    public String getPath() {
        return path;
    }

    /**
     * get HTTP GET variables
     * @return 
     */
    public GETMap GET() {
        return GETMap;
    }

    /**
     * Get request body mime type
     * @return 
     */
    public String getRequestType() {
        return request.getContentType();
    }

    /**
     * Get HTTP method
     * @return 
     */
    public HttpMethod getMethod() {
        return httpMethod;
    }

    /**
     * Set response mime type
     * @param responseType 
     */
    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }
    
    /**
     * Get response mime type
     * @return 
     */
    public String getResponseType() {
        return responseType;
    }

    /**
     * Get input stream
     * @return
     * @throws IOException 
     */
    public InputStream getInputStream() throws IOException {
        return request.getInputStream();
    }
    
    /**
     * Get output stream
     * @return
     * @throws IOException 
     */
    public OutputStream getOutputStream() throws IOException {
        return response.getOutputStream();
    }

    /**
     * Send exception to client
     * @param ex
     * @throws IOException 
     */
    public void sendError(Throwable ex) throws IOException {
        String error = toString(ex);
        System.out.println(error);
        
        if (!response.isCommitted()) {
            if (ex instanceof HttpException) {
                response.setStatus(((HttpException)ex).getCode());
            } else {
                response.setStatus(500);
            }
            response.setHeader("Content-type", "text/plain");
            getOutputStream().write(error.getBytes());
            response.flushBuffer();
        }
    }
    private String toString(Throwable ex) {
        StringBuilder  sb =  new StringBuilder();
        sb.append(ex.toString()).append(':');
        if (ex.getMessage() != null)
            sb.append(ex.getMessage());
        
        sb.append("\nStack trace:\n");
        
        for(StackTraceElement st:ex.getStackTrace()) {
            sb.append("\t at").append(st.toString()).append('\n');
        }
        if (ex.getCause() != null && !ex.getCause().getClass().equals(ex.getClass())) {
            sb.append("Caused by:\n");
            sb.append(toString(ex.getCause()));
        }
        return sb.toString();
    }

    /**
     * Get header from request
     * @param name
     * @return 
     */
    public String getHeader(String name) {
        return request.getHeader(name);
    }
    
    /**
     * Set header on response
     * @param name
     * @param value 
     */
    public void setHeader(String name, String value) {
        response.setHeader(name, value);
    }
    
    /**
     * Flush response buffer
     * @throws IOException 
     */
    public void flushBuffer() throws IOException {
        response.flushBuffer();
    }

    public void redirect(String path) throws IOException {
        response.sendRedirect(path);
        flushBuffer();
    }

    
    
    public static final class GETMap {
        private final Map<String,String[]> inner;

        public GETMap(Map<String, String[]> inner) {
            this.inner = inner;
        }
        public String get(String name) {
            String[] values = inner.get(name);
            if (values != null && values.length > 0)
                return values[0];
            return null;
        }
        public String[] getAll(String name) {
            return inner.get(name);
        }
    
    }
}
