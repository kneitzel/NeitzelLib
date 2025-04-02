package de.neitzel.core.sql;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Helper class to build and execute parameterized SQL queries with factory-based result mapping.
 */
@Slf4j
@RequiredArgsConstructor
public class Query<T> {

  /**
   * An Integer Factory that can be used to read Integer Values of a ResultSet that only has Integers (e.g. a min oder max query)
   */
  public static final Function<ResultSet, Integer> INTEGER_FACTORY = rs -> {
    try {
      return rs.getInt(1);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  };

  private final Connection connection;
  private final List<ParameterSetter> parameterSetters = new ArrayList<>();
  private Function<ResultSet, T> factory;
  private String queryText;

  public static void execute(final Connection connection, final String query) throws SQLException {
    new Query<Object>(connection)
        .setQueryText(query)
        .execute();
  }

  /**
   * sets the query.
   */
  public Query<T> setQueryText(final String queryText) {
    this.queryText = queryText;
    return this;
  }

  /**
   * Loads the query content from a resource URL.
   */
  public Query<T> setQueryResource(final String resourcePath) {
    log.info("loading resource: {}", resourcePath);
    try (Scanner scanner = new Scanner(Objects.requireNonNull(getClass().getResourceAsStream(resourcePath)),
        StandardCharsets.UTF_8)) {
      this.queryText = scanner.useDelimiter("\\A").next();
    }
    log.info("query text: {}", this.queryText);
    return this;
  }

  /**
   * Replaces placeholders in the query.
   */
  public Query<T> replace(final String placeholder, final String value) {
    this.queryText = this.queryText.replace(placeholder, value);
    log.info("query text: {}", this.queryText);
    return this;
  }

  /**
   * Adds another parameter setter
   *
   * @param index  Index of parameter in prepared statement
   * @param value  Value to set.
   * @param setter Setter to use on prepared statement.
   * @param <X>    Type of the Parameter.
   * @return The Query.
   */
  public <X> Query<T> addParameter(final int index, final X value, final TriSqlFunction<PreparedStatement, Integer, X> setter) {
    parameterSetters.add(ps -> setter.apply(ps, index, value));
    return this;
  }

  /**
   * Adds a ParameterSetter/
   *
   * @param setter Setter for a parameter.
   * @return the Qyery.
   */
  public Query<T> addParameter(ParameterSetter setter) {
    parameterSetters.add(setter);
    return this;
  }

  /**
   * Adds a result factory by name.
   */
  public Query<T> factory(final Function<ResultSet, T> factory) {
    this.factory = factory;
    return this;
  }

  /**
   * Executes the query and returns a stream of results. This Stream must be closed so that the underlying ResultSet is closed.
   */
  private Stream<T> stream() throws SQLException {
    ResultSet rs = createPreparedStatement().executeQuery();
    TrimmingResultSet trimmingResultSet = new TrimmingResultSet(rs);
    return streamFromResultSet(trimmingResultSet, factory);
  }

  /**
   * Creates a PreparedStatement and applies all Parameter.
   *
   * @return the created PreparedStatement
   * @throws SQLException thrown if the PreparedStatement could not be created.
   */
  private PreparedStatement createPreparedStatement() throws SQLException {
    PreparedStatement ps = connection.prepareStatement(queryText);
    for (ParameterSetter setter : parameterSetters) {
      setter.apply(ps);
    }
    return ps;
  }

  /**
   * Executes a query wich has no result.
   *
   * @throws SQLException Thrown when query cannot be executed.
   */
  public void execute() throws SQLException {
    try (PreparedStatement ps = createPreparedStatement()) {
      ps.execute();
    }
  }

  /**
   * Streams the results of a ResultSet using a factory that creates instances for each row of the ResultSet
   *
   * @param rs      ResultSet to stream from
   * @param factory Factory to create instances of T.
   * @return Stream of T instances.
   */
  private Stream<T> streamFromResultSet(final ResultSet rs, final Function<ResultSet, T> factory) {
    Iterator<T> iterator = new Iterator<>() {
      private boolean hasNextChecked = false;
      private boolean hasNext;

      @Override
      @SneakyThrows
      public boolean hasNext() {
        if (!hasNextChecked) {
          hasNext = rs.next();
          hasNextChecked = true;
        }
        return hasNext;
      }

      @Override
      public T next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        hasNextChecked = false;
        return factory.apply(rs);
      }
    };
    Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED);
    return StreamSupport.stream(spliterator, false)
        .onClose(() -> {
          try {
            rs.getStatement().close();
          } catch (SQLException ignored) {
            // Exception is ignored.
          }
          try {
            rs.close();
          } catch (SQLException ignored) {
            // Exception is ignored.
          }
        });
  }

  public <R> R withStream(Function<Stream<T>, R> handler) throws SQLException {
    try (Stream<T> stream = stream()) {
      return handler.apply(stream);
    }
  }

  /**
   * Function with three parameters and no result which can throw an SQLException
   *
   * @param <T> type of first parameter
   * @param <U> type of second parameter
   * @param <V> type of third parameter
   */
  @FunctionalInterface
  public interface TriSqlFunction<T, U, V> {

    /**
     * Calls the thre parameter function.
     *
     * @param t first parameter.
     * @param u second parameter.
     * @param v third parameter.
     * @throws SQLException Function could throw an SQLException.
     */
    void apply(T t, U u, V v) throws SQLException;
  }

  /**
   * ParameterSetter is a function Interface that could be used to alter a PreparedStatement.
   */
  @FunctionalInterface
  public interface ParameterSetter {

    /**
     * Does the required work on PreparedStatement.
     *
     * @param ps PreparedStatement to work on.
     * @throws SQLException Could be thrown when working on PreparedStatement.
     */
    void apply(PreparedStatement ps) throws SQLException;
  }
}
