package com.vonhof.webi.file;

import com.asual.lesscss.LessEngine;
import com.asual.lesscss.LessException;
import com.asual.lesscss.LessOptions;
import com.google.common.io.Files;
import com.google.javascript.jscomp.JSModule;
import com.google.javascript.jscomp.SourceFile;
import com.vonhof.webi.WebiContext;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class LESSHandler extends PreprocessingRequestHandler {

    public LESSHandler() {
        super("text/css");
    }

    @Override
    protected boolean isValid(File file) {
        String ext = getFileExt(file);
        return ext.equalsIgnoreCase("css") || ext.equalsIgnoreCase("less");
    }
    
    @Override
    protected void outputFiles(WebiContext req, List<File> files) throws IOException {
        LessOptions cssOptions = new LessOptions();
        cssOptions.setCss(true);
        final LessEngine css = new LessEngine(cssOptions);
        
        LessOptions lessOptions = new LessOptions();
        lessOptions.setCss(false);
        final LessEngine less = new LessEngine(lessOptions );
        
        for(File file:files) {
            String cssLess = Files.toString(file, Charset.forName("UTF-8"));
            try {
                String compiled = "";
                if (getFileExt(file).equalsIgnoreCase("less")) {
                    compiled = less.compile(cssLess, req.getParameterMap().contains("compressed"));
                } else {
                    compiled = css.compile(cssLess, req.getParameterMap().contains("compressed"));
                }
                
                req.getOutputStream().write(compiled.getBytes("UTF-8"));
            } catch (Throwable ex) {
                Logger.getLogger(LESSHandler.class.getName()).log(Level.SEVERE, null, ex);
                req.getOutputStream().write(cssLess.getBytes("UTF-8"));
            }
        }
    }
}
