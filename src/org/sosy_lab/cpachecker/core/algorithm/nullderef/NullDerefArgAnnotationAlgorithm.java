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
package org.sosy_lab.cpachecker.core.algorithm.nullderef;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.util.AbstractStates.IS_TARGET_STATE;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.Paths;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.types.c.CFunctionType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.core.CPABuilder;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.AssumptionCollectorAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.CEGARAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.ExternalCBMCAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.RestrictedProgramDomainAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.bmc.BMCAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.counterexamplecheck.CounterexampleCheckAlgorithm;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.resources.ResourceLimitChecker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

@Options(prefix="nullDerefArgAnnotationAlgorithm")
public class NullDerefArgAnnotationAlgorithm implements Algorithm, StatisticsProvider {

  private static final Splitter CONFIG_FILE_CONDITION_SPLITTER = Splitter.on("::").trimResults().limit(2);

  private static class NullDerefArgAnnotationAlgorithmStatistics implements Statistics {

    private final int noOfAlgorithms;
    private final Collection<Statistics> subStats;
    private int noOfAlgorithmsUsed = 0;
    private Timer totalTime = new Timer();

    public NullDerefArgAnnotationAlgorithmStatistics(int pNoOfAlgorithms) {
      noOfAlgorithms = pNoOfAlgorithms;
      subStats = new ArrayList<>();
    }

    public Collection<Statistics> getSubStatistics() {
      return subStats;
    }

    public void resetSubStatistics() {
      subStats.clear();
      totalTime = new Timer();
    }

    @Override
    public String getName() {
      return "Restart Algorithm";
    }

    private void printIntermediateStatistics(PrintStream out, Result result,
        ReachedSet reached) {

      String text = "Statistics for algorithm " + noOfAlgorithmsUsed + " of " + noOfAlgorithms;
      out.println(text);
      out.println(Strings.repeat("=", text.length()));

      printSubStatistics(out, result, reached);
      out.println();
    }

    @Override
    public void printStatistics(PrintStream out, Result result,
        ReachedSet reached) {

      out.println("Number of algorithms provided:    " + noOfAlgorithms);
      out.println("Number of algorithms used:        " + noOfAlgorithmsUsed);

      printSubStatistics(out, result, reached);
    }

    private void printSubStatistics(PrintStream out, Result result, ReachedSet reached) {
      out.println("Total time for algorithm " + noOfAlgorithmsUsed + ": " + totalTime);

      for (Statistics s : subStats) {
        String name = s.getName();
        if (!isNullOrEmpty(name)) {
          name = name + " statistics";
          out.println("");
          out.println(name);
          out.println(Strings.repeat("-", name.length()));
        }
        s.printStatistics(out, result, reached);
      }
    }

  }

  private static class ParameterDerefAnnotation {
    public String name;
    public Boolean isPointer;
    public Boolean mayBeDereferenced;
    public Boolean mustBeDereferenced;

    public ParameterDerefAnnotation(String pName, Boolean pIsPointer, Boolean pMayBeDereferenced, Boolean pMustBeDereferenced) {
      name = pName;
      isPointer = pIsPointer;
      mayBeDereferenced = pMayBeDereferenced;
      mustBeDereferenced = pMustBeDereferenced;
    }

    @Override
    public String toString() {
      if (isPointer) {
        return "Param *" + name + "(may deref: " + mayBeDereferenced + ", must deref: " + mustBeDereferenced + ")";
      } else {
        return "Param " + name;
      }
    }
  }

  private static class FunctionDerefAnnotation {
    public String name;
    public String retType;
    public Boolean retTypeIsPointer;
    public Boolean retMayBeNull;
    public ArrayList<ParameterDerefAnnotation> parameterAnnotations;

    public FunctionDerefAnnotation(String pName, String pRetType) {
      name = pName;
      retType = pRetType;
      parameterAnnotations = new ArrayList<>();
      retTypeIsPointer = false;
      retMayBeNull = false;
    }
  }

  private static class FunctionPlan {
    public String name;
    public ArrayList<Pair<String, String>> dependencies;

    public FunctionPlan(String pName) {
      name = pName;
      dependencies = new ArrayList<>();
    }
  }

  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final NullDerefArgAnnotationAlgorithmStatistics stats;
  private final String filename;
  private CFA cfa;
  private final Configuration globalConfig;
  private String objectFile;
  private List<FunctionPlan> functionPlans;
  private Map<String, FunctionDerefAnnotation> functionAnnotations;
  private Map<String, Map<String, FunctionDerefAnnotation>> otherObjectFileFunctionAnnotations;

  @Option(secure = true, name = "plan", description = "Path to file with analysis plan")
  private String planPath;

  @Option(secure = true, name = "annotationDirectory", description = "Path to annotation directory root")
  private String annotationDirectory;

  @Option(secure = true, name = "distinctTempSpecNames", description = "Use distinct names for all temporary spec files")
  private boolean distinctTempSpecNames = false;

  private Algorithm currentAlgorithm;

  public NullDerefArgAnnotationAlgorithm(Configuration config, LogManager pLogger,
      ShutdownNotifier pShutdownNotifier, String pFilename, CFA pCfa) throws InvalidConfigurationException {
    config.inject(this);

    this.stats = new NullDerefArgAnnotationAlgorithmStatistics(1);
    this.logger = pLogger;
    this.shutdownNotifier = pShutdownNotifier;
    this.filename = pFilename;
    this.cfa = pCfa;
    this.globalConfig = config;
    this.functionAnnotations = new HashMap<>();
    this.otherObjectFileFunctionAnnotations = new HashMap<>();
  }

  private void loadPlan() {
    functionPlans = new ArrayList<>();

    try (BufferedReader br = new BufferedReader(new FileReader(planPath))) {
      String line;
      FunctionPlan functionPlan = null;

      while ((line = br.readLine()) != null) {
        String[] parts = line.split(" ");

        if (parts[0].equals("FILE")) {
          objectFile = parts[1];
        } else if (parts[0].equals("FUNCTION")) {
          functionPlan = new FunctionPlan(parts[1]);
          functionPlans.add(functionPlan);
        } else if (parts[0].equals("CALLS")) {
          functionPlan.dependencies.add(Pair.of(parts[1], parts[2]));
        }
      }
  } catch (IOException e) {
    // TODO: ???
    e.printStackTrace();
  }

  }

  private String getAnnotationFilePath(String pObjectFile) {
    return Paths.get(annotationDirectory, pObjectFile, "deref_annotation.txt").toString();
  }

  private FunctionDerefAnnotation getFunctionAnnotation(String pDependencyName, String pDependencyObjectFile) {
    if (pDependencyObjectFile.equals(objectFile)) {
      return functionAnnotations.get(pDependencyName);
    } else if (otherObjectFileFunctionAnnotations.containsKey(pDependencyObjectFile)) {
      return otherObjectFileFunctionAnnotations.get(pDependencyObjectFile).get(pDependencyName);
    }

    HashMap<String, FunctionDerefAnnotation> otherFunctionAnnotations = new HashMap<>();

    try (BufferedReader br = new BufferedReader(new FileReader(getAnnotationFilePath(pDependencyObjectFile)))) {
      String line;
      FunctionDerefAnnotation annotation = null;

      while ((line = br.readLine()) != null) {
        String[] parts = line.split(" ");

        if (parts[0].equals("FUNCTION")) {
          annotation = new FunctionDerefAnnotation(parts[1], br.readLine());
          otherFunctionAnnotations.put(parts[1], annotation);
        } else if (parts[0].equals("PARAM")) {
          annotation.parameterAnnotations.add(new ParameterDerefAnnotation(
              parts[1], Boolean.parseBoolean(parts[2]),
              Boolean.parseBoolean(parts[3]), Boolean.parseBoolean(parts[4])));
        } else if (parts[0].equals("RET")) {
          annotation.retTypeIsPointer = Boolean.parseBoolean(parts[1]);
          annotation.retMayBeNull = Boolean.parseBoolean(parts[2]);
        }
      }
    } catch (IOException e) {
      logger.log(Level.INFO, "Could not read annotations from " + getAnnotationFilePath(pDependencyObjectFile));
    }

    otherObjectFileFunctionAnnotations.put(pDependencyObjectFile, otherFunctionAnnotations);
    return otherFunctionAnnotations.get(pDependencyName);
  }

  private void saveAnnotations() {
    String fileName = getAnnotationFilePath(objectFile);
    (new File(fileName)).getParentFile().mkdirs();

    try (PrintWriter writer = new PrintWriter(fileName)) {
      writer.println("FILE " + objectFile);

      for (FunctionDerefAnnotation functionAnnotation : functionAnnotations.values()) {
        writer.println("FUNCTION " + functionAnnotation.name);
        writer.println(functionAnnotation.retType);
        writer.println("RET " + functionAnnotation.retTypeIsPointer + " " + functionAnnotation.retMayBeNull);

        for (ParameterDerefAnnotation parameterAnnotation: functionAnnotation.parameterAnnotations) {
          writer.println("PARAM " + parameterAnnotation.name + " " + parameterAnnotation.isPointer +
              " " + parameterAnnotation.mayBeDereferenced + " " + parameterAnnotation.mustBeDereferenced);
        }
      }

      writer.close();
    } catch (FileNotFoundException e) {
      // TODO: ???
      e.printStackTrace();
    }
  }

  @Override
  public AlgorithmStatus run(ReachedSet pReached) throws CPAException, InterruptedException {
    loadPlan();
    logger.log(Level.INFO, "Analysing object file " + objectFile);

    for (FunctionPlan plan : functionPlans) {
      runFunction(plan);
      saveAnnotations();
    }

    return AlgorithmStatus.UNSOUND_AND_PRECISE;
  }

  private void runFunction(FunctionPlan pPlan) {
    logger.log(Level.INFO, "Analysing function " + pPlan.name);
    FunctionEntryNode entryNode = cfa.getFunctionHead(pPlan.name);
    CFunctionType functionType = (CFunctionType) entryNode.getFunctionDefinition().getType();
    String functionRetType = functionType.toASTString(pPlan.name);
    FunctionDerefAnnotation functionAnnotation = new FunctionDerefAnnotation(pPlan.name, functionRetType);
    ArrayList<ParameterDerefAnnotation> parameterAnnotations = functionAnnotation.parameterAnnotations;

    functionAnnotation.retTypeIsPointer = functionType.getReturnType() instanceof CPointerType;

    for (AParameterDeclaration parameterDeclaration : entryNode.getFunctionParameters()) {
      Boolean isPointer = parameterDeclaration.getType() instanceof CPointerType;
      ParameterDerefAnnotation parameterAnnotation = new ParameterDerefAnnotation(
          parameterDeclaration.getName(), isPointer, false, false);
      parameterAnnotations.add(parameterAnnotation);
    }

    cfa = cfa.getCopyWithMainFunction(entryNode);

    try {
      if (functionAnnotation.retTypeIsPointer) {
        if (mayReturnNull(pPlan, entryNode.getReturnVariable().get().getName())) {
          functionAnnotation.retMayBeNull = true;
        }

        logger.log(Level.INFO, "New return pointer annotation in function " + pPlan.name + ": " + functionAnnotation.retMayBeNull);
      }

      for (ParameterDerefAnnotation parameterAnnotation : parameterAnnotations) {
        if (parameterAnnotation.isPointer) {
          if (mayDereferenceNull(pPlan, parameterAnnotations, parameterAnnotation.name)) {
            parameterAnnotation.mayBeDereferenced = true;
          }

          if (mustDereferenceNull(pPlan, parameterAnnotation.name)) {
            parameterAnnotation.mustBeDereferenced = true;
          }

          logger.log(Level.INFO, "New parameter annotation in function " + pPlan.name + ": " + parameterAnnotation);
        }
      }

      functionAnnotations.put(pPlan.name, functionAnnotation);
    } catch (FileNotFoundException e) {
      // TODO ???
      e.printStackTrace();
    }
  }

  @Options
  private static class NullDerefArgAnnotationAlgorithmOptions {

    @Option(secure=true, name="analysis.collectAssumptions",
        description="use assumption collecting algorithm")
        boolean collectAssumptions = false;

    @Option(secure=true, name = "analysis.algorithm.CEGAR",
        description = "use CEGAR algorithm for lazy counter-example guided analysis"
          + "\nYou need to specify a refiner with the cegar.refiner option."
          + "\nCurrently all refiner require the use of the ARGCPA.")
          boolean useCEGAR = false;

    @Option(secure=true, name="analysis.checkCounterexamples",
        description="use a second model checking run (e.g., with CBMC or a different CPAchecker configuration) to double-check counter-examples")
        boolean checkCounterexamples = false;

    @Option(secure=true, name="analysis.algorithm.BMC",
        description="use a BMC like algorithm that checks for satisfiability "
          + "after the analysis has finished, works only with PredicateCPA")
          boolean useBMC = false;

    @Option(secure=true, name="analysis.algorithm.CBMC",
        description="use CBMC as an external tool from CPAchecker")
        boolean runCBMCasExternalTool = false;

    @Option(secure=true, name="analysis.unknownIfUnrestrictedProgram",
        description="stop the analysis with the result unknown if the program does not satisfies certain restrictions.")
    private boolean unknownIfUnrestrictedProgram = false;


  }

  private String generateCallAutomatonEdges(FunctionPlan pPlan, Boolean pIsMayAnalysis) {
    String res = "";

    for (Pair<String, String> dependency : pPlan.dependencies) {
      String dependencyName = dependency.getFirst();
      String dependencyObjectFile = dependency.getSecond();
      FunctionDerefAnnotation annotation = getFunctionAnnotation(dependencyName, dependencyObjectFile);

      if (annotation == null) {
        logger.log(Level.INFO, "Could not find annotation for " + dependencyName + " in " + dependencyObjectFile);
        continue;
      }

      if (pIsMayAnalysis) {
        for (ParameterDerefAnnotation parameterAnnotation : annotation.parameterAnnotations) {
          if (parameterAnnotation.isPointer && parameterAnnotation.mayBeDereferenced) {
            String parameterName = parameterAnnotation.name;
            String parametersTemplate = "";

            for (ParameterDerefAnnotation anotherParameterAnnotation : annotation.parameterAnnotations) {
              String joker = anotherParameterAnnotation.name.equals(parameterName) ? "$1" : "$?";
              parametersTemplate = parametersTemplate.equals("") ? joker : (parametersTemplate + ", " + joker);
            }

            String callTemplate = dependencyName + "(" + parametersTemplate + ")";
            res = res + "\n  MATCH CALL {" + callTemplate + "} -> SPLIT {$1 != (void *) 0} GOTO Init NEGATION ERROR;";
            res = res + "\n  MATCH CALL {$? = " + callTemplate + "} -> SPLIT {$1 != (void *) 0} GOTO Init NEGATION ERROR;";
          }
        }
      } else {
        String assumptions = "";
        String parametersTemplate = "";
        int nextNumberedJoker = 1;

        for (ParameterDerefAnnotation parameterAnnotation : annotation.parameterAnnotations) {
          String joker;

          if (parameterAnnotation.isPointer && parameterAnnotation.mustBeDereferenced) {
            joker = "$" + nextNumberedJoker;
            nextNumberedJoker++;
            assumptions = assumptions + joker + " != (void *) 0;";
          } else {
            joker = "$?";
          }

          parametersTemplate = parametersTemplate.equals("") ? joker : (parametersTemplate + ", " + joker);
        }

        if (!assumptions.equals("")) {
          String callTemplate = dependencyName + "(" + parametersTemplate + ")";
          res = res + "\n  MATCH CALL {" + callTemplate + "} -> ASSUME {" + assumptions + "} GOTO Init;";
          res = res + "\n  MATCH CALL {$? = " + callTemplate + "} -> ASSUME {" + assumptions + "} GOTO Init;";
        }
      }
    }

    return res;
  }

  private String generateReturnAutomatonEdges(FunctionPlan pPlan) {
    String res = "";

    for (Pair<String, String> dependency : pPlan.dependencies) {
      String dependencyName = dependency.getFirst();
      String dependencyObjectFile = dependency.getSecond();
      FunctionDerefAnnotation annotation = getFunctionAnnotation(dependencyName, dependencyObjectFile);

      if (annotation == null) {
        logger.log(Level.INFO, "Could not find annotation for " + dependencyName + " in " + dependencyObjectFile);
        continue;
      }

      if (annotation.retTypeIsPointer && !annotation.retMayBeNull) {
        String parametersTemplate = "";

        for (ParameterDerefAnnotation parameterAnnotation : annotation.parameterAnnotations) {
          parametersTemplate = parametersTemplate.equals("") ? "$?" : (parametersTemplate + ", $?");
        }

        String callTemplate = dependencyName + "(" + parametersTemplate + ")";

        res = res + "\n  MATCH RETURN {$1 = " + callTemplate + "} -> ASSUME {$1 != (void *) 0} GOTO Init;";
      }
    }

    return res;
  }

  private String generateNullDereferencePossibilitySpec(FunctionPlan pPlan, List<ParameterDerefAnnotation> pParameterAnnotations, String pNullParameter) throws FileNotFoundException {
    String fileName = distinctTempSpecNames ? ("may_" + pPlan.name + "_" + pNullParameter + "_tmp.spc") : "may_tmp.spc";
    PrintWriter writer = new PrintWriter(fileName);
    writer.println("CONTROL AUTOMATON MAYDEREF");
    writer.println("INITIAL STATE Init;");
    writer.println("STATE USEALL Init:");

    String assumptions = pNullParameter + " == (void *) 0;";

    for (ParameterDerefAnnotation parameterAnnotation : pParameterAnnotations) {
      if (parameterAnnotation.isPointer && !parameterAnnotation.name.equals(pNullParameter)) {
        assumptions = assumptions + parameterAnnotation.name + " != (void *) 0;";
      }
    }

    writer.println("  MATCH ENTRY -> ASSUME {" + assumptions + "} GOTO Init;");
    writer.println("  MATCH DEREF {$1} -> DISTINCT SPLIT {$1 != (void *) 0} GOTO Init NEGATION ERROR;");
    writer.println(generateCallAutomatonEdges(pPlan, true));
    writer.println("END AUTOMATON");
    writer.close();
    return fileName;
  }

  private String generateUnavoidableNullDereferenceSpec(FunctionPlan pPlan, String pNullParameter) throws FileNotFoundException {
    String fileName = distinctTempSpecNames ? ("must_" + pPlan.name + "_" + pNullParameter + "_tmp.spc") : "must_tmp.spc";
    PrintWriter writer = new PrintWriter(fileName);
    writer.println("CONTROL AUTOMATON MUSTDEREF");
    writer.println("INITIAL STATE Init;");
    writer.println("STATE USEALL Init:");

    writer.println("  MATCH ENTRY -> ASSUME {" + pNullParameter + " == (void *) 0} GOTO Init;");
    writer.println("  MATCH EXIT -> ERROR;");
    writer.println("  MATCH DEREF {$1} -> ASSUME {$1 != (void *) 0} GOTO Init;");
    writer.println(generateCallAutomatonEdges(pPlan, false));
    writer.println("END AUTOMATON");
    writer.close();
    return fileName;
  }

  private String generateReturnNullPossibilitySpec(FunctionPlan pPlan, String pFunctionRetVar) throws FileNotFoundException {
    String fileName = distinctTempSpecNames ? ("may_return_null_" + pPlan.name + "_tmp.spc") : "may_return_null_tmp.spc";
    PrintWriter writer = new PrintWriter(fileName);
    writer.println("CONTROL AUTOMATON MAYRETURNNULL");
    writer.println("INITIAL STATE Init;");
    writer.println("STATE USEALL Init:");

    writer.println("  MATCH EXIT -> SPLIT {" + pFunctionRetVar + " != (void *) 0} GOTO Init NEGATION ERROR;");
    writer.println(generateReturnAutomatonEdges(pPlan));
    writer.println("END AUTOMATON");
    writer.close();
    return fileName;
  }

  private Boolean mayReturnNull(FunctionPlan pPlan, String pFunctionRetVar) throws FileNotFoundException {
    return runWithSpecification(pPlan, generateReturnNullPossibilitySpec(pPlan, pFunctionRetVar), "May return null analysis");

  }

  private Boolean mayDereferenceNull(FunctionPlan pPlan, List<ParameterDerefAnnotation> pParameterAnnotations, String pNullParameter) throws FileNotFoundException {
    return runWithSpecification(pPlan,
        generateNullDereferencePossibilitySpec(pPlan, pParameterAnnotations, pNullParameter),
        "May analysis for parameter " + pNullParameter);
  }

  private Boolean mustDereferenceNull(FunctionPlan pPlan, String pNullParameter) throws FileNotFoundException {
    return !runWithSpecification(pPlan,
        generateUnavoidableNullDereferenceSpec(pPlan, pNullParameter),
        "Must analysis for parameter " + pNullParameter);
  }

  private Boolean runWithSpecification(FunctionPlan pPlan, String pSpecificationFilePath, String pAnalysisName) {
    stats.totalTime.start();

    FunctionEntryNode entryNode = cfa.getFunctionHead(pPlan.name);
    ShutdownManager singleShutdownManager = ShutdownManager.createWithParent(shutdownNotifier);

    try {
      ConfigurationBuilder singleConfigBuilder = Configuration.builder();
      singleConfigBuilder.copyFrom(globalConfig);
      singleConfigBuilder.setOption("specification", pSpecificationFilePath);
      Configuration singleConfig = singleConfigBuilder.build();

      NullDerefArgAnnotationAlgorithmOptions singleOptions = new NullDerefArgAnnotationAlgorithmOptions();
      singleConfig.inject(singleOptions);

      LogManager singleLogger = logger.withComponentName(pAnalysisName);

      ResourceLimitChecker singleLimits = ResourceLimitChecker.fromConfiguration(singleConfig, singleLogger, singleShutdownManager);
      singleLimits.start();

      ReachedSetFactory singleReachedSetFactory = new ReachedSetFactory(singleConfig);
      ConfigurableProgramAnalysis currentCpa = createCPA(singleReachedSetFactory, singleConfig, singleLogger, singleShutdownManager.getNotifier(), stats);
      Algorithm currentAlgorithm = createAlgorithm(currentCpa, singleConfig, singleLogger, singleShutdownManager, singleReachedSetFactory, singleOptions);
      ReachedSet currentReached = createInitialReachedSetForRestart(currentCpa, entryNode, singleReachedSetFactory, singleLogger);

      if (currentAlgorithm instanceof StatisticsProvider) {
        ((StatisticsProvider)currentAlgorithm).collectStatistics(stats.getSubStatistics());
      }
      shutdownNotifier.shutdownIfNecessary();

      stats.noOfAlgorithmsUsed++;

      // run algorithm
      AlgorithmStatus status = currentAlgorithm.run(currentReached);
      return from(currentReached).anyMatch(IS_TARGET_STATE) && status.isPrecise();
    } catch (InvalidConfigurationException e) {
      // TODO ???
      e.printStackTrace();
    } catch (CPAException e) {
      // TODO ???
      e.printStackTrace();
    } catch (InterruptedException e) {
      // TODO ??
      e.printStackTrace();
    } finally {
      stats.totalTime.stop();
    }

    return true;
  }

  private Triple<Algorithm, ConfigurableProgramAnalysis, ReachedSet> createNextAlgorithm(Path singleConfigFileName, CFANode mainFunction, ShutdownManager singleShutdownManager) throws InvalidConfigurationException, CPAException, IOException {

    ReachedSet reached;
    ConfigurableProgramAnalysis cpa;
    Algorithm algorithm;

    ConfigurationBuilder singleConfigBuilder = Configuration.builder();
    singleConfigBuilder.copyFrom(globalConfig);
    singleConfigBuilder.clearOption("NullDerefArgAnnotationAlgorithm.configFiles");
    singleConfigBuilder.clearOption("analysis.restartAfterUnknown");
    singleConfigBuilder.loadFromFile(singleConfigFileName);
    if (globalConfig.hasProperty("specification")) {
      singleConfigBuilder.copyOptionFrom(globalConfig, "specification");
    }
    Configuration singleConfig = singleConfigBuilder.build();
    LogManager singleLogger = logger.withComponentName("Analysis" + (stats.noOfAlgorithmsUsed+1));

    NullDerefArgAnnotationAlgorithmOptions singleOptions = new NullDerefArgAnnotationAlgorithmOptions();
    singleConfig.inject(singleOptions);

    ResourceLimitChecker singleLimits = ResourceLimitChecker.fromConfiguration(singleConfig, singleLogger, singleShutdownManager);
    singleLimits.start();

    if (singleOptions.runCBMCasExternalTool) {
      algorithm = new ExternalCBMCAlgorithm(filename, singleConfig, singleLogger);
      cpa = null;
      reached = new ReachedSetFactory(singleConfig).create();
    } else {
      ReachedSetFactory singleReachedSetFactory = new ReachedSetFactory(singleConfig);
      cpa = createCPA(singleReachedSetFactory, singleConfig, singleLogger, singleShutdownManager.getNotifier(), stats);
      algorithm = createAlgorithm(cpa, singleConfig, singleLogger, singleShutdownManager, singleReachedSetFactory, singleOptions);
      reached = createInitialReachedSetForRestart(cpa, mainFunction, singleReachedSetFactory, singleLogger);
    }

    return Triple.of(algorithm, cpa, reached);
  }

  private ReachedSet createInitialReachedSetForRestart(
      ConfigurableProgramAnalysis cpa,
      CFANode mainFunction,
      ReachedSetFactory pReachedSetFactory,
      LogManager singleLogger) {
    singleLogger.log(Level.FINE, "Creating initial reached set");

    AbstractState initialState = cpa.getInitialState(mainFunction, StateSpacePartition.getDefaultPartition());
    Precision initialPrecision = cpa.getInitialPrecision(mainFunction, StateSpacePartition.getDefaultPartition());

    ReachedSet reached = pReachedSetFactory.create();
    reached.add(initialState, initialPrecision);
    return reached;
  }

  private ConfigurableProgramAnalysis createCPA(ReachedSetFactory pReachedSetFactory,
      Configuration pConfig, LogManager singleLogger, ShutdownNotifier singleShutdownNotifier,
      NullDerefArgAnnotationAlgorithmStatistics stats) throws InvalidConfigurationException, CPAException {
    singleLogger.log(Level.FINE, "Creating CPAs");

    CPABuilder builder = new CPABuilder(pConfig, singleLogger, singleShutdownNotifier, pReachedSetFactory);
    ConfigurableProgramAnalysis cpa = builder.buildCPAWithSpecAutomatas(cfa);

    if (cpa instanceof StatisticsProvider) {
      ((StatisticsProvider)cpa).collectStatistics(stats.getSubStatistics());
    }
    return cpa;
  }

  private Algorithm createAlgorithm(
      final ConfigurableProgramAnalysis cpa, Configuration pConfig,
      final LogManager singleLogger,
      final ShutdownManager singleShutdownManager,
      ReachedSetFactory singleReachedSetFactory,
      NullDerefArgAnnotationAlgorithmOptions pOptions)
  throws InvalidConfigurationException, CPAException {
    ShutdownNotifier singleShutdownNotifier = singleShutdownManager.getNotifier();
    singleLogger.log(Level.FINE, "Creating algorithms");

    Algorithm algorithm = CPAAlgorithm.create(cpa, singleLogger, pConfig, singleShutdownNotifier);

    if (pOptions.useCEGAR) {
      algorithm = new CEGARAlgorithm(algorithm, cpa, pConfig, singleLogger);
    }

    if (pOptions.useBMC) {
      algorithm = new BMCAlgorithm(algorithm, cpa, pConfig, singleLogger, singleReachedSetFactory, singleShutdownManager, cfa);
    }

    if (pOptions.checkCounterexamples) {
      algorithm = new CounterexampleCheckAlgorithm(algorithm, cpa, pConfig, singleLogger, singleShutdownNotifier, cfa, filename);
    }

    if (pOptions.collectAssumptions) {
      algorithm = new AssumptionCollectorAlgorithm(algorithm, cpa, cfa, shutdownNotifier, pConfig, singleLogger);
    }

    if (pOptions.unknownIfUnrestrictedProgram) {
      algorithm = new RestrictedProgramDomainAlgorithm(algorithm, cfa);
    }

    return algorithm;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    if (currentAlgorithm instanceof StatisticsProvider) {
      ((StatisticsProvider)currentAlgorithm).collectStatistics(pStatsCollection);
    }
    pStatsCollection.add(stats);
  }
}
