package com.github.zhgzhg.tinydi.build.android;

import com.android.build.gradle.tasks.MergeSourceSetFolders;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.Objects;

public class JsonScanArtifactAppenderToBuildPlugin implements Plugin<Project> {

    public static abstract class JsonScanArtifactAppenderToBuildPluginExtension {
        public abstract Property<String[]> getScanArgs();
        public abstract Property<String[]> getBuildTargets();
        public abstract Property<Boolean> getCleanProducedAssets();

        public void scanArgs(Action<Property<String[]>> action) {
            action.execute(getScanArgs());
        }

        public void buildTargets(Action<Property<String[]>> action) {
            action.execute(getBuildTargets());
        }

        public void cleanProducedAssets(Action<Property<Boolean>> action) {
            action.execute(getCleanProducedAssets());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getScanArgs().getOrNull(), this.getBuildTargets().getOrNull(), getCleanProducedAssets().getOrNull());
        }
    }

    public void apply(Project project) {
        JsonScanArtifactAppenderToBuildPluginExtension options = project.getExtensions()
                .create("tinidiStaticJsonScanForAndroid", JsonScanArtifactAppenderToBuildPluginExtension.class);

        project.getPluginManager().withPlugin("com.android.application", appliedPlugin -> {

            project.afterEvaluate(evaluatedProject -> {

                String[] buildTargets;
                if (options.getBuildTargets().isPresent()) {
                    buildTargets = options.getBuildTargets().get();
                } else {
                    buildTargets = new String[] { "Debug", "Release" };
                }

                int optionsHashCode = options.hashCode();

                for (String bt : buildTargets) {
                    JavaCompile compileJavaWithJavac = (JavaCompile) evaluatedProject.getTasks().getByName(String.format("compile%sJavaWithJavac", bt));
                    MergeSourceSetFolders mergeAssets = (MergeSourceSetFolders) evaluatedProject.getTasks().getByName(String.format("merge%sAssets", bt));
                    Task assemble = evaluatedProject.getTasks().getByName("assemble" + bt);

                    compileJavaWithJavac.getInputs().property("tinydiCfgHash", optionsHashCode);
                    mergeAssets.getInputs().property("tinydiCfgHash", optionsHashCode);
                    assemble.getInputs().property("tinydiCfgHash", optionsHashCode);

                    Boolean cleanProducedAssets = options.getCleanProducedAssets().getOrElse(Boolean.TRUE);

                    compileJavaWithJavac.doLast("Generate TinyDI's Static JSON Scan Temporary Asset - " + bt,
                            new WriteTemporaryScanAsset(mergeAssets, options.getScanArgs().getOrNull(), cleanProducedAssets, assemble));

                }
            });
        });
    }
}
