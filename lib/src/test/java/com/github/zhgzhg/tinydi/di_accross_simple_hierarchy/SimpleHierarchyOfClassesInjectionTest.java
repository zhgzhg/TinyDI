package com.github.zhgzhg.tinydi.di_accross_simple_hierarchy;

import com.github.zhgzhg.tinydi.TinyDI;
import com.github.zhgzhg.tinydi.meta.annotations.Recorded;
import com.github.zhgzhg.tinydi.meta.annotations.Registrar;
import com.github.zhgzhg.tinydi.meta.annotations.Supervised;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleHierarchyOfClassesInjectionTest {

    public static abstract class Base { }

    public static class Inheritor extends Base { }

    public static class BadBase { }

    public static class Inheritor2 extends BadBase { }

    @Registrar
    public static class Cfg {
        @Recorded
        Inheritor inh() {
            return new Inheritor();
        }

        @Recorded
        Inheritor2 inh2() {
            return new Inheritor2();
        }
    }

    @Supervised
    public static class Component {
        public Component(Base basis) {
            basis = basis;
        }
    }

    @Supervised
    public static class Component2 {
        public Component2(BadBase badBase) {
            throw new IllegalStateException("Not supposed to be called");
        }
    }

    @Test
    void hierarchyOfClassesInjectionTest() {
        TinyDI tinyDI = TinyDI.config()
                .basePackages(this.getClass().getPackageName())
                .ignoredClasses(Component2.class.getCanonicalName())
                .configure();

        assertDoesNotThrow(() -> tinyDI.run());
    }

    @Test
    void hierarchyOfClassesInjectionWithMissingComponentsShouldFail() {
        TinyDI tinyDI = TinyDI.config()
                .basePackages(this.getClass().getPackageName())
                .configure();

        assertThrows(Exception.class, () -> tinyDI.run());
    }

    @Test
    void staticScanningShouldSucceed() {
        String result = TinyDI.config()
                .basePackages(this.getClass().getPackageName())
                .configureForStaticScan();

        assertNotNull(result);
        assertTrue(result.length() > 10);
    }
}
