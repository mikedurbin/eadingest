package edu.virginia.lib.fedora.eadingest.container;

import java.util.List;
import java.util.Properties;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraCredentials;

import edu.virginia.lib.fedora.eadingest.EADIndex;
import edu.virginia.lib.fedora.eadingest.EADIngest;
import edu.virginia.lib.fedora.eadingest.EADOntology;

public class LinkAllContainersToCollection {

    /**
     * A simple utility that links all known containers with
     * the collection-level object.  This is probably the best
     * default behavior.
     */
    public static void main(String args[]) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: LinkAllContainersToCollection [collectionPid]");
            return;
        } else {
            String cpid = args[0];
            
            Properties p = new Properties();
            p.load(EADIndex.class.getClassLoader().getResourceAsStream("config/fedora-test.properties"));
            FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

            p = new Properties();
            p.load(AnalyzeContainerInformation.class.getClassLoader().getResourceAsStream("config/ontology.properties"));
            EADOntology o = new EADOntology(p);
            
            List<String> marcPids = !o.isInverted(EADOntology.Relationship.HAS_MARC) 
                    ? EADIngest.getObjects(fc, cpid, o.getRelationship(EADOntology.Relationship.HAS_MARC)) 
                    : EADIngest.getSubjects(fc, cpid, o.getInverseRelationship(EADOntology.Relationship.HAS_MARC));
            for (String marcPid : marcPids) {
                List<String> containerPids = !o.isInverted(EADOntology.Relationship.DEFINES_CONTAINER) 
                        ? EADIngest.getObjects(fc,  marcPid, o.getRelationship(EADOntology.Relationship.DEFINES_CONTAINER)) 
                        : EADIngest.getSubjects(fc,  marcPid, o.getInverseRelationship(EADOntology.Relationship.DEFINES_CONTAINER));
                for (String containerPid : containerPids) {
                    if (!o.isInverted(EADOntology.Relationship.IS_CONTAINED_WITHIN)) {
                        FedoraClient.addRelationship(cpid).object("info:fedora/" + containerPid).predicate(o.getRelationship(EADOntology.Relationship.IS_CONTAINED_WITHIN)).execute(fc);
                        System.out.println(cpid + " --" + o.getRelationship(EADOntology.Relationship.IS_CONTAINED_WITHIN) + "--> " + containerPid);
                    } else {
                        FedoraClient.addRelationship(containerPid).object("info:fedora/" + cpid).predicate(o.getInverseRelationship(EADOntology.Relationship.IS_CONTAINED_WITHIN)).execute(fc);
                        System.out.println(containerPid + " --" + o.getInverseRelationship(EADOntology.Relationship.IS_CONTAINED_WITHIN) + "--> " + cpid);
                    }
                }
            }
            
        }
        
        
    }
    
}
