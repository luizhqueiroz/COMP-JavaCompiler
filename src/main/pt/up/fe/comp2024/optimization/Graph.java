package pt.up.fe.comp2024.optimization;

import java.util.*;

public class Graph {
    private Set<String> nodes;
    private Map<String, Set<String>> edges;

    public Graph() {
        this.nodes = new HashSet<>();
        this.edges = new HashMap<>();
    }

    public Graph(Set<String> nodes, Map<String, Set<String>> edges) {
        this.nodes = new HashSet<>(nodes);
        this.edges = new HashMap<>();
        for (String node : edges.keySet()) {
            this.edges.put(node, new HashSet<>(edges.get(node)));
        }
        for (String node : nodes) {
            this.edges.putIfAbsent(node, new HashSet<>());
        }
    }

    public Map<String, Set<String>> getEdges() {
        return edges;
    }

    public Set<String> getNodes() {
        return nodes;
    }

    public Set<String> getEdges(String node) {
        return edges.getOrDefault(node, Collections.emptySet());
    }

    public void removeNode(String node) {
        nodes.remove(node);
        Set<String> removedEdges = edges.remove(node);
        if (removedEdges != null) {
            for (String neighbor : removedEdges) {
                edges.get(neighbor).remove(node);
            }
        }
    }

    public void addNode(String node) {
        nodes.add(node);
        edges.putIfAbsent(node, new HashSet<>());
    }

    public void addEdge(String node1, String node2) {
        edges.computeIfAbsent(node1, k -> new HashSet<>()).add(node2);
        edges.computeIfAbsent(node2, k -> new HashSet<>()).add(node1);
    }

    public void removeEdge(String node1, String node2) {
        if (edges.containsKey(node1)) {
            edges.get(node1).remove(node2);
        }
        if (edges.containsKey(node2)) {
            edges.get(node2).remove(node1);
        }
    }

    public int degree(String node) {
        return edges.getOrDefault(node, Collections.emptySet()).size();
    }


    public Graph copy() {
        return new Graph(new HashSet<>(this.nodes), new HashMap<>(this.edges));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("Nodes: ");
        for (var node : this.nodes) {
            sb.append(node).append(" ");
        }

        sb.append("\nEdges: \n");
        for (var node : this.edges.keySet()) {
            sb.append(node).append(": ");
            for (var edge : this.edges.get(node)) {
                sb.append(edge).append(" ");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
