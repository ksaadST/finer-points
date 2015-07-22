package com.nodestand.controllers;

import com.nodestand.auth.NotAuthorizedException;
import com.nodestand.dao.GraphDao;
import com.nodestand.nodes.ArgumentNode;
import com.nodestand.nodes.ImmutableNodeException;
import com.nodestand.nodes.User;
import com.nodestand.nodes.assertion.AssertionBody;
import com.nodestand.nodes.assertion.AssertionNode;
import com.nodestand.nodes.repository.ArgumentNodeRepository;
import com.nodestand.nodes.version.VersionHelper;
import com.nodestand.service.NodeUserDetailsService;
import org.neo4j.graphdb.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.GraphDatabase;
import org.springframework.data.neo4j.template.Neo4jOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class EditController {

    @Autowired
    GraphDao graphDao;

    @Autowired
    NodeUserDetailsService nodeUserDetailsService;

    @Autowired
    VersionHelper versionHelper;

    @Autowired
    ArgumentNodeRepository nodeRepository;

    @Autowired
    GraphDatabase graphDatabase;

    @Autowired
    Neo4jOperations neo4jOperations;

    /**
     * For now, this will always mark the newly created node as a draft. There will be a separate operation
     * called 'publish' which will impose more rules.
     *
     * Hyperlinks within body text have an id corresponding to a major version. That way we don't have to update them
     * when children are directly edited and thereby have their minor versions changed. The real links are managed by
     * the graph database, where nodes (not bodies) link to other nodes (which are at the build version level). Giving
     * they hyperlinks the node id of the major version will be sufficient to map to the correct child node.
     *
     * - We do not want to create multiple minor versions as people make draft edits; that should only happen after
     * publishing.
     * - Can we just say that draft-mode edits don't do anything at all to the version number?
     *
     *
     * @return
     * @throws NotAuthorizedException
     * @throws ImmutableNodeException
     */
    @Transactional
    @PreAuthorize("hasRole('ROLE_USER')")
    @RequestMapping("/editassertion")
    public AssertionNode editAssertion(@RequestBody Map<String, Object> params) throws NotAuthorizedException, ImmutableNodeException {

        User user = nodeUserDetailsService.getUserFromSession();
        Long nodeId = Long.valueOf((Integer) params.get("nodeId"));
        String title = (String) params.get("title");
        String body = (String) params.get("body");
        List<Integer> children = (List<Integer>) params.get("links");

        AssertionNode existingNode = (AssertionNode) nodeRepository.findOne(nodeId);

        if (existingNode.getBody().isDraft()) {
            // We won't need to update any version numbers.
            // Only the original author of the draft is allowed to edit it.

            if (user.getNodeId() != existingNode.getBody().author.getNodeId()) {
                throw new NotAuthorizedException("Not allowed to edit a draft that you did not create.");
            }

            existingNode.getBody().setTitle(title);
            existingNode.getBody().setBody(body);

            existingNode.getSupportingNodes().clear();

            for (Integer id : children) {
                ArgumentNode supportingNode = nodeRepository.findOne(Long.valueOf(id));
                existingNode.supportedBy(supportingNode);
            }

            // Ugh: http://stackoverflow.com/questions/31505729/why-is-my-modified-neo4j-node-property-not-persisted-to-the-db
            neo4jOperations.save(existingNode.getBody());
            nodeRepository.save(existingNode);

            return existingNode;

        } else {

            AssertionBody newBodyVersion = new AssertionBody(title, body, user);
            newBodyVersion.setMajorVersion(existingNode.getBody().getMajorVersion()); // Same major version. Jumping to new major version will be a separate operation.
            VersionHelper.decorateDraftBody(newBodyVersion);

            AssertionNode draftNode = newBodyVersion.constructNode(versionHelper);

            for (Integer id : children) {
                ArgumentNode supportingNode = nodeRepository.findOne(Long.valueOf(id));
                existingNode.supportedBy(supportingNode);
            }


            nodeRepository.save(draftNode);

            return draftNode;
        }
    }
}
