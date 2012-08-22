package edu.virginia.lib.fedora.eadingest.container;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraCredentials;

import edu.virginia.lib.fedora.eadingest.EADIngest;
import edu.virginia.lib.fedora.eadingest.EADOntology;

public class AnalyzeContainerInformation {

    public static void main(String [] args) throws Exception {
        if (args.length != 2) {
            System.out.println("AnalyzeContainerInformation");
            System.out.println("");
            System.out.println("You must enter two parameters, the pid of the collection to analyze");
            System.out.println("followed by either \"summary\" to output a summary of the referenced");
            System.out.println("containers, or \"link\" which will match up container references based");
            System.out.println("on the rules hard-coded into this program and add relationships to the");
            System.out.println("repository.");
            System.out.println();
            System.out.println("It is recommended that one run the \"summary\" and use it to create a");
            System.out.println("new ContainerReferenceResolver instance that works for the given collection.");
            System.exit(-1);
        }
        Properties p = new Properties();
        p.load(AnalyzeContainerInformation.class.getClassLoader().getResourceAsStream("config/fedora-test.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));
        System.out.println("Acting on " + p.getProperty("fedora-url") + "...");
        
        p = new Properties();
        p.load(AnalyzeContainerInformation.class.getClassLoader().getResourceAsStream("config/ontology.properties"));
        EADOntology o = new EADOntology(p);
        
        AnalyzeContainerInformation a = new AnalyzeContainerInformation(fc, o, args[0], new MSS5950ContainerReferenceResolver());
        
        if (args[1].equals("summary")) {
            a.printSummary();
        } else if (args[1].equals("link")) {
            a.addLinks();
        } else if (args[1].equals("fix")) {
            // unadvertized feature that fixes inverted relationships
            // created by obsolete versions of this software
            a.fixContainers();
        } else {
            System.out.println("\nUnrecognized parameter: \"" + args[1] + "\"");
        }
    }
    
    private FedoraClient fc;
    
    private String collectionPid;
    
    private Map<String, String> boxToPidMap;
    
    private ContainerReferenceResolver r;
    
    private Set<String> referencedBoxes;
    
    private XPath xpath;
    
    private DocumentBuilder parser;
    
    private EADOntology o;
    
    public AnalyzeContainerInformation(FedoraClient fc, EADOntology o, String collectionPid, ContainerReferenceResolver hr) throws Exception {
        r = hr;
        referencedBoxes = new HashSet<String>();
        this.o = o;
        this.fc = fc;
        this.collectionPid = collectionPid;
        
        parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        xpath = XPathFactory.newInstance().newXPath();
        
        boxToPidMap = new HashMap<String, String>();
        System.out.println(collectionPid);
        for (String marcPid : EADIngest.getObjects(fc,  collectionPid, o.hasMarc())) {
            System.out.println("  marc (" + marcPid + ")");
            for (String containerPid : EADIngest.getObjects(fc,  marcPid, o.definesContainer())) {
                Document doc = parser.parse(FedoraClient.getDatastreamDissemination(containerPid, "descMetadata").execute(fc).getEntityInputStream());
                String id = (String) xpath.evaluate("container/callNumber", doc, XPathConstants.STRING);
                System.out.println("    " + id + " --> " + containerPid);
                boxToPidMap.put(id, containerPid);
            }
        }
    }
    
    private void visitNodes(FedoraClient fc, String parentPid, String indent, boolean addLinks) throws Exception {
        for (String partPid : EADIngest.getOrderedParts(fc, parentPid, "info:fedora/fedora-system:def/relations-external#isPartOf", "http://fedora.lib.virginia.edu/relationship#follows")) {
            Document eadFragment = parser.parse(FedoraClient.getDatastreamDissemination(partPid, o.eadComponentDSID()).execute(fc).getEntityInputStream());
            String callNumber = ((String) xpath.evaluate("//container/@label", eadFragment, XPathConstants.STRING)).trim() + " " 
                        + ((String) xpath.evaluate("//container/text()", eadFragment, XPathConstants.STRING)).trim();
            if (!callNumber.trim().equals("")) {
                if (addLinks) {
                    for (String h : r.getCannonicalContainerIdentifier(callNumber, boxToPidMap.keySet())) {
                        System.out.println(partPid + " is contained in " + h + " (" + boxToPidMap.get(h) + ")");
                        FedoraClient.addRelationship(partPid).object("info:fedora/" + boxToPidMap.get(h)).predicate(o.isContainedWithin()).execute(fc);
                        System.out.println(" ... added link: " + partPid + " --" + o.isContainedWithin() + "--> " + boxToPidMap.get(h));
                    }
                }
                referencedBoxes.add(callNumber);
            }
            visitNodes(fc, partPid, indent + "  ", addLinks);
        }
    }
    
    public void fixContainers() throws Exception {
        for (String marcPid : EADIngest.getSubjects(fc,  collectionPid, o.hasMarc())) {
            for (String containerPid : EADIngest.getSubjects(fc,  marcPid, o.isContainedWithin())) {
                // add the relationship from the marc record to the container
                FedoraClient.addRelationship(marcPid).object("info:fedora/" + containerPid).predicate(o.definesContainer()).execute(fc);

                // remove the relationship from the container to the marc record
                FedoraClient.purgeRelationship(containerPid).object("info:fedora/" + marcPid).predicate(o.isContainedWithin()).execute(fc);
                
                System.out.println("Reversed relationship between " + marcPid + " (marc) <-- " + containerPid + " (container)");
            }
        }
    }
    
    public void addLinks() throws Exception {
        visitNodes(fc, collectionPid, "  ", true);
    }
    
    public void printSummary() throws Exception {
        if (referencedBoxes.isEmpty()) {
            visitNodes(fc, collectionPid, "  ", false);
        }
        
        System.out.println("\nKnown containers:");
        ArrayList<String> keys = new ArrayList<String>(boxToPidMap.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            System.out.println("  " + key);
        }
        
        System.out.println("\nReferenced containers:");
        ArrayList<String> a = new ArrayList<String>(referencedBoxes);
        Collections.sort(a);
        for (String key : a) {
            System.out.println("  " + key);
        }
    }
    
    
}