package edu.virginia.lib.fedora.eadingest;
import java.util.List;
import java.util.Properties;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraCredentials;

import edu.virginia.lib.fedora.eadingest.EADIngest;

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
        
        String updateUrl = solrP.getProperty("solr-update-url");
        boolean dryRun = solrP.containsKey("dry-run") && solrP.getProperty("dry-run").equals("true");
        String filterPid = solrP.getProperty("filter-pid");
        
        // find all collections
        List<String> collectionRootPids = EADIngest.getParts(fc, "cmodel:ead-root", EADIngest.HAS_MODEL_PREDICATE);
        System.out.println(collectionRootPids.size() + " collections found");
        for (String cpid : collectionRootPids) {
            if (filterPid == null || filterPid.equals(cpid)) {
                System.out.println("  " + cpid);
                if (!dryRun) {
                    PostSolrDocument.indexPid(fc, cpid, updateUrl, SERVICE_PID, SERVICE_METHOD);
                }
                for (String marcPid : EADIngest.getParts(fc,  cpid, "http://fedora.lib.virginia.edu/relationship#hasHoldingRecordsFor")) {
                    System.out.println("    " + "marc --> " + marcPid);
                    for (String containerPid : EADIngest.getParts(fc,  marcPid, "http://fedora.lib.virginia.edu/relationships#isContainedWithin")) {
                        System.out.println("      " + "container --> " + containerPid);
                    }
    
                }
                printParts(fc, cpid, "  ", dryRun, updateUrl);
            }
        }
        
        
    }
    
    public static void printParts(FedoraClient fc, String parentPid, String indent, boolean dryRun, String updateUrl) throws Exception {
        for (String partPid : EADIngest.getOrderedParts(fc, parentPid, "info:fedora/fedora-system:def/relations-external#isPartOf", "http://fedora.lib.virginia.edu/relationship#follows")) {
            System.out.println(indent + "component --> " + partPid);
            if (!dryRun) {
                PostSolrDocument.indexPid(fc, partPid, updateUrl, SERVICE_PID, SERVICE_METHOD);
            }
            printParts(fc, partPid, indent + "  ", dryRun, updateUrl);
        }
    }
    
}
