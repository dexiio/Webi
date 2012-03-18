package com.vonhof.webi;

import com.vonhof.webi.HttpException;
import com.vonhof.webi.RequestHandler;
import com.vonhof.webi.WebiContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;

/**
 * File request handler. Servers local static file resources.
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class FileRequestHandler implements RequestHandler {
    
    private final Map<String,String> mimeTypes = new HashMap<String, String>();
    private String docRoot = "./";
    private String defaultMimeType = "text/plain";
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
            System.out.println("File not found:"+filePath);
        }
    }
    
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
    
    protected void serveFile(WebiContext req,File file) throws IOException {
        req.setResponseType(getResponseType(file));

        FileInputStream fileIn = new FileInputStream(file);
        while(fileIn.available() > 0) {
            req.getOutputStream().write(fileIn.read());
        }
        req.flushBuffer();
    }
    
    protected String getResponseType(File file) {
        String[] parts = file.getName().split("\\.");
        String ext = parts[parts.length-1].toLowerCase();
        String mimeType = mimeTypes.get(ext);
        if (mimeType == null)
            mimeType = defaultMimeType;
        return mimeType;
    }

    public void setIndexFileName(String indexFileName) {
        this.indexFileName = indexFileName;
    }

    public void setDocumentRoot(String docRoot) {
        this.docRoot = docRoot;
    }

    public void addMimeType(String fileExtension, String mimeType) {
        mimeTypes.put(fileExtension.toLowerCase(), mimeType.toLowerCase());
    }

    public void setDefaultMimeType(String defaultMimeType) {
        this.defaultMimeType = defaultMimeType;
    }
    
    
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
        
        return out;
    }

}
