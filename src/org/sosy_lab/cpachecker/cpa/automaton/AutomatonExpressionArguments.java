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
package org.sosy_lab.cpachecker.cpa.automaton;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AAstNode;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNode;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.SubstitutingCAstNodeVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.SubstitutingCAstNodeVisitor.SubstituteProvider;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CProblemType;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.ForwardingCFAVisitor;
import org.sosy_lab.cpachecker.util.CFATraversal.NodeCollectingCFAVisitor;
import org.sosy_lab.cpachecker.util.CFATraversal.TraversalProcess;
import org.sosy_lab.cpachecker.util.Pair;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutomatonExpressionArguments {

  private Map<String, AutomatonVariable> automatonVariables;
  // Variables that are only valid for one transition ($1,$2,...)
  // these will be set in a MATCH statement, and are erased when the transitions actions are executed.
  // MATCH DEREF statement may set several series of variables.
  private ArrayList<Map<Integer, AAstNode>> transitionVariablesSeries = new ArrayList<>();
  private int transitionVariablesSeriesNum = 0;
  private List<AbstractState> abstractStates;
  private AutomatonState state;
  private CFAEdge cfaEdge;
  private LogManager logger;
  private CFA cfa;

  /**
   * In this String all print messages of the Transition are collected.
   * They are logged (INFO-level) together at the end of the transition actions.
   */
  private String transitionLogMessages = "";

  // the pattern \$\$\d+ matches Expressions like $$x $$y23rinnksd $$AutomatonVar (all terminated by a non-word-character)
  private static Pattern AUTOMATON_VARS_PATTERN = Pattern.compile("\\$\\$[a-zA-Z]\\w*");
  // the pattern \$\d+ matches Expressions like $1 $2 $3 $201
  // If this pattern is changed the pattern in AutomatonASTcomparison should be changed too!
  private static Pattern TRANSITION_VARS_PATTERN = Pattern.compile("\\$\\d+");

  public AutomatonExpressionArguments(AutomatonState pState,
      Map<String, AutomatonVariable> pAutomatonVariables,
      List<AbstractState> pAbstractStates, CFAEdge pCfaEdge,
      LogManager pLogger) {
    super();
    if (pAutomatonVariables == null) {
      automatonVariables = Collections.emptyMap();
    } else {
      automatonVariables = pAutomatonVariables;
    }
    if (pAbstractStates == null) {
      abstractStates = Collections.emptyList();
    } else {
      abstractStates = pAbstractStates;
    }

    transitionVariablesSeries.add(new HashMap<Integer, AAstNode>());

    cfaEdge = pCfaEdge;
    logger = pLogger;
    state = pState;
    cfa = null;
  }

  void setAutomatonVariables(Map<String, AutomatonVariable> pAutomatonVariables) {
    automatonVariables = pAutomatonVariables;
  }

  void setCFA(final CFA pCFA) {
    cfa = pCFA;
  }

  Map<String, AutomatonVariable> getAutomatonVariables() {
    return automatonVariables;
  }

  List<AbstractState> getAbstractStates() {
    return abstractStates;
  }

  public CFAEdge getCfaEdge() {
    return cfaEdge;
  }

  LogManager getLogger() {
    return logger;
  }
  void appendToLogMessage(String message) {
    this.transitionLogMessages = transitionLogMessages  + message;
  }
  void appendToLogMessage(int message) {
    this.transitionLogMessages = transitionLogMessages  + message;
  }
  String getLogMessage() {
    return transitionLogMessages;
  }
  void clearLogMessage() {
    transitionLogMessages = "";
  }

  void clearTransitionVariables() {
    this.transitionVariablesSeries.clear();
    this.transitionVariablesSeries.add(new HashMap<Integer, AAstNode>());
    this.transitionVariablesSeriesNum = 0;
  }

  private boolean containsTransitionVariable(final int pKey) {
    return this.transitionVariablesSeries.get(this.transitionVariablesSeriesNum).containsKey(pKey);
  }

  AAstNode getTransitionVariable(final int pKey) {
    // this is the variable adressed with $<key> in the automaton definition
    return this.transitionVariablesSeries.get(this.transitionVariablesSeriesNum).get(pKey);
  }

  void putTransitionVariable(int pKey, AAstNode pValue) {
    this.transitionVariablesSeries.get(this.transitionVariablesSeriesNum).put(pKey, pValue);
  }

  /**
   * This method replaces all references to
   * 1. AutomatonVariables (referenced by $$<Name-of-Variable>)
   * 2. TransitionVariables (referenced by $<Number-of-Variable>)
   * with the values of the variables.
   * If the variable is not found the function returns null.
   */
  String replaceVariables(String pSourceString) {

    // replace references to Transition Variables
    Matcher matcher = AutomatonExpressionArguments.TRANSITION_VARS_PATTERN.matcher(pSourceString);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(result, "");
      String key = matcher.group().substring(1); // matched string startswith $
      try {
        int varKey = Integer.parseInt(key);
        AAstNode node = this.getTransitionVariable(varKey);
        if (node == null) {
          // this variable has not been set.
          this.getLogger().log(Level.WARNING, "could not replace the transition variable $" + varKey + " (not found).");
          return null;
        } else {
          result.append(node.toASTString());
        }
      } catch (NumberFormatException e) {
        this.getLogger().log(Level.WARNING, "could not parse the int in " + matcher.group() + " , leaving it untouched");
        result.append(matcher.group());
      }
    }
    matcher.appendTail(result);

    // replace references to automaton Variables
    matcher = AutomatonExpressionArguments.AUTOMATON_VARS_PATTERN.matcher(result.toString());
    result = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(result, "");
      String varName =  matcher.group().substring(2); // matched string starts with $$
      AutomatonVariable variable = this.getAutomatonVariables().get(varName);
      if (variable == null) {
        // this variable has not been set.
        this.getLogger().log(Level.WARNING, "could not replace the Automaton variable reference " + varName + " (not found).");
        return null;
      } else {
        result.append(variable.getValue());
      }
    }
    matcher.appendTail(result);
    return result.toString();
  }

  public AutomatonState getState() {
    return state;
  }

  Map<Integer, AAstNode> getTransitionVariables() {
    return this.transitionVariablesSeries.get(this.transitionVariablesSeriesNum);
  }

  void putTransitionVariables(Map<Integer, AAstNode> pTransitionVariables) {
    this.transitionVariablesSeries.get(this.transitionVariablesSeriesNum).putAll(pTransitionVariables);
  }

  ArrayList<Map<Integer, AAstNode>> getTransitionVariablesSeries() {
    return this.transitionVariablesSeries;
  }

  void putTransitionVariablesSeries(List<Map<Integer, AAstNode>> pTransitionVariablesSeries) {
    this.transitionVariablesSeries = new ArrayList<>(pTransitionVariablesSeries);
  }

  void extendTransitionVariablesSeries() {
    this.transitionVariablesSeriesNum = this.transitionVariablesSeries.size();
    this.transitionVariablesSeries.add(new HashMap<Integer, AAstNode>());
  }

  void scratchTransitionVariablesSeries() {
    this.transitionVariablesSeries.remove(this.transitionVariablesSeriesNum);
    this.transitionVariablesSeriesNum--;
  }

  /**
   * For a {@code CBinaryExpression} of type {@code CProblemType} try to fix type.
   *
   * @param pExpression The expression to fix.
   * @return An expression with a fixed expression type, if one could be found. Otherwise, the
   * original expression will be returned.
   */
  private CBinaryExpression fixBinaryExpressionType(final CBinaryExpression pExpression) {
    // TODO: implement correct type fixing logic.
    final CBinaryExpression fixedExpression = new CBinaryExpression(
        pExpression.getFileLocation(),
        pExpression.getOperand2().getExpressionType(),
        pExpression.getOperand2().getExpressionType(),
        pExpression.getOperand1(),
        pExpression.getOperand2(),
        pExpression.getOperator());
    return fixedExpression;
  }

  /**
   * Searches for a declaration of an expression in the set of transition variables.
   *
   * @param pExpressionName The name of the expression.
   * @return The declaration of a transition variable if one could be found, {@code null} otherwise.
   */
  private CSimpleDeclaration getDeclarationForTransitionVariable(final String pExpressionName) {
    final int index =
        Integer.parseInt(pExpressionName.replace(AutomatonASTComparator.NUMBERED_JOKER_EXPR, ""));
    if (containsTransitionVariable(index) && getTransitionVariable(index) instanceof CIdExpression) {
      return ((CIdExpression) getTransitionVariable(index)).getDeclaration();
    }

    return null;
  }

  private CAstNode substituteJokerVariables(CAstNode pNode) {
    final SubstitutingCAstNodeVisitor visitor = new SubstitutingCAstNodeVisitor(new SubstituteProvider() {
      @Override
      public CAstNode findSubstitute(CAstNode pNode) {

        if (pNode instanceof CIdExpression) {
          CIdExpression exp = (CIdExpression) pNode;
          String name = exp.getName();

          // TODO: Ensure type safety of the substitution!

          if (name.startsWith(AutomatonASTComparator.NUMBERED_JOKER_EXPR)) {

            String varIdStr = name.substring(AutomatonASTComparator.NUMBERED_JOKER_EXPR.length());
            int varId = Integer.parseInt(varIdStr);
            return (CAstNode) getTransitionVariable(varId);

          } else if (name.startsWith(AutomatonASTComparator.NAMED_JOKER_EXPR)) {

            String varIdStr = name.substring(AutomatonASTComparator.NAMED_JOKER_EXPR.length());
            AutomatonVariable var = automatonVariables.get(varIdStr);

            Preconditions.checkState(var != null, "The referenced automata variable must be defined for the state!");

            return new CIntegerLiteralExpression(pNode.getFileLocation(),
                CNumericTypes.INT, BigInteger.valueOf(var.getValue()));
          } else {
            // A variable inside a substitution template doesn't have a declaration, find it in CFA.
            Preconditions.checkNotNull(
                cfa,
                "Unable to fix a expression type with out a CFA. Inject a CFA with "
                    + "\"AutomatonExpressionArguments.setCFA(CFA)\" before calling the method "
                    + "\"substituteJokerVariables(CAstNode)\"!");

            if (cfa.getMainFunction().getReturnVariable().isPresent()) {
              CVariableDeclaration returnVariableDeclaration = (CVariableDeclaration) cfa.getMainFunction().getReturnVariable().get();

              if (name.equals(returnVariableDeclaration.getName())) {
                return new CIdExpression(
                    exp.getFileLocation(),
                    returnVariableDeclaration.getType(),
                    exp.toASTString(),
                    exp.getDeclaration() == null ? returnVariableDeclaration : exp.getDeclaration());
              }
            }

            final SearchDeclarationVisitor visitor =
                new SearchDeclarationVisitor(exp.toASTString());
            CFATraversal.dfs().traverseOnce(cfa.getMainFunction(), visitor);

            if (visitor.getMatching().size() == 0) {
              return exp;
            }

            assert visitor.getMatching().size() == 1 : "More than one matching declaration found, result might "
                + "be ambiguous!";

            final CSimpleDeclaration newDeclaration = visitor.getMatching().get(0);
            final CIdExpression newExp = new CIdExpression(
                exp.getFileLocation(),
                newDeclaration.getType(),
                exp.toASTString(),
                exp.getDeclaration() == null ? newDeclaration : exp.getDeclaration());
            return newExp;
          }
        }

        // Attention: Only put here code for cases where we DO NOT HAVE TO
        //    perform substitutions of child nodes later!

        return null;
      }

      private boolean isProblemExpression(CBinaryExpression pExpr) {
        return pExpr.getCalculationType() instanceof CProblemType
            || pExpr.getExpressionType() instanceof CProblemType;
      }

      @Override
      public CAstNode adjustTypesAfterSubstitution(CAstNode pNode) {
        if (pNode instanceof CBinaryExpression) {
          // Fix assume expressions where the type could not be determined during parsing because
          // the type information needs to be inferred from information in a C header file; this
          // information is not present during automaton parsing, hence we have to adjust this
          // afterwards.
          final CBinaryExpression expression = (CBinaryExpression) pNode;
          if (isProblemExpression(expression)) {
            CBinaryExpression result = fixBinaryExpressionType(expression);
            Preconditions.checkState(!isProblemExpression(result));
            return result;
          }
        } else if (pNode instanceof CIdExpression) {
          CIdExpression idExpr = (CIdExpression) pNode;
          if (idExpr.getExpressionType() instanceof CProblemType) {
            throw new IllegalStateException(String.format("The ID expression '%s' is not mapped to a correct type!"
                + "\nIs the referenced variable declared? Misspelling or missing header file?", idExpr.toString()));
          }
        }

        return null;
      }
    });

    final CAstNode result = pNode.accept(visitor);
    return result;
  }

  ImmutableList<Pair<AStatement, Boolean>> instantiateAssumptions(
      final ImmutableList<Pair<AStatement, Boolean>> pSymbolicAssumes) {

    Builder<Pair<AStatement, Boolean>> builder = ImmutableList.<Pair<AStatement, Boolean>>builder();

    for (Pair<AStatement, Boolean> entry: pSymbolicAssumes) {
      final AStatement stmt = entry.getFirst();
      final Boolean truth = entry.getSecond();

      if (stmt instanceof CStatement) {
        for (int i = 0; i < transitionVariablesSeries.size(); i++) {
          transitionVariablesSeriesNum = i;
          CStatement inst = (CStatement)substituteJokerVariables((CStatement) stmt);
          builder.add(Pair.<AStatement, Boolean>of(inst, truth));
        }
      } else {
        this.getLogger().log(Level.WARNING, "Could not instantiate transition assumption! Support for non-C-languages is missing at the moment!");
        builder.add(Pair.of(stmt, truth));
      }
    }

    return builder.build();
  }

  public List<AAstNode> instantiateCode(ImmutableList<AAstNode> pShadowCode) {
    Builder<AAstNode> result = ImmutableList.<AAstNode>builder();

    for (int i = 0; i < transitionVariablesSeries.size(); i++) {
      for (AAstNode n: pShadowCode) {
        transitionVariablesSeriesNum = i;
        AAstNode nPrime = substituteJokerVariables((CAstNode)n);
        result.add(nPrime);
      }
    }

    return result.build();
  }

  /**
   * A visitor to iterate over the CFA in order to find a declaration edge whose qualified name
   * matches a given search pattern.
   *
   * @see #fixBinaryExpressionType(CBinaryExpression)
   */
  private final static class SearchDeclarationVisitor extends ForwardingCFAVisitor {

    private final List<CSimpleDeclaration> matchingDeclarations = new ArrayList<>();
    private final String searchPattern;

    SearchDeclarationVisitor(final String pSearchPattern) {
      super(new NodeCollectingCFAVisitor());
      searchPattern = pSearchPattern;
    }

    @Override
    public TraversalProcess visitEdge(final CFAEdge pEdge) {
      if (pEdge.getPredecessor() instanceof CFunctionEntryNode) {
        CFunctionEntryNode entryNode = (CFunctionEntryNode) pEdge.getPredecessor();

        for (CParameterDeclaration paramDeclaration : entryNode.getFunctionParameters()) {
          if (searchPattern.equals(paramDeclaration.getName())) {
            matchingDeclarations.add(paramDeclaration);
          }
        }
      }

      if (!(pEdge instanceof CDeclarationEdge)) {
        return TraversalProcess.CONTINUE;
      }

      final CDeclarationEdge edge = (CDeclarationEdge) pEdge;
      if (searchPattern.equals(edge.getDeclaration().getName())) {
        matchingDeclarations.add(edge.getDeclaration());
      }
      return TraversalProcess.CONTINUE;
    }

    List<CSimpleDeclaration> getMatching() {
      return matchingDeclarations;
    }
  }


}
