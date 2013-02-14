package edu.virginia.lib.fedora.eadingest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.yourmediashelf.fedora.client.FedoraClient;

public class SolrTarget {
    
    private String updateUrl;
    
    private File cacheDir;
    
    private File transactionDir;
    
    public SolrTarget(String url, File cache) {
        updateUrl = url;
        cacheDir = cache;
        transactionDir = new File(cacheDir, "transaction-" + System.currentTimeMillis());
        transactionDir.mkdir();
    }
    
    /**
     * Create a SolrTarget that refererences an 
     * actual SOLR server.
     * @param url the URL of the solr server.
     */
    public SolrTarget(String url) {
        updateUrl = url;
    }
    
    public SolrTarget(File cacheDir) {
        this.cacheDir = cacheDir;
        transactionDir = new File(cacheDir, "transaction-" + System.currentTimeMillis());
        transactionDir.mkdir();
    }
    
    public void indexPid(FedoraClient fc, String pid, String servicePid, String serviceMethod) throws Exception {
        File addDoc = null;
        if (transactionDir != null) {
            addDoc = new File(transactionDir, pid.substring(pid.indexOf(':') + 1) + "-solr-add-doc.xml");
        } else {
            addDoc = File.createTempFile("addDoc", ".xml");
            addDoc.deleteOnExit();
        }
        File cacheDoc = new File(cacheDir, pid.substring(pid.indexOf(':') + 1) + "-solr-add-doc.xml");
        if (!cacheDoc.exists()) {
            FileOutputStream fos = new FileOutputStream(addDoc);
            try {
                IOUtils.copy(FedoraClient.getDissemination(pid, servicePid, serviceMethod).execute(fc).getEntityInputStream(), fos);
            } finally {
                fos.close();
            }
            cacheDoc = addDoc;
        }

        if (updateUrl != null) {
            postAddDoc(pid, cacheDoc);
        }
    }
    
    private void postAddDoc(InputStream addDocIS) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(addDocIS, baos);
        
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
    
    public void postAddDoc(String pid, File addDoc) throws Exception {
        if (transactionDir != null) {
            File cacheDoc = new File(transactionDir, pid.substring(pid.indexOf(':') + 1) + "-solr-add-doc.xml");
            if (!cacheDoc.equals(addDoc)) {
                FileUtils.copyFile(addDoc, cacheDoc);
            }
        }
        if (updateUrl != null) {
            System.out.println("POSTing to " + updateUrl);
            HttpClient client = new HttpClient();
    
            PostMethod post = new PostMethod(updateUrl);
            Part[] parts = {
                    new FilePart("add.xml", addDoc)
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
    }
    
    public void commit() throws HttpException, IOException {
        if (transactionDir != null) {
            // move files
            FileUtils.copyDirectory(transactionDir, cacheDir);
            
            // delete dir
            FileUtils.deleteDirectory(transactionDir);
            
            // create new transaction
            transactionDir = new File(cacheDir, "transaction-" + System.currentTimeMillis());
            transactionDir.mkdir();
        }
        if (updateUrl != null) {
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
    }
    
    public void rollback() throws HttpException, IOException {
        if (transactionDir != null) {
            // don't actually delete, we want to see the stuff
            
            transactionDir = new File(cacheDir, "transaction-" + System.currentTimeMillis());
            transactionDir.mkdir();
        }
        if (updateUrl != null) {
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
    }
    
}
