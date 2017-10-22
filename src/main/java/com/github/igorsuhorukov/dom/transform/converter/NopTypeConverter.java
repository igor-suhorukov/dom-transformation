package com.github.igorsuhorukov.dom.transform.converter;

public class NopTypeConverter implements TypeConverter {
    @Override
    public Object transform(Object srcData) {
        return srcData;
    }
}
