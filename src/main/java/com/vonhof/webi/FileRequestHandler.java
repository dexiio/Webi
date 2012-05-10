package com.vonhof.webi;

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
    private Webi webi;
    
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

    public void handle(WebiContext ctxt) throws IOException, ServletException {
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
            ctxt.sendError(new HttpException(HttpException.NOT_FOUND,"Not found"));
            if (!file.getName().equalsIgnoreCase("favicon.ico")) {
                System.out.println("File not found:"+filePath);
            }
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
        if (!webi.isDevMode())
            req.setDateHeader("Last-Modified",file.lastModified());
        
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
        String[] parts = file.getName().split("\\.");
        String ext = parts[parts.length-1].toLowerCase();
        String mimeType = mimeTypes.get(ext);
        if (mimeType == null)
            mimeType = defaultMimeType;
        return mimeType;
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
        out.addMimeType("jpg","image/jpeg");
        out.addMimeType("png","image/png");
        out.addMimeType("gif","image/gif");
        out.addMimeType("bmp","image/bmp");
        out.addMimeType("html","text/html");
        out.addMimeType("htm","text/html");
        out.addMimeType("css","text/css");
        out.addMimeType("json","application/json");
        out.addMimeType("xml","text/xml");
        out.addMimeType("js","text/javascript");
        out.addMimeType("woff","application/x-font-woff");
        out.addMimeType("ttf","font/ttf");
        out.addMimeType("eot","font/eot");
        out.addMimeType("otf","font/otf");
        
        return out;
    }

}
