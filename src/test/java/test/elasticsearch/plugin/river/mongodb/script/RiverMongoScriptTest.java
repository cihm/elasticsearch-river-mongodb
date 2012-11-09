package test.elasticsearch.plugin.river.mongodb.script;

import static org.elasticsearch.client.Requests.countRequest;
import static org.elasticsearch.common.io.Streams.copyToStringFromClasspath;
import static org.elasticsearch.index.query.QueryBuilders.fieldQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.admin.indices.exists.IndicesExistsRequest;
import org.elasticsearch.action.admin.indices.exists.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import test.elasticsearch.plugin.river.mongodb.RiverMongoDBTestAsbtract;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import com.mongodb.util.JSON;

@Test
public class RiverMongoScriptTest extends RiverMongoDBTestAsbtract {

	private final ESLogger logger = Loggers.getLogger(getClass());

	private static final String DATABASE_NAME = "testscript";
	private static final String COLLECTION_NAME = "documents";
	private static final String RIVER_NAME = "testscript";
	private static final String INDEX_NAME = "documentsindex";

	private DB mongoDB;
	private DBCollection mongoCollection;

	protected RiverMongoScriptTest() {
		super(RIVER_NAME, DATABASE_NAME, COLLECTION_NAME, INDEX_NAME);
	}

	@BeforeClass
	public void createDatabase() {
		logger.debug("createDatabase {}", DATABASE_NAME);
		try {
			mongoDB = getMongo().getDB(DATABASE_NAME);
//			logger.debug("Create river {}", RIVER_NAME);
//			super.createRiver("test-mongodb-river-with-script.json", String.valueOf(getMongoPort1()), String.valueOf(getMongoPort2()), String.valueOf(getMongoPort3()), DATABASE_NAME, COLLECTION_NAME, SCRIPT, INDEX_NAME);
			logger.info("Start createCollection");
			mongoCollection = mongoDB.createCollection(COLLECTION_NAME, null);
			Assert.assertNotNull(mongoCollection);
		} catch (Throwable t) {
			logger.error("createDatabase failed.", t);
		}
	}

	@AfterClass
	public void cleanUp() {
//		super.deleteRiver();
		logger.info("Drop database " + mongoDB.getName());
		mongoDB.dropDatabase();
	}

	@Test
	public void testIgnoreScript() throws Throwable {
		logger.debug("Start testIgnoreScript");
		try {
			logger.debug("Create river {}", RIVER_NAME);
			String script = "ctx.ignore = true;";
			super.createRiver("/test/elasticsearch/plugin/river/mongodb/script/test-mongodb-river-with-script.json", String.valueOf(getMongoPort1()), String.valueOf(getMongoPort2()), String.valueOf(getMongoPort3()), DATABASE_NAME, COLLECTION_NAME, script, INDEX_NAME);

			String mongoDocument = copyToStringFromClasspath("/test/elasticsearch/plugin/river/mongodb/script/test-simple-mongodb-document.json");
			DBObject dbObject = (DBObject) JSON.parse(mongoDocument);
			WriteResult result = mongoCollection.insert(dbObject,
					WriteConcern.REPLICAS_SAFE);
			Thread.sleep(1000);
			logger.info("WriteResult: {}", result.toString());
			getNode().client().admin().indices()
					.refresh(new RefreshRequest(INDEX_NAME));
			ActionFuture<IndicesExistsResponse> response = getNode().client()
					.admin().indices()
					.exists(new IndicesExistsRequest(INDEX_NAME));
			assertThat(response.actionGet().isExists(), equalTo(true));
			CountResponse countResponse = getNode()
					.client()
					.count(countRequest(INDEX_NAME)).actionGet();
			logger.info("Document count: {}", countResponse.count());
			assertThat(countResponse.count(), equalTo(0l));

			mongoCollection.remove(dbObject, WriteConcern.REPLICAS_SAFE);

		} catch (Throwable t) {
			logger.error("testIgnoreScript failed.", t);
			t.printStackTrace();
			throw t;
		} finally {
			super.deleteRiver();
			super.deleteIndex();
		}
	}

	@Test
	public void testUpdateAttribute() throws Throwable {
		logger.debug("Start testUpdateAttribute");
		try {
			logger.debug("Create river {}", RIVER_NAME);
			String script = "ctx.document.score = 200;";
			super.createRiver("test-mongodb-river-with-script.json", String.valueOf(getMongoPort1()), String.valueOf(getMongoPort2()), String.valueOf(getMongoPort3()), DATABASE_NAME, COLLECTION_NAME, script, INDEX_NAME);

			String mongoDocument = copyToStringFromClasspath("/test/elasticsearch/plugin/river/mongodb/script/test-simple-mongodb-document.json");
			DBObject dbObject = (DBObject) JSON.parse(mongoDocument);
			WriteResult result = mongoCollection.insert(dbObject,
					WriteConcern.REPLICAS_SAFE);
			Thread.sleep(1000);
			String id = dbObject.get("_id").toString();
			logger.info("WriteResult: {}", result.toString());
			getNode().client().admin().indices()
					.refresh(new RefreshRequest(INDEX_NAME).waitForOperations(true));
			ActionFuture<IndicesExistsResponse> response = getNode().client()
					.admin().indices()
					.exists(new IndicesExistsRequest(INDEX_NAME));
			assertThat(response.actionGet().isExists(), equalTo(true));

			SearchResponse sr = getNode().client().prepareSearch(INDEX_NAME).setQuery(fieldQuery("_id", id)).execute().actionGet();
			logger.debug("SearchResponse {}", sr.toString());
			long totalHits = sr.hits().getTotalHits();
			logger.debug("TotalHits: {}", totalHits);
			assertThat(totalHits, equalTo(1l));
			
			assertThat(sr.getHits().getHits()[0].sourceAsMap().containsKey("score"), equalTo(true));
			int score = Integer.parseInt(sr.getHits().getHits()[0].sourceAsMap().get("score").toString());
			
			logger.debug("Score: {}", score);
			assertThat(score, equalTo(200));
			
			mongoCollection.remove(dbObject, WriteConcern.REPLICAS_SAFE);

		} catch (Throwable t) {
			logger.error("testUpdateAttribute failed.", t);
			t.printStackTrace();
			throw t;
		} finally {
			super.deleteRiver();
			super.deleteIndex();
		}
	}
}
