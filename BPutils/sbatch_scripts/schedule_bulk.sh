#!/bin/bash

# Define the ranges for holes and models
nbHoles_array=(500 600 700)
objective_array=(pseudodiagonal_30_15_10 pseudodiagonal_30_15_15 pseudodiagonal_90_15_10 pseudodiagonal_90_15_15)
searchType_array=(dfs lds)
truncateRate_array=(0) # (0 50)

# Iterate over holes and models
for nbHoles in "${nbHoles_array[@]}"; do
    for objective in "${objective_array[@]}"; do
        for searchType in "${searchType_array[@]}"; do
	            for truncateRate in "${truncateRate_array[@]}"; do
                        # Construct the sbatch command
                        sbatch_command="sbatch job.sh --nbHoles $nbHoles --objective $objective --searchType $searchType"
                        
                        # Execute the sbatch command
                        eval "$sbatch_command"
                        
                        # Print the executed command for logging purposes
                        echo "Executing: $sbatch_command"

	                # 2-second sleep to not overload the scheduler
	                sleep 2
	            done
        done
    done
done

