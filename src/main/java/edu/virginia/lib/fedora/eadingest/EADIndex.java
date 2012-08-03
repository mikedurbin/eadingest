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

    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        p.load(EADIndex.class.getClassLoader().getResourceAsStream("config/fedora-test.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        // find all collections
        List<String> collectionRootPids = EADIngest.getParts(fc, "cmodel:ead-root", EADIngest.HAS_MODEL_PREDICATE);
        System.out.println(collectionRootPids.size() + " collections found");
        for (String cpid : collectionRootPids) {
            System.out.println("  " + cpid);
            PostSolrDocument.indexPid(fc, cpid);
            for (String marcPid : EADIngest.getParts(fc,  cpid, "http://fedora.lib.virginia.edu/relationship#hasHoldingRecordsFor")) {
                System.out.println("    " + "marc --> " + marcPid);
                for (String containerPid : EADIngest.getParts(fc,  marcPid, "http://fedora.lib.virginia.edu/relationships#isContainedWithin")) {
                    System.out.println("      " + "container --> " + containerPid);
                }

            }
            printParts(fc, cpid, "  ");
        }
        
        
    }
    
    public static void printParts(FedoraClient fc, String parentPid, String indent) throws Exception {
        /* SLOW algorithm: N requests */
        /*
        String partPid = EADIngest.getFirstPart(fc, parentPid, "info:fedora/fedora-system:def/relations-external#isPartOf", "http://fedora.lib.virginia.edu/relationship#follows");
        while (partPid != null) {
            System.out.println(indent + "component --> " + partPid);
            printParts(fc, partPid, indent + "  ");
            partPid = EADIngest.getNextPart(fc, partPid, "http://fedora.lib.virginia.edu/relationship#follows");
        }
        */
        
        /* Faster algorithm 1 Request, then sort */
        for (String partPid : EADIngest.getOrderedParts(fc, parentPid, "info:fedora/fedora-system:def/relations-external#isPartOf", "http://fedora.lib.virginia.edu/relationship#follows")) {
            System.out.println(indent + "component --> " + partPid);
            PostSolrDocument.indexPid(fc, partPid);
            printParts(fc, partPid, indent + "  ");
        }
    }
    
}
