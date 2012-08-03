package edu.virginia.lib.fedora.eadingest;

import java.io.IOException;
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

    public static final String isPlaceholderFor = "http://fedora.lib.virginia.edu/relationships#isPlaceholderFor";
    
    public static void main(String args[]) throws Exception {
        Properties prodP = new Properties();
        prodP.load(EADIndex.class.getClassLoader().getResourceAsStream("config/fedora-production.properties"));
        FedoraClient source = new FedoraClient(new FedoraCredentials(prodP.getProperty("fedora-url"), prodP.getProperty("fedora-username"), prodP.getProperty("fedora-password")));

        Properties testP = new Properties();
        testP.load(EADIndex.class.getClassLoader().getResourceAsStream("config/fedora-test.properties"));
        FedoraClient dest = new FedoraClient(new FedoraCredentials(testP.getProperty("fedora-url"), testP.getProperty("fedora-username"), testP.getProperty("fedora-password")));
        
        copyObjectAndUpdateRelationship(source, dest, "uva-lib:1051794", "http://fedora.lib.virginia.edu/relationships#hasCatalogRecordIn", "uva-lib:744806", "info:fedora/fedora-system:def/relations-external#isPartOf", "uva-lib-test:4556");
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
     * @param newPredicateUri the predicate URI for the relationship to
     *     a logical object in archival collection context into which we
     *     are importing the object.
     * @param newObjectPid the pid of the parent (series, collection, whatever)
     *     for the archival colleciton context into which we are importing the
     *     object
     */
    public static void copyObjectAndUpdateRelationship(FedoraClient source, FedoraClient dest, String subjectPid, String oldPredicateUri, String oldObjectPid, String newPredicateUri, String newObjectPid) throws Exception {
        copyObject(subjectPid, source, dest, false);
        
        String logicalItemPid = FedoraClient.ingest().execute(dest).getPid();
        FedoraClient.addRelationship(logicalItemPid).predicate(EADIngest.HAS_MODEL_PREDICATE).object("info:fedora/cmodel:logical-item").execute(dest);
        FedoraClient.addRelationship(logicalItemPid).predicate(newPredicateUri).object("info:fedora/" + newObjectPid).execute(dest);
        FedoraClient.addRelationship(logicalItemPid).predicate(isPlaceholderFor).object("info:fedora/" + subjectPid).execute(dest);
        // TODO: add the "follows" relationship
        
        // We don't actually want to monkey with the existing object
        //FedoraClient.purgeRelationship(subjectPid).predicate(oldPredicateUri).object("info:fedora/" + oldObjectPid).execute(dest);
        //FedoraClient.addRelationship(subjectPid).predicate(newPredicateUri).object("info:fedora/" + newObjectPid).execute(dest);
    }
    
    public static void copyObject(String pid, FedoraClient source, FedoraClient dest, boolean overwrite) throws IOException, FedoraClientException {
        if (!(FedoraClient.findObjects().pid().query("pid=" + pid).execute(dest).getPids().size() == 0 || overwrite)) {
            System.out.println("Skipping " + pid + " because it already exists.");
        } else {
            System.out.print("Copying " + pid + "...");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtils.copy(FedoraClient.export(pid).context("archive").execute(source).getEntityInputStream(), baos);
            FedoraClient.ingest().content(baos.toString("UTF-8")).execute(dest);
            
            System.out.println("DONE");
        }
    }
    
}
