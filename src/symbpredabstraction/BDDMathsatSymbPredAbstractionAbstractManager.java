package symbpredabstraction;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;

import logging.CPACheckerLogger;
import logging.CustomLogLevel;
import logging.LazyLogger;
import cfa.objectmodel.CFAFunctionDefinitionNode;
import cmdline.CPAMain;

import common.Pair;

import cpa.symbpredabs.AbstractFormula;
import cpa.symbpredabs.ConcreteTraceFunctionCalls;
import cpa.symbpredabs.CounterexampleTraceInfo;
import cpa.symbpredabs.InterpolatingTheoremProver;
import cpa.symbpredabs.Predicate;
import cpa.symbpredabs.SSAMap;
import cpa.symbpredabs.SymbolicFormula;
import cpa.symbpredabs.SymbolicFormulaManager;
import cpa.symbpredabs.TheoremProver;
import cpa.symbpredabs.mathsat.BDDAbstractFormula;
import cpa.symbpredabs.mathsat.BDDMathsatAbstractFormulaManager;
import cpa.symbpredabs.mathsat.MathsatSymbolicFormula;
import cpa.symbpredabs.mathsat.MathsatSymbolicFormulaManager;
import cpa.symbpredabsCPA.SymbPredAbsAbstractElement;

public class BDDMathsatSymbPredAbstractionAbstractManager 
extends BDDMathsatAbstractFormulaManager 
implements SymbPredAbstFormulaManager
{

  public class AllSatCallbackStats extends AllSatCallback
  implements TheoremProver.AllSatCallback {
    public long totTime = 0;
    private long[] curModel;

    public AllSatCallbackStats(int bdd, long msatEnv, long absEnv) {
      super(bdd, msatEnv, absEnv);
      curModel = null;
    }

    @Override
    public void callback(long[] model) {
      long start = System.currentTimeMillis();
      super.callback(model);
      long end = System.currentTimeMillis();
      totTime += (end - start);
    }

    @Override
    public void modelFound(Vector<SymbolicFormula> model) {
      if (curModel == null || curModel.length != model.size()) {
        curModel = new long[model.size()];
      }
      for (int i = 0; i < curModel.length; ++i) {
        long t = ((MathsatSymbolicFormula)model.elementAt(i)).getTerm();
        curModel[i] = t;
      }
      callback(curModel);
    }
  }

  public class Stats {
    public long abstractionMathsatTime = 0;
    public long abstractionMaxMathsatTime = 0;
    public long abstractionBddTime = 0;
    public long abstractionMaxBddTime = 0;
    public int numCallsAbstraction = 0;
    public int numCallsAbstractionCached = 0;
    public long cexAnalysisTime = 0;
    public long cexAnalysisMaxTime = 0;
    public int numCallsCexAnalysis = 0;
    public long abstractionMathsatSolveTime = 0;
    public long abstractionMaxMathsatSolveTime = 0;
    public long cexAnalysisMathsatTime = 0;
    public long cexAnalysisMaxMathsatTime = 0;
    public int numCoverageChecks = 0;
    public long bddCoverageCheckTime = 0;
    public long bddCoverageCheckMaxTime = 0;
    public long cexAnalysisGetUsefulBlocksTime = 0;
    public long cexAnalysisGetUsefulBlocksMaxTime = 0;
  }
  private Stats stats;

  // private boolean dumpHardAbstractions;
  private TheoremProver thmProver;
  private InterpolatingTheoremProver itpProver;

  // TODO later
//private Map<Pair<CFANode, CFANode>, Pair<MathsatSymbolicFormula, SSAMap>>
//abstractionTranslationCache;
//private Map<Pair<SymbolicFormula, Vector<SymbolicFormula>>, AbstractFormula>
//abstractionCache;
//boolean useCache;

//private BDDMathsatSummaryAbstractionPrinter absPrinter = null;
//private boolean dumpHardAbstractions;

  public BDDMathsatSymbPredAbstractionAbstractManager(TheoremProver prover,
                                                      InterpolatingTheoremProver interpolator) 
  {
    super();
    stats = new Stats();
    // TODO cache
//  abstractionTranslationCache =
//  new HashMap<Pair<CFANode, CFANode>,
//  Pair<MathsatSymbolicFormula, SSAMap>>();
    // dumpHardAbstractions = CPAMain.cpaConfig.getBooleanValue("cpas.symbpredabs.mathsat.dumpHardAbstractionQueries");
    thmProver = prover;
    itpProver = interpolator;

    // TODO cache
//  abstractionCache =
//  new HashMap<Pair<SymbolicFormula, Vector<SymbolicFormula>>,
//  AbstractFormula>();
//  useCache = CPAMain.cpaConfig.getBooleanValue(
//  "cpas.symbpredabs.mathsat.useCache");
  }

  public Stats getStats() { return stats; }

  @Override
  public AbstractFormula buildAbstraction(SymbolicFormulaManager mgr,
                                          AbstractFormula abs, PathFormula pathFormula,
                                          Collection<Predicate> predicates, MathsatSymbolicFormula functionExitFormula) {
    stats.numCallsAbstraction++;
    return buildBooleanAbstraction(mgr, abs, pathFormula, predicates, functionExitFormula);
  }

  private AbstractFormula buildBooleanAbstraction(SymbolicFormulaManager mgr,
                                                  AbstractFormula abs, PathFormula pathFormula,
                                                  Collection<Predicate> predicates, MathsatSymbolicFormula functionExitFormula) {
    // A SummaryFormulaManager for MathSAT formulas
    MathsatSymbolicFormulaManager mmgr = (MathsatSymbolicFormulaManager)mgr;

    long startTime = System.currentTimeMillis();

    // get the environment from the manager - this is unique, it is the
    // environment in which all terms are created
    long msatEnv = mmgr.getMsatEnv();

    // first, build the concrete representation of the abstract formula of e
    // this is an abstract formula - specifically it is a bddabstractformula
    // which is basically an integer which represents it
    //AbstractFormula abs = element.getAbstraction();
    // create the concrete form of the abstract formula
    // (abstract formula is the bdd representation)
    MathsatSymbolicFormula fabs =
      (MathsatSymbolicFormula)mmgr.instantiate(
          toConcrete(mmgr, abs), null);

    LazyLogger.log(LazyLogger.DEBUG_3, "Abstraction: ",
        mathsat.api.msat_term_id(fabs.getTerm()));

    if (functionExitFormula != null) {
      if (CPAMain.cpaConfig.getBooleanValue(
      "cpas.symbpredabs.refinement.addWellScopedPredicates")) {
        // but only if we are adding well-scoped predicates, otherwise
        // this should not be necessary
        fabs = (MathsatSymbolicFormula)mmgr.makeAnd(fabs, functionExitFormula);

        LazyLogger.log(LazyLogger.DEBUG_3,
            "TAKING CALLING CONTEXT INTO ACCOUNT: ", functionExitFormula);
      } else {
        LazyLogger.log(LazyLogger.DEBUG_3,
            "NOT TAKING CALLING CONTEXT INTO ACCOUNT,",
        "as we are not using well-scoped predicates");
      }
    }

    // create an ssamap from concrete formula
    SSAMap absSsa = mmgr.extractSSA(fabs);

    SymbolicFormula f = null;
    SSAMap ssa = null;

    // TODO cache operations
//  Pair<CFANode, CFANode> key = new Pair<CFANode, CFANode>(
//  e.getLocationNode(), succ.getLocationNode());
//  if (abstractionTranslationCache.containsKey(key)) {
//  Pair<MathsatSymbolicFormula, SSAMap> pc =
//  abstractionTranslationCache.get(key);
//  f = pc.getFirst();
//  ssa = pc.getSecond();
//  } else {
//  // take all outgoing edges from e to succ and OR them
//  Pair<SymbolicFormula, SSAMap> pc =
//  buildConcreteFormula(mmgr, e, succ, false);
////SymbolicFormula f = pc.getFirst();
////SSAMap ssa = pc.getSecond();
    f = pathFormula.getSymbolicFormula();
    ssa = pathFormula.getSsa();

    pathFormula = toPathFormula(mmgr.shift(f, absSsa));
    f = mmgr.replaceAssignments((MathsatSymbolicFormula)pathFormula.getSymbolicFormula());
    ssa = pathFormula.getSsa();

    // TODO cache
//  abstractionTranslationCache.put(key,
//  new Pair<MathsatSymbolicFormula, SSAMap>(
//  (MathsatSymbolicFormula)f, ssa));
//  }

    if (CPAMain.cpaConfig.getBooleanValue(
    "cpas.symbpredabs.useBitwiseAxioms")) {
      MathsatSymbolicFormula bitwiseAxioms = mmgr.getBitwiseAxioms(
          (MathsatSymbolicFormula)f);
      f = mmgr.makeAnd(f, bitwiseAxioms);

      LazyLogger.log(LazyLogger.DEBUG_3, "ADDED BITWISE AXIOMS: ",
          bitwiseAxioms);
    }

    long term = ((MathsatSymbolicFormula)f).getTerm();
    assert(!mathsat.api.MSAT_ERROR_TERM(term));

    LazyLogger.log(LazyLogger.DEBUG_2, "Term: ", f);


    // build the definition of the predicates, and instantiate them
    PredInfo predinfo = buildPredList(mmgr, predicates);
    long preddef = predinfo.predDef;
    long[] important = predinfo.important;
    Collection<String> predvars = predinfo.allVars;
    Collection<Pair<String, SymbolicFormula[]>> predlvals =
      predinfo.allFuncs;
    // update the SSA map, by instantiating all the uninstantiated
    // variables that occur in the predicates definitions (at index 1)
    for (String var : predvars) {
      if (ssa.getIndex(var) < 0) {
        ssa.setIndex(var, 1);
      }
    }
    Map<SymbolicFormula, SymbolicFormula> cache =
      new HashMap<SymbolicFormula, SymbolicFormula>();
    for (Pair<String, SymbolicFormula[]> p : predlvals) {
      SymbolicFormula[] args =
        getInstantiatedAt(mmgr, p.getSecond(), ssa, cache);
      if (ssa.getIndex(p.getFirst(), args) < 0) {
        ssa.setIndex(p.getFirst(), args, 1);
      }
    }

    if (CPACheckerLogger.getLevel() <= LazyLogger.DEBUG_1.intValue()) {
      StringBuffer importantStrBuf = new StringBuffer();
      for (long t : important) {
        importantStrBuf.append(mathsat.api.msat_term_repr(t));
        importantStrBuf.append(" ");
      }
      LazyLogger.log(LazyLogger.DEBUG_1,
          "IMPORTANT SYMBOLS (", important.length, "): ",
          importantStrBuf);
    }

    // first, create the new formula corresponding to
    // (f & edges from e to succ)
    // TODO - at the moment, we assume that all the edges connecting e and
    // succ have no statement or assertion attached (i.e. they are just
    // return edges or gotos). This might need to change in the future!!
    // (So, for now we don't need to to anything...)

    // instantiate the definitions with the right SSA
    MathsatSymbolicFormula inst = (MathsatSymbolicFormula)mmgr.instantiate(
        new MathsatSymbolicFormula(preddef), ssa);
    preddef = inst.getTerm();
    long curstate = fabs.getTerm();

    // the formula is (curstate & term & preddef)
    // build the formula and send it to the absEnv
    long formula = mathsat.api.msat_make_and(msatEnv,
        mathsat.api.msat_make_and(msatEnv, curstate, term), preddef);
    SymbolicFormula fm = new MathsatSymbolicFormula(formula);
    Vector<SymbolicFormula> imp = new Vector<SymbolicFormula>();
    imp.ensureCapacity(important.length);
    for (long p : important) {
      imp.add(new MathsatSymbolicFormula(p));
    }

    LazyLogger.log(LazyLogger.DEBUG_2,
        "COMPUTING ALL-SMT ON FORMULA: ", fm);

    // Pair<SymbolicFormula, Vector<SymbolicFormula>> absKey =
    //   new Pair<SymbolicFormula, Vector<SymbolicFormula>>(fm, imp);
    AbstractFormula result = null;
    // TODO cache
//  if (useCache && abstractionCache.containsKey(absKey)) {
    if(false){
//    ++stats.numCallsAbstractionCached;
//    result = abstractionCache.get(absKey);
    } else {
      int absbdd = bddManager.getZero();
      AllSatCallbackStats func =
        new AllSatCallbackStats(absbdd, msatEnv, 0);
      long msatSolveStartTime = System.currentTimeMillis();
      int numModels = thmProver.allSat(fm, imp, func);
      assert(numModels != -1);
      long msatSolveEndTime = System.currentTimeMillis();

      // update statistics
      long endTime = System.currentTimeMillis();
      long msatSolveTime =
        (msatSolveEndTime - msatSolveStartTime) - func.totTime;
      long abstractionMsatTime = (endTime - startTime) - func.totTime;
      stats.abstractionMaxMathsatTime =
        Math.max(abstractionMsatTime, stats.abstractionMaxMathsatTime);
      stats.abstractionMaxBddTime =
        Math.max(func.totTime, stats.abstractionMaxBddTime);
      stats.abstractionMathsatTime += abstractionMsatTime;
      stats.abstractionBddTime += func.totTime;
      stats.abstractionMathsatSolveTime += msatSolveTime;
      stats.abstractionMaxMathsatSolveTime =
        Math.max(msatSolveTime, stats.abstractionMaxMathsatSolveTime);

      // TODO dump hard abst
//    if (abstractionMsatTime > 10000 && dumpHardAbstractions) {
//    // we want to dump "hard" problems...
//    if (absPrinter == null) {
//    absPrinter = new BDDMathsatSummaryAbstractionPrinter(
//    msatEnv, "abs");
//    }
//    absPrinter.printMsatFormat(curstate, term, preddef, important);
//    absPrinter.printNusmvFormat(curstate, term, preddef, important);
//    absPrinter.nextNum();
//    }

      if (numModels == -2) {
        absbdd = bddManager.getOne();
        //return new BDDAbstractFormula(absbdd);
        result = new BDDAbstractFormula(absbdd);
      } else {
        //return new BDDAbstractFormula(func.getBDD());
        result = new BDDAbstractFormula(func.getBDD());
      }
      // TODO later
//    if (useCache) {
//    abstractionCache.put(absKey, result);
//    }
    }

    return result;
  }

  @Override
  public CounterexampleTraceInfo buildCounterexampleTrace(
     SymbolicFormulaManager mgr,
     Deque<SymbPredAbsAbstractElement> abstractTrace) {
    assert(abstractTrace.size() > 1);

    long startTime = System.currentTimeMillis();
    stats.numCallsCexAnalysis++;

    // create the DAG formula corresponding to the abstract trace. We create
    // n formulas, one per interpolation group
    SSAMap ssa = null;
    MathsatSymbolicFormulaManager mmgr = (MathsatSymbolicFormulaManager)mgr;

    Vector<SymbolicFormula> f = new Vector<SymbolicFormula>();

    LazyLogger.log(LazyLogger.DEBUG_1, "\nBUILDING COUNTEREXAMPLE TRACE\n");
    LazyLogger.log(LazyLogger.DEBUG_1, "ABSTRACT TRACE: ", abstractTrace);

    //printFuncNamesInTrace(abstractTrace);

    Object[] abstarr = abstractTrace.toArray();
    SymbPredAbsAbstractElement cur = (SymbPredAbsAbstractElement)abstarr[0];

    boolean theoryCombinationNeeded = false;
    boolean noDtc = CPAMain.cpaConfig.getBooleanValue(
    "cpas.symbpredabs.mathsat.useDtc") == false;

    MathsatSymbolicFormula bitwiseAxioms =
      (MathsatSymbolicFormula)mmgr.makeTrue();
    for (int i = 1; i < abstarr.length; ++i) {
      SymbPredAbsAbstractElement e = (SymbPredAbsAbstractElement)abstarr[i];
      // TODO here we take the formula from the abstract element
//      Pair<SymbolicFormula, SSAMap> p =
//        buildConcreteFormula(mmgr, cur, e, (ssa == null));
      PathFormula p = e.getInitAbstractionSet().getInitSymbolicFormula(mgr, (ssa == null));
      SSAMap newssa = null;
      if (ssa != null) {
        LazyLogger.log(LazyLogger.DEBUG_3, "SHIFTING: ", p.getSymbolicFormula(),
            " WITH SSA: ", ssa);
        p = toPathFormula(mmgr.shift(p.getSymbolicFormula(), ssa));
        newssa = p.getSsa();
        LazyLogger.log(LazyLogger.DEBUG_3, "RESULT: ", p.getSymbolicFormula(),
            " SSA: ", newssa);
        newssa.update(ssa);
      } else {
        LazyLogger.log(LazyLogger.DEBUG_3, "INITIAL: ", p.getSymbolicFormula(),
            " SSA: ", p.getSsa());
        newssa = p.getSsa();
      }
      boolean hasUf = false;
      if (!noDtc) {
        hasUf = mmgr.hasUninterpretedFunctions(
            (MathsatSymbolicFormula)p.getSymbolicFormula());
        theoryCombinationNeeded |= hasUf;
      }
      f.add(p.getSymbolicFormula());
      ssa = newssa;
      cur = e;

      if (hasUf && CPAMain.cpaConfig.getBooleanValue(
          "cpas.symbpredabs.useBitwiseAxioms")) {
        MathsatSymbolicFormula a = mmgr.getBitwiseAxioms(
            (MathsatSymbolicFormula)p.getSymbolicFormula());
        bitwiseAxioms = (MathsatSymbolicFormula)mmgr.makeAnd(
            bitwiseAxioms, a);
      }

      LazyLogger.log(LazyLogger.DEBUG_2, "Adding formula: ", p.getSymbolicFormula());
//    mathsat.api.msat_term_id(
//    ((MathsatSymbolicFormula)p.getFirst()).getTerm()));
    }

    if (CPAMain.cpaConfig.getBooleanValue(
    "cpas.symbpredabs.useBitwiseAxioms")) {
      LazyLogger.log(LazyLogger.DEBUG_3, "ADDING BITWISE AXIOMS TO THE ",
          "LAST GROUP: ", bitwiseAxioms);
      f.setElementAt(mmgr.makeAnd(f.elementAt(f.size()-1), bitwiseAxioms),
          f.size()-1);
    }

    LazyLogger.log(LazyLogger.DEBUG_3,
    "Checking feasibility of abstract trace");

    // now f is the DAG formula which is satisfiable iff there is a
    // concrete counterexample
    //
    // create a working environment
    itpProver.init();

    boolean shortestTrace = CPAMain.cpaConfig.getBooleanValue(
    "cpas.symbpredabs.shortestCexTrace");
    boolean useSuffix = CPAMain.cpaConfig.getBooleanValue(
    "cpas.symbpredabs.shortestCexTraceUseSuffix");
    boolean useZigZag = CPAMain.cpaConfig.getBooleanValue(
    "cpas.symbpredabs.shortestCexTraceZigZag");

    long msatSolveTimeStart = System.currentTimeMillis();

    boolean unsat = false;
    int res = -1;

    //dumpInterpolationProblem(mmgr, f, "itp");

    if (shortestTrace && CPAMain.cpaConfig.getBooleanValue(
    "cpas.symbpredabs.explicit.getUsefulBlocks")) {
      long gubStart = System.currentTimeMillis();
      f = getUsefulBlocks(mmgr, f, theoryCombinationNeeded,
          useSuffix, useZigZag, false);
      long gubEnd = System.currentTimeMillis();
      stats.cexAnalysisGetUsefulBlocksTime += gubEnd - gubStart;
      stats.cexAnalysisGetUsefulBlocksMaxTime = Math.max(
          stats.cexAnalysisGetUsefulBlocksMaxTime, gubEnd - gubStart);
      // set shortestTrace to false, so we perform only one final call
      // to msat_solve
      shortestTrace = false;
    }


    if (!shortestTrace || !useZigZag) {
      for (int i = useSuffix ? f.size()-1 : 0;
      useSuffix ? i >= 0 : i < f.size(); i += useSuffix ? -1 : 1) {
        SymbolicFormula fm = f.elementAt(i);
        itpProver.addFormula(fm);
        if (shortestTrace && !fm.isTrue()) {
          if (itpProver.isUnsat()) {
            res = 0;
            // we need to add the other formulas to the itpProver
            // anyway, so it can setup its internal state properly
            for (int j = i+(useSuffix ? -1 : 1);
            useSuffix ? j >= 0 : j < f.size();
            j += useSuffix ? -1 : 1) {
              itpProver.addFormula(f.elementAt(j));
            }
            break;
          } else {
            res = 1;
          }
        } else {
          res = -1;
        }
      }
      if (!shortestTrace || res == -1) {
        unsat = itpProver.isUnsat();
      } else {
        unsat = res == 0;
      }
    } else { // shortestTrace && useZigZag
      int e = f.size()-1;
      int s = 0;
      boolean fromStart = false;
      while (true) {
        int i = fromStart ? s : e;
        if (fromStart) s++;
        else e--;
        fromStart = !fromStart;
        SymbolicFormula fm = f.elementAt(i);
        itpProver.addFormula(fm);
        if (!fm.isTrue()) {
          if (itpProver.isUnsat()) {
            res = 0;
            for (int j = s; j <= e; ++j) {
              itpProver.addFormula(f.elementAt(j));
            }
            break;
          } else {
            res = 1;
          }
        } else {
          res = -1;
        }
        if (s > e) break;
      }
      assert(res != -1);
      unsat = res == 0;
    }

    long msatSolveTimeEnd = System.currentTimeMillis();
    long msatSolveTime = msatSolveTimeEnd - msatSolveTimeStart;

    CounterexampleTraceInfo info = null;

    long msatEnv = mmgr.getMsatEnv();

    if (unsat) {
      //dumpInterpolationProblem(mmgr, f, "itp");
      // the counterexample is spurious. Extract the predicates from
      // the interpolants
      info = new CounterexampleTraceInfo(true);
      boolean splitItpAtoms = CPAMain.cpaConfig.getBooleanValue(
      "cpas.symbpredabs.refinement.splitItpAtoms");
      // how to partition the trace into (A, B) depends on whether
      // there are function calls involved or not: in general, A
      // is the trace from the entry point of the current function
      // to the current point, and B is everything else. To implement
      // this, we keep track of which function we are currently in.
      Stack<Integer> entryPoints = new Stack<Integer>();
      entryPoints.push(0);
      for (int i = 1; i < f.size(); ++i) {
        int start_of_a = entryPoints.peek();
        if (!CPAMain.cpaConfig.getBooleanValue(
            "cpas.symbpredabs.refinement.addWellScopedPredicates")) {
          // if we don't want "well-scoped" predicates, we always
          // cut from the beginning
          start_of_a = 0;
        }

        int sz = i - start_of_a;
        Vector<SymbolicFormula> formulasOfA =
          new Vector<SymbolicFormula>();
        formulasOfA.ensureCapacity(sz);
        for (int j = 0; j < sz; ++j) {
          formulasOfA.add(f.elementAt(j+start_of_a));
        }
        msatSolveTimeStart = System.currentTimeMillis();
        SymbolicFormula itp = itpProver.getInterpolant(formulasOfA);
        msatSolveTimeEnd = System.currentTimeMillis();
        msatSolveTime += msatSolveTimeEnd - msatSolveTimeStart;

        Collection<SymbolicFormula> atoms = mmgr.extractAtoms(
            itp, true, splitItpAtoms, false);
        Set<Predicate> preds = buildPredicates(msatEnv, atoms);
        SymbPredAbsAbstractElement s1 =
          (SymbPredAbsAbstractElement)abstarr[i];
        info.addPredicatesForRefinement(s1, preds);

        LazyLogger.log(LazyLogger.DEBUG_1,
            "Got interpolant(", i, "): ", itp, ", location: ", s1);
        LazyLogger.log(LazyLogger.DEBUG_1, "Preds for ",
            s1.getAbstractionLocation(), ": ", preds);

        // If we are entering or exiting a function, update the stack
        // of entry points
        SymbPredAbsAbstractElement e = (SymbPredAbsAbstractElement)abstarr[i];
        // TODO checking if the abstraction node is a new function
        if (e.getAbstractionLocation() instanceof CFAFunctionDefinitionNode) {
          LazyLogger.log(LazyLogger.DEBUG_3,
              "Pushing entry point, function: ",
              e.getAbstractionLocation().getFunctionName());
          entryPoints.push(i);
        }
        // TODO check we are returning from a function
        if (e.getAbstractionLocation().getEnteringSummaryEdge() != null) {
          LazyLogger.log(LazyLogger.DEBUG_3,
              "Popping entry point, returning from function: ",
              e.getAbstractionLocation().getEnteringEdge(0).getPredecessor().getFunctionName());
          entryPoints.pop();

//        SummaryAbstractElement s1 =
//        (SummaryAbstractElement)abstarr[i];
          //pmap.update((CFANode)s1.getLocation(), preds);
        }
      }
    } else {
      // this is a real bug, notify the user
      info = new CounterexampleTraceInfo(false);
      ConcreteTraceFunctionCalls cf = new ConcreteTraceFunctionCalls();
      for (SymbPredAbsAbstractElement e : abstractTrace) {
        cf.add(e.getAbstractionLocation().getFunctionName());
      }
      info.setConcreteTrace(cf);
      // TODO - reconstruct counterexample
      // For now, we dump the asserted formula to a user-specified file
      String cexPath = CPAMain.cpaConfig.getProperty(
      "cpas.symbpredabs.refinement.msatCexPath");
      if (cexPath != null) {
        long t = mathsat.api.msat_make_true(msatEnv);
        for (SymbolicFormula fm : f) {
          long term = ((MathsatSymbolicFormula)fm).getTerm();
          t = mathsat.api.msat_make_and(msatEnv, t, term);
        }
        String msatRepr = mathsat.api.msat_to_msat(msatEnv, t);
        try {
          PrintWriter pw = new PrintWriter(new File(cexPath));
          pw.println(msatRepr);
          pw.close();
        } catch (FileNotFoundException e) {
          LazyLogger.log(CustomLogLevel.INFO,
              "Failed to save msat Counterexample to file: ",
              cexPath);
        }
      }
    }

    itpProver.reset();

    // update stats
    long endTime = System.currentTimeMillis();
    long totTime = endTime - startTime;
    stats.cexAnalysisTime += totTime;
    stats.cexAnalysisMaxTime = Math.max(totTime, stats.cexAnalysisMaxTime);
    stats.cexAnalysisMathsatTime += msatSolveTime;
    stats.cexAnalysisMaxMathsatTime =
      Math.max(msatSolveTime, stats.cexAnalysisMaxMathsatTime);

    return info;
  }

  // generates the predicates corresponding to the given atoms, which were
  // extracted from the interpolant
  private Set<Predicate> buildPredicates(long dstenv,
      Collection<SymbolicFormula> atoms) {
    Set<Predicate> ret = new HashSet<Predicate>();
    for (SymbolicFormula atom : atoms) {
      long tt = ((MathsatSymbolicFormula)atom).getTerm();
      long d = mathsat.api.msat_declare_variable(dstenv,
          "\"PRED" + mathsat.api.msat_term_repr(tt) + "\"",
          mathsat.api.MSAT_BOOL);
      long var = mathsat.api.msat_make_variable(dstenv, d);

      assert(!mathsat.api.MSAT_ERROR_TERM(tt));
      assert(!mathsat.api.MSAT_ERROR_TERM(var));

      ret.add(makePredicate(var, tt));
    }
    return ret;
  }

  @Override
  public boolean entails(AbstractFormula f1, AbstractFormula f2) {
    long start = System.currentTimeMillis();
    boolean ret = super.entails(f1, f2);
    long end = System.currentTimeMillis();
    stats.bddCoverageCheckMaxTime = Math.max(stats.bddCoverageCheckMaxTime,
        (end - start));
    stats.bddCoverageCheckTime += (end - start);
    ++stats.numCoverageChecks;
    return ret;
  }

  public Vector<SymbolicFormula> getUsefulBlocks(
      SymbolicFormulaManager mgr, Vector<SymbolicFormula> f,
      boolean theoryCombinationNeeded, boolean suffixTrace,
      boolean zigZag, boolean setAllTrueIfSat) {
    // try to find a minimal-unsatisfiable-core of the trace (as Blast does)
    MathsatSymbolicFormulaManager mmgr =
      (MathsatSymbolicFormulaManager)mgr;

    long msatEnv = mmgr.getMsatEnv();
    thmProver.init(TheoremProver.COUNTEREXAMPLE_ANALYSIS);

    LazyLogger.log(LazyLogger.DEBUG_1, "Calling getUsefulBlocks on path ",
        "of length: ", f.size());

    MathsatSymbolicFormula trueFormula = new MathsatSymbolicFormula(
        mathsat.api.msat_make_true(msatEnv));
    MathsatSymbolicFormula[] needed = new MathsatSymbolicFormula[f.size()];
    for (int i = 0; i < needed.length; ++i) {
      needed[i] = trueFormula;
    }
    int pos = suffixTrace ? f.size()-1 : 0;
    int incr = suffixTrace ? -1 : 1;
    int toPop = 0;

    while (true) {
      boolean consistent = true;
      // 1. assert all the needed constraints
      for (int i = 0; i < needed.length; ++i) {
        if (!needed[i].isTrue()) {
          thmProver.push(needed[i]);
          ++toPop;
        }
      }
      // 2. if needed is inconsistent, then return it
      if (thmProver.isUnsat(trueFormula)) {
        f = new Vector<SymbolicFormula>();
        for (int i = 0; i < needed.length; ++i) {
          f.add(needed[i]);
        }
        break;
      }
      // 3. otherwise, assert one block at a time, until we get an
      // inconsistency
      if (zigZag) {
        int s = 0;
        int e = f.size()-1;
        boolean fromStart = false;
        while (true) {
          int i = fromStart ? s : e;
          if (fromStart) ++s;
          else --e;
          fromStart = !fromStart;

          MathsatSymbolicFormula t =
            (MathsatSymbolicFormula)f.elementAt(i);
          thmProver.push(t);
          ++toPop;
          if (thmProver.isUnsat(trueFormula)) {
            // add this block to the needed ones, and repeat
            needed[i] = t;
            LazyLogger.log(LazyLogger.DEBUG_1,
                "Found needed block: ", i, ", term: ", t);
            // pop all
            while (toPop > 0) {
              --toPop;
              thmProver.pop();
            }
            // and go to the next iteration of the while loop
            consistent = false;
            break;
          }

          if (e < s) break;
        }
      } else {
        for (int i = pos; suffixTrace ? i >= 0 : i < f.size();
        i += incr) {
          MathsatSymbolicFormula t =
            (MathsatSymbolicFormula)f.elementAt(i);
          thmProver.push(t);
          ++toPop;
          if (thmProver.isUnsat(trueFormula)) {
            // add this block to the needed ones, and repeat
            needed[i] = t;
            LazyLogger.log(LazyLogger.DEBUG_1,
                "Found needed block: ", i, ", term: ", t);
            // pop all
            while (toPop > 0) {
              --toPop;
              thmProver.pop();
            }
            // and go to the next iteration of the while loop
            consistent = false;
            break;
          }
        }
      }
      if (consistent) {
        // if we get here, the trace is consistent:
        // this is a real counterexample!
        if (setAllTrueIfSat) {
          f = new Vector<SymbolicFormula>();
          for (int i = 0; i < needed.length; ++i) {
            f.add(trueFormula);
          }
        }
        break;
      }
    }

    while (toPop > 0) {
      --toPop;
      thmProver.pop();
    }

    thmProver.reset();

    LazyLogger.log(LazyLogger.DEBUG_1, "Done getUsefulBlocks");

    return f;
  }

  private PathFormula toPathFormula(Pair<SymbolicFormula, SSAMap> pair) {
    return new PathFormula(pair.getFirst(), pair.getSecond());
  }

}
