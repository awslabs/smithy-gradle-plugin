/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.smithy.gradle;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import software.amazon.smithy.gradle.tasks.SmithyBuildJar;
import software.amazon.smithy.utils.ListUtils;

/**
 * Applies the Smithy plugin to Gradle.
 */
public final class SmithyPlugin implements Plugin<Project> {

    private static final String DEFAULT_CLI_VERSION = "0.9.5";
    private static final List<String> SOURCE_DIRS = ListUtils.of(
            "model", "src/$name/smithy", "src/$name/resources/META-INF/smithy");
    private static final Logger LOGGER = Logger.getLogger(SmithyPlugin.class.getName());

    @Override
    public void apply(Project project) {
        // Ensure that the Java plugin is applied.
        project.getPluginManager().apply(JavaPlugin.class);

        // Register the Smithy extension so that tasks can be configured.
        project.getExtensions().create("smithy", SmithyExtension.class);

        // Register the "smithyBuildJar" task. It's configured once the extension is available.
        TaskProvider<SmithyBuildJar> provider = project.getTasks().register("smithyBuildJar", SmithyBuildJar.class);

        // Can't read from the "smithy" extension until the "afterEvaluate" step.
        project.afterEvaluate(p -> {
            registerSourceSets(project);
            addCliDependencies(project);
            registerSmithyBuildTask(provider.get(), project);
        });
    }

    private void registerSmithyBuildTask(SmithyBuildJar buildTask, Project project) {
        if (buildTask.isEnabled()) {
            // Smithy should build before the assemble task if no jar is being created, otherwise, compileJava.
            if (!project.getTasks().getByName("jar").getEnabled()) {
                project.getTasks().getByName("assemble").dependsOn(buildTask);
            } else {
                project.getTasks().getByName("compileJava").dependsOn(buildTask);
            }
        }
    }

    /**
     * Add the CLI to the "smithyCli" dependencies.
     *
     * <p>The Smithy CLI is invoked in various ways to perform a build. This
     * method ensures that the JARs needed to invoke the CLI with a custom
     * classpath include the JARs needed to run the CLI.
     *
     * <p>The plugin will attempt to detect which version of the Smithy
     * CLI to use when performing validation of the generated JAR. If a
     * CLI version is explicitly defined in the "smithyCli" configuration,
     * then that version is used. Otherwise, a CLI version is inferred by
     * scanning the compileClasspath and runtimeClasspath for instances of
     * the "smithy-model" dependency. If found, the version of smithy-model
     * detected is used for the CLI.
     *
     * @param project Project to update.
     */
    private void addCliDependencies(Project project) {
        Configuration cli = project.getConfigurations().maybeCreate("smithyCli");
        DependencySet existing = cli.getAllDependencies();

        if (existing.stream().anyMatch(d -> isMatchingDependency(d, "smithy-cli"))) {
            LOGGER.info("Using explicitly configured Smithy CLI");
            return;
        }

        String cliVersion = project.getConfigurations().getByName("runtimeClasspath").getAllDependencies().stream()
                .filter(d -> isMatchingDependency(d, "smithy-model"))
                .map(Dependency::getVersion)
                .peek(v -> LOGGER.info(String.format("Detected Smithy CLI version %s", v)))
                .findFirst()
                .orElseGet(() -> {
                    LOGGER.info(String.format(
                            "No Smithy model dependencies were found in the JAR, assuming Smithy CLI version %s",
                            DEFAULT_CLI_VERSION));
                    return DEFAULT_CLI_VERSION;
                });

        project.getDependencies().add(cli.getName(), "software.amazon.smithy:smithy-cli:" + cliVersion);
    }

    private static boolean isMatchingDependency(Dependency dependency, String name) {
        return Objects.equals(dependency.getGroup(), "software.amazon.smithy") && dependency.getName().equals(name);
    }

    /**
     * Adds Smithy model files to the Java resources source sets of main and test.
     *
     * <p>Smithy models can be placed in {@code model/}, {@code src/main/smithy},
     * {@code src/test/smithy}, {@code src/main/resources/META-INF/smithy}, and
     * {@code src/test/resources/META-INF/smithy}. This code will add these
     * directories to the appropriate existing Java source sets as extensions.
     * Access to these source sets is provided by
     * {@link SmithyUtils#getSmithyModelSources}.
     *
     * <p>Additionally, this code adds the "manifest" plugin output of smithy
     * build to the "main" source set's resources, making them show up in the
     * generated JAR.
     *
     * @param project Project to modify.
     */
    private void registerSourceSets(Project project) {
        // Add the smithy source set to all Java source sets.
        // By default, look in model/ and src/main/smithy, src/test/smithy.
        for (SourceSet sourceSet : project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets()) {
            String name = sourceSet.getName();
            SourceDirectorySet sds = project.getObjects().sourceDirectorySet(name, name + " Smithy sources");
            sourceSet.getExtensions().add("smithy", sds);
            SOURCE_DIRS.forEach(sourceDir -> sds.srcDir(sourceDir.replace("$name", name)));
            LOGGER.fine(String.format("Adding Smithy extension to %s Java convention", name));

            // Include Smithy models and the generated manifest in the JAR.
            if (name.equals("main")) {
                File metaInf = SmithyUtils.getSmithyResourceTempDir(project).getParentFile().getParentFile();
                LOGGER.fine(String.format("Registering Smithy resource artifacts with Java resources: %s", metaInf));
                sourceSet.getResources().srcDir(metaInf);
            }
        }
    }
}
