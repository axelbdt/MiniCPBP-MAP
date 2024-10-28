import os
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Tuple

CPBP_DIR = Path(
    os.path.expandvars("$HOME/dev/eclipse-workspace/CPBP-axel/MiniCPBP-MAP")
)
INSTANCE_DIR = CPBP_DIR / "data/latin-square/"
OUTPUT_DIR = CPBP_DIR / "logs/latin-square/traces/"
FOUND_SOLUTION_DIR = CPBP_DIR / "logs/latin-square/solutions/"


# instance dataclass with n and partial assignment as dict of (i, j) -> value
@dataclass
class Instance:
    n: int
    assignments: Dict[Tuple[int, int], int]


def to_text(square):
    if square:
        width = max(len(str(sym)) for row in square for sym in row)
        txt = "\n".join(" ".join(f"{sym:>{width}}" for sym in row) for row in square)
    else:
        txt = ""
    return txt


def get_hole_number(n):
    return int(n * n * 11 / 20)


def display_instance(instance):
    square = [["*" for _ in range(instance.n)] for _ in range(instance.n)]
    for i, j in instance.assignments:
        square[i][j] = instance.assignments[(i, j)]
    print(to_text(square))


def get_filepath(n, nb_filled, file_number):
    return INSTANCE_DIR / f"latin-square-{n}-filled{nb_filled}-{file_number}.dat"


def save_instance(instance, file_number):
    nb_filled = len(instance.assignments)
    filepath = get_filepath(instance.n, nb_filled, file_number)
    with filepath.open("w") as f:
        f.write(f"{instance.n} {nb_filled}\n")
        for i, j in sorted(instance.assignments):
            f.write(f"{i} {j} {instance.assignments[(i, j)]}\n")


def load_instance(n, nb_filled, file_number) -> Instance:
    filepath = get_filepath(n, nb_filled, file_number)
    with filepath.open("r") as f:
        n, nb_filled = map(int, f.readline().split())
        assignments = {}
        for line in f:
            i, j, value = map(int, line.split())
            assignments[(i, j)] = value
        return Instance(n, assignments)


def create_instance_file(n, holes, file_number, output_dir):
    command = [
        "./lsencode",
        "new",
        "bqcp",
        Path(output_dir) / f"latin-square-{n}-holes{holes}-{file_number}",
        str(n),
        str(holes),
    ]
    try:
        result = subprocess.run(command, check=True, capture_output=True, text=True)
        print("Output:", result.stdout)
    except subprocess.CalledProcessError as e:
        print("Error occurred:", e)
        print("Error output:", e.stderr)


def parse_instance_file(file_path: str) -> Instance:
    with open(file_path, "r") as file:
        lines = file.readlines()

    # Parse the order (n)
    n = int(lines[0].split()[1])

    # Initialize assignments dictionary
    assignments = {}

    # Parse the grid
    for i, line in enumerate(lines[1:]):
        values = line.split()
        for j, value in enumerate(values):
            if value != "-1":
                assignments[(i, j)] = int(value)
    return Instance(n, assignments)


if __name__ == "__main__":
    n = 30
    for holes in [350]:
        for i in range(1, 11):
            create_instance_file(n, holes, i, INSTANCE_DIR)
