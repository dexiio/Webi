package com.vonhof.webi.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.fileupload.FileItem;

/**
 *
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class TextFile {
    private static final String[] encodings = new String[] {
        "UTF-8",
        "UTF-16",
        "ISO-8859-15",
        "ISO-8859-2",
        "ISO-8859-1",
        "MacRoman",
        "Windows-1252",
        "Windows-1250",
        "ASCII"
    };
    
    private String charset = "UTF-8";
    private String contentType;
    private byte[] data;
    private String name;
    
    

    public TextFile(FileItem file) throws IOException {
        name = file.getName();
        contentType = file.getContentType();
        
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        InputStream in = file.getInputStream();
        int n = 0;
        while ((n = in.read()) > 0) {
            bout.write(n);
        }
        data = bout.toByteArray();
        
        detect();
    }

    public String getCharset() {
        return charset;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }
    
    public String getContent() throws UnsupportedEncodingException {
        String content = new String(data, charset);
        //Fix line endings
        int offset = 0;
        
        while ((offset = content.indexOf(13,offset)) > -1) {
            
            content = content.replaceFirst("\r",(content.indexOf(10,offset)-offset == 1) ? "" : "\n");
        }
        return content;
    }

    public String getName() {
        return name;
    }
    
    
    private void detect() {
        for(String encoding:encodings) {
            try {
                String tst = new String(data,encoding);
                if (Arrays.equals(tst.getBytes(), data)) {
                    charset = encoding;
                }
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(TextFile.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        
    }

}
