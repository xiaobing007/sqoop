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
package org.apache.sqoop.validation.validators;

import com.google.common.base.Strings;
import org.apache.sqoop.validation.Status;

import java.util.regex.Pattern;

/**
 * Ensure that given string represents a Kite dataset uri.
 */
public class DatasetURIValidator extends AbstractValidator<String> {

  private static final Pattern DATASET_URI_PATTERN = Pattern
      .compile("^dataset:(hive|hdfs|file):.*$");

  @Override
  public void validate(String uri) {
    if (Strings.isNullOrEmpty(uri)) {
      addMessage(Status.ERROR, "Cannot be null nor empty");
      return;
    }

    if (!DATASET_URI_PATTERN.matcher(uri).matches()) {
      addMessage(Status.ERROR, "Invalid dataset URI: " + uri);
    }
  }

}