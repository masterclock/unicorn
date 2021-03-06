/*******************************************************************************
  * (C) Copyright 2015 ADP, LLC.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  *******************************************************************************/

package unicorn.unibase.graph

import org.specs2.mutable._
import org.specs2.specification.BeforeAfterAll
import unicorn.bigtable.accumulo.Accumulo
import unicorn.json._
import unicorn.oid.Snowflake
import unicorn.unibase._

/**
 * @author Haifeng Li
 */
class GraphOpsSpec extends Specification with BeforeAfterAll {
  // Make sure running examples one by one.
  // Otherwise, test cases on same columns will fail due to concurrency
  sequential
  val bigtable = Accumulo()
  val db = new Unibase(bigtable)
  val graphName = "unicorn_unibase_graph_test"

  implicit val snowflake = new Snowflake(0)

  var saturn = 0L
  var sky = 0L
  var sea = 0L
  var jupiter = 0L
  var neptune = 0L
  var hercules = 0L
  var alcmene = 0L
  var pluto = 0L
  var nemean = 0L
  var hydra = 0L
  var cerberus = 0L
  var tartarus = 0L

  override def beforeAll = {
    db.createGraph(graphName)

    val gods = db.graph(graphName)

    saturn = gods.addVertex(json"""{"label": "titan", "name": "saturn", "age": 10000}""")
    sky = gods.addVertex(json"""{"label": "location", "name": "sky"}""")
    sea = gods.addVertex(json"""{"label": "location", "name": "sea"}""")
    jupiter = gods.addVertex(json"""{"label": "god", "name": "jupiter", "age": 5000}""")
    neptune = gods.addVertex(json"""{"label": "god", "name": "neptune", "age": 4500}""")
    hercules = gods.addVertex(json"""{"label": "demigod", "name": "hercules", "age": 30}""")
    alcmene = gods.addVertex(json"""{"label": "human", "name": "alcmene", "age": 45}""")
    pluto = gods.addVertex(json"""{"label": "god", "name": "pluto", "age": 4000}""")
    nemean = gods.addVertex(json"""{"label": "monster", "name": "nemean"}""")
    hydra = gods.addVertex(json"""{"label": "monster", "name": "hydra"}""")
    cerberus = gods.addVertex(json"""{"label": "monster", "name": "cerberus"}""")
    tartarus = gods.addVertex(json"""{"label": "location", "name": "tartarus"}""")

    gods.addEdge(jupiter, "father", saturn)
    gods.addEdge(jupiter, "lives", sky, json"""{"reason": "loves fresh breezes"}""")
    gods.addEdge(jupiter, "brother", neptune)
    gods.addEdge(jupiter, "brother", pluto)

    gods.addEdge(neptune, "lives", sea, json"""{"reason": "loves waves"}""")
    gods.addEdge(neptune, "brother", jupiter)
    gods.addEdge(neptune, "brother", pluto)

    gods.addEdge(hercules, "father", jupiter)
    gods.addEdge(hercules, "mother", alcmene)
    gods.addEdge(hercules, "battled", nemean, json"""{"time": 1, "place": {"latitude": 38.1, "longitude": 23.7}}""")
    gods.addEdge(hercules, "battled", hydra, json"""{"time": 2, "place": {"latitude": 37.7, "longitude": 23.9}}""")
    gods.addEdge(hercules, "battled", cerberus, json"""{"time": 12, "place": {"latitude": 39.0, "longitude": 22.0}}""")

    gods.addEdge(pluto, "brother", jupiter)
    gods.addEdge(pluto, "brother", neptune)
    gods.addEdge(pluto, "lives", tartarus, json"""{"reason": "no fear of death"}""")
    gods.addEdge(pluto, "pet", cerberus)

    gods.addEdge(cerberus, "lives", tartarus)
  }

  override def afterAll = {
    db.dropGraph(graphName)
  }

  "Graph" should {
    "bfs" in {
      val gods = db.graph(graphName)
      val queue = collection.mutable.Queue[(Long, String, Int)]()

      GraphOps.bfs(jupiter, new SimpleTraveler(gods) {
        override def apply(vertex: Vertex, edge: Option[Edge], hops: Int): Unit = {
          queue.enqueue((vertex.id, edge.map(_.label).getOrElse(""), hops))
        }
      })
      queue.dequeue === (jupiter, "", 0)
      queue.dequeue === (neptune, "brother", 1)
      queue.dequeue === (pluto, "brother", 1)
      queue.dequeue === (saturn, "father", 1)
      queue.dequeue === (sky, "lives", 1)
      queue.dequeue === (sea, "lives", 2)
      queue.dequeue === (tartarus, "lives", 2)
      queue.dequeue === (cerberus, "pet", 2)
      queue.isEmpty === true
    }
    "dfs" in {
      val gods = db.graph(graphName)
      val queue = collection.mutable.Queue[(Long, String, Int)]()

      GraphOps.dfs(jupiter, new SimpleTraveler(gods) {
        override def apply(vertex: Vertex, edge: Option[Edge], hops: Int): Unit = {
          queue.enqueue((vertex.id, edge.map(_.label).getOrElse(""), hops))
        }
      })

      queue.dequeue === (jupiter, "", 0)
      queue.dequeue === (neptune, "brother", 1)
      queue.dequeue === (pluto, "brother", 2)
      queue.dequeue === (tartarus, "lives", 3)
      queue.dequeue === (cerberus, "pet", 3)
      queue.dequeue === (sea, "lives", 2)
      queue.dequeue === (saturn, "father", 1)
      queue.dequeue === (sky, "lives", 1)
      queue.isEmpty === true
    }
    "dijkstra" in {
      val gods = db.graph(graphName)

      val jupiter2cerberus = GraphOps.dijkstra(jupiter, cerberus, new SimpleTraveler(gods)).map { edge =>
        (edge.from, edge.label, edge.to)
      }
      jupiter2cerberus.size === 2
      jupiter2cerberus(0) === (jupiter, "brother", pluto)
      jupiter2cerberus(1) === (pluto, "pet", cerberus)

      val hercules2tartarus = GraphOps.dijkstra(hercules, tartarus, new SimpleTraveler(gods)).map { edge =>
        (edge.from, edge.label, edge.to)
      }
      hercules2tartarus.size === 3
      hercules2tartarus(0) === (hercules, "father", jupiter)
      hercules2tartarus(1) === (jupiter, "brother", pluto)
      hercules2tartarus(2) === (pluto, "lives", tartarus)

      GraphOps.dijkstra(saturn, sky, new SimpleTraveler(gods)) === List.empty
    }
    "a* search" in {
      val gods = db.graph(graphName)

      val path = GraphOps.astar(jupiter, cerberus, new SimpleTraveler(gods) with AstarTraveler {
        override  def h(v1: Long, v2: Long): Double = {
          val node1 = vertex(v1)
          val node2 = vertex(v2)

          if (!node1.edges.filter(_.to == v2).isEmpty) return 1.0

          6 - node1.properties.fields.keySet.intersect(node2.properties.fields.keySet).size
        }
      }).map { edge =>
        (edge.from, edge.label, edge.to)
      }

      path.size === 2
      path(0) === (jupiter, "brother", pluto)
      path(1) === (pluto, "pet", cerberus)
    }
  }
}
