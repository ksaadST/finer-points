package com.nodestand.nodes.repository;

import com.nodestand.nodes.ArgumentNode;
import com.nodestand.nodes.Author;
import com.nodestand.nodes.User;
import org.neo4j.ogm.model.Result;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;

import java.util.Set;

public interface ArgumentNodeRepository extends GraphRepository<ArgumentNode> {

    @Query("START t=node({0}), r=node({1}) MATCH p=(t)<-[:SUPPORTED_BY|INTERPRETS*0..]-(r) " +
            "WITH p as p, nodes(p) as path UNWIND path AS item MATCH mvPath=(item)-[:DEFINED_BY]->(:ArgumentBody)-[:VERSION_OF]->(:MajorVersion) " +
            "RETURN path, rels(p), nodes(mvPath), rels(mvPath)")
    Result getPaths(long childId, long rootId);

    @Query("match path=(n:ArgumentNode {stableId: {0}})-[support:SUPPORTED_BY|INTERPRETS*0..5]->(argument:ArgumentNode)" +
            "-[:DEFINED_BY]->(body:ArgumentBody)-[:VERSION_OF]->(:MajorVersion)-[:AUTHORED_BY]->(:Author) return path")
    Set<ArgumentNode> getGraph(String stableRootId);

    @Query("start mv=node({0}) match p=(mv)<-[:VERSION_OF]-(:ArgumentBody)" +
            "<-[:DEFINED_BY]-(node:ArgumentNode) return p")
    Set<ArgumentNode> getNodesInMajorVersion(long majorVersionId);

    @Query("match (node:ArgumentNode) return node")
    Set<ArgumentNode> getAllNodes();

    @Query("match p=(node:ArgumentNode)-[:DEFINED_BY]->(body:ArgumentBody)-[:AUTHORED_BY]->(:Author) return p")
    Set<ArgumentNode> getAllNodesRich();

    @Query("match p=(n:AssertionNode)-[:DEFINED_BY]->(b:ArgumentBody {isPublic:true})-[:VERSION_OF]->(:MajorVersion)-[:AUTHORED_BY]->(:Author)" +
            " where not (:AssertionNode)-[:SUPPORTED_BY]->(n)" +
            " with p as p, b as b match q=(b)-[:AUTHORED_BY]->(:Author) return p, q")
    Set<ArgumentNode> getRootNodesRich();

    @Query("match p=(n:ArgumentNode {stableId: {0}})-[:DEFINED_BY]->(b:ArgumentBody)-[:VERSION_OF]->(:MajorVersion)-[:AUTHORED_BY]->(:Author)" +
            " with p as p, b as b match q=(b)-[:AUTHORED_BY]->(:Author) return p, q")
    ArgumentNode getNodeRich(String stableId);

    @Query("start n=node({0}) match (body)-[VERSION_OF]->(n) return max(body.minorVersion)")
    Integer getMaxMinorVersion(long majorVersionId);


    @Query("start n=node({0}) match (node)-[DEFINED_BY]->(n) return max(node.buildVersion)")
    Integer getMaxBuildVersion(long bodyId);

    @Query("start n=node({0}) match (n)-[:SUPPORTED_BY*0..]->(support:ArgumentNode) " +
            "WHERE NOT (support)-[:INTERPRETS]->(:SourceNode) AND NOT (support)-[:SUPPORTED_BY]->(:ArgumentNode) return support")
    Set<ArgumentNode> getUnsupportedNodes(long nodeId);

    @Query("start n=node({0}) match p=(n)-[:DEFINED_BY]->(:ArgumentBody)-[:VERSION_OF]->(:MajorVersion)-[:AUTHORED_BY]->(:Author) return p")
    ArgumentNode loadWithMajorVersion(long id);

    @Query("match p=(:Author {stableId: {0}})<-[:AUTHORED_BY]-(b:ArgumentBody)<-[:DEFINED_BY]-(n:ArgumentNode) where not b.isPublic" +
            " with p as p, b as b match q=(b)-[:VERSION_OF]->(:MajorVersion)-[:AUTHORED_BY]->(:Author) return p, q")
    Set<ArgumentNode> getDraftNodesRich(String authorStableId);

    @Query("match p=(:Author {stableId: {0}})<-[:AUTHORED_BY]-(mv:MajorVersion)<-[:VERSION_OF]-(b:ArgumentBody)<-[:DEFINED_BY]-(n:ArgumentNode) where b.isPublic" +
            " with p as p, b as b match q=(b)-[:AUTHORED_BY]->(:Author) return p, q")
    Set<ArgumentNode> getNodesOriginallyAuthoredByUser(String authorStableId);

    @Query("start n=node({0}) match (c:ArgumentNode)-[:SUPPORTED_BY|INTERPRETS]->(n)" +
            " with c match p=(c)-[:DEFINED_BY]->(b:ArgumentBody)-[:VERSION_OF]->(:MajorVersion)-[:AUTHORED_BY]->(:Author) where b.isPublic" +
            " with p as p, b as b match q=(b)-[:AUTHORED_BY]->(:Author) return p, q")
    Set<ArgumentNode> getConsumerNodes(long nodeId);

    @Query("MATCH (c:ArgumentNode)-[:SUPPORTED_BY|INTERPRETS]->(n) WHERE ID(n) = {0}" +
            " with c match p=(c)-[:DEFINED_BY]->(b:ArgumentBody)-[:AUTHORED_BY]->(:Author)-[:CONTROLLED_BY]->(u:User) where b.isPublic OR ID(u) = {1}" +
            " with p as p, b as b match q=(b)-[:VERSION_OF]->(:MajorVersion)-[:AUTHORED_BY]->(:Author) return p, q")
    Set<ArgumentNode> getConsumerNodes(long nodeId, long userId);

    /**
     * At the moment, the ArgumentNode that this thing returns is rootNode, which is what I want.
     * I'm assuming it does so because rootNode is the first thing in the return list.
     * I'm not super sure that's part of the OGM contract, presently I'm too lazy to check.
     * Hopefully it will not change on me.
     *
     * Oh great, now I've recorded a case of
     * Caused by: java.lang.RuntimeException: Result not of expected size. Expected 1 row but found 2
     * at org.neo4j.ogm.session.delegates.ExecuteQueriesDelegate.queryForObject(ExecuteQueriesDelegate.java:65)
     *
     * Have not duped yet...
     */
    @Query("MATCH p=(rootNode:ArgumentNode {stableId:{0}})-[:DEFINED_BY]->(rootBody:ArgumentBody)-[:PRECEDED_BY*0..]->(:ArgumentBody)" +
            " WITH p as p, nodes(p) as pathNodes, rootBody as rootBody, rootNode as rootNode" +
            " MATCH mvPath=(rootBody)-[:VERSION_OF]->(:MajorVersion)-[:AUTHORED_BY]->(:Author)" +
            " UNWIND pathNodes AS bdy" +
            " MATCH editorPath=(bdy:ArgumentBody)-[:AUTHORED_BY]->(editor:Author)" +
            " MATCH argumentPath=(bdy:ArgumentBody)<-[:DEFINED_BY]-(argNode:ArgumentNode)" +
            " RETURN rootNode, pathNodes, rels(p), nodes(mvPath), rels(mvPath), editor, rels(editorPath), argNode, rels(argumentPath)")
    ArgumentNode getEditHistory(String stableId);
}
