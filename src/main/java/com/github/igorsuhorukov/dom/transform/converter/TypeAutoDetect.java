package com.github.igorsuhorukov.dom.transform.converter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;
import java.util.regex.Pattern;

public class TypeAutoDetect implements TypeConverter{

    private static final Pattern INTEGER_NUMBER = Pattern.compile("[+-]?\\d+");
    private static final Pattern BOOLEAN = Pattern.compile("true|TRUE|false|FALSE");
    private static final Pattern REAL_NUMBER = Pattern.compile("[+-]?(\\d*[.])?\\d+");
    private static final Collection<ContentMatcher> MATCHERS= Arrays.asList(
            new ContentMatcher(BOOLEAN, Boolean::parseBoolean),
            new ContentMatcher(INTEGER_NUMBER, BigInteger::new),
            new ContentMatcher(REAL_NUMBER, BigDecimal::new));

    private static class ContentMatcher {
        private Pattern matcher;
        private Function<String, Object> function;

        private ContentMatcher(Pattern matcher, Function<String, Object> function) {
            this.matcher = matcher;
            this.function = function;
        }
    }

    public Object transform(Object srcData){
        if(srcData instanceof String){
            String data = (String) srcData;
            return MATCHERS.stream().filter(matcher1 -> matcher1.matcher.matcher(data).matches()).findFirst().
                    map(matcher -> matcher.function.apply(data)).orElse(data);
        } else {
            return srcData;
        }
    }
}
