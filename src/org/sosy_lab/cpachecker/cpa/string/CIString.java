/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.string;

import java.io.Serializable;
import java.util.SortedSet;
import java.util.TreeSet;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;

public class CIString implements AbstractState, Serializable {

  private static final long serialVersionUID = 1L;
  private SortedSet<Character> certainly;
  private SortedSet<Character> maybe;

  CIString() {
    certainly = new TreeSet<>();
    maybe = new TreeSet<>();
  }

  CIString(String str) {

    certainly = new TreeSet<>();
    maybe = new TreeSet<>();

    char[] charArray = str.toCharArray();

    for (int i = 0; i < charArray.length; i++) {
      certainly.add(new Character(charArray[i]));
      maybe.add(new Character(charArray[i]));
    }
  }

  public final static CIString BOTTOM = new CIString();

  public void SetCertainly(SortedSet<Character> set) {
    certainly.clear();
    certainly.addAll(set);
  }

  public void SetMaybe(SortedSet<Character> set) {
    maybe.clear();
    maybe.addAll(set);
  }

  public void AddToSertainly(SortedSet<Character> set) {
    certainly.addAll(set);
  }

  public void AddToMaybe(SortedSet<Character> set) {
    maybe.addAll(set);
  }

  public SortedSet<Character> GetCertainly() {
    return certainly;
  }

  public SortedSet<Character> GetMaybe() {
    return maybe;
  }

  @Override
  public boolean equals(Object pObj) {
    CIString other = (CIString) pObj;
    if (other != null) {
      return certainly.equals(other.GetCertainly()) && maybe.equals(other.GetMaybe());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    return "(" + certainly.toString() + ", " + maybe.toString() + ")";
  }
}