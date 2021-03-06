/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sqoop.connector.jdbc;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import org.apache.sqoop.common.SqoopException;
import org.apache.sqoop.connector.jdbc.configuration.ConnectionConfiguration;
import org.apache.sqoop.connector.jdbc.configuration.ImportJobConfiguration;
import org.apache.sqoop.job.etl.Partition;
import org.apache.sqoop.job.etl.Partitioner;
import org.apache.sqoop.job.etl.PartitionerContext;

public class GenericJdbcImportPartitioner extends Partitioner<ConnectionConfiguration, ImportJobConfiguration> {

  private static final BigDecimal NUMERIC_MIN_INCREMENT = new BigDecimal(10000 * Double.MIN_VALUE);


  private long numberPartitions;
  private String partitionColumnName;
  private int partitionColumnType;
  private String partitionMinValue;
  private String partitionMaxValue;

  @Override
  public List<Partition> getPartitions(PartitionerContext context,ConnectionConfiguration connection, ImportJobConfiguration job) {
    numberPartitions = context.getMaxPartitions();
    partitionColumnName = context.getString(GenericJdbcConnectorConstants.CONNECTOR_JDBC_PARTITION_COLUMNNAME);
    partitionColumnType = context.getInt(GenericJdbcConnectorConstants.CONNECTOR_JDBC_PARTITION_COLUMNTYPE, -1);
    partitionMinValue = context.getString(GenericJdbcConnectorConstants.CONNECTOR_JDBC_PARTITION_MINVALUE);
    partitionMaxValue = context.getString(GenericJdbcConnectorConstants.CONNECTOR_JDBC_PARTITION_MAXVALUE);

    if (partitionMinValue == null && partitionMaxValue == null) {
      List<Partition> partitions = new LinkedList<Partition>();
      GenericJdbcImportPartition partition = new GenericJdbcImportPartition();
      partition.setConditions(partitionColumnName + "IS NULL");
      partitions.add(partition);
      return partitions;
    }

    switch (partitionColumnType) {
    case Types.TINYINT:
    case Types.SMALLINT:
    case Types.INTEGER:
    case Types.BIGINT:
      // Integer column
      return partitionIntegerColumn();

    case Types.REAL:
    case Types.FLOAT:
    case Types.DOUBLE:
      // Floating point column
      return partitionFloatingPointColumn();

    case Types.NUMERIC:
    case Types.DECIMAL:
      // Decimal column
      return partitionNumericColumn();

    case Types.BIT:
    case Types.BOOLEAN:
      // Boolean column
      return partitionBooleanColumn();

    case Types.DATE:
    case Types.TIME:
    case Types.TIMESTAMP:
      // Date time column
      return partitionDateTimeColumn();

    case Types.CHAR:
    case Types.VARCHAR:
    case Types.LONGVARCHAR:
      // Text column
      return partitionTextColumn();

    default:
      throw new SqoopException(
          GenericJdbcConnectorError.GENERIC_JDBC_CONNECTOR_0011,
          String.valueOf(partitionColumnType));
    }
  }
  protected List<Partition> partitionDateTimeColumn() {
    List<Partition> partitions = new LinkedList<Partition>();

    long minDateValue = 0;
    long maxDateValue = 0;
    SimpleDateFormat sdf = null;
    switch(partitionColumnType) {
      case Types.DATE:
        sdf = new SimpleDateFormat("yyyy-MM-dd");
        minDateValue = Date.valueOf(partitionMinValue).getTime();
        maxDateValue = Date.valueOf(partitionMaxValue).getTime();
        break;
      case Types.TIME:
        sdf = new SimpleDateFormat("HH:mm:ss");
        minDateValue = Time.valueOf(partitionMinValue).getTime();
        maxDateValue = Time.valueOf(partitionMaxValue).getTime();
        break;
      case Types.TIMESTAMP:
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        minDateValue = Timestamp.valueOf(partitionMinValue).getTime();
        maxDateValue = Timestamp.valueOf(partitionMaxValue).getTime();
        break;
    }

    long tzOffset = TimeZone.getDefault().getRawOffset();

    minDateValue += tzOffset;
    maxDateValue += tzOffset;

    sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

    long interval =  (maxDateValue - minDateValue) / numberPartitions;
    long remainder = (maxDateValue - minDateValue) % numberPartitions;

    if (interval == 0) {
      numberPartitions = (int)remainder;
    }

    long lowerBound;
    long upperBound = minDateValue;

    Object objLB = null;
    Object objUB = null;

    for (int i = 1; i < numberPartitions; i++) {
      lowerBound = upperBound;
      upperBound = lowerBound + interval;
      upperBound += (i <= remainder) ? 1 : 0;

      switch(partitionColumnType) {
        case Types.DATE:
          objLB = new Date(lowerBound);
          objUB = new Date(upperBound);
          break;
        case Types.TIME:
          objLB = new Time(lowerBound);
          objUB = new Time(upperBound);

          break;
        case Types.TIMESTAMP:
          objLB = new Timestamp(lowerBound);
          objUB = new Timestamp(upperBound);
          break;
      }

      GenericJdbcImportPartition partition = new GenericJdbcImportPartition();
      partition.setConditions(
          constructDateConditions(sdf, objLB, objUB, false));
      partitions.add(partition);
    }

    switch(partitionColumnType) {
      case Types.DATE:
        objLB = new Date(upperBound);
        objUB = new Date(maxDateValue);
        break;
      case Types.TIME:
        objLB = new Time(upperBound);
        objUB = new Time(maxDateValue);
        break;
      case Types.TIMESTAMP:
        objLB = new Timestamp(upperBound);
        objUB = new Timestamp(maxDateValue);
        break;
    }


    GenericJdbcImportPartition partition = new GenericJdbcImportPartition();
    partition.setConditions(
        constructDateConditions(sdf, objLB, objUB, true));
    partitions.add(partition);
    return partitions;
  }

  protected List<Partition> partitionTextColumn() {
    List<Partition> partitions = new LinkedList<Partition>();

    String minStringValue = null;
    String maxStringValue = null;

    // Remove common prefix if any as it does not affect outcome.
    int maxPrefixLen = Math.min(partitionMinValue.length(),
        partitionMaxValue.length());
    // Calculate common prefix length
    int cpLen = 0;

    for (cpLen = 0; cpLen < maxPrefixLen; cpLen++) {
      char c1 = partitionMinValue.charAt(cpLen);
      char c2 = partitionMaxValue.charAt(cpLen);
      if (c1 != c2) {
        break;
      }
    }

    // The common prefix has length 'sharedLen'. Extract it from both.
    String prefix = partitionMinValue.substring(0, cpLen);
    minStringValue = partitionMinValue.substring(cpLen);
    maxStringValue = partitionMaxValue.substring(cpLen);

    BigDecimal minStringBD = textToBigDecimal(minStringValue);
    BigDecimal maxStringBD = textToBigDecimal(maxStringValue);

    // Having one single value means that we can create only one single split
    if(minStringBD.equals(maxStringBD)) {
      GenericJdbcImportPartition partition = new GenericJdbcImportPartition();
      partition.setConditions(constructTextConditions(prefix, maxStringBD));
      partitions.add(partition);
      return partitions;
    }

    // Get all the split points together.
    List<BigDecimal> splitPoints = new LinkedList<BigDecimal>();

    BigDecimal splitSize = divide(maxStringBD.subtract(minStringBD),
        new BigDecimal(numberPartitions));
    if (splitSize.compareTo(NUMERIC_MIN_INCREMENT) < 0) {
      splitSize = NUMERIC_MIN_INCREMENT;
    }

    BigDecimal curVal = minStringBD;

    while (curVal.compareTo(maxStringBD) <= 0) {
      splitPoints.add(curVal);
      curVal = curVal.add(splitSize);
    }

    if (splitPoints.size() == 0
        || splitPoints.get(0).compareTo(minStringBD) != 0) {
      splitPoints.add(0, minStringBD);
    }

    if (splitPoints.get(splitPoints.size() - 1).compareTo(maxStringBD) != 0
        || splitPoints.size() == 1) {
      splitPoints.add(maxStringBD);
    }

    // Turn the split points into a set of string intervals.
    BigDecimal start = splitPoints.get(0);
    for (int i = 1; i < splitPoints.size(); i++) {
      BigDecimal end = splitPoints.get(i);

      GenericJdbcImportPartition partition = new GenericJdbcImportPartition();
      partition.setConditions(constructTextConditions(prefix, start,
          end, i == splitPoints.size() - 1));
      partitions.add(partition);

      start = end;
    }

    return partitions;
  }


  protected List<Partition> partitionIntegerColumn() {
    List<Partition> partitions = new LinkedList<Partition>();

    long minValue = partitionMinValue == null ? Long.MIN_VALUE
      : Long.parseLong(partitionMinValue);
    long maxValue = Long.parseLong(partitionMaxValue);

    long interval =  (maxValue - minValue) / numberPartitions;
    long remainder = (maxValue - minValue) % numberPartitions;

    if (interval == 0) {
      numberPartitions = (int)remainder;
    }

    long lowerBound;
    long upperBound = minValue;
    for (int i = 1; i < numberPartitions; i++) {
      lowerBound = upperBound;
      upperBound = lowerBound + interval;
      upperBound += (i <= remainder) ? 1 : 0;

      GenericJdbcImportPartition partition = new GenericJdbcImportPartition();
      partition.setConditions(
          constructConditions(lowerBound, upperBound, false));
      partitions.add(partition);
    }

    GenericJdbcImportPartition partition = new GenericJdbcImportPartition();
    partition.setConditions(
        constructConditions(upperBound, maxValue, true));
    partitions.add(partition);

    return partitions;
  }

  protected List<Partition> partitionFloatingPointColumn() {
    List<Partition> partitions = new LinkedList<Partition>();


    double minValue = partitionMinValue == null ? Double.MIN_VALUE
      : Double.parseDouble(partitionMinValue);
    double maxValue = Double.parseDouble(partitionMaxValue);

    double interval =  (maxValue - minValue) / numberPartitions;

    double lowerBound;
    double upperBound = minValue;
    for (int i = 1; i < numberPartitions; i++) {
      lowerBound = upperBound;
      upperBound = lowerBound + interval;

      GenericJdbcImportPartition partition = new GenericJdbcImportPartition();
      partition.setConditions(
          constructConditions(lowerBound, upperBound, false));
      partitions.add(partition);
    }

    GenericJdbcImportPartition partition = new GenericJdbcImportPartition();
    partition.setConditions(
        constructConditions(upperBound, maxValue, true));
    partitions.add(partition);

    return partitions;
  }

  protected List<Partition> partitionNumericColumn() {
    List<Partition> partitions = new LinkedList<Partition>();
    // Having one end in null is not supported
    if (partitionMinValue == null || partitionMaxValue == null) {
      throw new SqoopException(GenericJdbcConnectorError.GENERIC_JDBC_CONNECTOR_0015);
    }

    BigDecimal minValue = new BigDecimal(partitionMinValue);
    BigDecimal maxValue = new BigDecimal(partitionMaxValue);

    // Having one single value means that we can create only one single split
    if(minValue.equals(maxValue)) {
      GenericJdbcImportPartition partition = new GenericJdbcImportPartition();
      partition.setConditions(constructConditions(minValue));
      partitions.add(partition);
      return partitions;
    }

    // Get all the split points together.
    List<BigDecimal> splitPoints = new LinkedList<BigDecimal>();

    BigDecimal splitSize = divide(maxValue.subtract(minValue), new BigDecimal(numberPartitions));

    if (splitSize.compareTo(NUMERIC_MIN_INCREMENT) < 0) {
      splitSize = NUMERIC_MIN_INCREMENT;
    }

    BigDecimal curVal = minValue;

    while (curVal.compareTo(maxValue) <= 0) {
      splitPoints.add(curVal);
      curVal = curVal.add(splitSize);
    }

    if (splitPoints.get(splitPoints.size() - 1).compareTo(maxValue) != 0 || splitPoints.size() == 1) {
      splitPoints.remove(splitPoints.size() - 1);
      splitPoints.add(maxValue);
    }

    // Turn the split points into a set of intervals.
    BigDecimal start = splitPoints.get(0);
    for (int i = 1; i < splitPoints.size(); i++) {
      BigDecimal end = splitPoints.get(i);

      GenericJdbcImportPartition partition = new GenericJdbcImportPartition();
      partition.setConditions(constructConditions(start, end, i == splitPoints.size() - 1));
      partitions.add(partition);

      start = end;
    }

    return partitions;
  }

  protected  List<Partition> partitionBooleanColumn() {
    List<Partition> partitions = new LinkedList<Partition>();


    Boolean minValue = parseBooleanValue(partitionMinValue);
    Boolean maxValue = parseBooleanValue(partitionMaxValue);

    StringBuilder conditions = new StringBuilder();

    // Having one single value means that we can create only one single split
    if(minValue.equals(maxValue)) {
      GenericJdbcImportPartition partition = new GenericJdbcImportPartition();

      conditions.append(partitionColumnName).append(" = ")
          .append(maxValue);
      partition.setConditions(conditions.toString());
      partitions.add(partition);
      return partitions;
    }

    GenericJdbcImportPartition partition = new GenericJdbcImportPartition();

    if (partitionMinValue == null) {
      conditions = new StringBuilder();
      conditions.append(partitionColumnName).append(" IS NULL");
      partition.setConditions(conditions.toString());
      partitions.add(partition);
    }
    partition = new GenericJdbcImportPartition();
    conditions = new StringBuilder();
    conditions.append(partitionColumnName).append(" = TRUE");
    partition.setConditions(conditions.toString());
    partitions.add(partition);
    partition = new GenericJdbcImportPartition();
    conditions = new StringBuilder();
    conditions.append(partitionColumnName).append(" = FALSE");
    partition.setConditions(conditions.toString());
    partitions.add(partition);
    return partitions;
  }

  private Boolean parseBooleanValue(String value) {
    if (value == null) {
      return null;
    }
    if (value.equals("1")) {
      return Boolean.TRUE;
    } else if (value.equals("0")) {
      return Boolean.FALSE;
    } else {
      return Boolean.parseBoolean(value);
    }
  }

  protected BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
    try {
      return numerator.divide(denominator);
    } catch (ArithmeticException ae) {
      return numerator.divide(denominator, BigDecimal.ROUND_HALF_UP);
    }
  }

  protected String constructConditions(
      Object lowerBound, Object upperBound, boolean lastOne) {
    StringBuilder conditions = new StringBuilder();
    conditions.append(lowerBound);
    conditions.append(" <= ");
    conditions.append(partitionColumnName);
    conditions.append(" AND ");
    conditions.append(partitionColumnName);
    conditions.append(lastOne ? " <= " : " < ");
    conditions.append(upperBound);
    return conditions.toString();
  }

  protected String constructConditions(Object value) {
    return new StringBuilder()
      .append(partitionColumnName)
      .append(" = ")
      .append(value)
      .toString()
     ;
  }

  protected String constructDateConditions(SimpleDateFormat sdf,
      Object lowerBound, Object upperBound, boolean lastOne) {
    StringBuilder conditions = new StringBuilder();
    conditions.append('\'').append(sdf.format((java.util.Date)lowerBound)).append('\'');
    conditions.append(" <= ");
    conditions.append(partitionColumnName);
    conditions.append(" AND ");
    conditions.append(partitionColumnName);
    conditions.append(lastOne ? " <= " : " < ");
    conditions.append('\'').append(sdf.format((java.util.Date)upperBound)).append('\'');
    return conditions.toString();
  }

  protected String constructTextConditions(String prefix,
      Object lowerBound, Object upperBound, boolean lastOne) {
    StringBuilder conditions = new StringBuilder();
    String lbString = prefix + bigDecimalToText((BigDecimal)lowerBound);
    String ubString = prefix + bigDecimalToText((BigDecimal)upperBound);
    conditions.append('\'').append(lbString).append('\'');
    conditions.append(" <= ");
    conditions.append(partitionColumnName);
    conditions.append(" AND ");
    conditions.append(partitionColumnName);
    conditions.append(lastOne ? " <= " : " < ");
    conditions.append('\'').append(ubString).append('\'');
    return conditions.toString();
  }

  protected String constructTextConditions(String prefix, Object value) {
    return new StringBuilder()
      .append(partitionColumnName)
      .append(" = ").append('\'')
      .append(prefix + bigDecimalToText((BigDecimal)value))
      .append('\'').toString()
     ;
  }


  /**
   *  Converts a string to a BigDecimal representation in Base 2^21 format.
   *  The maximum Unicode code point value defined is 10FFFF.  Although
   *  not all database system support UTF16 and mostly we expect UCS2
   *  characters only, for completeness, we assume that all the unicode
   *  characters are supported.
   *  Given a string 's' containing characters s_0, s_1,..s_n,
   *  the string is interpreted as the number: 0.s_0 s_1 s_2 s_3 s_48)
   *  This can be split and each split point can be converted back to
   *  a string value for comparison purposes.   The number of characters
   *  is restricted to prevent repeating fractions and rounding errors
   *  towards the higher fraction positions.
   */
  private static final BigDecimal UNITS_BASE = new BigDecimal(2097152);
  private static final int MAX_CHARS_TO_CONVERT = 4;

  private BigDecimal textToBigDecimal(String str) {
    BigDecimal result = BigDecimal.ZERO;
    BigDecimal divisor = UNITS_BASE;

    int len = Math.min(str.length(), MAX_CHARS_TO_CONVERT);

    for (int n = 0; n < len; ) {
      int codePoint = str.codePointAt(n);
      n += Character.charCount(codePoint);
      BigDecimal val = divide(new BigDecimal(codePoint), divisor);
      result = result.add(val);
      divisor = divisor.multiply(UNITS_BASE);
    }

    return result;
  }

  private String bigDecimalToText(BigDecimal bd) {
    BigDecimal curVal = bd.stripTrailingZeros();
    StringBuilder sb = new StringBuilder();

    for (int n = 0; n < MAX_CHARS_TO_CONVERT; ++n) {
      curVal = curVal.multiply(UNITS_BASE);
      int cp = curVal.intValue();
      if (0 == cp) {
        break;
      }
      curVal = curVal.subtract(new BigDecimal(cp));
      sb.append(Character.toChars(cp));
    }
    return sb.toString();
  }

}
