// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg.graphs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

public class SMGChangeTracker {
  private final int id;
  private SMGChangeTracker parent;
  private static SMGChangeTracker ROOT;

  public SMGChangeTracker(SMGChangeTracker pParent, int pFreshId) {
    parent = pParent;
    id = pFreshId;
  }

  public static SMGChangeTracker createRoot(int pFreshId) {
    assert (ROOT == null);
    ROOT = new SMGChangeTracker(null, pFreshId);
    ROOT.parent = ROOT;
    return ROOT;
  }

  public SMGChangeTracker copy(int pFreshId) {
    return new SMGChangeTracker(this, pFreshId);
  }

  public SMGChangeTracker getParent() {
    return parent;
  }

  public int getId() {
    return id;
  }

  public ImmutableList<SMGChangeTracker> getChangePathTo(SMGChangeTracker pEnd) {
    Builder<SMGChangeTracker> firstPart = new Builder<>();
    Builder<SMGChangeTracker> lastPart = new Builder<>();
    SMGChangeTracker beginChangeTracker = this;
    SMGChangeTracker endChangeTracker = pEnd;

    while (beginChangeTracker.getId() != endChangeTracker.getId()) {
      SMGChangeTracker beginParent = beginChangeTracker.getParent();
      SMGChangeTracker endParent = endChangeTracker.getParent();
      if (beginParent.getId() > endChangeTracker.getId()) {
        firstPart.add(beginChangeTracker);
        beginChangeTracker = beginParent;
      } else if (endParent.getId() > beginChangeTracker.getId()) {
        lastPart.add(endChangeTracker);
        endChangeTracker = endParent;
      } else if (beginChangeTracker.getId() > endChangeTracker.getId()) {
        firstPart.add(beginChangeTracker);
        beginChangeTracker = beginParent;
      } else if (endChangeTracker.getId() > beginChangeTracker.getId()) {
        lastPart.add(endChangeTracker);
        endChangeTracker = endParent;
      }
    }
    firstPart.add(beginChangeTracker);
    return firstPart.addAll(lastPart.build().reverse()).build();
  }
}
