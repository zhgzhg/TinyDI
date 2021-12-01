package com.github.zhgzhg.tinydi.build.android;

import com.android.build.gradle.tasks.MergeSourceSetFolders;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.Objects;

/**
 * A Gradle helper plugin working with along with the Android Gradle Plugin to produce a static scan JSON asset during compile time.
 */
public class JsonScanArtifactAppenderToBuildPlugin implements Plugin<Project> {

    static final String TINYDI_CFG_HASH = "tinydiCfgHash";

    /**
     * Android JSON Scan Appender Plugin configuration holder.
     */
    public abstract static class JsonScanArtifactAppenderToBuildPluginExtension {
        /**
         * Returns the current scanArgs option.
         * @return String array of scanArgs to be used.
         */
        public abstract ListProperty<String> getScanArgs();

        /**
         * Returns the current buildTargets option.
         * @return String array of buildTargets to be used.
         */
        public abstract Property<String[]> getBuildTargets();

        /**
         * Returns the current flag for the cleanProducedAssets option.
         * @return Boolean value indicating whether the produced assets shall be cleaned. Usually that's preferred by default.
         */
        public abstract Property<Boolean> getCleanProducedAssets();

        /**
         * Returns the current flag for the removeClassPathInfo option.
         * @return Boolean value indicating whether the class path info shall be cleaned from the serialized scan.
         *         Usually that's preferred by default.
         */
        public abstract Property<Boolean> getRemoveClassPathInfo();

        /**
         * Executes the setter of the scan args config String array customizing the static classpath scan process.
         * @param action The actual setter that will be executed.
         */
        public void scanArgs(Action<ListProperty<String>> action) {
            action.execute(getScanArgs());
        }

        /**
         * Executes the setter of the build targets for which a scan will be run. Typically Debug, and Release.
         * @param action The actual setter that will be executed.
         */
        public void buildTargets(Action<Property<String[]>> action) {
            action.execute(getBuildTargets());
        }

        /**
         * Executes the setter of the flag indicating whether to automatically clean the produced assets when the compilation process is finished.
         * @param action The actual setter that will be executed.
         */
        public void cleanProducedAssets(Action<Property<Boolean>> action) {
            action.execute(getCleanProducedAssets());
        }

        /**
         * Executes the setter of the flag indicating whether to remove the class path data from the serialized static DI scan result.
         * @param action The actual setter that will be executed.
         */
        public void removeClassPathInfo(Action<Property<Boolean>> action) {
            action.execute(getRemoveClassPathInfo());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getScanArgs().getOrNull(), this.getBuildTargets().getOrNull(), getCleanProducedAssets().getOrNull());
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) return true;
            if (obj instanceof JsonScanArtifactAppenderToBuildPluginExtension) {
                JsonScanArtifactAppenderToBuildPluginExtension that = (JsonScanArtifactAppenderToBuildPluginExtension) obj;
                return Objects.equals(this.getScanArgs().getOrNull(), that.getScanArgs().getOrNull())
                        && Objects.equals(this.getBuildTargets().getOrNull(), that.getBuildTargets().getOrNull())
                        && Objects.equals(this.getCleanProducedAssets().getOrNull(), that.getCleanProducedAssets().getOrNull())
                        && Objects.equals(this.getRemoveClassPathInfo().getOrNull(), that.getRemoveClassPathInfo().getOrNull());
            }
            return false;
        }
    }

    @Override
    public void apply(Project project) {
        JsonScanArtifactAppenderToBuildPluginExtension options = project.getExtensions()
                .create("tinidiStaticJsonScanForAndroid", JsonScanArtifactAppenderToBuildPluginExtension.class);

        project.getPluginManager().withPlugin("com.android.application", appliedPlugin ->

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

                    compileJavaWithJavac.getInputs().property(TINYDI_CFG_HASH, optionsHashCode);
                    mergeAssets.getInputs().property(TINYDI_CFG_HASH, optionsHashCode);
                    assemble.getInputs().property(TINYDI_CFG_HASH, optionsHashCode);

                    Boolean cleanProducedAssets = options.getCleanProducedAssets().getOrElse(Boolean.TRUE);
                    Boolean removeClassPathDataFromJSON = options.getRemoveClassPathInfo().getOrElse(Boolean.TRUE);

                    compileJavaWithJavac.doLast("Generate TinyDI's Static JSON Scan Temporary Asset - " + bt,
                            new WriteTemporaryScanAsset(mergeAssets, options.getScanArgs().getOrNull(), cleanProducedAssets,
                                    removeClassPathDataFromJSON, assemble)
                    );
                }
            })
        );
    }
}
