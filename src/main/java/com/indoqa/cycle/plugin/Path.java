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

import java.util.*;

public class Path<T> implements Iterable<T> {

    public static final Comparator<Path<?>> LENGTH_COMPARATOR = new Comparator<Path<?>>() {

        @Override
        public int compare(Path<?> o1, Path<?> o2) {
            return o1.size() - o2.size();
        }
    };

    private List<T> elements = new ArrayList<T>();

    public Path(T element) {
        this.elements.add(element);
    }

    private Path(List<T> elements) {
        this.elements.addAll(elements);
    }

    private Path(Path<T> dependencyChain, T element) {
        this.elements.addAll(dependencyChain.getElements());
        this.elements.add(element);
    }

    public boolean beginsWith(Path<T> other) {
        if (this.size() < other.size()) {
            return false;
        }

        return this.elements.subList(0, other.size()).equals(other.elements);
    }

    public boolean containsCycle() {
        for (int i = 0; i < this.elements.size() - 1; i++) {
            T eachElement = this.elements.get(i);

            for (int j = i + 1; j < this.elements.size(); j++) {
                if (eachElement.equals(this.elements.get(j))) {
                    return true;
                }
            }
        }

        return false;
    }

    public Path<T> createChild(T element) {
        return new Path<T>(this, element);
    }

    public List<T> getElements() {
        return this.elements;
    }

    public T getLastElement() {
        return this.elements.get(this.elements.size() - 1);
    }

    @Override
    public Iterator<T> iterator() {
        if (this.elements == null) {
            return Collections.<T> emptyList().iterator();
        }

        return this.elements.iterator();
    }

    public void setElements(List<T> elements) {
        this.elements = elements;
    }

    public int size() {
        return this.elements.size();
    }

    public Path<T> subPath(int startIndex) {
        return this.subPath(startIndex, this.elements.size());
    }

    public Path<T> subPath(int startIndex, int endIndex) {
        return new Path<T>(this.elements.subList(startIndex, endIndex));
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();

        for (T eachElement : this.elements) {
            stringBuilder.append(eachElement);
            stringBuilder.append(", ");
        }

        return stringBuilder.toString();
    }
}
