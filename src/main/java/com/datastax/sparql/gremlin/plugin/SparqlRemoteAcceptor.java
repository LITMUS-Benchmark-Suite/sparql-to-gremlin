/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.datastax.sparql.gremlin.plugin;

import com.datastax.sparql.gremlin.SparqlToGremlinCompiler;
import org.apache.tinkerpop.gremlin.groovy.plugin.RemoteAcceptor;
import org.apache.tinkerpop.gremlin.groovy.plugin.RemoteException;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.codehaus.groovy.tools.shell.Groovysh;

import java.util.List;

/**
 * @author Daniel Kuppitz (http://gremlin.guru)
 */
public class SparqlRemoteAcceptor implements RemoteAcceptor {

    private final Groovysh shell;
    private GraphTraversalSource g;

    public SparqlRemoteAcceptor(final Groovysh shell) {
        this.shell = shell;
    }

    @Override
    public Object connect(final List<String> args) throws RemoteException {
        if (args.size() != 1) {
            throw new IllegalArgumentException("Usage: :remote connect " + SparqlGremlinPlugin.NAME + " <variable name of graph or graph traversal source>");
        }
        final Object graphOrTraversalSource = this.shell.getInterp().getContext().getVariable(args.get(0));
        if (graphOrTraversalSource instanceof Graph) {
            this.g = ((Graph) graphOrTraversalSource).traversal();
        } else {
            this.g = (GraphTraversalSource) graphOrTraversalSource;
        }
        return this;
    }

    @Override
    public Object configure(final List<String> args) throws RemoteException {
        return null;
    }

    @Override
    public Object submit(final List<String> args) throws RemoteException {
        try {
            final String query = RemoteAcceptor.getScript(String.join(" ", args), this.shell);
            return SparqlToGremlinCompiler.convertToGremlinTraversal(this.g, query);
        } catch (final Exception e) {
            throw new RemoteException(e);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public String toString() {
        return "SPARQL[" + this.g + "]";
    }
}
