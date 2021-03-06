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
package org.apache.sqoop.model;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Test class for org.apache.sqoop.model.MConnection
 */
public class TestMConnection {

  /**
   * Test for initialization
   */
  @Test
  public void testInitialization() {
    MConnectionForms connectorPart = connector1();
    MConnectionForms frameworkPart = connector2();
    MConnection connection = new MConnection(123l, connectorPart, frameworkPart);
    assertEquals(123l, connection.getConnectorId());
    assertEquals(connector1(), connection.getConnectorPart());
    assertEquals(connector2(), connection.getFrameworkPart());
    assertFalse(connector1().equals(connection.getFrameworkPart()));
    connection.setName("NAME");
    assertEquals("NAME", connection.getName());
    assertEquals(connector1().getForms().get(0), connection.getConnectorForm("FORMNAME"));
    assertEquals(connector2().getForms().get(0), connection.getFrameworkForm("form"));
  }

  @Test
  public void testClone() {
    MConnectionForms connectorPart = connector1();
    MConnectionForms frameworkPart = connector2();
    MConnection connection = new MConnection(123l, connectorPart, frameworkPart);
    connection.setPersistenceId(12l);
    MForm originalForm = connection.getConnectorPart().getForms().get(0);
    MConnection clone1 = connection.clone(true);
    assertEquals(123l, clone1.getConnectorId());
    MForm clonedForm1 = clone1.getConnectorPart().getForms().get(0);
    assertEquals(clonedForm1.getInputs().get(0).getValue(), originalForm.getInputs().get(0).getValue());
    assertEquals(clonedForm1.getInputs().get(1).getValue(), originalForm.getInputs().get(1).getValue());
    assertNotNull(clonedForm1.getInputs().get(0).getValue());
    assertNotNull(clonedForm1.getInputs().get(1).getValue());
    assertEquals(connection, clone1);
    MConnection cloneCopy = clone1.clone(true);
    assertEquals(clone1, cloneCopy);
    //Clone without values
    MConnection clone2 = connection.clone(false);
    assertNotSame(connection, clone2);
  }

  private MConnectionForms connector1() {
    List<MForm> forms = new ArrayList<MForm>();
    MIntegerInput input = new MIntegerInput("INTEGER-INPUT", false);
    input.setValue(100);
    MStringInput strInput = new MStringInput("STRING-INPUT",false,(short)20);
    strInput.setValue("TEST-VALUE");
    List<MInput<?>> list = new ArrayList<MInput<?>>();
    list.add(input);
    list.add(strInput);
    MForm form = new MForm("FORMNAME", list);
    forms.add(form);
    return new MConnectionForms(forms);
  }

  private MConnectionForms connector2() {
    List<MForm> forms = new ArrayList<MForm>();
    MMapInput input = new MMapInput("MAP-INPUT", false);
    List<MInput<?>> list = new ArrayList<MInput<?>>();
    list.add(input);
    MForm form = new MForm("form", list);
    forms.add(form);
    return new MConnectionForms(forms);
  }

}
