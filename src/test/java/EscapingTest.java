import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.igorsuhorukov.dom.transform.DomTransformer;
import com.github.igorsuhorukov.dom.transform.converter.NopTypeConverter;
import com.github.igorsuhorukov.dom.transform.converter.TypeAutoDetect;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class EscapingTest {

    @Test
    public void testEscapeXmlNames() throws Exception {
        Map<String, Object> jsonTree = new ObjectMapper().readValue("{\"1name\":2, \"@2attr\":true}", new TypeReference<Map<String, Object>>() {});
        DomTransformer domTransformer = new DomTransformer(new NopTypeConverter());
        Node transformedNode = domTransformer.transform(Collections.singletonMap("wrapper", jsonTree));
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><wrapper _x0032_attr=\"true\"><_x0031_name>2</_x0031_name></wrapper>", toXmlString(transformedNode));
    }

    @Test
    public void testEscapeXmlNamesToJson() throws Exception {
        String srcXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><wrapper _x0032_attr=\"true\"><_x0031_name>2</_x0031_name></wrapper>";
        Document xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(srcXml.getBytes()));

        DomTransformer domTransformer = new DomTransformer(new TypeAutoDetect());
        Map<String, Object> json = domTransformer.transform(xml.getDocumentElement());
        String jsonString = new ObjectMapper().writeValueAsString(json);
        assertEquals("{\"wrapper\":{\"@2attr\":true,\"1name\":2}}", jsonString);
    }

    private String toXmlString(Node transformedNode) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        DOMSource source = new DOMSource(transformedNode);
        StringWriter xmlWriter = new StringWriter();
        transformer.transform(source, new StreamResult(xmlWriter));
        return xmlWriter.toString();
    }
}
