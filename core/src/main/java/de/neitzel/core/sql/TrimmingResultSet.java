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
 * ResultSet that trims all String values.
 */
@RequiredArgsConstructor
public class TrimmingResultSet implements ResultSet {

  /**
   * The ResultSet used to read data from.
   */
  private final ResultSet resultSet;
    
  /**
   * Gets string.
   *
   * @param columnIndex the column index
   * @return the string
   * @throws SQLException the sql exception
   */
  @Override
  public String getString(final int columnIndex) throws SQLException {
    String value = resultSet.getString(columnIndex);
    return value != null ? value.trim() : null;
  }

  /**
   * Gets string.
   *
   * @param columnLabel the column label
   * @return the string
   * @throws SQLException the sql exception
   */
  @Override
  public String getString(final String columnLabel) throws SQLException {
    String value = resultSet.getString(columnLabel);
    return value != null ? value.trim() : null;
  }

  /**
   * Next boolean.
   *
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean next() throws SQLException {
    return resultSet.next();
  }

  /**
   * Close.
   *
   * @throws SQLException the sql exception
   */
  @Override
  public void close() throws SQLException {
    resultSet.close();
  }

  /**
   * Was null boolean.
   *
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean wasNull() throws SQLException {
    return resultSet.wasNull();
  }

  /**
   * Gets boolean.
   *
   * @param columnIndex the column index
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean getBoolean(final int columnIndex) throws SQLException {
    return resultSet.getBoolean(columnIndex);
  }

  /**
   * Gets boolean.
   *
   * @param columnLabel the column label
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean getBoolean(final String columnLabel) throws SQLException {
    return resultSet.getBoolean(columnLabel);
  }

  /**
   * Gets byte.
   *
   * @param columnIndex the column index
   * @return the byte
   * @throws SQLException the sql exception
   */
  @Override
  public byte getByte(final int columnIndex) throws SQLException {
    return resultSet.getByte(columnIndex);
  }

  /**
   * Gets byte.
   *
   * @param columnLabel the column label
   * @return the byte
   * @throws SQLException the sql exception
   */
  @Override
  public byte getByte(final String columnLabel) throws SQLException {
    return resultSet.getByte(columnLabel);
  }

  /**
   * Gets short.
   *
   * @param columnIndex the column index
   * @return the short
   * @throws SQLException the sql exception
   */
  @Override
  public short getShort(final int columnIndex) throws SQLException {
    return resultSet.getShort(columnIndex);
  }

  /**
   * Gets short.
   *
   * @param columnLabel the column label
   * @return the short
   * @throws SQLException the sql exception
   */
  @Override
  public short getShort(final String columnLabel) throws SQLException {
    return resultSet.getShort(columnLabel);
  }

  /**
   * Gets int.
   *
   * @param columnIndex the column index
   * @return the int
   * @throws SQLException the sql exception
   */
  @Override
  public int getInt(final int columnIndex) throws SQLException {
    return resultSet.getInt(columnIndex);
  }

  /**
   * Gets int.
   *
   * @param columnLabel the column label
   * @return the int
   * @throws SQLException the sql exception
   */
  @Override
  public int getInt(final String columnLabel) throws SQLException {
    return resultSet.getInt(columnLabel);
  }

  /**
   * Gets long.
   *
   * @param columnIndex the column index
   * @return the long
   * @throws SQLException the sql exception
   */
  @Override
  public long getLong(final int columnIndex) throws SQLException {
    return resultSet.getLong(columnIndex);
  }

  /**
   * Gets long.
   *
   * @param columnLabel the column label
   * @return the long
   * @throws SQLException the sql exception
   */
  @Override
  public long getLong(final String columnLabel) throws SQLException {
    return resultSet.getLong(columnLabel);
  }

  /**
   * Gets float.
   *
   * @param columnIndex the column index
   * @return the float
   * @throws SQLException the sql exception
   */
  @Override
  public float getFloat(final int columnIndex) throws SQLException {
    return resultSet.getFloat(columnIndex);
  }

  /**
   * Gets float.
   *
   * @param columnLabel the column label
   * @return the float
   * @throws SQLException the sql exception
   */
  @Override
  public float getFloat(final String columnLabel) throws SQLException {
    return resultSet.getFloat(columnLabel);
  }

  /**
   * Gets double.
   *
   * @param columnIndex the column index
   * @return the double
   * @throws SQLException the sql exception
   */
  @Override
  public double getDouble(final int columnIndex) throws SQLException {
    return resultSet.getDouble(columnIndex);
  }

  /**
   * Gets double.
   *
   * @param columnLabel the column label
   * @return the double
   * @throws SQLException the sql exception
   */
  @Override
  public double getDouble(final String columnLabel) throws SQLException {
    return resultSet.getDouble(columnLabel);
  }

  /**
   * Gets big decimal.
   *
   * @param columnIndex the column index
   * @param scale       the scale
   * @return the big decimal
   * @throws SQLException the sql exception
   */
  @Override
  @Deprecated
  public BigDecimal getBigDecimal(final int columnIndex, int scale) throws SQLException {
    return resultSet.getBigDecimal(columnIndex, scale);
  }

  /**
   * Gets big decimal.
   *
   * @param columnIndex the column index
   * @return the big decimal
   * @throws SQLException the sql exception
   */
  @Override
  public BigDecimal getBigDecimal(final int columnIndex) throws SQLException {
    return resultSet.getBigDecimal(columnIndex);
  }

  /**
   * Gets big decimal.
   *
   * @param columnLabel the column label
   * @param scale       the scale
   * @return the big decimal
   * @throws SQLException the sql exception
   */
  @Override
  @Deprecated
  public BigDecimal getBigDecimal(final String columnLabel, int scale) throws SQLException {
    return resultSet.getBigDecimal(columnLabel, scale);
  }

  /**
   * Gets big decimal.
   *
   * @param columnLabel the column label
   * @return the big decimal
   * @throws SQLException the sql exception
   */
  @Override
  public BigDecimal getBigDecimal(final String columnLabel) throws SQLException {
    return resultSet.getBigDecimal(columnLabel);
  }

  /**
   * Get bytes byte [ ].
   *
   * @param columnIndex the column index
   * @return the byte [ ]
   * @throws SQLException the sql exception
   */
  @Override
  public byte[] getBytes(final int columnIndex) throws SQLException {
    return resultSet.getBytes(columnIndex);
  }

  /**
   * Get bytes byte [ ].
   *
   * @param columnLabel the column label
   * @return the byte [ ]
   * @throws SQLException the sql exception
   */
  @Override
  public byte[] getBytes(final String columnLabel) throws SQLException {
    return resultSet.getBytes(columnLabel);
  }

  /**
   * Gets date.
   *
   * @param columnIndex the column index
   * @return the date
   * @throws SQLException the sql exception
   */
  @Override
  public Date getDate(final int columnIndex) throws SQLException {
    return resultSet.getDate(columnIndex);
  }

  /**
   * Gets date.
   *
   * @param columnLabel the column label
   * @return the date
   * @throws SQLException the sql exception
   */
  @Override
  public Date getDate(final String columnLabel) throws SQLException {
    return resultSet.getDate(columnLabel);
  }

  /**
   * Gets date.
   *
   * @param columnIndex the column index
   * @param cal         the cal
   * @return the date
   * @throws SQLException the sql exception
   */
  @Override
  public Date getDate(final int columnIndex, final Calendar cal) throws SQLException {
    return resultSet.getDate(columnIndex, cal);
  }

  /**
   * Gets date.
   *
   * @param columnLabel the column label
   * @param cal         the cal
   * @return the date
   * @throws SQLException the sql exception
   */
  @Override
  public Date getDate(final String columnLabel, final Calendar cal) throws SQLException {
    return resultSet.getDate(columnLabel, cal);
  }

  /**
   * Gets time.
   *
   * @param columnIndex the column index
   * @return the time
   * @throws SQLException the sql exception
   */
  @Override
  public Time getTime(final int columnIndex) throws SQLException {
    return resultSet.getTime(columnIndex);
  }

  /**
   * Gets time.
   *
   * @param columnLabel the column label
   * @return the time
   * @throws SQLException the sql exception
   */
  @Override
  public Time getTime(final String columnLabel) throws SQLException {
    return resultSet.getTime(columnLabel);
  }

  /**
   * Gets time.
   *
   * @param columnIndex the column index
   * @param cal         the cal
   * @return the time
   * @throws SQLException the sql exception
   */
  @Override
  public Time getTime(final int columnIndex, final Calendar cal) throws SQLException {
    return resultSet.getTime(columnIndex, cal);
  }

  /**
   * Gets time.
   *
   * @param columnLabel the column label
   * @param cal         the cal
   * @return the time
   * @throws SQLException the sql exception
   */
  @Override
  public Time getTime(final String columnLabel, final Calendar cal) throws SQLException {
    return resultSet.getTime(columnLabel, cal);
  }

  /**
   * Gets timestamp.
   *
   * @param columnIndex the column index
   * @return the timestamp
   * @throws SQLException the sql exception
   */
  @Override
  public Timestamp getTimestamp(final int columnIndex) throws SQLException {
    return resultSet.getTimestamp(columnIndex);
  }

  /**
   * Gets timestamp.
   *
   * @param columnLabel the column label
   * @return the timestamp
   * @throws SQLException the sql exception
   */
  @Override
  public Timestamp getTimestamp(final String columnLabel) throws SQLException {
    return resultSet.getTimestamp(columnLabel);
  }

  /**
   * Gets timestamp.
   *
   * @param columnIndex the column index
   * @param cal         the cal
   * @return the timestamp
   * @throws SQLException the sql exception
   */
  @Override
  public Timestamp getTimestamp(final int columnIndex, final Calendar cal) throws SQLException {
    return resultSet.getTimestamp(columnIndex, cal);
  }

  /**
   * Gets timestamp.
   *
   * @param columnLabel the column label
   * @param cal         the cal
   * @return the timestamp
   * @throws SQLException the sql exception
   */
  @Override
  public Timestamp getTimestamp(final String columnLabel, final Calendar cal) throws SQLException {
    return resultSet.getTimestamp(columnLabel, cal);
  }

  /**
   * Gets ascii stream.
   *
   * @param columnIndex the column index
   * @return the ascii stream
   * @throws SQLException the sql exception
   */
  @Override
  public InputStream getAsciiStream(final int columnIndex) throws SQLException {
    return resultSet.getAsciiStream(columnIndex);
  }

  /**
   * Gets unicode stream.
   *
   * @param columnIndex the column index
   * @return the unicode stream
   * @throws SQLException the sql exception
   */
  @Override
  public InputStream getUnicodeStream(final int columnIndex) throws SQLException {
    return null;
  }

  /**
   * Gets ascii stream.
   *
   * @param columnLabel the column label
   * @return the ascii stream
   * @throws SQLException the sql exception
   */
  @Override
  public InputStream getAsciiStream(final String columnLabel) throws SQLException {
    return resultSet.getAsciiStream(columnLabel);
  }

  /**
   * Gets unicode stream.
   *
   * @param columnLabel the column label
   * @return the unicode stream
   * @throws SQLException the sql exception
   */
  @Override
  public InputStream getUnicodeStream(final String columnLabel) throws SQLException {
    return null;
  }

  /**
   * Gets binary stream.
   *
   * @param columnIndex the column index
   * @return the binary stream
   * @throws SQLException the sql exception
   */
  @Override
  public InputStream getBinaryStream(final int columnIndex) throws SQLException {
    return resultSet.getBinaryStream(columnIndex);
  }

  /**
   * Gets binary stream.
   *
   * @param columnLabel the column label
   * @return the binary stream
   * @throws SQLException the sql exception
   */
  @Override
  public InputStream getBinaryStream(final String columnLabel) throws SQLException {
    return resultSet.getBinaryStream(columnLabel);
  }

  /**
   * Gets warnings.
   *
   * @return the warnings
   * @throws SQLException the sql exception
   */
  @Override
  public SQLWarning getWarnings() throws SQLException {
    return null;
  }

  /**
   * Gets character stream.
   *
   * @param columnIndex the column index
   * @return the character stream
   * @throws SQLException the sql exception
   */
  @Override
  public Reader getCharacterStream(final int columnIndex) throws SQLException {
    return resultSet.getCharacterStream(columnIndex);
  }

  /**
   * Gets character stream.
   *
   * @param columnLabel the column label
   * @return the character stream
   * @throws SQLException the sql exception
   */
  @Override
  public Reader getCharacterStream(final String columnLabel) throws SQLException {
    return resultSet.getCharacterStream(columnLabel);
  }

  /**
   * Gets n string.
   *
   * @param columnIndex the column index
   * @return the n string
   * @throws SQLException the sql exception
   */
  @Override
  public String getNString(final int columnIndex) throws SQLException {
    String value = resultSet.getNString(columnIndex);
    return value != null ? value.trim() : null;
  }

  /**
   * Gets n string.
   *
   * @param columnLabel the column label
   * @return the n string
   * @throws SQLException the sql exception
   */
  @Override
  public String getNString(final String columnLabel) throws SQLException {
    String value = resultSet.getNString(columnLabel);
    return value != null ? value.trim() : null;
  }

  /**
   * Gets n character stream.
   *
   * @param columnIndex the column index
   * @return the n character stream
   * @throws SQLException the sql exception
   */
  @Override
  public Reader getNCharacterStream(final int columnIndex) throws SQLException {
    return resultSet.getNCharacterStream(columnIndex);
  }

  /**
   * Gets n character stream.
   *
   * @param columnLabel the column label
   * @return the n character stream
   * @throws SQLException the sql exception
   */
  @Override
  public Reader getNCharacterStream(final String columnLabel) throws SQLException {
    return resultSet.getNCharacterStream(columnLabel);
  }

  /**
   * Clear warnings.
   *
   * @throws SQLException the sql exception
   */
  @Override
  public void clearWarnings() throws SQLException {
    resultSet.clearWarnings();
  }

  /**
   * Gets cursor name.
   *
   * @return the cursor name
   * @throws SQLException the sql exception
   */
  @Override
  public String getCursorName() throws SQLException {
    return resultSet.getCursorName();
  }

  /**
   * Gets meta data.
   *
   * @return the meta data
   * @throws SQLException the sql exception
   */
  @Override
  public ResultSetMetaData getMetaData() throws SQLException {
    return resultSet.getMetaData();
  }

  /**
   * Gets object.
   *
   * @param columnIndex the column index
   * @return the object
   * @throws SQLException the sql exception
   */
  @Override
  public Object getObject(final int columnIndex) throws SQLException {
    return resultSet.getObject(columnIndex);
  }

  /**
   * Gets object.
   *
   * @param columnLabel the column label
   * @return the object
   * @throws SQLException the sql exception
   */
  @Override
  public Object getObject(final String columnLabel) throws SQLException {
    return resultSet.getObject(columnLabel);
  }

  /**
   * Gets object.
   *
   * @param columnIndex the column index
   * @param map         the map
   * @return the object
   * @throws SQLException the sql exception
   */
  @Override
  public Object getObject(final int columnIndex, final Map<String, Class<?>> map) throws SQLException {
    return resultSet.getObject(columnIndex, map);
  }

  /**
   * Gets ref.
   *
   * @param columnIndex the column index
   * @return the ref
   * @throws SQLException the sql exception
   */
  @Override
  public Ref getRef(final int columnIndex) throws SQLException {
    return resultSet.getRef(columnIndex);
  }

  /**
   * Gets blob.
   *
   * @param columnIndex the column index
   * @return the blob
   * @throws SQLException the sql exception
   */
  @Override
  public Blob getBlob(final int columnIndex) throws SQLException {
    return resultSet.getBlob(columnIndex);
  }

  /**
   * Gets clob.
   *
   * @param columnIndex the column index
   * @return the clob
   * @throws SQLException the sql exception
   */
  @Override
  public Clob getClob(final int columnIndex) throws SQLException {
    return resultSet.getClob(columnIndex);
  }

  /**
   * Gets array.
   *
   * @param columnIndex the column index
   * @return the array
   * @throws SQLException the sql exception
   */
  @Override
  public Array getArray(final int columnIndex) throws SQLException {
    return resultSet.getArray(columnIndex);
  }

  /**
   * Gets object.
   *
   * @param columnLabel the column label
   * @param map         the map
   * @return the object
   * @throws SQLException the sql exception
   */
  @Override
  public Object getObject(final String columnLabel, final Map<String, Class<?>> map) throws SQLException {
    return resultSet.getObject(columnLabel, map);
  }

  /**
   * Gets ref.
   *
   * @param columnLabel the column label
   * @return the ref
   * @throws SQLException the sql exception
   */
  @Override
  public Ref getRef(final String columnLabel) throws SQLException {
    return resultSet.getRef(columnLabel);
  }

  /**
   * Gets blob.
   *
   * @param columnLabel the column label
   * @return the blob
   * @throws SQLException the sql exception
   */
  @Override
  public Blob getBlob(final String columnLabel) throws SQLException {
    return resultSet.getBlob(columnLabel);
  }

  /**
   * Gets clob.
   *
   * @param columnLabel the column label
   * @return the clob
   * @throws SQLException the sql exception
   */
  @Override
  public Clob getClob(final String columnLabel) throws SQLException {
    return resultSet.getClob(columnLabel);
  }

  /**
   * Gets array.
   *
   * @param columnLabel the column label
   * @return the array
   * @throws SQLException the sql exception
   */
  @Override
  public Array getArray(final String columnLabel) throws SQLException {
    return resultSet.getArray(columnLabel);
  }

  /**
   * Find column int.
   *
   * @param columnLabel the column label
   * @return the int
   * @throws SQLException the sql exception
   */
  @Override
  public int findColumn(final String columnLabel) throws SQLException {
    return resultSet.findColumn(columnLabel);
  }

  /**
   * Is before first boolean.
   *
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean isBeforeFirst() throws SQLException {
    return resultSet.isBeforeFirst();
  }

  /**
   * Is after last boolean.
   *
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean isAfterLast() throws SQLException {
    return resultSet.isAfterLast();
  }

  /**
   * Is first boolean.
   *
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean isFirst() throws SQLException {
    return resultSet.isFirst();
  }

  /**
   * Is last boolean.
   *
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean isLast() throws SQLException {
    return resultSet.isLast();
  }

  /**
   * Before first.
   *
   * @throws SQLException the sql exception
   */
  @Override
  public void beforeFirst() throws SQLException {
    resultSet.beforeFirst();
  }

  /**
   * After last.
   *
   * @throws SQLException the sql exception
   */
  @Override
  public void afterLast() throws SQLException {
    resultSet.afterLast();
  }

  /**
   * First boolean.
   *
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean first() throws SQLException {
    return resultSet.first();
  }

  /**
   * Last boolean.
   *
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean last() throws SQLException {
    return resultSet.last();
  }

  /**
   * Gets row.
   *
   * @return the row
   * @throws SQLException the sql exception
   */
  @Override
  public int getRow() throws SQLException {
    return resultSet.getRow();
  }

  /**
   * Absolute boolean.
   *
   * @param row the row
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean absolute(final int row) throws SQLException {
    return resultSet.absolute(row);
  }

  /**
   * Relative boolean.
   *
   * @param rows the rows
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean relative(final int rows) throws SQLException {
    return resultSet.relative(rows);
  }

  /**
   * Previous boolean.
   *
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean previous() throws SQLException {
    return resultSet.previous();
  }

  /**
   * Gets fetch direction.
   *
   * @return the fetch direction
   * @throws SQLException the sql exception
   */
  @Override
  public int getFetchDirection() throws SQLException {
    return resultSet.getFetchDirection();
  }

  /**
   * Sets fetch direction.
   *
   * @param direction the direction
   * @throws SQLException the sql exception
   */
  @Override
  public void setFetchDirection(final int direction) throws SQLException {
    resultSet.setFetchDirection(direction);
  }

  /**
   * Gets fetch size.
   *
   * @return the fetch size
   * @throws SQLException the sql exception
   */
  @Override
  public int getFetchSize() throws SQLException {
    return resultSet.getFetchSize();
  }

  /**
   * Sets fetch size.
   *
   * @param rows the rows
   * @throws SQLException the sql exception
   */
  @Override
  public void setFetchSize(final int rows) throws SQLException {
    resultSet.setFetchSize(rows);
  }

  /**
   * Gets type.
   *
   * @return the type
   * @throws SQLException the sql exception
   */
  @Override
  public int getType() throws SQLException {
    return resultSet.getType();
  }

  /**
   * Gets concurrency.
   *
   * @return the concurrency
   * @throws SQLException the sql exception
   */
  @Override
  public int getConcurrency() throws SQLException {
    return resultSet.getConcurrency();
  }

  /**
   * Row updated boolean.
   *
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean rowUpdated() throws SQLException {
    return resultSet.rowUpdated();
  }

  /**
   * Row inserted boolean.
   *
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean rowInserted() throws SQLException {
    return resultSet.rowInserted();
  }

  /**
   * Row deleted boolean.
   *
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean rowDeleted() throws SQLException {
    return resultSet.rowDeleted();
  }

  /**
   * Update null.
   *
   * @param columnIndex the column index
   * @throws SQLException the sql exception
   */
  @Override
  public void updateNull(final int columnIndex) throws SQLException {
    resultSet.updateNull(columnIndex);
  }

  /**
   * Update boolean.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBoolean(final int columnIndex, final boolean x) throws SQLException {
    resultSet.updateBoolean(columnIndex, x);
  }

  /**
   * Update byte.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateByte(final int columnIndex, final byte x) throws SQLException {
    resultSet.updateByte(columnIndex, x);
  }

  /**
   * Update short.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateShort(final int columnIndex, final short x) throws SQLException {
    resultSet.updateShort(columnIndex, x);
  }

  /**
   * Update int.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateInt(final int columnIndex, final int x) throws SQLException {
    resultSet.updateInt(columnIndex, x);
  }

  /**
   * Update long.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateLong(final int columnIndex, final long x) throws SQLException {
    resultSet.updateLong(columnIndex, x);
  }

  /**
   * Update float.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateFloat(final int columnIndex, final float x) throws SQLException {
    resultSet.updateFloat(columnIndex, x);
  }

  /**
   * Update double.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateDouble(final int columnIndex, final double x) throws SQLException {
    resultSet.updateDouble(columnIndex, x);
  }

  /**
   * Update big decimal.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBigDecimal(final int columnIndex, final BigDecimal x) throws SQLException {
    resultSet.updateBigDecimal(columnIndex, x);
  }

  /**
   * Update string.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateString(final int columnIndex, final String x) throws SQLException {
    resultSet.updateString(columnIndex, x);
  }

  /**
   * Update bytes.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBytes(final int columnIndex, final byte[] x) throws SQLException {
    resultSet.updateBytes(columnIndex, x);
  }

  /**
   * Update date.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateDate(final int columnIndex, final Date x) throws SQLException {
    resultSet.updateDate(columnIndex, x);
  }

  /**
   * Update time.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateTime(final int columnIndex, final Time x) throws SQLException {
    resultSet.updateTime(columnIndex, x);
  }

  /**
   * Update timestamp.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateTimestamp(final int columnIndex, final Timestamp x) throws SQLException {
    resultSet.updateTimestamp(columnIndex, x);
  }

  /**
   * Update ascii stream.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateAsciiStream(final int columnIndex, final InputStream x, final int length) throws SQLException {
    resultSet.updateAsciiStream(columnIndex, x, length);
  }

  /**
   * Update binary stream.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBinaryStream(final int columnIndex, final InputStream x, final int length) throws SQLException {
    resultSet.updateBinaryStream(columnIndex, x, length);
  }

  /**
   * Update character stream.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateCharacterStream(final int columnIndex, final Reader x, final int length) throws SQLException {
    resultSet.updateCharacterStream(columnIndex, x, length);
  }

  /**
   * Update object.
   *
   * @param columnIndex   the column index
   * @param x             the x
   * @param scaleOrLength the scale or length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateObject(final int columnIndex, final Object x, final int scaleOrLength) throws SQLException {
    resultSet.updateObject(columnIndex, x, scaleOrLength);
  }

  /**
   * Update object.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateObject(final int columnIndex, final Object x) throws SQLException {
    resultSet.updateObject(columnIndex, x);
  }

  /**
   * Update null.
   *
   * @param columnLabel the column label
   * @throws SQLException the sql exception
   */
  @Override
  public void updateNull(final String columnLabel) throws SQLException {
    resultSet.updateNull(columnLabel);
  }

  /**
   * Update boolean.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBoolean(final String columnLabel, final boolean x) throws SQLException {
    resultSet.updateBoolean(columnLabel, x);
  }

  /**
   * Update byte.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateByte(final String columnLabel, final byte x) throws SQLException {
    resultSet.updateByte(columnLabel, x);
  }

  /**
   * Update short.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateShort(final String columnLabel, final short x) throws SQLException {
    resultSet.updateShort(columnLabel, x);
  }

  /**
   * Update int.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateInt(final String columnLabel, final int x) throws SQLException {
    resultSet.updateInt(columnLabel, x);
  }

  /**
   * Update long.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateLong(final String columnLabel, final long x) throws SQLException {
    resultSet.updateLong(columnLabel, x);
  }

  /**
   * Update float.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateFloat(final String columnLabel, final float x) throws SQLException {
    resultSet.updateFloat(columnLabel, x);
  }

  /**
   * Update double.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateDouble(final String columnLabel, final double x) throws SQLException {
    resultSet.updateDouble(columnLabel, x);
  }

  /**
   * Update big decimal.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBigDecimal(final String columnLabel, final BigDecimal x) throws SQLException {
    resultSet.updateBigDecimal(columnLabel, x);
  }

  /**
   * Update string.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateString(final String columnLabel, final String x) throws SQLException {
    resultSet.updateString(columnLabel, x);
  }

  /**
   * Update bytes.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBytes(final String columnLabel, final byte[] x) throws SQLException {
    resultSet.updateBytes(columnLabel, x);
  }

  /**
   * Update date.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateDate(final String columnLabel, final Date x) throws SQLException {
    resultSet.updateDate(columnLabel, x);
  }

  /**
   * Update time.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateTime(final String columnLabel, final Time x) throws SQLException {
    resultSet.updateTime(columnLabel, x);
  }

  /**
   * Update timestamp.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateTimestamp(final String columnLabel, final Timestamp x) throws SQLException {
    resultSet.updateTimestamp(columnLabel, x);
  }

  /**
   * Update ascii stream.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateAsciiStream(final String columnLabel, final InputStream x, final int length) throws SQLException {
    resultSet.updateAsciiStream(columnLabel, x, length);
  }

  /**
   * Update binary stream.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBinaryStream(final String columnLabel, final InputStream x, final int length) throws SQLException {
    resultSet.updateBinaryStream(columnLabel, x, length);
  }

  /**
   * Update character stream.
   *
   * @param columnLabel the column label
   * @param reader      the reader
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateCharacterStream(final String columnLabel, final Reader reader, final int length) throws SQLException {
    resultSet.updateCharacterStream(columnLabel, reader, length);
  }

  /**
   * Update object.
   *
   * @param columnLabel   the column label
   * @param x             the x
   * @param scaleOrLength the scale or length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateObject(final String columnLabel, final Object x, final int scaleOrLength) throws SQLException {
    resultSet.updateObject(columnLabel, x, scaleOrLength);
  }

  /**
   * Update object.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateObject(final String columnLabel, final Object x) throws SQLException {
    resultSet.updateObject(columnLabel, x);
  }

  /**
   * Insert row.
   *
   * @throws SQLException the sql exception
   */
  @Override
  public void insertRow() throws SQLException {
    resultSet.insertRow();
  }

  /**
   * Update row.
   *
   * @throws SQLException the sql exception
   */
  @Override
  public void updateRow() throws SQLException {
    resultSet.updateRow();
  }

  /**
   * Delete row.
   *
   * @throws SQLException the sql exception
   */
  @Override
  public void deleteRow() throws SQLException {
    resultSet.deleteRow();
  }

  /**
   * Refresh row.
   *
   * @throws SQLException the sql exception
   */
  @Override
  public void refreshRow() throws SQLException {
    resultSet.refreshRow();
  }

  /**
   * Cancel row updates.
   *
   * @throws SQLException the sql exception
   */
  @Override
  public void cancelRowUpdates() throws SQLException {
    resultSet.cancelRowUpdates();
  }

  /**
   * Move to insert row.
   *
   * @throws SQLException the sql exception
   */
  @Override
  public void moveToInsertRow() throws SQLException {
    resultSet.moveToInsertRow();
  }

  /**
   * Move to current row.
   *
   * @throws SQLException the sql exception
   */
  @Override
  public void moveToCurrentRow() throws SQLException {
    resultSet.moveToCurrentRow();
  }

  /**
   * Gets statement.
   *
   * @return the statement
   * @throws SQLException the sql exception
   */
  @Override
  public Statement getStatement() throws SQLException {
    return resultSet.getStatement();
  }

  /**
   * Update ascii stream.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateAsciiStream(final int columnIndex, final InputStream x, final long length) throws SQLException {
    resultSet.updateAsciiStream(columnIndex, x, length);
  }

  /**
   * Update binary stream.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBinaryStream(final int columnIndex, final InputStream x, final long length) throws SQLException {
    resultSet.updateBinaryStream(columnIndex, x, length);
  }

  /**
   * Update character stream.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateCharacterStream(final int columnIndex, final Reader x, final long length) throws SQLException {
    resultSet.updateCharacterStream(columnIndex, x, length);
  }

  /**
   * Update ascii stream.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateAsciiStream(final String columnLabel, final InputStream x, final long length) throws SQLException {
    resultSet.updateAsciiStream(columnLabel, x, length);
  }

  /**
   * Update binary stream.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBinaryStream(final String columnLabel, final InputStream x, final long length) throws SQLException {
    resultSet.updateBinaryStream(columnLabel, x, length);
  }

  /**
   * Update character stream.
   *
   * @param columnLabel the column label
   * @param reader      the reader
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateCharacterStream(final String columnLabel, final Reader reader, final long length) throws SQLException {
    resultSet.updateCharacterStream(columnLabel, reader, length);
  }

  /**
   * Update blob.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBlob(final int columnIndex, final Blob x) throws SQLException {
    resultSet.updateBlob(columnIndex, x);
  }

  /**
   * Update blob.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBlob(final String columnLabel, final Blob x) throws SQLException {
    resultSet.updateBlob(columnLabel, x);
  }

  /**
   * Update clob.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateClob(final int columnIndex, final Clob x) throws SQLException {
    resultSet.updateClob(columnIndex, x);
  }

  /**
   * Update clob.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateClob(final String columnLabel, final Clob x) throws SQLException {
    resultSet.updateClob(columnLabel, x);
  }

  /**
   * Update array.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateArray(final int columnIndex, final Array x) throws SQLException {
    resultSet.updateArray(columnIndex, x);
  }

  /**
   * Update array.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateArray(final String columnLabel, final Array x) throws SQLException {
    resultSet.updateArray(columnLabel, x);
  }

  /**
   * Gets row id.
   *
   * @param columnIndex the column index
   * @return the row id
   * @throws SQLException the sql exception
   */
  @Override
  public RowId getRowId(final int columnIndex) throws SQLException {
    return resultSet.getRowId(columnIndex);
  }

  /**
   * Gets row id.
   *
   * @param columnLabel the column label
   * @return the row id
   * @throws SQLException the sql exception
   */
  @Override
  public RowId getRowId(final String columnLabel) throws SQLException {
    return resultSet.getRowId(columnLabel);
  }

  /**
   * Update row id.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateRowId(final int columnIndex, final RowId x) throws SQLException {
    resultSet.updateRowId(columnIndex, x);
  }

  /**
   * Update row id.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateRowId(final String columnLabel, final RowId x) throws SQLException {
    resultSet.updateRowId(columnLabel, x);
  }

  /**
   * Gets holdability.
   *
   * @return the holdability
   * @throws SQLException the sql exception
   */
  @Override
  public int getHoldability() throws SQLException {
    return resultSet.getHoldability();
  }

  /**
   * Is closed boolean.
   *
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean isClosed() throws SQLException {
    return resultSet.isClosed();
  }

  /**
   * Update n string.
   *
   * @param columnIndex the column index
   * @param nString     the n string
   * @throws SQLException the sql exception
   */
  @Override
  public void updateNString(final int columnIndex, final String nString) throws SQLException {
    resultSet.updateNString(columnIndex, nString);
  }

  /**
   * Update n string.
   *
   * @param columnLabel the column label
   * @param nString     the n string
   * @throws SQLException the sql exception
   */
  @Override
  public void updateNString(final String columnLabel, final String nString) throws SQLException {
    resultSet.updateNString(columnLabel, nString);
  }

  /**
   * Update n clob.
   *
   * @param columnIndex the column index
   * @param nClob       the n clob
   * @throws SQLException the sql exception
   */
  @Override
  public void updateNClob(final int columnIndex, final NClob nClob) throws SQLException {
    resultSet.updateNClob(columnIndex, nClob);
  }

  /**
   * Update n clob.
   *
   * @param columnLabel the column label
   * @param nClob       the n clob
   * @throws SQLException the sql exception
   */
  @Override
  public void updateNClob(final String columnLabel, final NClob nClob) throws SQLException {
    resultSet.updateNClob(columnLabel, nClob);
  }

  /**
   * Gets n clob.
   *
   * @param columnIndex the column index
   * @return the n clob
   * @throws SQLException the sql exception
   */
  @Override
  public NClob getNClob(final int columnIndex) throws SQLException {
    return resultSet.getNClob(columnIndex);
  }

  /**
   * Gets n clob.
   *
   * @param columnLabel the column label
   * @return the n clob
   * @throws SQLException the sql exception
   */
  @Override
  public NClob getNClob(final String columnLabel) throws SQLException {
    return resultSet.getNClob(columnLabel);
  }

  /**
   * Gets sqlxml.
   *
   * @param columnIndex the column index
   * @return the sqlxml
   * @throws SQLException the sql exception
   */
  @Override
  public SQLXML getSQLXML(final int columnIndex) throws SQLException {
    return resultSet.getSQLXML(columnIndex);
  }

  /**
   * Gets sqlxml.
   *
   * @param columnLabel the column label
   * @return the sqlxml
   * @throws SQLException the sql exception
   */
  @Override
  public SQLXML getSQLXML(final String columnLabel) throws SQLException {
    return resultSet.getSQLXML(columnLabel);
  }

  /**
   * Update sqlxml.
   *
   * @param columnIndex the column index
   * @param xmlObject   the xml object
   * @throws SQLException the sql exception
   */
  @Override
  public void updateSQLXML(final int columnIndex, final SQLXML xmlObject) throws SQLException {
    resultSet.updateSQLXML(columnIndex, xmlObject);
  }

  /**
   * Update sqlxml.
   *
   * @param columnLabel the column label
   * @param xmlObject   the xml object
   * @throws SQLException the sql exception
   */
  @Override
  public void updateSQLXML(final String columnLabel, final SQLXML xmlObject) throws SQLException {
    resultSet.updateSQLXML(columnLabel, xmlObject);
  }

  /**
   * Update n character stream.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateNCharacterStream(final int columnIndex, final Reader x, final long length) throws SQLException {
    resultSet.updateNCharacterStream(columnIndex, x, length);
  }

  /**
   * Update n character stream.
   *
   * @param columnLabel the column label
   * @param reader      the reader
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateNCharacterStream(final String columnLabel, final Reader reader, final long length) throws SQLException {
    resultSet.updateNCharacterStream(columnLabel, reader, length);
  }

  /**
   * Update n character stream.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateNCharacterStream(final int columnIndex, final Reader x) throws SQLException {
    resultSet.updateNCharacterStream(columnIndex, x);
  }

  /**
   * Update n character stream.
   *
   * @param columnLabel the column label
   * @param reader      the reader
   * @throws SQLException the sql exception
   */
  @Override
  public void updateNCharacterStream(final String columnLabel, final Reader reader) throws SQLException {
    resultSet.updateNCharacterStream(columnLabel, reader);
  }

  /**
   * Update blob.
   *
   * @param columnIndex the column index
   * @param inputStream the input stream
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBlob(final int columnIndex, final InputStream inputStream, final long length) throws SQLException {
    resultSet.updateBlob(columnIndex, inputStream, length);
  }

  /**
   * Update blob.
   *
   * @param columnLabel the column label
   * @param inputStream the input stream
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBlob(final String columnLabel, final InputStream inputStream, final long length) throws SQLException {
    resultSet.updateBlob(columnLabel, inputStream, length);
  }

  /**
   * Update blob.
   *
   * @param columnIndex the column index
   * @param inputStream the input stream
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBlob(final int columnIndex, final InputStream inputStream) throws SQLException {
    resultSet.updateBlob(columnIndex, inputStream);
  }

  /**
   * Update blob.
   *
   * @param columnLabel the column label
   * @param inputStream the input stream
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBlob(final String columnLabel, final InputStream inputStream) throws SQLException {
    resultSet.updateBlob(columnLabel, inputStream);
  }

  /**
   * Update clob.
   *
   * @param columnIndex the column index
   * @param reader      the reader
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateClob(final int columnIndex, final Reader reader, final long length) throws SQLException {
    resultSet.updateClob(columnIndex, reader, length);
  }

  /**
   * Update clob.
   *
   * @param columnLabel the column label
   * @param reader      the reader
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateClob(final String columnLabel, final Reader reader, final long length) throws SQLException {
    resultSet.updateClob(columnLabel, reader, length);
  }

  /**
   * Update clob.
   *
   * @param columnIndex the column index
   * @param reader      the reader
   * @throws SQLException the sql exception
   */
  @Override
  public void updateClob(final int columnIndex, final Reader reader) throws SQLException {
    resultSet.updateClob(columnIndex, reader);
  }

  /**
   * Update clob.
   *
   * @param columnLabel the column label
   * @param reader      the reader
   * @throws SQLException the sql exception
   */
  @Override
  public void updateClob(final String columnLabel, final Reader reader) throws SQLException {
    resultSet.updateClob(columnLabel, reader);
  }

  /**
   * Update n clob.
   *
   * @param columnIndex the column index
   * @param reader      the reader
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateNClob(final int columnIndex, final Reader reader, final long length) throws SQLException {
    resultSet.updateNClob(columnIndex, reader, length);
  }

  /**
   * Update n clob.
   *
   * @param columnLabel the column label
   * @param reader      the reader
   * @param length      the length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateNClob(final String columnLabel, final Reader reader, final long length) throws SQLException {
    resultSet.updateNClob(columnLabel, reader, length);
  }

  /**
   * Update n clob.
   *
   * @param columnIndex the column index
   * @param reader      the reader
   * @throws SQLException the sql exception
   */
  @Override
  public void updateNClob(final int columnIndex, final Reader reader) throws SQLException {
    resultSet.updateNClob(columnIndex, reader);
  }

  /**
   * Update n clob.
   *
   * @param columnLabel the column label
   * @param reader      the reader
   * @throws SQLException the sql exception
   */
  @Override
  public void updateNClob(final String columnLabel, final Reader reader) throws SQLException {
    resultSet.updateNClob(columnLabel, reader);
  }

  /**
   * Gets object.
   *
   * @param <T>         the type parameter
   * @param columnIndex the column index
   * @param type        the type
   * @return the object
   * @throws SQLException the sql exception
   */
  @Override
  public <T> T getObject(final int columnIndex, final Class<T> type) throws SQLException {
    return resultSet.getObject(columnIndex, type);
  }

  /**
   * Gets object.
   *
   * @param <T>         the type parameter
   * @param columnLabel the column label
   * @param type        the type
   * @return the object
   * @throws SQLException the sql exception
   */
  @Override
  public <T> T getObject(final String columnLabel, final Class<T> type) throws SQLException {
    return resultSet.getObject(columnLabel, type);
  }

  /**
   * Update object.
   *
   * @param columnIndex   the column index
   * @param x             the x
   * @param targetSqlType the target sql type
   * @param scaleOrLength the scale or length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateObject(final int columnIndex, final Object x, final SQLType targetSqlType, final int scaleOrLength)
      throws SQLException {
    resultSet.updateObject(columnIndex, x, targetSqlType, scaleOrLength);
  }

  /**
   * Update object.
   *
   * @param columnLabel   the column label
   * @param x             the x
   * @param targetSqlType the target sql type
   * @param scaleOrLength the scale or length
   * @throws SQLException the sql exception
   */
  @Override
  public void updateObject(final String columnLabel, final Object x, final SQLType targetSqlType, final int scaleOrLength)
      throws SQLException {
    resultSet.updateObject(columnLabel, x, targetSqlType, scaleOrLength);
  }

  /**
   * Update object.
   *
   * @param columnIndex   the column index
   * @param x             the x
   * @param targetSqlType the target sql type
   * @throws SQLException the sql exception
   */
  @Override
  public void updateObject(final int columnIndex, final Object x, final SQLType targetSqlType) throws SQLException {
    resultSet.updateObject(columnIndex, x, targetSqlType);
  }

  /**
   * Update object.
   *
   * @param columnLabel   the column label
   * @param x             the x
   * @param targetSqlType the target sql type
   * @throws SQLException the sql exception
   */
  @Override
  public void updateObject(final String columnLabel, final Object x, final SQLType targetSqlType) throws SQLException {
    resultSet.updateObject(columnLabel, x, targetSqlType);
  }

  /**
   * Update ascii stream.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateAsciiStream(final int columnIndex, final InputStream x) throws SQLException {
    resultSet.updateAsciiStream(columnIndex, x);
  }

  /**
   * Update binary stream.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBinaryStream(final int columnIndex, final InputStream x) throws SQLException {
    resultSet.updateBinaryStream(columnIndex, x);
  }

  /**
   * Update character stream.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateCharacterStream(final int columnIndex, final Reader x) throws SQLException {
    resultSet.updateCharacterStream(columnIndex, x);
  }

  /**
   * Update ascii stream.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateAsciiStream(final String columnLabel, final InputStream x) throws SQLException {
    resultSet.updateAsciiStream(columnLabel, x);
  }

  /**
   * Update binary stream.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateBinaryStream(final String columnLabel, final InputStream x) throws SQLException {
    resultSet.updateBinaryStream(columnLabel, x);
  }

  /**
   * Update character stream.
   *
   * @param columnLabel the column label
   * @param reader      the reader
   * @throws SQLException the sql exception
   */
  @Override
  public void updateCharacterStream(final String columnLabel, final Reader reader) throws SQLException {
    resultSet.updateCharacterStream(columnLabel, reader);
  }

  /**
   * Gets url.
   *
   * @param columnIndex the column index
   * @return the url
   * @throws SQLException the sql exception
   */
  @Override
  public URL getURL(final int columnIndex) throws SQLException {
    return resultSet.getURL(columnIndex);
  }

  /**
   * Gets url.
   *
   * @param columnLabel the column label
   * @return the url
   * @throws SQLException the sql exception
   */
  @Override
  public URL getURL(final String columnLabel) throws SQLException {
    return resultSet.getURL(columnLabel);
  }

  /**
   * Update ref.
   *
   * @param columnIndex the column index
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateRef(final int columnIndex, final Ref x) throws SQLException {
    resultSet.updateRef(columnIndex, x);
  }

  /**
   * Update ref.
   *
   * @param columnLabel the column label
   * @param x           the x
   * @throws SQLException the sql exception
   */
  @Override
  public void updateRef(final String columnLabel, final Ref x) throws SQLException {
    resultSet.updateRef(columnLabel, x);
  }

  /**
   * Is wrapper for boolean.
   *
   * @param iface the iface
   * @return the boolean
   * @throws SQLException the sql exception
   */
  @Override
  public boolean isWrapperFor(final Class<?> iface) throws SQLException {
    return resultSet.isWrapperFor(iface);
  }

  /**
   * Unwrap t.
   *
   * @param <T>   the type parameter
   * @param iface the iface
   * @return the t
   * @throws SQLException the sql exception
   */
  @Override
  public <T> T unwrap(final Class<T> iface) throws SQLException {
    return resultSet.unwrap(iface);
  }
}

