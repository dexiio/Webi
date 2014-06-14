package com.vonhof.webi.client;

import com.vonhof.babelshark.BabelShark;
import com.vonhof.babelshark.BabelSharkInstance;
import com.vonhof.babelshark.Input;
import com.vonhof.babelshark.Output;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class RESTClient {
    
    private BabelSharkInstance bs = BabelShark.getDefaultInstance();
    private String contentType = "application/json";
    
    private final String baseUrl;

    public RESTClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public Request req() {
        return new Request();
    }
    
    protected <T> T send(Request req,String method,Object body,Class<T> responseClass) throws MalformedURLException, IOException {
        URL url = new URL(req.toURL());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestMethod(method.toUpperCase());
        conn.setInstanceFollowRedirects(true);
        if (body != null) {
            conn.setRequestProperty("Content-Type", contentType);
        }

        if (!req.headers.isEmpty()) {

            for(Entry<String,String> header:req.headers.entrySet()) {
                conn.addRequestProperty(header.getKey(), header.getValue());
            }
        }
        
        if (body != null) {

            conn.setDoOutput(true);
            bs.write(new Output(conn.getOutputStream(),contentType), body);
        }
        
        if (responseClass != null) {
            String responseType = conn.getHeaderField("Content-type");
            T out = bs.read(new Input(conn.getInputStream(), responseType), responseClass);
            conn.disconnect();
            return out;
        } else {
            String out = IOUtils.toString(conn.getInputStream());
            conn.disconnect();
            return (T) out;
        }
    }
    
    public static Request from(String baseUrl) {
        return new RESTClient(baseUrl).req();
    }
    
    public final class Request {
        private List<String> paths = new LinkedList<String>();
        private Map<String,String> queryParams = new HashMap<String, String>();
        private Map<String,String> headers = new HashMap<String, String>();

        private Request() {
            
        }
            
        public Request p(String path) {
            paths.add(path);
            return this;
        }
        
        public Request q(String name,String value) {
            queryParams.put(name, value);
            return this;
        }
        
        public Request h(String name,String value) {
            headers.put(name, value);
            return this;
        }
        
        private String toURL() {
            StringBuilder sb = new StringBuilder();
            sb.append(baseUrl);
            sb.append("/");
            
            for(String path:paths) {
                sb.append(path);
                sb.append("/");
            }
            
            if (!queryParams.isEmpty()) {
                sb.append("?");
                for(Entry<String,String> parm:queryParams.entrySet()) {
                    sb.append(parm.getKey())
                            .append("=")
                            .append(parm.getValue());
                }
            }
            
            return sb.toString();
        }
        
        public <T> T get(Class<T> responseType) throws IOException {
            return send(this, "GET", null, responseType);
        }
        
        public void get() throws IOException {
            get(null);
        }
        
        public <T> T head(Class<T> responseType) throws IOException {
            return send(this, "HEAD", null, responseType);
        }
        
        public void head() throws IOException {
            head(null);
        }
        
        public <T> T delete(Class<T> responseType) throws IOException {
            return send(this, "DELETE", null, responseType);
        }
        
        public void delete() throws IOException {
            delete(null);
        }
        
        public <T> T put(Object body,Class<T> responseType) throws IOException {
            return send(this, "PUT", body, responseType);
        }
        
        public void put(Object body) throws IOException {
            put(body, null);
        }
        
        public void put() throws IOException {
            put(null);
        }
        
        public <T> T post(Object body,Class<T> responseType) throws IOException {
            return send(this, "POST", body, responseType);
        }
        
        public void post(Object body) throws IOException {
            post(body, null);
        }
        
        public void post() throws IOException {
            post(null);
        }
        
        
    }
}
