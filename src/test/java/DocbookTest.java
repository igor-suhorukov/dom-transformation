import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.igorsuhorukov.dom.transform.DomTransformer;
import com.github.igorsuhorukov.dom.transform.converter.NopTypeConverter;
import com.github.igorsuhorukov.dom.transform.converter.TypeAutoDetect;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Map;

import static junit.framework.TestCase.assertNotNull;

/**
 */
public class DocbookTest {
    @Test
    public void testParseComplexDocument() throws Exception {
        Document document;
        try (InputStream stream = DocbookTest.class.getResourceAsStream("/docbook.xml")){
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        objectMapper.setDateFormat(df);
        Map<String, Object> transformedDoc = new DomTransformer(new NopTypeConverter()).
                                                    transform(document.getDocumentElement());
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(transformedDoc);
        assertNotNull(json);
    }

    @Test
    public void testBackTransform() throws Exception {
        Document document;
        try (InputStream stream = DocbookTest.class.getResourceAsStream("/docbook.xml")){
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        objectMapper.setDateFormat(df);
        DomTransformer domTransformer = new DomTransformer(new TypeAutoDetect());

        Map<String, Object> transformedDoc = domTransformer.
                transform(document.getDocumentElement());

        Node transformedNode = domTransformer.transform(transformedDoc);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        DOMSource source = new DOMSource(transformedNode);
        StringWriter result = new StringWriter();
        transformer.transform(source, new StreamResult(result));
        assertNotNull(result.toString());

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(transformedDoc);
        assertNotNull(json);
    }

}
