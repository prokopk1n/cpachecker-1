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
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.ifcsecurity.util.SetUtil;
import org.sosy_lab.cpachecker.cpa.smg.util.PersistentSet;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class CIString
    implements AbstractState, Serializable, LatticeAbstractState<CIString> {

  private static final long serialVersionUID = 1L;
  private PersistentSet<Character> certainly;
  private PersistentSet<Character> maybe;

  CIString() {
    certainly = PersistentSet.of();
    maybe = PersistentSet.of();
  }

  CIString(String str) {

    certainly = PersistentSet.of();
    maybe = PersistentSet.of();

    char[] charArray = str.toCharArray();

    for (int i = 0; i < charArray.length; i++) {
      certainly = certainly.addAndCopy(new Character(charArray[i]));
      maybe = maybe.addAndCopy(new Character(charArray[i]));
    }
  }

  private CIString(PersistentSet<Character> pCertainly, PersistentSet<Character> pMaybe) {
    certainly = pCertainly;
    maybe = pMaybe;
  }

  public CIString copyOf() {
    return new CIString(certainly, maybe);
  }

  public final static CIString BOTTOM = new CIString();

  public boolean isBottom() {
    return this.equals(CIString.BOTTOM);
  }

  public void setCertainly(Set<Character> set) {
    certainly = certainly.removeAllAndCopy();
    certainly = certainly.addAllAndCopy(set);
  }

  public void setMaybe(Set<Character> set) {
    maybe = maybe.removeAllAndCopy();
    maybe = maybe.addAllAndCopy(set);
  }

  public void addToSertainly(Set<Character> set) {
    certainly = certainly.addAllAndCopy(set);
  }

  public void addToMaybe(SortedSet<Character> set) {
    maybe = maybe.addAllAndCopy(set);
  }

  public PersistentSet<Character> getCertainly() {
    return certainly;
  }

  public PersistentSet<Character> getMaybe() {
    return maybe;
  }

  @Override
  public boolean equals(Object pObj) {
    CIString other = (CIString) pObj;
    if (other != null) {
      return certainly.equals(other.getCertainly()) && maybe.equals(other.getMaybe());
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

  @Override
  public CIString join(CIString pOther) throws CPAException, InterruptedException {
    if (pOther == null) {
      return null;
    }

    CIString str = new CIString();

    str.setCertainly(
        SetUtil.generalizedIntersect(this.getCertainly().asSet(), pOther.getCertainly().asSet()));
    str.setMaybe(SetUtil.generalizedUnion(this.getMaybe().asSet(), pOther.getMaybe().asSet()));
    return str;
  }

  @Override
  public boolean isLessOrEqual(CIString pOther) throws CPAException, InterruptedException {

    if (pOther != null) {
      if (this.equals(CIString.BOTTOM)) {
        return true;
      }
      return this.getCertainly().containsAll(pOther.getCertainly().asSet())
          && pOther.getMaybe().containsAll(this.getMaybe().asSet());
    }
    return false;
  }
}