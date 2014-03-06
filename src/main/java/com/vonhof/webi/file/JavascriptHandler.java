package com.vonhof.webi.file;

import com.google.common.io.Files;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSModule;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.SourceMap;
import com.google.javascript.jscomp.deps.SortedDependencies.CircularDependencyException;
import com.vonhof.webi.WebiContext;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class JavascriptHandler extends PreprocessingRequestHandler {
    
    private final static Pattern modulePattern = Pattern.compile("(?uis)^//@module\\s+([A-Z\\.\\-_][A-Z0-9\\.\\-_]+)(?:\\s+@prio ([0-9]+))?");
        
    private final Charset charset = Charset.forName("UTF-8");
    
    private final Map<String,String> sourceMaps = new HashMap<String, String>();

    private boolean minify = true;

    public JavascriptHandler() {
        super("text/javascript");
        Compiler.setLoggingLevel(Level.SEVERE);
    }

    public boolean isMinify() {
        return minify;
    }

    public void setMinify(boolean minify) {
        this.minify = minify;
    }

    @Override
    protected boolean isValid(File file) {
        String ext = getFileExt(file);
        return ext.equalsIgnoreCase("js") || ext.equalsIgnoreCase("map");
    }
    

    @Override
    protected void outputFiles(WebiContext req, List<File> files) throws IOException {
        if (req.getParameterMap().contains("source")) {
            super.outputFiles(req, files);
        } else {
            compiled(req, files);
        }
    } 
    
    private void compiled(WebiContext req, List<File> files) throws IOException {
        
        final String sourceName = req.getRequest().getRequestURI();
        final String sourceMapPath = sourceName+"?map";
        //Check if we should be outputting source map
        boolean outputMap = req.getParameterMap().contains("map");
        if (outputMap) {
            req.setHeader("Content-type", "application/json");
            if (sourceMaps.containsKey(sourceName)) {
                IOUtils.write(sourceMaps.get(sourceName), req.getOutputStream());
                return;
            }
        } else {
            req.setHeader("X-SourceMap",sourceMapPath);
        }
        
        final CompilerOptions options = new CompilerOptions();
        if (minify) {
            CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        } else {
            CompilationLevel.WHITESPACE_ONLY.setOptionsForCompilationLevel(options);
        }

        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT5);
        options.sourceMapDetailLevel = SourceMap.DetailLevel.ALL;
        options.sourceMapOutputPath = sourceMapPath;
        
        
        final ArrayList<SourceFile> externs = new ArrayList<SourceFile>();
        final Map<String,JSModule> modules = new HashMap<String, JSModule>();
        JSModule rootModule = new JSModule("global");
        modules.put("__global", rootModule);
        
        //Atleast 1 source file required in root module
        rootModule.add(SourceFile.fromCode("root.js", ""));
        
        //Get absolute dir path to current url
        final String baseDir = this.getDocumentRoot()+req.getPath();
        
        //Run through all files that should be compiled
        Map<String,List<PrioritizedSourceFile>> moduleSourceFiles = new HashMap<String, List<PrioritizedSourceFile>>();

        for(File file:files) {

            //Calculate relative path to HTTP root path
            String relativePath = req.getBase()+file.getAbsolutePath().substring(this.getDocumentRoot().length()+1);
            
            //Get relative path to file from the current path. This is that path that the browsers will use for 
            //finding non-minified js
            String sourcePath = file.getAbsolutePath().substring(baseDir.length());
            if (sourcePath.isEmpty()) {
                //If the current path is same as the file path, just use the filename
                sourcePath = file.getName();
            }
            
            //Add parm to have actual non-minified source code show up
            sourcePath += "?source";
            
            //Build source file using paths that the browsers will recognize in source maps
            SourceFile sFile = SourceFile.fromCode(sourcePath,relativePath,Files.toString(file, charset));
            
            //Check for special comment //@module <name> @prio - low-tech dependency management
            String firstLine = Files.readFirstLine(file, charset);
            Matcher m = modulePattern.matcher(firstLine);
            String moduleName = null;
            int order = 0;
            if (m.find()) {
                moduleName = m.group(1);
                if (m.groupCount() > 1 && m.group(2) != null) {
                    order = Integer.parseInt(m.group(2));
                }
            }
            
            //Default module name
            if (moduleName == null || moduleName.isEmpty()) {
                moduleName = "__global";
            }
            //Create module if not exists
            if (!modules.containsKey(moduleName)) {
                modules.put(moduleName, new JSModule(moduleName));
            }

            if (!moduleSourceFiles.containsKey(moduleName)) {
                moduleSourceFiles.put(moduleName, new LinkedList<PrioritizedSourceFile>());
            }

            moduleSourceFiles.get(moduleName).add(new PrioritizedSourceFile(sFile,order));
        }

        for(Entry<String,List<PrioritizedSourceFile>> moduleEntry: moduleSourceFiles.entrySet()) {

            Collections.sort(moduleEntry.getValue(),new Comparator<PrioritizedSourceFile>() {
                @Override
                public int compare(PrioritizedSourceFile a, PrioritizedSourceFile b) {
                    return b.prio - a.prio;
                }
            });

            for(PrioritizedSourceFile file: moduleEntry.getValue()) {
                modules.get(moduleEntry.getKey()).add(file.sourceFile);
            }
        }
        
        //Run through all modules and add dependencies as necessary
        for(Entry<String,JSModule> entry:modules.entrySet()) {
            String moduleName = entry.getKey();
            
            if (moduleName.indexOf(".") > -1) {
                String[] parents = moduleName.split("\\.");
                String parent = "";
                for(int i = 0; i < parents.length-1;i++) {
                    if (i > 0) {
                        parent += ".";
                    }
                    parent += parents[i];
                    if (modules.get(parent) != null) {
                        entry.getValue().addDependency(modules.get(parent));
                    }
                }
            }
        }
        
        //Sort dependencies - Closure needs them to be ordered correctly.
        List<JSModule> moduleList = null;
        try {
            JSModule[] sortJsModules = JSModule.sortJsModules(modules.values());
            moduleList = Arrays.asList(sortJsModules);
        } catch (CircularDependencyException ex) {
            throw new IOException(ex);
        }
        
        //Instantiate and compile all modules
        final Compiler compiler = new Compiler();
        Result result = compiler.compileModules(externs,moduleList, options);
        
        //Must generate sources before accessing source map
        String source = compiler.toSource();


        //Build source map
        StringBuilder sb = new StringBuilder();
        result.sourceMap.validate(true);
        result.sourceMap.appendTo(sb, sourceName);
        sourceMaps.put(sourceName, sb.toString());
        
        if (outputMap) {
            //Output source map
            IOUtils.write(sourceMaps.get(sourceName), req.getOutputStream());
        } else {
            if (minify) {
                //Add source map special comment to source and output compiled js
                source += "\n//@ sourceMappingURL="+options.sourceMapOutputPath;
                req.setHeader("X-SourceMap",options.sourceMapOutputPath);
            }
            IOUtils.write(source, req.getOutputStream());
        }
    }

    private class PrioritizedSourceFile {
        final SourceFile sourceFile;
        final int prio;

        private PrioritizedSourceFile(SourceFile sourceFile, int prio) {
            this.sourceFile = sourceFile;
            this.prio = prio;
        }

        private PrioritizedSourceFile(SourceFile sourceFile) {
            this.sourceFile = sourceFile;
            this.prio = -1;
        }
    }
}
