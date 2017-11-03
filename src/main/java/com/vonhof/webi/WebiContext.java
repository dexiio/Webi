package com.vonhof.webi;

import com.vonhof.babelshark.node.SharkNode;
import com.vonhof.webi.bean.BeanScope;
import com.vonhof.webi.session.SessionHandler;
import com.vonhof.webi.session.WebiSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.Callable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.logging.log4j.LogManager;
import org.eclipse.jetty.server.Request;

/**
 * Webi context wraps request, response and paths.
 * @author Henrik Hofmeister <@vonhofdk>
 */
@BeanScope(value = BeanScope.Type.LOCAL, ignored = true)
public class WebiContext {
    private static final ServletFileUpload fileUpload = new ServletFileUpload(new DiskFileItemFactory());
    private final String path;
    private final String base;
    private final HttpServletRequest request;
    private final Request jettyRequest;
    private final HttpServletResponse response;
    private final HttpMethod httpMethod;
    private final ParmMap parmMap;
    private WebiSession session;
    
    private final List<DiskFileItem> uploads;
    private final List<RequestLoadMetricEntry> loadMetricEntries = new ArrayList<>();
    private final Stack<RequestLoadMetricEntry> callStack = new Stack<>();


    private boolean loadMetricsEnabled = false;

    private String responseType = "text/plain";
    private String outputType = null;


    public WebiContext() {
        path = null;
        base = null;
        request = null;
        jettyRequest = null;
        response = null;
        httpMethod = null;
        parmMap = null;
        session = null;
        uploads = null;
    }

    protected WebiContext(String base,String path, Request jettyRequest, HttpServletRequest request, HttpServletResponse response) {
        this.base = base;
        this.path = path;
        this.jettyRequest = jettyRequest;
        this.request = request;
        this.response = response;
        httpMethod = HttpMethod.valueOf(request.getMethod());
        parmMap = new ParmMap(request.getParameterMap());
        
        if (isMultiPart()) {
            List<DiskFileItem> tmp = null;
            try {
                tmp = fileUpload.parseRequest(request);
            } catch (FileUploadException ex) {
                LogManager.getLogger(WebiContext.class).fatal("Failed to read file upload", ex);
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

    public void setRequestHandled(boolean handled) {
        jettyRequest.setHandled(handled);
    }

    public boolean isHandled() {
        return jettyRequest.isHandled();
    }

    public Request getJettyRequest() {
        return jettyRequest;
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
        if (request.getContentType() == null) {
            return null;
        }

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

    public void setStatus(int code) {
        if (!response.isCommitted()) {
            response.setStatus(code);
        }
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
                if (code == 404) {
                    showTrace = false;
                }
                    
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

    public RequestLoadMetricEntry startCall(String description, SharkNode details) {
        RequestLoadMetricEntry out = new RequestLoadMetricEntry(description, System.currentTimeMillis());
        out.setDetails(details);

        if (!callStack.isEmpty()) {
            out.setParentId(callStack.peek().getId());
        }

        callStack.push(out);

        return out;
    }

    public void endCall(RequestLoadMetricEntry entry) {
        entry.endCall();
        loadMetricEntries.add(entry);
        callStack.remove(entry);
    }

    public void addTiming(String description, long timeTaken, SharkNode details) {
        if (!loadMetricsEnabled) {
            return;
        }

        RequestLoadMetricEntry entry = new RequestLoadMetricEntry(description, timeTaken, details);

        if (!callStack.isEmpty()) {
            entry.setParentId(callStack.peek().getId());
        }

        loadMetricEntries.add(entry);
    }

    public void addTiming(String description, Runnable runnable) {
        addTiming(description, runnable, null);
    }

    public void addTiming(String description, Runnable runnable, SharkNode details) {
        long timeStart = System.currentTimeMillis();
        try {
            runnable.run();
        } finally {
            long timeTaken = System.currentTimeMillis() - timeStart;
            addTiming(description, timeTaken, details);
        }
    }

    public <T> T addTiming(String description, Callable<T> runnable) throws Exception {
        return addTiming(description, runnable, null);
    }

    public <T> T addTiming(String description, Callable<T> runnable, SharkNode details) throws Exception {
        long timeStart = System.currentTimeMillis();
        try {
            return runnable.call();
        } finally {
            long timeTaken = System.currentTimeMillis() - timeStart;
            addTiming(description, timeTaken, details);
        }
    }

    public <T> T addTimingNoException(String description, Callable<T> runnable) {
        return addTimingNoException(description, runnable, null);
    }

    public <T> T addTimingNoException(String description, Callable<T> runnable, SharkNode details) {
        long timeStart = System.currentTimeMillis();
        try {
            return runnable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            long timeTaken = System.currentTimeMillis() - timeStart;
            addTiming(description, timeTaken, details);
        }
    }

    public boolean isLoadMetricsEnabled() {
        return loadMetricsEnabled;
    }

    public void setLoadMetricsEnabled(boolean loadMetricsEnabled) {
        this.loadMetricsEnabled = loadMetricsEnabled;
    }

    public List<RequestLoadMetricEntry> getLoadMetricEntries() {
        return loadMetricEntries;
    }

    public WebiSession resolve(SessionHandler resolver) {
        WebiSession s = null;
        if (resolver != null) {
            s = resolver.handle(this);
        }
        if (s == null)
            s = new WebiSession();
        this.session = s;

        return s;
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

    public static class RequestLoadMetricEntry {
        private String description;

        private SharkNode details;

        private long timeTaken;

        private Date timeStarted;

        private UUID parentId;

        private UUID id = UUID.randomUUID();


        public RequestLoadMetricEntry() {
        }

        public RequestLoadMetricEntry(String description, long timeTaken, SharkNode details) {
            this.description = description;
            this.timeTaken = timeTaken;
            this.timeStarted = new Date(System.currentTimeMillis() - timeTaken);
            this.details = details;
        }

        public RequestLoadMetricEntry(String description, long startTime) {
            this.description = description;
            this.timeStarted = new Date(startTime);
        }

        public UUID getParentId() {
            return parentId;
        }

        public UUID getId() {
            return id;
        }

        public void setParentId(UUID parentId) {
            this.parentId = parentId;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public SharkNode getDetails() {
            return details;
        }

        public void setDetails(SharkNode details) {
            this.details = details;
        }

        public long getTimeTaken() {
            return timeTaken;
        }

        public void setTimeTaken(long timeTaken) {
            this.timeTaken = timeTaken;
        }

        public Date getTimeStarted() {
            return timeStarted;
        }

        public void setTimeStarted(Date timeStarted) {
            this.timeStarted = timeStarted;
        }

        public void endCall() {
            this.timeTaken = System.currentTimeMillis() - timeStarted.getTime();
        }
    }
}
