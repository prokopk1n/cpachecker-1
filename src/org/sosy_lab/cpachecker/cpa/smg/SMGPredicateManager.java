// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg;

import com.google.common.collect.ImmutableSet;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cpa.smg.graphs.SMGPredicateRelation;
import org.sosy_lab.cpachecker.cpa.smg.graphs.SMGPredicateRelation.ExplicitRelation;
import org.sosy_lab.cpachecker.cpa.smg.graphs.SMGPredicateRelation.SMGValuesPair;
import org.sosy_lab.cpachecker.cpa.smg.graphs.SMGPredicateRelation.SymbolicRelation;
import org.sosy_lab.cpachecker.cpa.smg.graphs.SMGType;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownExpValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownSymbolicValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGZeroValue;
import org.sosy_lab.cpachecker.util.predicates.smt.BitvectorFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BitvectorFormula;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.SolverException;

@Options(prefix = "cpa.smg")
public class SMGPredicateManager {
  @Option(secure=true, name="verifyPredicates", description = "Allow SMG to check predicates")
  private boolean verifyPredicates = false;

  private final Configuration config;
  private final LogManager logger;
  private final Solver solver;
  private final FormulaManagerView fmgr;
  private final BooleanFormulaManagerView bfmgr;
  private final BitvectorFormulaManagerView efmgr;
  private final Map<SMGValue, BitvectorFormula> createdValueFormulas;
  private final Map<SMGValue, SMGType> valueTypes;

  public SMGPredicateManager(Configuration pConfig, LogManager pLogger, ShutdownNotifier
      shutdownNotifier)
      throws InvalidConfigurationException {
    config = pConfig;
    config.inject(this);
    logger = pLogger;
    solver = Solver.create(pConfig, pLogger,shutdownNotifier);
    fmgr = solver.getFormulaManager();
    bfmgr = fmgr.getBooleanFormulaManager();
    efmgr = fmgr.getBitvectorFormulaManager();
    createdValueFormulas = new HashMap<>();
    valueTypes = new HashMap<>();
    valueTypes.put(SMGZeroValue.INSTANCE, new SMGType(1, false));
  }

  private BooleanFormula createBooleanFormula(
      Formula pFormulaOne, Formula pFormulaTwo, BinaryOperator pOp) {
    switch (pOp) {
      case GREATER_THAN:
        return fmgr.makeGreaterThan(pFormulaOne, pFormulaTwo, true);
      case GREATER_EQUAL:
        return fmgr.makeGreaterOrEqual(pFormulaOne, pFormulaTwo, true);
      case LESS_THAN:
        return fmgr.makeLessThan(pFormulaOne, pFormulaTwo, true);
      case LESS_EQUAL:
        return fmgr.makeLessOrEqual(pFormulaOne, pFormulaTwo, true);
      case EQUALS:
        return fmgr.makeEqual(pFormulaOne, pFormulaTwo);
      case NOT_EQUALS:
        return bfmgr.not(fmgr.makeEqual(pFormulaOne, pFormulaTwo));
      default:
        throw new AssertionError();
    }
  }

  private BooleanFormula addPredicateToFormula(
      BooleanFormula pFormula, ExplicitRelation pRelation, boolean conjunction) {
    BooleanFormula result;
    BigInteger explicitValue = pRelation.getExplicitValue().getValue();
    SMGType symbolicSMGType = pRelation.getSymbolicSMGType();

    long explicitSize = symbolicSMGType.getCastedSizeLast();
    boolean isExplicitSigned = symbolicSMGType.getCastedSignedLast();

    BinaryOperator op = pRelation.getOperator();
    BitvectorFormula explicitValueFormula;
    BitvectorFormula explicitValueFormulaCasted;


    if (explicitValue.compareTo(BigInteger.valueOf(0)) < 0) {
      if (explicitValue.abs().compareTo(BigInteger.valueOf(1).shiftLeft((int)(explicitSize - 1))) > 0) {
        symbolicSMGType = new SMGType(new SMGType(64, true), symbolicSMGType);
        explicitSize = 64;
      }
    } else if (isExplicitSigned) {
      if (explicitValue.compareTo(BigInteger.valueOf(1).shiftLeft((int)(explicitSize - 1))) >= 0)  {
        symbolicSMGType = new SMGType(new SMGType(64, true), symbolicSMGType);
        explicitSize = 64;
      }
    } else if (explicitValue.compareTo(BigInteger.valueOf(1).shiftLeft((int)explicitSize)) >= 0) {
      symbolicSMGType = new SMGType(new SMGType(64, true), symbolicSMGType);
      explicitSize = 64;
    }

    explicitValueFormula =
        efmgr.makeBitvector(BigInteger.valueOf(explicitSize + 1).intValueExact(), explicitValue);
    explicitValueFormulaCasted =
        efmgr.extract(explicitValueFormula, BigInteger.valueOf(explicitSize - 1).intValueExact(),
            0, isExplicitSigned);


    BitvectorFormula symbolicValue = getCastedValue(pRelation.getSymbolicValue(), symbolicSMGType);
    result = createBooleanFormula(symbolicValue, explicitValueFormulaCasted, op);

    if (conjunction) {
      result = bfmgr.and(result, pFormula);
    } else {
      result = bfmgr.or(result, pFormula);
    }

    return result;
  }

  /**
   * Method for getting value casted to different types
   *
   * @param pSMGValue value
   * @param pSMGType casting type
   * @return formula with variable for value casted according to pSMGType
   */
  private BitvectorFormula getCastedValue(SMGValue pSMGValue, SMGType pSMGType) {
    BitvectorFormula valueFormula = createdValueFormulas.get(pSMGValue);
    if (valueFormula == null) {
      long size = pSMGType.getOriginSize();
      boolean isSigned = pSMGType.isOriginSigned();
      valueFormula =
          efmgr.makeVariable(BigInteger.valueOf(size).intValueExact(), pSMGValue.toString());
      valueFormula = efmgr.extend(valueFormula, 0, isSigned);
      createdValueFormulas.put(pSMGValue, valueFormula);
      valueTypes.put(pSMGValue, pSMGType);
    }
    return cast(valueFormula, valueTypes.get(pSMGValue), pSMGType);
  }

  private BitvectorFormula cast(
      BitvectorFormula pVariableFormula, SMGType pFromSMGType, SMGType pToSMGType) {
    BitvectorFormula result;
    long fromSize = pFromSMGType.getOriginSize();
    boolean isFromSigned = pFromSMGType.isOriginSigned();
    List<Long> toSize = pToSMGType.getCastedSize();
    toSize.add(0, pToSMGType.getOriginSize());
    List<Boolean> isToSigned = pToSMGType.getCastedSigned();
    isToSigned.add(0, pToSMGType.isOriginSigned());
    result = pVariableFormula;
    for (int i=0;i<toSize.size();i++) {
      if (toSize.get(i) > fromSize) {
        result =
            efmgr.extend(result, BigInteger.valueOf(toSize.get(i) - fromSize).intValueExact(), isToSigned.get(i));
      } else if (toSize.get(i) < fromSize) {
        result =
            efmgr.extract(result, BigInteger.valueOf(toSize.get(i) - 1).intValueExact(), 0, isToSigned.get(i));
      } else if (isToSigned.get(i) != isFromSigned) {
        result = efmgr.extend(result, 0, isToSigned.get(i));
      }
      fromSize = toSize.get(i);
      isFromSigned = isToSigned.get(i);

    }
    return result;
  }

  private BooleanFormula addPredicateToFormula(
      BooleanFormula pFormula, SymbolicRelation pRelation, boolean conjunction) {

    BitvectorFormula formulaOne;
    BitvectorFormula formulaTwo;

    SMGType firstValSMGType = pRelation.getFirstValSMGType();
    List<Long> firstCastedSize = firstValSMGType.getCastedSize();

    SMGType secondValSMGType = pRelation.getSecondValSMGType();
    List<Long> secondCastedSize = secondValSMGType.getCastedSize();

    // Special case for NULL value
    if (pRelation.getFirstValue().isZero()) {
      firstCastedSize = secondCastedSize;
      formulaOne = efmgr.makeBitvector(BigInteger.valueOf(firstCastedSize.get(firstCastedSize.size()-1)).intValueExact(), 0);
    } else {
      formulaOne = getCastedValue(pRelation.getFirstValue(), firstValSMGType);
    }

    if (pRelation.getSecondValue().isZero()) {
      secondCastedSize = firstCastedSize;
      formulaTwo = efmgr.makeBitvector(BigInteger.valueOf(secondCastedSize.get(secondCastedSize.size()-1)).intValueExact(), 0);
    } else {
      formulaTwo = getCastedValue(pRelation.getSecondValue(), secondValSMGType);
    }

    //FIXME: require calculate cast on integer promotions
    if (firstCastedSize.get(firstCastedSize.size()-1) > secondCastedSize.get(secondCastedSize.size()-1)) {
      SMGType firstVALSMGTypeCasted = new SMGType(firstValSMGType.getCastedSize().get(firstValSMGType.getCastedSize().size()-1),
          firstValSMGType.getCastedSigned().get(firstValSMGType.getCastedSigned().size()-1));
      formulaTwo = cast(formulaTwo, secondValSMGType, firstVALSMGTypeCasted);
    }

    if (secondCastedSize.get(secondCastedSize.size()-1) > firstCastedSize.get(firstCastedSize.size()-1)) {
      SMGType secondValSMGTypeCasted = new SMGType(secondValSMGType.getCastedSize().get(secondValSMGType.getCastedSize().size()-1),
          secondValSMGType.getCastedSigned().get(secondValSMGType.getCastedSigned().size()-1));
      formulaOne = cast(formulaOne, firstValSMGType, secondValSMGTypeCasted);
    }

    BinaryOperator op = pRelation.getOperator();
    BooleanFormula result = createBooleanFormula(formulaOne, formulaTwo, op);
    if (conjunction) {
      result = fmgr.makeAnd(pFormula, result);
    } else {
      result = fmgr.makeOr(pFormula, result);
    }

    return result;
  }

  public BooleanFormula getPathPredicateFormula(UnmodifiableSMGState pState) {
    SMGPredicateRelation pRelation = pState.getPathPredicateRelation();
    BooleanFormula predicateFormula = getPredicateFormula(pRelation, true);
    //System.out.format("predicateFormula = %s\npSate = %s\n",predicateFormula, pState);
    //System.out.format("explicitFormulaFromState = %s\n", getExplicitFormulaFromState(pState));
    predicateFormula = fmgr.makeAnd(predicateFormula, getExplicitFormulaFromState(pState));
    //System.out.format("PathPredicateFormula = %s\n", predicateFormula.toString());
    return predicateFormula;
  }

  public BooleanFormula getErrorPredicateFormula(
      SMGPredicateRelation pErrorPredicate, UnmodifiableSMGState pState) {
    BooleanFormula errorFormula = getPredicateFormula(pErrorPredicate, false);
    //System.out.print("errorFormula = ");
    //System.out.println(errorFormula);
    BooleanFormula pathFormula = getPathPredicateFormula(pState);
    //System.out.print("pathFormula = ");
    //System.out.println(pathFormula);
    pathFormula = fmgr.makeAnd(pathFormula, getExplicitFormulaFromState(pState));
    return fmgr.makeAnd(pathFormula, errorFormula);
  }

  private BooleanFormula getExplicitFormulaFromState(UnmodifiableSMGState pState) {
    BooleanFormula result = bfmgr.makeBoolean(true);
    if (!verifyPredicates) {
      return result;
    }
    SMGPredicateRelation errorPredicateRelation = pState.getErrorPredicateRelation();
    SMGPredicateRelation pathPredicateRelation = pState.getPathPredicateRelation();
    for (Entry<SMGKnownSymbolicValue, SMGKnownExpValue> expValueEntry :
        pState.getExplicitValues()) {
      SMGKnownSymbolicValue symbolicValue = expValueEntry.getKey();
      if (errorPredicateRelation.hasRelation(symbolicValue)
          || pathPredicateRelation.hasRelation(symbolicValue)) {
        SMGKnownExpValue explicitValue = expValueEntry.getValue();
        SMGType symbolicType = valueTypes.get(symbolicValue);
        BitvectorFormula valueFormula = getCastedValue(symbolicValue, symbolicType);
        BooleanFormula equality =
            fmgr.makeEqual(
                valueFormula,
                efmgr.makeBitvector(BigInteger.valueOf(symbolicType.getCastedSize().get(symbolicType.getCastedSize().size()-1)).intValueExact(), explicitValue.getValue()));
        result = fmgr.makeAnd(result, equality);
      }
    }
    return result;
  }

  private BooleanFormula getPredicateFormula(SMGPredicateRelation pRelation, boolean conjunction) {
    BooleanFormula result = bfmgr.makeBoolean(conjunction);

    //System.out.format("getPredicateFormula\npRelation= %s\nresult = %s\nconjuction=%b\n",
        //pRelation.toString(), result.toString(), conjunction);

    if (!verifyPredicates) {
      return result;
    }

    for (Entry<SMGValuesPair, ImmutableSet<SymbolicRelation>> entry :
        pRelation.getValuesRelations()) {
      if (entry.getKey().getSecond().compareTo(entry.getKey().getFirst()) >= 0) {
        ImmutableSet<SymbolicRelation> values = entry.getValue();
        for (SymbolicRelation value : values) {
          result = addPredicateToFormula(result, value, conjunction);
        }
      }
    }

    //System.out.format("result = %s\n", result.toString());

    for (ExplicitRelation relation : pRelation.getExplicitRelations()) {
      //System.out.println("CYCLE");
      //System.out.println(relation);
      //System.out.println(result);
      result = addPredicateToFormula(result, relation, conjunction);
    }

    //System.out.format("Out of CYCLE result = %s\n", result.toString());
    return result;
  }

  public boolean isUnsat(BooleanFormula pFormula) throws SolverException, InterruptedException {
    if (verifyPredicates && pFormula != null) {
      boolean result = solver.isUnsat(pFormula);
      if (result) {
        logger.log(Level.FINER, "Unsat: " + pFormula);
      }
      return result;
    } else {
      return false;
    }
  }

  public boolean isErrorPathFeasible(UnmodifiableSMGState pState) {
    if (!verifyPredicates) {
      return false;
    }

    SMGPredicateRelation errorPredicate = pState.getErrorPredicateRelation();
    if (!errorPredicate.isEmpty()) {
      BooleanFormula errorPredicateFormula = getErrorPredicateFormula(errorPredicate, pState);
      try {
        if (!isUnsat(errorPredicateFormula)) {
          logger.log(Level.FINER, "Sat: ", errorPredicateFormula);
          return true;
        } else {
          return false;
        }
      } catch (SolverException pE) {
        logger.log(Level.WARNING, "Solver Exception: " + pE + " on predicate " + errorPredicate);
      } catch (InterruptedException pE) {
        logger.log(Level.WARNING, "Solver Interrupted Exception: " + pE + " on predicate " +
            errorPredicate);
      }
    }

    return !errorPredicate.isEmpty();
  }
}