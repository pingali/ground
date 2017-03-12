/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.berkeley.ground.api.models.postgres;

import edu.berkeley.ground.api.models.*;
import edu.berkeley.ground.api.versions.GroundType;
import edu.berkeley.ground.db.DBClient;
import edu.berkeley.ground.db.DbDataContainer;
import edu.berkeley.ground.db.PostgresClient;
import edu.berkeley.ground.db.QueryResults;
import edu.berkeley.ground.exceptions.EmptyResultException;
import edu.berkeley.ground.exceptions.GroundDBException;
import edu.berkeley.ground.util.IdGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PostgresEdgeVersionFactory extends EdgeVersionFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(PostgresEdgeVersionFactory.class);
  private final PostgresClient dbClient;
  private final PostgresEdgeFactory edgeFactory;
  private final PostgresRichVersionFactory richVersionFactory;

  private final IdGenerator idGenerator;

  public PostgresEdgeVersionFactory(PostgresEdgeFactory edgeFactory, PostgresRichVersionFactory richVersionFactory, PostgresClient dbClient, IdGenerator idGenerator) {
    this.dbClient = dbClient;
    this.edgeFactory = edgeFactory;
    this.richVersionFactory = richVersionFactory;
    this.idGenerator = idGenerator;
  }

  public EdgeVersion create(Map<String, Tag> tags,
                            long structureVersionId,
                            String reference,
                            Map<String, String> referenceParameters,
                            long edgeId,
                            long fromId,
                            long toId,
                            List<Long> parentIds) throws GroundDBException {

    try {
      long id = this.idGenerator.generateVersionId();

      tags = tags.values().stream().collect(Collectors.toMap(Tag::getKey, tag -> new Tag(id, tag.getKey(), tag.getValue(), tag.getValueType())));

      this.richVersionFactory.insertIntoDatabase(id, tags, structureVersionId, reference, referenceParameters);

      List<DbDataContainer> insertions = new ArrayList<>();
      insertions.add(new DbDataContainer("id", GroundType.LONG, id));
      insertions.add(new DbDataContainer("edge_id", GroundType.LONG, edgeId));
      insertions.add(new DbDataContainer("from_node_version_id", GroundType.LONG, fromId));
      insertions.add(new DbDataContainer("to_node_version_id", GroundType.LONG, toId));

      this.dbClient.insert("edge_version", insertions);

      this.edgeFactory.update(edgeId, id, parentIds);

      this.dbClient.commit();
      LOGGER.info("Created edge version " + id + " in edge " + edgeId + ".");

      return EdgeVersionFactory.construct(id, tags, structureVersionId, reference, referenceParameters, edgeId, fromId, toId);
    } catch (GroundDBException e) {
      this.dbClient.abort();
      throw e;
    }
  }

  public EdgeVersion retrieveFromDatabase(long id) throws GroundDBException {
    try {
      RichVersion version = this.richVersionFactory.retrieveFromDatabase(id);

      List<DbDataContainer> predicates = new ArrayList<>();
      predicates.add(new DbDataContainer("id", GroundType.LONG, id));

      QueryResults resultSet;
      try {
        resultSet = this.dbClient.equalitySelect("edge_version", DBClient.SELECT_STAR, predicates);
      } catch (EmptyResultException e) {
        throw new GroundDBException("No EdgeVersion found with id " + id + ".");
      }
      long edgeId = resultSet.getLong(2);
      long fromId = resultSet.getLong(3);
      long toId = resultSet.getLong(4);

      this.dbClient.commit();
      LOGGER.info("Retrieved edge version " + id + " in edge " + edgeId + ".");

      return EdgeVersionFactory.construct(id, version.getTags(), version.getStructureVersionId(), version.getReference(), version.getParameters(), edgeId, fromId, toId);
    } catch (GroundDBException e) {
      this.dbClient.abort();

      throw e;
    }
  }
}
