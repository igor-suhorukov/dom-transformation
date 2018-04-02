package com.github.igorsuhorukov.dom.transform.converter;

@FunctionalInterface
public interface AttributeResolver {
    boolean isAttribute(String name);
}
