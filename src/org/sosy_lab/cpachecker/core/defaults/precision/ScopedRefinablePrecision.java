// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.defaults.precision;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.types.Type;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

public class ScopedRefinablePrecision extends RefinablePrecision {

  private static final long serialVersionUID = 1L;

  /**
   * the collection that determines which variables are tracked within a specific scope
   */
  private final ImmutableSortedSet<MemoryLocation> rawPrecision;

  ScopedRefinablePrecision(VariableTrackingPrecision pBaseline) {
    super(pBaseline);
    rawPrecision = ImmutableSortedSet.of();
  }

  private ScopedRefinablePrecision(
      VariableTrackingPrecision pBaseline, ImmutableSortedSet<MemoryLocation> pRawPrecision) {
    super(pBaseline);
    rawPrecision = pRawPrecision;
  }

  @Override
  public ScopedRefinablePrecision withIncrement(Multimap<CFANode, MemoryLocation> increment) {
    if (this.rawPrecision.containsAll(increment.values())) {
      return this;
    } else {
      SortedSet<MemoryLocation> refinedPrec = new TreeSet<>(rawPrecision);
      refinedPrec.addAll(increment.values());

      return new ScopedRefinablePrecision(super.getBaseline(), ImmutableSortedSet.copyOf(refinedPrec));
    }
  }

  @Override
  public void serialize(Writer writer) throws IOException {

    List<String> globals = new ArrayList<>();
    String previousScope = null;

    for (MemoryLocation variable : rawPrecision) {
      if (variable.isOnFunctionStack()) {
        String functionName = variable.getFunctionName();
        if (!functionName.equals(previousScope)) {
          writer.write("\n" + functionName + ":\n");
        }
        writer.write(variable.serialize() + "\n");

        previousScope = functionName;
      } else {
        globals.add(variable.serialize());
      }
    }

    if (previousScope != null) {
      writer.write("\n");
    }

    writer.write("*:\n" + Joiner.on("\n").join(globals));
  }

  @Override
  public VariableTrackingPrecision join(VariableTrackingPrecision consolidatedPrecision) {
    Preconditions.checkArgument(getClass().equals(consolidatedPrecision.getClass()));
    checkArgument(
        super.getBaseline().equals(((ScopedRefinablePrecision) consolidatedPrecision).getBaseline()));

    SortedSet<MemoryLocation> joinedPrec = new TreeSet<>(rawPrecision);
    joinedPrec.addAll(((ScopedRefinablePrecision) consolidatedPrecision).rawPrecision);
    return new ScopedRefinablePrecision(super.getBaseline(), ImmutableSortedSet.copyOf(joinedPrec));
  }

  @Override
  public int getSize() {
    return rawPrecision.size();
  }

  @Override
  public String toString() {
    return rawPrecision.toString();
  }

  @Override
  public boolean isEmpty() {
    return rawPrecision.isEmpty();
  }

  @Override
  public boolean isTracking(MemoryLocation pVariable, Type pType, CFANode pLocation) {
    return super.isTracking(pVariable, pType, pLocation) && rawPrecision.contains(pVariable);
  }

  @Override
  public boolean tracksTheSameVariablesAs(VariableTrackingPrecision pOtherPrecision) {
    if (pOtherPrecision.getClass().equals(getClass())
        && super.getBaseline().equals(((ScopedRefinablePrecision) pOtherPrecision).getBaseline())
        && rawPrecision.equals(((ScopedRefinablePrecision) pOtherPrecision).rawPrecision)) {
      return true;
    }
    return false;
  }

  @Override
  public boolean equals(Object other) {
    return super.equals(other)
        && other instanceof ScopedRefinablePrecision
        && rawPrecision.equals(((ScopedRefinablePrecision) other).rawPrecision);
  }

  @Override
  public int hashCode() {
    return super.hashCode() * 31 + rawPrecision.hashCode();
  }
}
