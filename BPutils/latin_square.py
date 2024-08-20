import os
import pickle
import random
import re
from pathlib import Path
from typing import Any, Callable, Dict, List, Tuple

import matplotlib.pyplot as plt
import numpy as np
import scipy.stats as stats
from square_gen import rls

# Type aliases for clarity
Solution = List[List[int]]
Beliefs = List[List[Dict[int, float]]]
Marginals = List[List[Dict[int, float]]]
Ranks = List[List[Dict[int, int]]]

MAX_MARGINAL = "maxMarginal"
MAX_MARGINAL_REGRET_RANDOM_TIE_BREAK = "maxMarginalRegretRandomTieBreak"
FIRST_FAIL_RANDOM_VAL = "firstFailRandomVal"

MAX_PRODUCT_ORACLE = "max-product-oracle"
MAX_PRODUCT_INIT = "max-product-init"
MAX_PRODUCT_INIT_EXP = "max-product-init-exp"
SUM_PRODUCT_ORACLE = "sum-product-oracle"
SUM_PRODUCT_INIT = "sum-product-init"
SUM_PRODUCT_INIT_EXP = "sum-product-init-exp"
SUM_PRODUCT_NO_INIT = "sum-product-no-init"
MAX_PRODUCT_OBJECTIVE_DIAGONAL = "max-product-objective-diagonal"

INSTANCE_NUMBER = 100

SOLUTION_NB = "number of solutions"
KL_DIV_WEIGHTED_UTILITY = "kl divergence /w weighted utility marginals"
KL_DIV_REAL = "kl divergence /w real marginals"
KL_DIV_MAX_UTILITY = "kl divergence /w max utility marginals"
RANK_FOUND_SOLUTION = "rank of first solution found"

PLOT_COLORS = {
    MAX_PRODUCT_INIT: "blue",
    MAX_PRODUCT_ORACLE: "orange",
    SUM_PRODUCT_INIT: "green",
    SUM_PRODUCT_ORACLE: "red",
    SUM_PRODUCT_NO_INIT: "purple",
}

METRICS = [
    RANK_FOUND_SOLUTION,
]

RANK = "rank"

###############################################################################
# OLD CODE
###############################################################################

MODELS = ["max-product-oracle", "sum-product-oracle"]


def all_metrics_on_instance_suite(n, models=MODELS, metrics=METRICS):
    return [
        (
            len(solutions := load_all_solutions(n, filled, file_number)),
            metrics_on_instance(
                solutions, load_beliefs(n, filled, file_number, models), metrics, models
            ),
        )
        for filled in filled_list(n)
        for file_number in range(1, INSTANCE_NUMBER + 1)
    ]


def index_of_best_solution_on_instance_suite(
    n, models=MODELS, branchingScheme=MAX_MARGINAL
):
    return [
        (
            len(solutions),
            {
                model: solution_index_in_list(
                    solutions,
                    load_found_solution(n, filled, file_number, model, branchingScheme),
                )
                for model in models
            },
        )
        for filled in filled_list(n)
        for file_number in range(1, INSTANCE_NUMBER + 1)
        for filled in filled_list(n)
        for file_number in range(1, INSTANCE_NUMBER + 1)
        if len(solutions := load_all_found_solutions(n, filled, file_number))
    ]


def solution_index_in_list(solutions, solution):
    for i, s in enumerate(solutions):
        if same_solution(s, solution):
            return i


def same_solution(s1, s2):
    return all(s1[i][j] == s2[i][j] for i in range(len(s1)) for j in range(len(s1)))


def metrics_on_instance(solutions, beliefs, metrics, models):
    n = len(solutions[0])
    utility_ranks = get_real_utility_ranks(solutions)
    instance_real_marginals = real_marginals(solutions)
    instance_weighted_marginals = marginals_weighted_by_utility(solutions)
    instance_max_utility_marginals = max_utility_marginals(solutions)

    result = {}
    for metric in metrics:
        if metric == ERROR_NB:

            def m_fun(b):
                return compare_rank_and_belief(n, utility_ranks, b)

        elif metric == KL_DIV_REAL:

            def m_fun(b):
                return mean_kl_divergence(instance_real_marginals, b)

        elif metric == KL_DIV_WEIGHTED_UTILITY:

            def m_fun(b):
                return mean_kl_divergence(instance_weighted_marginals, b)

        elif metric == KL_DIV_MAX_UTILITY:

            def m_fun(b):
                return mean_kl_divergence(instance_max_utility_marginals, b)

        result[metric] = {model: m_fun(beliefs[model]) for model in models}

    return result


def load_beliefs(n, filled, file_number, models):
    return {
        model: parse_output_file(n, filled, file_number, model)[-1] for model in models
    }


def plot_metrics_on_instance_suite(
    n, models=MODELS, metrics=METRICS, output_dir="plots"
):
    data = all_metrics_on_instance_suite(n, models, metrics)

    # Create output directory if it doesn't exist
    os.makedirs(output_dir, exist_ok=True)

    # Initialize dictionaries to store plots, solutions, and errors for each metric
    plots = {}
    solutions = {metric: {model: [] for model in models} for metric in metrics}
    errors = {metric: {model: [] for model in models} for metric in metrics}

    # Process the data
    for num_solutions, metric_dict in data:
        for metric, model_errors in metric_dict.items():
            if metric not in plots:
                fig, ax = plt.subplots(figsize=(10, 6))
                ax.set_xlabel("Number of Solutions")
                ax.set_ylabel(metric)
                ax.set_title(f"{metric} vs Solutions")
                ax.grid(True)
                plots[metric] = (fig, ax)

            for model, num_errors in model_errors.items():
                solutions[metric][model].append(num_solutions)
                errors[metric][model].append(num_errors)

    # Plot data and finalize plots
    for metric, (fig, ax) in plots.items():
        for model in models:
            ax.scatter(
                solutions[metric][model],
                errors[metric][model],
                label=model,
                marker="o",
                color=PLOT_COLORS[model],
            )

        ax.legend()
        metric_filename = metric.replace(" ", "_").replace("/", "_")
        nb_data_points = len(data)
        filename = f"{output_dir}/plot_{n}_{metric_filename}_{nb_data_points}.png"
        fig.savefig(filename)
        plt.close(fig)  # Close the figure to free up memory
        print(f"Saved plot: {filename}")


###############################################################################
# END OLD CODE
###############################################################################


# --- PLOTS ---
def plot_model_metrics(
    results: List[Dict[str, Any]],
    metric: str,
    title: str,
    output_path: Path,
    x_key: str = SOLUTION_NB,
    y_label: str = None,
):
    """
    Plot a specific metric for each model.

    Args:
    results (List[Dict[str, Any]]): List of results to plot.
    metric (str): The metric to plot.
    title (str): Title of the plot.
    output_path (Path): Path to save the plot to.
    x_key (str): Key to use for the x-axis. Defaults to SOLUTION_NB.
    y_label (str): Label for the y-axis. If None, uses the metric name.
    """
    plt.figure(figsize=(10, 6))

    models = set(result["model"] for result in results)
    for model in models:
        model_results = [r for r in results if r["model"] == model]
        x_values = [r[x_key] for r in model_results]
        y_values = [r[metric] for r in model_results]
        plt.scatter(
            x_values,
            y_values,
            label=model,
            color=PLOT_COLORS.get(model, None),
            marker="o",
        )

    plt.xlabel("Number of Solutions" if x_key == SOLUTION_NB else x_key)
    plt.ylabel(y_label or metric)
    plt.title(title)
    plt.legend()
    plt.grid(True)
    plt.savefig(output_path)
    plt.close()


def generate_metric_plots(n: int, models: List[str], output_dir: str):
    """
    Generate plots for all metrics for different models.

    Args:
    n (int): Side length of the square grid.
    models (List[str]): List of models to compare.
    branching_scheme (str): Branching scheme to use.
    output_dir (str): Directory to save the plots to.
    """
    branching_scheme = MAX_MARGINAL
    results = process_instance_suite(n, models, [branching_scheme])

    metrics = [KL_DIV_REAL, KL_DIV_WEIGHTED_UTILITY, KL_DIV_MAX_UTILITY]

    for metric in metrics:
        title = f"{metric} for Different Models\n(Branching Scheme: {branching_scheme})"
        output_path = (
            Path(output_dir)
            / f"{metric.replace(' ', '_').replace('/', '')}_n{n}_{branching_scheme}.png"
        )
        plot_model_metrics(results, metric, title, output_path)


def process_instance_suite(
    n: int, models: List[str], branching_schemes: List[str]
) -> List[Dict[str, Any]]:
    """
    Process all instances for a given set of models and branching schemes.

    Args:
    n (int): Side length of the square grid.
    models (List[str]): List of models to use.
    branching_schemes (List[str]): List of branching schemes to use.

    Returns:
    List[Dict[str, Any]]: List of results for each instance.
    """
    results = []
    for filled in filled_list(n):
        for file_number in range(1, INSTANCE_NUMBER + 1):
            solutions = load_all_solutions(n, filled, file_number)
            if len(solutions):  # < 8000
                model_beliefs = {
                    model: load_beliefs(n, filled, file_number, model)[
                        -1
                    ]  # Use the last iteration
                    for model in models
                }
                metrics = process_results(solutions, model_beliefs)

                for model in models:
                    for scheme in branching_schemes:
                        result = {
                            SOLUTION_NB: len(solutions),
                            "model": model,
                            "branching_scheme": scheme,
                            KL_DIV_REAL: metrics[model][KL_DIV_REAL],
                            KL_DIV_WEIGHTED_UTILITY: metrics[model][
                                KL_DIV_WEIGHTED_UTILITY
                            ],
                            KL_DIV_MAX_UTILITY: metrics[model][KL_DIV_MAX_UTILITY],
                        }
                        results.append(result)
    return results


def process_rank_instance_suite(
    n: int, models: List[str], branching_schemes: List[str]
) -> List[Dict[str, Any]]:
    """
    Process all instances for a given set of models and branching schemes.

    Args:
    n (int): Side length of the square grid.
    models (List[str]): List of models to use.
    branching_schemes (List[str]): List of branching schemes to use.

    Returns:
    List[Dict[str, Any]]: List of results for each instance.
    """
    results = []
    for filled in filled_list(n):
        for file_number in range(1, INSTANCE_NUMBER + 1):
            solutions = load_all_solutions(n, filled, file_number)
            if len(solutions) < 8000:
                ranked_solutions = rank_solutions(solutions)
                for model in models:
                    for scheme in branching_schemes:
                        found_solution = load_found_solution(
                            n, filled, file_number, model, scheme
                        )
                        rank = solution_index_in_list(ranked_solutions, found_solution)
                        results.append(
                            {
                                SOLUTION_NB: len(solutions),
                                "model": model,
                                "branching_scheme": scheme,
                                RANK: rank,
                            }
                        )
    return results


def process_results(
    solutions: List[Solution], model_beliefs: Dict[str, Marginals]
) -> Dict[str, Dict[str, float]]:
    return {
        model: calculate_metrics(solutions, beliefs)
        for model, beliefs in model_beliefs.items()
    }


def plot_solution_ranks(
    results: List[Dict[str, Any]],
    x_key: str,
    group_key: str,
    title: str,
    output_path: Path,
):
    """
    Plot the rank of the first solution for each model and branching scheme.

    Args:
    results (List[Dict[str, Any]]): List of results to plot.
    x_key (str): Key to use for the x-axis.
    group_key (str): Key to use for the group.
    title (str): Title of the plot.
    output_path (Path): Path to save the plot to.
    """
    plt.figure(figsize=(10, 6))

    groups = set(result[group_key] for result in results)
    for group in groups:
        group_results = [r for r in results if r[group_key] == group]
        x_values = [r[SOLUTION_NB] for r in group_results]
        y_values = [r[RANK] for r in group_results]
        plt.scatter(x_values, y_values, label=group, marker="o")

    plt.xlabel("Number of Solutions")
    plt.ylabel("Rank of first Solution")
    plt.title(title)
    plt.legend()
    plt.grid(True)
    plt.savefig(output_path)
    plt.close()


def compare_models(n: int, models: List[str], branching_scheme: str, output_dir: str):
    """
    Compare the rank of the first solution for different models.

    Args:
    n (int): Side length of the square grid.
    models (List[str]): List of models to compare.
    branching_scheme (str): Branching scheme to use.
    output_dir (str): Directory to save the plot to.
    """
    results = process_rank_instance_suite(n, models, [branching_scheme])
    title = f"Rank of first Solution for Different Models\n(Branching Scheme: {branching_scheme})"
    output_path = Path(output_dir) / f"compare_models_n{n}_{branching_scheme}.png"
    plot_solution_ranks(results, "num_solutions", "model", title, output_path)


def compare_branching_schemes(
    n: int, model: str, branching_schemes: List[str], output_dir: str
):
    """
    Compare the rank of the first solution for different branching schemes.

    Args:
    n (int): Side length of the square grid.
    model (str): Model to compare.
    branching_schemes (List[str]): List of branching schemes to compare.
    output_dir (str): Directory to save the plot to.
    """
    results = process_rank_instance_suite(n, [model], branching_schemes)
    title = f"Rank of first Solution for Different Branching Schemes\n(Model: {model})"
    output_path = Path(output_dir) / f"compare_branching_schemes_n{n}_{model}.png"
    plot_solution_ranks(
        results, "num_solutions", "branching_scheme", title, output_path
    )


# --- Instance management ---


def to_text(square):
    if square:
        width = max(len(str(sym)) for row in square for sym in row)
        txt = "\n".join(" ".join(f"{sym:>{width}}" for sym in row) for row in square)
    else:
        txt = ""
    return txt


def next_available_file_number(n, filled):
    file_number = 1
    while os.path.exists(instance_filepath(n, filled, file_number)):
        file_number += 1
    return file_number


def create_instance(n, filled):
    square = rls(n)

    all_coordinates = [(i, j) for i in range(n) for j in range(n)]
    filled_coordinates = sorted(
        random.sample(all_coordinates, filled), key=lambda x: (x[0], x[1])
    )

    instance_str = f"{n} {filled}\n" + "\n".join(
        f"{coord[0]} {coord[1]} {square[coord[0]][coord[1]]}"
        for coord in filled_coordinates
    )

    # save the result in a file
    file_number = next_available_file_number(n, filled)
    filepath = instance_filepath(n, filled, file_number)

    with open(filepath, "w") as f:
        f.write(instance_str)

    print(f"File written: {filepath}")


def display_instance(n, filled, file_number):
    square = [["*" for _ in range(n)] for _ in range(n)]
    filepath = instance_filepath(n, filled, file_number)
    with open(filepath, "r") as f:
        n, filled = map(int, f.readline().split())
        for _ in range(filled):
            i, j, value = map(int, f.readline().split())
            square[i][j] = value
    print(f"Instance: {filepath}")
    print(to_text(square))


def filled_list(n):
    """
    Generate a list of numbers of values filled in the instance of a given side length.
    Allows to vary the number of solutions for the instances generated.

    Args:
    n (int): Side length of the square grid.

    Returns:
    List[int]: List of numbers of values filled in the instance.
    """
    return [int(n * n * 8 / 20), int(n * n * 9 / 20)]


def generate_instance_suite(n):
    """
    Generate all instances for a given side length.

    Args:
    n (int): Side length of the square grid.
    """
    for filled in filled_list(n):
        for _ in range(INSTANCE_NUMBER):
            create_instance(n, filled)


# --- Solution processing ---


def process_instance(
    n: int, filled: int, file_number: int, models: List[str], branching_scheme: str
) -> Dict[str, Any]:
    """
    Dtermine the rank of the first solution for a given instance, model, and branching scheme.

    Args:
    n (int): Side length of the square grid.
    filled (int): Number of values filled in the instance.
    file_number (int): File number of the instance.
    models (List[str]): List of models to use.
    branching_scheme (str): Branching scheme to use.

    Returns:
    Dict[str, Any]: Result for the instance.
    """
    solutions = load_all_solutions(n, filled, file_number)
    ranked_solutions = rank_solutions(solutions)

    result = {"num_solutions": len(solutions), "model_ranks": {}}

    for model in models:
        found_solution = load_found_solution(
            n, filled, file_number, model, branching_scheme
        )
        rank = solution_index_in_list(ranked_solutions, found_solution)
        if rank:  # < 200:
            result["model_ranks"][model] = rank

    return result


def rank_solutions(solutions: List[Solution]) -> List[Tuple[Solution, int]]:
    """
    Rank solutions based on their utility (diagonal sum).

    Args:
    solutions (List[Solution]): List of solutions to rank.

    Returns:
    List[Tuple[Solution, int]]: List of tuples containing (solution, rank),
                                sorted by rank (0 being the best).
    """
    # Sort solutions by score in descending order
    sorted_solutions = sorted(solutions, key=solution_score, reverse=True)

    # Create a list of tuples (solution, rank)
    ranked_solutions = [(sol, rank) for rank, sol in enumerate(sorted_solutions)]

    return ranked_solutions


def solution_index_in_list(
    ranked_solutions: List[Tuple[Solution, int]], solution: Solution
) -> int:
    """
    Find the rank of a given solution in the list of ranked solutions.

    Args:
    ranked_solutions (List[Tuple[Solution, int]]): List of ranked solutions.
    solution (Solution): The solution to find.

    Returns:
    int: The rank of the solution, or -1 if not found.
    """
    for sol, rank in ranked_solutions:
        if same_solution(sol, solution):
            return rank
    return -1


def same_solution(s1: Solution, s2: Solution) -> bool:
    return all(s1[i][j] == s2[i][j] for i in range(len(s1)) for j in range(len(s1)))


def normalize(stats: Dict[int, float]) -> Dict[int, float]:
    total = sum(stats.values())
    return {k: v / total for k, v in stats.items()}


def solution_score(solution):
    """
    Calculate the score of a solution, which is the sum of the values in the diagonal.

    Args:
    solution (Solution): Solution to calculate the score for.

    Returns:
    int: Score of the solution.
    """
    return sum(solution[i][i] for i in range(len(solution)))


# --- Instance solution metrics ---


def calculate_marginals(
    solutions: List[Solution], weight_func: Callable[[Solution], float] = lambda _: 1
) -> Marginals:
    n = len(solutions[0])
    hist = [[{} for _ in range(n)] for _ in range(n)]
    for solution in solutions:
        weight = weight_func(solution)
        for i in range(n):
            for j in range(n):
                hist[i][j][solution[i][j]] = hist[i][j].get(solution[i][j], 0) + weight
    return [[normalize(stats) for stats in row] for row in hist]


def real_marginals(solutions: List[Solution]) -> Marginals:
    return calculate_marginals(solutions)


def marginals_weighted_by_utility(solutions: List[Solution]) -> Marginals:
    return calculate_marginals(solutions, weight_func=solution_score)


def max_utility_marginals(solutions: List[Solution]) -> Marginals:
    n = len(solutions[0])
    hist = [[{} for _ in range(n)] for _ in range(n)]
    for solution in solutions:
        utility = solution_score(solution)
        for i in range(n):
            for j in range(n):
                hist[i][j][solution[i][j]] = max(
                    hist[i][j].get(solution[i][j], 0), utility
                )
    return [[normalize(stats) for stats in row] for row in hist]


def get_real_utility_ranks(solutions: List[Solution]) -> Ranks:
    """
    Gives the rank of the best solution in which each value appears at each position
    """
    n = len(solutions[0])
    ranks = [[{} for _ in range(n)] for _ in range(n)]
    solutions = sorted(solutions, key=solution_score)
    for rank, solution in enumerate(solutions):
        for i in range(n):
            for j in range(n):
                if solution[i][j] not in ranks[i][j]:
                    ranks[i][j][solution[i][j]] = rank
    return ranks


def count_errors_in_rank_and_belief(ranks, beliefs):
    """
    Check if the solution with lowest rank is the one with highest belief

    Args:
    n (int): Side length of the square grid.
    ranks (List[List[Dict[int, int]]]): List of ranks for each variable.
    beliefs (List[List[Dict[int, float]]]): List of beliefs for each variable.

    Returns:
    int: Number of errors, i.e. the number of solutions where the rank of the first solution is not the same as the maximum belief.
    """
    n = len(ranks)
    nb_errors = 0
    for i in range(n):
        for j in range(n):
            best_rank = min(ranks[i][j], key=ranks[i][j].get)
            max_belief = max(beliefs[i][j].values())
            best_beliefs = [k for k, v in beliefs[i][j].items() if v == max_belief]

            if best_rank not in best_beliefs:
                nb_errors += 1
    return nb_errors


def convert_to_numpy_array(data: Marginals) -> Tuple[np.ndarray, np.ndarray]:
    max_key = max(max(max(d.keys()) for d in row) for row in data)
    result = np.zeros((max_key + 1, len(data), len(data[0])))
    mask = np.ones((len(data), len(data[0])), dtype=bool)

    for i, row in enumerate(data):
        for j, d in enumerate(row):
            for k, v in d.items():
                result[k, i, j] = v
            if len(d) == 1:
                mask[i, j] = False

    return result, mask


def calculate_kl_divergence(ref_dist: Marginals, dist: Marginals) -> np.ma.MaskedArray:
    ref_dist_array, ref_mask = convert_to_numpy_array(ref_dist)
    dist_array, dist_mask = convert_to_numpy_array(dist)

    mask = ref_mask & dist_mask
    entropy = stats.entropy(dist_array, ref_dist_array, axis=0)
    return np.ma.array(entropy, mask=~mask)


def mean_kl_divergence(dist: Marginals, ref_dist: Marginals) -> float:
    masked_entropy = calculate_kl_divergence(ref_dist, dist)
    return np.mean(masked_entropy)


def calculate_metrics(
    solutions: List[Solution], beliefs: Marginals
) -> Dict[str, float]:
    real_marg = real_marginals(solutions)
    weighted_marg = marginals_weighted_by_utility(solutions)
    max_util_marg = max_utility_marginals(solutions)

    return {
        KL_DIV_REAL: mean_kl_divergence(beliefs, real_marg),
        KL_DIV_WEIGHTED_UTILITY: mean_kl_divergence(beliefs, weighted_marg),
        KL_DIV_MAX_UTILITY: mean_kl_divergence(beliefs, max_util_marg),
    }


# --- File management helpers ---
CPBP_DIR = Path(
    os.path.expandvars("$HOME/dev/eclipse-workspace/CPBP-axel/MiniCPBP-MAP")
)
OUTPUT_DIR = CPBP_DIR / "logs/LatinSquare/traces/"
FOUND_SOLUTION_DIR = CPBP_DIR / "logs/LatinSquare/solutions/"


def base_filename(n: int, filled: int, file_number: int) -> str:
    return f"latinSquare{n}-filled{filled}-{file_number}"


def instance_filepath(n: int, filled: int, file_number: int) -> Path:
    return (
        CPBP_DIR
        / "src/main/java/minicpbp/examples/data/LatinSquare"
        / f"{base_filename(n, filled, file_number)}.dat"
    )


def solution_filepath(n: int, filled: int, file_number: int) -> Path:
    return (
        CPBP_DIR
        / "solutions/LatinSquare"
        / f"solutions-{base_filename(n, filled, file_number)}.sol"
    )


def solution_pickle_filepath(n: int, filled: int, file_number: int) -> Path:
    return (
        Path("solutions")
        / "LatinSquare"
        / f"solutions-{base_filename(n, filled, file_number)}.pickle"
    )


def model_solution_filepath(
    n: int, filled: int, file_number: int, model: str, branching_scheme: str
) -> Path:
    return (
        CPBP_DIR
        / "logs/solutions/LatinSquare"
        / f"out-solution-{base_filename(n, filled, file_number)}-{model}-{branching_scheme}.out"
    )


def output_filepath(
    n: int, filled: int, file_number: int, model: str, branching_scheme: str
) -> Path:
    return (
        OUTPUT_DIR
        / f"out-{base_filename(n, filled, file_number)}-{model}-{branching_scheme}.out"
    )


def found_solution_filepath(
    n: int, filled: int, file_number: int, model: str, branching_scheme: str
) -> Path:
    return (
        FOUND_SOLUTION_DIR
        / f"out-solution-{base_filename(n, filled, file_number)}-{model}-{branching_scheme}.out"
    )


# --- Load data ---


def load_raw_data(filepath: Path) -> List[str]:
    with filepath.open("r") as f:
        return f.readlines()


def parse_solution(raw_data: List[str], n: int) -> Solution:
    """
    Parse raw solution data into a 2D list of integers.

    Args:
    raw_data (List[str]): Raw solution data.
    n (int): Side length of the square grid.

    Returns:
    Solution: Structured solution data.
    """
    solution = [[-1 for _ in range(n)] for _ in range(n)]
    for line in raw_data:
        if line.strip() and not line.startswith("="):
            i, j, v = map(int, line.split())
            solution[i][j] = v
    return solution


def parse_beliefs(raw_data: List[str], n: int) -> Beliefs:
    """
    Parse raw belief data into a structured format.

    Args:
    raw_data (List[str]): Raw belief data.
    n (int): Side length of the square grid.

    Returns:
    Beliefs: Structured belief data.
    """
    beliefs = [[{} for _ in range(n)] for _ in range(n)]
    for line in raw_data:
        match = re.match(r"x\[(\d+),(\d+)]{(.+?)}", line)
        if match:
            i, j = int(match.group(1)), int(match.group(2))
            match_values = re.findall(r"(\d+)\s*<([^>]+)>", match.group(3))
            beliefs[i][j] = {int(p): float(v) for p, v in match_values}
    return beliefs


def load_raw_solution(filepath: Path) -> List[str]:
    """Load raw solution data from a file."""
    with filepath.open("r") as f:
        return f.readlines()


def load_pickle(filepath: Path) -> Any:
    """Load data from a pickle file."""
    with filepath.open("rb") as f:
        return pickle.load(f)


def save_pickle(filepath: Path, data: Any) -> None:
    """Save data to a pickle file."""
    with filepath.open("wb") as f:
        pickle.dump(data, f)


def load_all_solutions(
    n: int,
    filled: int,
    file_number: int,
) -> List[Solution]:
    """
    Load solutions from a pickle file, if absent, load from the solution file and create the pickle file
    """
    # Determine the appropriate filepath
    filepath = solution_filepath(n, filled, file_number)

    try:
        pickle_filename = filepath.name + ".pickle"
        pickle_filepath = Path("solutions") / pickle_filename
        return load_pickle(pickle_filepath)
    except FileNotFoundError:
        filepath = solution_filepath(n, filled, file_number)
        # If pickle file not found, load from original file
        raw_data = load_raw_data(filepath)
        solutions = parse_all_solutions(raw_data, n)
        # Save to pickle file for future use
        save_pickle(pickle_filepath, solutions)
        return solutions


def parse_all_solutions(raw_data: List[str], n: int) -> List[Solution]:
    solutions = []
    solution_data = []
    for line in raw_data:
        if line.startswith("=====") and solution_data:
            solutions.append(parse_solution(solution_data, n))
            solution_data = []
    return solutions


def load_found_solution(
    n: int, filled: int, file_number: int, model: str, branching_scheme: str
) -> Solution:
    filepath = found_solution_filepath(n, filled, file_number, model, branching_scheme)
    raw_data = load_raw_data(filepath)
    return parse_solution(raw_data, n)


def load_beliefs(n: int, filled: int, file_number: int, model: str) -> List[Beliefs]:
    """
    Load the beliefs from the output file for a given model.

    Args:
    n (int): Side length of the square grid.
    filled (int): Number of values filled in the instance.
    file_number (int): File number of the instance.
    model (str): Model to use.

    Returns:
    List[Beliefs]: List of beliefs for each iteration, where each belief is a dictionary mapping values to their probabilities.
    """
    branching_scheme = MAX_MARGINAL
    filepath = output_filepath(n, filled, file_number, model, branching_scheme)
    raw_data = load_raw_data(filepath)
    belief_iterations = []
    current_iteration = []
    for line in raw_data:
        if line.startswith("##### after BP iteration"):
            if current_iteration:
                belief_iterations.append(parse_beliefs(current_iteration, n))
                current_iteration = []
        else:
            current_iteration.append(line)
    if current_iteration:
        belief_iterations.append(parse_beliefs(current_iteration, n))
    return belief_iterations
