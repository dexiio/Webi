package com.vonhof.webi;

import com.vonhof.babelshark.BabelShark;
import com.vonhof.babelshark.language.JsonLanguage;
import com.vonhof.webi.annotation.Body;
import com.vonhof.webi.annotation.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestServer {
    public static void main(String[] args) throws Exception {
        BabelShark.getInstance().register(new JsonLanguage());
        
        Webi webi = new Webi(8910);
        webi.expose(new HalloService());
        webi.start();
    }
    
    @Path("hallo")
    private static class HalloService {
        
        public String world() {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Logger.getLogger(TestServer.class.getName()).log(Level.SEVERE, null, ex);
            }
            return "world";
        }
        
        public Map<String,Object> map() {
            Map<String,Object> out = new HashMap<String, Object>();
            out.put("id", 1);
            out.put("name", "The Dude");
            out.put("age", 43);
            out.put("alive", true);
            return out;
        }
        
        @Path(value="world",method=HttpMethod.POST)
        public String worldPOST(@Body Map<String,Object> body) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Logger.getLogger(TestServer.class.getName()).log(Level.SEVERE, null, ex);
            }
            return "world";
        }
        
        public String parms(String text,String other) {
            return "hello "+ text + " other: "+ other;
        }
    }
}
