package com.vonhof.webi.php;

import com.caucho.quercus.QuercusContext;
import com.caucho.quercus.QuercusExitException;
import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.lib.*;
import com.caucho.quercus.lib.curl.CurlModule;
import com.caucho.quercus.lib.date.DateModule;
import com.caucho.quercus.lib.dom.QuercusDOMModule;
import com.caucho.quercus.lib.file.FileModule;
import com.caucho.quercus.lib.file.StreamModule;
import com.caucho.quercus.lib.json.JsonModule;
import com.caucho.quercus.lib.mail.MailModule;
import com.caucho.quercus.lib.regexp.RegexpModule;
import com.caucho.quercus.lib.simplexml.SimpleXMLModule;
import com.caucho.quercus.lib.string.StringModule;
import com.caucho.quercus.lib.xml.XmlModule;
import com.caucho.quercus.page.QuercusPage;
import com.caucho.quercus.servlet.QuercusServletImpl;
import com.caucho.util.Alarm;
import com.caucho.util.L10N;
import com.caucho.vfs.Path;
import com.caucho.vfs.Vfs;
import com.caucho.vfs.WriteStream;
import com.vonhof.webi.file.FileRequestHandler;
import com.vonhof.webi.WebiContext;
import com.vonhof.webi.rest.RESTServiceHandler;
import com.vonhof.webi.php.module.WebiModule;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Logger;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class PHPRequestHandler extends FileRequestHandler {
    
    private final RESTServiceHandler service;
    
    private static final L10N L = new L10N(QuercusServletImpl.class);
    private static final Logger LOG = Logger.getLogger(PHPRequestHandler.class.getName());
    
    private final QuercusContext quercus = new QuercusContext();
    private final Path basePath;

    public PHPRequestHandler(RESTServiceHandler service,String docRoot) {
        super();
        this.service = service;
        
        quercus.addModule(new MathModule());
        quercus.addModule(new ClassesModule());
        quercus.addModule(new ErrorModule());
        quercus.addModule(new HttpModule());
        quercus.addModule(new UrlModule());
        quercus.addModule(new VariableModule());
        quercus.addModule(new TokenModule());
        quercus.addModule(new HashModule());
        quercus.addModule(new FunctionModule());
        quercus.addModule(new HtmlModule());
        quercus.addModule(new MiscModule());
        quercus.addModule(new OptionsModule());
        quercus.addModule(new FileModule());
        quercus.addModule(new QuercusDOMModule());
        quercus.addModule(new DateModule());
        quercus.addModule(new CurlModule());
        quercus.addModule(new JsonModule());
        quercus.addModule(new MailModule());
        quercus.addModule(new RegexpModule());
        quercus.addModule(new StringModule());
        quercus.addModule(new XmlModule());
        quercus.addModule(new SimpleXMLModule());
        quercus.addModule(new OutputModule());
        quercus.addModule(new StreamModule());
        quercus.addModule(new ArrayModule());
        quercus.addModule(new WebiModule(webi,service));
        
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
        
        this.setDocumentRoot(docRoot);
        
        basePath = Vfs.lookup(getDocumentRoot());
        
        quercus.setPwd(basePath);

        // need to set these for non-Resin containers
        if (!Alarm.isTest() && !quercus.isResin()) {
            Vfs.setPwd(basePath);
        }

        quercus.init();
        quercus.start();
        
    }

    public QuercusContext getQuercus() {
        return quercus;
    }
    
    @Override
    protected void serveFile(WebiContext req, File file) throws IOException {
        if (!file.getName().endsWith(".php")) {
            super.serveFile(req, file);
            return;
        }
        
        
        Env env = null;
        WriteStream ws = null;
        try {

            Path path = basePath.lookup(file.getPath());
            QuercusPage page;

            try {
                page = quercus.parse(path);
            } catch (FileNotFoundException ex) {
                req.sendError(ex);
                return;
            }
            
            ws = Vfs.openWrite(req.getOutputStream());
            ws.setNewlineString("\n");

            env = quercus.createEnv(page, ws, req.getRequest(), req.getResponse());
            //quercus.setServletContext(null);

            env.start();

            env.setGlobalValue("CONTEXT",env.wrapJava(req));
            env.setGlobalValue("SESSION",env.wrapJava(req.getSession()));
            
            StringValue prepend = quercus.getIniValue("auto_prepend_file").toStringValue(env);
            if (prepend.length() > 0) {
                Path prependPath = env.lookup(prepend);

                if (prependPath == null) {
                    env.error(L.l("auto_prepend_file '{0}' not found.", prepend));
                } else {
                    QuercusPage prependPage = quercus.parse(prependPath);
                    prependPage.executeTop(env);
                }
            }

            env.executeTop();

            StringValue append = quercus.getIniValue("auto_append_file").toStringValue(env);
            if (append.length() > 0) {
                Path appendPath = env.lookup(append);

                if (appendPath == null) {
                    env.error(L.l("auto_append_file '{0}' not found.", append));
                } else {
                    QuercusPage appendPage = quercus.parse(appendPath);
                    appendPage.executeTop(env);
                }
            }
        } catch (QuercusExitException ex) {
            //Do nothing... just exiting...
            return;
        } catch (Throwable e) {
            req.sendError(e);
        } finally {
            if (env != null) {
                env.close();
            }

            // don't want a flush for an exception
            if (ws != null && env.getDuplex() == null) {
                ws.close();
            }
        }
        
    }

    
    
}
