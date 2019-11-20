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
package org.sosy_lab.cpachecker.cpa.string.util;

import java.util.Set;
import org.sosy_lab.cpachecker.cpa.ifcsecurity.util.SetUtil;
import org.sosy_lab.cpachecker.cpa.smg.util.PersistentSet;

public class explicitCIString implements CIString {

  private static final long serialVersionUID = 1L;
  private PersistentSet<Character> certainly;
  private PersistentSet<Character> maybe;

  public explicitCIString() {
    certainly = PersistentSet.of();
    maybe = PersistentSet.of();
  }

  public explicitCIString(String str) {

    certainly = PersistentSet.of();
    maybe = PersistentSet.of();

    char[] charArray = str.toCharArray();

    for (int i = 0; i < charArray.length; i++) {
      certainly = certainly.addAndCopy(new Character(charArray[i]));
      maybe = maybe.addAndCopy(new Character(charArray[i]));
    }
  }

  private explicitCIString(PersistentSet<Character> pCertainly, PersistentSet<Character> pMaybe) {
    certainly = pCertainly;
    maybe = pMaybe;
  }

  public explicitCIString copyOf() {
    return new explicitCIString(certainly, maybe);
  }

  public final static explicitCIString EMPTY = new explicitCIString();

  public boolean isEmpty() {
    return equals(explicitCIString.EMPTY);
  }

  @Override
  public boolean isBottom() {
    return false;
  }

  @Override
  public boolean equals(Object pObj) {

    if (!(pObj instanceof CIString)) {
      return false;
    }
    explicitCIString other = (explicitCIString) pObj;

    if (other.isBottom()) {
      return false;
    }

    return certainly.equals(other.getCertainly()) && maybe.equals(other.getMaybe());
  }

  public void setCertainly(Set<Character> set) {
    certainly = certainly.removeAllAndCopy();
    certainly = certainly.addAllAndCopy(set);
  }

  public void clearCertainly() {
    certainly = certainly.removeAllAndCopy();
  }

  public void setMaybe(Set<Character> set) {
    maybe = maybe.removeAllAndCopy();
    maybe = maybe.addAllAndCopy(set);
  }

  public void addToSertainly(Set<Character> set) {
    certainly = certainly.addAllAndCopy(set);
  }

  public void addToMaybe(Set<Character> set) {
    maybe = maybe.addAllAndCopy(set);
  }

  @Override
  public PersistentSet<Character> getCertainly() {
    return certainly;
  }

  @Override
  public PersistentSet<Character> getMaybe() {
    return maybe;
  }

  @Override
  public CIString join(CIString pOther) {
    // if (pOther == null) {
    // return null;
    // }

    if(pOther.isBottom()) {
      return bottomCIString.INSTANCE;
    }

    explicitCIString str = new explicitCIString();

    str.setCertainly(
        SetUtil.generalizedIntersect(this.getCertainly().asSet(), pOther.getCertainly().asSet()));
    str.setMaybe(SetUtil.generalizedUnion(this.getMaybe().asSet(), pOther.getMaybe().asSet()));

    return str;
  }

  @Override
  public boolean isLessOrEqual(CIString pOther) {

    if (pOther.isBottom()) {
      return false;
    }

    if(isEmpty()) {
      return true;
    }
    return this.getCertainly().containsAll(pOther.getCertainly().asSet())
        && pOther.getMaybe().containsAll(this.getMaybe().asSet());

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
