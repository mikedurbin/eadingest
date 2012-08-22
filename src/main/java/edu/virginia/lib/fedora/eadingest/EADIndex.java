package edu.virginia.lib.fedora.eadingest;
import java.util.List;
import java.util.Properties;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraCredentials;

import edu.virginia.lib.fedora.eadingest.EADIngest;
import edu.virginia.lib.fedora.eadingest.container.AnalyzeContainerInformation;

/**
 * Performs some queries on a fedora repository and prints 
 * out information about EAD-based collections while updating
 * the index records for each item.
 */
public class EADIndex {

    public static String SERVICE_PID = "sdef:indexable";
    public static String SERVICE_METHOD = "getIndexingMetadata";
    
    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        p.load(EADIndex.class.getClassLoader().getResourceAsStream("config/fedora-test.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        Properties solrP = new Properties();
        solrP.load(PostSolrDocument.class.getClassLoader().getResourceAsStream("config/solr.properties"));
        
        p = new Properties();
        p.load(AnalyzeContainerInformation.class.getClassLoader().getResourceAsStream("config/ontology.properties"));
        EADOntology o = new EADOntology(p);
        
        
        String updateUrl = solrP.getProperty("solr-update-url");
        boolean dryRun = solrP.containsKey("dry-run") && solrP.getProperty("dry-run").equals("true");
        String filterPid = solrP.getProperty("filter-pid");
        
        // find all collections
        List<String> collectionRootPids = EADIngest.getSubjects(fc, o.eadRootCModel(), EADIngest.HAS_MODEL_PREDICATE);
        System.out.println(collectionRootPids.size() + " collections found");
        for (String cpid : collectionRootPids) {
            if (filterPid == null || filterPid.equals(cpid)) {
                System.out.println("  " + cpid);
                if (!dryRun) {
                    PostSolrDocument.indexPid(fc, cpid, updateUrl, SERVICE_PID, SERVICE_METHOD);
                }
                for (String marcPid : EADIngest.getObjects(fc,  cpid, o.hasMarc())) {
                    System.out.println("    " + "marc --> " + marcPid);
                    for (String containerPid : EADIngest.getObjects(fc,  marcPid, o.definesContainer())) {
                        System.out.println("      " + "container --> " + containerPid);
                    }
    
                }
                printParts(fc, cpid, "  ", dryRun, updateUrl, o);
            }
        }
        
        
    }
    
    public static void printParts(FedoraClient fc, String parentPid, String indent, boolean dryRun, String updateUrl, EADOntology o) throws Exception {
        for (String partPid : EADIngest.getOrderedParts(fc, parentPid, o.isPartOf(), o.follows())) {
            System.out.println(indent + "component --> " + partPid);
            if (!dryRun) {
                PostSolrDocument.indexPid(fc, partPid, updateUrl, SERVICE_PID, SERVICE_METHOD);
            }
            printParts(fc, partPid, indent + "  ", dryRun, updateUrl, o);
        }
    }
    
}
