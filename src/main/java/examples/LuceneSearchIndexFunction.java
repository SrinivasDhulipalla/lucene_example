package examples;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionContext;
import org.apache.geode.cache.lucene.LuceneIndex;
import org.apache.geode.cache.lucene.LuceneQuery;
import org.apache.geode.cache.lucene.LuceneQueryException;
import org.apache.geode.cache.lucene.LuceneResultStruct;
import org.apache.geode.cache.lucene.LuceneService;
import org.apache.geode.cache.lucene.LuceneServiceProvider;
import org.apache.geode.cache.lucene.PageableLuceneQueryResults;

/**
 * The LuceneSearchIndexFunction class is a function used to collect the information on a particular
 * lucene index.
 * </p>
 * 
 * @see Cache
 * @see org.apache.geode.cache.execute.Function
 * @see FunctionAdapter
 * @see FunctionContext
 * @see InternalEntity
 * @see LuceneIndexDetails
 * @see LuceneIndexInfo
 */
@SuppressWarnings("unused")
public class LuceneSearchIndexFunction<K, V> implements Function {

  protected Cache getCache() {
    return CacheFactory.getAnyInstance();
  }

  public String getId() {
    return LuceneSearchIndexFunction.class.getSimpleName();
  }

  private LuceneQueryInfo createQueryInfoFromString(String strParm) {
    String params[] = strParm.split(",");
    //  "personIndex,Person,name:Tom99*,name,-1,false"
    int limit = Integer.parseInt(params[4]);
    boolean isKeyOnly = Boolean.parseBoolean(params[5]);
    LuceneQueryInfo queryInfo = new LuceneQueryInfo(params[0] /*index name */, 
        params[1] /* regionPath */, 
        params[2] /* queryString */,
        params[3] /* default field */,
        limit, isKeyOnly);
    return queryInfo;
  }
  
  public void execute(final FunctionContext context) {
    final Cache cache = getCache();
    LuceneQueryInfo queryInfo = null;
    Object args = context.getArguments();
    if (args instanceof LuceneQueryInfo) {
      queryInfo = (LuceneQueryInfo)args;
    } else if (args instanceof String) {
      String strParm = (String)args;
      queryInfo = createQueryInfoFromString(strParm);
    }

    LuceneService luceneService = LuceneServiceProvider.get(getCache());
    try {
      if (luceneService.getIndex(queryInfo.getIndexName(), queryInfo.getRegionPath()) == null) {
        throw new Exception("Index " + queryInfo.getIndexName() + " not found on region "
            + queryInfo.getRegionPath());
      }
      final LuceneQuery<K, V> query = luceneService.createLuceneQueryFactory()
          .setLimit(queryInfo.getLimit()).create(queryInfo.getIndexName(),
              queryInfo.getRegionPath(), queryInfo.getQueryString(), queryInfo.getDefaultField());
      if (queryInfo.getKeysOnly()) {
        context.getResultSender().lastResult(query.findKeys());
      } else {
        PageableLuceneQueryResults<K, V> pageableLuceneQueryResults = query.findPages();
        List<LuceneResultStruct<K, V>> pageResult = new ArrayList();
        while (pageableLuceneQueryResults.hasNext()) {
          List<LuceneResultStruct<K, V>> page = pageableLuceneQueryResults.next();
          pageResult.addAll(page);
        }
        context.getResultSender().lastResult(pageResult);
      }
    } catch (LuceneQueryException e) {
      context.getResultSender().lastResult(new LuceneSearchResults(true, e.getRootCause().getMessage()));
    } catch (Exception e) {
      context.getResultSender().lastResult(new LuceneSearchResults(true, e.getMessage()));
    }
  }
}

