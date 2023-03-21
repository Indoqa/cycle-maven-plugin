/*
 * Licensed to the Indoqa Software Design und Beratung GmbH (Indoqa) under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Indoqa licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.indoqa.cycle.plugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.Predefined;

public class CycleDetector {

    private static final String NEW_LINE = "\r\n";

    private JavaClasses javaClasses;
    private String[] excludedPackages;

    public CycleDetector(File directory, String[] excludedPackages) {
        super();
        this.excludedPackages = excludedPackages;

        this.javaClasses = new ClassFileImporter(Arrays.asList(Predefined.DO_NOT_INCLUDE_ARCHIVES)).importPath(directory.toPath());
    }

    private static void indent(BufferedWriter writer, int depth) throws IOException {
        for (int i = 0; i < depth; i++) {
            writer.write("  ");
        }
    }

    private static Cycle toCycle(Path<JavaPackage> path) {
        Cycle result = new Cycle();

        for (JavaPackage eachPackageNode : path) {
            result.addInvolvedPackage(eachPackageNode.getName());
        }

        return result;
    }

    public List<Cycle> getPackageCycles() {
        List<Cycle> result = new ArrayList<>();

        Set<JavaPackage> packages = this.javaClasses.getDefaultPackage().getSubpackagesInTree();
        for (JavaPackage eachPackage : packages) {
            if (this.isExcluded(eachPackage)) {
                continue;
            }

            List<Path<JavaPackage>> dependencyChains = this.getDependencyChains(eachPackage);
            for (Path<JavaPackage> eachDependencyChain : dependencyChains) {
                if (eachDependencyChain.containsCycle()) {
                    result.add(toCycle(eachDependencyChain));
                }
            }
        }

        return result;
    }

    public void writeCycleFile(List<Cycle> cycles, java.nio.file.Path targetPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(targetPath, Charset.forName("UTF-8"))) {
            for (Cycle eachCycle : cycles) {
                writer.write("Cycle");
                writer.write(NEW_LINE);
                for (String eachPackage : eachCycle.getInvolvedPackages()) {
                    indent(writer, 1);
                    writer.write(eachPackage);
                    writer.write(NEW_LINE);
                }

                writer.write(NEW_LINE);
                indent(writer, 1);
                writer.write("Involved Classes From Each Package");
                writer.write(NEW_LINE);

                List<String> involvedPackages = eachCycle.getInvolvedPackages();
                for (int i = 0; i < involvedPackages.size() - 1; i++) {
                    JavaPackage startPackage = this.getPackage(involvedPackages.get(i));
                    JavaPackage endPackage = this.getPackage(involvedPackages.get(i + 1));

                    indent(writer, 2);
                    writer.write(startPackage.getName());
                    writer.write(NEW_LINE);

                    Set<Connection<JavaClass>> classConnections = this.getClassConnections(startPackage, endPackage);
                    for (Connection<JavaClass> eachClassConnection : classConnections) {
                        indent(writer, 3);
                        writer.write(eachClassConnection.getStart().getName());
                        writer.write(" -> ");
                        writer.write(eachClassConnection.getEnd().getName());
                        writer.write(NEW_LINE);
                    }

                    writer.write(NEW_LINE);
                }

                writer.write(NEW_LINE);
            }
        }
    }

    private Set<Connection<JavaClass>> getClassConnections(JavaPackage startPackage, JavaPackage endPackage) {
        Set<Connection<JavaClass>> result = new HashSet<>();

        Set<JavaClass> startClasses = startPackage.getClasses();
        Set<JavaClass> endClasses = endPackage.getClasses();

        for (JavaClass eachStartClass : startClasses) {
            Set<Dependency> connections = eachStartClass.getDirectDependenciesFromSelf();
            for (Dependency eachConnection : connections) {
                if (!endClasses.contains(eachConnection.getTargetClass())) {
                    continue;
                }

                result.add(new Connection<>(eachStartClass, eachConnection.getTargetClass()));
            }
        }

        return result;
    }

    private List<Path<JavaPackage>> getDependencyChains(JavaPackage packageNode) {
        return this.getDependencyChains(new Path<>(packageNode));
    }

    private List<Path<JavaPackage>> getDependencyChains(Path<JavaPackage> dependencyChain) {
        if (dependencyChain.containsCycle()) {
            return Collections.singletonList(dependencyChain);
        }

        Set<JavaPackage> dependencies = dependencyChain.getLastElement().getPackageDependenciesFromThisPackage();
        if (dependencies == null || dependencies.isEmpty()) {
            return Collections.singletonList(dependencyChain);
        }

        List<Path<JavaPackage>> result = new ArrayList<>();

        for (JavaPackage eachDependency : dependencies) {
            result.addAll(this.getDependencyChains(dependencyChain.createChild(eachDependency)));
        }

        return result;
    }

    private JavaPackage getPackage(String packageName) {
        return this.javaClasses.getPackage(packageName);
    }

    private boolean isExcluded(JavaPackage javaPackage) {
        if (this.excludedPackages == null || this.excludedPackages.length == 0) {
            return false;
        }

        String name = javaPackage.getName();
        return Stream.of(this.excludedPackages).anyMatch(excludedPackage -> name.equals(excludedPackage));
    }
}
