package com.cphse.webi;

import com.cphse.webi.mapping.HttpMethod;
import com.cphse.webi.mapping.annotation.Name;
import com.cphse.webi.mapping.annotation.Path;

public class TestServer {
    public static void main(String[] args) throws Exception {
        Webi webi = new Webi(8910);
        webi.expose(new HalloService());
        webi.start();
    }
    
    @Path("hallo")
    private static class HalloService {
        
        public String world() {
            return "world";
        }
        
        @Path(value="world",methods={HttpMethod.POST})
        public String worldPOST() {
            return "world";
        }
        
        public String parms(@Name("text") String input,@Name() String other) {
            return "hello "+input;
        }
    }
}
