package com.vonhof.webi.file;

import com.asual.lesscss.LessEngine;
import com.asual.lesscss.LessOptions;
import com.google.common.io.Files;
import com.vonhof.webi.WebiContext;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class LESSHandler extends PreprocessingRequestHandler {
    final LessEngine css;
    final LessEngine less;

    public LESSHandler() {
        super("text/css");
        
        //Setup 2 engines - 1 for CSS and 1 for LESS
        LessOptions cssOptions = new LessOptions();
        cssOptions.setCss(true);
        css = new LessEngine(cssOptions);
        
        LessOptions lessOptions = new LessOptions();
        lessOptions.setCss(false);
        less = new LessEngine(lessOptions );
    }

    @Override
    protected boolean isValid(File file) {
        String ext = getFileExt(file);
        return ext.equalsIgnoreCase("css") || ext.equalsIgnoreCase("less");
    }
    
    @Override
    protected void outputFiles(WebiContext req, List<File> files) throws IOException {
        
        //Run through all files that should be compiled
        for(File file:files) {
            //Actual source
            String cssLess = Files.toString(file, Charset.forName("UTF-8"));
            try {
                //Compiled source
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
