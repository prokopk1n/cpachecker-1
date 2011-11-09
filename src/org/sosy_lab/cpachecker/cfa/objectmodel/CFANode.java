/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa.objectmodel;

import static com.google.common.base.Preconditions.*;

import java.util.ArrayList;
import java.util.List;

import org.sosy_lab.cpachecker.cfa.objectmodel.c.CallToReturnEdge;

public class CFANode implements Comparable<CFANode> {

  private static int nextNodeNumber = 0;

  private final int nodeNumber;
  private final int lineNumber;

  private final List<CFAEdge> leavingEdges = new ArrayList<CFAEdge>();
  private final List<CFAEdge> enteringEdges = new ArrayList<CFAEdge>();

  // is start node of a loop?
  private boolean isLoopStart = false;

  // in which function is that node?
  private final String functionName;

  // list of summary edges
  private CallToReturnEdge leavingSummaryEdge = null;
  private CallToReturnEdge enteringSummaryEdge = null;

  // topological sort id, smaller if it appears later in sorting
  private int topologicalSortId = 0;

  public CFANode(int pLineNumber, String pFunctionName) {
    assert !pFunctionName.isEmpty();

    lineNumber = pLineNumber;
    functionName = pFunctionName;
    nodeNumber = nextNodeNumber++;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  public int getNodeNumber() {
    return nodeNumber;
  }

  public int getTopologicalSortId() {
    return topologicalSortId;
  }

  public void setTopologicalSortId(int pId) {
    topologicalSortId = pId;
  }

  public void addLeavingEdge(CFAEdge pNewLeavingEdge) {
    checkArgument(pNewLeavingEdge.getPredecessor() == this,
        "Cannot add edges to another node");
    leavingEdges.add(pNewLeavingEdge);
  }

  public void removeLeavingEdge(CFAEdge pEdge) {
    boolean removed = leavingEdges.remove(pEdge);
    checkArgument(removed, "Cannot remove non-existing leaving edge");
  }

  public int getNumLeavingEdges() {
    return leavingEdges.size();
  }

  public CFAEdge getLeavingEdge(int pIndex) {
    return leavingEdges.get(pIndex);
  }

  public void addEnteringEdge(CFAEdge pEnteringEdge) {
    checkArgument(pEnteringEdge.getSuccessor() == this,
        "Cannot add edges to another node");
    enteringEdges.add(pEnteringEdge);
  }

  public void removeEnteringEdge(CFAEdge pEdge) {
    boolean removed = enteringEdges.remove(pEdge);
    checkArgument(removed, "Cannot remove non-existing entering edge");
  }

  public int getNumEnteringEdges() {
    return enteringEdges.size();
  }

  public CFAEdge getEnteringEdge(int pIndex) {
    return enteringEdges.get(pIndex);
  }

  public CFAEdge getEdgeTo(CFANode pOther) {
    for (CFAEdge edge : leavingEdges) {
      if (edge.getSuccessor() == pOther) {
        return edge;
      }
    }

    throw new IllegalArgumentException();
  }

  public boolean hasEdgeTo(CFANode pOther) {
    boolean hasEdge = false;
    for (CFAEdge edge : leavingEdges) {
      if (edge.getSuccessor() == pOther) {
        hasEdge = true;
        break;
      }
    }

    return hasEdge;
  }

  public void setLoopStart() {
    isLoopStart = true;
  }

  public boolean isLoopStart() {
    return isLoopStart;
  }

  public String getFunctionName() {
    return functionName;
  }

  public void addEnteringSummaryEdge(CallToReturnEdge pEdge) {
    checkState(leavingSummaryEdge == null,
        "Cannot add two entering summary edges");
    enteringSummaryEdge = pEdge;
  }

  public void addLeavingSummaryEdge(CallToReturnEdge pEdge) {
    checkState(leavingSummaryEdge == null,
        "Cannot add two leaving summary edges");
    leavingSummaryEdge = pEdge;
  }

  public CallToReturnEdge getEnteringSummaryEdge() {
    return enteringSummaryEdge;
  }

  public CallToReturnEdge getLeavingSummaryEdge() {
    return leavingSummaryEdge;
  }

  public void removeEnteringSummaryEdge(CallToReturnEdge pEdge) {
    checkArgument(enteringSummaryEdge == pEdge,
        "Cannot remove non-existing entering summary edge");
    enteringSummaryEdge = null;
  }

  public void removeLeavingSummaryEdge(CallToReturnEdge pEdge) {
    checkArgument(leavingSummaryEdge == pEdge,
        "Cannot remove non-existing leaving summary edge");
    leavingSummaryEdge = null;
  }

  @Override
  public boolean equals(Object pOther) {
    if (pOther == this) {
      return true;
    }

    if (pOther == null || !(pOther instanceof CFANode)) {
      return false;
    }

    return getNodeNumber() == ((CFANode) pOther).getNodeNumber();
  }

  @Override
  public int hashCode() {
    return getNodeNumber();
  }

  @Override
  public String toString() {
    return "N" + getNodeNumber();
  }

  @Override
  public int compareTo(CFANode pOther) {
    return getNodeNumber() - pOther.getNodeNumber();
  }
}
