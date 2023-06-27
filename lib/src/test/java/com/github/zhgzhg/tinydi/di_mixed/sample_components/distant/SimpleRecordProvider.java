package com.github.zhgzhg.tinydi.di_mixed.sample_components.distant;

import com.github.zhgzhg.tinydi.meta.annotations.Recorded;
import com.github.zhgzhg.tinydi.meta.annotations.Registrar;

@Registrar
public class SimpleRecordProvider {

    @Recorded
    public int number() {
        int i = 5;
        i++;
        --i;
        return i;
    }

    @Recorded(value = "nullValuesAreNotRegisteredAndNotErroredImmediately")
    public Short missigShort() {
        return null;
    }
}