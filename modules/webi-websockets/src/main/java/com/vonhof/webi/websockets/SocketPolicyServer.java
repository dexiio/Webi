package com.vonhof.webi.websockets;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class SocketPolicyServer extends ServerSocket {
    
    private Thread runner;
    private boolean running;
    

    public SocketPolicyServer(int port) throws IOException {
        super(port);
    }

    public SocketPolicyServer() throws IOException {
        this(843);
    }
    
    public void start () {
        running = true;
        runner = new Thread() {
            
            @Override
            public void run() {
                while(running) {
                    try {
                        Socket client = accept();
                        InputStream in = client.getInputStream();
                        
                        while(in.available() > 0) {
                            in.read();
                        }
                        
                        IOUtils.write(
                                "<?xml version=\"1.0\"?>\n"+
                                "<!DOCTYPE cross-domain-policy SYSTEM \"/xml/dtds/cross-domain-policy.dtd\">\n"+
                                "<cross-domain-policy><allow-access-from domain=\"*\" to-ports=\"*\" /></cross-domain-policy>", 
                                client.getOutputStream());
                        
                        client.getOutputStream().flush();
                        client.getOutputStream().close();
                        
                    } catch (IOException ex) {
                        Logger.getLogger(SocketPolicyServer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                
            }
        };
        runner.start();
    }
    
    public void stop () {
        running = false;
        if (runner != null)
            runner.interrupt();
        runner = null;
    }
}
