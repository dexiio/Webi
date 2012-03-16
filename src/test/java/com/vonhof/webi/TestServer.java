package com.vonhof.webi;

import com.vonhof.webi.HttpMethod;
import com.vonhof.webi.Webi;
import com.vonhof.webi.annotation.Parm;
import com.vonhof.webi.annotation.Path;
import com.vonhof.babelshark.BabelShark;
import com.vonhof.babelshark.language.JsonLanguage;
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
        
        @Path(value="world",methods={HttpMethod.POST})
        public String worldPOST() {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Logger.getLogger(TestServer.class.getName()).log(Level.SEVERE, null, ex);
            }
            return "world";
        }
        
        public String parms(@Parm("text") String input,@Parm() String other) {
            return "hello "+input;
        }
    }
}
