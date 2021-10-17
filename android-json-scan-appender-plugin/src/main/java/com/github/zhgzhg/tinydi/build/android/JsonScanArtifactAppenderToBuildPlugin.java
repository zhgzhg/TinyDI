package com.github.zhgzhg.tinydi.build.android;

import com.android.build.gradle.tasks.MergeSourceSetFolders;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.Objects;

/**
 * A Gradle helper plugin working with along with the Android Gradle Plugin to produce a static scan JSON asset during compile time.
 */
public class JsonScanArtifactAppenderToBuildPlugin implements Plugin<Project> {

    /**
     * Android JSON Scan Appender's Plugin configuration holder.
     */
    public static abstract class JsonScanArtifactAppenderToBuildPluginExtension {
        /**
         * Returns the current scanArgs option.
         * @return String array of scanArgs to be used.
         */
        public abstract Property<String[]> getScanArgs();

        /**
         * Returns the current buildTargers option.
         * @return String array of buildTargets to be used.
         */
        public abstract Property<String[]> getBuildTargets();

        /**
         * Returns the current flag for the cleanProducedAssets option.
         * @return Boolean value indicating whether the produced assets shall be cleaned. Usually that's preferred by default.
         */
        public abstract Property<Boolean> getCleanProducedAssets();

        /**
         * Executes the setter of the scan args config String array customizing the static classpath scan process.
         * @param action The actuall setter that will be executed.
         */
        public void scanArgs(Action<Property<String[]>> action) {
            action.execute(getScanArgs());
        }

        /**
         * Executes the setter of the build targets for which a scan will be run. Typically Debug, and Release.
         * @param action The actuall setter that will be executed.
         */
        public void buildTargets(Action<Property<String[]>> action) {
            action.execute(getBuildTargets());
        }

        /**
         * Executes the setter of the flag indicating whether to automatically clean the produced assets when the compilation process is finished.
         * @param action The actuall setter that will be executed.
         */
        public void cleanProducedAssets(Action<Property<Boolean>> action) {
            action.execute(getCleanProducedAssets());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getScanArgs().getOrNull(), this.getBuildTargets().getOrNull(), getCleanProducedAssets().getOrNull());
        }
    }

    @Override
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
