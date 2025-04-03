package de.neitzel.core.sql;

import lombok.RequiredArgsConstructor;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.Map;

/**
 * A wrapper implementation of the {@link ResultSet} interface that modifies or intercepts
 * specific behavior, such as trimming data retrieved from the underlying {@code ResultSet}.
 *
 * <p>This class delegates method calls to an encapsulated {@link ResultSet} instance.
 * If any additional behaviors or transformations are applied, they are documented
 * in the respective overridden method descriptions.
 */
public class TrimmingResultSet implements ResultSet {

  /**
   * Represents the result set obtained from executing a database query.
   * This object provides functionality to traverse and access the data
   * in a tabular format returned by the query execution.
   *
   * This ResultSet instance is immutable and cannot be modified after initialization.
   * It is typically used for reading query results and is tied to a specific
   * SQL query execution lifecycle.
   */
  private final ResultSet resultSet;

  /**
   * Initializes a new instance of the TrimmingResultSet class, wrapping the provided ResultSet.
   *
   * @param resultSet the ResultSet to be wrapped and processed by this TrimmingResultSet instance
   */
  public TrimmingResultSet(ResultSet resultSet) {
    this.resultSet = resultSet;
  }

  /**
   * Retrieves the string value from the specified column index of the current row in the ResultSet.
   * The value is trimmed to remove leading and trailing whitespace. If the value is null, it returns null.
   *
   * @param columnIndex the column index, starting from 1, from which the string is to be retrieved
   * @return the trimmed string value from the specified column, or null if the column value is SQL NULL
   * @throws SQLException if a database access error occurs or the columnIndex is not valid
   */
  @Override
  public String getString(final int columnIndex) throws SQLException {
    String value = resultSet.getString(columnIndex);
    return value != null ? value.trim() : null;
  }

  /**
   * Retrieves the value of the specified column as a trimmed string.
   *
   * @param columnLabel the label of the column from which to retrieve the value
   * @return the trimmed string value of the specified column, or null if the column value is SQL NULL
   * @throws SQLException if a database access error occurs or the columnLabel is invalid
   */
  @Override
  public String getString(final String columnLabel) throws SQLException {
    String value = resultSet.getString(columnLabel);
    return value != null ? value.trim() : null;
  }

  /**
   * Moves the cursor forward one row from its current position in the ResultSet.
   *
   * @return true if the new current row is valid, false if there are no more rows
   * @throws SQLException if a database access error occurs or this method is called on a closed ResultSet
   */
  @Override
  public boolean next() throws SQLException {
    return resultSet.next();
  }

  /**
   * Closes the current resource associated with this instance.
   * This method ensures that the ResultSet object is properly closed to
   * release database and JDBC resources.
   *
   * @throws SQLException if a database access error occurs while closing the ResultSet.
   */
  @Override
  public void close() throws SQLException {
    resultSet.close();
  }

  /**
   * Checks if the last column read had a value of SQL NULL.
   * This method should be called only after calling a getter method
   * on the ResultSet to retrieve a column value.
   *
   * @return true if the last column read was SQL NULL; false otherwise
   * @throws SQLException if a database access error occurs
   */
  @Override
  public boolean wasNull() throws SQLException {
    return resultSet.wasNull();
  }

  /**
   * Retrieves the value of the designated column in the current row of the result set
   * as a boolean.
   *
   * @param columnIndex the index of the column, starting from 1
   * @return the column value as a boolean
   * @throws SQLException if a database access error occurs or the columnIndex is out of bounds
   */
  @Override
  public boolean getBoolean(final int columnIndex) throws SQLException {
    return resultSet.getBoolean(columnIndex);
  }

  /**
   * Retrieves the value of the designated column as a boolean.
   *
   * @param columnLabel the label for the column specified with the SQL AS clause. If the SQL AS clause was not specified,
   *                    then the label is the name of the column.
   * @return the column value as a boolean. Returns false if the value in the designated column is SQL NULL.
   * @throws SQLException if a database access error occurs or the column label is not valid.
   */
  @Override
  public boolean getBoolean(final String columnLabel) throws SQLException {
    return resultSet.getBoolean(columnLabel);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this ResultSet object as a byte.
   *
   * @param columnIndex the first column is 1, the second is 2, and so on;
   *                    must be a valid column index
   * @return the column value as a byte
   * @throws SQLException if the column index is invalid or if a database access error occurs
   */
  @Override
  public byte getByte(final int columnIndex) throws SQLException {
    return resultSet.getByte(columnIndex);
  }

  /**
   * Retrieves the value of the designated column in the current row of this ResultSet
   * object as a byte.
   *
   * @param columnLabel the label for the column specified with the SQL AS clause.
   *                    If the SQL AS clause was not specified, then the label is
   *                    the name of the column.
   * @return the column value as a byte. If the value is SQL NULL, the result is 0.
   * @throws SQLException if the columnLabel is not valid or a database access error occurs.
   */
  @Override
  public byte getByte(final String columnLabel) throws SQLException {
    return resultSet.getByte(columnLabel);
  }

  /**
   * Retrieves the value of the designated column in the current row of this ResultSet object
   * as a short.
   *
   * @param columnIndex the column index, starting from 1, which indicates the column to retrieve
   *                    the short value from
   * @return the column value as a short
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  public short getShort(final int columnIndex) throws SQLException {
    return resultSet.getShort(columnIndex);
  }

  /**
   * Retrieves the value of the specified column as a <code>short</code>.
   *
   * @param columnLabel the label for the column specified with the SQL query
   * @return the column value as a <code>short</code>
   * @throws SQLException if a database access error occurs or the columnLabel is not valid
   */
  @Override
  public short getShort(final String columnLabel) throws SQLException {
    return resultSet.getShort(columnLabel);
  }

  /**
   * Retrieves the value of the column specified by the given index as an int.
   *
   * @param columnIndex the index of the column in the current row, starting from 1
   * @return the column value as an int
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  public int getInt(final int columnIndex) throws SQLException {
    return resultSet.getInt(columnIndex);
  }

  /**
   * Retrieves the value of the column designated by the given label as an int.
   *
   * @param columnLabel the label for the column specified with the SQL AS clause, or the original column name
   * @return the column value as an int
   * @throws SQLException if a database access error occurs or the column label is not valid
   */
  @Override
  public int getInt(final String columnLabel) throws SQLException {
    return resultSet.getInt(columnLabel);
  }

  /**
   * Retrieves the value of the designated column in the current row of the ResultSet
   * as a long value.
   *
   * @param columnIndex the index of the column from which to retrieve the value, starting from 1
   * @return the long value of the designated column
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  public long getLong(final int columnIndex) throws SQLException {
    return resultSet.getLong(columnIndex);
  }

  /**
   * Retrieves the value of the specified column as a long.
   *
   * @param columnLabel the name of the column from which to retrieve the value
   * @return the long value of the specified column
   * @throws SQLException if a database access error occurs or the column label is not valid
   */
  @Override
  public long getLong(final String columnLabel) throws SQLException {
    return resultSet.getLong(columnLabel);
  }

  /**
   * Retrieves the value of the designated column in the current row of this
   * ResultSet as a float.
   *
   * @param columnIndex the index of the column, starting from 1, from which
   *                    the float value is to be retrieved
   * @return the column value as a float
   * @throws SQLException if a database access error occurs or the column
   *                       index is invalid
   */
  @Override
  public float getFloat(final int columnIndex) throws SQLException {
    return resultSet.getFloat(columnIndex);
  }

  /**
   * Retrieves the value of the specified column as a float from the result set.
   *
   * @param columnLabel the label for the column specified in the SQL query
   * @return the column value as a float
   * @throws SQLException if a database access error occurs or the columnLabel is not valid
   */
  @Override
  public float getFloat(final String columnLabel) throws SQLException {
    return resultSet.getFloat(columnLabel);
  }

  /**
   * Retrieves the value of the designated column in the current row of this
   * ResultSet object as a double.
   *
   * @param columnIndex the index of the column, starting from 1, from which
   *                    the double value is to be retrieved
   * @return the column value as a double
   * @throws SQLException if a database access error occurs or the column
   *                       index is invalid
   */
  @Override
  public double getDouble(final int columnIndex) throws SQLException {
    return resultSet.getDouble(columnIndex);
  }

  /**
   * Retrieves the value of the designated column as a double.
   *
   * @param columnLabel the label for the column specified in the SQL query
   * @return the column value as a double
   * @throws SQLException if a database access error occurs or the columnLabel is not valid
   */
  @Override
  public double getDouble(final String columnLabel) throws SQLException {
    return resultSet.getDouble(columnLabel);
  }

  /**
   * Retrieves the value of the specified column as a BigDecimal object with the specified scale.
   *
   * @param columnIndex the index of the column (1-based) from which to retrieve the BigDecimal value
   * @param scale the scale to apply to the retrieved BigDecimal value
   * @return the BigDecimal value from the specified column with the given scale
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  @Deprecated
  public BigDecimal getBigDecimal(final int columnIndex, int scale) throws SQLException {
    return resultSet.getBigDecimal(columnIndex, scale);
  }

  /**
   * Retrieves the value of the specified column as a {@code BigDecimal} object.
   *
   * @param columnIndex the column index (starting at 1) from which the BigDecimal value is to be retrieved
   * @return the column value as a {@code BigDecimal}, or null if the column value is SQL NULL
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  public BigDecimal getBigDecimal(final int columnIndex) throws SQLException {
    return resultSet.getBigDecimal(columnIndex);
  }

  /**
   * Retrieves the value of the designated column as a {@code BigDecimal} with the specified scale.
   *
   * @param columnLabel the label for the column in the SQL result set
   * @param scale the number of digits to the right of the decimal point
   * @return the value of the column as a {@code BigDecimal} with the specified scale
   * @throws SQLException if a database access error occurs or this method is called on a closed result set
   */
  @Override
  @Deprecated
  public BigDecimal getBigDecimal(final String columnLabel, int scale) throws SQLException {
    return resultSet.getBigDecimal(columnLabel, scale);
  }

  /**
   * Retrieves the value of the specified column as a {@code BigDecimal} object.
   *
   * @param columnLabel the label of the column from which to retrieve the value
   * @return the column value as a {@code BigDecimal} object, or {@code null} if the value is SQL {@code NULL}
   * @throws SQLException if an SQL error occurs while retrieving the value
   */
  @Override
  public BigDecimal getBigDecimal(final String columnLabel) throws SQLException {
    return resultSet.getBigDecimal(columnLabel);
  }

  /**
   * Retrieves the value of the designated column in the current row of this ResultSet
   * object as a byte array.
   *
   * @param columnIndex the first column is 1, the second is 2, and so on; indicates
   *                    which column value to retrieve.
   * @return the column value as a byte array. Returns null if the SQL value is
   *         SQL NULL.
   * @throws SQLException if the columnIndex is invalid or a database access error occurs.
   */
  @Override
  public byte[] getBytes(final int columnIndex) throws SQLException {
    return resultSet.getBytes(columnIndex);
  }

  /**
   * Retrieves the value of the specified column in the current row of this ResultSet
   * as a byte array. The column is specified using the column label.
   *
   * @param columnLabel the label for the column from which to retrieve the value
   * @return a byte array containing the column value; null if the value is SQL NULL
   * @throws SQLException if a database access error occurs or the column label is invalid
   */
  @Override
  public byte[] getBytes(final String columnLabel) throws SQLException {
    return resultSet.getBytes(columnLabel);
  }

  /**
   * Retrieves the value of the designated column in the current row of this result set
   * as a {@code Date} object.
   *
   * @param columnIndex the first column is 1, the second column is 2, and so on
   * @return the {@code Date} value of the specified column in the current row
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  public Date getDate(final int columnIndex) throws SQLException {
    return resultSet.getDate(columnIndex);
  }

  /**
   * Retrieves the date value of the specified column.
   *
   * @param columnLabel the label of the column from which the date value is to be retrieved
   * @return the date value of the specified column, or null if the value is SQL NULL
   * @throws SQLException if a database access error occurs or the column label is invalid
   */
  @Override
  public Date getDate(final String columnLabel) throws SQLException {
    return resultSet.getDate(columnLabel);
  }

  /**
   * Retrieves the value of the designated column as a <code>Date</code> object
   * using the given <code>Calendar</code> object for interpreting the timezone.
   *
   * @param columnIndex the index of the column, starting from 1
   * @param cal the <code>Calendar</code> object to use for interpreting dates, or null
   * @return the value of the column as a <code>Date</code> object;
   *         if the value is SQL NULL, the result is null
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  public Date getDate(final int columnIndex, final Calendar cal) throws SQLException {
    return resultSet.getDate(columnIndex, cal);
  }

  /**
   * Retrieves the value of the specified column as a Date object using the given Calendar instance to construct the date.
   *
   * @param columnLabel the label for the column from which to retrieve the value
   * @param cal the Calendar instance to use for determining the date
   * @return the column value as a Date object; if the value is SQL NULL, the result is null
   * @throws SQLException if a database access error occurs or the column label is invalid
   */
  @Override
  public Date getDate(final String columnLabel, final Calendar cal) throws SQLException {
    return resultSet.getDate(columnLabel, cal);
  }

  /**
   * Retrieves the value of the designated column as a {@code Time} object.
   *
   * @param columnIndex the index of the column, starting from 1
   * @return the {@code Time} value of the specified column in the current row
   * @throws SQLException if the column index is invalid or a database access error occurs
   */
  @Override
  public Time getTime(final int columnIndex) throws SQLException {
    return resultSet.getTime(columnIndex);
  }

  /**
   * Retrieves the value of the specified column as a {@code Time} object.
   *
   * @param columnLabel the label of the column from which to retrieve the value
   * @return a {@code Time} object representing the value of the specified column
   * @throws SQLException if a database access error occurs or the columnLabel is not valid
   */
  @Override
  public Time getTime(final String columnLabel) throws SQLException {
    return resultSet.getTime(columnLabel);
  }

  /**
   * Retrieves the value of the designated column as a {@code Time} object.
   *
   * @param columnIndex an integer indicating the column index, starting at 1
   * @param cal the {@code Calendar} object to use for constructing the {@code Time} object
   * @return the column value as a {@code Time} object or null if the column value is SQL {@code NULL}
   * @throws SQLException if a database access error occurs, the column index is invalid,
   *                      or the {@code Calendar} object is null
   */
  @Override
  public Time getTime(final int columnIndex, final Calendar cal) throws SQLException {
    return resultSet.getTime(columnIndex, cal);
  }

  /**
   * Retrieves the value of the specified column as a {@link Time} object using the given calendar for timezone adjustments.
   *
   * @param columnLabel the label for the column specified with the SQL query
   * @param cal the {@link Calendar} object to use in constructing the date
   * @return the column value as a {@link Time} object; if the value is SQL {@code NULL}, the result is {@code null}
   * @throws SQLException if a database access error occurs or the column label is not valid
   */
  @Override
  public Time getTime(final String columnLabel, final Calendar cal) throws SQLException {
    return resultSet.getTime(columnLabel, cal);
  }

  /**
   * Retrieves the value of the designated column in the current row of this
   * ResultSet object as a Timestamp object.
   *
   * @param columnIndex the column index, starting at 1, from which the timestamp
   *                    value is to be retrieved
   * @return a Timestamp object representing the SQL TIMESTAMP value in the specified
   *         column of the current row
   * @throws SQLException if a database access error occurs, the column index is invalid,
   *                       or the data in the column is not a valid timestamp
   */
  @Override
  public Timestamp getTimestamp(final int columnIndex) throws SQLException {
    return resultSet.getTimestamp(columnIndex);
  }

  /**
   * Retrieves the timestamp value for the specified column label from the underlying result set.
   *
   * @param columnLabel the label for the column from which to retrieve the timestamp
   * @return the timestamp value of the specified column; may return null if the value is SQL NULL
   * @throws SQLException if a database access error occurs or if the column label is invalid
   */
  @Override
  public Timestamp getTimestamp(final String columnLabel) throws SQLException {
    return resultSet.getTimestamp(columnLabel);
  }

  /**
   * Retrieves the value of the specified column as a {@code Timestamp} object,
   * using the provided {@code Calendar} to construct the {@code Timestamp}.
   *
   * @param columnIndex the index of the column (1-based) whose value is to be retrieved
   * @param cal the {@code Calendar} object to use for interpreting the timestamp
   * @return the column value as a {@code Timestamp} object; returns {@code null} if the column value is SQL {@code NULL}
   * @throws SQLException if the column index is invalid or a database access error occurs
   */
  @Override
  public Timestamp getTimestamp(final int columnIndex, final Calendar cal) throws SQLException {
    return resultSet.getTimestamp(columnIndex, cal);
  }

  /**
   * Retrieves the value of the designated column as a {@code Timestamp} object in the specified calendar.
   * This method uses the provided {@code Calendar} to construct the {@code Timestamp} object.
   *
   * @param columnLabel the label for the column from which to retrieve the value
   * @param cal the {@code Calendar} object to use to construct the timestamp
   * @return the column value as a {@code Timestamp}; if the value is SQL {@code NULL}, the value returned is {@code null}
   * @throws SQLException if a database access error occurs or the column label is invalid
   */
  @Override
  public Timestamp getTimestamp(final String columnLabel, final Calendar cal) throws SQLException {
    return resultSet.getTimestamp(columnLabel, cal);
  }

  /**
   * Retrieves the value of the designated column in the current row of this ResultSet object as a stream of ASCII characters.
   *
   * @param columnIndex the column index, starting from 1, indicating which column's value to retrieve
   * @return an InputStream object that contains the ASCII data of the specified column
   * @throws SQLException if a database access error occurs or the columnIndex is invalid
   */
  @Override
  public InputStream getAsciiStream(final int columnIndex) throws SQLException {
    return resultSet.getAsciiStream(columnIndex);
  }

  /**
   * Retrieves the value of the designated column in the current row as a stream of Unicode characters.
   * The stream is read in the Java Unicode character format.
   * This method is used to access database data as Unicode text.
   *
   * @param columnIndex the index of the column in the result set, starting from 1 for the first column
   * @return an InputStream object that provides a stream of Unicode characters,
   *         or null if the SQL value is null
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  public InputStream getUnicodeStream(final int columnIndex) throws SQLException {
    return null;
  }

  /**
   * Retrieves the value of the specified column as a stream of ASCII characters.
   *
   * @param columnLabel the label for the column from which the stream will be retrieved
   * @return an InputStream object containing the ASCII stream of the designated column value
   * @throws SQLException if a database access error occurs or the columnLabel is not valid
   */
  @Override
  public InputStream getAsciiStream(final String columnLabel) throws SQLException {
    return resultSet.getAsciiStream(columnLabel);
  }

  /**
   * Retrieves the value of the designated column labeled by the given column label
   * as a Unicode stream. The data will be returned as a stream of Unicode characters.
   * This method is intended for reading long text values or other binary data
   * that can be represented as Unicode.
   *
   * @param columnLabel the label for the column specified in the SQL query
   * @return an InputStream containing the Unicode stream of the designated column,
   *         or null if the value is SQL NULL
   * @throws SQLException if a database access error occurs or if the columnLabel
   *         does not match an existing column
   */
  @Override
  public InputStream getUnicodeStream(final String columnLabel) throws SQLException {
    return null;
  }

  /**
   * Retrieves the value of the designated*/
  @Override
  public InputStream getBinaryStream(final int columnIndex) throws SQLException {
    return resultSet.getBinaryStream(columnIndex);
  }

  /**
   * Retrieves the value of the designated column as a binary input stream.
   *
   * @param columnLabel the label for the column specified in the SQL query
   * @return an InputStream object that contains the binary data of the designated column
   * @throws SQLException if the columnLabel is not valid;
   *         if a database access error occurs;
   *         or if this method is called on a closed result set
   */
  @Override
  public InputStream getBinaryStream(final String columnLabel) throws SQLException {
    return resultSet.getBinaryStream(columnLabel);
  }

  /**
   * Retrieves the first warning reported by calls on the underlying object.
   * Subsequent warnings on the object are chained to the first SQLWarning object.
   *
   * @return the first SQLWarning object or null if there are no warnings
   * @throws SQLException if a database access error occurs
   */
  @Override
  public SQLWarning getWarnings() throws SQLException {
    return null;
  }

  /**
   * Retrieves the value of the designated column in the current row of
   * this ResultSet object as a java.io.Reader object.
   *
   * @param columnIndex the column index (1-based) for which the character stream is to be retrieved
   * @return a Reader object that contains the data of the specified column; if the value is SQL NULL, this method returns null
   * @throws SQLException if a database access error occurs or this method is called on a closed result set
   */
  @Override
  public Reader getCharacterStream(final int columnIndex) throws SQLException {
    return resultSet.getCharacterStream(columnIndex);
  }

  /**
   * Retrieves the character stream for the value of the specified column label in the current row of this ResultSet.
   * This allows reading character data from the column as a Reader.
   *
   * @param columnLabel the label for the column specified in the ResultSet object, which is typically the SQL AS name if available, or the actual column name.
   * @return a Reader object containing the column value as a stream of characters; null if the value is SQL NULL.
   * @throws SQLException if a database access error occurs or the columnLabel is not valid.
   */
  @Override
  public Reader getCharacterStream(final String columnLabel) throws SQLException {
    return resultSet.getCharacterStream(columnLabel);
  }

  /**
   * Retrieves the NString value at the specified column index from the underlying result set.
   * If the value is not null, it trims any leading or trailing whitespace characters.
   *
   * @param columnIndex the index of the column from which to retrieve the NString value, starting from 1
   * @return the trimmed NString value at the specified column index, or null if the value is null
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  public String getNString(final int columnIndex) throws SQLException {
    String value = resultSet.getNString(columnIndex);
    return value != null ? value.trim() : null;
  }

  /**
   * Retrieves the value of the designated column as a string in the current row of the result set.
   * The string is processed to ensure leading and trailing whitespace is removed.
   *
   * @param columnLabel the label for the column specified in the query
   * @return the column value as a trimmed string, or null if the value is SQL NULL
   * @throws SQLException if the columnLabel is not valid, if there is a database access error, or other SQL issues occur
   */
  @Override
  public String getNString(final String columnLabel) throws SQLException {
    String value = resultSet.getNString(columnLabel);
    return value != null ? value.trim() : null;
  }

  /**
   * Retrieves the value of the designated column in the current row of
   * this ResultSet object as a Reader object. The column value is expected
   * to be a national character stream in the Java programming language.
   *
   * @param columnIndex the column index (1-based) of the desired column in this ResultSet
   * @return a Reader object that contains the column value; if the value is SQL NULL, then null is returned
   * @throws SQLException if the columnIndex is invalid or a database access error occurs
   */
  @Override
  public Reader getNCharacterStream(final int columnIndex) throws SQLException {
    return resultSet.getNCharacterStream(columnIndex);
  }

  /**
   * Retrieves the value of the designated column in the current row of this ResultSet
   * object as a character stream in the form of a Reader.
   *
   * @param columnLabel the label for the column specified in the SQL query
   * @return a Reader object that contains the column value; if the column value is SQL NULL, the value returned is null
   * @throws SQLException if the columnLabel is not valid, the result set is closed, or a database access error occurs
   */
  @Override
  public Reader getNCharacterStream(final String columnLabel) throws SQLException {
    return resultSet.getNCharacterStream(columnLabel);
  }

  /**
   * Clears all warnings reported on this ResultSet object.
   *
   * This method delegates the call to the underlying ResultSet's clearWarnings method,
   * ensuring that any chain of warnings previously recorded is cleared.
   *
   * @throws SQLException if a database access error occurs or this method
   * is called on a closed ResultSet.
   */
  @Override
  public void clearWarnings() throws SQLException {
    resultSet.clearWarnings();
  }

  /**
   * Retrieves the name of the SQL cursor used by this ResultSet object, if available.
   *
   * @return the name of the SQL cursor, or null if the cursor does not have a name.
   * @throws SQLException if a database access error occurs or this method is called on a closed result set.
   */
  @Override
  public String getCursorName() throws SQLException {
    return resultSet.getCursorName();
  }

  /**
   * Retrieves the metadata for the result set.
   *
   * @return a ResultSetMetaData object that contains information about the columns of the result set.
   * @throws SQLException if a database access error occurs.
   */
  @Override
  public ResultSetMetaData getMetaData() throws SQLException {
    return resultSet.getMetaData();
  }

  /**
   * Retrieves the value of the designated column in the current row of this ResultSet object
   * as an Object. The type of the object returned is determined by the SQL type of the column.
   *
   * @param columnIndex the index of the column to retrieve, where the first column is 1
   * @return the value of the designated column as an Object; returns null if the value is SQL NULL
   * @throws SQLException if a database access error occurs or the column index is out of bounds
   */
  @Override
  public Object getObject(final int columnIndex) throws SQLException {
    return resultSet.getObject(columnIndex);
  }

  /**
   * Retrieves the value of the designated column in the current row of this ResultSet object
   * as an Object.
   *
   * @param columnLabel the label for the column specified with the SQL AS clause.
   *                    If the SQL AS clause was not specified, the label is the name of the column.
   * @return the value of the specified column as an Object
   * @throws SQLException if a database access error occurs or this method is called on a closed ResultSet
   */
  @Override
  public Object getObject(final String columnLabel) throws SQLException {
    return resultSet.getObject(columnLabel);
  }

  /**
   * Retrieves the value of the designated column in the current row of this ResultSet object
   * as an Object in the Java programming language. The value will be converted to the specified
   * Java class using the provided type map.
   *
   * @param columnIndex the first column is 1, the second is 2, and so on; must be in the range
   *                    [1, number of columns in the current row].
   * @param map a mapping of SQL structured types or SQL DISTINCT types to classes in the Java
   *            programming language. The type map parameter overrides the default type map
   *            associated with the connection for this call only.
   * @return an Object that represents the SQL value in the specified column, converted to a
   *         Java object using the provided type map. Returns null if the SQL value is SQL NULL.
   * @throws SQLException if a database access error occurs, the columnIndex is out of bounds,
   *                      or if the type map is invalid.
   */
  @Override
  public Object getObject(final int columnIndex, final Map<String, Class<?>> map) throws SQLException {
    return resultSet.getObject(columnIndex, map);
  }

  /**
   * Retrieves the value of the specified column as a {@link Ref} object.
   *
   * @param columnIndex the 1-based index of the column from which the value is to be retrieved
   * @return the value of the specified column as a {@link Ref} object, or null if the value is SQL NULL
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  public Ref getRef(final int columnIndex) throws SQLException {
    return resultSet.getRef(columnIndex);
  }

  /**
   * Retrieves the Blob object designated by the given column index from the current row of the result set.
   *
   * @param columnIndex the column index, starting from 1, from which the Blob object is to be retrieved.
   * @return the Blob object at the specified column index; may be null if the SQL value is SQL NULL.
   * @throws SQLException if a database access error occurs or if this method is called on a closed result set.
   */
  @Override
  public Blob getBlob(final int columnIndex) throws SQLException {
    return resultSet.getBlob(columnIndex);
  }

  /**
   * Retrieves the CLOB value at the specified column index from the underlying ResultSet.
   *
   * @param columnIndex the 1-based index of the column from which the CLOB is to be retrieved
   * @return the CLOB value from the specified column, or null if the column value is SQL NULL
   * @throws SQLException if a database access error occurs or the columnIndex is invalid
   */
  @Override
  public Clob getClob(final int columnIndex) throws SQLException {
    return resultSet.getClob(columnIndex);
  }

  /**
   * Retrieves an SQL Array object from the specified column index
   * of the current row in the ResultSet.
   *
   * @param columnIndex the index of the column to retrieve the Array from, starting from 1
   * @return an Array object representing the SQL Array value at the specified column index
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  public Array getArray(final int columnIndex) throws SQLException {
    return resultSet.getArray(columnIndex);
  }

  /**
   * Retrieves the value of the specified column as an Object, using the given type map for
   * custom mappings of SQL user-defined types to Java classes.
   *
   * @param columnLabel the label for the column from which to retrieve the value
   * @param map a map of SQL type names to Java classes for custom type mappings
   * @return the value of the specified column as an Object, or null if the value is SQL NULL
   * @throws SQLException if a database access error occurs, the columnLabel is not valid,
   *                      or a mapping for a user-defined type is invalid
   */
  @Override
  public Object getObject(final String columnLabel, final Map<String, Class<?>> map) throws SQLException {
    return resultSet.getObject(columnLabel, map);
  }

  /**
   * Retrieves the value of the designated column as a Ref object.
   *
   * @param columnLabel the label for the column specified with the SQL AS clause.
   *                    If the SQL AS clause was not specified, then the label is the name of the column.
   * @return a Ref object representing the SQL REF value in the specified column.
   * @throws SQLException if a database access error occurs or this method
   *                      is called on a closed result set.
   */
  @Override
  public Ref getRef(final String columnLabel) throws SQLException {
    return resultSet.getRef(columnLabel);
  }

  /**
   * Retrieves the value of the designated column as a {@code Blob} object.
   *
   * @param columnLabel the label for the column specified, which is the name of the column as defined in the database
   * @return the {@code Blob} object representing the value of the specified column
   * @throws SQLException if a database access error occurs or the column label is not valid
   */
  @Override
  public Blob getBlob(final String columnLabel) throws SQLException {
    return resultSet.getBlob(columnLabel);
  }

  /**
   * Retrieves the CLOB (Character Large Object) value of the designated column
   * as specified by the column label.
   *
   * @param columnLabel the label for the column from which to retrieve the CLOB value
   * @return the CLOB object containing the value of the designated column
   * @throws SQLException if a database access error occurs or the columnLabel is invalid
   */
  @Override
  public Clob getClob(final String columnLabel) throws SQLException {
    return resultSet.getClob(columnLabel);
  }

  /**
   * Retrieves the SQL {@code Array} object designated by the specified column label.
   *
   * @param columnLabel the label for the column from which to retrieve the SQL {@code Array}
   * @return the SQL {@code Array} object for the specified column label, or {@code null} if the SQL value is {@code NULL}
   * @throws SQLException if the column label is not valid or a database access error occurs
   */
  @Override
  public Array getArray(final String columnLabel) throws SQLException {
    return resultSet.getArray(columnLabel);
  }

  /**
   * Retrieves the column index for the specified column label in the result set.
   *
   * @param columnLabel the label of the column for which the index is to be retrieved
   * @return the column index corresponding to the specified label
   * @throws SQLException if the column label is not valid or a database access error occurs
   */
  @Override
  public int findColumn(final String columnLabel) throws SQLException {
    return resultSet.findColumn(columnLabel);
  }

  /**
   * Checks if the cursor is positioned before the first row in the result set.
   *
   * @return true if the cursor is before the first row; false otherwise
   * @throws SQLException if a database access error occurs or this method is called on a closed result set
   */
  @Override
  public boolean isBeforeFirst() throws SQLException {
    return resultSet.isBeforeFirst();
  }

  /**
   * Checks if the cursor is positioned after the last row in the ResultSet.
   *
   * @return true if the cursor is after the last row; false otherwise.
   * @throws SQLException if a database access error occurs or this method is called on a closed ResultSet.
   */
  @Override
  public boolean isAfterLast() throws SQLException {
    return resultSet.isAfterLast();
  }

  /**
   * Checks if the cursor is positioned on the first row of the ResultSet.
   *
   * @return true if the cursor is on the first row; false otherwise
   * @throws SQLException if a database access error occurs or the result set is closed
   */
  @Override
  public boolean isFirst() throws SQLException {
    return resultSet.isFirst();
  }

  /**
   * Checks if the current row of the ResultSet object is the last row.
   *
   * @return true if the current row is the last row; false otherwise
   * @throws SQLException if a database access error occurs or this method is called on a closed ResultSet
   */
  @Override
  public boolean isLast() throws SQLException {
    return resultSet.isLast();
  }

  /**
   * Moves the cursor of the underlying ResultSet to the position
   * before the first row. This is typically used to start iterating
   * through the rows of the ResultSet from the beginning.
   *
   * @throws SQLException if a database access error occurs,
   *                      this method is called on a closed ResultSet,
   *                      or the ResultSet type does not support this operation.
   */
  @Override
  public void beforeFirst() throws SQLException {
    resultSet.beforeFirst();
  }

  /**
   * Moves the cursor to the end of the ResultSet, just after the last row.
   * This method is typically used to iterate backward through the ResultSet.
   *
   * @throws SQLException if a database access error occurs or the ResultSet
   *                      is closed.
   */
  @Override
  public void afterLast() throws SQLException {
    resultSet.afterLast();
  }

  /**
   * Moves the cursor to the first row of this ResultSet object.
   *
   * @return true if the cursor is on a valid row; false if there are no rows in the ResultSet.
   * @throws SQLException if a database access error occurs or this method is called on a closed ResultSet.
   */
  @Override
  public boolean first() throws SQLException {
    return resultSet.first();
  }

  /**
   * Moves the cursor to the last row in the result set.
   *
   * @return true if the cursor is moved to a valid row; false if there are no rows in the result set.
   * @throws SQLException if a database access error occurs or the result set type does not support this operation.
   */
  @Override
  public boolean last() throws SQLException {
    return resultSet.last();
  }

  /**
   * Retrieves the current row number from the underlying ResultSet object.
   *
   * @return the current row number, or 0 if there is no current row
   * @throws SQLException if a database access error occurs or this method is called on a closed result set
   */
  @Override
  public int getRow() throws SQLException {
    return resultSet.getRow();
  }

  /**
   * Moves the cursor to the given row number in this result set.
   *
   * @param row the row number to move the cursor to. A positive number moves the cursor
   *            to the given row number with respect to the beginning, while a negative
   *            number moves the cursor with respect to the end.
   * @return true if the cursor is on a valid row after the move; false if the cursor
   *         is positioned before the first row or after the last row.
   * @throws SQLException if a database access error occurs or the result set is closed.
   */
  @Override
  public boolean absolute(final int row) throws SQLException {
    return resultSet.absolute(row);
  }

  /**
   * Moves the cursor a relative number of rows, either forward or backward, from its current position.
   * Positive values move the cursor forward, and negative values move it backward.
   *
   * @param rows the number of rows to move the cursor relative to its current position
   * @return {@code true} if the cursor is on a valid row after the move; {@code false} if the cursor is positioned before the first row or after the last row
   * @throws SQLException if a database access error occurs or the result set is closed
   */
  @Override
  public boolean relative(final int rows) throws SQLException {
    return resultSet.relative(rows);
  }

  /**
   * Moves the cursor to the previous row in the result set.
   *
   * @return true if the cursor is moved to a valid row; false if there are no more rows.
   * @throws SQLException if a database access error occurs or the result set is closed.
   */
  @Override
  public boolean previous() throws SQLException {
    return resultSet.previous();
  }

  /**
   * Retrieves the current fetch direction for the underlying ResultSet.
   *
   * @return the current fetch direction; this will be one of ResultSet.FETCH_FORWARD,
   *         ResultSet.FETCH_REVERSE, or ResultSet.FETCH_UNKNOWN.
   * @throws SQLException if a database access error occurs or if the ResultSet is closed.
   */
  @Override
  public int getFetchDirection() throws SQLException {
    return resultSet.getFetchDirection();
  }

  /**
   * Sets the fetch direction for the ResultSet object to the given direction value.
   * The fetch direction gives a hint to the driver about the direction in which
   * the rows in the ResultSet will be processed.
   *
   * @param direction an integer value indicating the fetch direction.
   *                  The allowed values are ResultSet.FETCH_FORWARD,
   *                  ResultSet.FETCH_REVERSE, or ResultSet.FETCH_UNKNOWN.
   * @throws SQLException if a database access error occurs or the given
   *                       fetch direction is not supported.
   */
  @Override
  public void setFetchDirection(final int direction) throws SQLException {
    resultSet.setFetchDirection(direction);
  }

  /**
   * Retrieves the fetch size for the ResultSet associated with this method.
   * The fetch size determines how many rows the database fetches at a time from
   * the database server when more rows are needed for processing.
   *
   * @return the fetch size for the ResultSet
   * @throws SQLException if a database access error occurs
   */
  @Override
  public int getFetchSize() throws SQLException {
    return resultSet.getFetchSize();
  }

  /**
   * Sets the number of rows that should be fetched from the database when more rows are needed.
   * This can improve performance by reducing the number of database fetch operations.
   *
   * @param rows the number of rows to fetch.
   * @throws SQLException if a database access error occurs or the parameter value is invalid.
   */
  @Override
  public void setFetchSize(final int rows) throws SQLException {
    resultSet.setFetchSize(rows);
  }

  /**
   * Retrieves the type of the underlying `ResultSet` object.
   *
   * @return an integer representing the `ResultSet` type.
   * @throws SQLException if a database*/
  @Override
  public int getType() throws SQLException {
    return resultSet.getType();
  }

  /**
   * Retrieves the concurrency mode of the result set.
   *
   * @return an integer representing the concurrency mode of the result set.
   * @throws SQLException if a database access error occurs or the result set is closed.
   */
  @Override
  public int getConcurrency() throws SQLException {
    return resultSet.getConcurrency();
  }

  /**
   * Checks if the current row of the result set has been updated.
   *
   * @return true if the current row has been updated and the result set type is a type that detects changes; false otherwise
   * @throws SQLException if a database access error occurs or the method is called on a closed result set
   */
  @Override
  public boolean rowUpdated() throws SQLException {
    return resultSet.rowUpdated();
  }

  /**
   * Checks whether the current row in the ResultSet was successfully inserted.
   *
   * @return true if the current row was inserted, false otherwise
   * @throws SQLException if a database access error occurs
   */
  @Override
  public boolean rowInserted() throws SQLException {
    return resultSet.rowInserted();
  }

  /**
   * Checks whether the current row in the ResultSet has been deleted.
   *
   * This method provides a way to determine if the current row, positioned
   * in the ResultSet, has been deleted from the database. It relies on the
   * underlying ResultSet's implementation of the rowDeleted method.
   *
   * @return true if the current row has been deleted and false otherwise
   * @throws SQLException if a database access error occurs
   */
  @Override
  public boolean rowDeleted() throws SQLException {
    return resultSet.rowDeleted();
  }

  /**
   * Updates the designated column with a SQL NULL value.
   *
   * @param columnIndex the index of the column to update. The first column is 1, the second is 2, and so on.
   * @throws SQLException if a database access error occurs or if the columnIndex is invalid.
   */
  @Override
  public void updateNull(final int columnIndex) throws SQLException {
    resultSet.updateNull(columnIndex);
  }

  /**
   * Updates the designated column with a boolean value. The update is applied
   * to the ResultSet object and can be persisted into the underlying database
   * by calling the updateRow method.
   *
   * @param columnIndex the one-based index of the column to update
   * @param x the new boolean value to set in the specified column
   * @throws SQLException if the column index is invalid, the ResultSet is closed,
   *                       or a database access error occurs
   */
  @Override
  public void updateBoolean(final int columnIndex, final boolean x) throws SQLException {
    resultSet.updateBoolean(columnIndex, x);
  }

  /**
   * Updates the designated column with a new byte value.
   *
   * @param columnIndex the index of the column, starting from 1, where the byte value will be updated
   * @param x the new byte value to set in the specified column
   * @throws SQLException if a database access error occurs or if this method is called on a closed result set
   */
  @Override
  public void updateByte(final int columnIndex, final byte x) throws SQLException {
    resultSet.updateByte(columnIndex, x);
  }

  /**
   * Updates the designated column with a short value.
   * The update is made to the current row of the ResultSet object.
   *
   * @param columnIndex the index of the column to update, where the first column is 1
   * @param x the new column value as a short
   * @throws SQLException if a database access error occurs, the ResultSet is in read-only mode,
   *                      or the columnIndex is not valid
   */
  @Override
  public void updateShort(final int columnIndex, final short x) throws SQLException {
    resultSet.updateShort(columnIndex, x);
  }

  /**
   * Updates the designated column with an int value. The updater methods are used to update column
   * values in the current row or the insert row.
   *
   * @param columnIndex the column index (1-based) of the column to update
   * @param x the new column value
   * @throws SQLException if a database access error occurs or the columnIndex is invalid
   */
  @Override
  public void updateInt(final int columnIndex, final int x) throws SQLException {
    resultSet.updateInt(columnIndex, x);
  }

  /**
   * Updates the designated column with a long value.
   * The updater methods are used to update column values in the current row or the insert row.
   *
   * @param columnIndex the index of the column to update, starting from 1
   * @param x the new long value to be set in the column
   * @throws SQLException if a database access error occurs or if the result set is read-only
   */
  @Override
  public void updateLong(final int columnIndex, final long x) throws SQLException {
    resultSet.updateLong(columnIndex, x);
  }

  /**
   * Updates the designated column with a float value.
   * The updater methods are used to update column values in the current row or the insert row.
   *
   * @param columnIndex the index of the column to update, starting from 1
   * @param x the new column value as a float
   * @throws SQLException if a database access error occurs or if the method is called on a closed result set
   */
  @Override
  public void updateFloat(final int columnIndex, final float x) throws SQLException {
    resultSet.updateFloat(columnIndex, x);
  }

  /**
   * Updates the designated column with a double value. The column is specified by its index.
   * This method must be called on an updatable ResultSet, and the change is only reflected in
   * the ResultSet until updateRow() is called to propagate the change to the underlying database.
   *
   * @param columnIndex the first column is 1, the second is 2, and so on
   * @param x the new column value as a double
   * @throws SQLException if a database access error occurs or if the column index is not valid
   */
  @Override
  public void updateDouble(final int columnIndex, final double x) throws SQLException {
    resultSet.updateDouble(columnIndex, x);
  }

  /**
   * Updates the designated column with a BigDecimal value.
   * The update is made to the current row of the result set.
   *
   * @param columnIndex the first column is 1, the second is 2, and so on.
   * @param x the new column value as a BigDecimal. A null value indicates
   *          SQL NULL.
   * @throws SQLException if a database access error occurs, this method is
   *                       called on a closed result set, or the ResultSet is
   *                       in an invalid state.
   */
  @Override
  public void updateBigDecimal(final int columnIndex, final BigDecimal x) throws SQLException {
    resultSet.updateBigDecimal(columnIndex, x);
  }

  /**
   * Updates the designated column with a provided string value.
   * This method must be called on a valid column index within the ResultSet.
   *
   * @param columnIndex the index of the column to update, starting from 1
   * @param x the new string value to set; can be null
   * @throws SQLException if a database access error occurs or if the provided index is invalid
   */
  @Override
  public void updateString(final int columnIndex, final String x) throws SQLException {
    resultSet.updateString(columnIndex, x);
  }

  /**
   * Updates the designated column with a byte array value. The updated value
   * will be written to the database when updateRow, insertRow, or deleteRow
   * is called.
   *
   * @param columnIndex the first column is 1, the second is 2, and so on
   * @param x the new byte array value for the specified column
   * @throws SQLException if the columnIndex is invalid; if a database access
   *         error occurs; if this method is called on a closed result set;
   *         or if this method is called in a read-only result set
   */
  @Override
  public void updateBytes(final int columnIndex, final byte[] x) throws SQLException {
    resultSet.updateBytes(columnIndex, x);
  }

  /**
   * Updates the designated column with a Date object.
   * The updater methods are used to update column values in the current row or the insert row.
   *
   * @param columnIndex the index of the column to update (1-based)
   * @param x the new column value as a Date object
   * @throws SQLException if a database access error occurs or if the column index is invalid
   */
  @Override
  public void updateDate(final int columnIndex, final Date x) throws SQLException {
    resultSet.updateDate(columnIndex, x);
  }

  /**
   * Updates the designated column with a {@code Time} value.
   * The update is made in the current row of the ResultSet.
   *
   * @param columnIndex the first column is 1, the second is 2, and so on
   * @param x the new column value; must be a {@code Time} object
   * @throws SQLException if the column index is invalid, if a database access error occurs,
   * or if the ResultSet is in read-only mode
   */
  @Override
  public void updateTime(final int columnIndex, final Time x) throws SQLException {
    resultSet.updateTime(columnIndex, x);
  }

  /**
   * Updates the designated column with a {@link Timestamp} value.
   *
   * @param columnIndex the first column is 1, the second is 2, and so on.
   * @param x the new column value; can be null to update the column with SQL NULL.
   * @throws SQLException if the column index is invalid, this method is called on a closed result set,
   *                      or a database access error occurs.
   */
  @Override
  public void updateTimestamp(final int columnIndex, final Timestamp x) throws SQLException {
    resultSet.updateTimestamp(columnIndex, x);
  }

  /**
   * Updates the designated column with an ASCII stream value.
   * The data in the stream can be read and is then written to the database.
   *
   * @param columnIndex the index of the column to update, starting from 1
   * @param x the input stream containing the ASCII data to set
   * @param length the number of bytes in the input stream to write to the column
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  public void updateAsciiStream(final int columnIndex, final InputStream x, final int length) throws SQLException {
    resultSet.updateAsciiStream(columnIndex, x, length);
  }

  /**
   * Updates the designated column with a binary stream value.
   *
   * @param columnIndex the index of the column to update, where the first column is 1
   * @param x the InputStream containing the binary data to set in the column
   * @param length the number of bytes in the InputStream to read and write to the column
   * @throws SQLException if a database access error occurs or the columnIndex is invalid
   */
  @Override
  public void updateBinaryStream(final int columnIndex, final InputStream x, final int length) throws SQLException {
    resultSet.updateBinaryStream(columnIndex, x, length);
  }

  /**
   * Updates the designated column with a character stream value. The data will be read from
   * the provided Reader object and written to the database. The number of characters to be written
   * is specified by the length parameter.
   *
   * @param columnIndex the index of the column to be updated, starting from 1
   * @param x the Reader object containing the character data to be written
   * @param length the number of characters to be written from the Reader
   * @throws SQLException if a database access error occurs or if the columnIndex is invalid
   */
  @Override
  public void updateCharacterStream(final int columnIndex, final Reader x, final int length) throws SQLException {
    resultSet.updateCharacterStream(columnIndex, x, length);
  }

  /**
   * Updates the designated column with an Object value. The scale or length
   * is used for certain types of updates such as updating a numeric value
   * or strings where precision or length needs to be specified.
   *
   * @param columnIndex the index of the column (starting at 1) to be updated
   * @param x the new value for the column
   * @param scaleOrLength for numeric and string types, this is the precision or
   *                      length to be used; otherwise, ignored
   * @throws SQLException if a database access error occurs or if this method
   *                       is called on a closed result set
   */
  @Override
  public void updateObject(final int columnIndex, final Object x, final int scaleOrLength) throws SQLException {
    resultSet.updateObject(columnIndex, x, scaleOrLength);
  }

  /**
   * Updates the value of the designated column with the given object.
   *
   * @param columnIndex the index of the column to update, starting from 1
   * @param x the new value for the column, as an Object
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  public void updateObject(final int columnIndex, final Object x) throws SQLException {
    resultSet.updateObject(columnIndex, x);
  }

  /**
   * Updates the designated column with a null value. This method is typically used
   * when updating database records to explicitly set a column to NULL.
   *
   * @param columnLabel the label for the column specified with the SQL AS clause.
   *                    If the SQL AS clause was not specified, the column name is used.
   * @throws SQLException if a database access error occurs or the result set is not
   *                      updatable.
   */
  @Override
  public void updateNull(final String columnLabel) throws SQLException {
    resultSet.updateNull(columnLabel);
  }

  /**
   * Updates the designated column with a boolean value.
   *
   * @param columnLabel the label for the column specified to be updated
   * @param x the new boolean value to update the column with
   * @throws SQLException if a database access error occurs or the columnLabel is not valid
   */
  @Override
  public void updateBoolean(final String columnLabel, final boolean x) throws SQLException {
    resultSet.updateBoolean(columnLabel, x);
  }

  /**
   * Updates the designated column with a byte value. The update is applied to the current row
   * of the ResultSet object.
   *
   * @param columnLabel the label for the column to be updated. It must match the column name as
   *                    defined in the database table.
   * @param x the new byte value that will be assigned to the specified column.
   * @throws SQLException if a database access error occurs, the column label is not valid, or
   *                      the ResultSet is in read-only mode.
   */
  @Override
  public void updateByte(final String columnLabel, final byte x) throws SQLException {
    resultSet.updateByte(columnLabel, x);
  }

  /**
   * Updates the designated column with a short value.
   * The updated value is not immediately written to the database but is cached in the ResultSet until {@code updateRow()} is called.
   *
   * @param columnLabel the label for the column to be updated
   * @param x the new column value as a short
   * @throws SQLException if a database access error occurs, this method is called on a closed ResultSet,
   *                      or if the columnLabel is not valid
   */
  @Override
  public void updateShort(final String columnLabel, final short x) throws SQLException {
    resultSet.updateShort(columnLabel, x);
  }

  /**
   * Updates the designated column with the specified integer value.
   * This method is used to update the value of a column in the current row
   * of the ResultSet object. The column is specified by its label.
   *
   * @param columnLabel the label for the column to be updated
   * @param x the new integer value for the column
   * @throws SQLException if a database access error occurs or the result set
   *         is not updatable
   */
  @Override
  public void updateInt(final String columnLabel, final int x) throws SQLException {
    resultSet.updateInt(columnLabel, x);
  }

  /**
   * Updates the designated column with a specified long value. The updater methods are used
   * to update column values in the current row or the insert row*/
  @Override
  public void updateLong(final String columnLabel, final long x) throws SQLException {
    resultSet.updateLong(columnLabel, x);
  }

  /**
   * Updates the designated column with a float value. The update is applied to
   * the current row of the ResultSet object.
   *
   * @param columnLabel the label for the column that is to be updated
   * @param x the new column value as a float
   * @throws SQLException if an error occurs while updating the column or if the
   *         ResultSet is in an invalid state for the update
   */
  @Override
  public void updateFloat(final String columnLabel, final float x) throws SQLException {
    resultSet.updateFloat(columnLabel, x);
  }

  /**
   * Updates the designated column with a specified double value.
   *
   * @param columnLabel the label for the column specified, which is the name of the column
   * @param x the new column value as a double
   * @throws SQLException if a database access error occurs, the result set is closed,
   *         or the columnLabel is not valid
   */
  @Override
  public void updateDouble(final String columnLabel, final double x) throws SQLException {
    resultSet.updateDouble(columnLabel, x);
  }

  /**
   * Updates the designated column with a {@code BigDecimal} value.
   * The update is made to the underlying database as well as to the
   * {@code ResultSet} object.
   *
   * @param columnLabel the label of the column to update
   * @param x the new {@code BigDecimal} value to assign to the column;
   *          can be {@code null}
   * @throws SQLException if a database access error occurs
   */
  @Override
  public void updateBigDecimal(final String columnLabel, final BigDecimal x) throws SQLException {
    resultSet.updateBigDecimal(columnLabel, x);
  }

  /**
   * Updates the designated column with a new String value.
   * The update is applicable to the current row of the ResultSet.
   *
   * @param columnLabel the label for the column to be updated
   * @param x the new String value to update the column with
   * @throws SQLException if a database access error occurs,
   *                      the columnLabel is not valid or the ResultSet is in an invalid state
   */
  @Override
  public void updateString(final String columnLabel, final String x) throws SQLException {
    resultSet.updateString(columnLabel, x);
  }

  /**
   * Updates the designated column with a byte array value.
   * The updateBytes method should be used when updating columns in
   * a ResultSet that is updateable.
   *
   * @param columnLabel the label for the column to be updated
   * @param x the new column value stored as a byte array
   * @throws SQLException if a database access error occurs or the ResultSet cannot be updated
   */
  @Override
  public void updateBytes(final String columnLabel, final byte[] x) throws SQLException {
    resultSet.updateBytes(columnLabel, x);
  }

  /**
   * Updates the designated column with a Date value. This method is used to update
   * the value of a specified column in the current row of the ResultSet with a Date object.
   *
   * @param columnLabel the label for the column specified with the SQL AS clause.
   *        If the SQL AS clause was not specified, the label is the name of the column.
   * @param x the new Date value to update the specified column with. This value can be null.
   * @throws SQLException if a database access error occurs or the ResultSet is in read-only mode.
   */
  @Override
  public void updateDate(final String columnLabel, final Date x) throws SQLException {
    resultSet.updateDate(columnLabel, x);
  }

  /**
   * Updates the designated column with a {@code Time} value.
   * The update is made to the current row of the {@link ResultSet} object.
   *
   * @param columnLabel the label of the column to be updated
   * @param x the new column {@code Time} value
   * @throws SQLException if a database access error occurs or the columnLabel
   *         is not valid
   */
  @Override
  public void updateTime(final String columnLabel, final Time x) throws SQLException {
    resultSet.updateTime(columnLabel, x);
  }

  /**
   * Updates the designated column with a Timestamp value.
   * This method updates the column denoted by the specified column label
   * in the current row of this ResultSet object.
   *
   * @param columnLabel the label for the column to be updated
   * @param x the new column value as a Timestamp object; may be null
   * @throws SQLException if a database access error occurs, the ResultSet is closed,
   *                       or if the provided column label does not exist
   */
  @Override
  public void updateTimestamp(final String columnLabel, final Timestamp x) throws SQLException {
    resultSet.updateTimestamp(columnLabel, x);
  }

  /**
   * Updates the designated column with an ASCII stream value.
   * The input stream must contain ASCII characters. The stream must be fully read before
   * setting the value to the designated column.
   *
   * @param columnLabel the label for the column to be updated
   * @param x the input stream containing the ASCII data
   * @param length the number of bytes in the input stream
   * @throws SQLException if a database access error occurs or the columnLabel is not valid
   */
  @Override
  public void updateAsciiStream(final String columnLabel, final InputStream x, final int length) throws SQLException {
    resultSet.updateAsciiStream(columnLabel, x, length);
  }

  /**
   * Updates the designated column with a binary input stream containing the specified number of bytes.
   * This method is used to store binary data into a column.
   *
   * @param columnLabel the label for the column specified with the update
   * @param x the InputStream object that contains the binary data to be written
   * @param length the number of bytes in the InputStream to be written to the column
   * @throws SQLException if a database access error occurs or the columnLabel is not valid
   */
  @Override
  public void updateBinaryStream(final String columnLabel, final InputStream x, final int length) throws SQLException {
    resultSet.updateBinaryStream(columnLabel, x, length);
  }

  /**
   * Updates the designated column with a character stream value. The character stream will have
   * a specified length in characters.
   *
   * @param columnLabel the label of the column to update
   * @param reader the java.io.Reader object that contains the character stream
   * @param length the number of characters in the input stream
   * @throws SQLException if a database access error occurs or the value is invalid
   */
  @Override
  public void updateCharacterStream(final String columnLabel, final Reader reader, final int length) throws SQLException {
    resultSet.updateCharacterStream(columnLabel, reader, length);
  }

  /**
   * Updates the designated column with an object value. The update is applied to
   * the specified column in the current row of this ResultSet object.
   *
   * @param columnLabel the label for the column specified with the update
   * @param x the new column value, which must be an instance of a class supported by the JDBC driver
   *          or null to set the column value to SQL NULL
   * @param scaleOrLength for an input parameter of SQL type DECIMAL or NUMERIC, this is the
   *                      number of digits after the decimal point. For Java Object types
   *                      InputStream and Reader, this is the length of the data in the stream or reader.
   *                      For all other types, this value will be ignored.
   * @throws SQLException if a database access error occurs or this method is called on a closed ResultSet
   */
  @Override
  public void updateObject(final String columnLabel, final Object x, final int scaleOrLength) throws SQLException {
    resultSet.updateObject(columnLabel, x, scaleOrLength);
  }

  /**
   * Updates the designated column with an Object value.
   * The update is applied to the current row of the ResultSet, and will be written to the database
   * when the ResultSet is updated.
   *
   * @param columnLabel the label of the column to be updated
   * @param x the new column value to be set, which can be any object type
   * @throws SQLException if a database access error occurs or if the columnLabel is not valid
   */
  @Override
  public void updateObject(final String columnLabel, final Object x) throws SQLException {
    resultSet.updateObject(columnLabel, x);
  }

  /**
   * Inserts the current row into the database.
   *
   * This method delegates the call to {@link ResultSet#insertRow()}.
   * The method inserts the contents of the insert row into the database.
   * It must be called while the cursor is positioned on the insert row.
   * After the insertion, the cursor will remain positioned on the insert row.
   *
   * @throws SQLException if a database access error occurs or if this method
   *         is called when the cursor is not on the insert row.
   */
  @Override
  public void insertRow() throws SQLException {
    resultSet.insertRow();
  }

  /**
   * Updates the current row in the database using the current values of the
   * ResultSet. This method writes any changes made to the result set to
   * the database.
   *
   * @throws SQLException if a database access error occurs, the ResultSet
   *         is in read-only mode, or if this method is called on a
   *         ResultSet that is not positioned on a valid row.
   */
  @Override
  public void updateRow() throws SQLException {
    resultSet.updateRow();
  }

  /**
   * Deletes the current row from the database.
   * This method removes the current row in the ResultSet object
   * and the corresponding row in the database.
   *
   * @throws SQLException if a database access error occurs,
   *         the current row is not valid, or the ResultSet object is
   *         not updatable.
   */
  @Override
  public void deleteRow() throws SQLException {
    resultSet.deleteRow();
  }

  /**
   * Refreshes the current row of the ResultSet object to reflect the most recent
   * changes made to the database. This method is particularly useful when
   * concurrent updates are made to the underlying database, allowing the ResultSet
   * to synchronize its data with the latest state of the database row.
   *
   * @throws SQLException if a database access error occurs or if the ResultSet
   *         is closed.
   */
  @Override
  public void refreshRow() throws SQLException {
    resultSet.refreshRow();
  }

  /**
   * Cancels the updates made to the current row in this ResultSet object.
   *
   * If the cancelRowUpdates method is called, the updates made to the current row
   * will be discarded and no changes will be applied when the ResultSet is updated.
   *
   * This method delegates the call to the wrapped ResultSet's cancelRowUpdates method.
   *
   * @throws SQLException if a database access error occurs or this method is called
   *                       on a closed ResultSet.
   */
  @Override
  public void cancelRowUpdates() throws SQLException {
    resultSet.cancelRowUpdates();
  }

  /**
   * Moves the cursor to the insert row of the ResultSet object.
   * The insert row is a special row associated with a ResultSet
   * object that is used as a staging ground for building a row
   * to be inserted. Only columns with a defined value can be updated
   * when on the insert row.
   *
   * This method provides a convenient way to prepare a row for insert
   * operations. After setting the desired column values using the
   * appropriate updater methods, the row can be committed to the
   * database using the `insertRow` method of the ResultSet.
   *
   * @throws SQLException if a database access error occurs or
   *         if this method is called on a closed ResultSet.
   */
  @Override
  public void moveToInsertRow() throws SQLException {
    resultSet.moveToInsertRow();
  }

  /**
   * Moves the cursor to the remembered cursor position, usually the position
   * where the cursor was located prior to calling the {@code moveToInsertRow} method.
   * This method is typically used when working with updatable result sets, allowing the
   * cursor to return to its previous position after inserting a row.
   *
   * @throws SQLException if a database access error occurs or this method is
   * called on a ResultSet object that is not updatable.
   */
  @Override
  public void moveToCurrentRow() throws SQLException {
    resultSet.moveToCurrentRow();
  }

  /**
   * Retrieves the Statement object that produced this ResultSet object.
   *
   * @return the Statement object that produced this ResultSet or null if the result set was generated
   *         by a DatabaseMetaData method or if this method is called on a closed result set.
   * @throws SQLException if a database access error occurs or this method is called on a closed result set.
   */
  @Override
  public Statement getStatement() throws SQLException {
    return resultSet.getStatement();
  }

  /**
   * Updates the designated column with an ASCII stream value.
   * The data will be read from the provided InputStream and should match the specified length.
   * This method can be used to update a column in the current row of a ResultSet object.
   *
   * @param columnIndex the index of the column to update, starting from 1
   * @param x the InputStream object containing the ASCII stream data
   * @param length the length of the ASCII stream in bytes
   * @throws SQLException if a database access error occurs or the ResultSet is not updatable
   */
  @Override
  public void updateAsciiStream(final int columnIndex, final InputStream x, final long length) throws SQLException {
    resultSet.updateAsciiStream(columnIndex, x, length);
  }

  /**
   * Updates the designated column with a binary stream value. The data will be read from the given
   * InputStream object starting from the current position to the specified length. The stream must
   * provide at least the number of bytes specified by the length parameter.
   *
   * @param columnIndex the index of the column to update (1-based index)
   * @param x the InputStream containing the binary data
   * @param length the number of bytes to write from the InputStream
   * @throws SQLException if a database access error occurs or if any argument is invalid
   */
  @Override
  public void updateBinaryStream(final int columnIndex, final InputStream x, final long length) throws SQLException {
    resultSet.updateBinaryStream(columnIndex, x, length);
  }

  /**
   * Updates the designated column with a character stream value.
   * The data will be read from the provided Reader object.
   *
   * @param columnIndex the 1-based index of the column to update
   * @param x the Reader object that contains the new character stream value
   * @param length the number of characters in the stream
   * @throws SQLException if a database access error occurs, the column index is out of bounds,
   *         or if the provided Reader is null
   */
  @Override
  public void updateCharacterStream(final int columnIndex, final Reader x, final long length) throws SQLException {
    resultSet.updateCharacterStream(columnIndex, x, length);
  }

  /**
   * Updates the designated column with an ASCII stream value. The stream must contain
   * ASCII characters and is read to the specified number of characters. If the designated
   * column does not support ASCII stream updates, a SQLException will be thrown.
   *
   * @param columnLabel the label of the column to be updated
   * @param x the InputStream containing the ASCII data
   * @param length the number of bytes to read from the InputStream
   * @throws SQLException if a database access error occurs, if the columnLabel does not exist,
   *                       or if this method is called on a closed result set
   */
  @Override
  public void updateAsciiStream(final String columnLabel, final InputStream x, final long length) throws SQLException {
    resultSet.updateAsciiStream(columnLabel, x, length);
  }

  /**
   * Updates the designated column with a binary stream value.
   * The data will be read from the supplied {@code InputStream} and written to the column,
   * according to the specified length.
   *
   * @param columnLabel the label for the column to be updated
   * @param x the {@code InputStream} object that contains the new column value
   * @param length the number of bytes in the stream to read
   * @throws SQLException if a database access error occurs or if the columnLabel is not valid
   */
  @Override
  public void updateBinaryStream(final String columnLabel, final InputStream x, final long length) throws SQLException {
    resultSet.updateBinaryStream(columnLabel, x, length);
  }

  /**
   * Updates the designated column with a character stream value.
   * The data in the stream will have the specified length.
   *
   * @param columnLabel the label for the column name
   * @param reader the java.io.Reader object that contains the data to set the column value to
   * @param length the number of characters in the stream
   * @throws SQLException if the columnLabel is not valid, a database access error occurs,
   *                       the result set is closed, or if this method is called on a read-only result set
   */
  @Override
  public void updateCharacterStream(final String columnLabel, final Reader reader, final long length) throws SQLException {
    resultSet.updateCharacterStream(columnLabel, reader, length);
  }

  /**
   * Updates the designated column with a {@link Blob} value.
   *
   * The updateBlob method updates the current row's column with the
   * specified Blob object. The update is not persisted in the database
   * until the {@code updateRow()} method is called.
   *
   * @param columnIndex the index of the column to update, starting from 1
   * @param x the new Blob value to assign to the column
   * @throws SQLException if a database access error occurs or the result set
   *         is not updatable
   */
  @Override
  public void updateBlob(final int columnIndex, final Blob x) throws SQLException {
    resultSet.updateBlob(columnIndex, x);
  }

  /**
   * Updates the designated column with a Blob value. The updater methods are
   * used to update column values in the current row or the insert row. The
   * update is not applied to the database until the method `updateRow` is called.
   *
   * @param columnLabel the label for the column specified with the SQL query.
   * @param x the Blob object that contains the data to set the value in the specified column.
   * @throws SQLException if a database access error occurs or this method is
   * called on a closed ResultSet.
   */
  @Override
  public void updateBlob(final String columnLabel, final Blob x) throws SQLException {
    resultSet.updateBlob(columnLabel, x);
  }

  /**
   * Updates the designated column with a Clob value. The column is specified by its index,
   * and the value is provided as a Clob object. This method updates the Clob value on the
   * underlying database structure.
   *
   * @param columnIndex the index of the column to update, starting from 1.
   * @param x the Clob object containing the new value to set for the specified column.
   * @throws SQLException if a database access error occurs or the operation is not supported.
   */
  @Override
  public void updateClob(final int columnIndex, final Clob x) throws SQLException {
    resultSet.updateClob(columnIndex, x);
  }

  /**
   * Updates the designated column with a Clob value. The updater methods are used to update column
   * values in the result set, which can then be updated in the underlying database.
   *
   * @param columnLabel the label for the column specified in the SQL query
   * @param x the Clob value to update the column with
   * @throws SQLException if a database access error occurs or the result set is in read-only mode
   */
  @Override
  public void updateClob(final String columnLabel, final Clob x) throws SQLException {
    resultSet.updateClob(columnLabel, x);
  }

  /**
   * Updates the designated column with an Array value.
   *
   * @param columnIndex the index of the column to be updated, where the first column is 1
   * @param x the new Array value to update the column with
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  public void updateArray(final int columnIndex, final Array x) throws SQLException {
    resultSet.updateArray(columnIndex, x);
  }

  /**
   * Updates the designated column with an Array value.
   *
   * @param columnLabel the label for the column to be updated
   * @param x the Array object to update the column with
   * @throws SQLException if a database access error occurs or the columnLabel is invalid
   */
  @Override
  public void updateArray(final String columnLabel, final Array x) throws SQLException {
    resultSet.updateArray(columnLabel, x);
  }

  /**
   * Retrieves the RowId object that corresponds to the specified column index in the result set.
   *
   * @param columnIndex the column index, starting from 1, for which the RowId is to be retrieved
   * @return the RowId value for the specified column index
   * @throws SQLException if a database access error occurs or the columnIndex is invalid
   */
  @Override
  public RowId getRowId(final int columnIndex) throws SQLException {
    return resultSet.getRowId(columnIndex);
  }

  /**
   * Retrieves the RowId object corresponding to the specified column label.
   *
   * @param columnLabel the label for the column from which to retrieve the RowId
   * @return the RowId object for the specified column
   * @throws SQLException if a database access error occurs or the column label is invalid
   */
  @Override
  public RowId getRowId(final String columnLabel) throws SQLException {
    return resultSet.getRowId(columnLabel);
  }

  /**
   * Updates the designated column with a RowId value.
   * The updateRowId method provides the capability to update the column with a RowId object
   * which represents the SQL ROWID data type. The update is performed for the current row
   * of the ResultSet.
   *
   * @param columnIndex the 1-based index of the column to be updated
   * @param x the RowId object containing the new value for the column
   * @throws SQLException if a database access error occurs or the result set is not updatable
   */
  @Override
  public void updateRowId(final int columnIndex, final RowId x) throws SQLException {
    resultSet.updateRowId(columnIndex, x);
  }

  /**
   * Updates the designated column with a new RowId value.
   *
   * @param columnLabel the label for the column specified by the SQL AS clause.
   *                    If the SQL AS clause was not specified, the name of the column is used.
   * @param x the new RowId value to update in the specified column
   * @throws SQLException if a database access error occurs or the method is called on a closed ResultSet
   */
  @Override
  public void updateRowId(final String columnLabel, final RowId x) throws SQLException {
    resultSet.updateRowId(columnLabel, x);
  }

  /**
   * Retrieves the current holdability of ResultSet objects created using this method.
   *
   * @return an integer representing the holdability of ResultSet objects. The value is
   *         either ResultSet.HOLD_CURSORS_OVER_COMMIT or ResultSet.CLOSE_CURSORS_AT_COMMIT.
   * @throws SQLException if a database access error occurs.
   */
  @Override
  public int getHoldability() throws SQLException {
    return resultSet.getHoldability();
  }

  /**
   * Checks if the {@code ResultSet} is closed.
   *
   * @return {@code true} if the {@code ResultSet} is closed; {@code false} otherwise.
   * @throws SQLException if a database access error occurs.
   */
  @Override
  public boolean isClosed() throws SQLException {
    return resultSet.isClosed();
  }

  /**
   * Updates the designated column with a String value. The column index specifies
   * the column to be updated, and the String value provided will be stored in the
   * appropriate column of the underlying database.
   *
   * @param columnIndex the first column is 1, the second is 2, and so on
   * @param nString the new value for the designated column. Can be null.
   * @throws SQLException if a database access error occurs or this method is
   *         called on a closed ResultSet
   */
  @Override
  public void updateNString(final int columnIndex, final String nString) throws SQLException {
    resultSet.updateNString(columnIndex, nString);
  }

  /**
   * Updates the designated column with the given string value. The updateNString method should
   * be used when the column stores a national character set value.
   *
   * @param columnLabel the label for the column to be updated
   * @param nString     the new column value of type String
   * @throws SQLException if a database access error occurs or if the method is called on a closed result set
   */
  @Override
  public void updateNString(final String columnLabel, final String nString) throws SQLException {
    resultSet.updateNString(columnLabel, nString);
  }

  /**
   * Updates the designated column with a given NClob value.
   * Updates the NClob object in the ResultSet to reflect changes.
   *
   * @param columnIndex the column index in the ResultSet, starting at 1, which needs to be updated
   * @param nClob the NClob object that holds the new value to assign to the column
   * @throws SQLException if a database access error occurs or the ResultSet is in a read-only state
   */
  @Override
  public void updateNClob(final int columnIndex, final NClob nClob) throws SQLException {
    resultSet.updateNClob(columnIndex, nClob);
  }

  /**
   * Updates the designated column with a given NClob object. The column is specified by
   * its label, and the input NClob object will be used to update the value in the
   * ResultSet. This method can be used to update NClob data in a database.
   *
   * @param columnLabel the label for the column to be updated
   * @param nClob the NClob object containing the data to update the column with
   * @throws SQLException if a database access error occurs or this method is called
   * on a closed ResultSet
   */
  @Override
  public void updateNClob(final String columnLabel, final NClob nClob) throws SQLException {
    resultSet.updateNClob(columnLabel, nClob);
  }

  /**
   * Retrieves the value of the specified column as a {@code NClob} object.
   *
   * @param columnIndex the index of the column from which the value is to be retrieved,
   *                    starting at 1 for the first column.
   * @return the {@code NClob} object representing the value of the specified column,
   *         or {@code null} if the column contains SQL {@code NULL}.
   * @throws SQLException if a database access error occurs or if the column index is invalid.
   */
  @Override
  public NClob getNClob(final int columnIndex) throws SQLException {
    return resultSet.getNClob(columnIndex);
  }

  /**
   * Retrieves the value of the designated column as a {@link NClob} object.
   *
   * @param columnLabel the label for the column specified with the SQL AS clause.
   *                    If the SQL AS clause was not specified, then the label is the name of the column.
   * @return the value of the designated column as a {@link NClob} object;
   *         if the value is SQL NULL, the method returns null.
   * @throws SQLException if a database access error occurs or this method is called on a closed result set.
   */
  @Override
  public NClob getNClob(final String columnLabel) throws SQLException {
    return resultSet.getNClob(columnLabel);
  }

  /**
   * Retrieves the value of the designated column in the current row of this
   * ResultSet object as a SQLXML object.
   *
   * @param columnIndex the column index, starting from 1 for the first column
   * @return the SQLXML value of the specified column in the current row
   * @throws SQLException if a database access error occurs or the column index is not valid
   */
  @Override
  public SQLXML getSQLXML(final int columnIndex) throws SQLException {
    return resultSet.getSQLXML(columnIndex);
  }

  /**
   * Retrieves the value of the designated column as a {@code SQLXML} object.
   *
   * @param columnLabel the label for the column specified in the query.
   * @return the {@code SQLXML} object representing the SQL XML value of the specified column.
   * @throws SQLException if there is an error accessing the SQL data or if the column does not support SQLXML values.
   */
  @Override
  public SQLXML getSQLXML(final String columnLabel) throws SQLException {
    return resultSet.getSQLXML(columnLabel);
  }

  /**
   * Updates the designated column with the given SQLXML value.
   *
   * @param columnIndex the first column is 1, the second is 2, and so on.
   * @param xmlObject the SQLXML object representing the XML value to update the column with.
   * @throws SQLException if a database access error occurs or if the columnIndex is invalid.
   */
  @Override
  public void updateSQLXML(final int columnIndex, final SQLXML xmlObject) throws SQLException {
    resultSet.updateSQLXML(columnIndex, xmlObject);
  }

  /**
   * Updates the designated column with an SQLXML value. The update is performed
   * on the current row of the ResultSet.
   *
   * @param columnLabel the label for the column specified with the SQL XML data type.
   * @param xmlObject the SQLXML object containing the data to set in the specified column.
   * @throws SQLException if an error occurs while updating the SQLXML value or if the ResultSet is in read-only mode.
   */
  @Override
  public void updateSQLXML(final String columnLabel, final SQLXML xmlObject) throws SQLException {
    resultSet.updateSQLXML(columnLabel, xmlObject);
  }

  /**
   * Updates the designated column with a character stream value. The data will be read
   * from the provided Reader object and should have a specified length.
   *
   * @param columnIndex the column index (1-based) indicating the column to be updated
   * @param x the Reader object that contains the new character stream data
   * @param length the number of characters in the stream to be read
   * @throws SQLException if a database access error occurs or the columnIndex is invalid
   */
  @Override
  public void updateNCharacterStream(final int columnIndex, final Reader x, final long length) throws SQLException {
    resultSet.updateNCharacterStream(columnIndex, x, length);
  }

  /**
   * Updates the designated column with a character stream value.
   * The data will be read from the provided Reader and will have the specified length.
   *
   * @param columnLabel the label for the column to update
   * @param reader the java.io.Reader object from which the data will be read
   * @param length the length of the stream in characters
   * @throws SQLException if a database access error occurs or the column label is invalid
   */
  @Override
  public void updateNCharacterStream(final String columnLabel, final Reader reader, final long length) throws SQLException {
    resultSet.updateNCharacterStream(columnLabel, reader, length);
  }

  /**
   * Updates the designated column with a character stream value. The data will be read from the provided
   * Reader object and used to update the column indicated by the columnIndex parameter.
   *
   * @param columnIndex the index of the column to update, where the first column is 1
   * @param x the Reader object that contains the new character stream data
   * @throws SQLException if a database access error occurs or the result set is in a read-only mode
   */
  @Override
  public void updateNCharacterStream(final int columnIndex, final Reader x) throws SQLException {
    resultSet.updateNCharacterStream(columnIndex, x);
  }

  /**
   * Updates the designated column with a character stream value, which will then
   * be written to the database. The column specified by the given label will be updated
   * with the data provided by the Reader object.
   *
   * @param columnLabel the label for the column that needs to be updated.
   * @param reader the java.io.Reader object containing the data to set the column value.
   * @throws SQLException if a database access error occurs or the columnLabel is invalid.
   */
  @Override
  public void updateNCharacterStream(final String columnLabel, final Reader reader) throws SQLException {
    resultSet.updateNCharacterStream(columnLabel, reader);
  }

  /**
   * Updates the designated column with a binary stream value, which will have
   * the specified number of bytes. The data will be read from the provided
   * {@code InputStream}.
   *
   * @param columnIndex the first column is 1, the second is 2, ...
   * @param inputStream the {@code InputStream} object containing the binary data
   * @param length the number of bytes in the binary data
   * @throws SQLException if a database access error occurs or if the column
   *         index is invalid
   */
  @Override
  public void updateBlob(final int columnIndex, final InputStream inputStream, final long length) throws SQLException {
    resultSet.updateBlob(columnIndex, inputStream, length);
  }

  /**
   * Updates the designated column with an InputStream value, which will be written to the database as a Blob.
   *
   * @param columnLabel the label for the column to be updated
   * @param inputStream the InputStream containing the Blob data
   * @param length the number of bytes in the InputStream
   * @throws SQLException if a database access error occurs or the columnLabel is invalid
   */
  @Override
  public void updateBlob(final String columnLabel, final InputStream inputStream, final long length) throws SQLException {
    resultSet.updateBlob(columnLabel, inputStream, length);
  }

  /**
   * Updates the designated column with a binary stream value.
   * The input stream is used to supply the binary data for the value to be set in the column.
   *
   * @param columnIndex the first column is 1, the second is 2, and so on
   * @param inputStream the InputStream containing the binary data to set
   * @throws SQLException if the columnIndex is invalid, if a database access error occurs,
   *                      or if this method is called on a closed result set
   */
  @Override
  public void updateBlob(final int columnIndex, final InputStream inputStream) throws SQLException {
    resultSet.updateBlob(columnIndex, inputStream);
  }

  /**
   * Updates the designated column with an InputStream value, which will be
   * set as a BLOB in the underlying database.
   *
   * @param columnLabel the label for the column whose value will be set
   * @param inputStream the InputStream containing the binary data to set as a BLOB
   * @throws SQLException if a database access error occurs or the specified
   *                       columnLabel is not valid
   */
  @Override
  public void updateBlob(final String columnLabel, final InputStream inputStream) throws SQLException {
    resultSet.updateBlob(columnLabel, inputStream);
  }

  /**
   * Updates the designated column with a Clob value. The data will be read from the provided Reader
   * object for the specified number of characters.
   *
   * @param columnIndex the index of the column to update, starting from 1
   */
  @Override
  public void updateClob(final int columnIndex, final Reader reader, final long length) throws SQLException {
    resultSet.updateClob(columnIndex, reader, length);
  }

  /**
   * Updates the designated column with a character stream containing a CLOB value.
   * The data in the stream will be read into the CLOB value, starting at the beginning.
   * The length parameter indicates the number of characters to read from the reader.
   *
   * @param columnLabel the label for the column specified with the SQL AS clause
   *                    or the column name
   * @param reader the java.io.Reader object containing the data to set as the CLOB value
   * @param length the length of the data to be written to the CLOB column in characters
   * @throws SQLException if a database access error occurs, this method is called on a closed result set,
   *                      if the designated column does not have a CLOB data type,
   *                      or if the length specified is invalid
   */
  @Override
  public void updateClob(final String columnLabel, final Reader reader, final long length) throws SQLException {
    resultSet.updateClob(columnLabel, reader, length);
  }

  /**
   * Updates the designated Clob column using the data provided by a Reader object.
   * The new data will overwrite the existing Clob value at the specified column index.
   *
   * @param columnIndex the index of the column to be updated, starting from 1
   * @param reader the Reader object that contains the new Clob data to set
   * @throws SQLException if a database access error occurs or the column index is invalid
   */
  @Override
  public void updateClob(final int columnIndex, final Reader reader) throws SQLException {
    resultSet.updateClob(columnIndex, reader);
  }

  /**
   * Updates the designated Clob column with the given character stream reader.
   *
   * @param columnLabel the label for the column specified with a SQL identifier
   * @param reader the Reader object that contains the new Clob data
   * @throws SQLException if a database access error occurs or the columnLabel is not valid
   */
  @Override
  public void updateClob(final String columnLabel, final Reader reader) throws SQLException {
    resultSet.updateClob(columnLabel, reader);
  }

  /**
   * Updates the designated column with a character stream value, which will have
   * a specified number of characters, in the current row of this ResultSet object.
   *
   * @param columnIndex the column index (starting from 1) indicating the column to be updated
   * @param reader the java.io.Reader object that contains the data to be written to the NClob column
   * @param length the number of characters in the stream to be written to the column
   * @throws SQLException if a database access error occurs, second parameter is null, or this method is
   *         called on a closed ResultSet
   */
  @Override
  public void updateNClob(final int columnIndex, final Reader reader, final long length) throws SQLException {
    resultSet.updateNClob(columnIndex, reader, length);
  }

  /**
   * Updates the designated column with a new CLOB value. The data will be read from the supplied Reader
   * and will have the specified length. The column is identified using its label.
   *
   * @param columnLabel the label for the column to be updated
   * @param reader the Reader object that contains the data to update the CLOB value
   * @param length the number of characters in the Reader to be read
   * @throws SQLException if a database access error occurs or the columnLabel is not valid
   */
  @Override
  public void updateNClob(final String columnLabel, final Reader reader, final long length) throws SQLException {
    resultSet.updateNClob(columnLabel, reader, length);
  }

  /**
   * Updates the designated NClob column with a Reader object. The data in the Reader object
   * will be written to the NClob value that is being updated. This method is typically used
   * when working with large NClob data.
   *
   * @param columnIndex the index of the column to update (1-based index)
   * @param reader the java.io.Reader object containing the data to set the NClob value
   * @throws SQLException if a database access error occurs or the method is called on
   * a closed ResultSet
   */
  @Override
  public void updateNClob(final int columnIndex, final Reader reader) throws SQLException {
    resultSet.updateNClob(columnIndex, reader);
  }

  /**
   * Updates the designated column with a new NClob value. The data is read
   * from the provided Reader object.
   *
   * @param columnLabel the label for the column to be updated
   * @param reader the java.io.Reader object from which the NClob value will be read
   * @throws SQLException if a database access error occurs or the Reader object is null
   */
  @Override
  public void updateNClob(final String columnLabel, final Reader reader) throws SQLException {
    resultSet.updateNClob(columnLabel, reader);
  }

  /**
   * Retrieves an object from the specified column index in the result set and converts it to the specified type.
   *
   * @param <T> the type of the object to be retrieved
   * @param columnIndex the index of the column from which the object is to be retrieved, starting at 1
   * @param type the Class object representing the type to which the object should be converted
   * @return the object from the specified column converted to the specified type
   * @throws SQLException if a database access error occurs or this method is called on a closed result set
   */
  @Override
  public <T> T getObject(final int columnIndex, final Class<T> type) throws SQLException {
    return resultSet.getObject(columnIndex, type);
  }

  /**
   * Retrieves an object from the specified column label in the result set and casts it to the provided type.
   *
   * @param columnLabel the label for the column from which the object will be retrieved
   * @param type the class of the object to be retrieved
   * @return the object retrieved from the specified column, cast to the specified type
   * @throws SQLException if a database access error occurs or the column label is not valid
   */
  @Override
  public <T> T getObject(final String columnLabel, final Class<T> type) throws SQLException {
    return resultSet.getObject(columnLabel, type);
  }

  /**
   * Updates the designated column with an object value. The update will be applied to the column
   * specified by the given column index. This method allows specifying a SQL target type and a scale
   * or length for the object being updated.
   *
   * @param columnIndex the index of the column to update*/
  @Override
  public void updateObject(final int columnIndex, final Object x, final SQLType targetSqlType, final int scaleOrLength)
      throws SQLException {
    resultSet.updateObject(columnIndex, x, targetSqlType, scaleOrLength);
  }

  /**
   * Updates the designated column with an object value.
   * The object will be converted to the specified SQL type before being updated.
   *
   * @param columnLabel the label for the column to be updated
   * @param x the object containing the value to be stored in the specified column
   * @param targetSqlType the SQL type to convert the object to
   * @param scaleOrLength for numeric data, this is the number of digits after the decimal point;
   *                      for non-numeric data, this is the length of the data
   * @throws SQLException if a database access error occurs, this method is called on a closed ResultSet,
   *                      or the target SQL type is invalid
   */
  @Override
  public void updateObject(final String columnLabel, final Object x, final SQLType targetSqlType, final int scaleOrLength)
      throws SQLException {
    resultSet.updateObject(columnLabel, x, targetSqlType, scaleOrLength);
  }

  /**
   * Updates the designated column with an object value. The update is made to
   * the current row of the ResultSet object. This method allows specifying
   * the column index and the target SQL type for the object being updated.
   *
   * @param columnIndex the index of the column to update, starting from 1
   * @param x the object containing the new column value
   * @param targetSqlType the SQL type to be sent to the database
   * @throws SQLException if a database access error occurs or if the specified
   *         parameters are invalid
   */
  @Override
  public void updateObject(final int columnIndex, final Object x, final SQLType targetSqlType) throws SQLException {
    resultSet.updateObject(columnIndex, x, targetSqlType);
  }

  /**
   * Updates the value of the designated column with the given column label in the current row of this
   * ResultSet object to the provided Java object. The data will be converted to the SQL type specified
   * by the given targetSqlType parameter before updating.
   *
   * @param columnLabel the label for the column to be updated
   * @param x the new column value
   * @param targetSqlType the SQL type to be used for data conversion before updating
   * @throws SQLException if a database access error occurs or if the columnLabel is not valid
   */
  @Override
  public void updateObject(final String columnLabel, final Object x, final SQLType targetSqlType) throws SQLException {
    resultSet.updateObject(columnLabel, x, targetSqlType);
  }

  /**
   * Updates the designated column with an ASCII InputStream.
   * The stream must contain only ASCII characters. The driver reads the stream to the end.
   *
   * @param columnIndex the index of the column to update, starting from 1.
   * @param x the InputStream containing ASCII data to set in the specified column.
   * @throws SQLException if a database access error occurs or the columnIndex is invalid.
   */
  @Override
  public void updateAsciiStream(final int columnIndex, final InputStream x) throws SQLException {
    resultSet.updateAsciiStream(columnIndex, x);
  }

  /**
   * Updates the designated column with a binary stream value.
   * The data provided in the stream replaces the existing value of the column
   * at the specified index in the current row of the ResultSet.
   *
   * @param columnIndex the index of the column to be updated, where the first column is 1
   * @param x the InputStream containing the binary data to set in the column
   * @throws SQLException if a database access error occurs or the ResultSet is in a state
   *                      that does not permit the update
   */
  @Override
  public void updateBinaryStream(final int columnIndex, final InputStream x) throws SQLException {
    resultSet.updateBinaryStream(columnIndex, x);
  }

  /**
   * Updates the designated column with a new character stream value.
   * The data will be read from the provided Reader object and written to the database.
   *
   * @param columnIndex the index of the column to be updated, starting from 1
   * @param x the Reader object containing the new character stream value
   * @throws SQLException if a database access error occurs or the columnIndex is invalid
   */
  @Override
  public void updateCharacterStream(final int columnIndex, final Reader x) throws SQLException {
    resultSet.updateCharacterStream(columnIndex, x);
  }

  /**
   * Updates the designated column with an ASCII stream.
   * The data will be read from the input stream as needed until the end of the stream is reached.
   * This method can be used to update columns in the current row or the insert row.
   *
   * @param columnLabel the label for the column specified with the SQL AS clause.
   *                    If the SQL AS clause was not specified, the label will default to the column name.
   * @param x the InputStream object containing the ASCII stream data to set in the column
   * @throws SQLException if a database access error occurs, this method is called on a closed result set,
   *                      or if the ResultSet concurrency is CONCUR_READ_ONLY
   */
  @Override
  public void updateAsciiStream(final String columnLabel, final InputStream x) throws SQLException {
    resultSet.updateAsciiStream(columnLabel, x);
  }

  /**
   * Updates the designated column with a binary stream value. The column is specified by the column label.
   * The data in the InputStream can be used as an updated value for the column.
   *
   * @param columnLabel the label for the column to be updated.
   * @param x the InputStream containing the binary stream data to update the column with.
   * @throws SQLException if a database access error occurs or the column label is not valid.
   */
  @Override
  public void updateBinaryStream(final String columnLabel, final InputStream x) throws SQLException {
    resultSet.updateBinaryStream(columnLabel, x);
  }

  /**
   * Updates the designated column with a new character stream value.
   * The data will be read from the provided Reader object and sent to the database.
   *
   * @param columnLabel the label for the column that is to be updated
   * @param reader the Reader object containing the data to set the specified column to
   * @throws SQLException if a database access error occurs, this method is called on a closed result set,
   *                      or the columnLabel is invalid
   */
  @Override
  public void updateCharacterStream(final String columnLabel, final Reader reader) throws SQLException {
    resultSet.updateCharacterStream(columnLabel, reader);
  }

  /**
   * Retrieves the value of the designated column in the current row of this ResultSet object
   * as a <code>java.net.URL</code> object.
   *
   * @param columnIndex the index of the column from which the URL is to be retrieved, where*/
  @Override
  public URL getURL(final int columnIndex) throws SQLException {
    return resultSet.getURL(columnIndex);
  }

  /**
   * Retrieves the value of the designated column as a URL object.
   *
   * @param columnLabel the label for the column, which is the column name defined during table creation
   * @return the column value as a URL object, or null if the SQL value is SQL NULL
   * @throws SQLException if a database access error occurs or the columnLabel is not valid
   */
  @Override
  public URL getURL(final String columnLabel) throws SQLException {
    return resultSet.getURL(columnLabel);
  }

  /**
   * Updates the designated column with a <code>Ref</code> value.
   * The updater methods are used to update column values in the current row
   * or the insert row. The new row value will be updated into the database
   * when the method <code>updateRow</code> is called.
   *
   * @param columnIndex the first column is 1, the second is 2, and so on
   * @param x the new column value; must be a <code>Ref</code> object
   * @throws SQLException if a database access error occurs, if the result set
   *         concurrency is <code>CONCUR_READ_ONLY</code>, or if this method
   *         is called on a closed result set
   */
  @Override
  public void updateRef(final int columnIndex, final Ref x) throws SQLException {
    resultSet.updateRef(columnIndex, x);
  }

  /**
   * Updates the designated column with a Ref value.
   * This method should be called when updating a column in a ResultSet with a Ref object.
   *
   * @param columnLabel the label for the column specified in the query, used to identify the column being updated
   * @param x the Ref object containing the value to update the column with
   * @throws SQLException if a database access error occurs, the ResultSet is in a read-only mode,
   *         or if this method is called on a non-updatable ResultSet
   */
  @Override
  public void updateRef(final String columnLabel, final Ref x) throws SQLException {
    resultSet.updateRef(columnLabel, x);
  }

  /**
   * Checks if this object wraps an implementation of the specified interface.
   *
   * @param iface the interface to check if the object implements or wraps.
   * @return true if this object is a wrapper for the specified interface; false otherwise.
   * @throws SQLException if a database access error occurs.
   */
  @Override
  public boolean isWrapperFor(final Class<?> iface) throws SQLException {
    return resultSet.isWrapperFor(iface);
  }

  /**
   * Unwraps this instance to provide an implementation of the specified interface.
   *
   * @param <T> the type of the class modeled by the specified interface
   * @param iface the class of the interface to be unwrapped
   * @return an instance of the specified interface, which this object implements
   * @throws SQLException if no object implements the specified interface or if an error occurs
   */
  @Override
  public <T> T unwrap(final Class<T> iface) throws SQLException {
    return resultSet.unwrap(iface);
  }
}

