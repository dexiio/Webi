package com.vonhof.webi;

import com.vonhof.webi.session.SessionHandler;
import com.vonhof.webi.session.WebiSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

/**
 * Webi context wraps request, response and paths.
 * @author Henrik Hofmeister <@vonhofdk>
 */
public final class WebiContext {
    private static final ServletFileUpload fileUpload = new ServletFileUpload(new DiskFileItemFactory());
    private final String path;
    private final String base;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final HttpMethod httpMethod;
    private final ParmMap parmMap;
    private final WebiSession session;
    
    private final List<DiskFileItem> uploads;
    private String responseType = "text/plain";
    private String outputType = null;
    
    
    protected WebiContext(String base,String path, HttpServletRequest request, HttpServletResponse response,SessionHandler resolver) {
        this.base = base;
        this.path = path;
        this.request = request;
        this.response = response;
        httpMethod = HttpMethod.valueOf(request.getMethod());
        parmMap = new ParmMap(request.getParameterMap());
        
        WebiSession s = null;
        if (resolver != null) {
            s = resolver.handle(this);
        }
        if (s == null)
            s = new WebiSession();
        this.session = s;
        if (isMultiPart()) {
            List<DiskFileItem> tmp = null;
            try {
                tmp = fileUpload.parseRequest(request);
            } catch (FileUploadException ex) {
                Logger.getLogger(WebiContext.class.getName()).log(Level.SEVERE, null, ex);
            }
            uploads = tmp;
        } else {
            uploads = null;
        }
    }
    
    public boolean isMultiPart() {
        return ServletFileUpload.isMultipartContent(request);
    }

    public WebiSession getSession() {
        return session;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public HttpServletResponse getResponse() {
        return response;
    }
    
    public List<DiskFileItem> getUploads() throws FileUploadException {
        return uploads;
    }
    
    public DiskFileItem getUpload(String name) throws FileUploadException {
        if (uploads == null) 
            return null;
        for(DiskFileItem item:uploads) {
            if (item.getFieldName().equals(name))
                return item;
        }
        return null;
    }

    /**
     * Get base path
     * @return 
     */
    public String getBase() {
        return base;
    }

    /**
     * Get requested path without the base path
     * @return 
     */
    public String getPath() {
        return path;
    }

    /**
     * get HTTP GET variables
     * @return 
     */
    public ParmMap getParameterMap() {
        return parmMap;
    }

    /**
     * Get request body mime type
     * @return 
     */
    public String getRequestType() {
        return request.getContentType().replaceAll("(?uis); *charset=.*$","");
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

    public String getOutputType() {
        return outputType != null ? outputType : responseType;
    }

    public void setOutputType(String outputType) {
        this.outputType = outputType;
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
        boolean showTrace = true;
        
        if (!response.isCommitted()) {
            if (ex instanceof HttpException) {
                int code = ((HttpException)ex).getCode();
                response.setStatus(code);
                if (code == 404)
                    showTrace = false;
                    
            } else {
                response.setStatus(500);
            }
            response.setHeader("Content-type", "text/plain");
            if (showTrace) {
                System.out.println(error);
                getOutputStream().write(error.getBytes());
            }
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
    
    public void setDateHeader(String name,long timestamp) {
        response.setDateHeader(name, timestamp);
    }
    
    public void setIntHeader(String name,int value) {
        response.setIntHeader(name, value);
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

    public void sendError(int i, String msg) throws IOException {
        response.sendError(i, msg);
    }

    
    
    public static final class ParmMap {
        private final Map<String,String[]> inner;

        public ParmMap(Map<String, String[]> inner) {
            this.inner = inner;
        }
        public String get(String name) {
            return get(name, null);
        }
        public String get(String name,String defaultValue) {
            String[] values = inner.get(name);
            if (values != null && values.length > 0)
                return values[0];
            return defaultValue;
        }
        public String[] getAll(String name) {
            return inner.get(name);
        }

        public boolean contains(String name) {
            return inner.containsKey(name);
        }
    
    }
}
