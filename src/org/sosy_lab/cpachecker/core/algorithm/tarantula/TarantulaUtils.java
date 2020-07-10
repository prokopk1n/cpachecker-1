// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0
package org.sosy_lab.cpachecker.core.algorithm.tarantula;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.util.AbstractStates;

public class TarantulaUtils {

  /** Helper class for TarantulaAlgorithm related utility methods. */
  private TarantulaUtils() {}

  /**
   * Gets all possible paths from the possible leaves (whether targetStates or safeStates)to the
   * root.
   *
   * @param reachedSet input.
   * @param chosenState whether targetStates or safeStates.
   * @return All possible paths from root to the chosen state.
   */
  public static Set<ARGPath> getAllPaths(ReachedSet reachedSet, ARGState chosenState) {
    ARGState root = AbstractStates.extractStateByType(reachedSet.getFirstState(), ARGState.class);
    List<ARGState> states = new ArrayList<>();
    Set<ARGPath> results = new HashSet<>();
    List<List<ARGState>> paths = new ArrayList<>();

    states.add(chosenState);
    paths.add(states);

    // This is assuming from each node there is a way to go to the start
    // Go on until all the paths got the start
    while (!paths.isEmpty()) {
      // Expand the last path

      List<ARGState> curPath = paths.remove(paths.size() - 1);
      Preconditions.checkNotNull(curPath);
      // If there is no more to expand - add this path and continue
      if (curPath.get(curPath.size() - 1) == root) {
        results.add(new ARGPath(Lists.reverse(curPath)));

        continue;
      }

      // Add all parents
      for (ARGState parentElement : curPath.get(curPath.size() - 1).getParents()) {
        ImmutableList.Builder<ARGState> tmp =
            ImmutableList.builderWithExpectedSize(curPath.size() + 1);
        tmp.addAll(curPath);

        tmp.add(parentElement);
        paths.add(tmp.build());
      }
    }
    return results;
  }
}
