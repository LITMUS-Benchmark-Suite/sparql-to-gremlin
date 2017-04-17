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
import org.apache.jena.sparql.algebra.op.OpConditional;
import org.apache.jena.sparql.algebra.op.OpExtend;
import org.apache.jena.sparql.algebra.op.OpFilter;
import org.apache.jena.sparql.algebra.op.OpGroup;
import org.apache.jena.sparql.algebra.op.OpLeftJoin;
import org.apache.jena.sparql.algebra.op.OpProject;
import org.apache.jena.sparql.algebra.op.OpTopN;
import org.apache.jena.sparql.algebra.op.OpUnion;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.core.VarExprList;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.tinkerpop.gremlin.process.computer.MessageScope.Local;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.Scope;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.OrderGlobalStep;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;

// TODO: implement OpVisitor, don't extend OpVisitorBase
public class SparqlToGremlinCompiler extends OpVisitorBase {

	private GraphTraversal<Vertex, ?> traversal;

	static int count = 0;
	static int lastCount = 0;
	static boolean filterFlag = false;
	static boolean unionFlag = false;
	static String filterTrav = "";
	List<Integer> bgpIndxs = new ArrayList<Integer>();
	List<Integer> unionIndxs = new ArrayList<Integer>();
	List<Integer> filterIndxs = new ArrayList<Integer>();
	List<String> allTraversals = new ArrayList<String>();
	List<Traversal> tLst = new ArrayList<Traversal>();
	OrderGlobalStep<String, Comparable> ord;
	boolean limitflg = false, orderFlg = false, groupFlag = false;
	String grpVar = "";
	int sortDirec = 0;
	long offsetLimit = 0;
	long pagingoffset = 1;
	int cntLimit = 0;
	String sortvar = "";

	GraphTraversalSource temp;
	String allOperations[];
	Graph graph;

	private SparqlToGremlinCompiler(final GraphTraversal<Vertex, ?> traversal) {
		this.traversal = traversal;
		Exception e;
		// e.printStackTrace();
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
		final Op op = Algebra.compile(query); // SPARQL query compiles here to
												// OP
		System.out.println("OP Tree: " + op.toString());
		allOperations = op.toString().split("\\(");

		OpWalker.walk(op, this); // OP is being walked here

		System.out.println("=======================================================================");
		String finalTrav = "";
		int t = 0;
		int sizeTraversal = tLst.size();
//		if (query.hasGroupBy()) {
//			sizeTraversal++;
//		}
		Traversal allTr[] = new Traversal[sizeTraversal];

		

		for (Traversal tempTrav : tLst) {

			allTr[t++] = tempTrav;

			// traversal.asAdmin().sideEffect(tempTrav);
			// traversal.as(tempTrav.toString());
			// traversal.and(tempTrav);
			// traversal.map(tempTrav);
			// traversal.by(tempTrav);
			// traversal.match(tempTrav);
			// traversal.
			System.out.println("The Traversal : " + traversal.toString());
			if (t == 1) {

				if (query.hasOrderBy() && !query.hasGroupBy()) {
					// Order ord = new OrderGlobalStep<String, >
					// graph.traversal().V(ids).has(T.label,
					// vertexLabel).order().by(inE(inELabel).count(),
					// Order.incr);
					List<SortCondition> srtCond = query.getOrderBy();
					int dir = 0;

					for (SortCondition sc : srtCond) {
						Expr expr = sc.getExpression();
						dir = sc.getDirection();
						sortvar = expr.getVarName();
						// System.out.println("The expr : "+expr.getVarName());
						// System.out.println("Order Direction : "+dir);
					}

					Order odrDir = Order.incr;
					if (dir == -1) {
						odrDir = Order.decr;
					}
					if(!query.hasGroupBy())
					traversal = traversal.order().by(sortvar, odrDir);
					else
					{
						traversal = traversal.order(Scope.local).by(sortvar, odrDir);
					}
				}
			}
		}

		if (tLst.size() > 0)
			traversal = traversal.match(allTr);

		

		// if(query.hasGroupBy())
		// {
		// //traversal = traversal.group().by("unitPrice");
		// traversal = traversal.unfold();
		// }

		if (query.hasAggregators()) {
			List<ExprAggregator> exprAgg = query.getAggregators();
			String aggrname = "";
			for (ExprAggregator aggr : exprAgg) {
				aggrname = aggr.getAggregator().getName();
				System.out.println("The Aggregator by var: " + aggr.getAggregator().getName() + " " + aggr.getVar());
				System.out.println("The Aggregator is : " + aggr.toString());
			}

			// traversal = traversal.sum();
			// Lambda.<Vertex,Integer>function(lambdaSource)

			// traversal = traversal.by((Traversal<?, ?>)
			// Lambda.<Vertex,Integer>function("it.value('unitPrice').sum()"));
		}
		
		// traversal.by(OrderGlobalStep);

		// if(query.isDescribeType())
		// {
		//
		// traversal = traversal.group().by(grpVar);
		// }
		System.out.println("=======================================================================");
		if (!query.isQueryResultStar() && !query.hasGroupBy()) {

			final List<String> vars = query.getResultVars();
			List<ExprAggregator> lstexpr = query.getAggregators();

			for (ExprAggregator expr : lstexpr) {
				System.out.println("The aggr : " + expr.toString());
				if (expr.toString().contains("SAMPLE")) {
					String str[] = expr.toString().split(" ");
				}
			}

			System.out.println("Variable vars = " + vars.toString()); // printing
																		// the
																		// name
																		// of
																		// variables
																		// --
																		// test
																		// code
			switch (vars.size()) {
			case 0:
				throw new IllegalStateException();
			case 1:
				if (query.isDistinct()) {
					traversal = traversal.dedup(vars.get(0));
				}
				traversal = traversal.select(vars.get(0));
				break;
			case 2:
				if (query.isDistinct()) {
					traversal = traversal.dedup(vars.get(0), vars.get(1));
				}
				traversal = traversal.select(vars.get(0), vars.get(1));
				break;
			default:
				final String[] all = new String[vars.size()];
				vars.toArray(all);
				if (query.isDistinct()) {
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
		
		
		
		if (query.hasGroupBy()) {
			VarExprList lstExpr = query.getGroupBy();
			String grpVar = "";
			Traversal tempTrav;
			for (Var expr : lstExpr.getVars()) {
				grpVar = expr.getName();
				System.out.println("The Group by var: " + expr.getName());
			}
//			final List<String> vars = query.getResultVars();
//			List<ExprAggregator> lstexpr = query.getAggregators();
//			switch (vars.size()) {
//			case 0:
//				throw new IllegalStateException();
//			case 1:
//				
//				break;
//			case 2:
//				//if (query.isDistinct()) {
//					
//					if(vars.get(0).equals(grpVar))
//					traversal = traversal.dedup( vars.get(1));
//					else
//					traversal = traversal.dedup( vars.get(1));
//			//	}
//				//traversal = traversal.select(vars.get(0), vars.get(1));
//				break;
//			default:
////				final String[] all = new String[vars.size()];
////				vars.toArray(all);
////				//if (query.isDistinct()) {
////					traversal = traversal.dedup(all);
////				//}
////				final String[] others = Arrays.copyOfRange(all, 2, vars.size());
////			//	traversal = traversal.select(vars.get(0), vars.get(1), others);
//
//				break;
//			}
			traversal = traversal.select(grpVar);
			if (query.hasAggregators()) {
				List<ExprAggregator> exprAgg = query.getAggregators();
				for (ExprAggregator expr : exprAgg) {
					// grpVar = expr.getVarName();
					
//					System.out.println("The Aggregator by var: " + aggr.getAggregator().getName() + " " + aggr.getVar());
//					System.out.println("The Aggregator is : " + aggr.toString());
					System.out.println("The Aggregator by var: " + expr.getAggregator().getName() + " " + expr.getVar());
					if(expr.getAggregator().getName().contains("COUNT"))
					{
						traversal = traversal.groupCount();
					}
					if(expr.getAggregator().getName().contains("MAX"))
					{
						traversal = traversal.max();
					}
				}
				
				
				
			} else {
				
				traversal = traversal.group();
			}
		}
		
		if (query.hasOrderBy()) {
			// Order ord = new OrderGlobalStep<String, >
			// graph.traversal().V(ids).has(T.label,
			// vertexLabel).order().by(inE(inELabel).count(),
			// Order.incr);
			List<SortCondition> srtCond = query.getOrderBy();
			int dir = 0;

			for (SortCondition sc : srtCond) {
				Expr expr = sc.getExpression();
				dir = sc.getDirection();
				sortvar = expr.getVarName();
				// System.out.println("The expr : "+expr.getVarName());
				// System.out.println("Order Direction : "+dir);
			}

			Order odrDir = Order.incr;
			if (dir == -1) {
				odrDir = Order.decr;
			}
			if(query.hasGroupBy())
			{
				
				if(dir==-1)
				traversal = traversal.order(Scope.local).by(Order.valueDecr);
				else
				traversal = traversal.order(Scope.local).by(Order.valueIncr);
			}
		}
		
		if (query.hasLimit()) {
			long limit = query.getLimit(), offset = 0;

			if (query.hasOffset()) {
				offset = query.getOffset();

			}
			if(query.hasGroupBy() && query.hasOrderBy())
			traversal = traversal.range(Scope.local,offset, offset + limit);
			else
			traversal = traversal.range(offset, offset + limit);

		}
		

		// traversal = traversal.count();
		return traversal;
	}

	private static GraphTraversal<Vertex, ?> convertToGremlinTraversal(final GraphTraversalSource g,
			final Query query) {
		return new SparqlToGremlinCompiler(g).convertToGremlinTraversal(query);
	}

	public static GraphTraversal<Vertex, ?> convertToGremlinTraversal(final Graph graph, final String query) {
		return convertToGremlinTraversal(graph.traversal(),
				QueryFactory.create(Prefixes.prepend(query), Syntax.syntaxSPARQL));
	}

	public static GraphTraversal<Vertex, ?> convertToGremlinTraversal(final GraphTraversalSource g,
			final String query) {
		return convertToGremlinTraversal(g, QueryFactory.create(Prefixes.prepend(query), Syntax.syntaxSPARQL));
	}

	// VISITING SPARQL ALGEBRA OP BASIC TRIPLE PATTERNS - MAYBE
	@Override
	public void visit(final OpBGP opBGP) {
		//
		count++;
		{
			System.out.println("The opBGP Visit called ==========================================");
			final List<Triple> triples = opBGP.getPattern().getList();
			final Traversal[] matchTraversals = new Traversal[triples.size()];
			int i = 0;
			for (final Triple triple : triples) {

				allTraversals.add(TraversalBuilder.transform(triple).toString());
				matchTraversals[i++] = TraversalBuilder.transform(triple);
				tLst.add(matchTraversals[i - 1]);
				System.out.println("Triple: " + triple.toString());
				System.out.println("Graph Traversal: " + matchTraversals[i - 1].toString());
			}

		}

	}

	// VISITING SPARQL ALGEBRA OP FILTER - MAYBE
	@Override
	public void visit(final OpFilter opFilter) {
		System.out.println("The opFilter Visit called ==========================================");
		// if(!(bOP.toString().contains("union")))

		Traversal trav = null;
		count++;
		String st = "";
		for (Expr expr : opFilter.getExprs().getList()) {
			trav = ((GraphTraversal<Vertex, ?>) tLst.remove(tLst.size() - 1))
					.where(WhereTraversalBuilder.transform(expr));
			st += __.where(WhereTraversalBuilder.transform(expr)).toString() + ",";
		}
		tLst.add(trav);
		st = st.substring(0, st.length() - 2);
		st = allTraversals.remove(allTraversals.size() - 1) + "," + st;
		allTraversals.add(st);

		// opFilter.getExprs().getList().stream().map(WhereTraversalBuilder::transform).reduce(traversal1,
		// GraphTraversal::where);
	}
	// TODO: add more functions for operators other than FILTER, such as
	// OPTIONAL
	// This can be done by understanding how Jena handles these other
	// operators/filters inherently and then map them to Gremlin

	@Override
	public void visit(final OpUnion opUnion) {
		System.out.println("The OpUnion visit called===========================================");
		System.out.println("Traversal before Union:" + traversal.toString());

		// String st1 = allTraversals.remove(allTraversals.size()-1);
		// String st2 = allTraversals.remove(allTraversals.size()-1);

		Traversal unionTemp[] = new Traversal[2];

		unionTemp[1] = tLst.remove(tLst.size() - 1);
		unionTemp[0] = tLst.remove(tLst.size() - 1);

		for (Traversal temp : tLst) {
			traversal = traversal.match(temp);
		}

		traversal = (GraphTraversal<Vertex, ?>) traversal.union(unionTemp);
		tLst.add(__.union(unionTemp));
		tLst.clear();
		// String unionCombined = "UnionStep(["+st2+","+st1+"])";
		// allTraversals.add(unionCombined);

		// if(filterFlag)
		// {
		// condition = condition-1;
		// System.out.println("");
		// }
		// for(int i=condition-1;i<=condition;i++ )
		// {
		// matchTraversals[ti++] = travList.get(i);
		// System.out.println("The inside: Union: "+
		// matchTraversals[ti-1].toString());
		// }
		// if(lastCount<count )
		// {
		// System.out.println("The last count : "+lastCount);
		// System.out.println("The count : "+count);
		// System.out.println("count-3 : "+(count-3));
		// Traversal[] matchTraversals1 =new Traversal[(count-2) - lastCount];
		//
		//
		// int tempC=0;
		//
		// for(int i=lastCount;i<=condition-2;i++ )
		// {
		// matchTraversals1[tempC++] = travList.get(i);
		// System.out.println("The inside: "+
		// matchTraversals1[tempC-1].toString());
		// traversal = traversal.as(matchTraversals1[tempC-1].toString());
		// }
		//
		// System.out.println("Temp trav : "+tempC);
		//// if(tempC>0)
		//// {
		//// traversal = traversal.match(matchTraversals1);
		//// }
		// lastCount = count;
		// }
		// traversal = traversal.union(matchTraversals);

		// __.union(matchTraversals,matchTraversals1);
	}

	@Override
	public void visit(final OpConditional opConditional) {

		System.out.println("The Conditional Visit called ==========================================");

	}

	public void visit(final OpTopN opTopN) {
		System.out.println("The opTopN Visit called ==========================================");

		cntLimit = opTopN.getLimit();

	}

	public void visit(final OpGroup opGroup) {

		System.out.println("The opGroup Visit called ==========================================");
		List<Var> lstVar = opGroup.getGroupVars().getVars();

		groupFlag = true;

		for (Var var : lstVar) {
			System.out.println("The Var : " + var.toString());
			// tLst.add(__.group().by(var.getName()));

			grpVar = var.getName();
			// if(expr.toString().contains("SAMPLE"))
			// {
			// String str[] = expr.toString().split(" ");
			//
			// }
		}

	}

	public void visit(final OpProject opProject) {
		System.out.println("Inside OpProject==========================================");
		System.out.println("The function: " + opProject.toString());
	}

	public void visit(final OpExtend opExtend) {

		System.out.println("Inside OpExtend==========================================" + "\n" + opExtend.toString());
		VarExprList lstVar = opExtend.getVarExprList();
		// System.out.println(opExtend.);
		// System.out.println("The function: ");

		for (Var var : lstVar.getVars()) {
			System.out.println("The Var : " + var.toString());
			// tLst.add(__.group().by(var.getName()));

			// grpVar = var.getName();
			// if(expr.toString().contains("SAMPLE"))
			// {
			// String str[] = expr.toString().split(" ");
			//
			// }
		}
	}

	public void visit(final OpLeftJoin opLeftJoin) {
		System.out.println("The opLeftJoin Visit called ==========================================");

	}
}
