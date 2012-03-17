package com.vonhof.webi;

import com.vonhof.babelshark.BabelShark;
import com.vonhof.babelshark.Output;
import com.vonhof.babelshark.exception.MappingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import quicktime.util.StringHandle;

public class WebRequest {
    private static final BabelShark bs = BabelShark.getInstance();
    private final String path;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final HttpMethod httpMethod;

    public WebRequest(String path, HttpServletRequest request, HttpServletResponse response) {
        this.path = path.isEmpty() ? "" : path.substring(1);;
        this.request = request;
        this.response = response;
        httpMethod = HttpMethod.valueOf(request.getMethod());
    }

    public String getPath() {
        return path;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public HttpServletResponse getResponse() {
        return response;
    }

    public Map<String, String[]> getGETParms() {
        return request.getParameterMap();
    }

    public String getContentType() {
        return request.getContentType();
    }

    public HttpMethod getMethod() {
        return httpMethod;
    }
    
    public String getResponseType() {
        String format = request.getParameter("format");
        if (format == null) {
            format = bs.getDefaultType();
        }
        return bs.getMimeType(format);
    }

    public InputStream getBodyStream() throws IOException {
        return request.getInputStream();
    }

    public void sendError(Throwable ex) throws IOException {
        StringWriter writer =  new StringWriter();
        writer.append(ex.toString()).append('\n');
        if (ex.getMessage() != null)
            writer.append(ex.getMessage()).append('\n');
        
        for(StackTraceElement st:ex.getStackTrace()) {
            writer.append(st.toString()).append('\n');
        }
        
        System.out.println(writer.toString());
        
        if (!response.isCommitted()) {
            if (ex instanceof HttpException) {
                response.setStatus(((HttpException)ex).getCode());
            } else {
                response.setStatus(500);
            }
            response.setHeader("Content-type", "text/plain");
            response.getWriter().println(writer);
            response.flushBuffer();
        }
    }

    public void respond(Object output) throws IOException {
        response.setHeader("Content-type", getResponseType());
        final Output out = new Output(response.getOutputStream(),getResponseType());
        try {
            bs.write(out,output);
        } catch (MappingException ex) {
            throw new IOException(ex);
        }
        response.flushBuffer();
    }
    
}
