/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.berkeley.ground.dao.models.postgres;

import edu.berkeley.ground.dao.models.GraphVersionFactory;
import edu.berkeley.ground.db.DbClient;
import edu.berkeley.ground.db.DbDataContainer;
import edu.berkeley.ground.db.PostgresClient;
import edu.berkeley.ground.db.PostgresResults;
import edu.berkeley.ground.exceptions.EmptyResultException;
import edu.berkeley.ground.exceptions.GroundException;
import edu.berkeley.ground.model.models.GraphVersion;
import edu.berkeley.ground.model.models.RichVersion;
import edu.berkeley.ground.model.models.Tag;
import edu.berkeley.ground.model.versions.GroundType;
import edu.berkeley.ground.util.IdGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostgresGraphVersionFactory extends GraphVersionFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(PostgresGraphVersionFactory.class);
  private final PostgresClient dbClient;
  private final PostgresGraphFactory graphFactory;
  private final PostgresRichVersionFactory richVersionFactory;

  private final IdGenerator idGenerator;

  /**
   * Constructor for the Postgres graph version factory.
   *
   * @param graphFactory the singleton PostgresGraphFactory
   * @param richVersionFactory the singleton PostgresRichVersionFactory
   * @param dbClient the PostgresClient
   * @param idGenerator a unique ID generator
   */
  public PostgresGraphVersionFactory(PostgresGraphFactory graphFactory,
                                     PostgresRichVersionFactory richVersionFactory,
                                     PostgresClient dbClient,
                                     IdGenerator idGenerator) {
    this.dbClient = dbClient;
    this.graphFactory = graphFactory;
    this.richVersionFactory = richVersionFactory;
    this.idGenerator = idGenerator;
  }

  /**
   * Create and persist a graph version.
   *
   * @param tags tags associated with this graph version
   * @param structureVersionId the id of the StructureVersion associated with this version
   * @param reference an optional external reference
   * @param referenceParameters access parameters for the reference
   * @param graphId the id of the graph containing this version
   * @param edgeVersionIds the list of edge versions in this graph version
   * @param parentIds the ids of the parent(s) of this version
   * @return the created graph version
   * @throws GroundException an error while creating or persisting the graph
   */
  @Override
  public GraphVersion create(Map<String, Tag> tags,
                             long structureVersionId,
                             String reference,
                             Map<String, String> referenceParameters,
                             long graphId,
                             List<Long> edgeVersionIds,
                             List<Long> parentIds) throws GroundException {

    try {
      long id = this.idGenerator.generateVersionId();

      tags = tags.values().stream().collect(Collectors.toMap(Tag::getKey, tag ->
          new Tag(id, tag.getKey(), tag.getValue(), tag.getValueType()))
      );

      this.richVersionFactory.insertIntoDatabase(id, tags, structureVersionId, reference,
          referenceParameters);

      List<DbDataContainer> insertions = new ArrayList<>();
      insertions.add(new DbDataContainer("id", GroundType.LONG, id));
      insertions.add(new DbDataContainer("graph_id", GroundType.LONG, graphId));

      this.dbClient.insert("graph_version", insertions);

      for (long edgeVersionId : edgeVersionIds) {
        List<DbDataContainer> edgeInsertion = new ArrayList<>();
        edgeInsertion.add(new DbDataContainer("graph_version_id", GroundType.LONG, id));
        edgeInsertion.add(new DbDataContainer("edge_version_id", GroundType.LONG, edgeVersionId));

        this.dbClient.insert("graph_version_edge", edgeInsertion);
      }

      this.graphFactory.update(graphId, id, parentIds);

      this.dbClient.commit();
      LOGGER.info("Created graph version " + id + " in graph " + graphId + ".");

      return GraphVersionFactory.construct(id, tags, structureVersionId, reference,
          referenceParameters, graphId, edgeVersionIds);
    } catch (GroundException e) {
      this.dbClient.abort();

      throw e;
    }
  }

  /**
   * Retrieve a graph version from the database.
   *
   * @param id the id of the graph version to retrieve
   * @return the retrieved graph version
   * @throws GroundException either the graph version doesn't exist or couldn't be retrieved
   */
  @Override
  public GraphVersion retrieveFromDatabase(long id) throws GroundException {
    try {
      final RichVersion version = this.richVersionFactory.retrieveFromDatabase(id);

      List<DbDataContainer> predicates = new ArrayList<>();
      predicates.add(new DbDataContainer("id", GroundType.LONG, id));

      List<DbDataContainer> edgePredicate = new ArrayList<>();
      edgePredicate.add(new DbDataContainer("graph_version_id", GroundType.LONG, id));

      PostgresResults resultSet;
      try {
        resultSet = this.dbClient.equalitySelect("graph_version", DbClient.SELECT_STAR, predicates);
      } catch (EmptyResultException e) {
        throw new GroundException("No GraphVersion found with id " + id + ".");
      }

      long graphId = resultSet.getLong(2);

      PostgresResults edgeSet;
      List<Long> edgeVersionIds = new ArrayList<>();
      try {
        edgeSet = this.dbClient.equalitySelect("graph_version_edge", DbClient.SELECT_STAR,
            edgePredicate);

        do {
          edgeVersionIds.add(edgeSet.getLong(2));
        } while (edgeSet.next());
      } catch (EmptyResultException e) {
        // do nothing; this just means that there are no edges in the GraphVersion
      }

      this.dbClient.commit();
      LOGGER.info("Retrieved graph version " + id + " in graph " + graphId + ".");

      return GraphVersionFactory.construct(id, version.getTags(), version.getStructureVersionId(),
          version.getReference(), version.getParameters(), graphId, edgeVersionIds);
    } catch (GroundException e) {
      this.dbClient.abort();

      throw e;
    }
  }
}
