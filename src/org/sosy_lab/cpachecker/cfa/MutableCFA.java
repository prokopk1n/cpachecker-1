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
package org.sosy_lab.cpachecker.cfa;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.util.CFAUtils.Loop;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.SortedSetMultimap;

class MutableCFA implements CFA {

  private final Map<String, CFAFunctionDefinitionNode> functions;
  private final SortedSetMultimap<String, CFANode> allNodes;
  private final CFAFunctionDefinitionNode mainFunction;

  public MutableCFA(
      Map<String, CFAFunctionDefinitionNode> pFunctions,
      SortedSetMultimap<String, CFANode> pAllNodes,
      CFAFunctionDefinitionNode pMainFunction) {

    functions = pFunctions;
    allNodes = pAllNodes;
    mainFunction = pMainFunction;

    assert functions.keySet().equals(allNodes.keySet());
    assert functions.get(mainFunction.getFunctionName()) == mainFunction;
  }

  @Override
  public boolean isEmpty() {
    return functions.isEmpty();
  }

  @Override
  public Set<String> getAllFunctionNames() {
    return Collections.unmodifiableSet(functions.keySet());
  }

  @Override
  public CFAFunctionDefinitionNode getFunction(String pName) {
    return functions.get(pName);
  }

  @Override
  public Map<String, CFAFunctionDefinitionNode> getAllFunctions() {
    return Collections.unmodifiableMap(functions);
  }

  public SortedSet<CFANode> getFunctionNodes(String pName) {
    return Collections.unmodifiableSortedSet(allNodes.get(pName));
  }

  @Override
  public CFAFunctionDefinitionNode getMainFunction() {
    return mainFunction;
  }

  @Override
  public Optional<ImmutableMultimap<String, Loop>> getLoopStructure() {
    return Optional.absent();
  }

  public ImmutableCFA makeImmutableCFA(Optional<ImmutableMultimap<String, Loop>> pLoopStructure) {
    return new ImmutableCFA(functions, allNodes, mainFunction, pLoopStructure);
  }
}