package com.github.zhgzhg.tinydi.di_entrypoint;

import com.github.zhgzhg.tinydi.TinyDI;
import com.github.zhgzhg.tinydi.components.EntryPoint;
import com.github.zhgzhg.tinydi.dynamic.RecordedAnnotation;
import com.github.zhgzhg.tinydi.dynamic.TinyDynamicDI;
import com.github.zhgzhg.tinydi.meta.annotations.KnownAs;
import com.github.zhgzhg.tinydi.meta.annotations.Recorded;
import com.github.zhgzhg.tinydi.meta.annotations.Registrar;
import com.github.zhgzhg.tinydi.meta.annotations.Supervised;
import com.github.zhgzhg.tinydi.meta.enums.ScopeDI;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

@Registrar
class Config {

    /** A singleton component called 'appVersion' which is a string */
    @Recorded
    String appVersion() { return "1.0.0"; }

    /** A prototype component called 'currentTime' which is an instance of java.util.Date */
    @Recorded(value = "currentTime", scope = ScopeDI.PROTOTYPE)
    Date currTime() { return new Date(); }
}

interface Informer {
    void printInfo();
}

@Supervised
class AppInfo implements Informer {
    private String version;
    private Date currDt;

    public AppInfo(String appVersion, @KnownAs("currentTime") Date ct) {
        this.version = appVersion;
        this.currDt = ct;
    }

    @Override
    public void printInfo() {
        System.err.printf("App (%s) - v%s%n", this.currDt, version);
    }
}

@Supervised
class EP implements EntryPoint {
    private AppInfo appInfo;
    private List<String> executionIndicatorList;

    public EP(AppInfo appInfo, List<String> executionIndicatorList) {
        this.appInfo = appInfo;
        this.executionIndicatorList = executionIndicatorList;
    }

    @Override
    public void run() {
        this.appInfo.printInfo();
        this.executionIndicatorList.clear();
    }
}

class EntryPointExecutionTest {

    @Test
    void epShallBeExecuted() {
        List<String> execIndic = new LinkedList<>();
        execIndic.add("entry_that_shall_be_removed");

        Recorded execIndicRecord = TinyDynamicDI.attachRecordedAnnotation(
                () -> execIndic,
                LinkedList.class,
                new RecordedAnnotation("execIndic", ScopeDI.SINGLETON)
        );

        TinyDI.config()
                .basePackages(EntryPointExecutionTest.class.getPackageName())
                .records(execIndicRecord)
                .configure()
                .run();

        Assertions.assertTrue(execIndic.isEmpty());
    }
}
