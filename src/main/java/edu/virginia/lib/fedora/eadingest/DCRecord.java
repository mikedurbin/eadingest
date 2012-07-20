package edu.virginia.lib.fedora.eadingest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * A simple class to encapsulate a DC record.  Serialization and
 * parsing routines exist as well as a single method to update the
 * dc identifier.  Other methods may be easily added as needed.
 */
public class DCRecord {

    public static final String DC_NS = "http://purl.org/dc/elements/1.1/";
    
    private XPath xpath;
    
    private Document doc;
    
    public DCRecord(InputStream is) throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        doc = factory.newDocumentBuilder().parse(is);
        is.close();
    }
    
    public DCRecord(Document doc) {
        this.doc = doc;
    }
    
    public XPath getXPath() {
        if (xpath == null) {
            xpath = new net.sf.saxon.xpath.XPathFactoryImpl().newXPath();
            xpath.setNamespaceContext(new NamespaceContext() {

                public String getNamespaceURI(String prefix) {
                    if (prefix.equals("dc")) {
                        return DC_NS;
                    } else if (prefix.equals("oai_dc")) {
                        return "http://www.openarchives.org/OAI/2.0/oai_dc/";
                    } else {
                        return null;
                    }
                }

                public String getPrefix(String namespaceURI) {
                    if (namespaceURI.equals(DC_NS)) {
                        return "dc";
                    } else if (namespaceURI.equals("http://www.openarchives.org/OAI/2.0/oai_dc/")) {
                        return "oai_dc";
                    } else {
                        return null;
                    }
                }

                public Iterator getPrefixes(String namespaceURI) {
                    if (namespaceURI.equals(DC_NS)) {
                        return Collections.singletonList("dc").iterator();
                    } else if (namespaceURI.equals("http://www.openarchives.org/OAI/2.0/oai_dc/")) {
                        return Collections.singletonList("oai_dc").iterator();
                    } else {
                        return null;
                    }
                }});
        }
        return xpath;
    }
    
    /**
     * Updates the DC record encapsulated by this object to include the given 
     * value for a dc:identifier field.  If the exact value is already present
     * this method does not add an additional dc:identifier field.  This method
     * updates the underlying object *and* returns a reference of that object
     * (for convenience).
     * @param id the dc:identifier to add
     * @return this object
     */
    public DCRecord addIdentifier(String id) throws XPathExpressionException {
        // check for existing identifier
        NodeList idNl = (NodeList) getXPath().evaluate("oai_dc:dc/dc:identifier", doc, XPathConstants.NODESET);
        for (int i = 0; i < idNl.getLength(); i ++) {
            if (id.equals(getXPath().evaluate("text()", idNl.item(i), XPathConstants.STRING))) {
                return this;
            }
        }
        
        // add identifier
        Element oaiDcEl = (Element) getXPath().evaluate("oai_dc:dc", doc, XPathConstants.NODE);
        Element idEl = doc.createElementNS(DC_NS, "dc:identifier");
        idEl.appendChild(doc.createTextNode(id));
        oaiDcEl.appendChild(idEl);
        
        return this;
    }
    
    public Document getDocument() {
        return doc;
    }
    
    public void writeOut(File file) throws TransformerException, IOException {
        DOMSource source = new DOMSource(doc);
        StreamResult sResult = new StreamResult(new FileOutputStream(file));
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer t = tFactory.newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        t.setOutputProperty(OutputKeys.METHOD, "xml");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        t.transform(source, sResult);
        sResult.getOutputStream().close();
    }
    
}
