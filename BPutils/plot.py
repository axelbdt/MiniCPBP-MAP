from latin_square import (FIRST_FAIL_RANDOM_VAL, MAX_MARGINAL,
                          MAX_MARGINAL_REGRET_RANDOM_TIE_BREAK,
                          MAX_PRODUCT_INIT, MAX_PRODUCT_INIT_EXP,
                          MAX_PRODUCT_ORACLE, SUM_PRODUCT_INIT,
                          SUM_PRODUCT_INIT_EXP, SUM_PRODUCT_NO_INIT,
                          SUM_PRODUCT_ORACLE, compare_branching_schemes,
                          compare_models, generate_metric_plots)

output_dir = "plots"

compare_models(
    n=8,
    models=[
        MAX_PRODUCT_INIT,
        SUM_PRODUCT_INIT,
        SUM_PRODUCT_INIT_EXP,
        MAX_PRODUCT_INIT_EXP,
    ],
    branching_scheme=MAX_MARGINAL_REGRET_RANDOM_TIE_BREAK,
    output_dir="plots",
)
# compare_branching_schemes(
#     n = 8,
#     model = MAX_PRODUCT_INIT,
#     branching_schemes = [
#         MAX_MARGINAL,
#         MAX_MARGINAL_REGRET_RANDOM_TIE_BREAK,
#         # FIRST_FAIL_RANDOM_VAL,
#     ],
#     output_dir = output_dir,
# )

n = 8
models = [
    MAX_PRODUCT_ORACLE,
    MAX_PRODUCT_INIT,
    SUM_PRODUCT_ORACLE,
    SUM_PRODUCT_INIT,
    SUM_PRODUCT_NO_INIT,
]

# generate_metric_plots(n, models, output_dir)
