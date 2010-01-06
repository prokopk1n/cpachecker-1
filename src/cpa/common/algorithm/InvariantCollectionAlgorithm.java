/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker. 
 *
 *  Copyright (C) 2007-2008  Dirk Beyer and Erkan Keremoglu.
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
 *    http://www.cs.sfu.ca/~dbeyer/CPAchecker/
 */
package cpa.common.algorithm;

import java.util.List;

import cfa.objectmodel.CFAEdge;
import cfa.objectmodel.CFANode;
import symbpredabstraction.interfaces.AbstractFormula;
import symbpredabstraction.interfaces.SymbolicFormula;
import common.Pair;

import cpa.art.ARTElement;
import cpa.common.Path;
import cpa.common.ReachedElements;
import cpa.common.interfaces.AbstractElement;
import cpa.common.interfaces.AbstractElementWithLocation;
import cpa.common.interfaces.AbstractWrapperElement;
import cpa.common.interfaces.CPAWrapper;
import cpa.common.interfaces.ConfigurableProgramAnalysis;
import cpa.common.interfaces.Precision;
import cpa.invariant.dump.DumpInvariantElement;
import cpa.invariant.util.InvariantWithLocation;
import cpa.invariant.util.MathsatInvariantSymbolicFormulaManager;
import cpa.symbpredabsCPA.SymbPredAbsAbstractElement;
import cpa.symbpredabsCPA.SymbPredAbsCPA;
import cpa.symbpredabsCPA.SymbPredAbstFormulaManager;
import exceptions.CPAException;
import exceptions.RefinementFailedException;

/**
 * Outer algorithm to collect all invariants generated during
 * the analysis, and report them to the user
 * 
 * @author g.theoduloz
 */
public class InvariantCollectionAlgorithm implements Algorithm {

  private final Algorithm innerAlgorithm;
  private final MathsatInvariantSymbolicFormulaManager symbolicManager;
  private final SymbPredAbstFormulaManager symbPredAbstManager;
  
  public InvariantCollectionAlgorithm(Algorithm algo)
  {
    innerAlgorithm = algo;
    symbolicManager = MathsatInvariantSymbolicFormulaManager.getInstance();
    symbPredAbstManager = extractSymbPredAbstManager(innerAlgorithm.getCPA());
  }
  
  private SymbPredAbstFormulaManager extractSymbPredAbstManager(ConfigurableProgramAnalysis cpa) {
    if (cpa instanceof SymbPredAbsCPA)
      return ((SymbPredAbsCPA) cpa).getFormulaManager();
    
    if (cpa instanceof CPAWrapper) {
      for (ConfigurableProgramAnalysis subCPA : ((CPAWrapper) cpa).getWrappedCPAs()) {
        SymbPredAbstFormulaManager result = extractSymbPredAbstManager(subCPA);
        if (result != null)
          return result;
      }
    }
    
    return null;
  }

  @Override
  public ConfigurableProgramAnalysis getCPA() {
    return innerAlgorithm.getCPA();
  }
  
  @Override
  public void run(ReachedElements reached, boolean stopAfterError)
      throws CPAException {
    
    InvariantWithLocation invariantMap = new InvariantWithLocation();
    
    try {
      // run the inner algorithm to fill the reached set
      innerAlgorithm.run(reached, stopAfterError);
      
    } catch (RefinementFailedException failedRefinement) {
      addInvariantsForFailedRefinement(invariantMap, failedRefinement);
    }
      
    // collect and dump all assumptions stored in abstract states
    for (Pair<AbstractElementWithLocation, Precision> pair : reached.getReached())
    {
      AbstractElementWithLocation element = pair.getFirst();
      
      CFANode loc = element.getLocationNode();
      SymbolicFormula invariant = extractInvariant(element);
      
      invariantMap.addInvariant(loc, invariant);
    }
    
    // dump invariants to prevent going further with nodes in
    // the waitlist
    addInvariantsForWaitlist(invariantMap, reached.getWaitlist());
    
    invariantMap.dump(System.out);
  }

  /**
   * Returns the invariant(s) stored in the given abstract
   * element
   */
  private SymbolicFormula extractInvariant(AbstractElement element)
  {
    SymbolicFormula result = symbolicManager.makeTrue();
    
    // If it is a wrapper, add its sub-element's assertions
    if (element instanceof AbstractWrapperElement)
    {
      for (AbstractElement subel : ((AbstractWrapperElement) element).getWrappedElements())
        result = symbolicManager.makeAnd(result, extractInvariant(subel));
    }
    
    if (element instanceof DumpInvariantElement)
    {
      SymbolicFormula dumpedInvariant = ((DumpInvariantElement) element).getInvariant();
      if (dumpedInvariant != null)
        result = symbolicManager.makeAnd(result, dumpedInvariant);
    }
     
    return result;
  }
  
  /**
   * Returns a predicate representing states represented by
   * the given abstract element
   */
  private SymbolicFormula extractData(AbstractElement element)
  {
    SymbolicFormula result = symbolicManager.makeTrue();
    
    // If it is a wrapper, add its sub-element's assertions
    if (element instanceof AbstractWrapperElement)
    {
      for (AbstractElement subel : ((AbstractWrapperElement) element).getWrappedElements())
        result = symbolicManager.makeAnd(result, extractData(subel));
    }
    
    if ((symbPredAbstManager != null)
        && (element instanceof SymbPredAbsAbstractElement)) {
      AbstractFormula abstractFormula = ((SymbPredAbsAbstractElement) element).getAbstraction();
      SymbolicFormula symbolicFormula = symbPredAbstManager.toConcrete(abstractFormula);
      result = symbolicManager.makeAnd(result, symbolicFormula);
    }
     
    return result;
  }

  /**
   * Add to the given map the invariant required to
   * avoid the given refinement failure 
   */
  private void addInvariantsForFailedRefinement(
      InvariantWithLocation invariant,
      RefinementFailedException failedRefinement) {
    Path path = failedRefinement.getErrorPath();
    
    int pos = failedRefinement.getFailurePoint();
    
    if (pos == -1)
      pos = path.size() - 2; // the node before the error node
    
    Pair<ARTElement, CFAEdge> pair = path.get(pos);
    SymbolicFormula data = extractData(pair.getFirst());
    invariant.addInvariant(pair.getFirst().getLocationNode(), data);
  }
  
  /**
   * Add to the given map the invariant required to
   * avoid nodes in the given set of states
   */
  private void addInvariantsForWaitlist(
      InvariantWithLocation invariant,
      List<Pair<AbstractElementWithLocation, Precision>> waitlist) {
    for (Pair<AbstractElementWithLocation, Precision> pair : waitlist) {
      AbstractElementWithLocation element = pair.getFirst();
      SymbolicFormula dataRegion = extractData(element);
      invariant.addInvariant(element.getLocationNode(), symbolicManager.makeNot(dataRegion));
    }
  }
}
