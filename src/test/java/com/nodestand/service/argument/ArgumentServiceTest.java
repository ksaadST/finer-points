package com.nodestand.service.argument;

import com.nodestand.auth.NotAuthorizedException;
import com.nodestand.controllers.serial.EditResult;
import com.nodestand.nodes.ArgumentNode;
import com.nodestand.nodes.NodeRulesException;
import com.nodestand.nodes.User;
import com.nodestand.nodes.assertion.AssertionNode;
import com.nodestand.nodes.interpretation.InterpretationNode;
import com.nodestand.nodes.source.SourceNode;
import com.nodestand.service.user.UserService;
import com.nodestand.test.Neo4jIntegrationTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.LinkedList;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
public class ArgumentServiceTest extends Neo4jIntegrationTest {

    @Autowired
    private ArgumentService argumentService;

    @Autowired
    private UserService userService;

    private User registerUser(String socialId, String name) {
        return userService.register(socialId, name).getUser();
    }

    @Test
    public void createAssertionTest() throws NodeRulesException {

        User jim = registerUser("1234", "Jim");

        List<Long> links = new LinkedList<>();

        AssertionNode assertionNode = argumentService.createAssertion(jim.getNodeId(), "Test Title", "Hello, world!", links);

        Assert.assertNotNull(assertionNode);
        Assert.assertFalse(assertionNode.isFinalized());
        Assert.assertFalse(assertionNode.getBody().isPublic());
        Assert.assertTrue(assertionNode.getBody().isEditable());
        Assert.assertEquals("Test Title", assertionNode.getBody().getTitle());
        Assert.assertEquals("Hello, world!", assertionNode.getBody().getBody());

    }

    @Test
    public void publishTest() throws NodeRulesException, NotAuthorizedException {

        AssertionNode assertionNode = createPublishedAssertion();

        Assert.assertTrue(assertionNode.getBody().isPublic());
        Assert.assertTrue(assertionNode.getBody().isEditable());
    }


    @Test
    public void editingPublishedNodeTest() throws NotAuthorizedException, NodeRulesException {

        User kyle = registerUser("5678", "Kyle");
        AssertionNode assertionNode = createPublishedAssertion();

        try {
            argumentService.editAssertion(kyle.getNodeId(), assertionNode.getId(), "Title", "Body", new LinkedList<>());
            Assert.fail("Should have thrown an exception because you can't edit a published node directly.");
        } catch (NodeRulesException e) {
            // Good.
        }
    }

    @Test
    public void testEditingViaDraft() throws NotAuthorizedException, NodeRulesException {
        User kyle = registerUser("5678", "Kyle");
        AssertionNode assertionNode = createPublishedAssertion();

        EditResult result = argumentService.makeDraft(kyle.getNodeId(), assertionNode.getId(), assertionNode.getStableId());

        Assert.assertNotEquals(assertionNode.getId(), result.getEditedNode().getId());
        Assert.assertEquals(result.getEditedNode().getId(), result.getGraph().getRootId());
        Assert.assertFalse(result.getEditedNode().getBody().isPublic());

        ArgumentNode child = assertionNode.getSupportingNodes().stream().findFirst().get();
        List<Long> links = new LinkedList<>();
        links.add(child.getId());
        String body = "New Body {{[" + child.getBody().getMajorVersion().getStableId() + "]link}}";
        AssertionNode edited = argumentService.editAssertion(kyle.getNodeId(), result.getEditedNode().getId(), "New Title", body, links);

        Assert.assertFalse(edited.getBody().isPublic());
        Assert.assertEquals(assertionNode.getId(), edited.getPreviousVersion().getId());

        AssertionNode resultingNode = (AssertionNode) argumentService.publishNode(kyle.getNodeId(), edited.getId());

        Assert.assertEquals(assertionNode.getId(), resultingNode.getId());
        Assert.assertEquals("New Title", resultingNode.getBody().getTitle());
        Assert.assertEquals(body, resultingNode.getBody().getBody());
        Assert.assertTrue(resultingNode.getBody().isEditable());
        Assert.assertTrue(resultingNode.getBody().isPublic());
    }

    @Test
    public void testChildDraftCreation() throws NotAuthorizedException, NodeRulesException {
        User kyle = registerUser("5678", "Kyle");
        AssertionNode assertionNode = createPublishedAssertion();

        InterpretationNode interp = (InterpretationNode) assertionNode.getGraphChildren().iterator().next();
        SourceNode sourceNode = interp.getSource();

        EditResult result = argumentService.makeDraft(kyle.getNodeId(), sourceNode.getId(), assertionNode.getStableId());

        Assert.assertNotEquals(assertionNode.getId(), result.getGraph().getRootId()); // Draft creation has propagated to root
        Assert.assertNotEquals(result.getEditedNode().getId(), result.getGraph().getRootId()); // The root was not the subject of editing
        Assert.assertFalse(result.getEditedNode().getBody().isPublic());
    }




    private AssertionNode createPublishedAssertion() throws NodeRulesException, NotAuthorizedException {

        User jim = registerUser("1234", "Jim");
        return ArgumentTestUtil.createPublishedTriple(argumentService, jim);
    }


}
