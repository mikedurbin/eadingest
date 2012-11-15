package edu.virginia.lib.fedora.eadingest.pidmapping;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraClientException;

import edu.virginia.lib.fedora.eadingest.EADIngest;
import edu.virginia.lib.fedora.eadingest.EADOntology;

/**
 * A PidMapping implementation that meets the very 
 * specific needs of some early testing of certain
 * collections.  It has two main modes of operation:
 * to walk through the repository to create a mapping
 * and to load a mapping from a text file.
 */
public class SpecializedPidMapping implements PidMapping {

    private String mdServiceName = "uva-lib:descMetadataSDef";
    
    private String mdServiceMethod = "getMetadataAsEADFragment";
    
    private Map<String, String> hashToPidMap;

    private XPath xpath;
    
    /**
     * A constructor that accepts a file containing
     * mappings.  Each line of the file must be of 
     * the format "key --> pid", where "key" is the
     * value returned for getKey() on the metadata
     * for that object.
     */
    public SpecializedPidMapping(File mappingFile) throws IOException {
        xpath = XPathFactory.newInstance().newXPath();
        
        hashToPidMap = new HashMap<String, String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(mappingFile)));
        String line = null;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(" --> ");
            hashToPidMap.put(parts[0].trim(), parts[1].trim());
            System.out.println(line);
        }
    }
    
    /**
     * A constructor that accepts information about
     * the way the repository is structured and walks
     * through each object to add a key-value pair to
     * the map.
     */
    public SpecializedPidMapping(FedoraClient fc, EADOntology o, String rootId) throws SAXException, ParserConfigurationException, Exception {
        hashToPidMap = new HashMap<String, String>();
        
        xpath = XPathFactory.newInstance().newXPath();
        
        String cpid = EADIngest.lookupObjectById(fc, rootId);
        
        List<String> marcPids = !o.isInverted(EADOntology.Relationship.HAS_MARC) 
                ? EADIngest.getObjects(fc, cpid, o.getRelationship(EADOntology.Relationship.HAS_MARC)) 
                : EADIngest.getSubjects(fc, cpid, o.getInverseRelationship(EADOntology.Relationship.HAS_MARC));
        for (String marcPid : marcPids) {
            String marcKey = getKey(marcPid, o.marcDSID(), fc);
            System.out.println(marcKey + " --> " + marcPid + " (marc)");
            if (hashToPidMap.containsKey(marcKey)) {
                throw new RuntimeException("Two nodes have the same hash!");
            }
            hashToPidMap.put(marcKey, marcPid);
            List<String> containerPids = !o.isInverted(EADOntology.Relationship.DEFINES_CONTAINER) 
                    ? EADIngest.getObjects(fc,  marcPid, o.getRelationship(EADOntology.Relationship.DEFINES_CONTAINER)) 
                    : EADIngest.getSubjects(fc,  marcPid, o.getInverseRelationship(EADOntology.Relationship.DEFINES_CONTAINER));
            for (String containerPid : containerPids) {
                String containerKey = getKey(containerPid, o.containerDSID(), fc);
                System.out.println(containerKey + " --> " + containerPid);
                if (hashToPidMap.containsKey(containerKey)) {
                    throw new RuntimeException("Two nodes have the same hash!");
                }
                hashToPidMap.put(containerKey, containerPid);
            }

        }
        mapComponentAndChildren(hashToPidMap, cpid, getKey(cpid, mdServiceName, mdServiceMethod, fc), o, fc);
    }
    
    /**
     * A recursive method used by the constructor when walking through the
     * hierarchy in Fedora to build the map.
     */
    private void mapComponentAndChildren(Map<String, String> map, String pid, String hash, EADOntology o, FedoraClient fc) throws SAXException, IOException, ParserConfigurationException, FedoraClientException, Exception {
        System.out.println(hash + " --> " + pid);
        if (hash == null || hash.equals("")) {
            throw new NullPointerException("id must not be null!");
        }
        if (map.containsKey(hash)) {
            throw new RuntimeException("Two nodes have the same hash!");
        }
        map.put(hash, pid);
        
        if (o.isInverted(EADOntology.Relationship.IS_PART_OF) || o.isInverted(EADOntology.Relationship.FOLLOWS)) {
            throw new RuntimeException("getOrderedParts does not support inverted relationships!");
        }
        for (String partPid : EADIngest.getOrderedParts(fc, pid, o.getRelationship(EADOntology.Relationship.IS_PART_OF), o.getRelationship(EADOntology.Relationship.FOLLOWS))) {
            mapComponentAndChildren(map, partPid, getKey(partPid, mdServiceName, mdServiceMethod, fc), o, fc);
        }
        
    }
    
    /**
     * Gets the PID for the object associated with the
     * given metadata.  This method throws a RuntimeException 
     * if no PID is able to be determined.
     */
    public String getPidForElement(Element el) {
        try {
            String key = getKey(el);
            String pid = hashToPidMap.get(key);
            if (pid == null) {
                throw new IllegalArgumentException("Unable to find pid for item! (" + key + ")");
            }
            return pid;
        } catch (TransformerException ex) {
            throw new RuntimeException(ex);
        } catch (XPathExpressionException ex) {
            throw new RuntimeException(ex);
        }
    }
    

    
    private String getKey(String pid, String sdef, String service, FedoraClient fc) throws ParserConfigurationException, SAXException, IOException, FedoraClientException, XPathExpressionException, TransformerException {
        try {
            DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = parser.parse(FedoraClient.getDissemination(pid, sdef, service).execute(fc).getEntityInputStream());
            return getKey(doc.getDocumentElement());
        } catch (Throwable t) {
            return pid;
        }
    }

    private String getKey(String pid, String dsId, FedoraClient fc) throws ParserConfigurationException, SAXException, IOException, FedoraClientException, XPathExpressionException, TransformerException {
        try {
            DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = parser.parse(FedoraClient.getDatastreamDissemination(pid, dsId).execute(fc).getEntityInputStream());
            return getKey(doc.getDocumentElement());
        } catch (Throwable t) {
            return pid;
        }
    }
    
    private String getKey(Element el) throws XPathExpressionException, TransformerException {
        String id = (String) xpath.evaluate("@id", el, XPathConstants.STRING);
        if (id != null && !id.equalsIgnoreCase("")) {
            return id;
        } else {
            id = (String) xpath.evaluate("//controlfield[@tag='001']", el, XPathConstants.STRING);
            if (id != null && !id.equalsIgnoreCase("")) {
                return id;
            } else {
                id = (String) xpath.evaluate("//container/callNumber", el, XPathConstants.STRING);
                if (id != null && !id.equalsIgnoreCase("")) {
                    return id;
                } else {
                    // this is expensive and prone to error (like when fedora mucks with the XML)
                    return computeHash(el);
                }
            }
        }
    }
    
    public Collection<String> getPids() {
        return hashToPidMap.values();
    }
    
    
    /**
     * Computes an MD5 hash for a standard serialization of a given
     * Document.
     */
    public static String computeHash(Node node) throws TransformerException {
        DOMSource source = new DOMSource(node);
        HashOutputStream hos = new HashOutputStream();
        StreamResult sResult = new StreamResult(hos);
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer t = tFactory.newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        t.setOutputProperty(OutputKeys.METHOD, "xml");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        t.transform(source, sResult);
        return hos.getMD5Hash();
    }
    
    public static String computeHash(byte[] bytes) {
        HashOutputStream hos = new HashOutputStream();
        hos.write(bytes);
        return hos.getMD5Hash();
    }
    
    public static class HashOutputStream extends OutputStream {

        private MessageDigest digest;
        
        public HashOutputStream() {
            try {
                digest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException ex) {
                // can't happen because MD5 is supported by all JVMs
                assert false;
            }            
        }
        
        public String getMD5Hash() {
            byte[] inn = this.digest.digest();
            byte ch = 0x00;
            int i = 0;
            String pseudo[] = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"};
            StringBuffer out = new StringBuffer(inn.length * 2);
            while (i < inn.length) {
                ch = (byte) (inn[i] & 0xF0);
                ch = (byte) (ch >>> 4);
                ch = (byte) (ch & 0x0F);
                out.append(pseudo[ (int) ch]);
                ch = (byte) (inn[i] & 0x0F);
                out.append(pseudo[ (int) ch]);
                i++;
            }
            return new String(out);
        }
        
        public void write(int b) throws IOException {
            //System.out.print((char) b);
            this.digest.update(new byte[] { (byte) b });
        }
        
        public void write(byte[] b, int off, int len) throws IOException {
            //for (int i = off; i < off + len; i ++) {
            //    System.out.print((char) b[i]);
            //}
            this.digest.update(b, off, len);
        }
        
        public void write(byte[] b) {
            //for (int i = 0; i < b.length; i ++) {
            //    System.out.print((char) b[i]);
            //}

            this.digest.update(b);
        }
        
    }

}
