package com.vonhof.webi.file;

import com.vonhof.webi.HttpException;
import com.vonhof.webi.RequestHandler;
import com.vonhof.webi.Webi;
import com.vonhof.webi.WebiContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.ServletException;

/**
 * File request handler. Servers local static file resources.
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class FileRequestHandler implements RequestHandler {
    
    @Inject
    protected Webi webi;
    
    /**
     * Mime type map
     */
    private final Map<String,String> mimeTypes = new HashMap<String, String>();
    /**
     * Document root
     */
    private String docRoot = "./";
    /**
     * Default mime type - used when no mime type could be found from file extension
     */
    private String defaultMimeType = "text/plain";
    
    /**
     * Index file - defaults to this file if url points to directory
     */
    private String indexFileName = "index.html";

    @Override
    public final void handle(WebiContext ctxt) throws IOException, ServletException {
        String filePath = String.format("%s%s",docRoot,ctxt.getPath());
        File file = new File(filePath);
        if (file.isDirectory()) {
            //Redirect to url with ending /
            if (!ctxt.getPath().endsWith("/")) {
                ctxt.redirect(ctxt.getPath()+"/");
                return;
            }
            
            String indexFilePath = filePath+"/"+indexFileName;
            
            File indexFile = new File(indexFilePath);
            if (!indexFile.exists()) {
                 serveDir(ctxt, file);
                return;
            } else {
                file = indexFile;
            }
                
        }
        
        if (file.exists()) {
            serveFile(ctxt, file);;
        } else {
            unknownFile(ctxt, file, filePath);
        }
    }
    
    protected void unknownFile(WebiContext ctxt,File file,String path) throws IOException {
        ctxt.sendError(new HttpException(HttpException.NOT_FOUND,"Not found"));
        if (!file.getName().equalsIgnoreCase("favicon.ico")) {
            System.out.println("File not found:"+path);
        }
    }
    
    /**
     * Called when url points to directory and no index file was found. 
     * Outputs dir contents
     * @param req
     * @param dir
     * @throws IOException 
     */
    protected void serveDir(WebiContext req,File dir) throws IOException {
        
        req.setResponseType("text/html");
        File[] files = dir.listFiles();
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><title>Dir:")
                    .append(dir.getAbsolutePath())
                .append("</title></head><body><table><thead><tr><th>File</th><th>Size</th></tr></thead><tbody>");
        
        for(File file:files) {
            String path = file.getName();
            if (file.isDirectory())
                path += "/";
            sb.append("<tr>")
                    .append(String.format("<td><a href=\"%s\">%s</a></td>",
                        path,path))
                    .append(String.format("<td>%s</td>","??"));
        }
        
        sb.append("</tbody></table></body></html>");
        req.getOutputStream().write(sb.toString().getBytes());
        req.flushBuffer();
    }
    /**
     * Serve file from local filesystem. Gets mime type from file
     * @param req
     * @param file
     * @throws IOException 
     */
    protected void serveFile(WebiContext req,File file) throws IOException {
        req.setHeader("Content-type",getResponseType(file));
        
        long lastModified = file.lastModified();
        long reqLastModified = req.getRequest().getDateHeader("If-Modified-Since");
        
        req.setDateHeader("Last-Modified",lastModified);
        
        if (reqLastModified > 0 && lastModified <= reqLastModified) {
            req.setStatus(304);
            req.flushBuffer();
            return;
        }
        
        FileInputStream fileIn = new FileInputStream(file);
        while(fileIn.available() > 0) {
            req.getOutputStream().write(fileIn.read());
        }
        req.flushBuffer();
    }
    
    /**
     * Get mime type from file name (extension)
     * @param file
     * @return 
     */
    protected String getResponseType(File file) {
        String ext = getFileExt(file);
        String mimeType = mimeTypes.get(ext);
        if (mimeType == null) {
            mimeType = defaultMimeType;
        }
        return mimeType;
    }
    
    /**
     * Get file extension 
     * @param file
     * @return 
     */
    protected String getFileExt(File file) {
        String[] parts = file.getName().split("\\.");
        return parts[parts.length-1].toLowerCase();
    }

    /**
     * Set default index file for directories
     * @param indexFileName 
     */
    public void setIndexFileName(String indexFileName) {
        this.indexFileName = indexFileName;
    }

    /**
     * Set document root
     * @param docRoot 
     */
    public void setDocumentRoot(String docRoot) {
        this.docRoot = docRoot;
    }

    public String getDocumentRoot() {
        return docRoot;
    }
    
    

    /**
     * Add mime type to file extension mapping
     * @param fileExtension
     * @param mimeType 
     */
    public void addMimeType(String fileExtension, String mimeType) {
        mimeTypes.put(fileExtension.toLowerCase(), mimeType.toLowerCase());
    }

    /**
     * Set default mime type - used as fallback for file extensions 
     * not found in mime type map
     * @param defaultMimeType 
     */
    public void setDefaultMimeType(String defaultMimeType) {
        this.defaultMimeType = defaultMimeType;
    }
    
    /**
     * Get file handler initialized with typical mime types
     * @return 
     */
    public static FileRequestHandler getStandardFileHandler() {
        FileRequestHandler out = new FileRequestHandler();
        out.addDefaultMimeTypes();
        
        return out;
    }
    
    public void addDefaultMimeTypes() {
        addMimeType("jpg","image/jpeg");
        addMimeType("png","image/png");
        addMimeType("gif","image/gif");
        addMimeType("bmp","image/bmp");
        addMimeType("html","text/html");
        addMimeType("htm","text/html");
        addMimeType("css","text/css");
        addMimeType("json","application/json");
        addMimeType("xml","text/xml");
        addMimeType("js","text/javascript");
        addMimeType("woff","application/x-font-woff");
        addMimeType("ttf","font/ttf");
        addMimeType("eot","font/eot");
        addMimeType("otf","font/otf");
    }

}
