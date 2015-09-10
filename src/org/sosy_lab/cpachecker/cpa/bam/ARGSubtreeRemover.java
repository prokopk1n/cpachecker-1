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
package org.sosy_lab.cpachecker.cpa.bam;

import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.Precisions;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

public class ARGSubtreeRemover {

  private final BlockPartitioning partitioning;
  private final BAMDataManager data;
  private final Reducer wrappedReducer;
  private final BAMCache bamCache;
  private final LogManager logger;
  private final Timer removeCachedSubtreeTimer;

  public ARGSubtreeRemover(BAMCPA bamCpa, Timer pRemoveCachedSubtreeTimer) {
    this.partitioning = bamCpa.getBlockPartitioning();
    this.data = bamCpa.getData();
    this.wrappedReducer = bamCpa.getReducer();
    this.bamCache = bamCpa.getData().bamCache;
    this.logger = bamCpa.getData().logger;
    this.removeCachedSubtreeTimer = pRemoveCachedSubtreeTimer;
  }

  void removeSubtree(ARGReachedSet mainReachedSet, ARGPath pPath,
                     ARGState element, List<Precision> pNewPrecisions,
                     List<Predicate<? super Precision>> pNewPrecisionTypes,
                     Map<ARGState, ARGState> pPathElementToReachedState) {

    final ARGState firstState = (ARGState)mainReachedSet.asReachedSet().getFirstState();
    final ARGState lastState = (ARGState)mainReachedSet.asReachedSet().getLastState();

    assert pPathElementToReachedState.get(pPath.asStatesList().get(0)) == firstState : "path should start with root state";
    assert pPathElementToReachedState.get(Iterables.getLast(pPath.asStatesList())) == lastState : "path should end with target state";
    assert lastState.isTarget();

    final List<ARGState> relevantCallNodes = getRelevantDefinitionNodes(pPath.asStatesList(), element, pPathElementToReachedState);
    assert pPathElementToReachedState.get(relevantCallNodes.get(0)) == firstState : "root should be relevant";
    assert relevantCallNodes.size() >= 1 : "at least the main-function should be open at the target-state";

    Multimap<ARGState, ARGState> neededRemoveCachedSubtreeCalls = LinkedHashMultimap.create();

    //iterate from root to element and remove all subtrees for subgraph calls
    for (int i = 0; i < relevantCallNodes.size() - 1; i++) { // ignore root and the last element
      final ARGState pathElement = relevantCallNodes.get(i);
      final ARGState nextElement = relevantCallNodes.get(i+1);
      neededRemoveCachedSubtreeCalls.put(
              getReachedState(pPathElementToReachedState, pathElement),
              getReachedState(pPathElementToReachedState, nextElement));
    }

    if (bamCache.doesAggressiveCaching()) {
      ensureExactCacheHitsOnPath(mainReachedSet, pPath, element, pNewPrecisions, pPathElementToReachedState,
              neededRemoveCachedSubtreeCalls);
    }

    final ARGState lastRelevantNode = getReachedState(pPathElementToReachedState, Iterables.getLast(relevantCallNodes));
    final ARGState target = getReachedState(pPathElementToReachedState, element);
    for (final Entry<ARGState, ARGState> removeCachedSubtreeArguments : neededRemoveCachedSubtreeCalls.entries()) {
      final List<Precision> newPrecisions;
      if (removeCachedSubtreeArguments.getValue() == lastRelevantNode) { // last iteration
        newPrecisions = pNewPrecisions;
      } else {
        ReachedSet nextReachedSet = data.initialStateToReachedSet.get(removeCachedSubtreeArguments.getValue());
        // assert nextReachedSet != null : "call-state does not match reachedset";
        if (nextReachedSet != null && target.getParents().contains(nextReachedSet.getFirstState())) {
          newPrecisions = pNewPrecisions;
        } else {
          newPrecisions = null; // ignore newPrecisions for all iterations except the last one
        }
      }
      removeCachedSubtree(removeCachedSubtreeArguments.getKey(), removeCachedSubtreeArguments.getValue(), newPrecisions, pNewPrecisionTypes);
    }

    removeCachedSubtree(getReachedState(pPathElementToReachedState, Iterables.getLast(relevantCallNodes)),
            getReachedState(pPathElementToReachedState, element), pNewPrecisions, pNewPrecisionTypes);

    // the main-reachedset contains only the root, exit-states and targets.
    // we assume, that the current refinement was caused by a target-state.
    mainReachedSet.removeSubtree(lastState);
  }

  private ARGState getReachedState(Map<ARGState, ARGState> pathElementToReachedState, ARGState state) {
    return getMostInnerState(pathElementToReachedState.get(state));
  }

  private ARGState getMostInnerState(ARGState state) {
    while (data.expandedStateToReducedState.containsKey(state)) {
      state = (ARGState) data.expandedStateToReducedState.get(state);
    }
    return state;
  }

  /**
   * @return <code>true</code>, if the precision of the first element of the given reachedSet changed by this operation; <code>false</code>, otherwise.
   */
  private static boolean removeSubtree(ReachedSet reachedSet, ARGState argElement,
                                       List<Precision> newPrecisions, List<Predicate<? super Precision>> pPrecisionTypes) {
    ARGReachedSet argReachSet = new ARGReachedSet(reachedSet);
    boolean updateCacheNeeded = argElement.getParents().contains(reachedSet.getFirstState());
    removeSubtree(argReachSet, argElement, newPrecisions, pPrecisionTypes);
    return updateCacheNeeded;
  }

  static void removeSubtree(ARGReachedSet reachedSet, ARGState argElement) {
    if (BAMTransferRelation.isHeadOfMainFunction(extractLocation(argElement))) {
      reachedSet.removeSubtree((ARGState)reachedSet.asReachedSet().getLastState());
    } else {
      reachedSet.removeSubtree(argElement);
    }
  }

  private static void removeSubtree(ARGReachedSet reachedSet, ARGState argElement,
                                    List<Precision> newPrecisions, List<Predicate<? super Precision>> pPrecisionTypes) {
    if (newPrecisions == null || newPrecisions.size() == 0) {
      removeSubtree(reachedSet, argElement);
    } else {
      reachedSet.removeSubtree(argElement, newPrecisions, pPrecisionTypes);
    }
  }

  private void removeCachedSubtree(ARGState rootState, ARGState removeElement,
                                   List<Precision> pNewPrecisions,
                                   List<Predicate<? super Precision>> pPrecisionTypes) {
    removeCachedSubtreeTimer.start();

    try {
      CFANode rootNode = extractLocation(rootState);
      Block rootSubtree = partitioning.getBlockForCallNode(rootNode);

      logger.log(Level.FINER, "Remove cached subtree for", removeElement,
              "(rootNode: ", rootNode, ") issued with precision", pNewPrecisions);

      AbstractState reducedRootState = wrappedReducer.getVariableReducedState(rootState, rootSubtree, rootNode);
      ReachedSet reachedSet = data.initialStateToReachedSet.get(rootState);

      if (removeElement.isDestroyed()) {
        logger.log(Level.FINER, "state was destroyed before");
        //apparently, removeElement was removed due to prior deletions
        return;
      }

      assert reachedSet.contains(removeElement) : "removing state from wrong reachedSet: " + removeElement;

      Precision removePrecision = reachedSet.getPrecision(removeElement);
      ArrayList<Precision> newReducedRemovePrecision = null; // TODO newReducedRemovePrecision: NullPointerException 20 lines later!

      if (pNewPrecisions != null) {
        newReducedRemovePrecision = new ArrayList<>(1);

        for (int i = 0; i < pNewPrecisions.size(); i++) {
          removePrecision = Precisions.replaceByType(removePrecision, pNewPrecisions.get(i), pPrecisionTypes.get(i));
        }

        newReducedRemovePrecision.add(wrappedReducer.getVariableReducedPrecision(removePrecision, rootSubtree));
        pPrecisionTypes = new ArrayList<>();
        pPrecisionTypes.add(Predicates.instanceOf(newReducedRemovePrecision.get(0).getClass()));
      }

      assert !removeElement.getParents().isEmpty();

      Precision reducedRootPrecision = reachedSet.getPrecision(reachedSet.getFirstState());
      bamCache.removeReturnEntry(reducedRootState, reducedRootPrecision, rootSubtree);
      bamCache.removeBlockEntry(reducedRootState, reducedRootPrecision, rootSubtree);

      logger.log(Level.FINEST, "Removing subtree, adding a new cached entry, and removing the former cached entries");

      if (removeSubtree(reachedSet, removeElement, newReducedRemovePrecision, pPrecisionTypes)) {
        logger.log(Level.FINER, "updating cache");
        bamCache.updatePrecisionForEntry(reducedRootState, reducedRootPrecision, rootSubtree, newReducedRemovePrecision.get(0));
      }

    } finally {
      removeCachedSubtreeTimer.stop();
    }
  }

  /** returns only those states, where a block starts that is 'open' at the cutState. */
  private List<ARGState> getRelevantDefinitionNodes(List<ARGState> path, ARGState bamCutState, Map<ARGState, ARGState> pathElementToReachedState) {
    final Deque<ARGState> openCallStates = new ArrayDeque<>();
    for (final ARGState bamState : path) {

      final ARGState state = pathElementToReachedState.get(bamState);

      // ASSUMPTION: there can be several block-exits at once per location, but only one block-entry per location.

      // we use a loop here, because a return-node can be the exit of several blocks at once.
      ARGState tmp = state;
      while (data.expandedStateToReducedState.containsKey(tmp) && bamCutState != bamState) {
        assert partitioning.isReturnNode(extractLocation(tmp)) : "the mapping of expanded to reduced state should only exist for block-return-locations";
        // we are leaving a block, remove the start-state from the stack.
        tmp = (ARGState) data.expandedStateToReducedState.get(tmp);
        openCallStates.removeLast();
        // INFO:
        // if we leave several blocks at once, we leave the blocks in reverse order,
        // because the call-state of the most outer block is popped first.
        // We ignore this here, because we just need the 'number' of block-exits.
      }

      if (data.initialStateToReachedSet.containsKey(state)) {
        assert partitioning.isCallNode(extractLocation(state)) : "the mapping of initial state to reached-set should only exist for block-start-locations";
        // we start a new sub-reached-set, add state as start-state of a (possibly) open block.
        // if we are at lastState, we do not want to enter the block
        openCallStates.addLast(bamState);
      }

      if (bamCutState == bamState) {
        // TODO:
        // current solution: when we found the cutState, we only enter new blocks, but never leave one.
        // maybe better solution: do not enter or leave a block, when we found the cutState.
        break;
      }
    }

    return new ArrayList<>(openCallStates);
  }

  private void ensureExactCacheHitsOnPath(ARGReachedSet mainReachedSet, ARGPath pPath, final ARGState pElement,
                                          List<Precision> pNewPrecisions, Map<ARGState, ARGState> pPathElementToReachedState,
                                          Multimap<ARGState, ARGState> neededRemoveCachedSubtreeCalls) {
    Map<ARGState, UnmodifiableReachedSet> pathElementToOuterReachedSet = new HashMap<>();
    Pair<Set<ARGState>, Set<ARGState>> pair =
            getCallAndReturnNodes(pPath, pathElementToOuterReachedSet, mainReachedSet.asReachedSet(),
                    pPathElementToReachedState);
    Set<ARGState> callNodes = pair.getFirst();
    Set<ARGState> returnNodes = pair.getSecond();

    Deque<ARGState> remainingPathElements = new LinkedList<>(pPath.asStatesList());

    // we pop states until the cutState has been found
    // this code is ugly, we should improve it!
    while (!remainingPathElements.peek().equals(pElement)) {
      remainingPathElements.pop();
    }
    assert remainingPathElements.peek() == pElement;

    while (!remainingPathElements.isEmpty()) {
      ARGState currentElement = remainingPathElements.pop();
        if (callNodes.contains(currentElement)) {
          ARGState currentReachedState = getReachedState(pPathElementToReachedState, currentElement);
          CFANode node = extractLocation(currentReachedState);
          Block currentBlock = partitioning.getBlockForCallNode(node);
          AbstractState reducedState = wrappedReducer.getVariableReducedState(currentReachedState, currentBlock, node);

          removeUnpreciseCacheEntriesOnPath(currentElement, reducedState, pNewPrecisions, currentBlock,
                  remainingPathElements, pPathElementToReachedState, callNodes, returnNodes, pathElementToOuterReachedSet,
                  neededRemoveCachedSubtreeCalls);
        }
    }
  }

  private Pair<Set<ARGState>, Set<ARGState>> getCallAndReturnNodes(ARGPath path,
                                                                   Map<ARGState, UnmodifiableReachedSet> pathElementToOuterReachedSet, UnmodifiableReachedSet mainReachedSet,
                                                                   Map<ARGState, ARGState> pPathElementToReachedState) {
    Set<ARGState> callNodes = new HashSet<>();
    Set<ARGState> returnNodes = new HashSet<>();

    Deque<Block> openSubtrees = new ArrayDeque<>();

    Deque<UnmodifiableReachedSet> openReachedSets = new ArrayDeque<>();
    openReachedSets.push(mainReachedSet);

    for (ARGState pathState : path.asStatesList()) {
      CFANode node = extractLocation(pathState);

      // we use a loop here, because a return-node can be the exit of several blocks at once.
      // we have to handle returnNodes before entryNodes, because some nodes can be both,
      // and the transferRelation also handles entryNodes as first case.
      while (!openSubtrees.isEmpty() && openSubtrees.peek().isReturnNode(node)) {
        openSubtrees.pop();
        openReachedSets.pop();
        returnNodes.add(pathState);
      }

      // this line comes after handling returnStates --> returnStates from path are part of the outer-block-reachedSet
      pathElementToOuterReachedSet.put(pathState, openReachedSets.peek());

      if (partitioning.isCallNode(node)
              && !partitioning.getBlockForCallNode(node).equals(openSubtrees.peek())) {
        // the block can be equal, if this is a loop-block.
          openSubtrees.push(partitioning.getBlockForCallNode(node));
          openReachedSets.push(data.initialStateToReachedSet.get(getReachedState(pPathElementToReachedState, pathState)));
          callNodes.add(pathState);
      }
    }

    return Pair.of(callNodes, returnNodes);
  }

  private boolean removeUnpreciseCacheEntriesOnPath(ARGState rootState, AbstractState reducedRootState,
                                                    List<Precision> pNewPrecisions, Block rootBlock, Deque<ARGState> remainingPathElements,
                                                    Map<ARGState, ARGState> pPathElementToReachedState, Set<ARGState> callNodes, Set<ARGState> returnNodes,
                                                    Map<ARGState, UnmodifiableReachedSet> pathElementToOuterReachedSet,
                                                    Multimap<ARGState, ARGState> neededRemoveCachedSubtreeCalls) {
    UnmodifiableReachedSet outerReachedSet = pathElementToOuterReachedSet.get(rootState);

    Precision rootPrecision = outerReachedSet.getPrecision(getReachedState(pPathElementToReachedState, rootState));

    for (Precision pNewPrecision : pNewPrecisions) {
      rootPrecision = Precisions.replaceByType(rootPrecision, pNewPrecision, Predicates.instanceOf(pNewPrecision.getClass()));
    }
    Precision reducedNewPrecision =
            wrappedReducer.getVariableReducedPrecision(
                    rootPrecision, rootBlock);

    UnmodifiableReachedSet innerReachedSet = data.initialStateToReachedSet.get(getReachedState(pPathElementToReachedState, rootState));
    Precision usedPrecision = innerReachedSet.getPrecision(innerReachedSet.getFirstState());

    //add precise key for new precision if needed
    if (!bamCache.containsPreciseKey(reducedRootState, reducedNewPrecision, rootBlock)) {
      ReachedSet reachedSet = data.createInitialReachedSet(reducedRootState, reducedNewPrecision);
      bamCache.put(reducedRootState, reducedNewPrecision, rootBlock, reachedSet);
    }

    boolean isNewPrecisionEntry = usedPrecision.equals(reducedNewPrecision);

    //fine, this block will not lead to any problems anymore, but maybe inner blocks will?
    //-> check other (inner) blocks on path
    boolean foundInnerUnpreciseEntries = false;
    while (!remainingPathElements.isEmpty()) {
      ARGState currentElement = remainingPathElements.pop();

      if (callNodes.contains(currentElement)) {
        ARGState currentReachedState = getReachedState(pPathElementToReachedState, currentElement);
        CFANode node = extractLocation(currentReachedState);
        Block currentBlock = partitioning.getBlockForCallNode(node);
        AbstractState reducedState = wrappedReducer.getVariableReducedState(currentReachedState, currentBlock, node);

        boolean removedUnpreciseInnerBlock =
                removeUnpreciseCacheEntriesOnPath(currentElement, reducedState, pNewPrecisions, currentBlock,
                        remainingPathElements, pPathElementToReachedState, callNodes, returnNodes,
                        pathElementToOuterReachedSet, neededRemoveCachedSubtreeCalls);
        if (removedUnpreciseInnerBlock) {
          //ok we indeed found an inner block that was unprecise
          if (isNewPrecisionEntry && !foundInnerUnpreciseEntries) {
            //if we are in a reached set that already uses the new precision and this is the first such entry we have to remove the subtree starting from currentElement in the rootReachedSet
            neededRemoveCachedSubtreeCalls.put(getReachedState(pPathElementToReachedState, rootState), currentReachedState);
            foundInnerUnpreciseEntries = true;
          }
        }
      }

      if (returnNodes.contains(currentElement)) {
        //our block ended. Leave..
        return foundInnerUnpreciseEntries || !isNewPrecisionEntry;
      }
    }

    return foundInnerUnpreciseEntries || !isNewPrecisionEntry;
  }
}
