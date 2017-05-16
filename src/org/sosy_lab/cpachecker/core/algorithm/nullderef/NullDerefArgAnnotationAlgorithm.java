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
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
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
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.resources.ResourceLimitChecker;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final NullDerefArgAnnotationAlgorithmStatistics stats;
  private final String filename;
  private CFA cfa;
  private final Configuration globalConfig;

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
  }

  @Override
  public AlgorithmStatus run(ReachedSet pReached) throws CPAException, InterruptedException {
    Collection<FunctionEntryNode> functionEntryNodes = cfa.getAllFunctionHeads();

    for (FunctionEntryNode entryNode : functionEntryNodes) {
      List<? extends AParameterDeclaration> functionParameters = entryNode.getFunctionParameters();
      ArrayList<String> pointerParameterNames = new ArrayList<>();

      for (AParameterDeclaration parameterDeclaration : functionParameters) {
        if (parameterDeclaration.getType() instanceof CPointerType) {
          pointerParameterNames.add(parameterDeclaration.getName());
        }
      }

      if (pointerParameterNames.isEmpty()) {
        continue;
      }

      cfa = cfa.getCopyWithMainFunction(entryNode);

      try {
        if (checkNullDereferencePossibility(entryNode, pointerParameterNames)) {
          logger.log(Level.INFO, "Function " + entryNode.getFunctionName() + " can always dereference NULL");
          continue;
        }

        for (String parameterName : pointerParameterNames) {
          ArrayList<String> otherPointerParameterNames = new ArrayList<>(pointerParameterNames);
          otherPointerParameterNames.remove(parameterName);

          if (checkNullDereferencePossibility(entryNode, otherPointerParameterNames)) {
            logger.log(Level.INFO, "Parameter " + parameterName + " of function " + entryNode.getFunctionName() + " can cause NULL dereference");
          } else {
            logger.log(Level.INFO, "Parameter " + parameterName + " of function " + entryNode.getFunctionName() + " can not cause NULL dereference");
          }
        }
      } catch (FileNotFoundException e) {
        // TODO ???
        e.printStackTrace();
      }
    }

    return AlgorithmStatus.UNSOUND_AND_PRECISE;
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

  private String generateNullDereferencePossibilitySpec(List<String> nonNullParameters) throws FileNotFoundException {
    String fileName = "tmp.spc";
    PrintWriter writer = new PrintWriter(fileName);
    writer.println("CONTROL AUTOMATON MAYDEREF");
    writer.println("INITIAL STATE Init;");
    writer.println("STATE USEALL Init:");

    writer.print("  MATCH ENTRY -> ASSUME {");

    for (String nonNullParameter : nonNullParameters) {
      writer.print(nonNullParameter);
      writer.print(" != (void *) 0;");
    }

    writer.println("} GOTO Init;");

    writer.println("  MATCH DEREF {$1} -> SPLIT {$1 != (void *) 0} GOTO Init NEGATION ERROR;");
    writer.println("END AUTOMATON");
    writer.close();
    return fileName;
  }

  private Boolean checkNullDereferencePossibility(FunctionEntryNode entryNode, List<String> nonNullParameters) throws FileNotFoundException {
    stats.totalTime.start();

    ShutdownManager singleShutdownManager = ShutdownManager.createWithParent(shutdownNotifier);

    String specFileName = generateNullDereferencePossibilitySpec(nonNullParameters);

    try {
      ConfigurationBuilder singleConfigBuilder = Configuration.builder();
      singleConfigBuilder.copyFrom(globalConfig);
      singleConfigBuilder.setOption("specification", specFileName);
      Configuration singleConfig = singleConfigBuilder.build();

      NullDerefArgAnnotationAlgorithmOptions singleOptions = new NullDerefArgAnnotationAlgorithmOptions();
      singleConfig.inject(singleOptions);

      LogManager singleLogger = logger.withComponentName("Analysis of NULL dereference possibility");

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
