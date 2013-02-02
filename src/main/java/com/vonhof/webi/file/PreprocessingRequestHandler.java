package com.vonhof.webi.file;

import com.vonhof.webi.WebiContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
abstract public class PreprocessingRequestHandler extends FileRequestHandler {
    
    private final String contentType; 
    

    public PreprocessingRequestHandler(String contentType) {
        this.contentType = contentType;
        addDefaultMimeTypes();
    }

    @Override
    protected final void serveDir(WebiContext req, File dir) throws IOException {
        List<File> files = getFiles(dir);
        
        long lastModified = 0;
        
        for(File file:files) {
            if (file.lastModified() > lastModified) {
                lastModified = file.lastModified();
            }
        }
        
        long reqLastModified = req.getRequest().getDateHeader("If-Modified-Since");
        if (reqLastModified > 0 && lastModified <= reqLastModified) {
            req.setStatus(304);
            req.flushBuffer();
            return;
        }
        
        req.setHeader("Content-type",contentType);
        req.setDateHeader("Last-Modified",lastModified);
        
        outputFiles(req, files);
        
        req.flushBuffer();
    }

    @Override
    protected void unknownFile(WebiContext ctxt, File file, String path) throws IOException {
        super.unknownFile(ctxt, file, path);
    }
    
    
    

    @Override
    protected final void serveFile(WebiContext req, File file) throws IOException {
        if (!isValid(file)) {
            super.serveFile(req, file);
            return; 
        }
        long lastModified = file.lastModified();
        long reqLastModified = req.getRequest().getDateHeader("If-Modified-Since");
        
        req.setHeader("Content-type",contentType);
        req.setDateHeader("Last-Modified",lastModified);
        
        if (reqLastModified > 0 && lastModified <= reqLastModified) {
            req.setStatus(304);
            req.flushBuffer();
            return;
        }
        
        outputFiles(req, Collections.singletonList(file));
        
        req.flushBuffer();
    }
    
    
    protected void outputFiles(WebiContext req,List<File> files) throws IOException {
        byte[] lineBreak = "\n".getBytes("UTF-8");
        
        for(File file:files) {
            outputFile(req, file, req.getOutputStream());
            req.getOutputStream().write(lineBreak);
        }
    }
    
    protected void outputFile(WebiContext req, File file,OutputStream out) throws IOException {
        FileInputStream fileIn = new FileInputStream(file);
        IOUtils.copy(fileIn, out);
    }
    
    protected boolean isValid(File file) {
        return true;
    }
    
    protected List<File> getFiles(File dir) {
        List<File> files = new ArrayList<File>();
        
        for(File file:dir.listFiles()) {
            if (file.isDirectory()) {
                files.addAll(getFiles(file));
            } else if (isValid(file)) {
                files.add(file);
            }
        }
        
        return files;
    }
    
    

}
