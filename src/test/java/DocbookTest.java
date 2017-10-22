import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.igorsuhorukov.dom.transform.DomTransformer;
import com.github.igorsuhorukov.dom.transform.converter.NopTypeConverter;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
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
}
