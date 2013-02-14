package edu.virginia.lib.fedora.eadingest;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Enumeration;
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

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraClientException;
import com.yourmediashelf.fedora.client.FedoraCredentials;
import com.yourmediashelf.fedora.client.response.FedoraResponse;
import com.yourmediashelf.fedora.client.response.IngestResponse;

import edu.virginia.lib.fedora.eadingest.container.AnalyzeContainerInformation;
import edu.virginia.lib.fedora.eadingest.multipage.PageMapper;
import edu.virginia.lib.fedora.eadingest.multipage.RubyHashPageMapper;
import edu.virginia.lib.fedora.eadingest.multipage.UndigitizedPageMapper;
import edu.virginia.lib.fedora.eadingest.pidmapping.PidMapping;
import edu.virginia.lib.fedora.eadingest.pidmapping.SpecializedPidMapping;

/**
 * Test code to ingest XML content into a local fedora repository.
 */
public class EADIngest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("EADIngest -- a utility to ingest EAD content as well as the supporting objects");

        Properties p = new Properties();
        p.load(EADIngest.class.getClassLoader().getResourceAsStream("config/fedora-test.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));
        
        p = new Properties();
        p.load(AnalyzeContainerInformation.class.getClassLoader().getResourceAsStream("config/ontology.properties"));
        EADOntology o = new EADOntology(p);
        
        
        boolean purge = false;
        if (args.length != 1) {
            System.out.println("You must specify exactly one of the following parameters:");
            System.out.println("    --purge     purges the content models and EAD files");
            System.out.println("    --ingest    ingests the content models and EAD files");
            System.exit(1);
        } else if (args[0].trim().equalsIgnoreCase("--purge")) {
            purge = true;
        } else if (args[0].trim().equalsIgnoreCase("--ingest")) {
            purge = false;
        }
        
        // ingest or purge the support objects
        if (purge) {
            EADIngest.purgeSupportObjects(fc);
        } else {
            EADIngest.ingestSupportObjects(fc);
        }
        
        // ingest or purge the EAD document objects
        for (EADIngest i : getRecognizedEADIngests(o, fc)) {
            if (purge) {
                if (i.exists()) {
                    i.purgeFedoraObjects();
                }
            } else {
                if (!i.exists()) {
                    i.buildFedoraObjects();
                } else {
                    System.out.println("Collection already exists.");
                }
            }
        }
        
    }

    public static List<EADIngest> getRecognizedEADIngests(EADOntology o, FedoraClient fc) throws Exception {
        List<EADIngest> eadIngests = new ArrayList<EADIngest>();
        
        Properties catalogP = new Properties();
        catalogP.load(EADIngest.class.getClassLoader().getResourceAsStream("config/virgo.properties"));
        String catalogUrl = catalogP.getProperty("catalog-url");

        // Papers of John Dos Passos
        URL url = EADIngest.class.getClassLoader().getResource("ead/viu01215.xml");
        if (url != null) {
            EADIngest dosPassos = new EADIngest(new File(url.toURI()), new String[] { "u3523359" }, catalogUrl, o, new UndigitizedPageMapper(), fc);
            dosPassos.setPidMapping(new SpecializedPidMapping(new File(ReplaceCollection.class.getClassLoader().getResource("ead/viu01215-pid-mapping.txt").toURI())));
            eadIngests.add(dosPassos);
        }
        
        // Papers of Dr James Carmichael
        url = EADIngest.class.getClassLoader().getResource("ead/viu01265.xml");
        if (url != null) {
            eadIngests.add(new EADIngest(new File(url.toURI()), new String[] { "u2762707" }, catalogUrl, o, null, fc));
        }
        
        // Church
        url = EADIngest.class.getClassLoader().getResource("ead/viu00003.xml");
        URL mappingUrl = EADIngest.class.getClassLoader().getResource("ead/viu00003-digitized-item-mapping.txt");
        if (url != null) {
            EADIngest church = new EADIngest(new File(url.toURI()), new String[] { "u2525293", "u4327007", "u4293731" }, catalogUrl, o, new RubyHashPageMapper(FileUtils.readFileToString(new File(mappingUrl.toURI()))), fc); 
            church.setPidMapping(new SpecializedPidMapping(new File(ReplaceCollection.class.getClassLoader().getResource("ead/viu00003-pid-mapping.txt").toURI())));
            eadIngests.add(church);
        }
        
        // Holsinger
        url = EADIngest.class.getClassLoader().getResource("ead/viu02465.xml");
        if (url != null) {
            eadIngests.add(new EADIngest(new File(url.toURI()), new String[] {"u2091463", "u2091469", "u3686913", "u1909107", "u2316160" }, catalogUrl, o, null, fc));
        }
        
        // Walter Reed Yellow Fever
        url = EADIngest.class.getClassLoader().getResource("ead/viuh00010.xml");
        if (url != null) {
            eadIngests.add(new EADIngest(new File(url.toURI()), new String[] { "u3653257" }, catalogUrl, o, null, fc));
        }
        
        return eadIngests;
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
    
    private FedoraClient fc;
    
    private EADOntology o;
    
    private Document eadDoc;
    
    private Document[] marcXmlDocs;
    
    private String[] marcIds;

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
    
    /**
     * The id for the root element: derived from the filename.
     */
    private String id;
    
    private PidMapping pidMapping;
    
    private PageMapper pageMapper;
    
    public EADIngest(File file, String[] marcRecordIds, String catalogUrl, EADOntology ontology, PageMapper pm, FedoraClient fc) throws Exception {
        this.catalogUrl = catalogUrl;
        o = ontology;
        this.fc = fc;
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
            
        pageMapper = pm;
        
        // default transformers
        TransformerFactory tFactory = TransformerFactory.newInstance("net.sf.saxon.TransformerFactoryImpl",null);
        Templates template = tFactory.newTemplates(new StreamSource(EADIngest.class.getClassLoader().getResourceAsStream("strip-hierarchy.xsl")));
        Transformer t = template.newTransformer();
        collectionTransform = t;
        cTransform = t;
        
        template = tFactory.newTemplates(new StreamSource(EADIngest.class.getClassLoader().getResourceAsStream("convert-holding.xsl")));
        holdingTransform = template.newTransformer();
        
        id = file.getName().replace(".xml", "");
    }
    
    public void setPidMapping(PidMapping p) {
        pidMapping = p;
    }
    
    public String getCollectionId() {
        return id;
    }
    
    public boolean exists() throws Exception {
        return doesObjectExist(fc, id);
    }

    public static List<String> purgeSupportObjects(FedoraClient fc) throws Exception {
        return processSupportObjects(fc, true);
    }
    
    public static List<String> ingestSupportObjects(FedoraClient fc) throws Exception {
        return processSupportObjects(fc, false);
    }
    
    /**
     * Ingests or purges the support objects referenced by EAD content.
     * @param purge if true, purges rather than ingests
     * @return a list of pids for only the newly ingested or purged objects
     */
    private static List<String> processSupportObjects(FedoraClient fc, boolean purge) throws URISyntaxException, IOException, NumberFormatException, FedoraClientException {
        List<String> updated = new ArrayList<String>();
        Enumeration<URL> resources = EADIngest.class.getClassLoader().getResources("foxml");
        while (resources.hasMoreElements()) {
            File file = new File(resources.nextElement().toURI());
            for (File foxml : file.listFiles()) {
                String impliedPid = foxml.getName().replace('_', ':').replace(".xml", "");
                boolean exists = doesObjectExist(fc, impliedPid);
                if (purge) {
                    // purge the object if present
                    if (exists) {
                        FedoraResponse response = FedoraClient.purgeObject(impliedPid).execute(fc);
                        if (response.getStatus() / 100 != 2) {
                            throw new RuntimeException("Error purging \"" + impliedPid + "\": " + response.getStatus());
                        } else {
                            System.out.println("Purged " + impliedPid + ".");
                            updated.add(impliedPid);
                        }                    
                    } else {
                        System.out.println("No need to purge " + impliedPid + " because it wasn't found.");
                    }
                } else {
                    // ingest the object if not present
                    if (!exists) {
                        try {
                            IngestResponse response = FedoraClient.ingest().content(foxml).execute(fc);
                            if (!response.getPid().equals(impliedPid)) {
                                throw new RuntimeException("Expected \"" + foxml.getName() + "\" to produce an object with pid \"" + impliedPid + "\" but got \"" + response.getPid() + "\" instead!");
                            }
                            if (response.getStatus() / 100 != 2) {
                                throw new RuntimeException("Error ingesting \"" + foxml.getName() + "\": " + response.getStatus());
                            } else {
                                System.out.println("Ingested " + response.getPid() + ".");
                                updated.add(impliedPid);
                            }
                        } catch (Exception ex) {
                            throw new RuntimeException("Exception while ingesting \"" + foxml + "\"!", ex);
                        }
                    } else {
                        System.out.println("Skipped " + impliedPid + " because it was already present.");
                    }
                }
            }
        }
        return updated;
    }
    
    /**
     * Creates fedora objects (unless they already exist) that represent
     * this finding aid.  The id is assigned to the object for the purpose
     * of later removing or updating that object.
     */
    public void buildFedoraObjects() throws Exception {
        // create the object
        String pid = pidMapping != null ? pidMapping.getPidForElement(getCollectionMetadata().getDocumentElement()) : null;
        if (pid != null) {
            pid = FedoraClient.ingest(pid).execute(fc).getPid();
        } else {
            pid = FedoraClient.ingest().execute(fc).getPid();
        }
        System.out.println("Created " + pid + " (collection object)");
                    
        // add the ead fragment
        FedoraClient.addDatastream(pid, o.eadRootDSID()).content(getXMLDocument(getCollectionMetadata())).controlGroup("M").mimeType("text/xml").execute(fc);
        
        // add specified ID to the DC datastream
        FedoraClient.addDatastream(pid, "DC").content(getXMLDocument(new DCRecord(FedoraClient.getDatastreamDissemination(pid, "DC").execute(fc).getEntityInputStream()).addIdentifier(id).getDocument())).execute(fc);
        
        // add the content models
        FedoraClient.addRelationship(pid).object("info:fedora/" + o.eadRootCModel()).predicate(EADOntology.HAS_MODEL_PREDICATE).execute(fc);
        FedoraClient.addRelationship(pid).object("info:fedora/" + o.eadFragmentCmodel()).predicate(EADOntology.HAS_MODEL_PREDICATE).execute(fc);
        
        // create and add the marc record
        for (int i = 0; i < marcXmlDocs.length; i ++) {
            Document marcDoc = marcXmlDocs[i];
            String marcPid = pidMapping != null ? pidMapping.getPidForElement(marcDoc.getDocumentElement()) : null;
            if (marcPid != null) {
                marcPid = FedoraClient.ingest(marcPid).execute(fc).getPid();
            } else {
                marcPid = FedoraClient.ingest().execute(fc).getPid();
            }
            System.out.println("Created " + marcPid + " (marc record)");
                
            FedoraClient.addDatastream(marcPid, "DC").content(getXMLDocument(new DCRecord(FedoraClient.getDatastreamDissemination(marcPid, "DC").execute(fc).getEntityInputStream()).addIdentifier(marcIds[i]).getDocument())).execute(fc);
            FedoraClient.addDatastream(marcPid, o.marcDSID()).content(getXMLDocument(marcDoc)).controlGroup("M").execute(fc);
            if (!o.isInverted(EADOntology.Relationship.HAS_MARC)) {
                // add the relationship from the collection object to the marc object
                FedoraClient.addRelationship(pid).object("info:fedora/" + marcPid).predicate(o.getRelationship(EADOntology.Relationship.HAS_MARC)).execute(fc);
            } else {
                // add the relationship from the marc object to the collection object
                FedoraClient.addRelationship(marcPid).object("info:fedora/" + pid).predicate(o.getInverseRelationship(EADOntology.Relationship.HAS_MARC)).execute(fc);
            }
            
            // add the content model
            FedoraClient.addRelationship(marcPid).object("info:fedora/" + o.marcCmodel()).predicate(EADOntology.HAS_MODEL_PREDICATE).execute(fc);
            
            // pull the firehose records and create and add any containers
            addOrUpdateHoldingRecords(marcPid, marcIds[i]);
        }
        
        // add all the children
        String previousPid = null;
        for (Element c : listArchivalComponents(null)) {
            previousPid = addNode(fc, pid, c, previousPid);
        }
    }
    
    private String addNode(FedoraClient fc, String parentPid, Element el, String previousItemPid) throws Exception{
        // create the object
        String pid = null;
        if (pidMapping != null && pidMapping.getPidForElement(el) != null) {
            pid = FedoraClient.ingest(pidMapping.getPidForElement(el)).execute(fc).getPid();
        } else {
            pid = FedoraClient.ingest().execute(fc).getPid();
        }
        System.out.println("Created " + pid + " (" + el.getNodeName() + ")");

        if (!o.isInverted(EADOntology.Relationship.IS_PART_OF)) {
            // add the relationship from the part to the whole
            FedoraClient.addRelationship(pid).object("info:fedora/" + parentPid).predicate(o.getRelationship(EADOntology.Relationship.IS_PART_OF)).execute(fc);
        } else {
            // add the relationship from the whole to the part
            FedoraClient.addRelationship(parentPid).object("info:fedora/" + pid).predicate(o.getInverseRelationship(EADOntology.Relationship.IS_PART_OF)).execute(fc);

        }
        
        Document acDoc  = getArchivalComponentMetadata(el);
        
        XPath xpath = XPathFactory.newInstance().newXPath();
        String level = (String) xpath.evaluate("@level", acDoc.getDocumentElement());
        String id = (String) xpath.evaluate("@id", acDoc.getDocumentElement());
        if ("item".equals(level)) {
            // find or create the item
            String itemPid = null;
            List<String> pages = pageMapper.getDigitizedItemPagePids(id);
            if (pages != null && !pages.isEmpty()) {
                itemPid = findOrCreateMultipageObject(pageMapper.getDigitizedItemPagePids(id), pageMapper.getExemplar(id), getXMLDocument(acDoc));
            } else {
                // add the ead fragment directly
                FedoraClient.addDatastream(pid, o.eadComponentDSID()).content(getXMLDocument(acDoc)).controlGroup("M").mimeType("text/xml").execute(fc);
                
            }
           
            // add the content models
            FedoraClient.addRelationship(pid).object("info:fedora/" + o.eadItemCModel()).predicate(EADOntology.HAS_MODEL_PREDICATE).execute(fc);
            
            if (itemPid != null) {
                // this is a placehoder
                // add the relationship to the physical item objectand the corresponding content model
                FedoraClient.addRelationship(pid).object("info:fedora/" + itemPid).predicate(o.getRelationship(EADOntology.Relationship.IS_METADATA_PLACEHOLDER_FOR)).execute(fc);
                FedoraClient.addRelationship(pid).object("info:fedora/" + o.metadataPlaceholderCmodel()).predicate(EADOntology.HAS_MODEL_PREDICATE).execute(fc);
            } else {
                // this is a logical item only (there is no digitized representation of the physical item)
                // add the ead fragment and the "metadata" content model
                FedoraClient.addDatastream(pid, o.eadComponentDSID()).content(getXMLDocument(acDoc)).controlGroup("M").mimeType("text/xml").execute(fc);
                FedoraClient.addRelationship(pid).object("info:fedora/" + o.eadFragmentCmodel()).predicate(EADOntology.HAS_MODEL_PREDICATE).execute(fc);
            }
            
            // add sequence relationship
            if (previousItemPid != null) {
                if (!o.isInverted(EADOntology.Relationship.FOLLOWS)) {
                    // add the relationship from the follower to the followee
                    FedoraClient.addRelationship(pid).object("info:fedora/" + previousItemPid).predicate(o.getRelationship(EADOntology.Relationship.FOLLOWS)).execute(fc);
                } else {
                    // add the relationship from the followee to the follower
                    FedoraClient.addRelationship(previousItemPid).object("info:fedora/" + pid).predicate(o.getInverseRelationship(EADOntology.Relationship.FOLLOWS)).execute(fc);
                }
            }
        } else {
            // add the ead fragment
            FedoraClient.addDatastream(pid, o.eadComponentDSID()).content(getXMLDocument(acDoc)).controlGroup("M").mimeType("text/xml").execute(fc);
            
            // add the content models
            FedoraClient.addRelationship(pid).object("info:fedora/" + o.eadFragmentCmodel()).predicate(EADOntology.HAS_MODEL_PREDICATE).execute(fc);
            FedoraClient.addRelationship(pid).object("info:fedora/" + o.eadComponentCModel()).predicate(EADOntology.HAS_MODEL_PREDICATE).execute(fc);
            if (previousItemPid != null) {
                if (!o.isInverted(EADOntology.Relationship.FOLLOWS)) {
                    // add the relationship from the follower to the followee
                    FedoraClient.addRelationship(pid).object("info:fedora/" + previousItemPid).predicate(o.getRelationship(EADOntology.Relationship.FOLLOWS)).execute(fc);
                } else {
                    // add the relationship from the followee to the follower
                    FedoraClient.addRelationship(previousItemPid).object("info:fedora/" + pid).predicate(o.getInverseRelationship(EADOntology.Relationship.FOLLOWS)).execute(fc);
                }
            }
            
            // add all the children
            String previousPid = null;
            for (Element c : listArchivalComponents(el)) {
                previousPid = addNode(fc, pid, c, previousPid);
            }
        }
        return pid;
    }
    
    /**
     * Purges the fedora objects that represent this finding aid.
     */
    public void purgeFedoraObjects() throws Exception {
        for (String pid : lookupObjectsById(fc, id)) {
            purgePidAndParts(pid); 
        }
    }
    
    private void purgePidAndParts(String pid) throws Exception {
        System.out.println("Purging " + pid + "...");
        try {
            if (o.isInverted(EADOntology.Relationship.IS_PART_OF)) {
                for (String childPid : getObjects(fc, pid, o.getInverseRelationship(EADOntology.Relationship.IS_PART_OF))) {
                    purgePidAndParts(childPid);
                }
            } else {
                for (String childPid : getSubjects(fc, pid, o.getRelationship(EADOntology.Relationship.IS_PART_OF))) {
                    purgePidAndParts(childPid);
                }
            }
            if (o.isInverted(EADOntology.Relationship.HAS_MARC)) {
                for (String childPid : getSubjects(fc, pid, o.getInverseRelationship(EADOntology.Relationship.HAS_MARC))) {
                    purgePidAndParts(childPid);
                }
            } else {
                for (String childPid : getObjects(fc, pid, o.getRelationship(EADOntology.Relationship.HAS_MARC))) {
                    purgePidAndParts(childPid);
                }
            }
            /*
            if (o.isInverted(EADOntology.Relationship.IS_CONTAINED_WITHIN)) {
                for (String childPid : getSubjects(fc, pid, o.getInverseRelationship(EADOntology.Relationship.IS_CONTAINED_WITHIN))) {
                    purgePidAndParts(childPid);
                }
            } else {
                for (String childPid : getObjects(fc, pid, o.getRelationship(EADOntology.Relationship.IS_CONTAINED_WITHIN))) {
                    purgePidAndParts(childPid);
                }
            }
            */
            
            if (o.isInverted(EADOntology.Relationship.DEFINES_CONTAINER)) {
                for (String childPid : getSubjects(fc, pid, o.getInverseRelationship(EADOntology.Relationship.DEFINES_CONTAINER))) {
                    purgePidAndParts(childPid);
                }
            } else {
                for (String childPid : getObjects(fc, pid, o.getRelationship(EADOntology.Relationship.DEFINES_CONTAINER))) {
                    purgePidAndParts(childPid);
                }
            }
            FedoraClient.purgeObject(pid).execute(fc);
        } catch (FedoraClientException ex) {
            System.err.println("Unable to purge " + pid + "! (" + ex.getMessage() + ")");
        }
    }
    
    public void testPidMapping() throws Exception {
        if (pidMapping == null) {
            throw new IllegalStateException("You must invoke setPidMapping() before testPidMapping()!");
        }
        String pid = pidMapping.getPidForElement(getCollectionMetadata().getDocumentElement());
        System.out.println("Found " + pid + " (collection)");

        for (int i = 0; i < marcXmlDocs.length; i ++) {
            Document marcDoc = marcXmlDocs[i];
            String marcPid = pidMapping.getPidForElement(marcDoc.getDocumentElement());
            System.out.println("Found " + marcPid + " (marc)");
            testHoldingRecords(marcPid, marcIds[i]);
        }
        
        // test all the children
        String previousPid = null;
        for (Element c : listArchivalComponents(null)) {
            previousPid = testNode(fc, pid, c, previousPid);
        }
    }
    
    public void testHoldingRecords(String marcRecordPid, String marcRecordId) throws Exception {
        // pull the holdings information from firehose
        URL url = new URL(catalogUrl + marcRecordId + "/firehose");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        Document firehoseDoc = f.newDocumentBuilder().parse(conn.getInputStream());
        
        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList holdingNl = (NodeList) xpath.evaluate("/catalogItem/holding", firehoseDoc, XPathConstants.NODESET);
        for (int i = 0; i < holdingNl.getLength(); i ++) {
            Element holdingEl = (Element) holdingNl.item(i);
            Document containerDoc = getHoldingContainerMetadata(holdingEl);
            System.out.println("Found " + pidMapping.getPidForElement(containerDoc.getDocumentElement()) + " (container)");
        }
        
    }
    
    private String testNode(FedoraClient fc, String parentPid, Element el, String previousItemPid) throws Exception{
        // create the object
        String pid = pidMapping.getPidForElement(el);
        System.out.println("Found " + pid + " (" + el.getNodeName() + ")");

        // test all the children
        String previousPid = null;
        for (Element c : listArchivalComponents(el)) {
            previousPid = testNode(fc, pid, c, previousPid);
        }
        return pid;
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
    
    public void addOrUpdateHoldingRecords(String marcRecordPid, String marcRecordId) throws Exception {
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
            Document containerDoc = getHoldingContainerMetadata(holdingEl);
            String pid = null;
            if (pidMapping != null && pidMapping.getPidForElement(containerDoc.getDocumentElement()) != null) {
                pid = FedoraClient.ingest(pidMapping.getPidForElement(containerDoc.getDocumentElement())).execute(fc).getPid();
            } else {
                pid = FedoraClient.ingest().execute(fc).getPid();
            }
            System.out.println("Created " + pid + " (container)");

            if (!o.isInverted(EADOntology.Relationship.DEFINES_CONTAINER)) {
                // add the relationship from the marc record to the container
                FedoraClient.addRelationship(marcRecordPid).object("info:fedora/" +pid).predicate(o.getRelationship(EADOntology.Relationship.DEFINES_CONTAINER)).execute(fc);
            } else {
                // add the relationship from the container to the marc record
                FedoraClient.addRelationship(pid).object("info:fedora/" + marcRecordPid).predicate(o.getInverseRelationship(EADOntology.Relationship.DEFINES_CONTAINER)).execute(fc);
            }
            
            // add the metadata
            FedoraClient.addDatastream(pid, o.containerDSID()).content(getXMLDocument(containerDoc)).controlGroup("M").mimeType("text/xml").execute(fc);
            
            // add the content model
            FedoraClient.addRelationship(pid).object("info:fedora/" + o.containerCmodel()).predicate(EADOntology.HAS_MODEL_PREDICATE).execute(fc);
        }
        
    }
    
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
    
    /**
     * A method to locate or create the object in fedora that represents the
     * aggregate of the multiple objects that represent the scanned or otherwise
     * digitized pages of a physical object.
     * 
     * This method relies on the resource index to determine if an object already
     * exists.  If syncupdates is not true in fedora, this method may result in
     * the creation of additional multipage objects when called in relatively 
     * rapid succession with the same parameters.
     */
    public String findOrCreateMultipageObject(List<String> pages, String exemplarPid, String descMetadata) throws Exception {
        // look for the grouping object
        List<String> groupingObjects = null;
        if (o.isInverted(EADOntology.Relationship.HAS_DIGITAL_REPRESENTATION)) {
            groupingObjects = getObjects(fc, pages.get(0), o.getInverseRelationship(EADOntology.Relationship.HAS_DIGITAL_REPRESENTATION));
        } else {
            groupingObjects = getSubjects(fc, pages.get(0), o.getRelationship(EADOntology.Relationship.HAS_DIGITAL_REPRESENTATION));
        }
        if (groupingObjects.size() == 1) {
            System.out.println("found " + groupingObjects.get(0));
            return groupingObjects.get(0);
        } else if (groupingObjects.size() > 1) {
            throw new RuntimeException("There are multiple objects that reference the object " + pages.get(0) + "!");
        } else {
            // create the grouping object
            String pid = FedoraClient.ingest().execute(fc).getPid();
            System.out.println("created " + pid + " (multipage)");
            // link to each page
            for (String pagePid : pages) {
                if (o.isInverted(EADOntology.Relationship.HAS_DIGITAL_REPRESENTATION)) {
                    FedoraClient.addRelationship(pagePid).object("info:fedora/" + pid).predicate(o.getInverseRelationship(EADOntology.Relationship.HAS_DIGITAL_REPRESENTATION)).execute(fc);
                } else {
                    FedoraClient.addRelationship(pid).object("info:fedora/" + pagePid).predicate(o.getRelationship(EADOntology.Relationship.HAS_DIGITAL_REPRESENTATION)).execute(fc);
                }
                if (pagePid.equals(exemplarPid)) {
                    FedoraClient.addRelationship(pid).object("info:fedora/" + pagePid).predicate(o.getRelationship(EADOntology.Relationship.HAS_EXEMPLAR)).execute(fc);
                }
            }
            
            // add descriptive metadata
            FedoraClient.addDatastream(pid, o.eadItemDSID()).content(descMetadata).controlGroup("M").mimeType("text/xml").execute(fc);

            // add the content model
            FedoraClient.addRelationship(pid).object("info:fedora/" + o.eadFragmentCmodel()).predicate(EADOntology.HAS_MODEL_PREDICATE).execute(fc);
            
            return pid;
        }
        
    }
    
    
    public static boolean doesObjectExist(FedoraClient fc, String id) throws NumberFormatException, IOException, FedoraClientException {
        /* If resource index doesn't sync updates, this won't work... */
        String itqlQuery = "select $object from <#ri> where $object <dc:identifier> '" + id + "'";
        BufferedReader reader = new BufferedReader(new InputStreamReader(FedoraClient.riSearch(itqlQuery).lang("itql").format("count").execute(fc).getEntityInputStream()));
        int count = Integer.parseInt(reader.readLine());
        return count == 1;
    }
    
    public static String lookupObjectById(FedoraClient fc, String id) throws NumberFormatException, IOException, FedoraClientException {
        List<String> pids = lookupObjectsById(fc, id);
        if (pids.size() != 1) {
            throw new IllegalArgumentException(pids.size() + " objects found with id \"" + id + "\"!");
        }
        return pids.get(0);
    }
    
    public static List<String> lookupObjectsById(FedoraClient fc, String id) throws NumberFormatException, IOException, FedoraClientException {
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
    
    /**
     * Gets the subjects of the given predicate for which the object is give given object.
     * For example, a relationship like "[subject] follows [object]" this method would always
     * return the subject that comes before the given object.  
     * @param fc the fedora client that mediates access to fedora
     * @param object the pid of the object that will have the given predicate relationship
     * to all subjects returned.
     * @param predicate the predicate to query
     * @return the URIs of the subjects that are related to the given object by the given
     * predicate
     */

    public static List<String> getSubjects(FedoraClient fc, String object, String predicate) throws Exception {
        if (predicate == null) {
            throw new NullPointerException("predicate must not be null!");
        }
        String itqlQuery = "select $subject from <#ri> where $subject <" + predicate + "> <info:fedora/" + object + ">";
        BufferedReader reader = new BufferedReader(new InputStreamReader(FedoraClient.riSearch(itqlQuery).lang("itql").format("simple").execute(fc).getEntityInputStream()));
        List<String> pids = new ArrayList<String>();
        String line = null;
        Pattern p = Pattern.compile("\\Qsubject : <info:fedora/\\E([^\\>]+)\\Q>\\E");
        while ((line = reader.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                pids.add(m.group(1));
            }
        }
        return pids;
    }
    
    /**
     * Gets the objects of the given predicate for which the subject is give given subject.
     * For example, a relationship like "[subject] hasMarc [object]" this method would always
     * return marc record objects for the given subject.  
     * @param fc the fedora client that mediates access to fedora
     * @param subject the pid of the subject that will have the given predicate relationship 
     * to all objects returned.
     * @param predicate the predicate to query
     * @return the URIs of the objects that are related to the given subject by the given
     * predicate
     */
    public static List<String> getObjects(FedoraClient fc, String subject, String predicate) throws Exception {
        if (predicate == null) {
            throw new NullPointerException("predicate must not be null!");
        }
        String itqlQuery = "select $object from <#ri> where <info:fedora/" + subject + "> <" + predicate + "> $object";
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
            throw new RuntimeException("Multiple items are \"first\"! " + pids.get(0) + ", " + pids.get(1) + ")");
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
        if (pid == null && !prevToNextMap.isEmpty()) {
            // this is to handle some broke objects... in effect it treats
            // objects whose "previous" is not a sibling as if they had
            // no "previous"
            for (String prev : prevToNextMap.keySet()) {
                if (!prevToNextMap.values().contains(prev)) {
                    if (pid == null) {
                        pid = prev;
                    } else {
                        throw new RuntimeException("Two \"first\" children!");
                    }
                }
            }
        }
        while (pid != null) {
            pids.add(pid);
            String nextPid = prevToNextMap.get(pid);
            prevToNextMap.remove(pid);
            pid = nextPid;
            
        }
        if (!prevToNextMap.isEmpty()) {
            for (Map.Entry<String, String> entry : prevToNextMap.entrySet()) {
                System.err.println(entry.getKey() + " --> " + entry.getValue());
            }
            throw new RuntimeException("Broken relationship chain");
        }
        return pids;
    }
    
}
