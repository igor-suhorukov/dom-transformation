package com.github.igorsuhorukov.dom.transform;

import com.github.igorsuhorukov.dom.transform.converter.TypeConverter;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 */
public class DomTransformer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String VALUE_NAME = "_val_";
    private static final String TEXT_ELEMENT = "#text";
    private static final String CDATA_SECTION = "#cdata-section";

    private TypeConverter typeConverter;

    public DomTransformer(TypeConverter typeConverter) {
        this.typeConverter = typeConverter;
    }

    public Map<String, Object> transform(Node currentNode){
        String nodeName = currentNode.getNodeName();

        if(TEXT_ELEMENT.equals(nodeName) || CDATA_SECTION.equals(nodeName)){
            return Collections.singletonMap(nodeName, typeConverter.transform(currentNode.getNodeValue()));
        }

        Map<String, Object> nodesResultSet = new TreeMap<>();

        processAttributes(currentNode, nodesResultSet);
        processTextContent(nodesResultSet, currentNode.getChildNodes());
        processNestedElements(currentNode, nodesResultSet);

        if(nodesResultSet.size()==1 && nodesResultSet.containsKey(VALUE_NAME)){
            return Collections.singletonMap(nodeName, nodesResultSet.values().iterator().next());
        }
        if(nodesResultSet.size()==0){
            return Collections.singletonMap(nodeName, null);
        }
        return Collections.singletonMap(nodeName, nodesResultSet);
    }

    private void processNestedElements(Node currentNode, Map<String, Object> resultNodes) {
        List<Map<String, Object>> nestedElements = getNestedElement(currentNode.getChildNodes());
        if(!nestedElements.isEmpty()){
            if(allNodeSimple(nestedElements) && allNodeNamesAreUnique(nestedElements)){
                resultNodes.putAll(getNestedElementsAsMap(nestedElements));
            } else {
                Map<String, Long> keyCountFreq = getKeyCountFreq(nestedElements);
                Set<String> uniqueKeys = getUniqueKeys(keyCountFreq);
                keyCountFreq.entrySet().stream().filter(entry-> entry.getValue()>1).map(Map.Entry::getKey).forEach(fieldName ->{
                    resultNodes.put(fieldName, nestedElements.stream().map(Map::entrySet).flatMap(Collection::stream).filter(entry -> fieldName.equals(entry.getKey())).map(Map.Entry::getValue).collect(Collectors.toList()));
                });
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
            resultNodes.put(VALUE_NAME, typeConverter.transform(textContent.trim()));
        }
    }

    private Map<String, Object> getNestedElementsAsMap(List<Map<String, Object>> nestedNodes) {
        Map<String, Object> resultNodeMap = new TreeMap<>();
        nestedNodes.stream().map(Map::entrySet).flatMap(Collection::stream).forEach(answer -> resultNodeMap.put(answer.getKey(),
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
                forEach(entry -> resultNodes.put(entry.getKey(), entry.getValue()));
    }

    private List<Map<String, Object>> getNestedElement(NodeList nestedNodes) {
        return IntStream.range(0, nestedNodes.getLength()).mapToObj(nestedNodes::item).
                filter(node -> !TEXT_ELEMENT.equals(node.getNodeName()) && !CDATA_SECTION.equals(node.getNodeName())).
                map(this::transform).collect(Collectors.toList());
    }

    private String extractElementInternalContent(NodeList nestedNodes) {
        return IntStream.range(0, nestedNodes.getLength()).mapToObj(nestedNodes::item).
                filter(node -> TEXT_ELEMENT.equals(node.getNodeName()) || CDATA_SECTION.equals(node.getNodeName())).
                map(Node::getNodeValue).collect(Collectors.joining());
    }

    private Map<String, Object> transformAttributes(NamedNodeMap attributes) {
        return IntStream.range(0, attributes.getLength()).mapToObj(attributes::item).
                map(node -> new AbstractMap.SimpleImmutableEntry<>(getAttributeName(node),
                        typeConverter.transform(node.getNodeValue()))).
                collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private boolean allNodeNamesAreUnique(List<Map<String, Object>> nestedNodes) {
        return nestedNodes.stream().map(Map::keySet).flatMap(Collection::stream).distinct().count() == nestedNodes.size();
    }

    private boolean allNodeSimple(List<Map<String, Object>> nestedNodes) {
        return nestedNodes.stream().map(Map::size).allMatch(item->item==1);
    }

    private String getAttributeName(Node node) {
        return "@"+node.getNodeName();
    }
}
