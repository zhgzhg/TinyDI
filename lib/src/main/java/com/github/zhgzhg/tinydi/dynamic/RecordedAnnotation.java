package com.github.zhgzhg.tinydi.dynamic;

import com.github.zhgzhg.tinydi.meta.annotations.Recorded;
import com.github.zhgzhg.tinydi.meta.enums.ScopeDI;
import lombok.AllArgsConstructor;

import java.lang.annotation.Annotation;

/**
 * Dynamic variant of the {@link Recorded} annotation.
 * It allows for late association of {@link Recorded} attributes to a particular object which has not been annotated in the code.
 */
@AllArgsConstructor
public class RecordedAnnotation implements Recorded {

    private final String value;
    private final ScopeDI scope;

    /**
     * Default constructor of the pseudo @Recorded annotation instance, setting the component name to empty string,
     * and its scope to singeton.
     */
    public RecordedAnnotation() {
        this.value = "";
        this.scope = ScopeDI.SINGLETON;
    }

    @Override
    public String value() {
        return this.value;
    }

    @Override
    public ScopeDI scope() {
        return this.scope;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Recorded.class;
    }
}
