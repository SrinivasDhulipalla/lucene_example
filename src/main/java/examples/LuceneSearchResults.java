package examples;

import java.io.Serializable;

public class LuceneSearchResults<K, V> implements Comparable<LuceneSearchResults>, Serializable {

  private String key;
  private String value;
  private float score;
  private boolean exceptionFlag = false;
  private String exceptionMessage;

  public LuceneSearchResults(final String key, final String value, final float score) {
    this.key = key;
    this.value = value;
    this.score = score;
  }

  public LuceneSearchResults(final String key) {
    this.key = key;
  }

  public LuceneSearchResults(final boolean exceptionFlag, final String exceptionMessage) {
    this.exceptionFlag = exceptionFlag;
    this.exceptionMessage = exceptionMessage;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  public float getScore() {
    return score;
  }

  @Override
  public int compareTo(final LuceneSearchResults searchResults) {
    return Float.compare(getScore(), searchResults.getScore());
  }

  public boolean getExeptionFlag() {
    return exceptionFlag;
  }

  public String getExceptionMessage() {
    return exceptionMessage;
  }

  @Override
  public String toString() {
    return "LuceneSearchResults{" + "key='" + key + '\'' + ", value='" + value + '\'' + ", score="
        + score + ", exceptionFlag=" + exceptionFlag + ", exceptionMessage='" + exceptionMessage
        + '\'' + '}';
  }
}
