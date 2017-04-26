/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa.ast.c;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import org.sosy_lab.cpachecker.cfa.types.c.CEnumType.CEnumerator;

import java.util.List;

import javax.annotation.Nullable;

public class SubstitutingCAstNodeVisitor implements CAstNodeVisitor<CAstNode, UnsupportedOperationException> {

  public interface SubstituteProvider {
    @Nullable CAstNode findSubstitute(CAstNode pNode);
    @Nullable CAstNode adjustTypesAfterSubstitution(CAstNode pNode);
  }

  private SubstituteProvider sp;

  public SubstitutingCAstNodeVisitor(final SubstituteProvider pSubstituteProvider) {
    this.sp = Preconditions.checkNotNull(pSubstituteProvider);
  }

  private <T> T firstNotNull(final T pExpr1, final T pExpr2) {
    if (pExpr1 != null) {
      return pExpr1;
    }
    if (pExpr2 != null) {
      return pExpr2;
    }
    return null;
  }

  private CAstNode adjustTypesAfterSubstitution(CAstNode pNode) {
    return firstNotNull(sp.adjustTypesAfterSubstitution(pNode), pNode);
  }

  @Override
  public CAstNode visit(final CArrayDesignator pNode) {
    CAstNode result = sp.findSubstitute(pNode);

    if (result == null) {
      final CExpression oldSubEx = pNode.getSubscriptExpression();
      final CExpression newSubEx = (CExpression) oldSubEx.accept(this);
      if (oldSubEx != newSubEx) {
        result = new CArrayDesignator(
            oldSubEx.getFileLocation(),
            newSubEx);
      }
    }

    return adjustTypesAfterSubstitution(firstNotNull(result, pNode));
  }

  @Override
  public CAstNode visit(final CArrayRangeDesignator pNode)  {
    CAstNode result = sp.findSubstitute(pNode);

    if (result == null) {
      final CExpression oldCeil = pNode.getCeilExpression();
      final CExpression newCeil = (CExpression) pNode.getCeilExpression().accept(this);

      final CExpression oldFloor = pNode.getFloorExpression();
      final CExpression newFloor = (CExpression) pNode.getFloorExpression().accept(this);

      if (oldCeil != newCeil || newFloor != oldFloor) {
        result = new CArrayRangeDesignator(
            pNode.getFileLocation(),
            firstNotNull(newFloor, oldFloor),
            firstNotNull(newCeil, oldCeil));
      }
    }

    return adjustTypesAfterSubstitution(firstNotNull(result, pNode));
  }

  @Override
  public CAstNode visit(final CFieldDesignator pNode) {
    return adjustTypesAfterSubstitution(firstNotNull(sp.findSubstitute(pNode), pNode));
  }

  @Override
  public CAstNode visit(final CInitializerList pNode) {
    CAstNode result = sp.findSubstitute(pNode);

    if (result == null) {
      boolean initChanged = false;
      List<CInitializer> newInitializerList = Lists.newArrayListWithExpectedSize(pNode.getInitializers().size());

      for (CInitializer oldInit: pNode.getInitializers()) {
        CInitializer newInit = (CInitializer) oldInit.accept(this);
        if (newInit != oldInit) {
          initChanged = true;
        }
        newInitializerList.add(newInit);
      }

      if (initChanged) {
        result = new CInitializerList(pNode.getFileLocation(), newInitializerList);
      }
    }

    return adjustTypesAfterSubstitution(firstNotNull(result, pNode));
  }

  @Override
  public CAstNode visit(final CReturnStatement pNode) {
    throw new UnsupportedOperationException("Not yet implemented! Implement me!");
  }

  @Override
  public CAstNode visit(final CDesignatedInitializer pNode) {
    throw new UnsupportedOperationException("Not yet implemented! Implement me!");
  }

  @Override
  public CAstNode visit(final CInitializerExpression pNode) {
    throw new UnsupportedOperationException("Not yet implemented! Implement me!");
  }

  @Override
  public CAstNode visit(final CFunctionCallExpression pNode) {
    throw new UnsupportedOperationException("Not yet implemented! Implement me!");
  }

  @Override
  public CAstNode visit(final CBinaryExpression pNode) {
    CAstNode result = sp.findSubstitute(pNode);

    if (result == null) {
      final CExpression oldOp1 = pNode.getOperand1();
      final CExpression newOp1 = (CExpression) pNode.getOperand1().accept(this);

      final CExpression oldOp2 = pNode.getOperand2();
      final CExpression newOp2 = (CExpression) pNode.getOperand2().accept(this);

      if (oldOp1 != newOp1 || oldOp2 != newOp2) {
        result = new CBinaryExpression(
            pNode.getFileLocation(),
            pNode.getExpressionType(),
            pNode.getCalculationType(),
            firstNotNull(newOp1, oldOp1),
            firstNotNull(newOp2, oldOp2),
            pNode.getOperator());
      }
    }

    return adjustTypesAfterSubstitution(firstNotNull(result, pNode));
  }

  @Override
  public CAstNode visit(final CCastExpression pNode) {
    CAstNode result = sp.findSubstitute(pNode);

    if (result == null) {
      final CExpression oldOp1 = pNode.getOperand();
      final CExpression newOp1 = (CExpression) pNode.getOperand().accept(this);

      if (oldOp1 != newOp1) {
        result = new CCastExpression(
            pNode.getFileLocation(),
            pNode.getExpressionType(),
            newOp1);
      }
    }

    return adjustTypesAfterSubstitution(firstNotNull(result, pNode));
  }

  @Override
  public CAstNode visit(final CTypeIdExpression pNode) {
    return adjustTypesAfterSubstitution(firstNotNull(sp.findSubstitute(pNode), pNode));
  }

  @Override
  public CAstNode visit(final CUnaryExpression pNode) {
    CAstNode result = sp.findSubstitute(pNode);

    if (result == null) {
      final CExpression oldOp1 = pNode.getOperand();
      final CExpression newOp1 = (CExpression) pNode.getOperand().accept(this);

      if (oldOp1 != newOp1) {
        result = new CUnaryExpression(
            pNode.getFileLocation(),
            pNode.getExpressionType(),
            firstNotNull(newOp1, oldOp1),
            pNode.getOperator());
      }
    }

    return adjustTypesAfterSubstitution(firstNotNull(result, pNode));
  }

  @Override
  public CAstNode visit(final CArraySubscriptExpression pNode) {
    CAstNode result = sp.findSubstitute(pNode);

    if (result == null) {
      final CExpression oldOp1 = pNode.getArrayExpression();
      final CExpression newOp1 = (CExpression) pNode.getArrayExpression().accept(this);

      final CExpression oldOp2 = pNode.getSubscriptExpression();
      final CExpression newOp2 = (CExpression) pNode.getSubscriptExpression().accept(this);

      if (oldOp1 != newOp1) {
        result = new CArraySubscriptExpression(
            pNode.getFileLocation(),
            pNode.getExpressionType(),
            firstNotNull(newOp1, oldOp1),
            firstNotNull(newOp2, oldOp2));
      }
    }

    return adjustTypesAfterSubstitution(firstNotNull(result, pNode));
  }

  @Override
  public CAstNode visit(final CComplexCastExpression pNode) {
    throw new UnsupportedOperationException("Not yet implemented! Implement me!");
  }

  @Override
  public CAstNode visit(final CFieldReference pNode) {
    return adjustTypesAfterSubstitution(firstNotNull(sp.findSubstitute(pNode), pNode));
  }

  @Override
  public CAstNode visit(final CIdExpression pNode) {
    return adjustTypesAfterSubstitution(firstNotNull(sp.findSubstitute(pNode), pNode));
  }

  @Override
  public CAstNode visit(final CPointerExpression pNode) {
    CAstNode result = sp.findSubstitute(pNode);

    if (result == null) {
      final CExpression oldOp1 = pNode.getOperand();
      final CExpression newOp1 = (CExpression) pNode.getOperand().accept(this);

      if (oldOp1 != newOp1) {
        result = new CPointerExpression(
            pNode.getFileLocation(),
            pNode.getExpressionType(),
            firstNotNull(newOp1, oldOp1));
      }
    }

    return adjustTypesAfterSubstitution(firstNotNull(result, pNode));
  }

  @Override
  public CAstNode visit(final CCharLiteralExpression pNode) {
    return adjustTypesAfterSubstitution(firstNotNull(sp.findSubstitute(pNode), pNode));
  }

  @Override
  public CAstNode visit(final CFloatLiteralExpression pNode) {
    return adjustTypesAfterSubstitution(firstNotNull(sp.findSubstitute(pNode), pNode));
  }

  @Override
  public CAstNode visit(final CImaginaryLiteralExpression pNode) {
    return adjustTypesAfterSubstitution(firstNotNull(sp.findSubstitute(pNode), pNode));
  }

  @Override
  public CAstNode visit(final CIntegerLiteralExpression pNode) {
    return adjustTypesAfterSubstitution(firstNotNull(sp.findSubstitute(pNode), pNode));
  }

  @Override
  public CAstNode visit(final CStringLiteralExpression pNode) {
    return adjustTypesAfterSubstitution(firstNotNull(sp.findSubstitute(pNode), pNode));
  }

  @Override
  public CAstNode visit(final CAddressOfLabelExpression pNode) {
    throw new UnsupportedOperationException("Not yet implemented! Implement me!");
  }

  @Override
  public CAstNode visit(final CParameterDeclaration pNode) {
    throw new UnsupportedOperationException("Not yet implemented! Implement me!");
  }

  @Override
  public CAstNode visit(final CFunctionDeclaration pNode) {
    throw new UnsupportedOperationException("Not yet implemented! Implement me!");
  }

  @Override
  public CAstNode visit(final CComplexTypeDeclaration pNode) {
    throw new UnsupportedOperationException("Not yet implemented! Implement me!");
  }

  @Override
  public CAstNode visit(final CTypeDefDeclaration pNode) {
    throw new UnsupportedOperationException("Not yet implemented! Implement me!");
  }

  @Override
  public CAstNode visit(final CVariableDeclaration pNode) {
    CAstNode result = sp.findSubstitute(pNode);

    if (result == null) {
      CInitializer init = (CInitializer) sp.findSubstitute(pNode.getInitializer());
      if (init != null && init != pNode.getInitializer()) {
        result = new CVariableDeclaration(pNode.getFileLocation(), pNode.isGlobal(),
            pNode.getCStorageClass(), pNode.getType(), pNode.getName(),
            pNode.getOrigName(), pNode.getQualifiedName(),
            init);
      }
    }

    return adjustTypesAfterSubstitution(firstNotNull(result, pNode));
  }

  @Override
  public CAstNode visit(final CExpressionAssignmentStatement pNode) {
    CAstNode result = sp.findSubstitute(pNode);

    if (result == null) {
      final CLeftHandSide oldLeft = pNode.getLeftHandSide();
      final CLeftHandSide newLeft = (CLeftHandSide) pNode.getLeftHandSide().accept(this);

      final CExpression oldRight = pNode.getRightHandSide();
      final CExpression newRight = (CExpression) pNode.getRightHandSide().accept(this);

      if (oldRight != newRight || oldLeft != newLeft) {
        result = new CExpressionAssignmentStatement(
            pNode.getFileLocation(),
            firstNotNull(newLeft, oldLeft),
            firstNotNull(newRight, oldRight));
      }
    }

    return adjustTypesAfterSubstitution(firstNotNull(result, pNode));
  }

  @Override
  public CAstNode visit(final CExpressionStatement pNode) {
    CAstNode result = sp.findSubstitute(pNode);

    if (result == null) {
      final CExpression oldRight = pNode.getExpression();
      final CExpression newRight = (CExpression) pNode.getExpression().accept(this);

      if (oldRight != newRight) {
        result = new CExpressionStatement(
            pNode.getFileLocation(),
            newRight);
      }
    }

    return adjustTypesAfterSubstitution(firstNotNull(result, pNode));
  }

  @Override
  public CAstNode visit(final CFunctionCallAssignmentStatement pNode) {
    throw new UnsupportedOperationException("Not yet implemented! Implement me!");
  }

  @Override
  public CAstNode visit(final CFunctionCallStatement pNode) {
    throw new UnsupportedOperationException("Not yet implemented! Implement me!");
  }

  @Override
  public CAstNode visit(final CEnumerator pCEnumerator) {
    throw new UnsupportedOperationException("Not yet implemented! Implement me!");
  }


}
