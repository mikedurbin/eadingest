package edu.virginia.lib.fedora.eadingest;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraClientException;
import com.yourmediashelf.fedora.client.FedoraCredentials;

/**
 * A simple utility to import digitized items from one repository
 * to another.  This utility is incomplete and untested.
 */
public class ImportDigitizedItems {

    public static void main(String args[]) throws Exception {
        System.out.println("ImportDigitizedItems");
        
        Properties prodP = new Properties();
        prodP.load(ImportDigitizedItems.class.getClassLoader().getResourceAsStream("config/fedora-production.properties"));
        FedoraClient source = new FedoraClient(new FedoraCredentials(prodP.getProperty("fedora-url"), prodP.getProperty("fedora-username"), prodP.getProperty("fedora-password")));

        Properties testP = new Properties();
        testP.load(ImportDigitizedItems.class.getClassLoader().getResourceAsStream("config/fedora-test.properties"));
        FedoraClient dest = new FedoraClient(new FedoraCredentials(testP.getProperty("fedora-url"), testP.getProperty("fedora-username"), testP.getProperty("fedora-password")));
        
        Properties p = new Properties();
        p.load(ImportDigitizedItems.class.getClassLoader().getResourceAsStream("config/ontology.properties"));
        EADOntology o = new EADOntology(p);
        
        /*
         *  TODO: this all works on the test environment, but should
         *  be parameterized if this class or program will ever be used
         *  more generally.
         */
        int count = 1;
        boolean stealIdentity = false;
        String sourceCollection = "uva-lib:744806";
        String destinationCollection = "uva-lib-test:4556";
        String sourcePredicate = "http://fedora.lib.virginia.edu/relationships#hasCatalogRecordIn";
        List<String> pids = EADIngest.getSubjects(source, sourceCollection, sourcePredicate);
        System.out.println(prodP.getProperty("fedora-url"));
        System.out.println("  " + sourceCollection + ": " + pids.size() + " items found");
        for (int i = 0; i < pids.size() && i < count; i ++) {
            String pid = pids.get(i);
            System.out.println("    " + pid);
            copyObjectAndUpdateRelationship(source, dest, pid, sourcePredicate, sourceCollection, o, destinationCollection, stealIdentity);
        }
    }
    
    /**
     * Copies an object from one repository to another then creates a "logical-item" object 
     * in the new repository that references that copied item within the context of an 
     * archival collection in the new repository.
     * 
     * @param source the repository from which the object will be copied
     * @param dest the repository to which the object will be copied
     * @param subjectPid the pid of the object to be copied
     * @param oldPredicateUri (not currently used)
     * @param oldObjectPid (not currently used)
     * @param o the ontology for the new repository
     * @param newObjectPid the pid of the parent (series, collection, whatever)
     *     for the archival collection context into which we are importing the
     *     object
     * @param stealIdentity if true, when copying over the object, the logical
     *     object that's created will take the pid of the original object and
     *     the otherwise-exact copy of the original object will have a new pid.
     */
    public static void copyObjectAndUpdateRelationship(FedoraClient source, FedoraClient dest, String subjectPid, String oldPredicateUri, String oldObjectPid, EADOntology o, String newObjectPid, boolean stealIdentity) throws Exception {
        String logicalItemPid = null;
        
        if (stealIdentity) {
            logicalItemPid = subjectPid;
            String newSubjectPid = FedoraClient.getNextPID().execute(dest).getPid();
            copyObject(subjectPid, source, dest, false, newSubjectPid);
            subjectPid = newSubjectPid;
        } else {
            copyObject(subjectPid, source, dest, false, null);
        }
        
        logicalItemPid = logicalItemPid != null 
                ? FedoraClient.ingest(logicalItemPid).execute(dest).getPid() 
                : FedoraClient.ingest().execute(dest).getPid();
        FedoraClient.addRelationship(logicalItemPid).predicate(EADIngest.HAS_MODEL_PREDICATE).object("info:fedora/" + o.eadItemCModel()).execute(dest);
        FedoraClient.addRelationship(logicalItemPid).predicate(EADIngest.HAS_MODEL_PREDICATE).object("info:fedora/" + o.metadataPlaceholderCmodel()).execute(dest);
        FedoraClient.addRelationship(logicalItemPid).predicate(o.getRelationship(EADOntology.Relationship.IS_PART_OF)).object("info:fedora/" + newObjectPid).execute(dest);
        FedoraClient.addRelationship(logicalItemPid).predicate(o.getRelationship(EADOntology.Relationship.IS_PLACEHOLDER_FOR)).object("info:fedora/" + subjectPid).execute(dest);
        System.out.println(logicalItemPid + " --> " + subjectPid);
        // TODO: add the "follows" relationship
        
        // We don't actually want to monkey with the existing object
        //FedoraClient.purgeRelationship(subjectPid).predicate(oldPredicateUri).object("info:fedora/" + oldObjectPid).execute(dest);
        //FedoraClient.addRelationship(subjectPid).predicate(newPredicateUri).object("info:fedora/" + newObjectPid).execute(dest);
    }
    
    public static void copyObject(String pid, FedoraClient source, FedoraClient dest, boolean overwrite, String newPid) throws IOException, FedoraClientException {
        if (!(FedoraClient.findObjects().pid().query("pid=" + pid).execute(dest).getPids().size() == 0 || overwrite)) {
            System.out.println("Skipping " + pid + " because it already exists.");
        } else {
            System.out.print("Copying " + pid + "...");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtils.copy(FedoraClient.export(pid).context("archive").execute(source).getEntityInputStream(), baos);
            if (newPid != null) {
                // replace the pid: this doesn't work if the 
                // RELS-EXT or RELS-INT datastreams aren't using the
                // X control group.
                FedoraClient.ingest().content(baos.toString("UTF-8").replace(pid, newPid).replaceAll("\\Q<foxml:contentDigest\\E[^>]*\\Q/>\\E", "").replaceAll("(?s)\\Q<foxml:datastream ID=\"RELS-INT\"\\E.*?\\Q</foxml:datastream>\\E", "")).execute(dest);
            } else {
                FedoraClient.ingest().content(baos.toString("UTF-8")).execute(dest);
            }
            
            System.out.println("DONE");
        }
    }
    
}
