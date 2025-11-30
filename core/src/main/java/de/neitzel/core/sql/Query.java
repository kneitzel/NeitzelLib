package de.neitzel.core.sql;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Scanner;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A generic class for managing SQL queries and their execution using JDBC.
 * The class supports parameterized queries, allows loading queries from resources,
 * and facilitates streaming of result sets.
 *
 * @param <T> The type of the objects returned by the query.
 */
@Slf4j
public class Query<T> {

    /**
     * A factory function that extracts an Integer from the first column of a given ResultSet.
     * <p>
     * This function is designed to facilitate the conversion of a ResultSet row into an Integer
     * by accessing the value in the first column of the result set. If an SQL exception is encountered
     * during the retrieval of the integer value, it is wrapped and re-thrown as a runtime exception.
     */
    public static final Function<ResultSet, Integer> INTEGER_FACTORY = rs -> {
        try {
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    };

    /**
     * Represents a database connection instance.
     * This connection is used to interact with a database
     * through executing queries and retrieving results.
     * It is immutable and initialized once.
     */
    private final Connection connection;

    /**
     * A list of ParameterSetter objects used to configure parameters.
     * This collection serves as a storage for managing parameter-setting logic dynamically.
     * It is initialized as an empty ArrayList and is immutable since it is declared as final.
     */
    private final List<ParameterSetter> parameterSetters = new ArrayList<>();

    /**
     * A functional interface representing a factory method used to produce instances of type T.
     * This factory is typically implemented to map rows from a {@link ResultSet} to objects of type T.
     * The input parameter is a {@link ResultSet}, which provides access to database query results.
     * The resulting output is an instance of type T created based on the data in the provided ResultSet.
     */
    private Function<ResultSet, T> factory;

    /**
     * Represents the text of a query.
     * This variable stores the query string used for processing or execution purposes within the application.
     */
    private String queryText;

    /**
     * Constructs a new Query object with the given database connection.
     *
     * @param connection The Connection object used to interact with the database.
     */
    public Query(Connection connection) {
        this.connection = connection;
    }

    /**
     * Executes the given SQL query using the provided database connection.
     *
     * @param connection the database connection to be used for executing the query
     * @param query      the SQL query to be executed
     * @throws SQLException if a database access error occurs or the query execution fails
     */
    public static void execute(final Connection connection, final String query) throws SQLException {
        new Query<Object>(connection)
                .setQueryText(query)
                .execute();
    }

    /**
     * Executes the SQL statement represented by the PreparedStatement created in
     * the method {@code createPreparedStatement()}. This method is used to perform
     * database operations such as updates or other actions that do not produce a
     * result set.
     *
     * @throws SQLException if a database access error occurs or the SQL statement is invalid.
     */
    public void execute() throws SQLException {
        try (PreparedStatement ps = createPreparedStatement()) {
            ps.execute();
        }
    }

    /**
     * Sets the query text for the current query instance.
     *
     * @param queryText the text of the query to be set
     * @return the current instance of the query
     */
    public Query<T> setQueryText(final String queryText) {
        this.queryText = queryText;
        return this;
    }

    /**
     * Creates and returns a PreparedStatement object configured with the query text
     * and parameters defined for the current instance.
     *
     * @return a PreparedStatement object initialized with the query and parameter values
     * @throws SQLException if a database access error occurs or the PreparedStatement cannot be created
     */
    private PreparedStatement createPreparedStatement() throws SQLException {
        PreparedStatement ps = connection.prepareStatement(queryText);
        for (ParameterSetter setter : parameterSetters) {
            setter.apply(ps);
        }
        return ps;
    }

    /**
     * Sets the query resource by loading its content from the given resource path.
     * The resource content is read and stored as the query text for the query object.
     *
     * @param resourcePath the path to the resource file containing the query text
     * @return the current Query object with the loaded query text
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
     * Replaces all occurrences of the specified placeholder in the query text with the provided value.
     *
     * @param placeholder the placeholder string to be replaced in the query text
     * @param value       the value to replace the placeholder with
     * @return the updated Query instance with the modified query text
     */
    public Query<T> replace(final String placeholder, final String value) {
        this.queryText = this.queryText.replace(placeholder, value);
        log.info("query text: {}", this.queryText);
        return this;
    }

    /**
     * Adds a parameter to the query at the specified index with a custom setter function.
     *
     * @param index  The position of the parameter in the query starting from 1.
     * @param value  The value of the parameter to be set.
     * @param setter A TriSqlFunction that defines how to set the parameter in the PreparedStatement.
     * @param <X>    The type of the parameter value.
     * @return The current query instance for method chaining.
     */
    public <X> Query<T> addParameter(final int index, final X value, final TriSqlFunction<PreparedStatement, Integer, X> setter) {
        parameterSetters.add(ps -> setter.apply(ps, index, value));
        return this;
    }

    /**
     * Adds a parameter to the query using the specified ParameterSetter.
     *
     * @param setter the ParameterSetter used to configure the parameter
     * @return the current Query instance for method chaining
     */
    public Query<T> addParameter(ParameterSetter setter) {
        parameterSetters.add(setter);
        return this;
    }

    /**
     * Sets the factory function used to convert a {@link ResultSet} into an instance of type {@code T}.
     *
     * @param factory the function responsible for mapping a {@code ResultSet} to an object of type {@code T}
     * @return the current {@code Query} instance with the specified factory function set
     */
    public Query<T> factory(final Function<ResultSet, T> factory) {
        this.factory = factory;
        return this;
    }

    /**
     * Provides a managed stream to the given handler and ensures proper resource closing after use.
     * A {@code Stream} is opened and passed to the specified handler as an argument.
     * The stream will be automatically closed when the handler finishes execution or throws an exception.
     *
     * @param handler a function which receives a {@code Stream<T>} and processes it, returning a result of type {@code R}.
     * @param <R>     the type of the result produced by the handler.
     * @return the result returned by the handler after processing the stream.
     * @throws SQLException if an SQL error occurs during the creation or handling of the stream.
     */
    public <R> R withStream(Function<Stream<T>, R> handler) throws SQLException {
        try (Stream<T> stream = stream()) {
            return handler.apply(stream);
        }
    }

    /**
     * Creates a {@link Stream} from the result set obtained by executing a prepared statement.
     * The result set is wrapped in a {@link TrimmingResultSet} to handle data trimming.
     *
     * @return a {@link Stream} of type T populated with the query results processed using the specified factory
     * @throws SQLException if an SQL error occurs while executing the prepared statement or creating the stream
     */
    private Stream<T> stream() throws SQLException {
        ResultSet rs = createPreparedStatement().executeQuery();
        TrimmingResultSet trimmingResultSet = new TrimmingResultSet(rs);
        return streamFromResultSet(trimmingResultSet, factory);
    }

    /**
     * Converts a {@link ResultSet} into a {@link Stream} of objects created using the provided {@link Function}
     * factory. The stream ensures resources such as the {@code ResultSet} and its associated statement are
     * closed when the stream is closed.
     *
     * @param rs      the {@link ResultSet} to be transformed into a stream
     * @param factory a {@link Function} that maps rows of the {@link ResultSet} to objects of type T
     * @return a {@link Stream} of objects of type T created from the {@code ResultSet} rows
     */
    private Stream<T> streamFromResultSet(final ResultSet rs, final Function<ResultSet, T> factory) {
        Iterator<T> iterator = new Iterator<>() {
            private boolean hasNextChecked = false;

            private boolean hasNext;

            /**
             * Checks if the iteration has more elements. This method verifies if the
             * underlying {@link ResultSet} contains another row that has not yet been processed.
             *
             * @return {@code true} if there are more elements in the {@link ResultSet},
             *         otherwise {@code false}.
             */
            @Override
            @SneakyThrows
            public boolean hasNext() {
                if (!hasNextChecked) {
                    hasNext = rs.next();
                    hasNextChecked = true;
                }
                return hasNext;
            }

            /**
             * Returns the next element in the iteration. This method applies the provided
             * factory function to the current row of the {@link ResultSet} to create an object of type T.
             * It throws {@link NoSuchElementException} if there are no more elements to iterate.
             *
             * @return the next element of type T as created by the factory function from the current row of the {@link ResultSet}
             * @throws NoSuchElementException if the iteration has no more elements
             */
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

    /**
     * Represents a functional interface that accepts three input arguments and allows for operations
     * that can throw an {@link SQLException}. This can be useful in scenarios where SQL-related
     * logic requires processing with three parameters.
     *
     * @param <T> the type of the first input to the operation
     * @param <U> the type of the second input to the operation
     * @param <V> the type of the third input to the operation
     */
    @FunctionalInterface
    public interface TriSqlFunction<T, U, V> {

        /**
         * Executes an operation on three input arguments and allows for the operation to throw
         * an {@link SQLException}. This method is intended to facilitate SQL-related processes
         * where three parameters are involved.
         *
         * @param t the first input argument
         * @param u the second input argument
         * @param v the third input argument
         * @throws SQLException if an SQL error occurs during execution
         */
        void apply(T t, U u, V v) throws SQLException;
    }

    /**
     * Functional interface designed to handle operations on a PreparedStatement.
     * Represents a single abstract method that performs specific work on the
     * provided PreparedStatement instance.
     */
    @FunctionalInterface
    public interface ParameterSetter {

        /**
         * Applies an operation to the given PreparedStatement object.
         * This method is intended to set parameters or perform other modifications on a PreparedStatement.
         *
         * @param ps the PreparedStatement instance to be modified or configured
         * @throws SQLException if a database access error occurs while applying the operation
         */
        void apply(PreparedStatement ps) throws SQLException;
    }
}
