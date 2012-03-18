package com.vonhof.webi;

import com.vonhof.babelshark.BabelShark;
import com.vonhof.babelshark.language.JsonLanguage;
import com.vonhof.webi.annotation.Body;
import com.vonhof.webi.annotation.Path;
import com.vonhof.webi.FileRequestHandler;
import com.vonhof.webi.rest.RESTRequestHandler;
import java.util.HashMap;
import java.util.Map;

public class WebiServer {
    public static void main(String[] args) throws Exception {
        //Add JSON to BabelShark
        // - Webi uses BabelShark to serialize and deserialize and so this will allow Webi to talk JSON.
        BabelShark.getInstance().register(new JsonLanguage());
        
        //Tell webi to bind to port 8081 - it wont start listening until you call start();
        Webi webi = new Webi(8081);
        
        //Init ther REST request handler
        RESTRequestHandler restHandler = new RESTRequestHandler();
        
        //Expose the hallo service. You can expose services both before and after you've started webi.
        restHandler.expose(new HalloService());
        
        //Add the REST handler to /rest/
        webi.add("/rest/", restHandler);
        
        FileRequestHandler fileHandler = FileRequestHandler.getStandardFileHandler();
        fileHandler.setDocumentRoot(System.getProperty("user.home"));
        webi.add("/", fileHandler);
        
        
        
        //Start the webi webserver
        webi.start();
    }
    
    /**
     * Tell webi to serve this class on the path /hallo/
     * The @Path annotation applies to types and methods
     */
    @Path("hallo")
    public static class HalloService {
        
        /**
         * Handle a GET request to /hallo/world/
         * Outputs a serialized string (JSON: "world")
         * @return 
         */
        public String world() {
            return "world";
        }
        
        /**
         * Handle a GET request to /hallo/complex/
         * Outputs the serialized map
         * @return 
         */
        @Path("complex")
        public Map<String,Object> map() {
            Map<String,Object> out = new HashMap<String, Object>();
            out.put("id", 1);
            out.put("name", "The Dude");
            out.put("age", 43);
            out.put("alive", true);
            return out;
        }
        
        /**
         * Handle a POST request to /hello/world/
         * The POST body will be deserialized into the body argument
         * @param body
         * @return 
         */
        @Path(value="world",method=HttpMethod.POST)
        public String worldPOST(@Body Map<String,Object> body) {
            return "world";
        }
        
        /**
         * All arguments are considered optional GET parameters if nothing else has been specified
         * Handles GET requests to /hello/parms/?test=world&other=stuff
         * @param text
         * @param other
         * @return 
         */
        public String parms(String text,String other) {
            return "hello "+ text + " other: "+ other;
        }
        
    }
}
