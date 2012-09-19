package edu.virginia.lib.fedora.eadingest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;

import com.yourmediashelf.fedora.client.FedoraClient;

public class PostSolrDocument {
    
    public static void indexPid(FedoraClient fc, String pid, String updateUrl, String servicePid, String serviceMethod) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeStreamToStream(FedoraClient.getDissemination(pid, servicePid, serviceMethod).execute(fc).getEntityInputStream(), baos);
        
        HttpClient client = new HttpClient();

        PostMethod post = new PostMethod(updateUrl);
        Part[] parts = {
                new FilePart("add.xml", new ByteArrayPartSource("add.xml", baos.toByteArray()))
        };
        post.setRequestEntity(
                new MultipartRequestEntity(parts, post.getParams())
            );
        try {
            client.executeMethod(post);
            int status = post.getStatusCode();
            if (status != HttpStatus.SC_CREATED && status != HttpStatus.SC_OK) {
                throw new RuntimeException("REST action \"" + updateUrl + "\" failed: " + post.getStatusLine());
            }
        } finally {
            post.releaseConnection();
        }
        
    }
    
    public static void commit(String updateUrl) throws HttpException, IOException {
        String url = updateUrl + "?stream.body=%3Ccommit/%3E";
        GetMethod get = new GetMethod(url);
        try {
            HttpClient client = new HttpClient();
            client.executeMethod(get);
            int status = get.getStatusCode();
            if (status != HttpStatus.SC_OK) {
                throw new RuntimeException("REST action \"" + url + "\" failed: " + get.getStatusLine());
            }
        } finally {
            get.releaseConnection();
        }
    }
    
    public static void rollback(String updateUrl) throws HttpException, IOException {
        String url = updateUrl + "?stream.body=%3Crollback/%3E";
        GetMethod get = new GetMethod(url);
        try {
            HttpClient client = new HttpClient();
            client.executeMethod(get);
            int status = get.getStatusCode();
            if (status != HttpStatus.SC_OK) {
                throw new RuntimeException("REST action \"" + url + "\" failed: " + get.getStatusLine());
            }
        } finally {
            get.releaseConnection();
        }
    }
    
    public static void writeStreamToStream(InputStream is, OutputStream os) throws IOException {
        ReadableByteChannel inputChannel = Channels.newChannel(is);  
        WritableByteChannel outputChannel = Channels.newChannel(os);  
        ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);  
        while (inputChannel.read(buffer) != -1) {  
            buffer.flip();  
            outputChannel.write(buffer);  
            buffer.compact();  
        }  
        buffer.flip();  
        while (buffer.hasRemaining()) {  
            outputChannel.write(buffer);  
        }  
       inputChannel.close();  
       outputChannel.close();
    }
    
}
