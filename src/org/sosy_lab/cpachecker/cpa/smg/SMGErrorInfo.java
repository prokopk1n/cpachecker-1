// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentLinkedList;
import org.sosy_lab.common.collect.PersistentList;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.cpachecker.cpa.smg.SMGState.Property;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;

/**
 * Simple flags are not enough, this class contains more about the nature of the error.
 *
 * <p>This class is immutable.
 */
class SMGErrorInfo {

  private final boolean invalidWrite;
  private final boolean invalidRead;
  private final boolean invalidFree;
  private final boolean hasMemoryLeak;
  private final String errorDescription;
  private final PersistentList<Object> invalidChain;
  private final PersistentList<Object> currentChain;
  private final PersistentMap<String, SMGValue> readValues;

  private SMGErrorInfo(
      boolean pInvalidWrite,
      boolean pInvalidRead,
      boolean pInvalidFree,
      boolean pHasMemoryLeak,
      String pErrorDescription,
      PersistentList<Object> pInvalidChain,
      PersistentList<Object> pCurrentChain,
      PersistentMap<String, SMGValue> pReadValues) {
    invalidWrite = pInvalidWrite;
    invalidRead = pInvalidRead;
    invalidFree = pInvalidFree;
    hasMemoryLeak = pHasMemoryLeak;
    errorDescription = pErrorDescription;
    invalidChain = pInvalidChain;
    currentChain = pCurrentChain;
    readValues = pReadValues;
  }

  static SMGErrorInfo of() {
    return new SMGErrorInfo(
        false,
        false,
        false,
        false,
        "",
        PersistentLinkedList.of(),
        PersistentLinkedList.of(),
        PathCopyingPersistentTreeMap.of());
  }

  SMGErrorInfo withErrorMessage(String pErrorDescription) {
    return new SMGErrorInfo(
        invalidWrite,
        invalidRead,
        invalidFree,
        hasMemoryLeak,
        pErrorDescription,
        invalidChain,
        currentChain,
        readValues);
  }

  SMGErrorInfo withProperty(Property pProperty) {
    boolean pInvalidWrite = invalidWrite;
    boolean pInvalidRead = invalidRead;
    boolean pInvalidFree = invalidFree;
    boolean pHasLeaks = hasMemoryLeak;

    switch (pProperty) {
      case INVALID_WRITE:
        pInvalidWrite = true;
        break;
      case INVALID_READ:
        pInvalidRead = true;
        break;
      case INVALID_FREE:
        pInvalidFree = true;
        break;
      case INVALID_HEAP:
        pHasLeaks = true;
        break;
      default:
        throw new AssertionError();
    }

    return new SMGErrorInfo(
        pInvalidWrite,
        pInvalidRead,
        pInvalidFree,
        pHasLeaks,
        errorDescription,
        invalidChain,
        currentChain,
        readValues);
  }

  boolean hasMemoryErrors() {
    return invalidWrite || invalidRead || invalidFree || hasMemoryLeak;
  }

  SMGErrorInfo mergeWith(SMGErrorInfo pOther) {
    return new SMGErrorInfo(
        invalidWrite || pOther.invalidWrite,
        invalidRead || pOther.invalidRead,
        invalidFree || pOther.invalidFree,
        hasMemoryLeak || pOther.hasMemoryLeak,
        errorDescription,
        invalidChain,
        currentChain,
        readValues);
  }

  SMGErrorInfo withClearChain() {
    return new SMGErrorInfo(
        invalidWrite,
        invalidRead,
        invalidFree,
        hasMemoryLeak,
        errorDescription,
        PersistentLinkedList.of(),
        PersistentLinkedList.of(),
        PathCopyingPersistentTreeMap.of());
  }

  SMGErrorInfo moveCurrentChainToInvalidChain() {
    return new SMGErrorInfo(
        invalidWrite,
        invalidRead,
        invalidFree,
        hasMemoryLeak,
        errorDescription,
        invalidChain.withAll(currentChain),
        currentChain,
        readValues);
  }

  public SMGErrorInfo withObject(Object o) {
    return new SMGErrorInfo(
        invalidWrite,
        invalidRead,
        invalidFree,
        hasMemoryLeak,
        errorDescription,
        invalidChain,
        currentChain.with(o),
        readValues);
  }

  SMGErrorInfo withInvalidObject(SMGObject pSmgObject) {
    return withInvalidObjects(Collections.singleton(pSmgObject));
  }

  public SMGErrorInfo withInvalidObjects(Collection<?> pObjects) {
    return new SMGErrorInfo(
        invalidWrite,
        invalidRead,
        invalidFree,
        hasMemoryLeak,
        errorDescription,
        invalidChain.withAll(new ArrayList<>(pObjects)),
        currentChain,
        readValues);
  }

  public SMGErrorInfo addReadVariable(String pName, SMGValue pObject) {
    return new SMGErrorInfo(
        invalidWrite,
        invalidRead,
        invalidFree,
        hasMemoryLeak,
        errorDescription,
        invalidChain,
        currentChain,
        readValues.putAndCopy(pName, pObject));
  }

  public boolean isPresentVariable(String pName, SMGValue pValue, SMGState pState) {
    SMGObject variableObject = pState.getHeap().getObjectForVisibleVariable(pName);
    if (variableObject != null) {
      try {
        SMGValue result = pState.readValue(variableObject, 0, variableObject.getSize()).getObject();
        return result.equals(pValue);
      } catch (SMGInconsistentException pE) {
        pE.printStackTrace();
        return false;
      }
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        invalidWrite,
        invalidRead,
        invalidFree,
        hasMemoryLeak,
        invalidChain,
        currentChain,
        readValues);
  }

  @Override
  public boolean equals(Object pOther) {
    if (!(pOther instanceof SMGErrorInfo)) {
      return false;
    }
    SMGErrorInfo o = (SMGErrorInfo) pOther;
    return invalidWrite == o.invalidWrite
        && invalidRead == o.invalidRead
        && invalidFree == o.invalidFree
        && hasMemoryLeak == o.hasMemoryLeak
        && invalidChain.equals(o.invalidChain)
        && currentChain.equals(o.currentChain);
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder("ErrorInfo {");
    str.append(errorDescription.isEmpty() ? "<>" : errorDescription).append(", ");
    if (invalidWrite) {
      str.append("invalid write").append(", ");
    }
    if (invalidRead) {
      str.append("invalid read").append(", ");
    }
    if (isInvalidFree()) {
      str.append("invalid free").append(", ");
    }
    if (hasMemoryLeak()) {
      str.append("has memory leak").append(", ");
    }
    return str.append("}").toString();
  }

  String getErrorDescription() {
    return errorDescription;
  }

  boolean isInvalidWrite() {
    return invalidWrite;
  }

  boolean isInvalidRead() {
    return invalidRead;
  }

  boolean isInvalidFree() {
    return invalidFree;
  }

  boolean hasMemoryLeak() {
    return hasMemoryLeak;
  }

  List<Object> getCurrentChain() {
    return currentChain;
  }

  List<Object> getInvalidChain() {
    return invalidChain;
  }

  public PersistentMap<String, SMGValue> getReadValues() {
    return readValues;
  }
}
