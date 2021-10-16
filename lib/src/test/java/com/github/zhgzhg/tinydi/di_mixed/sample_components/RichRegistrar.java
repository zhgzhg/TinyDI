package com.github.zhgzhg.tinydi.di_mixed.sample_components;

import com.github.zhgzhg.tinydi.components.Environment;
import com.github.zhgzhg.tinydi.meta.annotations.KnownAs;
import com.github.zhgzhg.tinydi.meta.annotations.Recorded;
import com.github.zhgzhg.tinydi.meta.annotations.Registrar;
import com.github.zhgzhg.tinydi.meta.annotations.Supervised;
import com.github.zhgzhg.tinydi.meta.enums.ScopeDI;
import com.github.zhgzhg.tinydi.TinyDI;
import com.github.zhgzhg.tinydi.di_mixed.sample_components.distant.SimpleRecordProvider;
import lombok.Getter;

@Registrar
class OverridingSimpleRecordProvider extends SimpleRecordProvider {

    @Recorded
    @Override
    public int number() {
        return 44;
    }
}

@Registrar
public class RichRegistrar {

    public RichRegistrar(@KnownAs("SimpleRecordProvider") SimpleRecordProvider a) {
        a = a;
    }

    @Supervised
    class DummySingletonSupervised {
        public DummySingletonSupervised() {
            int i = 7;
            i = --i;
        }
    }

    @Supervised(scope = ScopeDI.PROTOTYPE)
    class SupervisedPrototype {
        public SupervisedPrototype(DummySingletonSupervised dummySingletonSupervised,
                OverridingSimpleRecordProvider overridenSimpleRecordProvider, TinyDI tinyDI, int number) {
            number = number;
        }
    }

    @Supervised(scope = ScopeDI.PROTOTYPE)
    class AnotherSupervisedPrototype {
        public AnotherSupervisedPrototype(SupervisedPrototype supervisedPrototype) {
            supervisedPrototype = supervisedPrototype;
        }
    }

    public interface IInterfaced { }

    @Getter
    public static class A implements IInterfaced {
        private final int number;
        public A(int number) {
            this.number = number;
        }
    }

    @Supervised
    public static class B {
        public B(IInterfaced aa) {
            aa = aa;
        }
    }

    @Supervised
    public static class C {
        public C(A aa) {
            aa = aa;
        }
    }

    @Supervised
    public static class Entrypoint {
        public Entrypoint(Environment env) {
            env = env;
        }
    }
}
