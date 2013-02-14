package edu.virginia.lib.fedora.eadingest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraClientException;

/**
 * A class that exposes information about an object in fedora that is a
 * participant in a hierarchical structure.  This class allows for easy 
 * traversal and inspection of the objects that make up the tree, but isn't
 * currently envisioned to expose access.
 *
 * Because the underlying system is external and dynamic, instances of this
 * class should treated as a way to inspect the current state of the underlying
 * object, and not a comprehensive picture of that object at any given time.
 */
public class HierarchyParticipant {

    private FedoraClient fc;

    private EADOntology o;

    private String pid;

    private DocumentBuilder parser;

    private XPath xpath;

    private List<String> contentModelPids;

    /**
     * @throws IllegalArgumentException if the objects specified by the
     * supplied pid cannot be found in the given repository or does not have
     * a content model that reflects its participation in the hierarchy.
     */
    public HierarchyParticipant(String pid, FedoraClient fc, EADOntology o) throws ParserConfigurationException, XPathExpressionException, FedoraClientException, SAXException, IOException {
        this.pid = pid;
        this.o = o;
        this.fc = fc;
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        parser = f.newDocumentBuilder();
        xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {

            public String getNamespaceURI(String prefix) {
                if (prefix.equals("rdf")) {
                    return "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
                } else if (prefix.equals("fedora-model")) {
                    return "info:fedora/fedora-system:def/model#";
                } else {
                    return null;
                }
            }

            public String getPrefix(String nsUri) {
                if (nsUri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#")) {
                    return "rdf";
                } else if (nsUri.equals("info:fedora/fedora-system:def/model#")) {
                    return "fedora-model";
                } else {
                    return null;
                }
            }

            public Iterator getPrefixes(String nsUri) {
                return Collections.singleton(getPrefix(nsUri)).iterator();
            }});
        
        contentModelPids = getContentModelPids();
        if (!contentModelPids.contains(o.eadComponentCModel()) && !contentModelPids.contains(o.eadRootCModel()) && !contentModelPids.contains(o.eadItemCModel())) {
            throw new IllegalArgumentException("Object " + pid + " does not have a content model that identifies it as a hierarchy participant!");
        }
    }

    public String getPid() {
        return pid;
    }

    public List<HierarchyParticipant> getChildren() throws Exception {
        String itqlQuery = "select $object $previous from <#ri> where $object <" + o.getRelationship(EADOntology.Relationship.IS_PART_OF) + "> <info:fedora/" + getPid() + "> and $object <" + o.getRelationship(EADOntology.Relationship.FOLLOWS) + "> $previous";
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
        
        List<HierarchyParticipant> pids = new ArrayList<HierarchyParticipant>();
        String pid = getFirstPart(fc, getPid(), o.getRelationship(EADOntology.Relationship.IS_PART_OF), o.getRelationship(EADOntology.Relationship.FOLLOWS));
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
            pids.add(new HierarchyParticipant(pid, fc, o));
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
    
    private List<String> getContentModelPids() throws FedoraClientException, SAXException, IOException, XPathExpressionException {
        Document relsExt = parser.parse(FedoraClient.getDatastreamDissemination(pid, "RELS-EXT").execute(fc).getEntityInputStream());
        List<String> cmodels = new ArrayList<String>();
        NodeList nl = (NodeList) xpath.evaluate("rdf:RDF/rdf:Description/fedora-model:hasModel", relsExt, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i ++) {
            cmodels.add(((String) xpath.evaluate("@rdf:resource", nl.item(i), XPathConstants.STRING)).replace("info:fedora/", ""));
        }
        return cmodels;
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

}
