package com.cphse.webi;

import com.cphse.webi.mapping.annotation.Name;
import com.cphse.webi.mapping.annotation.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestServer {
    public static void main(String[] args) throws Exception {
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
        
        public String parms(@Name("text") String input,@Name() String other) {
            return "hello "+input;
        }
    }
}
