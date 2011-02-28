package org.sosy_lab.cpachecker.cfa.ast;

import java.util.List;

/** This class implements the STANDARD-FunctionDeclarator of eclipse! */
public class IASTFunctionDeclarator extends IASTDeclarator {

  private final List<IASTParameterDeclaration> parameters;
  private final boolean                        takesVarArgs;

  public IASTFunctionDeclarator(final String pRawSignature,
      final IASTFileLocation pFileLocation, final IASTInitializer pInitializer,
      final IASTName pName, final IASTDeclarator pNestedDeclarator,
      final List<IASTPointer> pPointerOperators,
      final List<IASTParameterDeclaration> pParameters,
      final boolean pTakesVarArgs) {
    super(pRawSignature, pFileLocation, pInitializer, pName, pNestedDeclarator,
        pPointerOperators);
    parameters = pParameters;
    takesVarArgs = pTakesVarArgs;
  }

  public IASTParameterDeclaration[] getParameters() {
    return parameters.toArray(new IASTParameterDeclaration[parameters.size()]);
  }

  @Override
  public IASTNode[] getChildren() {
    final IASTNode[] children1 = super.getChildren();
    final IASTNode[] children2 = getParameters();
    IASTNode[] allChildren = new IASTNode[children1.length + children2.length];
    System.arraycopy(children1, 0, allChildren, 0, children1.length);
    System.arraycopy(children2, 0, allChildren, children1.length,
        children2.length);
    return allChildren;
  }

  public boolean takesVarArgs() {
    return takesVarArgs;
  }
}
