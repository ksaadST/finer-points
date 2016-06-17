package com.nodestand.service;

import com.nodestand.nodes.ArgumentBody;
import com.nodestand.nodes.ArgumentNode;
import com.nodestand.nodes.NodeRulesException;
import com.nodestand.nodes.assertion.AssertionNode;
import com.nodestand.nodes.interpretation.InterpretationNode;
import com.nodestand.nodes.repository.ArgumentBodyRepository;
import com.nodestand.nodes.repository.ArgumentNodeRepository;
import com.nodestand.nodes.source.SourceNode;
import com.nodestand.nodes.version.MajorVersion;
import com.nodestand.nodes.version.VersionAggregator;
import com.nodestand.util.TwoWayUtil;
import org.neo4j.ogm.session.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.template.Neo4jOperations;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class VersionHelper {

    @Autowired
    ArgumentNodeRepository nodeRepository;

    @Autowired
    ArgumentBodyRepository bodyRepository;

    @Autowired
    Neo4jOperations operations;

    @Autowired
    Session session;

    /**
     * This sets the major and minor version on the draft body.
     * @param body
     * @return
     */
    public static void decorateDraftBody(ArgumentBody body) {

        if (body.getMajorVersion() == null) {
            // If the major version is null, we assume this is a brand new body, i.e. version 1.0.0.
            // Normally we would wait until publish to set the version numbers, but we already know what they are.
            // Doing it eagerly because why not.
            MajorVersion mv = new MajorVersion(1, new VersionAggregator());
            body.setVersion(mv, 0);
        } else {
            // A minor version of -1 is set for all drafts. It will receive a real version number later
            // when it is published.
            body.setMinorVersion(-1);
        }


    }

    private int getNextMinorVersion(MajorVersion majorVersion) {

        Integer max = nodeRepository.getMaxMinorVersion(majorVersion.getId());

        if (max != null) {
            return max + 1;
        }

        return 0;
    }

    public ArgumentNode publish(ArgumentNode node) throws NodeRulesException {
        if (!node.getType().equals("source")) {
            // validate that the node and its descendants follow all the rules, e.g. being grounded in sources
            if (hasMissingSupport(node)) {
                throw new NodeRulesException("The roots of this node do not all end in sources!");
            }
            publishDescendants(node);
        }

        ArgumentNode resultingNode = node;

        ArgumentNode previousVersion = node.getPreviousVersion();
        if (previousVersion != null && !previousVersion.isFinalized()) {
            // previous version is the edit target.

            // Make sure it's loaded
            if (previousVersion.getBody() == null) {
                operations.load(previousVersion.getClass(), previousVersion.getId(), 1);
            }

            // Copy links to children into the previous version
            node.copyContentTo(previousVersion);

            previousVersion.setBody(node.getBody());

            ArgumentBody freshlyPublishedBody = previousVersion.getBody();
            freshlyPublishedBody.setDateEdited(new Date());
            freshlyPublishedBody.setIsPublic(true);

            resultingNode = previousVersion;

            // Any parents that had pointed to the draft should be modified so that they point to the
            // published version. The draft is going away.
            Set<? extends ArgumentNode> dependentNodes = node.getDependentNodes();
            if (dependentNodes != null) {
                for (ArgumentNode parent : node.getDependentNodes()) {
                    parent.alterToPointToChild(resultingNode, node);
                }
            }

            // Destroy the current version
            operations.delete(node);

            TwoWayUtil.forgetNode(node);
        }

        ArgumentBody body = resultingNode.getBody();
        if (body.getMinorVersion() < 0) {
            body.setMinorVersion(getNextMinorVersion(body.getMajorVersion()));
        }

        body.setIsPublic(true);

        operations.save(resultingNode);

        return resultingNode;
    }

    private void publishDescendants(ArgumentNode node) throws NodeRulesException {

        if (node instanceof AssertionNode) {
            AssertionNode assertion = (AssertionNode) node;

            // If assertion.getSupportingNodes returns null, this will go awry. Keep an eye on it.
            Collection<ArgumentNode> support = session.loadAll(ArgumentNode.class,
                    assertion.getSupportingNodes().stream().map(ArgumentNode::getId).collect(Collectors.toList()), 1);

            for (ArgumentNode childNode : support) {
                if (!childNode.getBody().isPublic()) {
                    publish(childNode);
                }
            }
        } else if (node instanceof InterpretationNode) {
            InterpretationNode interpretation = (InterpretationNode) node;

            // If interpretation.getSource returns null, this will go awry. Keep an eye on it.
            operations.load(SourceNode.class, interpretation.getSource().getId());

            if (!interpretation.getSource().getBody().isPublic()) {
                publish(interpretation.getSource());
            }
        }
    }

    private boolean hasMissingSupport(ArgumentNode node) {
        // TODO: consider making the isFinalized attribute belong to nodes instead of bodies so that we can easily
        // short-circuit these queries.

        Set<ArgumentNode> unsupportedNodes = nodeRepository.getUnsupportedNodes(node.getId());

        return !unsupportedNodes.isEmpty();
    }
}
