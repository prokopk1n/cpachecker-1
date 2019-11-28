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
import java.util.StringJoiner;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.cpa.string.util.CIString;
import org.sosy_lab.cpachecker.cpa.string.util.ExplicitCIString;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class CIStringState
    implements Serializable, LatticeAbstractState<CIStringState> {
  private static final long serialVersionUID = 1L;

  private final PersistentMap<String, CIString> ciDomains;

  public CIStringState() {
    ciDomains = PathCopyingPersistentTreeMap.of();
  }

  public CIStringState(PersistentMap<String, CIString> pCiDomains) {
    this.ciDomains = pCiDomains;
  }

  public CIString getCIString(String stringName) {
    return ciDomains.getOrDefault(stringName, ExplicitCIString.EMPTY);
  }

  public boolean contains(String stringName) {
    return ciDomains.containsKey(stringName);
  }

  public CIStringState addCIString(String stringName, CIString ciString) {

    if (ciString.isBottom()) {
      return removeCIString(stringName);
    }
    if (!ciDomains.containsKey(stringName)) {
      return new CIStringState(ciDomains.putAndCopy(stringName, ciString));
    }
    if (!ciDomains.get(stringName).equals(ciString)) {
      CIString str = ciDomains.get(stringName).join(ciString);
      return new CIStringState(ciDomains.putAndCopy(stringName, str));
    }
    return this;
  }

  public CIStringState removeAndAddCIString(String stringName, CIString ciString) {

    CIStringState newStState = removeCIString(stringName);

    if (!ciString.isBottom()) {
      return new CIStringState(newStState.ciDomains.putAndCopy(stringName, ciString));
    }

    return newStState;
  }

  public CIStringState removeCIString(String stringName) {
    if (ciDomains.containsKey(stringName)) {
      return new CIStringState(ciDomains.removeAndCopy(stringName));
    }
    return this;
  }

  // Join two sets (name, CIString). If exist name from this.keySet() and from pOther.keySet() join
  // their CIStrings
  @Override
  public CIStringState join(CIStringState pOther) throws CPAException, InterruptedException {

    boolean changed = false;
    PersistentMap<String, CIString> newCIDomains = PathCopyingPersistentTreeMap.of();

    for (String stringName : pOther.ciDomains.keySet()) {
      CIString otherCIString = pOther.getCIString(stringName);
      if (ciDomains.containsKey(stringName)) {
        newCIDomains =
            newCIDomains.putAndCopy(stringName, otherCIString.join(this.getCIString(stringName)));
        changed = true;
      } else {
        newCIDomains = newCIDomains.putAndCopy(stringName, otherCIString);
      }
    }

    for (String stringName : ciDomains.keySet()) {
      if (!pOther.ciDomains.containsKey(stringName)) {
        newCIDomains = newCIDomains.putAndCopy(stringName, this.getCIString(stringName));
        changed = true;
      }
    }

    if (changed) {
      return new CIStringState(newCIDomains);
    }
    return pOther;
  }

  // return true, if for any our name: (name, CISrting) exist (name, otherCIString) from pOther AND
  // CIString isLessOrEqual otherString

  @Override
  public boolean isLessOrEqual(CIStringState pOther) throws CPAException, InterruptedException {
    // TODO: is it correct?
    for (String stringName : ciDomains.keySet()) {
      if (!pOther.ciDomains.containsKey(stringName)) {
        return false;
      } else {
        if (!getCIString(stringName).isLessOrEqual(pOther.getCIString(stringName))) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public String toString() {
    StringJoiner str = new StringJoiner(", ");
    for (String stringName : ciDomains.keySet()) {
      str.add("(" + stringName + " = " + getCIString(stringName).toString() + ")");
    }
    return str.toString();
  }
}