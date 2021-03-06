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
package org.apache.sqoop.client.shell;

import org.apache.sqoop.client.core.ClientError;
import org.apache.sqoop.client.core.Constants;
import org.apache.sqoop.common.SqoopException;
import org.codehaus.groovy.tools.shell.Shell;

import java.util.List;

import static org.apache.sqoop.client.shell.ShellEnvironment.*;

/**
 * Client side cloning of connection and job objects.
 */
public class CloneCommand extends SqoopCommand {

  private CloneConnectionFunction connectionFunction;
  private CloneJobFunction jobFunction;

  public CloneCommand(Shell shell) {
    super(shell, Constants.CMD_CLONE, Constants.CMD_CLONE_SC,
      new String[] {Constants.FN_CONNECTION, Constants.FN_JOB},
      Constants.PRE_CLONE, Constants.SUF_INFO);
  }

  public Object executeCommand(List args) {
    if(!isInteractive()) {
      throw new SqoopException(ClientError.CLIENT_0007, "clone");
    }

    if (args.size() == 0) {
      printlnResource(Constants.RES_CLONE_USAGE, getUsage());
      return null;
    }

    String func = (String)args.get(0);
    if (func.equals(Constants.FN_CONNECTION)) {
      if (connectionFunction == null) {
        connectionFunction = new CloneConnectionFunction();
      }
      return connectionFunction.execute(args);
    } else if (func.equals(Constants.FN_JOB)) {
      if (jobFunction == null) {
        jobFunction = new CloneJobFunction();
      }
      return jobFunction.execute(args);
    } else {
      printlnResource(Constants.RES_FUNCTION_UNKNOWN, func);
      return null;
    }
  }
}
