// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm;

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.IO;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.export.DOTBuilder;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.AggregatedReachedSets;
import org.sosy_lab.cpachecker.core.reachedset.ForwardingReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.core.specification.Specification;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.arrayabstraction.ArrayAbstraction;
import org.sosy_lab.cpachecker.util.arrayabstraction.ArrayAbstractionResult;
import org.sosy_lab.cpachecker.util.cwriter.CFAToCTranslator;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;

/**
 * Algorithm for array abstraction by program transformation.
 *
 * <p>A delegate analysis is run on the abstracted program which is represented by a transformed CFA
 * that is derived from the specified original CFA.
 */
@Options(prefix = "arrayAbstraction")
public final class ArrayAbstractionAlgorithm extends NestingAlgorithm {

  @Option(
      secure = true,
      name = "delegateAnalysis",
      description =
          "Configuration file path of the delegate analysis running on the transformed program.")
  @FileOption(FileOption.Type.REQUIRED_INPUT_FILE)
  private Path delegateAnalysisConfigurationFile;

  @Option(
      secure = true,
      name = "checkCounterexamples",
      description =
          "Use a second delegate analysis run to check counterexamples on the original program that"
              + " contains (non-abstracted) arrays.")
  private boolean checkCounterexamples = false;

  @Option(
      secure = true,
      name = "cfa.dot.export",
      description = "Whether to export the CFA with abstracted arrays as DOT file.")
  private boolean exportDotTransformedCfa = true;

  @Option(
      secure = true,
      name = "cfa.dot.file",
      description = "DOT file path for CFA with abstracted arrays.")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path exportDotTransformedCfaFile = Path.of("cfa-abstracted-arrays.dot");

  @Option(
      secure = true,
      name = "cfa.c.export",
      description = "Whether to export the CFA with abstracted arrays as C source file.")
  private boolean exportCTransformedCfa = true;

  @Option(
      secure = true,
      name = "cfa.c.file",
      description = "C source file path for CFA with abstracted arrays.")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path exportCTransformedCfaFile = Path.of("abstracted-arrays.c");

  private final StatTimer arrayAbstractionTimer = new StatTimer("Time for array abstraction");

  private final ShutdownManager shutdownManager;
  private final Collection<Statistics> stats;
  private final ArrayAbstractionResult arrayAbstractionResult;
  private final CFA originalCfa;

  public ArrayAbstractionAlgorithm(
      Configuration pConfiguration,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      Specification pSpecification,
      CFA pCfa)
      throws InvalidConfigurationException {
    super(pConfiguration, pLogger, pShutdownNotifier, pSpecification);

    shutdownManager = ShutdownManager.createWithParent(shutdownNotifier);
    stats = new CopyOnWriteArrayList<>();
    originalCfa = pCfa;

    arrayAbstractionTimer.start();
    try {
      arrayAbstractionResult = ArrayAbstraction.transformCfa(globalConfig, logger, originalCfa);
    } finally {
      arrayAbstractionTimer.stop();
    }

    pConfiguration.inject(this);
  }

  private AlgorithmStatus runDelegateAnalysis(
      CFA pCfa,
      ForwardingReachedSet pForwardingReachedSet,
      AggregatedReachedSets pAggregatedReached)
      throws InterruptedException, CPAEnabledAnalysisPropertyViolationException, CPAException {

    ImmutableSet<String> ignoreOptions =
        ImmutableSet.of("analysis.useArrayAbstraction", "arrayAbstraction.delegateAnalysis");

    Triple<Algorithm, ConfigurableProgramAnalysis, ReachedSet> delegate;
    try {
      delegate =
          createAlgorithm(
              delegateAnalysisConfigurationFile,
              pCfa.getMainFunction(),
              pCfa,
              shutdownManager,
              pAggregatedReached,
              ignoreOptions,
              stats);
    } catch (IOException | InvalidConfigurationException ex) {
      logger.logUserException(Level.SEVERE, ex, "Could not create delegate algorithm");
      return AlgorithmStatus.NO_PROPERTY_CHECKED;
    }

    Algorithm algorithm = delegate.getFirst();
    ReachedSet reached = delegate.getThird();

    AlgorithmStatus status = algorithm.run(reached);

    while (reached.hasWaitingState()) {
      status = algorithm.run(reached);
    }

    pForwardingReachedSet.setDelegate(reached);

    return status;
  }

  @Override
  public AlgorithmStatus run(ReachedSet pReachedSet)
      throws CPAException, InterruptedException, CPAEnabledAnalysisPropertyViolationException {

    ForwardingReachedSet forwardingReachedSet = (ForwardingReachedSet) pReachedSet;
    AggregatedReachedSets aggregatedReached = AggregatedReachedSets.singleton(pReachedSet);

    AlgorithmStatus status = AlgorithmStatus.NO_PROPERTY_CHECKED;

    if (arrayAbstractionResult.getStatus() == ArrayAbstractionResult.Status.PRECISE) {
      status =
          runDelegateAnalysis(
              arrayAbstractionResult.getTransformedCfa(), forwardingReachedSet, aggregatedReached);
    }

    if (checkCounterexamples && forwardingReachedSet.wasTargetReached()) {
      status = runDelegateAnalysis(originalCfa, forwardingReachedSet, aggregatedReached);
    }

    return status;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {

    pStatsCollection.addAll(stats);

    if (arrayAbstractionResult.getStatus() != ArrayAbstractionResult.Status.FAILED) {

      CFA transformedCfa = arrayAbstractionResult.getTransformedCfa();

      if (exportDotTransformedCfa && exportDotTransformedCfaFile != null) {
        try (Writer writer =
            IO.openOutputFile(exportDotTransformedCfaFile, Charset.defaultCharset())) {
          DOTBuilder.generateDOT(writer, transformedCfa);
        } catch (IOException ex) {
          logger.logUserException(Level.WARNING, ex, "Could not write CFA to dot file");
        }
      }

      if (exportCTransformedCfa && exportCTransformedCfaFile != null) {
        try (Writer writer =
            IO.openOutputFile(exportCTransformedCfaFile, Charset.defaultCharset())) {
          String sourceCode = new CFAToCTranslator(globalConfig).translateCfa(transformedCfa);
          writer.write(sourceCode);
        } catch (IOException | CPAException | InvalidConfigurationException ex) {
          logger.logUserException(Level.WARNING, ex, "Could not export CFA as C source file");
        }
      }
    }

    pStatsCollection.add(
        new Statistics() {

          @Override
          public void printStatistics(
              final PrintStream pOut, final Result pResult, final UnmodifiableReachedSet pReached) {

            int indentation = 1;
            put(pOut, indentation, "Array abstraction status", arrayAbstractionResult.getStatus());
            put(pOut, indentation, arrayAbstractionTimer);
            put(
                pOut,
                indentation,
                "Number of transformed arrays",
                arrayAbstractionResult.getTransformedArrays().size());
            put(
                pOut,
                indentation,
                "Number of transformed loops",
                arrayAbstractionResult.getTransformedLoops().size());
          }

          @Override
          public String getName() {
            return ArrayAbstractionAlgorithm.class.getSimpleName();
          }
        });
  }
}
