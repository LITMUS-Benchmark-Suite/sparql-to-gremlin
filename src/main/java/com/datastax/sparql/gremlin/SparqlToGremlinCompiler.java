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

import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import org.apache.jena.sparql.algebra.op.OpFilter;
import org.apache.jena.sparql.algebra.op.OpLeftJoin;
import org.apache.jena.sparql.algebra.op.OpUnion;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.core.VarExprList;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.Scope;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import groovy.json.internal.ValueMap;
import groovy.json.internal.ValueMapImpl;

// TODO: implement OpVisitor, don't extend OpVisitorBase
public class SparqlToGremlinCompiler extends OpVisitorBase {

	private GraphTraversal<Vertex, ?> traversal;
	private GraphTraversal<Vertex, ?> optTraversal;

	List<Traversal> traversalList = new ArrayList<Traversal>();
	List<Traversal> optionalTraversals = new ArrayList<Traversal>();
	List<String> optionalVariable = new ArrayList<String>(); 
	
	boolean optionalFlag = false;

	String groupVariable = "";
	int sortingDirection = 0;
	long offsetLimit = 0;
	String sortingVariable = "";

	GraphTraversalSource temp;
	Graph graph;

	private SparqlToGremlinCompiler(final GraphTraversal<Vertex, ?> traversal) {
		this.traversal = traversal;
	}

	private SparqlToGremlinCompiler(final GraphTraversalSource g) {
		this(g.V());
		temp = g;

	}

	private SparqlToGremlinCompiler(final Graph g) {
		this.traversal = (GraphTraversal<Vertex, ?>) g.traversal();
		graph = g;
	}

	public String createMatchStep(String step) {
		String st = "";
		step = step.substring(1, step.length() - 2);
		String first = step.substring(0, step.indexOf(","));
		String second = step.substring(step.indexOf(",") + 1);
		System.out.println("First : " + first);
		System.out.println("Second : " + second);
		st = first.substring(first.indexOf("["), first.length() - 1);
		st = "[" + st + "," + second + "]";
		return st;
	}

	GraphTraversal<Vertex, ?> convertToGremlinTraversal(final Query query) {

		long startTime = System.currentTimeMillis();
		long endTime;
		final Op op = Algebra.compile(query); // SPARQL query compiles here to
												// OP
		// System.out.println("OP Tree: " + op.toString());

		OpWalker.walk(op, this); // OP is being walked here

		// System.out.println("time taken for opWalker:"+ (endTime-startTime));
		startTime = System.currentTimeMillis();
		int traversalIndex = 0;
		int numberOfTraversal = traversalList.size();
		int numberOfOptionalTraversal = optionalTraversals.size();
		Traversal arrayOfAllTraversals[] = null;
		if (numberOfOptionalTraversal > 0) {
			arrayOfAllTraversals = new Traversal[numberOfTraversal - numberOfOptionalTraversal + 1];
		} else {
			arrayOfAllTraversals = new Traversal[numberOfTraversal - numberOfOptionalTraversal];
		}
		Traversal arrayOfOptionalTraversals[] = new Traversal[numberOfOptionalTraversal];
		if (query.hasOrderBy() && !query.hasGroupBy()) {
			List<SortCondition> sortingConditions = query.getOrderBy();
			int directionOfSort = 0;

			for (SortCondition sortCondition : sortingConditions) {
				Expr expr = sortCondition.getExpression();
				directionOfSort = sortCondition.getDirection();
				sortingVariable = expr.getVarName();

			}

			Order orderDirection = Order.incr;
			if (directionOfSort == -1) {
				orderDirection = Order.decr;
			}
		}
		for (int i = 0; i < arrayOfAllTraversals.length; i++) {

			arrayOfAllTraversals[i] = traversalList.get(i);
		}
		traversalIndex = 0;
		for (Traversal tempTrav : optionalTraversals) {

			arrayOfOptionalTraversals[traversalIndex++] = tempTrav;
			// System.out.println("The optional traversal here : " +
			// arrayOfOptionalTraversals[traversalIndex - 1]);
		}

		int directionOfSort = 0;
		Order orderDirection = Order.incr;
		if (query.hasOrderBy()) {
			List<SortCondition> sortingConditions = query.getOrderBy();

			//
			for (SortCondition sortCondition : sortingConditions) {
				Expr expr = sortCondition.getExpression();
				directionOfSort = sortCondition.getDirection();
				sortingVariable = expr.getVarName();
				// System.out.println("order by var: "+sortingDirection);
			}
			//

			if (directionOfSort == -1) {
				orderDirection = Order.decr;
			}

		}

		if (traversalList.size() > 0) {
			traversal = traversal.match(arrayOfAllTraversals);
		}
		if (optionalTraversals.size() > 0) {
			System.out.println("The optional travesal : " + optionalTraversals + "size of array : "
					+ arrayOfOptionalTraversals.length);
			// GraphTraversal<Vertex, ?> traversalTemp = null;
			// traversalTemp = traversalTemp.match(arrayOfOptionalTraversals);
			traversal = traversal.coalesce(__.match(arrayOfOptionalTraversals), (Traversal) __.constant("N/A"));
			for (int i = 0; i < optionalVariable.size(); i++) {
				traversal = traversal.as(optionalVariable.get(i).substring(1));
			}
		}

		final List<String> vars = query.getResultVars();
		List<ExprAggregator> lstexpr = query.getAggregators();
		if (!query.isQueryResultStar() && !query.hasGroupBy()) {

			switch (vars.size()) {
			case 0:
				throw new IllegalStateException();
			case 1:
				if (query.isDistinct()) {
					// System.out.println("Inside ------------------- >Select
					// 1------------------------> Distinct");
					traversal = traversal.dedup(vars.get(0));
				}
				if (query.hasOrderBy()) {
					// System.out.println("Inside ------------------- >Select
					// 1");

					traversal = traversal.order().by(__.select(vars.get(0)), orderDirection);
				} else {

					traversal = traversal.select(vars.get(0));
				}
				break;
			case 2:
				if (query.isDistinct()) {
					traversal = traversal.dedup(vars.get(0), vars.get(1));
				}
				if (query.hasOrderBy()) {
					// System.out.println("Inside ------------------- >Select
					// 1");
					traversal = traversal.order().by(__.select(vars.get(0)), orderDirection).by(__.select(vars.get(1)));
				} else
					traversal = traversal.select(vars.get(0), vars.get(1));
				break;
			default:
				final String[] all = new String[vars.size()];
				vars.toArray(all);
				if (query.isDistinct()) {

					traversal = traversal.dedup(all);
				}
				final String[] others = Arrays.copyOfRange(all, 2, vars.size());
				if (query.hasOrderBy()) {

					traversal = traversal.order().by(__.select(vars.get(0)), orderDirection).by(__.select(vars.get(1)));

				} else
					traversal = traversal.select(vars.get(0), vars.get(1), others);

				break;
			}
			
		}

		if (query.hasGroupBy()) {
			VarExprList lstExpr = query.getGroupBy();
			String grpVar = "";
			Traversal tempTrav;
			for (Var expr : lstExpr.getVars()) {
				grpVar = expr.getName();
				System.out.println("The Group by var: " + expr.getName());
			}

			if (query.hasLimit()) {
				long limit = query.getLimit(), offset = 0;

				if (query.hasOffset()) {
					offset = query.getOffset();

				}
				// if (query.hasGroupBy() && query.hasOrderBy())
				// traversal = traversal.range( offset, offset + limit);
				// else
				// traversal = traversal.range(offset, offset + limit);

			}

			if (!grpVar.isEmpty())
				traversal = traversal.select(grpVar);
			if (query.hasAggregators()) {
				List<ExprAggregator> exprAgg = query.getAggregators();
				for (ExprAggregator expr : exprAgg) {

					// System.out.println("The Aggregator by var: " +
					// expr.getAggregator().getExprList().toString()
					// + " is :" + expr.getAggregator().toString());
					if (expr.getAggregator().getName().contains("COUNT")) {
						if (!query.toString().contains("GROUP")) {
							if (expr.getAggregator().toString().contains("DISTINCT")) {
								traversal = traversal
										.dedup(expr.getAggregator().getExprList().get(0).toString().substring(1));
							} else {
								traversal = traversal
										.select(expr.getAggregator().getExprList().get(0).toString().substring(1));
							}
							traversal = traversal.count();
						} else
							traversal = traversal.groupCount();
					}
					if (expr.getAggregator().getName().contains("MAX")) {
						traversal = traversal.max();
					}
				}

			} else {

				traversal = traversal.group();
			}

		}

		if (query.hasOrderBy() && query.hasGroupBy()) {

			traversal = traversal.order().by(sortingVariable, orderDirection);
		}
		if (query.hasLimit()) {
			long limit = query.getLimit(), offset = 0;

			if (query.hasOffset()) {
				offset = query.getOffset();

			}
			if (query.hasGroupBy() && query.hasOrderBy())
				traversal = traversal.range(Scope.local, offset, offset + limit);
			else
				traversal = traversal.range(offset, offset + limit);

		}
		endTime = System.currentTimeMillis();
		System.out.println(
				"time taken for convertToGremlinTraversal Function : " + (endTime - startTime) + " mili seconds");

		return traversal;
	}

	private static GraphTraversal<Vertex, ?> convertToGremlinTraversal(final GraphTraversalSource g,
			final Query query) {
		return new SparqlToGremlinCompiler(g).convertToGremlinTraversal(query);
	}

	public static GraphTraversal<Vertex, ?> convertToGremlinTraversal(final Graph graph, final String query) {

		if (query.contains("?x") && query.contains("?y") && query.contains("?z"))
			return graph.traversal().V().outE().inV().path().by(__.valueMap());
		return convertToGremlinTraversal(graph.traversal(), QueryFactory.create(Prefixes.prepend(query)));
	}

	public static GraphTraversal<Vertex, ?> convertToGremlinTraversal(final GraphTraversalSource g,
			final String query) {
		return convertToGremlinTraversal(g, QueryFactory.create(Prefixes.prepend(query), Syntax.syntaxSPARQL));
	}

	// VISITING SPARQL ALGEBRA OP BASIC TRIPLE PATTERNS - MAYBE
	@Override
	public void visit(final OpBGP opBGP) {
		{

			System.out.println("Inside opBGP ---------------------------------------------->");
			final List<Triple> triples = opBGP.getPattern().getList();
			final Traversal[] matchTraversals = new Traversal[triples.size()];
			int i = 0;
			for (final Triple triple : triples) {

				matchTraversals[i++] = TraversalBuilder.transform(triple);
				if (optionalFlag) {
					optionalTraversals.add(matchTraversals[i - 1]);
					optionalVariable.add(triple.getObject().toString());
					System.out.println("Inside Optional traversal==========================");
				} else {
					traversalList.add(matchTraversals[i - 1]);
					System.out.println("Inside regular traversal==========================");
				}
			}

		}

	}

	// VISITING SPARQL ALGEBRA OP FILTER - MAYBE
	@Override
	public void visit(final OpFilter opFilter) {

		System.out.println("Inside opFilter ---------------------------------------------->");
		Traversal traversal = null;
		for (Expr expr : opFilter.getExprs().getList()) {
			if (expr != null) {
				traversal = __.where(WhereTraversalBuilder.transform(expr));
				traversalList.add(traversal);
			}
		}

	}
	// TODO: add more functions for operators other than FILTER, such as
	// OPTIONAL
	// This can be done by understanding how Jena handles these other
	// operators/filters inherently and then map them to Gremlin

	public void visit(final OpLeftJoin opLeftJoin) {

		System.out.println("Inside opOptional ---------------------------------------------->");
		// System.out.println(opLeftJoin.getRight().toString());

		System.out.println("Printing:========" + opLeftJoin.getRight().toString() + " ===== " + opLeftJoin.getExprs());
		optionalFlag = true;

		optionalVisit(opLeftJoin.getRight());
		if (opLeftJoin.getExprs() != null) {
			for (Expr expr : opLeftJoin.getExprs().getList()) {
				if (expr != null) {
					optTraversal = __.where(WhereTraversalBuilder.transform(expr));
					System.out.println("Visiting optional filter=============");
					if (optionalFlag)
						optionalTraversals.add(optTraversal);
				}
			}
		}
		// OpWalker.walk(opLeftJoin.getRight(), this);
		// visit(opLeftJoin);
	}

	public void optionalVisit(final Op op) {

		OpWalker.walk(op, this);
	}

	@Override
	public void visit(final OpUnion opUnion) {

		System.out.println("Inside opUnion ---------------------------------------------->");
		Traversal unionTemp[] = new Traversal[2];

		if (optionalFlag) {
			// Traversal unionTemp1[] = new Traversal[optionalTraversals.size()
			// / 2];
			// Traversal unionTemp2[] = new Traversal[optionalTraversals.size()
			// / 2];
			//
			// int count = 0;
			//
			// for (int i = 0; i < optionalTraversals.size(); i++) {
			//
			// if (i < optionalTraversals.size() / 2) {
			//
			// unionTemp1[i] = optionalTraversals.get(i);
			// } else {
			// unionTemp2[count++] = optionalTraversals.get(i);
			// }
			// }
			//
			// unionTemp[1] = __.match(unionTemp2);
			// unionTemp[0] = __.match(unionTemp1);
			//
			// optionalTraversals.clear();
			// traversal = (GraphTraversal<Vertex, ?>)
			// traversal.union(unionTemp);
		} else {
			Traversal unionTemp1[] = new Traversal[traversalList.size() / 2];
			Traversal unionTemp2[] = new Traversal[traversalList.size() / 2];

			int count = 0;

			for (int i = 0; i < traversalList.size(); i++) {

				if (i < traversalList.size() / 2) {

					unionTemp1[i] = traversalList.get(i);
				} else {
					unionTemp2[count++] = traversalList.get(i);
				}
			}

			unionTemp[1] = __.match(unionTemp2);
			unionTemp[0] = __.match(unionTemp1);

			traversalList.clear();
			traversal = (GraphTraversal<Vertex, ?>) traversal.union(unionTemp);
			// System.out.println("Getting out from Union -------------------> :
			// "+traversal);
			// traversalList.add(__.union(unionTemp));
			// traversalList.clear();
		}
	}
}
