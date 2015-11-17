package com.nodestand.nodes.repository;

import com.nodestand.nodes.ArgumentBody;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ArgumentBodyRepository extends GraphRepository<ArgumentBody> {

    @Query("MATCH p=(a:User)<-[:AUTHORED_BY]-(n:ArgumentBody)-[:VERSION_OF]->(m:MajorVersion) WHERE n.title=~{0} return p")
    Set<ArgumentBody> queryTitlesRich(String query);

}
