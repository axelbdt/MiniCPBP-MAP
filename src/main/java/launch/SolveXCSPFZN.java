package launch;

import java.io.File;

import minicpbp.search.DovetailSearch;
import minicpbp.search.LDSearch;
import minicpbp.util.Log;
import java.util.HashMap;
import java.util.Map;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.Search;
import minicpbp.search.SearchStatistics;
import minicpbp.util.Procedure;
import minicpbp.util.SolutionDistribution;
import minicpbp.util.exception.InconsistencyException;
import model.ModelFormatFrontend;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import xcsp.XCSP;
import fzn.FZN;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.BranchingScheme.domWdeg;
import static minicpbp.cp.Factory.*;

public class SolveXCSPFZN {
    public enum BranchingHeuristic {
		FFRV, // first-fail, random value
		MXMS, // maximum marginal strength
		MNMS, // minimum marginal strength
		MXM, // maximum marginal
		MNM, // minimum marginal
		MNE, //minimum entropy
		MNERTB, //minimum entropy, uniform random tie-break among equal-entropy variables (probe N, BP_PROBE_PROTOCOL amendments 10/10a)
		IE, //impact entropy
		MIE, //min-entropy followed by impact entropy after first restart,
		MNEBW, //min-entropy with biased wheel value selection
		WDEG, //dom-wdeg
		WDEGMXM, //dom-wdeg variable selection + max-marginal value selection, BP kept ON (probe O, BP_PROBE_PROTOCOL amendment 11)
		WDEGRV, //dom-wdeg variable selection + uniform random value selection, no BP -- the unbiased value-rule control (N7, VALUE_ISOLATION_EXPERIMENT.md W4)
		WDEGCNT, //dom-wdeg variable selection + value ordering by the incident counting constraints' solution densities, no BP (MDD_COUNTING_PLAN.md amendment A5)
		IBS, //impact-based search
	}

	private static Map<String, BranchingHeuristic> branchingMap = new HashMap<String, BranchingHeuristic>() {
		private static final long serialVersionUID = 4936849715939593675L;
		{
			put("first-fail-random-value", BranchingHeuristic.FFRV);
			put("max-marginal-strength", BranchingHeuristic.MXMS);
			put("min-marginal-strength", BranchingHeuristic.MNMS);
			put("max-marginal", BranchingHeuristic.MXM);
			put("min-marginal", BranchingHeuristic.MNM);
			put("min-entropy", BranchingHeuristic.MNE);
			put("min-entropy-rtb", BranchingHeuristic.MNERTB);
			put("impact-entropy", BranchingHeuristic.IE);
			put("impact-min-entropy", BranchingHeuristic.MIE);
			put("min-entropy-biased", BranchingHeuristic.MNEBW);
			put("dom-wdeg", BranchingHeuristic.WDEG);
			put("dom-wdeg-max-marginal", BranchingHeuristic.WDEGMXM);
			put("dom-wdeg-random-value", BranchingHeuristic.WDEGRV);
			put("dom-wdeg-count-value", BranchingHeuristic.WDEGCNT);
			put("impact-based-search", BranchingHeuristic.IBS);
		}
	};

	public enum TreeSearchType {
		DFS, LDS, DFSR
	}

	private static Map<String, TreeSearchType> searchTypeMap = new HashMap<String, TreeSearchType>() {
		private static final long serialVersionUID = 8428231233538651558L;

		{
			put("dfs", TreeSearchType.DFS);
			put("lds", TreeSearchType.LDS);
		}
	};

	private Solver minicp = makeSolver();

	// Tracing flags
	private boolean traceBP = false;
	private boolean traceSearch = false;
	private boolean traceEntropy = false;

	// Required params
	private String inputStr;
	private BranchingHeuristic heuristic;
	private int timeout;

	// Optional Params
	private TreeSearchType searchType = TreeSearchType.DFS;
	private boolean checkSolution = false;
	private String statsFileStr = "";
	private String solFileStr = "";
	private int maxIter = 10;
	private boolean restart = false;
	private int nbFailCutof = 100;
	private double restartFactor = 1.5;
	private boolean initImpact = false;
	private boolean enumerateSolutions = false;

	/**
	 * INTENTIONALLY INERT (BP_COST_PROFILE.md 1.4 / Part 4 item 4). Damping is
	 * decided by {@code MiniCP.BPtuneDamping} at the root, and whatever factor
	 * it lands on stays in force for the whole search; this setter was never
	 * forwarded to the solver ({@code minicp.setDamp} call deliberately absent
	 * from {@code solve()}). Kept so existing harness callers compile, and
	 * documented so nobody believes {@code damp(false)} disables damping.
	 * Forwarding it would change every measured arm's behaviour and therefore
	 * requires a registered protocol and new campaigns, not a reconnect.
	 */
	private boolean damp = false;
	private double dampingFactor = 0.5;

	// TODO: unused fields, figure out if we want to keep them
	private boolean traceNbIter = false;
	private double variationThreshold = -Double.MAX_VALUE;
	private boolean dynamicStopBP = false;


	public void solve(ModelFormatFrontend frontend) {
		long t0 = System.currentTimeMillis();

		minicp.setTraceBPFlag(traceBP);
		minicp.setTraceSearchFlag(traceSearch);
		minicp.setTraceEntropyFlag(traceEntropy);
		minicp.setMaxIter(maxIter);
		// TODO: check if these should be commented out or removed
//		minicp.setDamp(damp);
//		minicp.setDampingFactor(dampingFactor);
//		minicp.setDynamicStopBP(dynamicStopBP);
//      minicp.setTraceNbIterFlag(traceNbIter);
//		minicp.setVariationThreshold(variationThreshold);

		Runnable earlyReturnSummary = () -> {
			SearchStatistics stats = new SearchStatistics();
			stats.setCompleted();
			frontend.onNoSolutionFound(stats);
			frontend.printStats(stats, statsFileStr, 0L, -1, "");
		};

		if (frontend.hasFailed()) {
			frontend.onPreInitFail();
			earlyReturnSummary.run();
			return;
		}
		frontend.initModel();
		if (frontend.hasFailed()) {
			earlyReturnSummary.run();
			return;
		}

		IntVar[] decisionsVars = frontend.getDecisionVars();

		// 2026-08-18: a constraint-free instance (seen from a pycsp3
		// mis-compilation that emits variables but no constraints) leaves
		// mapVar empty and used to crash the branching heuristics with an
		// ArrayIndexOutOfBoundsException; fail with a diagnosis instead.
		if (decisionsVars.length == 0) {
			System.out.println("status: UNSUPPORTED");
			Log.info("c no branching variables (constraint-free or unparsed instance); not searching");
			return;
		}

		Search search = null;
		try {
			switch (heuristic) {
				case FFRV:
					minicp.setMode(Solver.PropaMode.SP);
					search = makeSearch(firstFailRandomVal(decisionsVars));
					break;
				case MXMS:
					search = makeSearch(maxMarginalStrength(decisionsVars));
					break;
				case MXM:
					search = makeSearch(maxMarginal(decisionsVars));
					break;
				case MNMS:
					search = makeSearch(minMarginalStrength(decisionsVars));
					break;
				case MNM:
					search = makeSearch(minMarginal(decisionsVars));
					break;
				case MNE:
					search = makeSearch(minEntropy(decisionsVars));
					break;
				case MNERTB:
					// probe N (BP_PROBE_PROTOCOL amendments 10/10a): min entropy with a
					// uniform random tie-break among variables whose entropy agrees to
					// 2 decimals. Seed it with -Dminicpbp.seed; absent that property the
					// solver RNG is new Random() and the run is unreproducible.
					// NOTE: minEntropyRandomTieBreak does not call setBranchingOrder, so
					// stopRule=decision would sample the deterministic heuristic's
					// decision. Ship-stop arms only until that is fixed.
					search = makeSearch(minEntropyRandomTieBreak(decisionsVars));
					break;
				case IE:
					search = makeSearch(impactEntropy(decisionsVars));
					if (initImpact)
						search.initializeImpact(decisionsVars);
					break;
				case IBS:
					minicp.setMode(Solver.PropaMode.SP);
					search = makeSearch(impactBasedSearch(decisionsVars));
					search.initializeImpactDomains(decisionsVars);
					nbFailCutof = nbFailCutof * decisionsVars.length;
					break;
				case MIE:
					search = makeDfs(minicp, minEntropyRegisterImpact(decisionsVars), impactEntropy(decisionsVars));
					if (initImpact)
						search.initializeImpact(decisionsVars);
					break;
				case MNEBW:
					search = makeSearch(minEntropyBiasedWheelSelectVal(decisionsVars));
					break;
				case WDEG:
					// -Dexp.forceSBP=true keeps belief propagation ON under the
					// deterministic dom/wdeg tree (BP_COST_PROFILE.md Part 0). BP never
					// removes a value (MiniCP.actOnZeroOneBelief=false), so SP and SBP
					// explore a byte-identical tree and the wall-clock difference is
					// exactly the price of BP. Default unchanged (SP).
					minicp.setMode(Boolean.getBoolean("exp.forceSBP") ? Solver.PropaMode.SBP : Solver.PropaMode.SP);
					search = makeSearch(domWdeg(decisionsVars));
					nbFailCutof = nbFailCutof * decisionsVars.length;
					break;
				case WDEGCNT:
					// amendment A5 (MDD_COUNTING_PLAN.md §6.3): SP mode, no BP; the value
					// comes from the incident ValueCounter constraints (Cumulative)
					minicp.setMode(Solver.PropaMode.SP);
					search = makeSearch(domWdegCountValue(decisionsVars));
					nbFailCutof = nbFailCutof * decisionsVars.length;
					break;
				case WDEGRV:
					// N7 (VALUE_ISOLATION_EXPERIMENT.md, W4): the unbiased value-rule
					// control. Same variable rule as WDEG and WDEGMXM, same PropaMode.SP
					// and the same nbFailCutof scaling as WDEG, so the ONLY difference
					// from the dom-wdeg baseline is xs.randomValue() in place of
					// xs.min(). Seeded: pass -Dminicpbp.seed or the run is
					// unreproducible.
					minicp.setMode(Solver.PropaMode.SP);
					search = makeSearch(domWdegRandomValue(decisionsVars));
					nbFailCutof = nbFailCutof * decisionsVars.length;
					break;
				case WDEGMXM:
					// probe O (BP_PROBE_PROTOCOL amendment 11): dom/wdeg variable
					// selection with max-marginal value selection. BP mode is left ON
					// deliberately — BP's only job here is the branched variable's
					// marginal (value selection); variable selection needs no BP.
					// No nbFailCutof scaling: LDS/DFS only, no restart path uses it.
					search = makeSearch(domWdegMaxMarginalValue(decisionsVars));
					break;
				default:
					Log.info("unknown search strategy");
					System.exit(1);
			}
		} catch (InconsistencyException ignored) {
			// Do we consider this a pre init fail?
			earlyReturnSummary.run();
			return;
		}

		boolean extractSolutionStr = checkSolution || (!Objects.equals(solFileStr, ""));
		AtomicBoolean foundSolution = new AtomicBoolean(false);
		List<String> solutionStrs = new ArrayList<>();
		SolutionDistribution trueMarginals = new SolutionDistribution();
		search.onSolution(() -> {
			foundSolution.set(true);
			String solutionStr = frontend.getSolutionStr(extractSolutionStr);
			if (enumerateSolutions) {
				solutionStrs.add(solutionStr);
				trueMarginals.addSolution(decisionsVars);
			} else {
				// No need to keep other solutions, only keep latest
				solutionStrs.add(0, solutionStr);
			}
		});

		SearchStatistics stats;
		switch (frontend.getGoal()) {
			//find a solution that maximize the cost function
			case MAX:
				stats = search.optimize(minicp.maximize(frontend.getObjectiveVar()),
						ss -> (System.currentTimeMillis() - t0 >= timeout * 1000L));
				break;
			//find a solution that minimize the cost function
			case MIN:
				stats = search.optimize(minicp.minimize(frontend.getObjectiveVar()),
						ss -> (System.currentTimeMillis() - t0 >= timeout * 1000L));
				break;
			default:
				Predicate<SearchStatistics> limit = ss -> (System.currentTimeMillis() - t0 >= timeout * 1000L || (!enumerateSolutions && foundSolution.get()));
				//find a solution that satisfies all constraints without restart
				if(!restart) {
					stats = search.solve(limit);
				}
				//find a solution that satisfies all constraints with restarts during the search
				else {
					stats = search.solveRestarts(limit, nbFailCutof, restartFactor);
				}
				break;
		}
		Long runtime = System.currentTimeMillis() - t0;

		// Print result

		if (foundSolution.get() && (!enumerateSolutions || stats.isCompleted())) {
			if (enumerateSolutions) {
				frontend.onAllSolutionsFound(stats, solutionStrs);
			} else {
				frontend.onSolutionFound(stats, solutionStrs.get(0));
			}

			if (checkSolution) {
				for (int i = 0; i < solutionStrs.size(); i++) {
					if (solutionStrs.size() > 1) {
						System.out.println("verifying solution " + (i + 1));
					}
					frontend.verifySolution(solutionStrs.get(i));
				}
			}

			if (enumerateSolutions) {
				System.out.println("printing true marginals of " + solutionStrs.size() + " solutions");
				System.out.println(trueMarginals);
			}

			printSolutions(solutionStrs);
		} else if (stats.isCompleted()) {
			frontend.onNoSolutionFound(stats);
		} else {
			frontend.onInconclusiveSearch(stats);
		}

		// 2026-08-19 LDS amendment: report the exact discrepancy of the
		// solution path (-1 for DFS or when no solution was found).
		int solutionDiscrepancy = (search instanceof LDSearch)
				? ((LDSearch) search).solutionDiscrepancy() : -1;
		// 2026-08-19 run-length accounting (TODO.md item 3): naive LDS
		// re-expands the tree prefix each pass, so cumulative nodes are
		// not comparable to a DFS tree size. Per-pass deltas
		// (cap:nodes:failures:solutions) make total-expanded vs
		// final-pass work distinguishable downstream.
		String ldsPasses = (search instanceof LDSearch)
				? ((LDSearch) search).passSummary() : "";
		frontend.printStats(stats, statsFileStr, runtime, solutionDiscrepancy, ldsPasses);
	}

	public void solve() {
		ModelFormatFrontend frontend;
		try {
			String extension = inputStr.substring(inputStr.lastIndexOf('.')+1);
			if(extension.equals("fzn")) {
				// stdout is parsed by MiniZinc's solns2out: no banner line,
				// no optimization trace between solutions, warnings on stderr
				Log.setTraceOptimization(false);
				Log.setWarningStream(System.err);
				FZN fzn = new FZN(minicp, inputStr);
				fzn.printStats(true);
				frontend = fzn;
			}
			else {
				Log.info(extension);
				System.out.println("XCSP");
				frontend = new XCSP(minicp, inputStr);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		solve(frontend);
	}

	/**
	 * Amendment 10b (probe N phase 2): run an in-process dovetail over LDS
	 * passes on the parsed model, instead of one JVM per pass. The model is
	 * parsed and built once; {@link DovetailSearch} owns the pass schedule, the
	 * per-pass configuration and the completion rule.
	 *
	 * Only the two min-entropy heuristics are accepted: the arms of this
	 * experiment differ in BP configuration and in RNG seed, not in heuristic
	 * family, and the heuristics that also flip the propagation mode (FFRV,
	 * IBS, WDEG) have no meaning inside a BP dovetail.
	 */
	public DovetailSearch.Result solveDovetail(ModelFormatFrontend frontend,
											   BranchingHeuristic heuristic,
											   java.util.List<DovetailSearch.Arm> arms,
											   int[] ladder, long globalBudgetMs,
											   long calibBudgetMs, int maxPasses,
											   String solFileStr) {
		if (heuristic != BranchingHeuristic.MNE && heuristic != BranchingHeuristic.MNERTB)
			throw new IllegalArgumentException("c solveDovetail supports min-entropy and "
					+ "min-entropy-rtb only, not " + heuristic);
		if (frontend.hasFailed()) {
			DovetailSearch.Result r = new DovetailSearch.Result();
			r.status = "UNSAT";
			r.decidingLabel = "root";
			return r;
		}
		minicp.setTraceBPFlag(traceBP);
		minicp.setTraceSearchFlag(traceSearch);
		minicp.setTraceEntropyFlag(traceEntropy);

		IntVar[] vars = frontend.getDecisionVars();
		if (vars.length == 0) {
			DovetailSearch.Result r = new DovetailSearch.Result();
			r.status = "UNSUPPORTED";
			return r;
		}

		boolean extractSolutionStr = true;
		AtomicBoolean foundSolution = new AtomicBoolean(false);
		List<String> solutionStrs = new ArrayList<>();
		SolutionDistribution trueMarginals = new SolutionDistribution();

		// A fresh search AND a fresh branching per pass: minEntropyRandomTieBreak
		// captures the RNG and the branching flags at construction, so a reseed
		// between passes is only visible to a heuristic built after it.
		java.util.function.Supplier<LDSearch> factory = () -> {
			Supplier<Procedure[]> branching = (heuristic == BranchingHeuristic.MNERTB)
					? minEntropyRandomTieBreak(vars) : minEntropy(vars);
			LDSearch s = makeLds(minicp, branching);
			s.onSolution(() -> {
				foundSolution.set(true);
				String solutionStr = frontend.getSolutionStr(extractSolutionStr);
				if (enumerateSolutions) {
					solutionStrs.add(solutionStr);
					trueMarginals.addSolution(vars);
				} else {
					// No need to keep other solutions, only keep latest
					solutionStrs.add(0, solutionStr);
				}
			});
			return s;
		};

		DovetailSearch.Result r = DovetailSearch.run(minicp, factory,
				foundSolution::get, arms, ladder, globalBudgetMs, calibBudgetMs, maxPasses);
		if (foundSolution.get() && solFileStr != null && !solFileStr.isEmpty())
			printSolutions(solutionStrs);
		return r;
	}

	public void parseCli(String[] args) {
		String quotedValidBranchings = branchingMap.keySet().stream().sorted().map(x -> "\"" + x + "\"")
				.collect(Collectors.joining(",\n"));

		String quotedValidSearchTypes = searchTypeMap.keySet().stream().sorted().map(x -> "\"" + x + "\"")
				.collect(Collectors.joining(",\n"));

		Option xcspFileOpt = Option.builder().longOpt("input").argName("FILE").required().hasArg()
				.desc("input FZN or XCSP file").build();

		Option branchingOpt = Option.builder().longOpt("branching").argName("STRATEGY").required().hasArg()
				.desc("branching strategy.\nValid branching strategies are:\n" + quotedValidBranchings).build();

		Option searchOpt = Option.builder().longOpt("search-type").argName("SEARCH").required().hasArg()
				.desc("search type.\nValid search types are:\n" + quotedValidSearchTypes).build();

		Option timeoutOpt = Option.builder().longOpt("timeout").argName("SECONDS").required().hasArg()
				.desc("timeout in seconds").build();

		Option statsFileOpt = Option.builder().longOpt("stats").argName("FILE").hasArg()
				.desc("file for storing the statistics").build();

		Option solFileOpt = Option.builder().longOpt("solution").argName("FILE").hasArg()
				.desc("file for storing the solution").build();

		Option maxIterOpt = Option.builder().longOpt("max-iter").argName("ITERATIONS").hasArg()
				.desc("maximum number of belief propagation iterations").build();

		Option dFactorOpt = Option.builder().longOpt("damping-factor").argName("LAMBDA").hasArg()
				.desc("the damping factor used for damping the messages").build();

		Option checkOpt = Option.builder().longOpt("verify").hasArg(false)
				.desc("check the correctness of obtained solution").build();

		Option dampOpt = Option.builder().longOpt("damp-messages").hasArg(false).desc("damp messages").build();

		Option traceBPOpt = Option.builder().longOpt("trace-bp").hasArg(false)
				.desc("trace the belief propagation progress").build();

		Option traceSearchOpt = Option.builder().longOpt("trace-search").hasArg(false).desc("trace the search progress")
				.build();

		Option restartSearchOpt = Option.builder().longOpt("restart").hasArg(false).desc("authorized restart during search (available with dfs only)")
				.build();
		Option nbFailsCutofOpt = Option.builder().longOpt("cutoff").argName("CUTOF").hasArg()
				.desc("number of failure before restart").build();

		Option restartFactorOpt = Option.builder().longOpt("restart-factor").argName("restartFactor").hasArg()
				.desc("factor to increase number of failure before restart").build();

		Option variationThresholdOpt = Option.builder().longOpt("var-threshold").argName("variationThreshold").hasArg()
				.desc("threshold on entropy's variation under to stop belief propagation").build();

		Option initImpactOpt = Option.builder().longOpt("init-impact").hasArg(false).desc("initialize impact before search")
				.build();

		Option dynamicStopBPOpt = Option.builder().longOpt("dynamic-stop").hasArg(false).desc("BP iterations are stopped dynamically instead of a fixed number of iteration")
				.build();

		Option traceNbIterOpt = Option.builder().longOpt("trace-iter").hasArg(false).desc("trace the number of BP iterations before each branching")
				.build();

		Option traceEntropyOpt = Option.builder().longOpt("trace-entropy").hasArg(false).desc("trace the evolution of model's entropy after each BP iteration")
				.build();

		Option enumerateSolutionsOpt = Option.builder().longOpt("enumerate-solutions").hasArg(false).desc("search for all solutions and print true marginals (only for CSPs)")
				.build();

		Options options = new Options();
		options.addOption(xcspFileOpt);
		options.addOption(branchingOpt);
		options.addOption(searchOpt);
		options.addOption(timeoutOpt);
		options.addOption(statsFileOpt);
		options.addOption(solFileOpt);
		options.addOption(maxIterOpt);
		options.addOption(checkOpt);
		options.addOption(traceBPOpt);
		options.addOption(traceSearchOpt);
		options.addOption(dampOpt);
		options.addOption(dFactorOpt);
		options.addOption(restartSearchOpt);
		options.addOption(nbFailsCutofOpt);
		options.addOption(restartFactorOpt);
		options.addOption(variationThresholdOpt);
		options.addOption(initImpactOpt);
		options.addOption(dynamicStopBPOpt);
		options.addOption(traceNbIterOpt);
		options.addOption(traceEntropyOpt);
		options.addOption(enumerateSolutionsOpt);

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException exp) {
			Log.error(exp.getMessage());
			new HelpFormatter().printHelp("solve-XCSP", options);
			System.exit(1);
		}

		String branchingStr = cmd.getOptionValue("branching");
		checkBranchingOption(branchingStr);
		heuristic = branchingMap.get(branchingStr);

		String searchTypeStr = cmd.getOptionValue("search-type");
		checkSearchTypeOption(searchTypeStr);
		searchType = searchTypeMap.get(searchTypeStr);


		inputStr = cmd.getOptionValue("input");
		checkInputOption(inputStr);

		String timeoutStr = cmd.getOptionValue("timeout");
		timeout = checkTimeoutOption(timeoutStr);

		if (cmd.hasOption("stats")) {
			statsFileStr = cmd.getOptionValue("stats");
			checkCreateFile(statsFileStr);
		}

		if (cmd.hasOption("solution")) {
			solFileStr = cmd.getOptionValue("solution");
			checkCreateFile(solFileStr);
		}

		if (cmd.hasOption("max-iter"))
			maxIter = Integer.parseInt(cmd.getOptionValue("max-iter"));

		if (cmd.hasOption("damping-factor"))
			dampingFactor = Double.parseDouble(cmd.getOptionValue("damping-factor"));

		if(cmd.hasOption("cutoff"))
			nbFailCutof = Integer.parseInt(cmd.getOptionValue("cutoff"));

		if(cmd.hasOption("restart-factor"))
			restartFactor = Double.parseDouble(cmd.getOptionValue("restart-factor"));

		if(cmd.hasOption("var-threshold"))
			variationThreshold = Double.parseDouble(cmd.getOptionValue("var-threshold"));

		checkSolution = (cmd.hasOption("verify"));
		damp = (cmd.hasOption("damp-messages"));
		restart = (cmd.hasOption("restart"));
		initImpact = (cmd.hasOption("init-impact"));
		dynamicStopBP = (cmd.hasOption("dynamic-stop"));
		enumerateSolutions = (cmd.hasOption("enumerate-solutions"));

		traceBP = (cmd.hasOption("trace-bp"));
		traceSearch = (cmd.hasOption("trace-search"));
		traceNbIter = (cmd.hasOption("trace-iter"));
		traceEntropy = (cmd.hasOption("trace-entropy"));
	}

	public static void main(String[] args) {
		SolveXCSPFZN app = new SolveXCSPFZN();
		app.parseCli(args);
		app.solve();
	}

	public Solver getSolver() {
		return minicp;
	}

	public void setTraceBP(boolean traceBP) {
		this.traceBP = traceBP;
	}
	public void setTraceSearch(boolean traceSearch) {
		this.traceSearch = traceSearch;
	}
	public void setTraceEntropy(boolean traceEntropy) {
		this.traceEntropy = traceEntropy;
	}
	public void setInputStr(String inputStr) {
		this.inputStr = inputStr;
	}
	public void setHeuristic(BranchingHeuristic heuristic) {
		this.heuristic = heuristic;
	}
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}
	public void setSearchType(TreeSearchType searchType) {
		this.searchType = searchType;
	}
	public void setCheckSolution(boolean checkSolution) {
		this.checkSolution = checkSolution;
	}
	public void setStatsFileStr(String statsFileStr) {
		this.statsFileStr = statsFileStr;
	}
	public void setSolFileStr(String solFileStr) {
		this.solFileStr = solFileStr;
	}
	public void setMaxIter(int maxIter) {
		this.maxIter = maxIter;
	}
	public void setRestart(boolean restart) {
		this.restart = restart;
	}
	public void setNbFailCutof(int nbFailCutof) {
		this.nbFailCutof = nbFailCutof;
	}
	public void setRestartFactor(double restartFactor) {
		this.restartFactor = restartFactor;
	}
	public void setInitImpact(boolean initImpact) {
		this.initImpact = initImpact;
	}
	public void setEnumerateSolutions(boolean enumerateSolutions) {
		this.enumerateSolutions = enumerateSolutions;
	}

	/**
	 * Creates a search (either DFS or LDS) with a given branching heuristic
	 * @param branching a branching heuristic
	 * @return a search object
	 */
	private Search makeSearch(Supplier<Procedure[]> branching) {
		Search search = null;
		switch (searchType) {
			case DFS:
				search = makeDfs(minicp, branching);
				break;
			case LDS:
				search = makeLds(minicp, branching);
				break;
			default:
				System.out.println("unknown search type");
				System.exit(1);
		}
		return search;
	}

	private void printSolutions(List<String> solutionStrs) {
		if (!Objects.equals(solFileStr, "")) {
			try {
				PrintWriter out = new PrintWriter(new File(solFileStr));
				for (String solutionStr : solutionStrs) {
					out.print(solutionStr);
				}
				out.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				System.out.println("unable to create file " + solFileStr);
				System.exit(1);
			}
		}

	}

	private static void checkBranchingOption(String branchingStr) {

		if (!branchingMap.containsKey(branchingStr)) {
			Log.info("invalid branching strategy " + branchingStr);
			Log.info("Branching strategy should be one of the following: ");
			for (String branching : branchingMap.keySet())
				Log.info(branching);
			System.exit(1);
		}
	}

	private static void checkSearchTypeOption(String searchTypeStr) {

		if (!searchTypeMap.containsKey(searchTypeStr)) {
			Log.info("invalid search type " + searchTypeStr);
			Log.info("Search type should be one of the following: ");
			for (String branching : searchTypeMap.keySet())
				Log.info(branching);
			System.exit(1);
		}
	}

	private static void checkInputOption(String inputStr) {
		File inputFile = new File(inputStr);
		if (!inputFile.exists()) {
			Log.info("input file " + inputStr + " does not exist!");
			System.exit(1);
		}
	}

	private static int checkTimeoutOption(String timeoutStr) {
		Integer timeout = null;
		try {
			timeout = Integer.valueOf(timeoutStr);
		} catch (NumberFormatException e) {
			e.printStackTrace();
			Log.info("invalid timeout string " + timeoutStr);
			System.exit(1);
		}

		if (timeout < 0 || timeout > Integer.MAX_VALUE) {
			Log.info("invalid timeout " + timeout);
			System.exit(1);
		}

		return timeout.intValue();
	}

	private static void checkCreateFile(String filename) {
		File f = new File(filename);
		if (f.exists())
			f.delete();
		try {
			if (!f.createNewFile()) {
				Log.info("can not create file " + filename);
				System.exit(1);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.info("can not create file " + filename);
			System.exit(1);
		}
	}
}

