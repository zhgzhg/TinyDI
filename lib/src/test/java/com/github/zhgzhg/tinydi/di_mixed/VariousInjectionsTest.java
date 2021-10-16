package com.github.zhgzhg.tinydi.di_mixed;

import com.github.zhgzhg.tinydi.TinyDI;
import com.github.zhgzhg.tinydi.meta.annotations.Recorded;
import com.github.zhgzhg.tinydi.meta.enums.ScopeDI;
import com.github.zhgzhg.tinydi.dynamic.RecordedAnnotation;
import com.github.zhgzhg.tinydi.dynamic.TinyDynamicDI;
import com.github.zhgzhg.tinydi.di_mixed.sample_components.RichRegistrar;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class VariousInjectionsTest {

    @Test
    void multipleDependentAndIndependentComponentsInstantiationTest() {

        AtomicInteger aaa = new AtomicInteger(0);

        Recorded separateInstProxy = TinyDynamicDI.attachRecordedAnnotation(
                () -> new RichRegistrar.A(555 + aaa.getAndIncrement()),
                RichRegistrar.A.class,
                new RecordedAnnotation("someName", ScopeDI.PROTOTYPE)
        );

        TinyDI tinyDI = TinyDI.config()
                .records(separateInstProxy)
                .basePackages(RichRegistrar.class.getPackageName())
                .configure();

        Assertions.assertDoesNotThrow(() -> tinyDI.run());
    }
}
