package examples;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.queryparser.classic.ParseException;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.DataPolicy;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionFactory;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolManager;
import org.apache.geode.cache.execute.Execution;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.cache.execute.ResultCollector;
import org.apache.geode.cache.lucene.LuceneIndex;
import org.apache.geode.cache.lucene.LuceneQuery;
import org.apache.geode.cache.lucene.LuceneQueryException;
import org.apache.geode.cache.lucene.LuceneResultStruct;
import org.apache.geode.cache.lucene.LuceneService;
import org.apache.geode.cache.lucene.LuceneServiceProvider;
import org.apache.geode.cache.lucene.PageableLuceneQueryResults;
import org.apache.geode.distributed.ServerLauncher;
import org.apache.geode.distributed.ServerLauncher.Builder;
import org.apache.geode.pdx.JSONFormatter;
import org.apache.geode.pdx.PdxInstance;

public class Main {
  ServerLauncher serverLauncher;
  GemFireCache cache;
  Region PersonRegion;
  LuceneService service;
  static int serverPort = 50505;
  static boolean useLocator = false; 
  

  final static int ENTRY_COUNT = 1000;

  final static int SERVER_WITH_FEEDER = 1;
  final static int SERVER_ONLY = 2;
  final static int CLIENT = 3;
  final static int SERVER_WITH_CLUSTER_CONFIG = 4;
  static int instanceType = SERVER_WITH_FEEDER;
  
  /* Usage: ~ [1|2|3|4 [isUsingLocator]]
   * 1: server with feeder
   * 2: server only 
   * 3: client
   * 4: server with feeder using cluster config
   */
  public static void main(final String[] args) throws LuceneQueryException, IOException, InterruptedException, java.text.ParseException {
    Main prog = new Main();
    try {
      if (args.length > 0) {
        instanceType = Integer.valueOf(args[0]);
      }
      if (args.length > 1) {
        useLocator = Boolean.valueOf(args[1]);
      }
      serverPort += instanceType;
      
      switch (instanceType) {
        case CLIENT:
          // create client cache and proxy regions
          // do query
          prog.createClientCache();
          prog.doQuery();
          prog.doClientFunction();
          break;

        case SERVER_WITH_CLUSTER_CONFIG:
          // create cache without region
          // do feed
          // do query
          prog.createCache(serverPort);
          prog.getRegions();

          prog.feed(ENTRY_COUNT);
          prog.waitUntilFlushed("personIndex", "Person");
          prog.waitUntilFlushed("analyzerIndex", "Person");

          prog.doQuery();
          break;

        case SERVER_WITH_FEEDER:
          // create cache, create index, create region
          // do feed
          // do query
          prog.createCache(serverPort);
          prog.createIndexAndRegions(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT);        

          prog.feed(ENTRY_COUNT);
          prog.waitUntilFlushed("personIndex", "Person");
          prog.waitUntilFlushed("analyzerIndex", "Person");
          
          prog.doQuery();
          break;
      
        case SERVER_ONLY:
          // create cache, create index, create region
          prog.createCache(serverPort);
          prog.createIndexAndRegions(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT);
          break;
      }
      
      System.out.println("Press any key to exit");
      int c = System.in.read();

    } finally {
      prog.stopServer();
    }
    return;
  }

  private void createClientCache() {
    ClientCacheFactory cf = new ClientCacheFactory().addPoolLocator("localhost", 12345);
    cache = cf.setPdxReadSerialized(true).create();
    ClientRegionFactory crf = ((ClientCache)cache).createClientRegionFactory(ClientRegionShortcut.CACHING_PROXY);
    PersonRegion = crf.create("Person");
    
    service = LuceneServiceProvider.get(cache);
  }
  
  private void createCache(int port) {
    if (cache != null) {
      return;
    }

    System.out.println("GEMFIRE="+System.getenv("GEMFIRE"));
    Builder builder = new ServerLauncher.Builder()
        .setMemberName("server"+port)
        .setServerPort(port)
        .setPdxPersistent(true)
        .set("mcast-port", "0")
        .set("enable-time-statistics","true")
        .set("statistic-sample-rate","1000")
        .set("statistic-sampling-enabled", "true")
        .set("statistic-archive-file", "server1.gfs");
    //        .set("log-level", "debug")
    ;

    if (useLocator && instanceType != CLIENT) {
      builder.set("locators", "localhost[12345]");
    }
    serverLauncher  = builder.build();

    serverLauncher.start();
    System.out.println("Server started!");

    cache = CacheFactory.getAnyInstance();
    FunctionService.registerFunction(new LuceneSearchIndexFunction());
    final Map<String, Function> registeredFunctions = FunctionService.getRegisteredFunctions();
    for (String s:registeredFunctions.keySet()) {
      System.out.println("registered function:"+s);
    }
    service = LuceneServiceProvider.get(cache);
  }

  private void getRegions() {
    PersonRegion = cache.getRegion("/Person");
  }
  
  private void stopServer() {
    if (serverLauncher != null) {
      serverLauncher.stop();
      serverLauncher = null;
      System.out.println("server is stopped");
    }
  }

  private void createIndexAndRegions(RegionShortcut shortcut) {
    // create an index using several analyzers on region /Person
    Map<String, Analyzer> fields = new HashMap<String, Analyzer>();
    fields.put("name",  new DoubleMetaphoneAnalyzer());
    fields.put("email", new KeywordAnalyzer());
    service.createIndexFactory().setFields(fields).create("analyzerIndex", "Person");

    // create an index using standard analyzer on region /Person
    service.createIndexFactory().setFields("name", "email", "address", "revenue").create("personIndex", "Person");
    
    // Create the region after the Lucene indexes have been defined.
    PersonRegion = ((Cache)cache).createRegionFactory(shortcut).create("Person");
  }

  public void doQuery() throws LuceneQueryException {
    System.out.println("Regular query on standard analyzer:");
    queryByStringQueryParser("personIndex", "Person", "name:Tom99*", 5);

    System.out.println("\nFuzzy search examples:");
    queryByStringQueryParser("personIndex", "Person", "name:Tom999*", 0);
    queryByStringQueryParser("personIndex", "Person", "name:Tom999~", 0);
    queryByStringQueryParser("personIndex", "Person", "name:Tom999~0.8", 10);

    System.out.println("\nProximity search within 1 word of each other:");
    queryByStringQueryParser("personIndex", "Person", "address:\"999 Portland OR 97999\"~1", 0);
    
    System.out.println("\nProximity search within 3 words of each other:");
    queryByStringQueryParser("personIndex", "Person", "address:\"999 Portland OR 97999\"~3", 0);

    System.out.println("\nQuery with composite condition");
    queryByStringQueryParser("personIndex", "Person", "name:Tom999* OR address:97763", 0);

    System.out.println("\nUse custom QueryProvider for integer range query");
    queryByIntRange("personIndex", "Person", "revenue", 995, 10000);
    
    System.out.println("\nQuery using soundex analyzer: double metaphone");
    insertSoundexNames(PersonRegion);
    try {
      waitUntilFlushed("personIndex", "Person");
    }
    catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    queryByStringQueryParser("analyzerIndex", "Person", "name:Stephen", 5);
    queryByStringQueryParser("analyzerIndex", "Person", "name:Steve", 5);

  }
  
  public void doClientFunction() {
    System.out.println("\nCalling function from a client");
    Pool pool = PoolManager.createFactory().addLocator("localhost", 12345).create("clientPool");

    LuceneQueryInfo queryInfo = new LuceneQueryInfo("personIndex", "Person", "name:Tom99*", "name", -1, false); 
    Execution execution = FunctionService.onServer(pool).withArgs(queryInfo);
    // Client code can call function via its name, it does not need to know function class object
    ResultCollector<?,?> rc = execution.execute("LuceneSearchIndexFunction");
    displayResults(rc);
    
    queryInfo = new LuceneQueryInfo("analyzerIndex", "/Person", "address:97763", "name", -1, false);
    execution = FunctionService.onServer(pool).withArgs(queryInfo);
    rc = execution.execute("LuceneSearchIndexFunction");
    displayResults(rc);
  }
  
  private void displayResults(ResultCollector<?,?> rc) {
    ArrayList functionResults = (ArrayList)((ArrayList)rc.getResult()).get(0);
    
    System.out.println("\nClient Function found "+functionResults.size()+" results");
    functionResults.stream().forEach(result -> {
      System.out.println(result);
    });
  }
  
  private void waitUntilFlushed(String indexName, String regionName) throws InterruptedException {
    LuceneIndex index = service.getIndex(indexName, regionName);
    if (index == null) {
      // it's a client
      return;
    }
    boolean status = false;
    long then = System.currentTimeMillis();
    do {
      status = service.waitUntilFlushed(indexName, regionName, 60000, TimeUnit.MILLISECONDS);
    } while (status == false);
    System.out.println("wait time after feed is:"+(System.currentTimeMillis() - then));
  }

  private void feed(int count) {
    for (int i=0; i<count; i++) {
      PersonRegion.put("key"+i, new Person(i));
    }
    insertAJson(PersonRegion);
  }

  private void insertSoundexNames(Region region) {
    region.put("soundex1", new Person("Stefan", "a@b.com", "address1"));
    region.put("soundex2", new Person("Steph", "a@b.com", "address1"));
    region.put("soundex3", new Person("Stephen", "a@b.com", "address1"));
    region.put("soundex4", new Person("Steve", "a@b.com", "address1"));
    region.put("soundex5", new Person("Steven", "a@b.com", "address1"));
    region.put("soundex6", new Person("Stove", "a@b.com", "address1"));
    region.put("soundex7", new Person("Stuffin", "a@b.com", "address1"));
  }
  
  private void insertAJson(Region region) {
    String jsonPerson1 = "{"
        + "\"name\": \"Tom9_JSON\","
        + "\"lastName\": \"Smith\","
        + " \"age\": 25,"
        + " \"revenue\": 4000,"
        + "\"address\":"
        + "{"
        + "\"streetAddress\": \"21 2nd Street\","
        + "\"city\": \"New York\","
        + "\"state\": \"NY\","
        + "\"postalCode\": \"10021\""
        + "},"
        + "\"phoneNumber\":"
        + "["
        + "{"
        + " \"type\": \"home\","
        + "\"number\": \"212 555-1234\""
        + "},"
        + "{"
        + " \"type\": \"fax\","
        + "\"number\": \"646 555-4567\""
        + "}"
        + "]"
        + "}";

    region.put("jsondoc1", JSONFormatter.fromJSON(jsonPerson1));
    System.out.println("JSON documents added into Cache: " + jsonPerson1);
    System.out.println(region.get("jsondoc1"));
    System.out.println();

    String jsonPerson2 = "{"
        + "\"name\": \"Tom99_JSON\","
        + "\"lastName\": \"Smith\","
        + " \"age\": 25,"
        + " \"revenue\": 4001,"
        + "\"address\":"
        + "{"
        + "\"streetAddress\": \"21 2nd Street\","
        + "\"city\": \"New York\","
        + "\"state\": \"NY\","
        + "\"postalCode\": \"10021\""
        + "},"
        + "\"phoneNumber\":"
        + "["
        + "{"
        + " \"type\": \"home\","
        + "\"number\": \"212 555-1234\""
        + "},"
        + "{"
        + " \"type\": \"fax\","
        + "\"number\": \"646 555-4567\""
        + "}"
        + "]"
        + "}";
    region.put("jsondoc2", JSONFormatter.fromJSON(jsonPerson2));
    System.out.println("JSON documents added into Cache: " + jsonPerson2);
    System.out.println(region.get("jsondoc2"));
    System.out.println();
  }

  private HashSet getResults(LuceneQuery query, String regionName) throws LuceneQueryException {
    if (query == null) {
      return null;
    }

    PageableLuceneQueryResults<Object, Object> results = query.findPages();
    if (results.size() >0 ) {
      System.out.println("Search found "+results.size()+" rows in "+regionName);
    }

    HashSet values = new HashSet<>();
    int pageno = 0;
    if (results.size() < 20) {
    final AtomicInteger cnt = new AtomicInteger(0);
    while(results.hasNext()) {
      if (query.getPageSize() != 0) {
        System.out.println("Page:"+pageno+" starts here ------------");
      }
      results.next().stream()
      .forEach(struct -> {
        Object value = struct.getValue();
        if (value instanceof PdxInstance) {
          PdxInstance pdx = (PdxInstance)value;
          String jsonString = JSONFormatter.toJSON(pdx);
          System.out.println("Found a json object:"+jsonString);
          values.add(pdx);
        } else {
          System.out.println("No: "+cnt.get()+":key="+struct.getKey()+",value="+value+",score="+struct.getScore());
          values.add(value);
        }
        cnt.incrementAndGet();
      });
      if (query.getPageSize() != 0) {
        System.out.println("Page:"+pageno+" ends here, press any key to show next page ------------");
        try {
          int c = System.in.read();
        }
        catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
      pageno++;
    }
    }
    return values;
  }

  private HashSet queryByStringQueryParser(String indexName, String regionName, String queryString, int pageSize) throws LuceneQueryException {
    System.out.println("\nQuery string is:"+queryString);
    LuceneQuery query = service.createLuceneQueryFactory().setPageSize(pageSize).create(indexName, regionName, queryString, "name");

    return getResults(query, regionName);
  }

  private void queryByIntRange(String indexName, String regionName, String fieldName, int lowerValue, int upperValue) throws LuceneQueryException {
    System.out.println("\nQuery range is:"+fieldName+":["+lowerValue+" TO "+upperValue+"]");
    IntRangeQueryProvider provider = new IntRangeQueryProvider(fieldName, lowerValue, upperValue);
    LuceneQuery query = service.createLuceneQueryFactory().create(indexName, regionName, provider);

    getResults(query, regionName);
  }
}
