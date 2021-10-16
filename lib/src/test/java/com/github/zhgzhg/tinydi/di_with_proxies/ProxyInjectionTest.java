package com.github.zhgzhg.tinydi.di_with_proxies;

import com.github.zhgzhg.tinydi.TinyDI;
import com.github.zhgzhg.tinydi.dynamic.RecordedAnnotation;
import com.github.zhgzhg.tinydi.dynamic.TinyDynamicDI;
import com.github.zhgzhg.tinydi.meta.annotations.Recorded;
import com.github.zhgzhg.tinydi.meta.annotations.Registrar;
import com.github.zhgzhg.tinydi.meta.annotations.Supervised;
import com.github.zhgzhg.tinydi.meta.enums.ScopeDI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyInjectionTest {

    public static class A {
        private final int a;
        public A(int a) {
            this.a = a;
        }
    }

    @Registrar
    public static class Px {
        @Recorded
        int a() {
            return 1;
        }
    }

    public interface ICommon { }

    @Supervised
    public static class Rcv implements ICommon {
        public Rcv(A param) {
            param = param;
        }
    }

    @Supervised
    public static class Rcv2 implements ICommon {
        public Rcv2(A param) {
            param = param;
        }
    }

    @Test
    void proxyInjection() {

        AtomicInteger aaa = new AtomicInteger(0);

        Recorded separateInstProxy = TinyDynamicDI.attachRecordedAnnotation(
                () -> new A(555 + aaa.getAndIncrement()),
                A.class,
                new RecordedAnnotation("someName", ScopeDI.PROTOTYPE)
        );

        TinyDI tinyDI = TinyDI.config()
                .records(separateInstProxy)
                .basePackages(this.getClass().getPackageName())
                .configure();

        assertDoesNotThrow(() -> tinyDI.run());

        // ================================================================================================================

        Set<String> componentNames = tinyDI.registeredComponentNames();
        assertEquals(2 /*tinnydi's*/ + 5, componentNames.size());

        assertEquals(Integer.class, tinyDI.registeredComponentClass("a"));
        assertEquals("a", tinyDI.registeredComponentName(Integer.class));
        assertEquals("a", tinyDI.registeredComponentName(int.class));

        assertEquals(A.class, tinyDI.registeredComponentClass("someName"));
        assertEquals("someName", tinyDI.registeredComponentName(A.class));

        assertEquals("Rcv", tinyDI.registeredComponentName(ICommon.class));
        assertEquals(Rcv.class, tinyDI.componentFor(ICommon.class).getClass());
        assertEquals(Rcv.class, tinyDI.componentFor("Rcv").getClass());

        assertEquals(1, tinyDI.componentFor("a"));
        assertEquals(1, tinyDI.componentFor(Integer.class));
        assertEquals(1, tinyDI.componentFor(int.class));

        assertNotNull(tinyDI.componentFor(A.class));
        assertNotNull(tinyDI.componentFor("someName"));
        assertNotEquals(tinyDI.componentFor("someName"), tinyDI.componentFor("someName"));

        assertNotNull(tinyDI.componentFor("Rcv"));
        assertEquals(tinyDI.componentFor("Rcv"), tinyDI.componentFor("Rcv"));

        assertNotEquals(tinyDI.componentFor("Rcv"), tinyDI.componentFor("Rcv2"));

        assertEquals(ScopeDI.PROTOTYPE, tinyDI.instantiationStrategy(separateInstProxy));
        assertNull(tinyDI.instantiationStrategy(A.class));
        assertEquals(ScopeDI.SINGLETON, tinyDI.instantiationStrategy(Rcv.class));
    }
}
