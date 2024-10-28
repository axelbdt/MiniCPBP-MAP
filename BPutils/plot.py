import log_parse
import matplotlib.pyplot as plt
import numpy as np
from tabulate import tabulate

def_colors = {
    "sum_product-max_marginal_regret": "blue",
    "sum_product-max_marginal_regret-oracle": "orange",
    "max_product-max_marginal_regret-oracle": "green",
    "max_product-dom_wdeg_max_marginal-oracle": "steelblue",
    "sum_product-dom_wdeg_max_marginal": "orange",
    "no_bp-first_fail": "red",
    "no_bp-max_value": "violet",
    "no_bp-dom_wdeg": "purple",
    # trucated models
    "sum_product-max_marginal_regret-truncate50": "darkblue",
    "sum_product-max_marginal_regret-oracle-truncate50": "darkorange",
    "max_product-max_marginal_regret-oracle-truncate50": "darkgreen",
    "no_bp-first_fail-truncate50": "darkred",
    "no_bp-max_value-truncate50": "darkviolet",
    # strength models
    "sum_product-max_marginal_strength": "darkblue",
    "sum_product-max_marginal_strength-oracle": "darkorange",
    "max_product-max_marginal_strength-oracle": "darkgreen",
}

COLORS = {f"dfs-{model}": color for model, color in def_colors.items()} | {
    f"lds-{model}": color for model, color in def_colors.items()
}


def get_or_na(d, key):
    if d is None:
        return "N/A"
    return d.get(key, "N/A")


def hole_tables(n, holes, models, instance_numbers, objective="diagonal"):
    for model in models:
        table_data = []
        for instance_number in instance_numbers:
            filename = (
                f"latin-square30-holes{holes}-{instance_number}-{objective}-{model}.out"
            )
            file_path = f"logs/latin-square/{filename}"
            result = log_parse.parse_solution_file(file_path)
            try:
                end_stats = result["end_stats"]
                time = end_stats.get("execution time (ms)", "N/A")
                fails = end_stats.get("#fail", "N/A")
                time_per_fail = (
                    f"{(float(time) / float(fails)):.2f}"
                    if fails != "N/A" and time != "N/A"
                    else "N/A"
                )

                solutions = result.get("solutions", [])
                last_solution = solutions[-1] if solutions else None
                end_stats = result.get("end_stats", {})

                row = [
                    instance_number,
                    # last_solution.get("solution score", "N/A"),
                    get_or_na(last_solution, "solution score"),
                    end_stats.get("#choice", "N/A"),
                    end_stats.get("#fail", "N/A"),
                    end_stats.get("#sols", "N/A"),
                    end_stats.get("completed", "N/A"),
                ]

                table_data.append(row)
            except Exception as e:
                print(result)
                print(f"Error while parsing {file_path}: {e}")

        headers = [
            "Instance",
            "Score",
            "Choices",
            "Fails",
            # "Time (ms)",
            # "Time/Fail",
            "Solutions",
            "Completed",
        ]
        markdown_table = tabulate(table_data, headers=headers, tablefmt="pipe")

        print(f"### {model.replace('-', ', ').replace('_', ' ').title()}")
        print(markdown_table)
        print()


def fail_graph(
    n, holes, models, instance_numbers, objective="diagonal", colors=COLORS, legend=True
):

    fail_numbers = {}
    for model in models:
        fail_numbers[model] = []

        for instance_number in instance_numbers:
            filename = (
                f"latin-square30-holes{holes}-{instance_number}-{objective}-{model}.out"
            )
            file_path = f"logs/latin-square/{filename}"
            result = log_parse.parse_solution_file(file_path)
            if not result:
                raise Exception(f"Error while parsing {file_path}")
            else:
                end_stats = result["end_stats"]
                fail_numbers[model].append(int(end_stats.get("#fail", 0)))

    num_models = len(models)
    _, ax = plt.subplots(figsize=(8, 4))
    bar_width = 0.8 / num_models
    opacity = 0.8

    max_time = max(max(times) for times in fail_numbers.values() if times)

    for i, model in enumerate(models):
        times = fail_numbers[model]
        positions = (
            np.array(instance_numbers)
            + i * bar_width
            - (num_models - 1) * bar_width / 2
        )

        # Plot regular bars
        valid_times = [t if t != 0 else np.nan for t in times]
        ax.bar(
            positions,
            valid_times,
            bar_width,
            alpha=opacity,
            label=model,
            color=colors[model],
        )

        # Plot hatched bars for timeouts
        timeout_positions = [pos for pos, t in zip(positions, times) if t == 0]
        if timeout_positions:
            ax.bar(
                timeout_positions,
                [max_time * 1.2] * len(timeout_positions),
                bar_width,
                alpha=opacity,
                hatch="///",
                edgecolor=colors.get(model, None),
                fill=False,
            )

    ax.set_xlabel(f"Instance Number (n = {n}, holes = {holes})")
    ax.set_ylabel("# Fails")
    ax.set_title("Number of Fails by Model and Instance")
    ax.set_xticks(instance_numbers)
    if legend:
        ax.legend()

    plt.tight_layout()
    plt.show()


def quality_graph(n, holes, models, instance_number):
    plt.figure(figsize=(8, 4))
    for model in models:
        filename = f"latin-square30-holes{holes}-{instance_number}-{model}.out"
        file_path = f"logs/latin-square/{filename}"
        result = log_parse.parse_solution_file(file_path)
        if result:
            scores = [
                int(solution["solution score"]) for solution in result["solutions"]
            ]
            fails = [int(solution["#fail"]) for solution in result["solutions"]]
            plt.plot(fails, scores, "o-", label=model, color=colors[model])

    plt.xlabel("Number of Failures")
    plt.ylabel("Solution Score")
    plt.title(
        f"Quality Graph for Latin Square (n={n}, holes={holes}, instance={instance_number})"
    )
    plt.legend()
    plt.grid(True)
    # plt.savefig(f"quality_graph_n{n}_holes{holes}.png")
    plt.show()


def optimal_scores(n, holes, models, instance_numbers):
    for instance_number in instance_numbers:
        filename = f"latin-square30-holes{holes}-{instance_number}-max_product-max_marginal_regret-oracle.out"
        file_path = f"logs/latin-square/{filename}"
        result = log_parse.parse_solution_file(file_path)
        print(result["end_stats"].get("best_solution_score", "N/A"))


def first_solution_score(n, holes, models, instance_numbers):
    for instance_number in instance_numbers:
        filename = f"latin-square30-holes{holes}-{instance_number}-sum_product-max_marginal_regret-no_oracle.out"
        file_path = f"logs/latin-square/{filename}"
        result = log_parse.parse_solution_file(file_path)
        print(result["solutions"][0]["solution score"])
