package launch;

import fzn.FZN;
import minicpbp.engine.core.Solver;
import org.apache.commons.cli.*;
import xcsp.XCSP;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SolveXCSPFZN {
    public static Map<String, Solver.BPAlgorithm> algorithmMap = new HashMap<String, Solver.BPAlgorithm>() {
        private static final long serialVersionUID = 4936849715939593675L;

        {
            put("sum-product", Solver.BPAlgorithm.SUM_PRODUCT);
            put("max-product", Solver.BPAlgorithm.MAX_PRODUCT);
        }
    };

    public static Map<String, Boolean> BoolMap = new HashMap<String, Boolean>() {
        private static final long serialVersionUID = 4936849715939593675L;

        {
            put("True", true);
            put("true", true);

            put("False", false);
            put("false", false);
        }
    };

    public enum BranchingHeuristic {
        FFRV, // first-fail, random value
        MXMS, // maximum marginal strength
        MNMS, // minimum marginal strength
        MXMR, // maximum marginal regret
        MXM, // maximum marginal
        MNM, // minimum marginal
        MNE, //minimum entropy
        IE, //impact entropy
        MIE, //min-entropy followed by impact entropy after first restart,
        MNEBW, //min-entropy with biased wheel value selection
        WDEG, //dom-wdeg
        WDEGMXMR,
        MNEDWDEG, // min entropy with dom wdeg fallback
        MNNE, // min normalized entropy
        MNNEDWDEG, // min normalized entropy with dom wdeg fallback
        IBS, //impact-based search
    }

    private static Map<String, BranchingHeuristic> branchingMap = new HashMap<String, BranchingHeuristic>() {
        private static final long serialVersionUID = 4936849715939593675L;

        {
            put("first-fail-random-value", BranchingHeuristic.FFRV);
            put("max-marginal-strength", BranchingHeuristic.MXMS);
            put("min-marginal-strength", BranchingHeuristic.MNMS);
            put("max-marginal-regret", BranchingHeuristic.MXMR);
            put("max-marginal", BranchingHeuristic.MXM);
            put("min-marginal", BranchingHeuristic.MNM);
            put("min-entropy", BranchingHeuristic.MNE);
            put("impact-entropy", BranchingHeuristic.IE);
            put("impact-min-entropy", BranchingHeuristic.MIE);
            put("min-entropy-biased", BranchingHeuristic.MNEBW);
            put("dom-wdeg", BranchingHeuristic.WDEG);
            put("dom-wdeg-max-marginal", BranchingHeuristic.WDEGMXMR);
            put("min-entropy-dom-wdeg", BranchingHeuristic.MNEDWDEG);
            put("min-normalized-entropy", BranchingHeuristic.MNNE);
            put("min-normalized-entropy-dom-wdeg", BranchingHeuristic.MNNEDWDEG);
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

    public static void main(String[] args) {
        String quotedValidBPAlgorithms = algorithmMap.keySet().stream().sorted().map(x -> "\"" + x + "\"")
                .collect(Collectors.joining(",\n"));

        String quotedValidSwitchToSumProductAfterSolution = BoolMap.keySet().stream().sorted().map(x -> "\"" + x + "\"")
                .collect(Collectors.joining(",\n"));

        String quotedValidPropagationShortcut = BoolMap.keySet().stream().sorted().map(x -> "\"" + x + "\"")
                .collect(Collectors.joining(",\n"));

        String quotedValidSkipUniformMaxProd = BoolMap.keySet().stream().sorted().map(x -> "\"" + x + "\"")
                .collect(Collectors.joining(",\n"));

        String quotedValidResetMarginalsBeforeBP = BoolMap.keySet().stream().sorted().map(x -> "\"" + x + "\"")
                .collect(Collectors.joining(",\n"));

        String quotedValidFasterAllDiff = BoolMap.keySet().stream().sorted().map(x -> "\"" + x + "\"")
                .collect(Collectors.joining(",\n"));

        String quotedValidBranchings = branchingMap.keySet().stream().sorted().map(x -> "\"" + x + "\"")
                .collect(Collectors.joining(",\n"));

        String quotedValidSearchTypes = searchTypeMap.keySet().stream().sorted().map(x -> "\"" + x + "\"")
                .collect(Collectors.joining(",\n"));

        Option xcspFileOpt = Option.builder().longOpt("input").argName("FILE").required().hasArg()
                .desc("input FZN or XCSP file").build();

        Option bpAlgorithmOpt = Option.builder().longOpt("bp-algorithm").argName("ALGORITHM").required().hasArg()
                .desc("BP algorithm.\nValid BP algorithms are:\n" + quotedValidBPAlgorithms).build();

        Option oracleOnObjectiveOpt = Option.builder().longOpt("oracle-on-objective").argName("ORACLE").hasArg().type(Float.class)
                .desc("oracle on objective.\nValid oracle on objective are floats").build();

        Option branchingOpt = Option.builder().longOpt("branching").argName("STRATEGY").required().hasArg()
                .desc("branching strategy.\nValid branching strategies are:\n" + quotedValidBranchings).build();

        Option entropyBranchingThresholdOpt = Option.builder().longOpt("entropy-branching-threshold").argName("FLOAT").hasArg()
                .desc("entropy branching threshold.\nValid entropy branching threshold are floats").build();

        Option propagationShortcutOpt = Option.builder().longOpt("propagation-shortcut").argName("BOOL").hasArg()
                .desc("propagation shortcut.\nValid propagation shortcut are:\n" + quotedValidPropagationShortcut).build();

        Option skipUniformMaxProdOpt = Option.builder().longOpt("skip-uniform-max-prod").argName("BOOL").hasArg()
                .desc("skip uniform max product.\nValid skip uniform max prod are:\n" + quotedValidSkipUniformMaxProd).build();

        Option resetMarginalsBeforeBPOpt = Option.builder().longOpt("reset-marginals-before-bp").argName("BOOL").hasArg()
                .desc("reset marginals before BP.\nValid reset marginals before BP are:\n" + quotedValidResetMarginalsBeforeBP).build();

        Option fasterAllDiffOpt = Option.builder().longOpt("faster-all-diff-max-prod").argName("BOOL").hasArg()
                .desc("faster all different algorithm.\nValid faster all diff are:\n" + quotedValidFasterAllDiff).build();

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


        Options options = new Options();
        options.addOption(xcspFileOpt);
        options.addOption(bpAlgorithmOpt);
        options.addOption(oracleOnObjectiveOpt);
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
        options.addOption(entropyBranchingThresholdOpt);
        options.addOption(propagationShortcutOpt);
        options.addOption(skipUniformMaxProdOpt);
        options.addOption(resetMarginalsBeforeBPOpt);
        options.addOption(fasterAllDiffOpt);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException exp) {
            System.err.println(exp.getMessage());
            new HelpFormatter().printHelp("solve-XCSP", options);
            System.exit(1);
        }

        String bpAlgorithmStr = cmd.getOptionValue("bp-algorithm");
        checkBPAlgorithmOption(bpAlgorithmStr);
        Solver.BPAlgorithm bpAlgorithm = null;
        if (bpAlgorithmStr != "no-bp")
            bpAlgorithm = algorithmMap.get(bpAlgorithmStr);

        String oracleOnObjectiveStr = cmd.getOptionValue("oracle-on-objective", "0");
        checkOracleOnObjectiveOption(oracleOnObjectiveStr);
        float oracleOnObjective = Float.parseFloat(oracleOnObjectiveStr);


        String branchingStr = cmd.getOptionValue("branching");
        checkBranchingOption(branchingStr);
        BranchingHeuristic heuristic = branchingMap.get(branchingStr);

        String entropyBranchingThresholdStr = cmd.getOptionValue("entropy-branching-threshold", "2.0");
        Double entropyBranchingThreshold = parseEntropyBranchingThresholdOption(entropyBranchingThresholdStr);

        String bpShortcutStr = cmd.getOptionValue("propagation-shortcut");
        boolean propagationShortcut = true;
        if (bpShortcutStr != null) {
            checkPropagationShortcutOption(bpShortcutStr);
            propagationShortcut = BoolMap.get(bpShortcutStr);
        }

        String skipUniformMaxProdStr = cmd.getOptionValue("skip-uniform-max-prod");
        boolean skipUniformMaxProd = true;
        if (skipUniformMaxProdStr != null) {
            checkSkipUniformMaxProdOption(skipUniformMaxProdStr);
            skipUniformMaxProd = BoolMap.get(skipUniformMaxProdStr);
        }

        String resetMarginalsBeforeBPStr = cmd.getOptionValue("reset-marginals-before-bp");
        boolean resetMarginalsBeforeBP = true;
        if (resetMarginalsBeforeBPStr != null) {
            checkResetMarginalsBeforeBPOption(resetMarginalsBeforeBPStr);
            resetMarginalsBeforeBP = BoolMap.get(resetMarginalsBeforeBPStr);
        }

        String fasterAllDiffStr = cmd.getOptionValue("faster-all-diff");
        boolean fasterAllDiff = false;
        if (fasterAllDiffStr != null) {
            checkFasterAllDiffOption(fasterAllDiffStr);
            fasterAllDiff = BoolMap.get(fasterAllDiffStr);
        }

        String searchTypeStr = cmd.getOptionValue("search-type");
        checkSearchTypeOption(searchTypeStr);
        TreeSearchType searchType = searchTypeMap.get(searchTypeStr);


        String inputStr = cmd.getOptionValue("input");
        checkInputOption(inputStr);

        String timeoutStr = cmd.getOptionValue("timeout");
        int timeout = checkTimeoutOption(timeoutStr);

        String statsFileStr = "";
        if (cmd.hasOption("stats")) {
            statsFileStr = cmd.getOptionValue("stats");
            checkCreateFile(statsFileStr);
        }

        String solFileStr = "";
        if (cmd.hasOption("solution")) {
            solFileStr = cmd.getOptionValue("solution");
            checkCreateFile(solFileStr);
        }

        int maxIter = 10;
        if (cmd.hasOption("max-iter"))
            maxIter = Integer.parseInt(cmd.getOptionValue("max-iter"));

        double dampingFactor = 0.5;
        if (cmd.hasOption("damping-factor"))
            dampingFactor = Double.parseDouble(cmd.getOptionValue("damping-factor"));

        int nbFailCutof = 100;
        if (cmd.hasOption("cutoff"))
            nbFailCutof = Integer.parseInt(cmd.getOptionValue("cutoff"));

        double restartFactor = 1.5;
        if (cmd.hasOption("restart-factor"))
            restartFactor = Double.parseDouble(cmd.getOptionValue("restart-factor"));

        double variationThreshold = -Double.MAX_VALUE;
        if (cmd.hasOption("var-threshold"))
            variationThreshold = Double.parseDouble(cmd.getOptionValue("var-threshold"));

        boolean checkSolution = (cmd.hasOption("verify"));
        boolean traceBP = (cmd.hasOption("trace-bp"));
        boolean traceSearch = (cmd.hasOption("trace-search"));
        boolean damp = (cmd.hasOption("damp-messages"));
        boolean restart = (cmd.hasOption("restart"));
        boolean initImpact = (cmd.hasOption("init-impact"));
        boolean dynamicStopBP = (cmd.hasOption("dynamic-stop"));
        boolean traceNbIter = (cmd.hasOption("trace-iter"));
        boolean traceEntropy = (cmd.hasOption("trace-entropy"));


        try {
            // System.out.println(inputStr.substring(inputStr.lastIndexOf('.') + 1));
            if (inputStr.substring(inputStr.lastIndexOf('.') + 1).equals("fzn")) {
                FZN fzn = new FZN(inputStr);
                fzn.searchType(searchType);
                fzn.checkSolution(checkSolution);
                fzn.traceBP(traceBP);
                fzn.traceSearch(traceSearch);
                fzn.maxIter(maxIter);
                fzn.damp(damp);
                fzn.dampingFactor(dampingFactor);
                fzn.restart(restart);
                fzn.nbFailCutof(nbFailCutof);
                fzn.restartFactor(restartFactor);
                fzn.variationThreshold(variationThreshold);
                fzn.initImpact(initImpact);
                fzn.dynamicStopBP(dynamicStopBP);
                fzn.traceNbIter(traceNbIter);
                fzn.printStats(true);
                // TODO set BP algorithm
                fzn.solve(heuristic, timeout, statsFileStr, solFileStr);
            } else {
                System.out.println("input file: " + inputStr);
                System.out.println("BP algorithm: " + bpAlgorithmStr);
                System.out.println("oracle on objective: " + oracleOnObjective);
                System.out.println("branching strategy: " + branchingStr);
                System.out.println("entropy branching threshold: " + entropyBranchingThreshold);
                System.out.println("propagation shortcut: " + propagationShortcut);
                System.out.println("skip uniform max product: " + skipUniformMaxProd);
                System.out.println("reset marginals before BP: " + resetMarginalsBeforeBP);
                System.out.println("faster all diff: " + fasterAllDiff);
                System.out.println("search type: " + searchTypeStr);
                System.out.println("max iterations: " + maxIter);

                XCSP xcsp = new XCSP(inputStr);
                xcsp.searchType(searchType);
                xcsp.checkSolution(checkSolution);
                xcsp.traceBP(traceBP);
                xcsp.traceSearch(traceSearch);
                xcsp.maxIter(maxIter);
                xcsp.damp(damp);
                xcsp.dampingFactor(dampingFactor);
                xcsp.restart(restart);
                xcsp.nbFailCutof(nbFailCutof);
                xcsp.restartFactor(restartFactor);
                xcsp.variationThreshold(variationThreshold);
                xcsp.initImpact(initImpact);
                xcsp.dynamicStopBP(dynamicStopBP);
                xcsp.traceNbIter(traceNbIter);
                xcsp.traceEntropy(traceEntropy);
                xcsp.BPAlgorithm(bpAlgorithm);
                xcsp.oracleOnObjective(oracleOnObjective);
                xcsp.entropyBranchingThreshold(entropyBranchingThreshold);
                xcsp.propagationShortcut(propagationShortcut);
                xcsp.skipUniformMaxProd(skipUniformMaxProd);
                xcsp.resetMarginalsBeforeBP(resetMarginalsBeforeBP);
                xcsp.fasterAllDiff(fasterAllDiff);

                xcsp.solve(heuristic, timeout, statsFileStr, solFileStr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void checkBPAlgorithmOption(String bpAlgorithmStr) {
        if (!algorithmMap.containsKey(bpAlgorithmStr) && !bpAlgorithmStr.equals("no-bp")) {
            System.out.println("invalid BP algorithm " + bpAlgorithmStr);
            System.out.println("BP algorithm should be one of the following: ");
            System.out.println("no-bp");
            for (String bpAlgorithm : algorithmMap.keySet())
                System.out.println(bpAlgorithm);
            System.exit(1);
        }
    }

    private static void checkOracleOnObjectiveOption(String oracleOnObjectiveStr) {
        if (Float.isNaN(Float.parseFloat(oracleOnObjectiveStr))) {
            System.out.println("invalid oracle on objective " + oracleOnObjectiveStr);
            System.out.println("oracle on objective should be a float number");
            System.exit(1);
        }
    }

    private static Double parseEntropyBranchingThresholdOption(String entropyBranchingThresholdStr) {
        if (entropyBranchingThresholdStr.equals("null") || entropyBranchingThresholdStr.equals("None")) {
            return null;
        }
        Double entropyBranchingThreshold = null;
        entropyBranchingThreshold = Double.parseDouble(entropyBranchingThresholdStr);
        if (Double.isNaN(entropyBranchingThreshold)) {
            System.out.println("invalid entropy branching threshold " + entropyBranchingThresholdStr);
            System.out.println("entropy branching threshold should be a float number");
            System.exit(1);
            return null;
        } else {
            return entropyBranchingThreshold;
        }
    }


    private static void checkPropagationShortcutOption(String propagationShortcutStr) {
        if (!BoolMap.containsKey(propagationShortcutStr)) {
            System.out.println("invalid propagation shortcut " + propagationShortcutStr);
            System.out.println("propagation shortcut should be one of the following: ");
            for (String propagationShortcut : BoolMap.keySet())
                System.out.println(propagationShortcut);
            System.exit(1);
        }
    }

    private static void checkSkipUniformMaxProdOption(String skipUniformMaxProdStr) {
        if (!BoolMap.containsKey(skipUniformMaxProdStr)) {
            System.out.println("invalid skip uniform max prod " + skipUniformMaxProdStr);
            System.out.println("skip uniform max prod should be one of the following: ");
            for (String skipUniformMaxProd : BoolMap.keySet())
                System.out.println(skipUniformMaxProd);
            System.exit(1);
        }
    }

    private static void checkResetMarginalsBeforeBPOption(String resetMarginalsBeforeBPStr) {
        if (!BoolMap.containsKey(resetMarginalsBeforeBPStr)) {
            System.out.println("invalid reset marginals before BP " + resetMarginalsBeforeBPStr);
            System.out.println("reset marginals before BP should be one of the following: ");
            for (String resetMarginalsBeforeBP : BoolMap.keySet())
                System.out.println(resetMarginalsBeforeBP);
            System.exit(1);
        }
    }

    private static void checkFasterAllDiffOption(String fasterAllDiffStr) {
        if (!BoolMap.containsKey(fasterAllDiffStr)) {
            System.out.println("invalid faster all diff " + fasterAllDiffStr);
            System.out.println("faster all diff should be one of the following: ");
            for (String fasterAllDiff : BoolMap.keySet())
                System.out.println(fasterAllDiff);
            System.exit(1);
        }
    }

    private static void checkBranchingOption(String branchingStr) {

        if (!branchingMap.containsKey(branchingStr)) {
            System.out.println("invalid branching strategy " + branchingStr);
            System.out.println("Branching strategy should be one of the following: ");
            for (String branching : branchingMap.keySet())
                System.out.println(branching);
            System.exit(1);
        }
    }

    private static void checkSearchTypeOption(String searchTypeStr) {

        if (!searchTypeMap.containsKey(searchTypeStr)) {
            System.out.println("invalid search type " + searchTypeStr);
            System.out.println("Search type should be one of the following: ");
            for (String branching : searchTypeMap.keySet())
                System.out.println(branching);
            System.exit(1);
        }
    }

    private static void checkInputOption(String inputStr) {
        File inputFile = new File(inputStr);
        if (!inputFile.exists()) {
            System.out.println("input file " + inputStr + " does not exist!");
            System.exit(1);
        }
    }

    private static int checkTimeoutOption(String timeoutStr) {
        Integer timeout = null;
        try {
            timeout = Integer.valueOf(timeoutStr);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            System.out.println("invalid timeout string " + timeoutStr);
            System.exit(1);
        }

        if (timeout < 0 || timeout > Integer.MAX_VALUE) {
            System.out.println("invalid timeout " + timeout);
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
                System.out.println("can not create file " + filename);
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("can not create file " + filename);
            System.exit(1);
        }
    }

}
