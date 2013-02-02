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
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;


/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class JavascriptHandler extends PreprocessingRequestHandler {
    
    private final static Pattern modulePattern = Pattern.compile("(?uis)^//@module\\s+([A-Z\\.\\-_][A-Z0-9\\.\\-_]+)(?:\\s+@prio ([0-9]+))?");
        
    private final Charset charset = Charset.forName("UTF-8");
    
    private final Map<String,String> sourceMaps = new HashMap<String, String>();

    public JavascriptHandler() {
        super("text/javascript");
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
        final Compiler compiler = new Compiler();
        final String sourceName = req.getRequest().getRequestURI();
        boolean outputMap = req.getParameterMap().contains("map");
        if (outputMap) {
            req.setHeader("Content-type", "application/json");
            if (sourceMaps.containsKey(sourceName)) {
                IOUtils.write(sourceMaps.get(sourceName), req.getOutputStream());
                return;
            }
        }
        
        final CompilerOptions options = new CompilerOptions();
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT5);
        options.sourceMapDetailLevel = SourceMap.DetailLevel.ALL;
        options.sourceMapOutputPath = sourceName+"?map";
        
        
        final ArrayList<SourceFile> externs = new ArrayList<SourceFile>();
        final Map<String,JSModule> modules = new HashMap<String, JSModule>();
        JSModule rootModule = new JSModule("global");
        modules.put("__global", rootModule);
        
        rootModule.add(SourceFile.fromCode("root.js", ""));
        
        
        final String baseDir = this.getDocumentRoot()+req.getPath();
        final Map<String,String> dependencies = new HashMap<String, String>();
        
        for(File file:files) {
            
            String relativePath = req.getBase()+file.getAbsolutePath().substring(this.getDocumentRoot().length()+1);
            String fileName = file.getAbsolutePath().substring(baseDir.length());
            if (fileName.isEmpty()) {
                fileName = file.getName();
            }
            fileName += "?source";
            
            SourceFile sFile = SourceFile.fromCode(fileName,relativePath,Files.toString(file, charset));
            
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
            
            if (moduleName == null || moduleName.isEmpty()) {
                moduleName = "__global";
            }
            if (!modules.containsKey(moduleName)) {
                modules.put(moduleName, new JSModule(moduleName));
            }
            
            if (order > 0) {
                modules.get(moduleName).addFirst(sFile);
            } else {
                modules.get(moduleName).add(sFile);
            }
        }
        
        for(Entry<String,JSModule> entry:modules.entrySet()) {
            String moduleName = entry.getKey();
            
            if (moduleName.indexOf(".") > -1) {
                String[] parents = moduleName.split("\\.");
                String parent = "";
                for(int i = 0; i < parents.length-1;i++) {
                    parent += parents[i];
                    if (modules.get(parent) != null) {
                        entry.getValue().addDependency(modules.get(parent));
                    }
                }
            }
        }
        List<JSModule> moduleList = null;
        try {
            JSModule[] sortJsModules = JSModule.sortJsModules(modules.values());
            moduleList = Arrays.asList(sortJsModules);
        } catch (CircularDependencyException ex) {
            throw new IOException(ex);
        }
        
        Compiler.setLoggingLevel(Level.SEVERE);
        
        Result result = compiler.compileModules(externs,moduleList, options);
        
        //Must generate sources before accessing source map
        String source = compiler.toSource();
        
        StringBuilder sb = new StringBuilder();
        result.sourceMap.validate(true);
        result.sourceMap.appendTo(sb, sourceName);
        sourceMaps.put(sourceName, sb.toString());
        
        if (outputMap) {
            IOUtils.write(sourceMaps.get(sourceName), req.getOutputStream());
        } else {
            source += "\n//@ sourceMappingURL="+options.sourceMapOutputPath;
            req.setHeader("X-SourceMap",options.sourceMapOutputPath);
            IOUtils.write(source, req.getOutputStream());
        }
    }
}
