![gremlinator][SPARQL-Gremlin]

[SPARQL-Gremlin]: https://raw.githubusercontent.com/LITMUS-Benchmark-Suite/sparql-to-gremlin/master/docs/images/sparql-gremlin-logo.png

# Gremlinator documentation
This document provide a reference documentation for the SPARQL-Gremlin transpiler, aka *Gremlinator*, which is a compiler used to transform SPARQL queries into Gremlin traversals. It is based on the [Apache Jena](https://jena.apache.org/index.html) SPARQL processor  [ARQ](https://jena.apache.org/documentation/query/index.html), which provides access to a syntax tree of a SPARQL query.

The current version of SPARQL-Gremlin only uses a subset of the features provided by Apache Jena. The examples below show each implemented feature.

# Table of contents
1. [Introduction](#introduction-)
    1. [Goal](#goal-)
    2. [Supported Queries](#supported-queries-)
    3. [Limitations](#limitations-)
2. [Usage](#usage-)
    1. [Console Application](#console-application-)
    2. [Gremlin Shell Plugin](#gremlin-shell-plugin-)
        1. [Prefixes](#prefixes-)
3. [Examples](#examples-)
4. [Future work](#future-work-)
5. [Acknowledgements](#acknowledgements-)

## Introduction <a name="introduction"></a>
This is an continuous effort towards enabling automatic support for executing SPARQL queries over Graph systems via Gremlin query language. This is achieved by converting SPARQL queries to Gremlin pattern matching traversals.

### Goal <a name="goal"></a>
The *Gremlinator* work is a sub-task of a bigger goal: LITMUS, an open extensible framework for benchmarking diverse data management solutions. Further information can be obtained from the following resources:
  1. Proposal - [ESWC 2017 Ph.D. Symposium](https://arxiv.org/pdf/1608.02800.pdf "LITMUS Proposal")
  2. Publication - [Semantics 2017 R&D paper](https://dl.acm.org/citation.cfm?doid=3132218.3132232 "LITMUS Benchmark Suite") (_best research & innovation paper award_)
  3. First working prototype - [Docker](https://hub.docker.com/r/litmusbenchmarksuite/litmus/)

The foundational research work on Gremlinator can be found from - [Gremlinator full paper](https://arxiv.org/pdf/1801.02911.pdf "SWJ submission")
Furthermore, we point the interested reader to the following resourcesfor a better understanding:
  1. Gremlinator demonstration - ([Public Demo Mirror 1](http://gremlinator.iai.uni-bonn.de:8080/Demo/ "Gremlinator demonstration")) and ([Public Demo Mirror 2](http://195.201.31.31:8080/Demo/ "Gremlinator demonstration"))
  2. A short video tutorial on how to use the demonstration - [Video tutorial](https://www.youtube.com/watch?v=Z0ETx2IBamw "video tutorial")

### Supported Queries <a name="#supported-queries"></a>
*Gremlinator* is currently na on-going effort with an aim to cover the entire SPARQL 1.1 query spectrum,
however we currently support translation of the SPARQL 1.0 specification, especially *SELECT* queries.
The supported SPARQL query types are:
* Union
* Optional
* Order-By
* Group-By
* STAR-shaped or *neighbourhood queries*
* Query modifiers, such as:
    * Filter with *restrictions*
    * Count
    * LIMIT
    * OFFSET

### Limitations <a name="limitations"></a>
The current implementation of *Gremlinator* (i.e. SPARQL-Gremlin) does not support the following:
* SPARQL queries with variables in the predicate position are not currently covered, with an exception of the following case:
```sparql
SELECT * WHERE { ?x ?y ?z . }
```
* A SPARQL Union query with un-balanced patterns, i.e. a gremlin union traversal can only be generated if the unput SPARQL query has the same number of patterns on both the side of the union operator. For instance, the following SPARQL query cannot be mapped using Gremlinator, since a union is executed between different number of graph patterns (two patterns *union* 1 pattern).
```sparql
SELECT * WHERE {
  {?person e:created ?software .
  ?person v:name "daniel" .}
  UNION
  {?software v:lang "java" .}
}
```


## Usage <a name="usage"></a>

### Console Application <a name="console-application"></a>

The project contains a console application that can be used to compile SPARQL queries and evaluate the resulting Gremlin traversals. For usage examples simply run `${PROJECT_HOME}/bin/sparql-gremlin.sh`.

### Gremlin Shell Plugin <a name="gremlin-shell-plugin"></a>

To use Gremlin-SPARQL as a Gremlin shell plugin, run the following commands (be sure `sparql-gremlin-xyz.jar` is in the classpath):

```
gremlin> :install com.datastax sparql-gremlin 0.1
==>Loaded: [com.datastax, sparql-gremlin, 0.1]
gremlin> :plugin use datastax.sparql
==>datastax.sparql activated
```

Once the plugin is installed and activated, establish a remote connection to execute SPARQL queries:

```
gremlin> :remote connect datastax.sparql graph
==>SPARQL[graphtraversalsource[tinkergraph[vertices:6 edges:6], standard]]
gremlin> :> SELECT ?name ?age WHERE { ?person v:name ?name . ?person v:age ?age }
==>[name:marko, age:29]
==>[name:vadas, age:27]
==>[name:josh, age:32]
==>[name:peter, age:35]
```

**Note** that the `sparql-gremlin 0.1` is a legacy plugin and will be replaced by the new updated `sparql-gremlin 0.2` plugin, once successfully tested. `sparql-gremlin 0.1` does not support the SPARQL features described in the documentation, rather only basic graph patterns. 

#### Prefixes <a name="prefixes"></a>

SPARQL-Gremlin supports the following prefixes to traverse the graph:

| Prefix  | Purpose
| --- | ---
| `v:<label>` | label-access traversal
| `e:<label>` | out-edge traversal
| `p:<name>`  | property traversal
| `v:<name>`  | property-value traversal

Note that element IDs and labels are treated like normal properties, hence they can be accessed using the same pattern:
```sparql
SELECT ?name ?id ?label WHERE { ?element v:name ?name . ?element v:id ?id . ?element v:label ?label }
```

## Examples <a name="examples"></a>
In this section, we present comprehensive examples of SPARQL queries that are currently supported by *Gremlinator*.

### Select All
Select all vertices in the graph.
```sparql
SELECT * WHERE { }
```

### Match Constant Values
Select all vertices with the label `person`.
```sparql
SELECT * WHERE {  ?person v:label "person" .
}
```

### Select Specific Elements
Select the values of the properties `name` and `age` for each `person` vertex.
```sparql
SELECT ?name ?age
WHERE {
  ?person v:label "person" .
  ?person v:name ?name .
  ?person v:age ?age .
}
```

### Pattern Matching
Select only those persons who created a project.
```sparql
SELECT ?name ?age
WHERE {
  ?person v:label "person" .
  ?person v:name ?name .
  ?person v:age ?age .
  ?person e:created ?project .
}
```

### Filtering
Select only those persons who are older than 30.
```sparql
SELECT ?name ?age
WHERE {
  ?person v:label "person" .
  ?person v:name ?name .
  ?person v:age ?age .
  ?person e:created ?project .
    FILTER (?age > 30)
}
```

### Deduplication
Select the distinct names of the created projects.
```sparql
SELECT DISTINCT ?name
WHERE {
  ?person v:label "person" .
  ?person e:created ?project .
  ?project v:name ?name .
    FILTER (?age > 30)
}
```

### Multiple Filters
Select the distinct names of all Java projects.
```sparql
SELECT DISTINCT ?name
WHERE {
  ?person v:label "person" .
  ?person v:age ?age .
  ?person e:created ?project .
  ?project v:name ?name .
  ?project v:lang ?lang .
    FILTER (?age > 30 && ?lang == "java")
}
```

### Pattern Filter(s)
A different way to filter all person who created a project.
```sparql
SELECT ?name
WHERE {
  ?person v:label "person" .
  ?person v:name ?name .
    FILTER EXISTS { ?person e:created ?project }
}
```

Filter all person who did not create a project.
```sparql
SELECT ?name
WHERE {
  ?person v:label "person" .
  ?person v:name ?name .
    FILTER NOT EXISTS { ?person e:created ?project }
}
```

### Meta-Property Access
Accessing the Meta-Property of a graph element. Meta-Property can be perceived as the reified statements in an RDF graph.

```sparql
SELECT ?name ?startTime
WHERE {
  ?person v:name "daniel" .
  ?person p:location ?location .
  ?location v:value ?name .
  ?location v:startTime ?startTime
}
```

### Union
Select all persons who have developed a software in java using union.

```sparql
SELECT * WHERE {
  {?person e:created ?software .}
  UNION
  {?software v:lang "java" .}
}
```

### Optional
Return the names of the persons who have created a software in java and optionally also in python.
```sparql
SELECT ?person WHERE {
  ?person v:label "person" .
  ?person e:created ?software .
  ?software v:lang "java" .
  OPTIONAL {?software v:lang "python" . }
}
```

### Order By
Select all vertices with the label `person` and order them by their age.

```sparql
SELECT * WHERE {
  ?person v:label "person" .
  ?person v:age ?age .
} ORDER BY (?age)
```

### Group By
Select all vertices with the label `person` and group them by their age.

```sparql
SELECT * WHERE {
  ?person v:label "person" .
  ?person v:age ?age .
} GROUP BY (?age)
```

### Mixed/complex/aggregation-based queries
Count the number of projects which have been created by persons under the age of 30 and group them by age. Return only the top two.
```sparql
SELECT COUNT(?project) WHERE {
  ?person v:label "person" .
  ?person v:age ?age . FILTER (?age < 30)
  ?person e:created ?project .
} GROUP BY (?age) LIMIT 2
```


### STAR-shaped queries
STAR-shaped queries are the queries that form/follow a star-shaped execution plan. These in terms of graph traversals can be perceived as path queries or neighbourhood queries. For instance, getting all the information about a specific `person` or `software`.
```sparql
SELECT ?age ?software ?name ?location ?startTime WHERE {
  ?person v:name "daniel" .
  ?person v:age ?age .
  ?person e:created ?software .
  ?person p:location ?location .
  ?location v:value ?name .
  ?location v:startTime ?startTime
}
```


## Future work <a name="future-work"></a>
As future work we plan to:
1. cover all cases of SPARQL queries with variables in predicate position
2. cover SPARQL 1.1 specification (such as Property Paths)

## Acknowledgements <a name="acknowledgements"></a>
The authors of this project are supported by funding received from the European Union’s Horizon 2020 Research and  Innovation program under the Marie Skłodowska Curie Grant Agreement No. 642795 ([WDAqua ITN](http://wdaqua.eu/)).

We would like to express our gratitude to Mr. Daniel Kupitz, who laid the early foundation of work that follows. Lastly, we would also like to thank Mr. Stephen Mallette and Dr. Marko Rodriguez for their valuable inputs and efforts for enabling the integration of *Gremlinator* into Apache TinkerPop framework. Many thanks getting us started *three-cheers* :)
