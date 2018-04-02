package com.github.igorsuhorukov.dom.transform;

import com.github.igorsuhorukov.dom.transform.converter.AttributeDomToObject;
import com.github.igorsuhorukov.dom.transform.converter.AttributeObjectToDom;
import com.github.igorsuhorukov.dom.transform.converter.AttributeResolver;
import com.github.igorsuhorukov.dom.transform.converter.TypeConverter;
import org.apache.jackrabbit.util.ISO9075;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 */
public class DomTransformer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String TEXT_ELEMENT = "#text";
    private static final String COMMENT = "#comment";
    private static final String CDATA_SECTION = "#cdata-section";

    private final TypeConverter typeConverter;
    private final AttributeDomToObject attributeDomToObject;
    private final AttributeResolver attributeResolver;
    private final AttributeObjectToDom attributeObjectToDom;
    private final String valueName;

    private ThreadLocal<DocumentBuilder> documentBuilder = ThreadLocal.withInitial(() -> {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new UnsupportedOperationException(e);
        }
    });

    public DomTransformer(TypeConverter typeConverter) {
        this(typeConverter, name -> "@"+ name, name -> name.startsWith("@"), name -> name.substring(1), "_val_");
    }

    public DomTransformer(TypeConverter typeConverter, AttributeDomToObject attributeDomToObject,
                          AttributeResolver attributeResolver, AttributeObjectToDom attributeObjectToDom,
                          String valueName) {
        this.typeConverter = typeConverter;
        this.attributeDomToObject = attributeDomToObject;
        this.attributeResolver = attributeResolver;
        this.attributeObjectToDom = attributeObjectToDom;
        this.valueName = valueName;
    }

    public Node transform(Map<String, Object> objectMap){
        Document xmlDoc = documentBuilder.get().newDocument();
        if(objectMap.size()!=1){
            throw new IllegalArgumentException("map size must be 1");
        }
        return transform(xmlDoc, objectMap);
    }

    private Node transform(Document xmlDoc, Map<String, Object> objectMap){
        if(objectMap.size()!=1){
            throw new IllegalArgumentException();
        }
        String objectName = objectMap.keySet().iterator().next();
        if(attributeResolver.isAttribute(objectName)){
            return createAttribute(xmlDoc, objectMap, objectName);
        } else {
            return createNode(xmlDoc, objectMap, objectName);
        }
    }

    private Node createNode(Document xmlDoc, Map<String, Object> objectMap, String objectName) {
        Node node = null;
        try {
            node = xmlDoc.createElement(ISO9075.encode(objectName));
        } catch (DOMException e) {
            throw new IllegalArgumentException(e);
        }
        Object value = objectMap.get(objectName);
        if(value!=null) {
            if (value instanceof List) {
                for(Object item : (List)value){
                    if(item instanceof Map){
                        Map<String, Object> element = (Map<String, Object>) item;
                        transformMap(xmlDoc, node, element);
                    } else {
                        node.appendChild(xmlDoc.createTextNode(item.toString()));
                    }
                }
            } else if (value instanceof Map) {
                transformMap(xmlDoc, node, (Map<String, Object>) value);
            } else {
                node.appendChild(xmlDoc.createTextNode(value.toString()));
            }
        }
        return node;
    }

    private Node createAttribute(Document xmlDoc, Map<String, Object> objectMap, String objectName) {
        Attr attribute = xmlDoc.createAttribute(ISO9075.encode(attributeObjectToDom.getName(objectName)));
        Object value = objectMap.get(objectName);
        if(value instanceof Map || value instanceof Collection) {
            throw new IllegalArgumentException("invalid attribute "+objectName+" content: "+value.toString());
        }
        attribute.setValue(value.toString());
        return attribute;
    }

    private void transformMap(Document xmlDoc, Node node, Map<String, Object> value) {
        Map<String, Object> values = value;
        for(Map.Entry<String, Object> entry: values.entrySet()){
            if(entry.getValue() instanceof Collection){
                transformJsonCollection(xmlDoc, node, entry);
            } else {
                Node transform = transform(xmlDoc, Collections.singletonMap(entry.getKey(), entry.getValue()));
                if (valueName.equals(entry.getKey())) {
                    node.appendChild(xmlDoc.createTextNode(entry.getValue().toString()));
                } else if (attributeResolver.isAttribute(entry.getKey())) {
                    node.getAttributes().setNamedItem(transform);
                } else {
                    node.appendChild(transform);
                }
            }
        }
    }

    private void transformJsonCollection(Document xmlDoc, Node node, Map.Entry<String, Object> entry) {
        for(Object item: (Collection) entry.getValue()) {
            if(item!=null && item instanceof Map){
                node.appendChild(transform(xmlDoc, Collections.singletonMap(entry.getKey(),item)));
            } else if(item!=null){
                Element element = xmlDoc.createElement(ISO9075.encode(entry.getKey()));
                element.appendChild(xmlDoc.createTextNode(item.toString()));
                node.appendChild(element);
            }
        }
    }

    public Map<String, Object> transform(Node currentNode){
        String nodeName = currentNode.getNodeName();

        if(TEXT_ELEMENT.equals(nodeName) || CDATA_SECTION.equals(nodeName)){
            return Collections.singletonMap(nodeName, typeConverter.transform(currentNode.getNodeValue()));
        }

        Map<String, Object> nodesResultSet = new LinkedHashMap<>();

        processAttributes(currentNode, nodesResultSet);
        processTextContent(nodesResultSet, currentNode.getChildNodes());
        processNestedElements(currentNode, nodesResultSet);

        if(nodesResultSet.size()==1 && nodesResultSet.containsKey(valueName)){
            return Collections.singletonMap(ISO9075.decode(nodeName), nodesResultSet.values().iterator().next());
        }
        if(nodesResultSet.size()==0){
            return Collections.singletonMap(ISO9075.decode(nodeName), null);
        }
        return Collections.singletonMap(ISO9075.decode(nodeName), nodesResultSet);
    }

    private void processNestedElements(Node currentNode, Map<String, Object> resultNodes) {
        List<Map<String, Object>> nestedElements = getNestedElement(currentNode.getChildNodes());
        if(!nestedElements.isEmpty()){
            if(allNodeSimple(nestedElements) && allNodeNamesAreUnique(nestedElements)){
                resultNodes.putAll(getNestedElementsAsMap(nestedElements));
            } else {
                Map<String, Long> keyCountFreq = getKeyCountFreq(nestedElements);
                Set<String> uniqueKeys = getUniqueKeys(keyCountFreq);
                keyCountFreq.entrySet().stream().filter(entry-> entry.getValue()>1).map(Map.Entry::getKey).
                        forEach(fieldName -> resultNodes.put(ISO9075.decode(fieldName),
                                nestedElements.stream().map(Map::entrySet).
                                        flatMap(Collection::stream).filter(entry -> fieldName.equals(entry.getKey())).
                                        map(Map.Entry::getValue).collect(Collectors.toList())));
                extractUniqueNameElements(resultNodes, nestedElements, uniqueKeys);
            }
        }
    }

    private void processAttributes(Node currentNode, Map<String, Object> resultNodes) {
        NamedNodeMap attributes = currentNode.getAttributes();
        if(attributes!=null && attributes.getLength()>0){
            resultNodes.putAll(transformAttributes(attributes));
        }
    }

    private void processTextContent(Map<String, Object> resultNodes, NodeList nestedNodes) {
        String textContent = extractElementInternalContent(nestedNodes);
        if(textContent!=null && !textContent.isEmpty() && !WHITESPACE.matcher(textContent).matches()){
            resultNodes.put(valueName, typeConverter.transform(textContent.trim()));
        }
    }

    private Map<String, Object> getNestedElementsAsMap(List<Map<String, Object>> nestedNodes) {
        Map<String, Object> resultNodeMap = new LinkedHashMap<>();
        nestedNodes.stream().map(Map::entrySet).flatMap(Collection::stream).forEach(answer -> resultNodeMap.put(ISO9075.decode(answer.getKey()),
                typeConverter.transform(answer.getValue())));
        return resultNodeMap;
    }

    private Set<String> getUniqueKeys(Map<String, Long> keyCount) {
        return keyCount.entrySet().stream().filter(entry -> entry.getValue() == 1L).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private Map<String, Long> getKeyCountFreq(List<Map<String, Object>> nestedNodes) {
        return nestedNodes.stream().map(Map::keySet).flatMap(Collection::stream).collect(
                Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private void extractUniqueNameElements(Map<String, Object> resultNodes, List<Map<String, Object>> nestedNodes, Set<String> uniqueKeys) {
        nestedNodes.stream().map(Map::entrySet).flatMap(Collection::stream).
                filter(entry -> uniqueKeys.contains(entry.getKey())).
                forEach(entry -> resultNodes.put(ISO9075.decode(entry.getKey()), entry.getValue()));
    }

    private List<Map<String, Object>> getNestedElement(NodeList nestedNodes) {
        return IntStream.range(0, nestedNodes.getLength()).mapToObj(nestedNodes::item).
                filter(node -> !TEXT_ELEMENT.equals(node.getNodeName()) &&
                        !COMMENT.equals(node.getNodeName())&& !CDATA_SECTION.equals(node.getNodeName())).
                map(this::transform).collect(Collectors.toList());
    }

    private String extractElementInternalContent(NodeList nestedNodes) {
        return IntStream.range(0, nestedNodes.getLength()).mapToObj(nestedNodes::item).
                filter(node -> TEXT_ELEMENT.equals(node.getNodeName()) || CDATA_SECTION.equals(node.getNodeName())).
                map(Node::getNodeValue).collect(Collectors.joining());
    }

    private Map<String, Object> transformAttributes(NamedNodeMap attributes) {
        return IntStream.range(0, attributes.getLength()).mapToObj(attributes::item).
                map(node -> new AbstractMap.SimpleImmutableEntry<>(ISO9075.decode(attributeDomToObject.getName(node.getNodeName())),
                        typeConverter.transform(node.getNodeValue()))).
                collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private boolean allNodeNamesAreUnique(List<Map<String, Object>> nestedNodes) {
        return nestedNodes.stream().map(Map::keySet).flatMap(Collection::stream).distinct().count() == nestedNodes.size();
    }

    private boolean allNodeSimple(List<Map<String, Object>> nestedNodes) {
        return nestedNodes.stream().map(Map::size).allMatch(item->item==1);
    }
}
