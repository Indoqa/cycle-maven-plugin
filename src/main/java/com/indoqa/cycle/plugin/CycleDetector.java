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

import de.schauderhaft.degraph.check.ConstraintBuilder;
import de.schauderhaft.degraph.check.JCheck;
import de.schauderhaft.degraph.configuration.Configuration;
import de.schauderhaft.degraph.java.JavaGraph;
import de.schauderhaft.degraph.model.Node;

public class CycleDetector {

    private static final String NEW_LINE = "\r\n";

    private JavaGraph javaGraph;

    public CycleDetector(File directory, String[] excludedPackages) {
        super();

        this.javaGraph = createJavaGraph(directory, nullSafe(excludedPackages));
        this.calculatePackageDependencies();
    }

    private static JavaGraph createJavaGraph(File directory, String[] excludedPackages) {
        ConstraintBuilder constraintBuilder = JCheck.customClasspath(directory.getAbsolutePath()).noJars();

        for (String eachInclude : findIncludes(directory)) {
            constraintBuilder = constraintBuilder.including(eachInclude);
        }

        for (String eachExcludedPackage : excludedPackages) {
            constraintBuilder = constraintBuilder.excluding(eachExcludedPackage);
        }

        Configuration configuration = constraintBuilder.configuration();
        return new JavaGraph(configuration.createGraph());
    }

    private static List<String> findIncludes(File directory) {
        List<String> result = new ArrayList<String>();

        List<Path<File>> includes = findIncludes(new Path<File>(directory));
        for (Path<File> eachInclude : includes) {
            result.add(toClassName(eachInclude.subPath(1)));
        }

        return result;
    }

    private static List<Path<File>> findIncludes(Path<File> path) {
        List<Path<File>> result = new ArrayList<Path<File>>();

        File[] classFiles = path.getLastElement().listFiles(ClassFileFilter.CLASS_FILE_FILTER);
        if (classFiles != null && classFiles.length > 0) {
            result.add(path);
            for (File eachClassFile : classFiles) {
                result.add(path.createChild(eachClassFile));
            }
        }

        File[] childDirectories = path.getLastElement().listFiles(DirectoryFilter.DIRECTORY_FILTER);
        if (childDirectories != null && childDirectories.length > 0) {
            for (File eachChildDirectory : childDirectories) {
                result.addAll(findIncludes(path.createChild(eachChildDirectory)));
            }
        }

        return result;
    }

    private static void indent(BufferedWriter writer, int depth) throws IOException {
        for (int i = 0; i < depth; i++) {
            writer.write("  ");
        }
    }

    private static String[] nullSafe(String[] values) {
        if (values == null) {
            return new String[0];
        }

        return values;
    }

    private static String removeExtention(String name, String extension) {
        if (!name.endsWith(extension)) {
            return name;
        }

        return name.substring(0, name.length() - extension.length());
    }

    private static String toClassName(Path<File> path) {
        StringBuilder stringBuilder = new StringBuilder();

        for (File eachElement : path) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append('.');
            }

            stringBuilder.append(removeExtention(eachElement.getName(), ".class"));
        }

        return stringBuilder.toString();
    }

    private static Cycle toCycle(Path<Node> path) {
        Cycle result = new Cycle();

        for (Node eachPackageNode : path) {
            result.addInvolvedPackage(eachPackageNode.name());
        }

        return result;
    }

    public List<Cycle> getPackageCycles() {
        List<Cycle> result = new ArrayList<Cycle>();

        for (Node eachPackageNode : this.javaGraph.topNodes()) {
            List<Path<Node>> dependencyChains = this.getDependencyChains(eachPackageNode);
            for (Path<Node> eachDependencyChain : dependencyChains) {
                if (eachDependencyChain.containsCycle()) {
                    result.add(toCycle(eachDependencyChain));
                }
            }
        }

        return result;
    }

    public void writeCycleFile(List<Cycle> cycles, java.nio.file.Path targetPath) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(targetPath, Charset.forName("UTF-8"));

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
                Node startPackage = this.getPackage(involvedPackages.get(i));
                Node endPackage = this.getPackage(involvedPackages.get(i + 1));

                indent(writer, 2);
                writer.write(startPackage.name());
                writer.write(NEW_LINE);

                Set<Connection<Node>> classConnections = this.getClassConnections(startPackage, endPackage);
                for (Connection<Node> eachClassConnection : classConnections) {
                    indent(writer, 3);
                    writer.write(eachClassConnection.getStart().name());
                    writer.write(" -> ");
                    writer.write(eachClassConnection.getEnd().name());
                    writer.write(NEW_LINE);
                }

                writer.write(NEW_LINE);
            }

            writer.write(NEW_LINE);
        }

        writer.close();
    }

    private void calculatePackageDependencies() {
        for (Node eachPackageNode : this.javaGraph.topNodes()) {
            Set<Node> classNodes = this.javaGraph.contentsOf(eachPackageNode);

            for (Node eachClassNode : classNodes) {
                Set<Node> connectionsOf = this.javaGraph.connectionsOf(eachClassNode);

                for (Node eachConnection : connectionsOf) {
                    Node optionalPackageNode = this.getPackage(eachConnection);
                    if (optionalPackageNode == null || optionalPackageNode.equals(eachPackageNode)) {
                        continue;
                    }

                    this.javaGraph.connect(eachPackageNode, optionalPackageNode);
                }
            }
        }
    }

    private Set<Connection<Node>> getClassConnections(Node startPackage, Node endPackage) {
        Set<Connection<Node>> result = new HashSet<Connection<Node>>();

        Set<Node> startClasses = this.javaGraph.contentsOf(startPackage);
        Set<Node> endClasses = this.javaGraph.contentsOf(endPackage);

        for (Node eachStartClass : startClasses) {
            Set<Node> connection = this.javaGraph.connectionsOf(eachStartClass);
            for (Node eachConnection : connection) {
                if (!endClasses.contains(eachConnection)) {
                    continue;
                }

                result.add(new Connection<Node>(eachStartClass, eachConnection));
            }
        }

        return result;
    }

    private List<Path<Node>> getDependencyChains(Node packageNode) {
        return this.getDependencyChains(new Path<Node>(packageNode));
    }

    private List<Path<Node>> getDependencyChains(Path<Node> dependencyChain) {
        if (dependencyChain.containsCycle()) {
            return Collections.singletonList(dependencyChain);
        }

        Set<Node> dependencies = this.javaGraph.connectionsOf(dependencyChain.getLastElement());
        if (dependencies == null || dependencies.isEmpty()) {
            return Collections.singletonList(dependencyChain);
        }

        List<Path<Node>> result = new ArrayList<Path<Node>>();

        for (Node eachDependency : dependencies) {
            result.addAll(this.getDependencyChains(dependencyChain.createChild(eachDependency)));
        }

        return result;
    }

    private Node getPackage(Node classNode) {
        for (Node eachPackageNode : this.javaGraph.topNodes()) {
            if (this.javaGraph.contentsOf(eachPackageNode).contains(classNode)) {
                return eachPackageNode;
            }
        }

        return null;
    }

    private Node getPackage(String packageName) {
        for (Node eachPackageNode : this.javaGraph.topNodes()) {
            if (eachPackageNode.name().equals(packageName)) {
                return eachPackageNode;
            }
        }

        return null;
    }
}
