package edu.virginia.lib.fedora.eadingest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraClientException;
import com.yourmediashelf.fedora.client.FedoraCredentials;

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
    
    @BeforeClass 
    public static void setUpFedoraClient() throws IOException {
        Properties p = new Properties();
        p.load(RepositoryIntegrationTest.class.getClassLoader().getResourceAsStream("config/fedora-integration-test.properties"));
        fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));
    }
    
    /**
     * Ingests each content model (and service definitions and service deployment) object
     * to ensure that they FOXML is well-formed and ingestable and to set up the repository
     * for other integration tests.
     */
    @Test public synchronized void ingestContentModels() throws Exception {
        if (pidsToPurge == null) {
            pidsToPurge = new ArrayList<String>();
            pidsToPurge.addAll(EADIngest.ingestSupportObjects(fc));
        }
    }
    
    @Test public void testDocumentedMapping() throws Exception { 
        ingestContentModels();
        if (EADIngest.doesObjectExist(fc, "sdep:indexable-ead-fragment") && FedoraClient.getDissemination("sdep:indexable-ead-fragment", "sdef:documented-mapping", "getMappingDocumentation").execute(fc).getStatus() / 100 != 2) {
            throw new RuntimeException("getDocumentedMapping does not work for sdep:indexable-ead-fragment!");
        } else {
            System.out.println("sdep:indexable-ead-fragment is properly documented and the disseminators work.");
        }
    }
    
    @Test public void ingestFindingAids() throws Exception {
        ingestContentModels();
        
        Properties p = new Properties();
        p.load(EADIngest.class.getClassLoader().getResourceAsStream("config/fedora-integration-test.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        p = new Properties();
        p.load(AnalyzeContainerInformation.class.getClassLoader().getResourceAsStream("config/ontology.properties"));
        EADOntology o = new EADOntology(p);
        
        List<EADIngest> newlyIngestedEad = new ArrayList<EADIngest>();
        for (EADIngest i : EADIngest.getRecognizedEADIngests(o)) {
            if (!i.exists(fc)) {
                i.buildFedoraObjects(fc);
                newlyIngestedEad.add(i);
            }
        }
        
        // TODO: test indexing at each level
        
        for (EADIngest i : newlyIngestedEad) {
            i.purgeFedoraObjects(fc);
        }
    }
    
    @AfterClass
    public static void cleanUpObjects() throws IOException, NumberFormatException, FedoraClientException {
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
    }
    
}
