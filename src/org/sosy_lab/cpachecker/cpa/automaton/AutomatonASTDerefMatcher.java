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

import org.sosy_lab.cpachecker.cfa.ast.AAstNode;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CAddressOfLabelExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNode;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CImaginaryLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSideVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatementVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression.TypeIdOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonASTComparator.ASTMatcherProvider;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;

/**
 * Matches a pattern against all dereferenced expression in an AST.
 */
public class AutomatonASTDerefMatcher {

  private final ASTMatcherProvider patternAST;

  public AutomatonASTDerefMatcher(ASTMatcherProvider pAstMatcherProvider) {
    patternAST = pAstMatcherProvider;
  }

  boolean matches(CAstNode pSource, AutomatonExpressionArguments pArgs) throws UnrecognizedCFAEdgeException {
    if (pSource instanceof CStatement) {
      return ((CStatement)pSource).accept(new ASTDerefVisitor(patternAST, pArgs));
    } else {
      throw new UnrecognizedCFAEdgeException(pArgs.getCfaEdge());
    }
  }

  private class ASTDerefVisitor implements CStatementVisitor<Boolean, UnrecognizedCFAEdgeException>,
                                           CRightHandSideVisitor<Boolean, UnrecognizedCFAEdgeException> {

    private final ASTMatcherProvider patternAST;
    private final AutomatonExpressionArguments args;

    public ASTDerefVisitor(ASTMatcherProvider pAstMatcherProvider, AutomatonExpressionArguments pArgs) {
      patternAST = pAstMatcherProvider;
      args = pArgs;
    }

    private boolean match(CExpression exp) {
      // TODO: do something with args to store several sets of transition variables correctly.
      return patternAST.getMatcher().matches(exp, args);
    }

    @Override
    public Boolean visit(CExpressionStatement exp) throws UnrecognizedCFAEdgeException {
      return exp.getExpression().accept(this);
    }

    @Override
    public Boolean visit(CExpressionAssignmentStatement stmt) throws UnrecognizedCFAEdgeException {
      return ((CAssignment)stmt).accept(this);
    }

    @Override
    public Boolean visit(CFunctionCallAssignmentStatement stmt) throws UnrecognizedCFAEdgeException {
      return ((CAssignment)stmt).accept(this);
    }

    @Override
    public Boolean visit(CFunctionCallStatement stmt) throws UnrecognizedCFAEdgeException {
      return stmt.getFunctionCallExpression().accept(this);
    }

    public Boolean visit(CAssignment stmt) throws UnrecognizedCFAEdgeException {
      return stmt.getLeftHandSide().accept(this) || stmt.getRightHandSide().accept(this);
    }

    @Override
    public Boolean visit(CFunctionCallExpression exp) throws UnrecognizedCFAEdgeException {
      boolean res = exp.getFunctionNameExpression().accept(this);

      for (CExpression parameter : exp.getParameterExpressions()) {
        res = res || parameter.accept(this);
      }

      return res;
    }

    @Override
    public Boolean visit(CBinaryExpression exp) throws UnrecognizedCFAEdgeException {
      return exp.getOperand1().accept(this) || exp.getOperand2().accept(this);
    }

    @Override
    public Boolean visit(CCastExpression exp) throws UnrecognizedCFAEdgeException {
      return exp.getOperand().accept(this);
    }

    @Override
    public Boolean visit(CCharLiteralExpression exp) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CFloatLiteralExpression exp) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CIntegerLiteralExpression exp) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CStringLiteralExpression exp) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CTypeIdExpression exp) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CUnaryExpression exp) throws UnrecognizedCFAEdgeException {
      return exp.getOperand().accept(this);
    }

    @Override
    public Boolean visit(CImaginaryLiteralExpression exp) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CAddressOfLabelExpression exp) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CArraySubscriptExpression exp) throws UnrecognizedCFAEdgeException {
      return match(exp.getArrayExpression()) || exp.getArrayExpression().accept(this)
        || exp.getSubscriptExpression().accept(this);
    }

    @Override
    public Boolean visit(CFieldReference exp) throws UnrecognizedCFAEdgeException {
      boolean res = exp.getFieldOwner().accept(this);

      if (exp.isPointerDereference()) {
        res = res || match(exp.getFieldOwner());
      }

      return res;
    }

    @Override
    public Boolean visit(CIdExpression exp) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CPointerExpression exp) throws UnrecognizedCFAEdgeException {
      return match(exp.getOperand()) || exp.getOperand().accept(this);
    }

    @Override
    public Boolean visit(CComplexCastExpression exp) throws UnrecognizedCFAEdgeException {
      return exp.getOperand().accept(this);
    }
  }
}
