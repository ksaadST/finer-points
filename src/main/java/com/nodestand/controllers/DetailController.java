package com.nodestand.controllers;

import com.nodestand.nodes.repository.ArgumentNodeRepository;
import org.neo4j.kernel.impl.core.RelationshipProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.conversion.Result;
import org.springframework.data.neo4j.core.GraphDatabase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class DetailController {

    @Autowired
    ArgumentNodeRepository repo;

    @Autowired
    GraphDatabase graphDatabase;

    @Transactional
    @PreAuthorize("permitAll")
    @RequestMapping("/detail")
    public Object getGraph(@RequestParam(value="id", required=true) String id) {

        // id represents an ArgumentBody id.

        Map<String, Object> params = new HashMap<>();
        params.put( "id", Long.parseLong(id) );

        Result<Map<String, Object>> result = graphDatabase.queryEngine().query("start n=node({id}) " +
                "match n-[:VERSION_OF]->(mv:MajorVersion) " +
                "with mv " +
                "match mv<-[:VERSION_OF]-(argBody:ArgumentBody)<-[resp:RESPONDS_TO*0..]-commentable-[:AUTHORED_BY]->author " +
                "return {" +
                    "id: id(commentable), " +
                    "body: commentable.body, " +
                    "author: {id: id(author), displayName: author.displayName}," +
                    "minorVersion: argBody.minorVersion" +
                "} as Commentable, resp", params);

        List<Map<String, Object>> nodes = new LinkedList<>();
        Set<List<Long>> edges = new HashSet<>();
        Map<String, Object> everything = new HashMap<>();

        for (Map<String, Object> map: result) {
            nodes.add((Map<String, Object>) map.get("Commentable"));
            List<RelationshipProxy> rels = (List<RelationshipProxy>) map.get("resp");
            for (RelationshipProxy rel: rels) {
                edges.add(Arrays.asList(
                        rel.getStartNode().getId(),
                        rel.getEndNode().getId()));
            }
        }

        everything.put("nodes", nodes);
        everything.put("edges", edges);



        return everything;

    }

}
