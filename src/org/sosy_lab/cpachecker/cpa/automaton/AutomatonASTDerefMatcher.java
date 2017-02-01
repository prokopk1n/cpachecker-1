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
import org.sosy_lab.cpachecker.cfa.ast.c.CArrayDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CArrayRangeDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNode;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNodeVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexTypeDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CDesignatedInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CImaginaryLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CReturnStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSideVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatementVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeDefDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression.TypeIdOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType.CEnumerator;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonASTComparator.ASTMatcherProvider;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;

import com.google.common.base.Optional;

/**
 * Matches a pattern against all dereferenced expression in an AST.
 */
public class AutomatonASTDerefMatcher {

  private final ASTMatcherProvider patternAST;

  public AutomatonASTDerefMatcher(ASTMatcherProvider pAstMatcherProvider) {
    patternAST = pAstMatcherProvider;
  }

  boolean matches(CAstNode pSource, AutomatonExpressionArguments pArgs) throws UnrecognizedCFAEdgeException {
    // Remove default first set of transition variables, each match will add a new one.
    pArgs.scratchTransitionVariablesSeries();
    return pSource.accept(new ASTDerefVisitor(patternAST, pArgs));
  }

  private class ASTDerefVisitor implements CAstNodeVisitor<Boolean, UnrecognizedCFAEdgeException> {

    private final ASTMatcherProvider patternAST;
    private final AutomatonExpressionArguments args;

    public ASTDerefVisitor(ASTMatcherProvider pAstMatcherProvider, AutomatonExpressionArguments pArgs) {
      patternAST = pAstMatcherProvider;
      args = pArgs;
    }

    private boolean match(CExpression exp) {
      args.extendTransitionVariablesSeries();
      CExpressionStatement exp_wrapper = new CExpressionStatement(exp.getFileLocation(), exp);
      boolean res = patternAST.getMatcher().matches(exp_wrapper, args);

      if (!res) {
        args.scratchTransitionVariablesSeries();
      }

      return res;
    }

    @Override
    public Boolean visit(CArrayDesignator pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CArrayRangeDesignator pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CFieldDesignator pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CInitializerList pNode) throws UnrecognizedCFAEdgeException {
      boolean res = false;

      for (CInitializer initializer : pNode.getInitializers()) {
        res = res | initializer.accept(this);
      }

      return res;
    }

    @Override
    public Boolean visit(CReturnStatement pNode) throws UnrecognizedCFAEdgeException {
      Optional<CExpression> ret = pNode.getReturnValue();

      if (ret.isPresent()) {
        return ret.get().accept(this);
      } else {
        return false;
      }
    }

    @Override
    public Boolean visit(CDesignatedInitializer pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CInitializerExpression pNode) throws UnrecognizedCFAEdgeException {
      return pNode.getExpression().accept(this);
    }

    @Override
    public Boolean visit(CFunctionCallExpression pNode) throws UnrecognizedCFAEdgeException {
      boolean res = pNode.getFunctionNameExpression().accept(this);

      for (CExpression parameter : pNode.getParameterExpressions()) {
        res = res | parameter.accept(this);
      }

      return res;
    }

    @Override
    public Boolean visit(CBinaryExpression pNode) throws UnrecognizedCFAEdgeException {
      return pNode.getOperand1().accept(this) | pNode.getOperand2().accept(this);
    }

    @Override
    public Boolean visit(CCastExpression pNode) throws UnrecognizedCFAEdgeException {
      return pNode.getOperand().accept(this);
    }

    @Override
    public Boolean visit(CTypeIdExpression pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CUnaryExpression pNode) throws UnrecognizedCFAEdgeException {
      return pNode.getOperand().accept(this);
    }

    @Override
    public Boolean visit(CArraySubscriptExpression pNode) throws UnrecognizedCFAEdgeException {
      return match(pNode.getArrayExpression()) | pNode.getArrayExpression().accept(this)
        | pNode.getSubscriptExpression().accept(this);
    }

    @Override
    public Boolean visit(CComplexCastExpression pNode) throws UnrecognizedCFAEdgeException {
      return pNode.getOperand().accept(this);
    }

    @Override
    public Boolean visit(CFieldReference pNode) throws UnrecognizedCFAEdgeException {
      boolean res = pNode.getFieldOwner().accept(this);

      if (pNode.isPointerDereference()) {
        res = res | match(pNode.getFieldOwner());
      }

      return res;
    }

    @Override
    public Boolean visit(CIdExpression pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CPointerExpression pNode) throws UnrecognizedCFAEdgeException {
      return match(pNode.getOperand()) | pNode.getOperand().accept(this);
    }

    @Override
    public Boolean visit(CCharLiteralExpression pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CFloatLiteralExpression pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CImaginaryLiteralExpression pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CIntegerLiteralExpression pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CStringLiteralExpression pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CAddressOfLabelExpression pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CParameterDeclaration pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CFunctionDeclaration pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CComplexTypeDeclaration pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CTypeDefDeclaration pNode) throws UnrecognizedCFAEdgeException {
      return false;
    }

    @Override
    public Boolean visit(CVariableDeclaration pNode) throws UnrecognizedCFAEdgeException {
      CInitializer initializer = pNode.getInitializer();

      if (initializer != null) {
        return initializer.accept(this);
      } else {
        return false;
      }
    }

    @Override
    public Boolean visit(CExpressionAssignmentStatement pNode) throws UnrecognizedCFAEdgeException {
      return visit((CAssignment)pNode);
    }

    @Override
    public Boolean visit(CExpressionStatement pNode) throws UnrecognizedCFAEdgeException {
      return pNode.getExpression().accept(this);
    }

    @Override
    public Boolean visit(CFunctionCallAssignmentStatement pNode) throws UnrecognizedCFAEdgeException {
      return visit((CAssignment)pNode);
    }

    @Override
    public Boolean visit(CFunctionCallStatement pNode) throws UnrecognizedCFAEdgeException {
      return pNode.getFunctionCallExpression().accept(this);
    }

    @Override
    public Boolean visit(CEnumerator pCEnumerator) {
      return false;
    }

    public Boolean visit(CAssignment pNode) throws UnrecognizedCFAEdgeException {
      return pNode.getLeftHandSide().accept(this) | pNode.getRightHandSide().accept(this);
    }
  }
}
