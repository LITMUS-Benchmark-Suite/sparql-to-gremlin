class NorthwindFactory {

  public static Graph createGraph() {
    def graph = TinkerGraph.open()
    def file = new File('/tmp/northwind.kryo')
    if (file.exists() == false) {
      def os = file.newOutputStream()
      os << new URL('http://sql2gremlin.com/assets/northwind.kryo').openStream()
      os.close()
    }
    NorthwindFactory.load(graph, file.getAbsolutePath())
    return graph
  }

  public static void load(final Graph graph, final String path) {
    graph.createIndex('name', Vertex.class)
    graph.createIndex('customerId', Vertex.class)
    graph.io(IoCore.gryo()).readGraph(path)
  }
}
