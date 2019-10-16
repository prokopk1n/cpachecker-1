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
import java.util.Set;
import java.util.SortedSet;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.smg.util.PersistentSet;

public class CIStringState implements AbstractState, Serializable {

  private static final long serialVersionUID = 1L;
  private PersistentSet<Character> certainly;
  private PersistentSet<Character> maybe;

  CIStringState() {
    certainly = PersistentSet.of();
    maybe = PersistentSet.of();
  }

  CIStringState(String str) {

    certainly = PersistentSet.of();
    maybe = PersistentSet.of();

    char[] charArray = str.toCharArray();

    for (int i = 0; i < charArray.length; i++) {
      certainly.addAndCopy(new Character(charArray[i]));
      maybe.addAndCopy(new Character(charArray[i]));
    }
  }

  public final static CIStringState BOTTOM = new CIStringState();

  public void SetCertainly(Set<Character> set) {
    certainly.removeAllAndCopy();
    certainly.addAllAndCopy(set);
  }

  public void SetMaybe(Set<Character> set) {
    maybe.removeAllAndCopy();
    maybe.addAllAndCopy(set);
  }

  public void AddToSertainly(Set<Character> set) {
    certainly.addAllAndCopy(set);
  }

  public void AddToMaybe(SortedSet<Character> set) {
    maybe.addAllAndCopy(set);
  }

  public PersistentSet<Character> GetCertainly() {
    return certainly;
  }

  public PersistentSet<Character> GetMaybe() {
    return maybe;
  }

  @Override
  public boolean equals(Object pObj) {
    CIStringState other = (CIStringState) pObj;
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