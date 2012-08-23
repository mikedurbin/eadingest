package edu.virginia.lib.fedora.eadingest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraClientException;
import com.yourmediashelf.fedora.client.FedoraCredentials;
import com.yourmediashelf.fedora.generated.access.MethodType;
import com.yourmediashelf.fedora.generated.access.ServiceDefinitionType;
import com.yourmediashelf.fedora.generated.management.Datastream;
import com.yourmediashelf.fedora.generated.management.Validation;

import edu.virginia.lib.fedora.eadingest.container.AnalyzeContainerInformation;

/**
 * A suite of tests that ingests a great deal of content into 
 * the fedora repository and tests the actual ingestability and
 * functionality of the objects.  These tests require that a
 * fedora repository be available and referenced from the 
 * fedora-integration-test.properties configuration file.
 */
@Category (edu.virginia.lib.fedora.eadingest.RepositoryIntegrationTest.class)
public class RepositoryIntegrationTest {

    private static FedoraClient fc;
    
    private static List<String> pidsToPurge;
    
    private static List<EADIngest> newlyIngestedEad;
    
    @BeforeClass 
    public static void setUpFedoraClient() throws IOException {
        Properties p = new Properties();
        p.load(RepositoryIntegrationTest.class.getClassLoader().getResourceAsStream("config/fedora-integration-test.properties"));
        fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));
    }
    
    /**
     * Ingests each content model (and service definitions and service deployment) object
     * to ensure that they FOXML is well-formed and ingestable and to set up the repository
     * for other integration tests.  This method also ingests all the recognized finding
     * aids.  This method may take hours to run.
     */
    @Test public synchronized void ingestTestObjects() throws Exception {
        if (pidsToPurge == null) {
            pidsToPurge = new ArrayList<String>();
            pidsToPurge.addAll(EADIngest.ingestSupportObjects(fc));
            ingestFindingAids();
        }
    }
    
    @Test public synchronized void validateSupportObjects() throws Exception {
        ingestTestObjects();
        StringBuffer errors = new StringBuffer();
        DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        try {
            for (String pid : pidsToPurge) {
                validateNonFedoraMethods(pid, fc, errors);
            }
        } finally {
            if (errors.length() > 0) {
                System.err.println(errors);
                throw new RuntimeException("One or more content model objects is invalid!");
            }
        }
    }
    
    @Test public synchronized void validateEADObjects() throws Exception {
        ingestTestObjects();
        StringBuffer errors = new StringBuffer();
        
        Properties p = new Properties();
        p.load(AnalyzeContainerInformation.class.getClassLoader().getResourceAsStream("config/ontology.properties"));
        EADOntology o = new EADOntology(p);
        
        try {
            List<String> collectionRootPids = EADIngest.getSubjects(fc, o.eadRootCModel(), EADIngest.HAS_MODEL_PREDICATE);
            for (String cpid : collectionRootPids) {
                validateComponentAndChildren(fc, cpid, "  ", o, errors);
                for (String marcPid : EADIngest.getObjects(fc,  cpid, o.hasMarc())) {
                    validateNonFedoraMethods(marcPid, fc, errors);
                    for (String containerPid : EADIngest.getObjects(fc,  marcPid, o.definesContainer())) {
                        validateNonFedoraMethods(containerPid, fc, errors);
                    }
                }
            }
            
        } finally {
            if (errors.length() > 0) {
                System.err.println(errors);
                throw new RuntimeException("One or more EAD objects is invalid!");
            }
        }
    }
    
    public static void validateComponentAndChildren(FedoraClient fc, String parentPid, String indent, EADOntology o, StringBuffer errors) throws Exception {
        validateNonFedoraMethods(parentPid, fc, errors);
        for (String partPid : EADIngest.getOrderedParts(fc, parentPid, o.isPartOf(), o.follows())) {
            System.out.println(indent + "component --> " + partPid);
            validateComponentAndChildren(fc, partPid, indent + "  ", o, errors);
        }
    }
    
    public static void validateNonFedoraMethods(String pid, FedoraClient fc, StringBuffer errors) throws FedoraClientException {
        for (ServiceDefinitionType service : FedoraClient.listMethods(pid).execute(fc).getObjectMethods().getObjectMethodsTypeAttribute()) {
            if (!service.getPid().startsWith("fedora-system")) {
                for (MethodType m : service.getMethod()) {
                    int status = FedoraClient.getDissemination(pid, service.getPid(), m.getName()).execute(fc).getStatus();
                    if (status / 100 != 2) {
                        errors.append(pid + "/methods/" + service.getPid() + "/" + m.getName() + " failed " + status + "\n");
                    } else {
                        System.out.println(pid + "/methods/" + service.getPid() + "/" + m.getName() + " passed " + status);
                    }
                }
            }
        }
    }
    
    @Test public void testMethods() throws Exception {
        ingestTestObjects();
        StringBuffer errors = new StringBuffer();
        for (String pid : pidsToPurge) {
            
        }
        if (errors.length() > 0) {
            System.err.println(errors);
            throw new RuntimeException("One or more content model objects is invalid!");
        }
    }
    
    private void ingestFindingAids() throws Exception {
        ingestTestObjects();
        
        Properties p = new Properties();
        p.load(EADIngest.class.getClassLoader().getResourceAsStream("config/fedora-integration-test.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        p = new Properties();
        p.load(AnalyzeContainerInformation.class.getClassLoader().getResourceAsStream("config/ontology.properties"));
        EADOntology o = new EADOntology(p);
        
        newlyIngestedEad = new ArrayList<EADIngest>();
        for (EADIngest i : EADIngest.getRecognizedEADIngests(o)) {
            if (!i.exists(fc)) {
                i.buildFedoraObjects(fc);
                newlyIngestedEad.add(i);
            }
        }
    }
    
    @AfterClass
    public static void cleanUpObjects() throws Exception {
        for (String pid : pidsToPurge) {
            if (EADIngest.doesObjectExist(fc, pid)) {
                try {
                    FedoraClient.purgeObject(pid).execute(fc);
                    System.out.println("Purged " + pid + ".");
                } catch (FedoraClientException ex) {
                    System.err.println("Unable to purge " + pid + "!");
                    ex.printStackTrace();
                    // bummer... fall through and keep deleting
                    // the rest
                }
            }
        }
        
        for (EADIngest i : newlyIngestedEad) {
            try {
                i.purgeFedoraObjects(fc);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
}
