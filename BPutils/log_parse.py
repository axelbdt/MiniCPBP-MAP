import os
import re
import shutil
from dataclasses import dataclass


@dataclass
class Model:
    bp: str
    branching: str
    oracle: bool

    def __str__(self):
        oracle_str = "-oracle" if self.oracle else ""
        return f"{self.bp}-{self.branching}{oracle_str}"


@dataclass
class Run:
    n: int
    nb_holes: int
    nb_file: int
    objective: str
    search: str
    model: Model
    truncateRate: int

    def __str__(self):
        truncateRate_str = f"-truncate{self.truncateRate}" if self.truncateRate else ""
        return f"latin-square{self.n}-holes{self.nb_holes}-{self.nb_file}-{self.objective}-{self.search}-{self.model}{truncateRate_str}"

    def __repr__(self):
        return str(self)


MODELS = [
    Model(bp="no_bp", branching="first_fail", oracle=False),
    Model(bp="no_bp", branching="max_value", oracle=False),
    Model(bp="sum_product", branching="max_marginal_regret", oracle=False),
    Model(bp="sum_product", branching="max_marginal_regret", oracle=True),
    Model(bp="max_product", branching="max_marginal_regret", oracle=True),
    Model(bp="sum_product", branching="max_marginal_strength", oracle=False),
    Model(bp="sum_product", branching="max_marginal_strength", oracle=True),
    Model(bp="max_product", branching="max_marginal_strength", oracle=True),
]


def out_filename(run: Run):
    return f"{run}.out"


def parse_solution_file(file_path):
    info = {}
    solutions = []
    end_stats = {}
    current_section = None
    current_solution = None

    if not os.path.exists(file_path):
        raise FileNotFoundError(f"File {file_path} not found")

    with open(file_path, "r") as file:
        for line in file:
            line = line.strip()

            if line == "INFO":
                current_section = "info"
            elif line == "START SEARCH":
                current_section = "search"
            elif line == "NEW SOLUTION FOUND":
                if current_solution:
                    solutions.append(current_solution)
                current_solution = {}
                current_section = "solution"
            elif line == "END OF SEARCH":
                if current_solution:
                    solutions.append(current_solution)
                if solutions:
                    end_stats["best_solution_score"] = int(
                        current_solution["solution score"]
                    )
                current_solution = None
                current_section = "end_stats"
            elif ":" in line:
                key, value = map(str.strip, line.split(":", 1))

                if current_section == "info":
                    info[key] = value
                elif current_section == "solution":
                    current_solution[key] = value
                elif current_section == "end_stats":
                    end_stats[key] = value

    return {"info": info, "solutions": solutions, "end_stats": end_stats}


def missing_files(folder):
    tries = 0
    missing = 0
    for holes in [500, 600, 700]:
        for instance_number in range(1, 11):
            for model in MODELS:
                for truncateRate in [0, 50]:
                    for search in ["dfs", "lds"]:
                        tries += 1
                        run = Run(
                            n=30,
                            nb_holes=holes,
                            nb_file=instance_number,
                            objective="diagonal",
                            search=search,
                            model=model,
                            truncateRate=truncateRate,
                        )
                        filename = out_filename(run)
                        file_path = f"{folder}/{filename}"
                        if not os.path.exists(file_path):
                            missing += 1
                            print(file_path)
    print(f"searched for {tries} files")
    print(f"{missing} missing files")


def parse_info_section(content):
    info = {}
    info_section = re.search(r"INFO(.*?)START SEARCH", content, re.DOTALL)
    if info_section:
        info_lines = info_section.group(1).strip().split("\n")
        for line in info_lines:
            key, value = line.split(":")
            info[key.strip()] = value.strip()
    return info


if __name__ == "__main__":
    missing_files("logs/latin-square")
