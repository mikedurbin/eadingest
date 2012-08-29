package edu.virginia.lib.fedora.eadingest;

import java.io.File;
import java.net.URL;
import java.util.Properties;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraCredentials;

import edu.virginia.lib.fedora.eadingest.container.AnalyzeContainerInformation;
import edu.virginia.lib.fedora.eadingest.pidmapping.SpecializedPidMapping;
import edu.virginia.lib.fedora.eadingest.pidmapping.PidMapping;

public class ReplaceCollection {

    /**
     * A program that purges all the objects associated
     * with an EAD and ingests new (updated) versions of 
     * that finding aid such that the collection and 
     * component objects have the same pid as the original.
     */
    public static void main(String [] args) throws Exception {

        Properties p = new Properties();
        p.load(EADIngest.class.getClassLoader().getResourceAsStream("config/fedora-test.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));
        
        p = new Properties();
        p.load(EADIngest.class.getClassLoader().getResourceAsStream("config/virgo.properties"));
        String catalogUrl = p.getProperty("catalog-url");
        
        p = new Properties();
        p.load(AnalyzeContainerInformation.class.getClassLoader().getResourceAsStream("config/old-ontology.properties"));
        EADOntology oldOntology = new EADOntology(p);
        
        p = new Properties();
        p.load(AnalyzeContainerInformation.class.getClassLoader().getResourceAsStream("config/ontology.properties"));
        EADOntology newOntology = new EADOntology(p);
        
        URL url = EADIngest.class.getClassLoader().getResource("ead/viu02465.xml");
        String[] catalogKeys = new String[] {"u2091463", "u2091469", "u3686913", "u1909107", "u2316160"}; 
        EADIngest original = new EADIngest(new File(url.toURI()), catalogKeys, catalogUrl, oldOntology, fc);
        PidMapping mapping = new SpecializedPidMapping(fc, oldOntology, "uva-lib-test:4556");
        //PidMapping mapping = new SpecializedPidMapping(new File(ReplaceCollection.class.getClassLoader().getResource("holsinger-pid-mapping.txt").toURI()));
        
        EADIngest updated = new EADIngest(new File(url.toURI()), catalogKeys, catalogUrl, newOntology, fc);
        updated.setPidMapping(mapping);
        updated.testPidMapping();
        original.purgeFedoraObjects();
        updated.buildFedoraObjects();
        
        
        //URL url = EADIngest.class.getClassLoader().getResource("ead/viu01215.xml");
        //EADIngest original = new EADIngest(new File(url.toURI()), new String[] { "u3523359" }, catalogUrl, oldOntology, fc);
        //EADIngest updated = new EADIngest(new File(url.toURI()), new String[] { "u3523359" }, catalogUrl, newOntology, fc);
        //updated.setPidMapping(new HashPidMapping(new File(ReplaceCollection.class.getClassLoader().getResource("pid-mapping.txt").toURI())));
        //updated.testPidMapping();
        
        //original.purgeFedoraObjects();
        
        //updated.buildFedoraObjects();

    }
}
