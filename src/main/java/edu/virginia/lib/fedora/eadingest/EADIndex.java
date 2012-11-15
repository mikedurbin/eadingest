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

    public static String SERVICE_PID = "uva-lib:indexableSDef";
    public static String SERVICE_METHOD = "getIndexingMetadata";
    
    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        p.load(EADIndex.class.getClassLoader().getResourceAsStream("config/fedora-test.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        p = new Properties();
        p.load(AnalyzeContainerInformation.class.getClassLoader().getResourceAsStream("config/ontology.properties"));
        EADOntology o = new EADOntology(p);
        
        Properties solrP = new Properties();
        solrP.load(PostSolrDocument.class.getClassLoader().getResourceAsStream("config/solr.properties"));
        String updateUrl = solrP.getProperty("solr-update-url");
        boolean dryRun = solrP.containsKey("dry-run") && solrP.getProperty("dry-run").equals("true");
        String filterPid = solrP.getProperty("filter-pid");
        
        try {
            // find all collections
            List<String> collectionRootPids = EADIngest.getSubjects(fc, o.eadRootCModel(), EADOntology.HAS_MODEL_PREDICATE);
            System.out.println(collectionRootPids.size() + " collections found");
            for (String cpid : collectionRootPids) {
                if (filterPid == null || filterPid.equals(cpid)) {
                    System.out.println("  " + cpid);
                    if (!dryRun) {
                        PostSolrDocument.indexPid(fc, cpid, updateUrl, SERVICE_PID, SERVICE_METHOD);
                    }
                    List<String> marcPids = !o.isInverted(EADOntology.Relationship.HAS_MARC) 
                            ? EADIngest.getObjects(fc, cpid, o.getRelationship(EADOntology.Relationship.HAS_MARC)) 
                            : EADIngest.getSubjects(fc, cpid, o.getInverseRelationship(EADOntology.Relationship.HAS_MARC));
                    for (String marcPid : marcPids) {
                        System.out.println("    " + "marc --> " + marcPid);
                        List<String> containerPids = !o.isInverted(EADOntology.Relationship.DEFINES_CONTAINER) 
                                ? EADIngest.getObjects(fc,  marcPid, o.getRelationship(EADOntology.Relationship.DEFINES_CONTAINER)) 
                                : EADIngest.getSubjects(fc,  marcPid, o.getInverseRelationship(EADOntology.Relationship.DEFINES_CONTAINER));
                        for (String containerPid : containerPids) {
                            System.out.println("      " + "container --> " + containerPid);
                        }
        
                    }
                    printParts(fc, cpid, "  ", dryRun, updateUrl, o);
                } else {
                    System.out.println("  " + cpid + " (skipped)");
                }
            }
            if (!dryRun) {
                PostSolrDocument.commit(updateUrl);
            }
        } catch (Throwable t) {
            PostSolrDocument.rollback(updateUrl);
            if (t instanceof Exception) {
                throw (Exception) t;
            } else if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new RuntimeException(t);
            }
        } 
    }
    
    public static void printParts(FedoraClient fc, String parentPid, String indent, boolean dryRun, String updateUrl, EADOntology o) throws Exception {
        if (o.isInverted(EADOntology.Relationship.IS_PART_OF) || o.isInverted(EADOntology.Relationship.FOLLOWS)) {
            throw new RuntimeException("getOrderedParts does not support inverted relationships!");
        }
        for (String partPid : EADIngest.getOrderedParts(fc, parentPid, o.getRelationship(EADOntology.Relationship.IS_PART_OF), o.getRelationship(EADOntology.Relationship.FOLLOWS))) {
            System.out.println(indent + "component --> " + partPid);
            if (!dryRun) {
                PostSolrDocument.indexPid(fc, partPid, updateUrl, SERVICE_PID, SERVICE_METHOD);
            }
            printParts(fc, partPid, indent + "  ", dryRun, updateUrl, o);
        }
    }
    
}
