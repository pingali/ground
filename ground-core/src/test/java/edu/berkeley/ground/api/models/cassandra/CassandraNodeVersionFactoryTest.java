package edu.berkeley.ground.api.models.cassandra;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.berkeley.ground.api.CassandraTest;
import edu.berkeley.ground.api.models.NodeVersion;
import edu.berkeley.ground.api.models.Tag;
import edu.berkeley.ground.api.versions.GroundType;
import edu.berkeley.ground.exceptions.GroundException;

import static org.junit.Assert.*;

public class CassandraNodeVersionFactoryTest extends CassandraTest {

  public CassandraNodeVersionFactoryTest() throws GroundException {
    super();
  }

  @Test
  public void testNodeVersionCreation() throws GroundException {
    String nodeName = "testNode";
    long nodeId = CassandraTest.factories.getNodeFactory()
        .create(nodeName, new HashMap<>()).getId();

    String structureName = "testStructure";
    long structureId = CassandraTest.factories.getStructureFactory()
        .create(structureName, new HashMap<>()).getId();

    Map<String, GroundType> structureVersionAttributes = new HashMap<>();
    structureVersionAttributes.put("intfield", GroundType.INTEGER);
    structureVersionAttributes.put("boolfield", GroundType.BOOLEAN);
    structureVersionAttributes.put("strfield", GroundType.STRING);

    long structureVersionId = CassandraTest.factories.getStructureVersionFactory().create(
        structureId, structureVersionAttributes, new ArrayList<>()).getId();

    Map<String, Tag> tags = new HashMap<>();
    tags.put("intfield", new Tag(-1, "intfield", 1, GroundType.INTEGER));
    tags.put("strfield", new Tag(-1, "strfield", "1", GroundType.STRING));
    tags.put("boolfield", new Tag(-1, "boolfield", true, GroundType.BOOLEAN));

    String testReference = "http://www.google.com";
    Map<String, String> parameters = new HashMap<>();
    parameters.put("http", "GET");

    long nodeVersionId = CassandraTest.factories.getNodeVersionFactory().create(tags,
        structureVersionId, testReference, parameters, nodeId, new ArrayList<>()).getId();

    NodeVersion retrieved = CassandraTest.factories.
        getNodeVersionFactory().
        retrieveFromDatabase(nodeVersionId);

    assertEquals(nodeId, retrieved.getNodeId());
    assertEquals(structureVersionId, retrieved.getStructureVersionId());
    assertEquals(testReference, retrieved.getReference());

    assertEquals(parameters.size(), retrieved.getParameters().size());
    assertEquals(tags.size(), retrieved.getTags().size());

    Map<String, String> retrievedParameters = retrieved.getParameters();
    Map<String, Tag> retrievedTags = retrieved.getTags();

    for (String key : parameters.keySet()) {
      assert (retrievedParameters).containsKey(key);
      assertEquals(parameters.get(key), retrievedParameters.get(key));
    }

    for (String key : tags.keySet()) {
      assert (retrievedTags).containsKey(key);
      assertEquals(tags.get(key), retrievedTags.get(key));
    }

    List<Long> leaves = CassandraTest.factories.getNodeFactory().getLeaves(nodeName);

    assertTrue(leaves.contains(nodeVersionId));
    assertTrue(1 == leaves.size());
  }

  @Test
  public void testTransitiveClosure() throws GroundException {
    String nodeName = "testNode1";
    long nodeId = CassandraTest.factories.getNodeFactory()
        .create(nodeName, new HashMap<>()).getId();

    long nodeVersionId = CassandraTest.factories.getNodeVersionFactory().create(new HashMap<>(),
        -1, null, new HashMap<>(), nodeId, new ArrayList<>()).getId();

    nodeName = "testNode2";
    nodeId = CassandraTest.factories.getNodeFactory().create(nodeName, new HashMap<>()).getId();

    long secondNVId = CassandraTest.factories.getNodeVersionFactory().create(new HashMap<>(), -1,
        null, new HashMap<>(), nodeId, new ArrayList<>()).getId();

    String edgeName = "testEdge1";
    long edgeId = CassandraTest.factories.getEdgeFactory()
        .create(edgeName, new HashMap<>()).getId();

    CassandraTest.factories.getEdgeVersionFactory().create(new HashMap<>(), -1, null, new
        HashMap<>(), edgeId, secondNVId, nodeVersionId, new ArrayList<>()).getId();

    nodeName = "testNode3";
    nodeId = CassandraTest.factories.getNodeFactory().create(nodeName, new HashMap<>()).getId();

    long thirdNVId = CassandraTest.factories.getNodeVersionFactory().create(new HashMap<>(), -1,
        null, new HashMap<>(), nodeId, new ArrayList<>()).getId();


    edgeName = "testEdge3";
    edgeId = CassandraTest.factories.getEdgeFactory().create(edgeName, new HashMap<>()).getId();

    CassandraTest.factories.getEdgeVersionFactory().create(new HashMap<>(), -1, null, new
        HashMap<>(), edgeId, thirdNVId, secondNVId, new ArrayList<>()).getId();


    List<Long> reachable = CassandraTest.factories.getNodeVersionFactory().getTransitiveClosure
        (thirdNVId);

    assertTrue(reachable.contains(nodeVersionId));
    assertTrue(reachable.contains(secondNVId));
  }
}
