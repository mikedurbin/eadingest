package edu.virginia.lib.fedora.eadingest;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraClientException;
import com.yourmediashelf.fedora.client.FedoraCredentials;

/**
 * Test code to ingest XML content into a local fedora repository.
 */
public class EADIngest {

    public static final String HAS_MODEL_PREDICATE = "info:fedora/fedora-system:def/model#hasModel";
    
    public String logicalCollectionContentModelPid = "cmodel:collection";
    public String logicalComponentContentModelPid = "cmodel:component";
    public String isPartOfPredicate = "info:fedora/fedora-system:def/relations-external#isPartOf";
    public String followsSequentiallyPredicate = "http://fedora.lib.virginia.edu/relationship#follows";
    
    public String eadRootContentModelPid = "cmodel:ead-root";
    public String eadRootDsId = "descMetadata";
    public String eadComponentContentModelPid = "cmodel:ead-component";
    public String eadComponentDsId = "descMetadata";
    
    public String marcContentModelPid = "cmodel:marc";
    public String marcDsId = "descMetadata";
    public String hasMarcPredicate = "http://fedora.lib.virginia.edu/relationship#hasHoldingRecordsFor";
    public String modsContentModelPid = "cmodel:mods";
    public String modsDsId = "descMetadata";
    
    public String physicalContainerContentModel = "cmodel:container";
    public String physicalContainerDsId = "descMetadata";
    public String physicalContainerNestingPredicate = "http://fedora.lib.virginia.edu/relationships#isContainedWithin";
    
    public static void main(String args[]) throws Exception {
        Properties p = new Properties();
        p.load(EADIngest.class.getClassLoader().getResourceAsStream("config/fedora-test.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        Properties catalogP = new Properties();
        catalogP.load(EADIngest.class.getClassLoader().getResourceAsStream("config/virgo.properties"));
        
        // Papers of John Dos Passos
        File eadFile = new File("data/viu01215.xml");
        String[] eadId = new String[] { "u3523359" };
        
        // Papers of Dr James Carmichael
        //File eadFile = new File("data/viu01265.xml");
        //String[] eadId = new String[] { "u2762707" };
        
        // Church
        //File eadFile = new File("data/viu00003.xml");
        //String[] eadId = new String[] { "u2525293", "u4327007", "u4293731" };
        
        // Holsinger
        //File eadFile = new File("data/viu02465.xml");
        //String[] eadId = new String[] {"u2091463", "u2091469", "u3686913", "u1909107", "u2316160" };
        
        // Walter Reed Yellow Fever
        //File eadFile = new File("data/viuh00010.xml");
        //String[] eadId = new String[] { "u3653257" };
        
        EADIngest i = new EADIngest(eadFile, eadId, catalogP.getProperty("catalog-url"));
        try {
            i.buildFedoraObjects(fc, eadFile.getName());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        //i.purgeFedoraObjects(fc, eadFile.getName());
    }
    
    
    /**
     * Prints the structure of the parsed EAD file from the
     * EADIngest object to the standard output stream.
     */
    public static void printStructure(EADIngest i, Element el) throws Exception {
        System.out.println(el == null ? "root" : el.getNodeName());
        if (el != null) {
            System.out.println(getXMLDocument(i.getArchivalComponentMetadata(el)));
        }
        for (Element c : i.listArchivalComponents(el)) {
            printStructure(i, c);
        }
    }
    
    /**
     * Serializes a DOM to an XML String which is returned.
     */
    public static String getXMLDocument(Document doc) throws TransformerException, IOException {
        DOMSource source = new DOMSource(doc);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamResult sResult = new StreamResult(baos);
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer t = tFactory.newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        t.setOutputProperty(OutputKeys.METHOD, "xml");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        t.transform(source, sResult);
        return baos.toString("UTF-8");
    }
    
    private Document eadDoc;
    
    private Document[] marcXmlDocs;
    
    private String[] marcIds;
    
    private Map<String, String> holdingCallNumberToPidMap;
    
    /**
     * A transformer to return just the collection-scoped 
     * information from an EAD XML file.
     */
    private Transformer collectionTransform;
    
    /**
     * A transformer to return just the archival component
     * information when applied to a given archival component.
     * (c01, c02, c03, etc. elements)
     */
    private Transformer cTransform;
    
    private Transformer holdingTransform;
    
    private String catalogUrl;
    
    public EADIngest(File file, String[] marcRecordIds, String catalogUrl) throws Exception {
        this.catalogUrl = catalogUrl;
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        eadDoc = f.newDocumentBuilder().parse(file);
        
        marcXmlDocs = new Document[marcRecordIds.length];
        for (int i = 0; i < marcRecordIds.length ; i ++) {
            URL url = new URL(catalogUrl + marcRecordIds[i] + ".xml");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            marcXmlDocs[i] = f.newDocumentBuilder().parse(conn.getInputStream());
            
        }
        marcIds = marcRecordIds;
            
        
        // default transformers
        TransformerFactory tFactory = TransformerFactory.newInstance("net.sf.saxon.TransformerFactoryImpl",null);
        Templates template = tFactory.newTemplates(new StreamSource(EADIngest.class.getClassLoader().getResourceAsStream("strip-hierarchy.xsl")));
        Transformer t = template.newTransformer();
        collectionTransform = t;
        cTransform = t;
        
        template = tFactory.newTemplates(new StreamSource(EADIngest.class.getClassLoader().getResourceAsStream("convert-holding.xsl")));
        holdingTransform = template.newTransformer();
        
    }
    
    /**
     * Creates fedora objects (unless they already exist) that represent
     * this finding aid.  The id is assigned to the object for the purpose
     * of later removing or updating that object.
     */
    public void buildFedoraObjects(FedoraClient fc, String id) throws Exception {
        if (doesObjectExist(fc, id)) {
            throw new IllegalStateException("The object already exists!");
        } else {
            // create the object
            String pid = FedoraClient.ingest().execute(fc).getPid();
            System.out.println("Created " + pid);
            
            // add the ead fragment
            FedoraClient.addDatastream(pid, eadRootDsId).content(getXMLDocument(getCollectionMetadata())).controlGroup("M").mimeType("text/xml").execute(fc);
            
            // add specified ID to the DC datastream
            FedoraClient.addDatastream(pid, "DC").content(getXMLDocument(new DCRecord(FedoraClient.getDatastreamDissemination(pid, "DC").execute(fc).getEntityInputStream()).addIdentifier(id).getDocument())).execute(fc);
            
            // add the content models
            FedoraClient.addRelationship(pid).object("info:fedora/" + logicalCollectionContentModelPid).predicate(HAS_MODEL_PREDICATE).execute(fc);
            FedoraClient.addRelationship(pid).object("info:fedora/" + eadRootContentModelPid).predicate(HAS_MODEL_PREDICATE).execute(fc);
            
            // create and add the marc record
            for (int i = 0; i < marcXmlDocs.length; i ++) {
                String marcPid = FedoraClient.ingest().execute(fc).getPid();
                FedoraClient.addDatastream(marcPid, "DC").content(getXMLDocument(new DCRecord(FedoraClient.getDatastreamDissemination(marcPid, "DC").execute(fc).getEntityInputStream()).addIdentifier(marcIds[i]).getDocument())).execute(fc);
                FedoraClient.addDatastream(marcPid, marcDsId).content(getXMLDocument(marcXmlDocs[i])).controlGroup("M").execute(fc);
                FedoraClient.addRelationship(marcPid).object("info:fedora/" + pid).predicate(hasMarcPredicate).execute(fc);

                // pull the firehose records and create and add any containers
                addOrUpdateHoldingRecords(marcPid, marcIds[i], fc);
            }
            
            // add all the children
            String previousPid = null;
            for (Element c : listArchivalComponents(null)) {
                previousPid = addNode(fc, pid, c, previousPid);
            }
        }
    }
    
    private String addNode(FedoraClient fc, String parentPid, Element el, String previousItemPid) throws Exception{
        // create the object
        String pid = FedoraClient.ingest().execute(fc).getPid();
        System.out.println("Created " + pid);

        // add the relationship to the parent
        FedoraClient.addRelationship(pid).object("info:fedora/" + parentPid).predicate(isPartOfPredicate).execute(fc);
        
        // add the ead fragment
        FedoraClient.addDatastream(pid, eadComponentDsId).content(getXMLDocument(getArchivalComponentMetadata(el))).controlGroup("M").mimeType("text/xml").execute(fc);
        
        // add the content model
        FedoraClient.addRelationship(pid).object("info:fedora/" + logicalComponentContentModelPid).predicate(HAS_MODEL_PREDICATE).execute(fc);
        FedoraClient.addRelationship(pid).object("info:fedora/" + eadComponentContentModelPid).predicate(HAS_MODEL_PREDICATE).execute(fc);
        if (previousItemPid != null) {
            FedoraClient.addRelationship(pid).object("info:fedora/" + previousItemPid).predicate(followsSequentiallyPredicate).execute(fc);
        }
        
        // add all the children
        String previousPid = null;
        for (Element c : listArchivalComponents(el)) {
            previousPid = addNode(fc, pid, c, previousPid);
        }
        return pid;
    }
    
    /**
     * Purges the fedora objects that represent this finding aid.
     */
    public void purgeFedoraObjects(FedoraClient fc, String id) throws Exception {
        for (String pid : lookupObjectById(fc, id)) {
            purgePidAndParts(fc, pid);
        }
    }
    
    private void purgePidAndParts(FedoraClient fc, String pid) throws Exception {
        System.out.println("Purging " + pid + "...");
        for (String childPid : getParts(fc, pid, isPartOfPredicate)) {
            purgePidAndParts(fc, childPid);
        }
        for (String childPid : getParts(fc, pid, hasMarcPredicate)) {
            purgePidAndParts(fc, childPid);
        }
        
        for (String childPid : getParts(fc, pid, physicalContainerNestingPredicate)) {
            purgePidAndParts(fc, childPid);
        }
        FedoraClient.purgeObject(pid).execute(fc);
    }
    
    public Document getCollectionMetadata() throws TransformerException, ParserConfigurationException {
        Document result = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        collectionTransform.transform(new DOMSource(eadDoc), new DOMResult(result));
        return result;
    }
    
    public List<Element> listArchivalComponents(Element parent) throws XPathExpressionException {
        List<Element> children = new ArrayList<Element>();
        XPath xpath = XPathFactory.newInstance().newXPath();
        String path = "//c01";
        if (parent == null) {
            parent = eadDoc.getDocumentElement();
        } else {
            Matcher m = Pattern.compile("c(\\d\\d)").matcher(parent.getNodeName());
            if (m.matches()) {
                path = ".//c" + new DecimalFormat("00").format(Integer.parseInt(m.group(1)) + 1);
            } else {
                throw new RuntimeException("Unexpected element: " + parent.getNodeName());
            }
        }
        NodeList nl = (NodeList) xpath.evaluate(path, parent, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i ++) {
            children.add((Element) nl.item(i));
        }
        return children;
    }
    
    public void addOrUpdateHoldingRecords(String marcRecordPid, String marcRecordId, FedoraClient fc) throws Exception {
        holdingCallNumberToPidMap = new HashMap<String, String>();
        
        // pull the holdings information from firehose
        URL url = new URL(catalogUrl + marcRecordId + "/firehose");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        Document firehoseDoc = f.newDocumentBuilder().parse(conn.getInputStream());

        // purge existing records?
        
        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList holdingNl = (NodeList) xpath.evaluate("/catalogItem/holding", firehoseDoc, XPathConstants.NODESET);
        for (int i = 0; i < holdingNl.getLength(); i ++) {
            Element holdingEl = (Element) holdingNl.item(i);
            String callNumber = (String) xpath.evaluate("@callNumber", holdingEl, XPathConstants.STRING);
            
            String pid = FedoraClient.ingest().execute(fc).getPid();
            System.out.println("Created " + pid);

            // add the relationship to the parent
            FedoraClient.addRelationship(pid).object("info:fedora/" +marcRecordPid).predicate(physicalContainerNestingPredicate).execute(fc);
            
            // add the metadata
            FedoraClient.addDatastream(pid, this.physicalContainerDsId).content(getXMLDocument(getHoldingContainerMetadata(holdingEl))).controlGroup("M").mimeType("text/xml").execute(fc);
            
            // add the content model
            FedoraClient.addRelationship(pid).object("info:fedora/" + physicalContainerContentModel).predicate(HAS_MODEL_PREDICATE).execute(fc);
 
            holdingCallNumberToPidMap.put(callNumber, pid);
        }
        
    }
    /*
    public List<Map<String, String>> getMarcFields(String tag) throws XPathExpressionException {
        List<Element> children = new ArrayList<Element>();
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {

            public String getNamespaceURI(String prefix) {
                if (prefix.equals("marc")) {
                    return "http://www.loc.gov/MARC21/slim";
                } else {
                    return null;
                }
            }

            public String getPrefix(String nsUri) {
                if (nsUri.equals("http://www.loc.gov/MARC21/slim")) {
                    return "marc";
                } else {
                    return null;
                }
            }

            public Iterator getPrefixes(String nsUri) {
                throw new UnsupportedOperationException();
            }});
        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        NodeList fields = (NodeList) xpath.evaluate("marc:record/marc:datafield[@tag='" + tag + "']", marcXmlDoc, XPathConstants.NODESET);
        for (int j = 0; j < fields.getLength(); j ++) {
            NodeList subfields = (NodeList) xpath.evaluate("marc:subfield", fields.item(j), XPathConstants.NODESET);
            Map<String, String> map = new HashMap<String, String>();
            for (int i = 0; i < subfields.getLength(); i ++) {
                map.put((String) xpath.evaluate("@code", subfields.item(i), XPathConstants.STRING), (String) xpath.evaluate("text()", subfields.item(i), XPathConstants.STRING));
            }
            result.add(map);
        }
        return result;
    }
    */
    
    public Document getArchivalComponentMetadata(Element ac) throws TransformerException, ParserConfigurationException {
        Document fragment = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element fragmentEl = (Element) ac.cloneNode(true);
        fragment.adoptNode(fragmentEl);
        fragment.appendChild(fragmentEl);
        Document result = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        cTransform.transform(new DOMSource(fragment), new DOMResult(result));
        return result;
    }
    
    public Document getHoldingContainerMetadata(Element holding) throws TransformerException, ParserConfigurationException {
        Document fragment = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element fragmentEl = (Element) holding.cloneNode(true);
        fragment.adoptNode(fragmentEl);
        fragment.appendChild(fragmentEl);
        Document result = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        holdingTransform.transform(new DOMSource(fragment), new DOMResult(result));
        return result;
    }
    
    
    public static boolean doesObjectExist(FedoraClient fc, String id) throws NumberFormatException, IOException, FedoraClientException {
        String itqlQuery = "select $object from <#ri> where $object <dc:identifier> '" + id + "'";
        BufferedReader reader = new BufferedReader(new InputStreamReader(FedoraClient.riSearch(itqlQuery).lang("itql").format("count").execute(fc).getEntityInputStream()));
        int count = Integer.parseInt(reader.readLine());
        return count == 1;
    }
    
    public static List<String> lookupObjectById(FedoraClient fc, String id) throws NumberFormatException, IOException, FedoraClientException {
        String itqlQuery = "select $object from <#ri> where $object <dc:identifier> '" + id + "'";
        BufferedReader reader = new BufferedReader(new InputStreamReader(FedoraClient.riSearch(itqlQuery).lang("itql").format("simple").execute(fc).getEntityInputStream()));
        List<String> pids = new ArrayList<String>();
        String line = null;
        Pattern p = Pattern.compile("\\Qobject : <info:fedora/\\E([^\\>]+)\\Q>\\E");
        while ((line = reader.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                pids.add(m.group(1));
            }
        }
        return pids;
    }
    
    public static List<String> getParts(FedoraClient fc, String parent, String isPartOfPredicate) throws Exception {
        String itqlQuery = "select $object from <#ri> where $object <" + isPartOfPredicate + "> <info:fedora/" + parent + ">";
        BufferedReader reader = new BufferedReader(new InputStreamReader(FedoraClient.riSearch(itqlQuery).lang("itql").format("simple").execute(fc).getEntityInputStream()));
        List<String> pids = new ArrayList<String>();
        String line = null;
        Pattern p = Pattern.compile("\\Qobject : <info:fedora/\\E([^\\>]+)\\Q>\\E");
        while ((line = reader.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                pids.add(m.group(1));
            }
        }
        return pids;
    }
    
    public static String getFirstPart(FedoraClient fc, String parent, String isPartOfPredicate, String followsPredicate) throws Exception {
        String itqlQuery = "select $object from <#ri> where $object <" + isPartOfPredicate + "> <info:fedora/" + parent + "> minus $object <" + followsPredicate + "> $other";
        BufferedReader reader = new BufferedReader(new InputStreamReader(FedoraClient.riSearch(itqlQuery).lang("itql").format("simple").execute(fc).getEntityInputStream()));
        List<String> pids = new ArrayList<String>();
        String line = null;
        Pattern p = Pattern.compile("\\Qobject : <info:fedora/\\E([^\\>]+)\\Q>\\E");
        while ((line = reader.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                pids.add(m.group(1));
            }
        }
        if (pids.isEmpty()) {
            return null;
        } else if (pids.size() == 1) {
            return pids.get(0);
        } else {
            throw new RuntimeException("Multiple items are \"first\"!");
        }
    }
    
    public static String getNextPart(FedoraClient fc, String partPid, String followsPredicate) throws Exception {
        String itqlQuery = "select $object from <#ri> where $object <" + followsPredicate + "> <info:fedora/" + partPid + ">";
        BufferedReader reader = new BufferedReader(new InputStreamReader(FedoraClient.riSearch(itqlQuery).lang("itql").format("simple").execute(fc).getEntityInputStream()));
        List<String> pids = new ArrayList<String>();
        String line = null;
        Pattern p = Pattern.compile("\\Qobject : <info:fedora/\\E([^\\>]+)\\Q>\\E");
        while ((line = reader.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                pids.add(m.group(1));
            }
        }
        if (pids.isEmpty()) {
            return null;
        } else if (pids.size() == 1) {
            return pids.get(0);
        } else {
            throw new RuntimeException("Multiple items follow " + partPid + "!");
        }
    }
    
    public static List<String> getOrderedParts(FedoraClient fc, String parent, String isPartOfPredicate, String followsPredicate) throws Exception {
        String itqlQuery = "select $object $previous from <#ri> where $object <" + isPartOfPredicate + "> <info:fedora/" + parent + "> and $object <" + followsPredicate + "> $previous";
        BufferedReader reader = new BufferedReader(new InputStreamReader(FedoraClient.riSearch(itqlQuery).lang("itql").format("csv").execute(fc).getEntityInputStream()));
        Map<String, String> prevToNextMap = new HashMap<String, String>();
        String line = reader.readLine(); // read the csv labels
        Pattern p = Pattern.compile("\\Qinfo:fedora/\\E([^,]*),\\Qinfo:fedora/\\E([^,]*)");
        while ((line = reader.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                prevToNextMap.put(m.group(2), m.group(1));
            } else {
                throw new RuntimeException(line + " does not match pattern!");
            }
        }
        
        List<String> pids = new ArrayList<String>();
        String pid = getFirstPart(fc, parent, isPartOfPredicate, followsPredicate);
        while (pid != null) {
            pids.add(pid);
            String nextPid = prevToNextMap.get(pid);
            prevToNextMap.remove(pid);
            pid = nextPid;
            
        }
        if (!prevToNextMap.isEmpty()) {
            throw new RuntimeException("Broken relaitonship chain after \"" + pids.get(pids.size() - 1));
        }
        return pids;
    }
    
}
