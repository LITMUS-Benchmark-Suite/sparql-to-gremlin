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

package com.datastax.sparql.gremlin;

import org.apache.jena.base.Sys;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.SortCondition;
import org.apache.jena.query.Syntax;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpVisitorBase;
import org.apache.jena.sparql.algebra.OpWalker;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpConditional;
import org.apache.jena.sparql.algebra.op.OpFilter;
import org.apache.jena.sparql.algebra.op.OpJoin;
import org.apache.jena.sparql.algebra.op.OpLeftJoin;
import org.apache.jena.sparql.algebra.op.OpOrder;
import org.apache.jena.sparql.algebra.op.OpTopN;
import org.apache.jena.sparql.algebra.op.OpUnion;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.Scope;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import groovy.util.OrderBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Daniel Kuppitz (http://gremlin.guru)
 */
// TODO: implement OpVisitor, don't extend OpVisitorBase
public class SparqlToGremlinCompiler extends OpVisitorBase 
{

    private GraphTraversal<Vertex, ?> traversal;
    private Op bOP ;
    private List<Traversal> travList = new ArrayList<Traversal>();

    private SparqlToGremlinCompiler(final GraphTraversal<Vertex, ?> traversal) 
    {
        this.traversal = traversal;
    }

    private SparqlToGremlinCompiler(final GraphTraversalSource g) 
    {
        this(g.V());
        
    }

    GraphTraversal<Vertex, ?> convertToGremlinTraversal(final Query query) 
    {
        final Op op = Algebra.compile(query); //SPARQL query compiles here to OP
        System.out.println("OP Tree: "+op.toString());
        bOP = op;
        OpWalker.walk(op, this); //OP is being walked here
        if (!query.isQueryResultStar()) 
        {
            final List<String> vars = query.getResultVars();
            System.out.println("Variable vars = "+vars.toString()); //printing the name of variables -- test code
            switch (vars.size()) 
            {
                case 0:
                    throw new IllegalStateException();
                case 1:
                    if (query.isDistinct()) 
                    {
                        traversal = traversal.dedup(vars.get(0));
                    }
                    traversal = traversal.select(vars.get(0));
                    break;
                case 2:
                    if (query.isDistinct()) 
                    {
                        traversal = traversal.dedup(vars.get(0), vars.get(1));
                    }
                    traversal = traversal.select(vars.get(0), vars.get(1));
                    break;
                default:
                    final String[] all = new String[vars.size()];
                    vars.toArray(all);
                    if (query.isDistinct()) 
                    {
                        traversal = traversal.dedup(all);
                    }
                    final String[] others = Arrays.copyOfRange(all, 2, vars.size());
                    traversal = traversal.select(vars.get(0), vars.get(1), others);
                    break;
	            }
            
	     } else {
	    	 
	            if (query.isDistinct()) {
	                traversal = traversal.dedup();
	            }
	        }
	        return traversal;
    }

    private static GraphTraversal<Vertex, ?> convertToGremlinTraversal(final GraphTraversalSource g, final Query query) {
        return new SparqlToGremlinCompiler(g).convertToGremlinTraversal(query);
    }

    public static GraphTraversal<Vertex, ?> convertToGremlinTraversal(final Graph graph, final String query) {
        return convertToGremlinTraversal(graph.traversal(), QueryFactory.create(Prefixes.prepend(query), Syntax.syntaxSPARQL));
    }

    public static GraphTraversal<Vertex, ?> convertToGremlinTraversal(final GraphTraversalSource g, final String query) {
        return convertToGremlinTraversal(g, QueryFactory.create(Prefixes.prepend(query), Syntax.syntaxSPARQL));
    }

    //VISITING SPARQL ALGEBRA OP BASIC TRIPLE PATTERNS - MAYBE
    @Override
    public void visit(final OpBGP opBGP) {
    	//
    	{
	    	System.out.println("The opBGP Visit called ==========================================");
	        final List<Triple> triples = opBGP.getPattern().getList();
	        final Traversal[] matchTraversals = new Traversal[triples.size()];
	        int i = 0;
	        for (final Triple triple : triples) {
	        	travList.add(TraversalBuilder.transform(triple));
	            matchTraversals[i++] = TraversalBuilder.transform(triple);
	            System.out.println("Triple: "+triple.toString());
	            System.out.println("Graph Traversal: "+matchTraversals[i-1].toString());
	        }
	        if(!(bOP.toString().contains("union")))
	        traversal = traversal.match(matchTraversals);
    	}
    }

  //VISITING SPARQL ALGEBRA OP FILTER - MAYBE
    @Override
    public void visit(final OpFilter opFilter) {
    	System.out.println("The opFilter Visit called ==========================================");
        opFilter.getExprs().getList().stream().
                map(WhereTraversalBuilder::transform).reduce(traversal, GraphTraversal::where);
    }
    // TODO: add more functions for operators other than FILTER, such as OPTIONAL
    // This can be done by understanding how Jena handles these other operators/filters inherently and then map them to Gremlin
    
    @Override
    public void visit(final OpUnion opUnion)
    {
    	System.out.println("The OpUnion visit called===========================================");
    	System.out.println("Traversal before Union:"+traversal.toString());	

    	Traversal[] matchTraversals = new Traversal[travList.size()];
    	int i=0;
    	for(Traversal tr: travList)
    	{
    		matchTraversals[i++] = tr;
    	}
        traversal = traversal.union(matchTraversals);
      
      //  __.union(matchTraversals,matchTraversals1);
    }
    
    @Override
    public void visit(final OpConditional opConditional)
    {
    
    	System.out.println("The Conditional Visit called ==========================================");
    	
    }
    
    public void visit(final OpTopN opTopN)
    {
    	System.out.println("The opTopN Visit called ==========================================");
    }
    public void visit(final OpOrder opOrder)
    {
    	System.out.println("The opOrder Visit called ==========================================");
    	for(SortCondition sc: opOrder.getConditions())
    	{
    		System.out.println("Conditions: "+sc.toString()+"\nExpr = "+sc.getExpression());
    		//traversal = traversal.order().by(sc.expression.toString(),Order.decr);sc.toString().substring(sc.toString().indexOf("C(")+2,sc.toString().length()-2)
    		//traversal = traversal.order().by(Order.incr);
    	}
    	System.out.println("Traversal : "+traversal.toString());
    	traversal = traversal.order(Scope.global);
    }
    public void visit(final OpLeftJoin opLeftJoin)
    {
    	System.out.println("The opLeftJoin Visit called ==========================================");
//    	OpBGP opBGP = (OpBGP)opLeftJoin.getLeft();
//    	final List<Triple> triples = opBGP.getPattern().getList();
//        final Traversal[] matchTraversals = new Traversal[triples.size()];
//        int i = 0;
//        for (final Triple triple : triples) {
//            matchTraversals[i++] = TraversalBuilder.transform(triple);
//            System.out.println("Triple: "+triple.toString());
//            System.out.println("Graph Traversal: "+matchTraversals[i-1].toString());
//        }
//        traversal = traversal.match(matchTraversals);
    	
    	
    }
}
